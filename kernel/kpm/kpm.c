/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2025 Liankong (xhsw.new@outlook.com). All Rights Reserved.
 * 本代码由GPL-2授权
 * 
 * 适配KernelSU的KPM 内核模块加载器兼容实现
 * 
 * 集成了 ELF 解析、内存布局、符号处理、重定位（支持 ARM64 重定位类型）
 * 并参照KernelPatch的标准KPM格式实现加载和控制
 * 
 * 本文件是 KernelPatch (LyraVoid/KernelPatch @ 5591b8e, v0.13.5)
 * kernel/patch/module/module.c + relo.c + insn.c 的移植版:
 *   - kp_malloc_exec/kp_free_exec        -> module_alloc/module_memfree
 *   - symbol_lookup_name                 -> sukisu_compact_find_symbol
 *   - compat_copy_to_user                -> copy_to_user
 *   - flush_icache_all                   -> flush_icache_range
 *   - 模块列表锁: 由 KernelPatch 的 rcu_read_lock 改为 mutex 串行化
 *     (ioctl 进程上下文, PREEMPT=y 下 rcu_read_lock 内调用 exit 会 sleep 报警)
 */

#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/mutex.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/set_memory.h>
#include <linux/export.h>
#include <linux/slab.h>
#include <linux/errno.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) && defined(CONFIG_MODULES)
#include <linux/moduleloader.h>
#endif
#include "kpm.h"
#include "compact.h"

#define KPM_NAME_LEN 32
#define KPM_ARGS_LEN 1024
#define KPM_LOAD_ERROR_MESSAGE_LEN 160

/* 移植源: LyraVoid/KernelPatch @ 5591b8e (v0.13.5) */
#define KPM_LOADER_VERSION "0.13.5"

#define KPM_LOAD_RESULT_MAGIC 0x4b504d52

struct kpm_load_result {
    unsigned int magic;
    unsigned int size;
    int code;
    char message[KPM_LOAD_ERROR_MESSAGE_LEN];
};

#define elf_check_arch(x) ((x)->e_machine == EM_AARCH64)

#define ARCH_SHF_SMALL 0

/* ===================== KPM 模块/加载信息结构 (KernelPatch module.h) ===================== */

typedef long (*kpm_initcall_t)(const char *args, const char *event, void *reserved);
typedef long (*kpm_ctl0call_t)(const char *ctl_args, char *__user out_msg, int outlen);
typedef long (*kpm_ctl1call_t)(void *a1, void *a2, void *a3);
typedef long (*kpm_exitcall_t)(void *reserved);
typedef long (*kpm_eventcall_t)(const char *event, const char *args, void *reserved);

struct kpm_load_info {
    struct {
        const char *base;
        unsigned long size;
        const char *name, *version, *license, *author, *description;
        char error_msg[KPM_LOAD_ERROR_MESSAGE_LEN];
    } info;
    const Elf_Ehdr *hdr;
    unsigned long len;
    Elf_Shdr *sechdrs;
    char *secstrings, *strtab;
    unsigned long symoffs, stroffs;
    struct {
        unsigned int sym, str, mod, info;
    } index;
};

struct kpm_module {
    struct {
        const char *base, *name, *version, *license, *author, *description;
    } info;

    char *args, *ctl_args;
    char load_event[32];
    char load_source[16];

    kpm_initcall_t *init;
    kpm_ctl0call_t *ctl0;
    kpm_ctl1call_t *ctl1;
    kpm_exitcall_t *exit;
    kpm_eventcall_t *event;

    unsigned int size;
    unsigned int text_size;
    unsigned int ro_size;

    void *start;

    struct list_head list;
};

static LIST_HEAD(kpm_modules);
static DEFINE_MUTEX(kpm_lock);

/* ===================== ARM64 指令编码 (KernelPatch insn.c 最小集) ===================== */
/* 注: 命名加了 KPM_INSN_ 前缀, 避免与 asm/insn.h 的 aarch64_insn_* 定义冲突
 * (asm/insn.h 会经 kprobes.h -> asm/probes.h 被引入, 且本内核未编入 insn.o) */

enum kpm_insn_imm_type {
    KPM_INSN_IMM_ADR,
    KPM_INSN_IMM_26,
    KPM_INSN_IMM_19,
    KPM_INSN_IMM_16,
    KPM_INSN_IMM_14,
    KPM_INSN_IMM_12,
    KPM_INSN_IMM_9,
    KPM_INSN_IMM_7,
    KPM_INSN_IMM_6,
    KPM_INSN_IMM_S,
    KPM_INSN_IMM_R,
    KPM_INSN_IMM_MAX
};

static u32 kpm_insn_encode_immediate(enum kpm_insn_imm_type type, u32 insn, u64 imm)
{
    u32 immlo, immhi, lomask, himask, mask;
    int shift;

    switch (type) {
    case KPM_INSN_IMM_ADR:
        lomask = 0x3;
        himask = 0x7ffff;
        immlo = imm & lomask;
        imm >>= 2;
        immhi = imm & himask;
        imm = (immlo << 24) | (immhi);
        mask = (lomask << 24) | (himask);
        shift = 5;
        break;
    case KPM_INSN_IMM_26:
        mask = BIT(26) - 1;
        shift = 0;
        break;
    case KPM_INSN_IMM_19:
        mask = BIT(19) - 1;
        shift = 5;
        break;
    case KPM_INSN_IMM_16:
        mask = BIT(16) - 1;
        shift = 5;
        break;
    case KPM_INSN_IMM_14:
        mask = BIT(14) - 1;
        shift = 5;
        break;
    case KPM_INSN_IMM_12:
        mask = BIT(12) - 1;
        shift = 10;
        break;
    case KPM_INSN_IMM_9:
        mask = BIT(9) - 1;
        shift = 12;
        break;
    case KPM_INSN_IMM_7:
        mask = BIT(7) - 1;
        shift = 15;
        break;
    case KPM_INSN_IMM_6:
    case KPM_INSN_IMM_S:
        mask = BIT(6) - 1;
        shift = 10;
        break;
    case KPM_INSN_IMM_R:
        mask = BIT(6) - 1;
        shift = 16;
        break;
    default:
        pr_err("kpm: kpm_insn_encode_immediate: unknown immediate encoding %d\n", type);
        return 0;
    }

    /* Update the immediate field. */
    insn &= ~(mask << shift);
    insn |= (imm & mask) << shift;

    return insn;
}

/* ===================== ARM64 重定位 (KernelPatch relo.c) ===================== */

#define KPM_INSN_IMM_MOVNZ KPM_INSN_IMM_MAX
#define KPM_INSN_IMM_MOVK KPM_INSN_IMM_16

enum aarch64_reloc_op {
    RELOC_OP_NONE,
    RELOC_OP_ABS,
    RELOC_OP_PREL,
    RELOC_OP_PAGE,
};

static u64 kpm_do_reloc(enum aarch64_reloc_op reloc_op, void *place, u64 val)
{
    switch (reloc_op) {
    case RELOC_OP_ABS:
        return val;
    case RELOC_OP_PREL:
        return val - (u64)place;
    case RELOC_OP_PAGE:
        return (val & ~0xfff) - ((u64)place & ~0xfff);
    case RELOC_OP_NONE:
        return 0;
    }

    pr_err("kpm: do_reloc: unknown relocation operation %d\n", reloc_op);
    return 0;
}

static int kpm_reloc_data(enum aarch64_reloc_op op, void *place, u64 val, int len)
{
    u64 imm_mask = (1 << len) - 1;
    s64 sval = kpm_do_reloc(op, place, val);

    switch (len) {
    case 16:
        *(s16 *)place = sval;
        break;
    case 32:
        *(s32 *)place = sval;
        break;
    case 64:
        *(s64 *)place = sval;
        break;
    default:
        pr_err("kpm: Invalid length (%d) for data relocation\n", len);
        return 0;
    }
    /*
	 * Extract the upper value bits (including the sign bit) and
	 * shift them to bit 0.
	 */
    sval = (s64)(sval & ~(imm_mask >> 1)) >> (len - 1);

    /*
	 * Overflow has occurred if the value is not representable in
	 * len bits (i.e the bottom len bits are not sign-extended and
	 * the top bits are not all zero).
	 */
    if ((u64)(sval + 1) > 2)
        return -ERANGE;

    return 0;
}

static int kpm_reloc_insn_movw(enum aarch64_reloc_op op, void *place, u64 val, int lsb, enum kpm_insn_imm_type imm_type)
{
    u64 imm, limit = 0;
    s64 sval;
    u32 insn = le32_to_cpu(*(u32 *)place);

    sval = kpm_do_reloc(op, place, val);
    sval >>= lsb;
    imm = sval & 0xffff;

    if (imm_type == KPM_INSN_IMM_MOVNZ) {
        /*
		 * For signed MOVW relocations, we have to manipulate the
		 * instruction encoding depending on whether or not the
		 * immediate is less than zero.
		 */
        insn &= ~(3 << 29);
        if ((s64)imm >= 0) {
            /* >=0: Set the instruction to MOVZ (opcode 10b). */
            insn |= 2 << 29;
        } else {
            /*
			 * <0: Set the instruction to MOVN (opcode 00b).
			 *     Since we've masked the opcode already, we
			 *     don't need to do anything other than
			 *     inverting the new immediate field.
			 */
            imm = ~imm;
        }
        imm_type = KPM_INSN_IMM_MOVK;
    }

    /* Update the instruction with the new encoding. */
    insn = kpm_insn_encode_immediate(imm_type, insn, imm);
    *(u32 *)place = cpu_to_le32(insn);

    /* Shift out the immediate field. */
    sval >>= 16;

    /*
	 * For unsigned immediates, the overflow check is straightforward.
	 * For signed immediates, the sign bit is actually the bit past the
	 * most significant bit of the field.
	 * The KPM_INSN_IMM_16 immediate type is unsigned.
	 */
    if (imm_type != KPM_INSN_IMM_16) {
        sval++;
        limit++;
    }

    /* Check the upper bits depending on the sign of the immediate. */
    if ((u64)sval > limit)
        return -ERANGE;

    return 0;
}

static int kpm_reloc_insn_imm(enum aarch64_reloc_op op, void *place, u64 val, int lsb, int len,
                              enum kpm_insn_imm_type imm_type)
{
    u64 imm, imm_mask;
    s64 sval;
    u32 insn = le32_to_cpu(*(u32 *)place);

    /* Calculate the relocation value. */
    sval = kpm_do_reloc(op, place, val);
    sval >>= lsb;
    /* Extract the value bits and shift them to bit 0. */
    imm_mask = (BIT(lsb + len) - 1) >> lsb;
    imm = sval & imm_mask;
    /* Update the instruction's immediate field. */
    insn = kpm_insn_encode_immediate(imm_type, insn, imm);
    *(u32 *)place = cpu_to_le32(insn);
    /*
	 * Extract the upper value bits (including the sign bit) and
	 * shift them to bit 0.
	 */
    sval = (s64)(sval & ~(imm_mask >> 1)) >> (len - 1);
    /*
	 * Overflow has occurred if the upper bits are not all equal to
	 * the sign bit of the value.
	 */
    if ((u64)(sval + 1) >= 2)
        return -ERANGE;

    return 0;
}

static int kpm_apply_relocate(Elf_Shdr *sechdrs, const char *strtab, unsigned int symindex, unsigned int relsec,
                              struct kpm_module *me)
{
    return 0;
}

static int kpm_apply_relocate_add(Elf_Shdr *sechdrs, const char *strtab, unsigned int symindex, unsigned int relsec,
                                  struct kpm_module *me)
{
    unsigned int i;
    int ovf;
    bool overflow_check;
    Elf_Sym *sym;
    void *loc;
    u64 val;
    Elf_Rela *rel = (void *)sechdrs[relsec].sh_addr;

    for (i = 0; i < sechdrs[relsec].sh_size / sizeof(*rel); i++) {
        /* loc corresponds to P in the AArch64 ELF document. */
        loc = (void *)sechdrs[sechdrs[relsec].sh_info].sh_addr + rel[i].r_offset;
        /* sym is the ELF symbol we're referring to. */
        sym = (Elf_Sym *)sechdrs[symindex].sh_addr + ELF64_R_SYM(rel[i].r_info);
        /* val corresponds to (S + A) in the AArch64 ELF document. */
        val = sym->st_value + rel[i].r_addend;

        overflow_check = true;

        /* Perform the static relocation. */
        switch (ELF64_R_TYPE(rel[i].r_info)) {
        /* Null relocations. */
        case R_ARM_NONE:
        case R_AARCH64_NONE:
            ovf = 0;
            break;
        /* Data relocations. */
        case R_AARCH64_ABS64:
            overflow_check = false;
            ovf = kpm_reloc_data(RELOC_OP_ABS, loc, val, 64);
            break;
        case R_AARCH64_ABS32:
            ovf = kpm_reloc_data(RELOC_OP_ABS, loc, val, 32);
            break;
        case R_AARCH64_ABS16:
            ovf = kpm_reloc_data(RELOC_OP_ABS, loc, val, 16);
            break;
        case R_AARCH64_PREL64:
            overflow_check = false;
            ovf = kpm_reloc_data(RELOC_OP_PREL, loc, val, 64);
            break;
        case R_AARCH64_PREL32:
            ovf = kpm_reloc_data(RELOC_OP_PREL, loc, val, 32);
            break;
        case R_AARCH64_PREL16:
            ovf = kpm_reloc_data(RELOC_OP_PREL, loc, val, 16);
            break;

        /* MOVW instruction relocations. */
        case R_AARCH64_MOVW_UABS_G0_NC:
            overflow_check = false;
            fallthrough;
        case R_AARCH64_MOVW_UABS_G0:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 0, KPM_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G1_NC:
            overflow_check = false;
            fallthrough;
        case R_AARCH64_MOVW_UABS_G1:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 16, KPM_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G2_NC:
            overflow_check = false;
            fallthrough;
        case R_AARCH64_MOVW_UABS_G2:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 32, KPM_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G3:
            /* We're using the top bits so we can't overflow. */
            overflow_check = false;
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 48, KPM_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_SABS_G0:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 0, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_SABS_G1:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 16, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_SABS_G2:
            ovf = kpm_reloc_insn_movw(RELOC_OP_ABS, loc, val, 32, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G0_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 0, KPM_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G0:
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 0, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G1_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 16, KPM_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G1:
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 16, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G2_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 32, KPM_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G2:
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 32, KPM_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G3:
            /* We're using the top bits so we can't overflow. */
            overflow_check = false;
            ovf = kpm_reloc_insn_movw(RELOC_OP_PREL, loc, val, 48, KPM_INSN_IMM_MOVNZ);
            break;
        /* Immediate instruction relocations. */
        case R_AARCH64_LD_PREL_LO19:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 19, KPM_INSN_IMM_19);
            break;
        case R_AARCH64_ADR_PREL_LO21:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PREL, loc, val, 0, 21, KPM_INSN_IMM_ADR);
            break;
        case R_AARCH64_ADR_PREL_PG_HI21_NC:
            overflow_check = false;
            fallthrough;
        case R_AARCH64_ADR_PREL_PG_HI21:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PAGE, loc, val, 12, 21, KPM_INSN_IMM_ADR);
            break;
        case R_AARCH64_ADD_ABS_LO12_NC:
        case R_AARCH64_LDST8_ABS_LO12_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_imm(RELOC_OP_ABS, loc, val, 0, 12, KPM_INSN_IMM_12);
            break;
        case R_AARCH64_LDST16_ABS_LO12_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_imm(RELOC_OP_ABS, loc, val, 1, 11, KPM_INSN_IMM_12);
            break;
        case R_AARCH64_LDST32_ABS_LO12_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_imm(RELOC_OP_ABS, loc, val, 2, 10, KPM_INSN_IMM_12);
            break;
        case R_AARCH64_LDST64_ABS_LO12_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_imm(RELOC_OP_ABS, loc, val, 3, 9, KPM_INSN_IMM_12);
            break;
        case R_AARCH64_LDST128_ABS_LO12_NC:
            overflow_check = false;
            ovf = kpm_reloc_insn_imm(RELOC_OP_ABS, loc, val, 4, 8, KPM_INSN_IMM_12);
            break;
        case R_AARCH64_TSTBR14:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 14, KPM_INSN_IMM_14);
            break;
        case R_AARCH64_CONDBR19:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 19, KPM_INSN_IMM_19);
            break;
        case R_AARCH64_JUMP26:
        case R_AARCH64_CALL26:
            ovf = kpm_reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 26, KPM_INSN_IMM_26);
            break;
        default:
            pr_err("kpm: unsupported RELA relocation: %llu\n", ELF64_R_TYPE(rel[i].r_info));
            return -ENOEXEC;
        }

        if (overflow_check && ovf == -ERANGE)
            goto overflow;
    }
    return 0;
overflow:
    pr_err("kpm: overflow in relocation type %d val %llx\n", (int)ELF64_R_TYPE(rel[i].r_info), val);
    return -ENOEXEC;
}

/* ===================== ELF 解析与加载 (KernelPatch module.c) ===================== */

static void set_load_error(struct kpm_load_info *info, const char *message)
{
    if (!info || !message)
        return;
    snprintf(info->info.error_msg, sizeof(info->info.error_msg), "%s", message);
}

static const char *load_error(const struct kpm_load_info *info, const char *fallback)
{
    if (info && info->info.error_msg[0])
        return info->info.error_msg;
    return fallback;
}

static bool kpm_load_result_enabled(void __user *reserved)
{
    struct kpm_load_result *result;

    if (!reserved)
        return false;

    result = memdup_user(reserved, sizeof(*result));
    if (!result || IS_ERR(result))
        return false;

    bool enabled = result->magic == KPM_LOAD_RESULT_MAGIC && result->size >= sizeof(*result);
    kvfree(result);
    return enabled;
}

static void kpm_set_load_result(void __user *reserved, long code, const char *message)
{
    struct kpm_load_result result;

    if (!kpm_load_result_enabled(reserved))
        return;

    memset(&result, 0, sizeof(result));
    result.magic = KPM_LOAD_RESULT_MAGIC;
    result.size = sizeof(result);
    result.code = code;
    if (message)
        snprintf(result.message, sizeof(result.message), "%s", message);
    (void)copy_to_user(reserved, &result, sizeof(result));
}

static char *kpm_next_string(char *string, unsigned long *secsize)
{
    while (string[0]) {
        string++;
        if ((*secsize)-- <= 1)
            return 0;
    }
    while (!string[0]) {
        string++;
        if ((*secsize)-- <= 1)
            return 0;
    }
    return string;
}

/* Update size with this section: return offset. */
static long kpm_get_offset(struct kpm_module *mod, unsigned int *size, Elf_Shdr *sechdr, unsigned int section)
{
    long ret = ALIGN(*size, sechdr->sh_addralign ?: 1);
    *size = ret + sechdr->sh_size;
    return ret;
}

static char *kpm_get_next_modinfo(const struct kpm_load_info *info, const char *tag, char *prev)
{
    char *p;
    unsigned int taglen = strlen(tag);
    Elf_Shdr *infosec = &info->sechdrs[info->index.info];
    unsigned long size = infosec->sh_size;
    char *modinfo = (char *)info->hdr + infosec->sh_offset;
    if (prev) {
        size -= prev - modinfo;
        modinfo = kpm_next_string(prev, &size);
    }
    for (p = modinfo; p; p = kpm_next_string(p, &size)) {
        if (strncmp(p, tag, taglen) == 0 && p[taglen] == '=')
            return p + taglen + 1;
    }
    return 0;
}

static char *kpm_get_modinfo(const struct kpm_load_info *info, const char *tag)
{
    return kpm_get_next_modinfo(info, tag, 0);
}

static int kpm_find_sec(const struct kpm_load_info *info, const char *name)
{
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        Elf_Shdr *shdr = &info->sechdrs[i];
        if ((shdr->sh_flags & SHF_ALLOC) && strcmp(info->secstrings + shdr->sh_name, name) == 0)
            return i;
    }
    return 0;
}

static void *kpm_get_sh_base(struct kpm_load_info *info, const char *secname)
{
    int idx = kpm_find_sec(info, secname);
    Elf_Shdr *infosec;
    void *addr;

    if (!idx)
        return 0;
    infosec = &info->sechdrs[idx];
    addr = (void *)info->hdr + infosec->sh_offset;
    return addr;
}

static unsigned long kpm_get_sh_size(struct kpm_load_info *info, const char *secname)
{
    int idx = kpm_find_sec(info, secname);
    Elf_Shdr *infosec;

    if (!idx)
        return 0;
    infosec = &info->sechdrs[idx];
    return infosec->sh_entsize;
}

static void kpm_layout_sections(struct kpm_module *mod, struct kpm_load_info *info)
{
    static unsigned long const masks[][2] = { /* NOTE: all executable code must be the first section in this array;
         * otherwise modify the text_size finder in the two loops below */
                                              { SHF_EXECINSTR | SHF_ALLOC, ARCH_SHF_SMALL },
                                              { SHF_ALLOC, SHF_WRITE | ARCH_SHF_SMALL },
                                              { SHF_WRITE | SHF_ALLOC, ARCH_SHF_SMALL },
                                              { ARCH_SHF_SMALL | SHF_ALLOC, 0 }
    };

    for (int i = 0; i < info->hdr->e_shnum; i++)
        info->sechdrs[i].sh_entsize = ~0UL;

    for (int m = 0; m < sizeof(masks) / sizeof(masks[0]); ++m) {
        for (int i = 0; i < info->hdr->e_shnum; ++i) {
            Elf_Shdr *s = &info->sechdrs[i];
            if ((s->sh_flags & masks[m][0]) != masks[m][0] || (s->sh_flags & masks[m][1]) || s->sh_entsize != ~0UL)
                continue;
            s->sh_entsize = kpm_get_offset(mod, &mod->size, s, i);
        }
        switch (m) {
        case 0: /* executable */
            mod->size = ALIGN(mod->size, PAGE_SIZE);
            mod->text_size = mod->size;
            break;
        case 1: /* RO: text and ro-data */
            mod->size = ALIGN(mod->size, PAGE_SIZE);
            mod->ro_size = mod->size;
            break;
        case 2:
            break;
        case 3: /* whole */
            mod->size = ALIGN(mod->size, PAGE_SIZE);
            break;
        }
    }
}

static bool kpm_is_core_symbol(const Elf_Sym *src, const Elf_Shdr *sechdrs, unsigned int shnum)
{
    const Elf_Shdr *sec;
    if (src->st_shndx == SHN_UNDEF || src->st_shndx >= shnum || !src->st_name)
        return false;
    sec = sechdrs + src->st_shndx;
    if (!(sec->sh_flags & SHF_ALLOC) || !(sec->sh_flags & SHF_EXECINSTR))
        return false;
    return true;
}

/* Change all symbols so that st_value encodes the pointer directly. */
static int kpm_simplify_symbols(struct kpm_module *mod, struct kpm_load_info *info)
{
    Elf_Shdr *symsec = &info->sechdrs[info->index.sym];
    Elf_Sym *sym = (void *)symsec->sh_addr;
    unsigned long secbase;
    unsigned int i;
    int ret = 0;

    for (i = 1; i < symsec->sh_size / sizeof(Elf_Sym); i++) {
        const char *name = info->strtab + sym[i].st_name;
        switch (sym[i].st_shndx) {
        case SHN_COMMON:
            if (!strncmp(name, "__gnu_lto", 9)) {
                pr_info("kpm: please compile with -fno-common\n");
                ret = -ENOEXEC;
            }
            break;
        case SHN_ABS:
            break;
        case SHN_UNDEF: {
            unsigned long addr = sukisu_compact_find_symbol(name);
            if (!addr) {
                pr_err("kpm: unknown symbol: %s\n", name);
                if (!info->info.error_msg[0])
                    snprintf(info->info.error_msg, sizeof(info->info.error_msg), "unknown symbol: %s", name);
                ret = -ENOENT;
                break;
            }
            sym[i].st_value = addr;
            break;
        }
        default:
            secbase = info->sechdrs[sym[i].st_shndx].sh_addr;
            sym[i].st_value += secbase;
            break;
        }
    }
    return ret;
}

static int kpm_apply_relocations(struct kpm_module *mod, const struct kpm_load_info *info)
{
    int rc = 0;
    unsigned int i;
    for (i = 1; i < info->hdr->e_shnum; i++) {
        unsigned int infosec = info->sechdrs[i].sh_info;
        if (infosec >= info->hdr->e_shnum)
            continue;
        if (!(info->sechdrs[infosec].sh_flags & SHF_ALLOC))
            continue;
        if (info->sechdrs[i].sh_type == SHT_REL) {
            rc = kpm_apply_relocate(info->sechdrs, info->strtab, info->index.sym, i, mod);
        } else if (info->sechdrs[i].sh_type == SHT_RELA) {
            rc = kpm_apply_relocate_add(info->sechdrs, info->strtab, info->index.sym, i, mod);
        }
        if (rc < 0)
            break;
    }
    return rc;
}

static void kpm_layout_symtab(struct kpm_module *mod, struct kpm_load_info *info)
{
    Elf_Shdr *symsect = info->sechdrs + info->index.sym;
    Elf_Shdr *strsect = info->sechdrs + info->index.str;
    const Elf_Sym *src;
    unsigned int i, nsrc, ndst, strtab_size = 0;

    /* Put symbol section at end of module. */
    symsect->sh_flags |= SHF_ALLOC;
    symsect->sh_entsize = kpm_get_offset(mod, &mod->size, symsect, info->index.sym);

    src = (void *)info->hdr + symsect->sh_offset;
    nsrc = symsect->sh_size / sizeof(*src);

    /* strtab always starts with a nul, so offset 0 is the empty string. */
    strtab_size = 1;
    /* Compute total space required for the core symbols' strtab. */
    for (ndst = i = 0; i < nsrc; i++) {
        if (i == 0 || kpm_is_core_symbol(src + i, info->sechdrs, info->hdr->e_shnum)) {
            strtab_size += strlen(&info->strtab[src[i].st_name]) + 1;
            ndst++;
        }
    }

    /* Append room for core symbols at end. */
    info->symoffs = ALIGN(mod->size, symsect->sh_addralign ?: 1);
    info->stroffs = mod->size = info->symoffs + ndst * sizeof(Elf_Sym);
    mod->size += strtab_size;

    /* Put string table section at end of module. */
    strsect->sh_flags |= SHF_ALLOC;
    strsect->sh_entsize = kpm_get_offset(mod, &mod->size, strsect, info->index.str);
}

static int kpm_rewrite_section_headers(struct kpm_load_info *info)
{
    info->sechdrs[0].sh_addr = 0;
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        Elf_Shdr *shdr = &info->sechdrs[i];
        if (shdr->sh_type != SHT_NOBITS && info->len < shdr->sh_offset + shdr->sh_size) {
            return -ENOEXEC;
        }
        /* Mark all sections sh_addr with their address in the temporary image. */
        shdr->sh_addr = (size_t)info->hdr + shdr->sh_offset;
    }
    return 0;
}

static int kpm_move_module(struct kpm_module *mod, struct kpm_load_info *info)
{
    mod->start = module_alloc(mod->size);
    if (!mod->start) {
        return -ENOMEM;
    }
    memset(mod->start, 0, mod->size);

    /* Transfer each section which specifies SHF_ALLOC */
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        void *dest;
        Elf_Shdr *shdr = &info->sechdrs[i];
        if (!(shdr->sh_flags & SHF_ALLOC))
            continue;

        dest = mod->start + shdr->sh_entsize;
        const char *sname = info->secstrings + shdr->sh_name;

        if (shdr->sh_type != SHT_NOBITS)
            memcpy(dest, (void *)shdr->sh_addr, shdr->sh_size);

        shdr->sh_addr = (unsigned long)dest;

        if (!mod->init && !strcmp(".kpm.init", sname))
            mod->init = (kpm_initcall_t *)dest;

        if (!strcmp(".kpm.ctl0", sname))
            mod->ctl0 = (kpm_ctl0call_t *)dest;
        if (!strcmp(".kpm.ctl1", sname))
            mod->ctl1 = (kpm_ctl1call_t *)dest;

        if (!mod->exit && !strcmp(".kpm.exit", sname))
            mod->exit = (kpm_exitcall_t *)dest;
        if (!mod->event && !strcmp(".kpm.event", sname))
            mod->event = (kpm_eventcall_t *)dest;

        if (!mod->info.base && !strcmp(".kpm.info", sname))
            mod->info.base = (const char *)dest;
    }
    mod->info.name = info->info.name - info->info.base + mod->info.base;
    mod->info.version = info->info.version - info->info.base + mod->info.base;

    if (info->info.license)
        mod->info.license = info->info.license - info->info.base + mod->info.base;
    if (info->info.author)
        mod->info.author = info->info.author - info->info.base + mod->info.base;
    if (info->info.description)
        mod->info.description = info->info.description - info->info.base + mod->info.base;

    return 0;
}

static int kpm_setup_load_info(struct kpm_load_info *info)
{
    int rc = 0;
    info->sechdrs = (void *)info->hdr + info->hdr->e_shoff;
    info->secstrings = (void *)info->hdr + info->sechdrs[info->hdr->e_shstrndx].sh_offset;

    if ((rc = kpm_rewrite_section_headers(info))) {
        pr_err("kpm: rewrite section error\n");
        set_load_error(info, "rewrite section headers failed");
        return rc;
    }

    if (!kpm_find_sec(info, ".kpm.init") || !kpm_find_sec(info, ".kpm.exit")) {
        pr_err("kpm: no .kpm.init or .kpm.exit section\n");
        set_load_error(info, "no .kpm.init or .kpm.exit section");
        return -ENOEXEC;
    }

    info->index.info = kpm_find_sec(info, ".kpm.info");
    if (!info->index.info) {
        pr_err("kpm: no .kpm.info section\n");
        set_load_error(info, "no .kpm.info section");
        return -ENOEXEC;
    }
    info->info.base = kpm_get_sh_base(info, ".kpm.info");
    info->info.size = kpm_get_sh_size(info, ".kpm.info");

    const char *name = kpm_get_modinfo(info, "name");
    if (!name) {
        pr_err("kpm: module name not found\n");
        set_load_error(info, "module name not found");
        return -ENOEXEC;
    }
    info->info.name = name;
    pr_info("kpm: loading module name: %s\n", name);

    const char *version = kpm_get_modinfo(info, "version");
    if (!version) {
        pr_info("kpm: module version not found\n");
        set_load_error(info, "module version not found");
        return -ENOEXEC;
    }
    info->info.version = version;
    pr_info("kpm: version: %s\n", version);

    const char *license = kpm_get_modinfo(info, "license");
    info->info.license = license;
    pr_info("kpm: license: %s\n", license);

    const char *author = kpm_get_modinfo(info, "author");
    info->info.author = author;
    pr_info("kpm: author: %s\n", author);

    const char *description = kpm_get_modinfo(info, "description");
    info->info.description = description;
    pr_info("kpm: description: %s\n", description);

    for (int i = 1; i < info->hdr->e_shnum; i++) {
        if (info->sechdrs[i].sh_type == SHT_SYMTAB) {
            info->index.sym = i;
            info->index.str = info->sechdrs[i].sh_link;
            info->strtab = (char *)info->hdr + info->sechdrs[info->index.str].sh_offset;
            break;
        }
    }

    if (info->index.sym == 0) {
        pr_info("kpm: module has no symbols (stripped?)\n");
        set_load_error(info, "module has no symbols (stripped?)");
        return -ENOEXEC;
    }
    return 0;
}

static int kpm_elf_header_check(struct kpm_load_info *info)
{
    if (info->len <= sizeof(*(info->hdr))) {
        set_load_error(info, "ELF header is truncated");
        return -ENOEXEC;
    }
    if (memcmp(info->hdr->e_ident, ELFMAG, SELFMAG) || info->hdr->e_type != ET_REL || !elf_check_arch(info->hdr) ||
        info->hdr->e_shentsize != sizeof(Elf_Shdr)) {
        set_load_error(info, "ELF header is not a supported AArch64 relocatable module");
        return -ENOEXEC;
    }
    if (info->hdr->e_shoff >= info->len || (info->hdr->e_shnum * sizeof(Elf_Shdr) > info->len - info->hdr->e_shoff)) {
        set_load_error(info, "ELF section headers are invalid");
        return -ENOEXEC;
    }
    return 0;
}

static struct kpm_module *kpm_find_module(const char *name);

static long kpm_load_module_ex(const void *data, int len, const char *args, const char *event, const char *source,
                               void *__user reserved)
{
    struct kpm_load_info load_info = { .len = len, .hdr = data };
    struct kpm_load_info *info = &load_info;
    struct kpm_module *mod = NULL;
    long rc = 0;

    if ((rc = kpm_elf_header_check(info)))
        goto out;
    if ((rc = kpm_setup_load_info(info)))
        goto out;

    mod = (struct kpm_module *)vmalloc(sizeof(struct kpm_module));
    if (!mod) {
        set_load_error(info, "allocate module state failed");
        rc = -ENOMEM;
        goto out;
    }
    memset(mod, 0, sizeof(struct kpm_module));
    snprintf(mod->load_event, sizeof(mod->load_event), "%s", event ? event : "");
    snprintf(mod->load_source, sizeof(mod->load_source), "%s", source ? source : "embedded");

    if (args) {
        mod->args = vmalloc(strlen(args) + 1);
        if (!mod->args) {
            set_load_error(info, "allocate module args failed");
            rc = -ENOMEM;
            goto free1;
        }
        strcpy(mod->args, args);
    }

    kpm_layout_sections(mod, info);
    kpm_layout_symtab(mod, info);

    if ((rc = kpm_move_module(mod, info))) {
        set_load_error(info, "allocate executable module memory failed");
        goto free;
    }
    if ((rc = kpm_simplify_symbols(mod, info)))
        goto free;
    if ((rc = kpm_apply_relocations(mod, info))) {
        set_load_error(info, "apply relocations failed");
        goto free;
    }

    flush_icache_range((unsigned long)mod->start, (unsigned long)mod->start + mod->size);

    /* 防御: 空 .kpm.init/.kpm.exit 段禁止调用空函数指针 */
    if (!mod->init || !*mod->init) {
        set_load_error(info, "no kpm init call");
        rc = -ENOEXEC;
        goto free;
    }
    if (!mod->exit || !*mod->exit) {
        set_load_error(info, "no kpm exit call");
        rc = -ENOEXEC;
        goto free;
    }

    rc = (*mod->init)(mod->args, event, reserved);

    if (!rc) {
        mutex_lock(&kpm_lock);
        if (kpm_find_module(mod->info.name)) {
            pr_info("kpm: %s already exists\n", mod->info.name);
            set_load_error(info, "module already exists");
            rc = -EEXIST;
            mutex_unlock(&kpm_lock);
            (*mod->exit)(reserved);
        } else {
            list_add_tail(&mod->list, &kpm_modules);
            mutex_unlock(&kpm_lock);
            pr_info("kpm: [%s] loaded with [%s]\n", mod->info.name, args ? args : "");
            goto out;
        }
    } else {
        set_load_error(info, "module init failed");
        pr_info("kpm: [%s] init failed with [%s] error: %ld, try exit ...\n", mod->info.name, args ? args : "", rc);
        (*mod->exit)(reserved);
    }

free:
    if (mod->args)
        kvfree(mod->args);
    if (mod->start)
        module_memfree(mod->start);
free1:
    kvfree(mod);
out:
    kpm_set_load_result(reserved, rc, rc ? load_error(info, "load module failed") : "module loaded");
    return rc;
}

static long kpm_load_module_path(const char *path, const char *args, void *__user reserved)
{
    struct file *filp = NULL;
    loff_t len;
    loff_t pos = 0;
    void *data = NULL;
    long rc = 0;

    pr_info("kpm: load module path: %s\n", path);
    if (!path) {
        rc = -EINVAL;
        kpm_set_load_result(reserved, rc, "module path is null");
        goto out;
    }

    filp = filp_open(path, O_RDONLY, 0);
    if (unlikely(!filp || IS_ERR(filp))) {
        pr_err("kpm: open module: %s error\n", path);
        rc = PTR_ERR(filp);
        filp = NULL;
        kpm_set_load_result(reserved, rc, "open module file failed");
        goto out;
    }
    len = vfs_llseek(filp, 0, SEEK_END);
    pr_info("kpm: module size: %lld\n", len);
    if (len <= 0) {
        rc = -EIO;
        kpm_set_load_result(reserved, rc, "invalid module file size");
        goto close;
    }
    vfs_llseek(filp, 0, SEEK_SET);

    data = vmalloc(len);
    if (!data) {
        rc = -ENOMEM;
        kpm_set_load_result(reserved, rc, "allocate module file buffer failed");
        goto close;
    }
    memset(data, 0, len);

    kernel_read(filp, data, len, &pos);
    filp_close(filp, NULL);
    filp = NULL;

    if (pos != len) {
        pr_err("kpm: read module: %s error\n", path);
        rc = -EIO;
        kpm_set_load_result(reserved, rc, "read module file failed");
        goto free;
    }

    rc = kpm_load_module_ex(data, len, args, "load-file", "file", reserved);
free:
    kvfree(data);
close:
    if (filp)
        filp_close(filp, NULL);
out:
    return rc;
}

static struct kpm_module *kpm_find_module(const char *name)
{
    struct kpm_module *pos;
    list_for_each_entry (pos, &kpm_modules, list) {
        if (!strcmp(name, pos->info.name)) {
            return pos;
        }
    }
    return NULL;
}

static int kpm_get_module_nums(void)
{
    struct kpm_module *pos;
    int n = 0;

    mutex_lock(&kpm_lock);
    list_for_each_entry (pos, &kpm_modules, list) {
        n++;
    }
    mutex_unlock(&kpm_lock);

    return n;
}

static int kpm_list_modules(char *out_names, int size)
{
    struct kpm_module *pos;
    int off = 0;

    if (!out_names || size <= 0)
        return -EINVAL;
    out_names[0] = '\0';

    mutex_lock(&kpm_lock);
    list_for_each_entry (pos, &kpm_modules, list) {
        if (off >= size - 1)
            break;
        off += snprintf(out_names + off, size - 1 - off, "%s\n", pos->info.name);
    }
    if (off > 0)
        out_names[off - 1] = '\0';
    mutex_unlock(&kpm_lock);

    return off;
}

static int kpm_get_module_info(const char *name, char *out_info, int size)
{
    struct kpm_module *mod;
    int sz, tail;

    if (size <= 0)
        return 0;

    mutex_lock(&kpm_lock);
    mod = kpm_find_module(name);
    if (!mod) {
        mutex_unlock(&kpm_lock);
        return -ENOENT;
    }

    sz = snprintf(out_info, size,
                  "name=%s\n"
                  "version=%s\n"
                  "license=%s\n"
                  "author=%s\n"
                  "description=%s\n"
                  "args=%s\n",
                  mod->info.name, mod->info.version, mod->info.license, mod->info.author, mod->info.description,
                  mod->args ? mod->args : "");

    if (sz < 0)
        sz = 0;
    if (sz < size) {
        tail = snprintf(out_info + sz, size - sz,
                        "load_event=%s\n"
                        "load_source=%s\n",
                        mod->load_event, mod->load_source);
        if (tail > 0)
            sz += tail;
    }

    out_info[size - 1] = '\0';

    mutex_unlock(&kpm_lock);
    return sz;
}

static long kpm_module_control0(const char *name, const char *ctl_args, char *__user out_msg, int outlen)
{
    struct kpm_module *mod;
    long rc;
    int args_len;

    if (!name || !ctl_args)
        return -EINVAL;
    args_len = strlen(ctl_args);
    if (args_len <= 0)
        return -EINVAL;

    pr_info("kpm: control name %s, args: %s\n", name, ctl_args);

    mutex_lock(&kpm_lock);
    mod = kpm_find_module(name);
    if (!mod) {
        mutex_unlock(&kpm_lock);
        rc = -ENOENT;
        goto out;
    }

    if (!mod->ctl0 || !*mod->ctl0) {
        pr_info("kpm: no ctl0\n");
        mutex_unlock(&kpm_lock);
        rc = -ENOSYS;
        goto out;
    }

    if (mod->ctl_args)
        kvfree(mod->ctl_args);

    mod->ctl_args = vmalloc(args_len + 1);
    if (!mod->ctl_args) {
        mutex_unlock(&kpm_lock);
        rc = -ENOMEM;
        goto out;
    }

    strcpy(mod->ctl_args, ctl_args);

    rc = (*mod->ctl0)(mod->ctl_args, out_msg, outlen);
    mutex_unlock(&kpm_lock);

    pr_info("kpm: control %s rc: %ld\n", name, rc);
out:
    return rc;
}

static long kpm_unload_module(const char *name, void *__user reserved)
{
    struct kpm_module *mod;
    long rc = 0;

    if (!name)
        return -EINVAL;
    pr_info("kpm: unload name: %s\n", name);

    mutex_lock(&kpm_lock);
    mod = kpm_find_module(name);
    if (!mod) {
        mutex_unlock(&kpm_lock);
        rc = -ENOENT;
        goto out;
    }
    list_del(&mod->list);
    mutex_unlock(&kpm_lock);

    rc = (*mod->exit)(reserved);

    if (mod->args)
        kvfree(mod->args);
    if (mod->ctl_args)
        kvfree(mod->ctl_args);

    module_memfree(mod->start);
    kvfree(mod);

    pr_info("kpm: unloaded %s, rc: %ld\n", name, rc);

out:
    return rc;
}

/* ===================== KSU ioctl 入口 (协议不变) ===================== */

#ifndef NO_OPTIMIZE
#if defined(__GNUC__) && !defined(__clang__)
#define NO_OPTIMIZE __attribute__((optimize("O0")))
#elif defined(__clang__)
#define NO_OPTIMIZE __attribute__((optnone))
#else
#define NO_OPTIMIZE
#endif
#endif

noinline NO_OPTIMIZE void sukisu_kpm_load_module_path(const char *path, const char *args, void *ptr, int *result)
{
    long rc;

    rc = kpm_load_module_path(path, args, (void __user *)ptr);
    if (result)
        *result = (int)rc;
}
EXPORT_SYMBOL(sukisu_kpm_load_module_path);

noinline NO_OPTIMIZE void sukisu_kpm_unload_module(const char *name, void *ptr, int *result)
{
    long rc;

    rc = kpm_unload_module(name, (void __user *)ptr);
    if (result)
        *result = (int)rc;
}
EXPORT_SYMBOL(sukisu_kpm_unload_module);

noinline NO_OPTIMIZE void sukisu_kpm_num(int *result)
{
    if (result)
        *result = kpm_get_module_nums();
}
EXPORT_SYMBOL(sukisu_kpm_num);

noinline NO_OPTIMIZE void sukisu_kpm_info(const char *name, char *buf, int bufferSize, int *size)
{
    if (size)
        *size = kpm_get_module_info(name, buf, bufferSize);
}
EXPORT_SYMBOL(sukisu_kpm_info);

noinline NO_OPTIMIZE void sukisu_kpm_list(void *out, int bufferSize, int *result)
{
    if (result)
        *result = kpm_list_modules(out, bufferSize);
}
EXPORT_SYMBOL(sukisu_kpm_list);

noinline NO_OPTIMIZE void sukisu_kpm_control(const char *name, const char *args, long arg_len, int *result)
{
    long rc;

    rc = kpm_module_control0(name, arg_len > 0 ? args : NULL, NULL, 0);
    if (result)
        *result = (int)rc;
}
EXPORT_SYMBOL(sukisu_kpm_control);

noinline NO_OPTIMIZE void sukisu_kpm_version(char *buf, int bufferSize)
{
    if (buf && bufferSize > 0)
        snprintf(buf, bufferSize, "KernelPatch %s", KPM_LOADER_VERSION);
}
EXPORT_SYMBOL(sukisu_kpm_version);

noinline int sukisu_handle_kpm(unsigned long control_code, unsigned long arg1, unsigned long arg2,
                               unsigned long result_code)
{
    int res = -1;
    if (control_code == SUKISU_KPM_LOAD) {
        char kernel_load_path[256] = { 0 };
        char kernel_args_buffer[256] = { 0 };

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok(arg1, 255)) {
            goto invalid_arg;
        }

        if (strncpy_from_user((char *)&kernel_load_path, (const char __user *)arg1, 255) <= 0) {
            res = -EINVAL;
            goto exit;
        }

        if (arg2 != 0) {
            if (!access_ok(arg2, 255)) {
                goto invalid_arg;
            }

            strncpy_from_user((char *)&kernel_args_buffer, (const char __user *)arg2, 255);
        }

        sukisu_kpm_load_module_path((const char *)&kernel_load_path, (const char *)&kernel_args_buffer, NULL, &res);
    } else if (control_code == SUKISU_KPM_UNLOAD) {
        char kernel_name_buffer[256] = { 0 };

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok(arg1, sizeof(kernel_name_buffer))) {
            goto invalid_arg;
        }

        strncpy_from_user((char *)&kernel_name_buffer, (const char __user *)arg1, sizeof(kernel_name_buffer));

        sukisu_kpm_unload_module((const char *)&kernel_name_buffer, NULL, &res);
    } else if (control_code == SUKISU_KPM_NUM) {
        sukisu_kpm_num(&res);
    } else if (control_code == SUKISU_KPM_INFO) {
        char kernel_name_buffer[256] = { 0 };
        char buf[256] = { 0 };
        int size;

        if (arg1 == 0 || arg2 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok(arg1, sizeof(kernel_name_buffer))) {
            goto invalid_arg;
        }

        strncpy_from_user((char *)&kernel_name_buffer, (const char __user *)arg1, sizeof(kernel_name_buffer));

        sukisu_kpm_info((const char *)&kernel_name_buffer, (char *)&buf, sizeof(buf), &size);

        if (size < 0) {
            res = size;
            goto exit;
        }

        if (!access_ok(arg2, size)) {
            goto invalid_arg;
        }

        res = copy_to_user(arg2, &buf, size);

    } else if (control_code == SUKISU_KPM_LIST) {
        char buf[1024] = { 0 };
        int len = (int)arg2;

        if (len <= 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok(arg2, len)) {
            goto invalid_arg;
        }

        sukisu_kpm_list((char *)&buf, sizeof(buf), &res);

        if (res < 0) {
            goto exit;
        }

        if (res > len) {
            res = -ENOBUFS;
            goto exit;
        }

        if (res > 0 && copy_to_user(arg1, &buf, res) != 0)
            pr_info("kpm: Copy to user failed.");

    } else if (control_code == SUKISU_KPM_CONTROL) {
        char kpm_name[KPM_NAME_LEN] = { 0 };
        char kpm_args[KPM_ARGS_LEN] = { 0 };

        if (!access_ok(arg1, sizeof(kpm_name))) {
            goto invalid_arg;
        }

        if (!access_ok(arg2, sizeof(kpm_args))) {
            goto invalid_arg;
        }

        long name_len = strncpy_from_user((char *)&kpm_name, (const char __user *)arg1, sizeof(kpm_name));
        if (name_len <= 0) {
            res = -EINVAL;
            goto exit;
        }

        long arg_len = strncpy_from_user((char *)&kpm_args, (const char __user *)arg2, sizeof(kpm_args));

        sukisu_kpm_control((const char *)&kpm_name, (const char *)&kpm_args, arg_len, &res);

    } else if (control_code == SUKISU_KPM_VERSION) {
        char buffer[256] = { 0 };

        sukisu_kpm_version((char *)&buffer, sizeof(buffer));

        unsigned int outlen = (unsigned int)arg2;
        int len = strlen(buffer);
        if (len >= outlen)
            len = outlen - 1;

        res = copy_to_user(arg1, &buffer, len + 1);
    }

exit:
    if (copy_to_user(result_code, &res, sizeof(res)) != 0)
        pr_info("kpm: Copy to user failed.");

    return 0;
invalid_arg:
    pr_err("kpm: invalid pointer detected! arg1: %px arg2: %px\n", (void *)arg1, (void *)arg2);
    res = -EFAULT;
    goto exit;
}
EXPORT_SYMBOL(sukisu_handle_kpm);

int sukisu_is_kpm_control_code(unsigned long control_code)
{
    return (control_code >= CMD_KPM_CONTROL && control_code <= CMD_KPM_CONTROL_MAX) ? 1 : 0;
}

int do_kpm(void __user *arg)
{
    struct ksu_kpm_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("kpm: copy_from_user failed\n");
        return -EFAULT;
    }

    if (!access_ok(cmd.control_code, sizeof(int))) {
        pr_err("kpm: invalid control_code pointer %px\n", (void *)cmd.control_code);
        return -EFAULT;
    }

    if (!access_ok(cmd.result_code, sizeof(int))) {
        pr_err("kpm: invalid result_code pointer %px\n", (void *)cmd.result_code);
        return -EFAULT;
    }

    return sukisu_handle_kpm(cmd.control_code, cmd.arg1, cmd.arg2, cmd.result_code);
}
