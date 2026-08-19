#include <linux/export.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>
#include <linux/vmalloc.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/vmalloc.h>
#include <linux/set_memory.h>
#include <linux/version.h>
#include <linux/export.h>
#include <linux/slab.h>
#include <linux/path.h>
#include <linux/dcache.h>
#include "infra/symbol_resolver.h"
#include "kpm.h"
#include "compact.h"
#include "policy/allowlist.h"
#include "manager/manager_identity.h"

#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/uidgid.h>
#include <linux/user_namespace.h>
#include <linux/random.h>
#include <linux/ptrace.h>
#include <linux/errno.h>
#include <linux/err.h>
#include <linux/thread_info.h>
#include <asm/syscall.h>
#include <asm/unistd.h>
#include <asm/processor.h>

static int sukisu_is_su_allow_uid(uid_t uid)
{
    return ksu_is_allow_uid_for_current(uid) ? 1 : 0;
}

static int sukisu_get_ap_mod_exclude(uid_t uid)
{
    return 0; /* Not supported */
}

static int sukisu_is_uid_should_umount(uid_t uid)
{
    return ksu_uid_should_umount(uid) ? 1 : 0;
}

static int sukisu_is_current_uid_manager(void)
{
    return is_manager();
}

static uid_t sukisu_get_manager_uid(void)
{
    return ksu_last_manager_appid;
}

static void sukisu_set_manager_uid(uid_t uid, int force)
{
    if (force || ksu_last_manager_appid == KSU_INVALID_APPID)
        ksu_last_manager_appid = uid;
}

struct CompactAddressSymbol {
    const char *symbol_name;
    void *addr;
};

unsigned long sukisu_compact_find_symbol(const char *name);

/* KernelPatch 兼容符号别名: KPM 模块引用的 KernelPatch API 映射到本地等价物 */
static u32 kpm_kpver = 0x000D0505; /* KernelPatch 0.13.5 (5591b8e) */

static long kpm_compat_copy_to_user(void __user *to, const void *from, unsigned long n)
{
    /* KernelPatch 语义: 返回成功复制的字节数 (n - 未复制数); 内核 copy_to_user
     * 返回的是未复制字节数, 直接透传会让 KPM 把成功误判为失败 */
    return n - copy_to_user(to, from, n);
}

static long kpm_compat_copy_from_user(void *to, const void __user *from, unsigned long n)
{
    return copy_from_user(to, from, n);
}

/* KernelPatch 的 kf_* 是"函数指针变量"(KPM 代码经宏展开成 (*kf_xxx)(...)),
 * 因此必须映射到变量地址而非函数地址 */
static char *kpm_strlcpy_wrap(char *d, const char *s, size_t n)
{
    return strlcpy(d, s, n);
}
static char *kpm_strscpy_wrap(char *d, const char *s, size_t n)
{
    return strscpy(d, s, n);
}
static void *kpm_memscan_wrap(void *a, int c, size_t n)
{
    return memscan(a, c, n);
}
static char *kpm_d_path_wrap(const struct path *p, char *b, int l)
{
    return d_path(p, b, l);
}

static char *(*kf_strcpy)(char *, const char *) = strcpy;
static char *(*kf_strncpy)(char *, const char *, size_t) = strncpy;
static char *(*kf_strlcpy)(char *, const char *, size_t) = kpm_strlcpy_wrap;
static char *(*kf_strscpy)(char *, const char *, size_t) = kpm_strscpy_wrap;
static char *(*kf_stpcpy)(char *, const char *) = stpcpy;
static char *(*kf_strcat)(char *, const char *) = strcat;
static char *(*kf_strncat)(char *, const char *, size_t) = strncat;
static size_t (*kf_strlcat)(char *, const char *, size_t) = strlcat;
static size_t (*kf_strlen)(const char *) = strlen;
static size_t (*kf_strnlen)(const char *, size_t) = strnlen;
static int (*kf_strcmp)(const char *, const char *) = strcmp;
static int (*kf_strncmp)(const char *, const char *, size_t) = strncmp;
static int (*kf_strcasecmp)(const char *, const char *) = strcasecmp;
static int (*kf_strncasecmp)(const char *, const char *, size_t) = strncasecmp;
static char *(*kf_strchr)(const char *, int) = strchr;
static char *(*kf_strrchr)(const char *, int) = strrchr;
static char *(*kf_strchrnul)(const char *, int) = strchrnul;
static char *(*kf_strnchr)(const char *, size_t, int) = strnchr;
static char *(*kf_strstr)(const char *, const char *) = strstr;
static char *(*kf_strnstr)(const char *, const char *, size_t) = strnstr;
static char *(*kf_strsep)(char **, const char *) = strsep;
static char *(*kf_strpbrk)(const char *, const char *) = strpbrk;
static size_t (*kf_strcspn)(const char *, const char *) = strcspn;
static size_t (*kf_strspn)(const char *, const char *) = strspn;
static char *(*kf_strreplace)(char *, char, char) = strreplace;
static char *(*kf_strim)(char *) = strim;
static char *(*kf_skip_spaces)(const char *) = skip_spaces;
static void *(*kf_memcpy)(void *, const void *, size_t) = memcpy;
static void *(*kf_memmove)(void *, const void *, size_t) = memmove;
static void *(*kf_memset)(void *, int, size_t) = memset;
static int (*kf_memcmp)(const void *, const void *, size_t) = memcmp;
static void *(*kf_memchr)(const void *, int, size_t) = memchr;
static void *(*kf_memchr_inv)(const void *, int, size_t) = memchr_inv;
static void *(*kf_memscan)(void *, int, size_t) = kpm_memscan_wrap;
static void *(*kf_memset16)(u16 *, u16, size_t) = memset16;
static void *(*kf_memset32)(u32 *, u32, size_t) = memset32;
static void *(*kf_memset64)(u64 *, u64, size_t) = memset64;
static int (*kf_snprintf)(char *, size_t, const char *, ...) = snprintf;
static int (*kf_vsnprintf)(char *, size_t, const char *, va_list) = vsnprintf;
static int (*kf_scnprintf)(char *, size_t, const char *, ...) = scnprintf;
static int (*kf_vscnprintf)(char *, size_t, const char *, va_list) = vscnprintf;
static int (*kf_sprintf)(char *, const char *, ...) = sprintf;
static int (*kf_vsprintf)(char *, const char *, va_list) = vsprintf;
static int (*kf_sscanf)(const char *, const char *, ...) = sscanf;
static int (*kf_vsscanf)(const char *, const char *, va_list) = vsscanf;
static long (*kf_strnlen_user)(const char __user *, long) = strnlen_user;
static int (*kf_kstrtoll)(const char *, unsigned int, long long *) = kstrtoll;
static int (*kf_kstrtoull)(const char *, unsigned int, unsigned long long *) = kstrtoull;
static char *(*kf_kasprintf)(gfp_t, const char *, ...) = kasprintf;
static char *(*kf_kvasprintf)(gfp_t, const char *, va_list) = kvasprintf;
static int (*kf_match_string)(const char *const *, size_t, const char *) = match_string;
static bool (*kf_sysfs_streq)(const char *, const char *) = sysfs_streq;
static char *(*kf_d_path)(const struct path *, char *, int) = kpm_d_path_wrap;
static unsigned long (*kf_kallsyms_lookup_name)(const char *) = kallsyms_lookup_name;

/* KernelPatch 模块侧把这些名字声明为"函数指针变量" (extern int (*printk)(...)),
 * KPM 代码是 `ldr 指针` 后再调用 —— 必须映射到变量地址, 不能直接给函数地址 */
static int (*kpm_printk_fn)(const char *fmt, ...) = _printk;
static unsigned long (*kpm_kallsyms_lookup_name_fn)(const char *name) = kallsyms_lookup_name;
static int (*kpm_kallsyms_on_each_symbol_fn)(int (*fn)(void *, const char *, unsigned long),
                                             void *data) = kallsyms_on_each_symbol;

/* kf_* 补齐: KernelPatch libs.c 还导出了以下函数指针变量 (KPM 经 kfunc() 宏
 * 展开为 (*kf_xxx)(...) 调用, 必须映射到变量地址)。6.6 内核已无
 * strncpy_from_unsafe_user/strnlen_unsafe_user, 保留 NULL 以防重定位找不到符号 */
static ssize_t (*kf_strscpy_pad)(char *, const char *, size_t) = strscpy_pad;
static char *(*kf_strnchrnul)(const char *, size_t, int) = strnchrnul;
static int (*kf_bcmp)(const void *, const void *, size_t) = bcmp;
static void (*kf_fortify_panic)(const char *name) = fortify_panic;
static int (*kf___sysfs_match_string)(const char *const *array, size_t n, const char *str) = __sysfs_match_string;
static char **(*kf_argv_split)(gfp_t, const char *, int *) = argv_split;
static void (*kf_argv_free)(char **argv) = argv_free;
static void (*kf_dump_stack_lvl)(const char *log_lvl) = dump_stack_lvl;
static void (*kf_dump_stack)(void) = dump_stack;
static long (*kf_strncpy_from_user_nofault)(char *, const void __user *, long) = strncpy_from_user_nofault;
static long (*kf_strnlen_user_nofault)(const void __user *, long) = strnlen_user_nofault;
static long (*kf_strncpy_from_unsafe_user)(char *, const void __user *, long) = NULL;
static long (*kf_strnlen_unsafe_user)(const void __user *, long) = NULL;
static struct file *(*kf_fget)(unsigned int fd) = fget;
static void (*kf_fput)(struct file *file) = fput;

/* ============================================================================
 * KernelPatch 0.13.5 兼容符号补齐
 *
 * 使 KernelPatch 生态的 .kpm 模块能在 ReSukiSU 上完成 ELF 重定位。实现分四类:
 *   1. 真实等价: 6.6 内核已有标准实现, 直接包装
 *      (current_uid / _task_pt_reg / get_random_u64 / copy_to_user_stack /
 *       branch_absolute / branch_relative / ret_absolute / raw_syscall* /
 *       kf_* 函数指针变量等)
 *   2. 兼容变量: 提供可寻址的同名变量, 值指向内核真实对象
 *      (sys_call_table / compat_sys_call_table / has_config_compat / kver /
 *       thread_size / 结构体偏移表等)
 *   3. 仅解析地址: 依赖 KernelPatch 特有运行时 (hotpatch / kstorage /
 *      Android daemon), 无法在 ReSukiSU 上真实实现, 提供安全占位:
 *      函数返回 -EOPNOTSUPP/空操作, 变量置 0/NULL —— 保证加载不报
 *      "unknown symbol", 调用不崩溃
 *   4. 纯数据: syscall_name_table / kp_feature_list 等, 提供最小静态对象
 *
 * 安全约束: 所有函数指针都有完整类型声明; 禁止用 kallsyms_lookup_name
 * 猜测签名调用未知函数; 改动仅限 kernel/kpm/ 目录。
 * ========================================================================== */

/* ---- KernelPatch 结构体偏移表布局 (与 KernelPatch 0.13.5 模块侧头文件一致) ---- */
struct kpm_task_struct_offset {
    int16_t pid_offset;
    int16_t tgid_offset;
    int16_t thread_pid_offset;
    int16_t ptracer_cred_offset;
    int16_t real_cred_offset;
    int16_t cred_offset;
    int16_t comm_offset;
    int16_t fs_offset;
    int16_t files_offset;
    int16_t loginuid_offset;
    int16_t sessionid_offset;
    int16_t seccomp_offset;
    int16_t security_offset;
    int16_t stack_offset;
    int16_t tasks_offset;
    int16_t mm_offset;
    int16_t active_mm_offset;
};

struct kpm_cred_offset {
    int16_t usage_offset;
    int16_t subscribers_offset;
    int16_t magic_offset;
    int16_t uid_offset;
    int16_t gid_offset;
    int16_t suid_offset;
    int16_t sgid_offset;
    int16_t euid_offset;
    int16_t egid_offset;
    int16_t fsuid_offset;
    int16_t fsgid_offset;
    int16_t securebits_offset;
    int16_t cap_inheritable_offset;
    int16_t cap_permitted_offset;
    int16_t cap_effective_offset;
    int16_t cap_bset_offset;
    int16_t cap_ambient_offset;
    int16_t user_offset;
    int16_t user_ns_offset;
    int16_t ucounts_offset;
    int16_t group_info_offset;
    int16_t session_keyring_offset;
    int16_t process_keyring_offset;
    int16_t thread_keyring_offset;
    int16_t request_key_auth_offset;
    int16_t security_offset;
    int16_t rcu_offset;
};

struct kpm_mm_struct_offset {
    int16_t mmap_base_offset;
    int16_t task_size_offset;
    int16_t pgd_offset;
    int16_t map_count_offset;
    int16_t total_vm_offset;
    int16_t locked_vm_offset;
    int16_t pinned_vm_offset;
    int16_t data_vm_offset;
    int16_t exec_vm_offset;
    int16_t stack_vm_offset;
    int16_t start_code_offset, end_code_offset, start_data_offset, end_data_offset;
    int16_t start_brk_offset, brk_offset, start_stack_offset;
    int16_t arg_start_offset, arg_end_offset, env_start_offset, env_end_offset;
};

/* ---- 兼容变量 (KPM 模块引用的是变量地址, 值指向内核真实对象或占位) ---- */
static uintptr_t *kpm_sys_call_table = (uintptr_t *)sys_call_table;
#ifdef CONFIG_COMPAT
static uintptr_t *kpm_compat_sys_call_table = (uintptr_t *)compat_sys_call_table;
#else
static uintptr_t *kpm_compat_sys_call_table = NULL;
#endif
static int kpm_has_syscall_wrapper = 1; /* arm64 6.6: __arm64_sys_* 收 const struct pt_regs* */
static int kpm_has_config_compat = IS_ENABLED(CONFIG_COMPAT);
static u32 kpm_kver = LINUX_VERSION_CODE;
static int kpm_endian = 0; /* little endian */
static void *kpm_patch_config = NULL;
static void *kpm_setup_header = NULL;
static int kpm_task_ext_size = 0;
static int kpm_thread_size = THREAD_SIZE;
static int kpm_thread_info_in_task = IS_ENABLED(CONFIG_THREAD_INFO_IN_TASK);
static int kpm_sp_el0_is_current = IS_ENABLED(CONFIG_THREAD_INFO_IN_TASK);
static int kpm_sp_el0_is_thread_info = 0;
static int kpm_task_in_thread_info_offset = -1;
static int kpm_stack_in_task_offset = offsetof(struct task_struct, stack);
static int kpm_stack_end_offset = 0;
static int kpm_android_is_safe_mode = 0;

static struct kpm_task_struct_offset kpm_task_struct_offset = {
    .pid_offset = offsetof(struct task_struct, pid),
    .tgid_offset = offsetof(struct task_struct, tgid),
    .thread_pid_offset = offsetof(struct task_struct, thread_pid),
    .ptracer_cred_offset = offsetof(struct task_struct, ptracer_cred),
    .real_cred_offset = offsetof(struct task_struct, real_cred),
    .cred_offset = offsetof(struct task_struct, cred),
    .comm_offset = offsetof(struct task_struct, comm),
    .fs_offset = offsetof(struct task_struct, fs),
    .files_offset = offsetof(struct task_struct, files),
    .loginuid_offset = offsetof(struct task_struct, loginuid),
    .sessionid_offset = offsetof(struct task_struct, sessionid),
    .seccomp_offset = offsetof(struct task_struct, seccomp),
    .security_offset = offsetof(struct task_struct, security),
    .stack_offset = offsetof(struct task_struct, stack),
    .tasks_offset = offsetof(struct task_struct, tasks),
    .mm_offset = offsetof(struct task_struct, mm),
    .active_mm_offset = offsetof(struct task_struct, active_mm),
};

static struct kpm_cred_offset kpm_cred_offset = {
    .usage_offset = offsetof(struct cred, usage),
    .subscribers_offset = -1,
    .magic_offset = -1,
    .uid_offset = offsetof(struct cred, uid),
    .gid_offset = offsetof(struct cred, gid),
    .suid_offset = offsetof(struct cred, suid),
    .sgid_offset = offsetof(struct cred, sgid),
    .euid_offset = offsetof(struct cred, euid),
    .egid_offset = offsetof(struct cred, egid),
    .fsuid_offset = offsetof(struct cred, fsuid),
    .fsgid_offset = offsetof(struct cred, fsgid),
    .securebits_offset = offsetof(struct cred, securebits),
    .cap_inheritable_offset = offsetof(struct cred, cap_inheritable),
    .cap_permitted_offset = offsetof(struct cred, cap_permitted),
    .cap_effective_offset = offsetof(struct cred, cap_effective),
    .cap_bset_offset = offsetof(struct cred, cap_bset),
    .cap_ambient_offset = offsetof(struct cred, cap_ambient),
    .user_offset = offsetof(struct cred, user),
    .user_ns_offset = offsetof(struct cred, user_ns),
    .ucounts_offset = offsetof(struct cred, ucounts),
    .group_info_offset = offsetof(struct cred, group_info),
#if defined(CONFIG_KEYS)
    .session_keyring_offset = offsetof(struct cred, session_keyring),
    .process_keyring_offset = offsetof(struct cred, process_keyring),
    .thread_keyring_offset = offsetof(struct cred, thread_keyring),
    .request_key_auth_offset = offsetof(struct cred, request_key_auth),
#else
    .session_keyring_offset = -1,
    .process_keyring_offset = -1,
    .thread_keyring_offset = -1,
    .request_key_auth_offset = -1,
#endif
    .security_offset = offsetof(struct cred, security),
    .rcu_offset = offsetof(struct cred, rcu),
};

static struct kpm_mm_struct_offset kpm_mm_struct_offset = {
    .mmap_base_offset = offsetof(struct mm_struct, mmap_base),
    .task_size_offset = offsetof(struct mm_struct, task_size),
    .pgd_offset = offsetof(struct mm_struct, pgd),
    .map_count_offset = offsetof(struct mm_struct, map_count),
    .total_vm_offset = offsetof(struct mm_struct, total_vm),
    .locked_vm_offset = offsetof(struct mm_struct, locked_vm),
    .pinned_vm_offset = offsetof(struct mm_struct, pinned_vm),
    .data_vm_offset = offsetof(struct mm_struct, data_vm),
    .exec_vm_offset = offsetof(struct mm_struct, exec_vm),
    .stack_vm_offset = offsetof(struct mm_struct, stack_vm),
    .start_code_offset = offsetof(struct mm_struct, start_code),
    .end_code_offset = offsetof(struct mm_struct, end_code),
    .start_data_offset = offsetof(struct mm_struct, start_data),
    .end_data_offset = offsetof(struct mm_struct, end_data),
    .start_brk_offset = offsetof(struct mm_struct, start_brk),
    .brk_offset = offsetof(struct mm_struct, brk),
    .start_stack_offset = offsetof(struct mm_struct, start_stack),
    .arg_start_offset = offsetof(struct mm_struct, arg_start),
    .arg_end_offset = offsetof(struct mm_struct, arg_end),
    .env_start_offset = offsetof(struct mm_struct, env_start),
    .env_end_offset = offsetof(struct mm_struct, env_end),
};

/* KernelPatch syscall_name_table 布局: { name, addr } 数组, KPM 按其索引 */
struct kpm_syscall_name {
    const char *name;
    uintptr_t addr;
};
static struct kpm_syscall_name kpm_syscall_name_table[460] = { { 0 } };
static struct kpm_syscall_name kpm_compat_syscall_name_table[460] = { { 0 } };

/* ---- 真实等价实现 ---- */
static uid_t kpm_current_uid(void)
{
    return from_kuid(&init_user_ns, current_cred()->uid);
}

static struct pt_regs *kpm_task_pt_reg(struct task_struct *task)
{
    return task_pt_regs(task);
}

static uint64_t kpm_get_random_u64(void)
{
    return get_random_u64();
}

static void *__user kpm_copy_to_user_stack(const void *data, int len)
{
    uintptr_t addr = current_user_stack_pointer();
    int cplen;

    addr -= len;
    addr &= 0xFFFFFFFFFFFFFFF8ULL;
    cplen = kpm_compat_copy_to_user((void __user *)addr, data, len);
    return cplen > 0 ? (void __user *)addr : (void *)(long)cplen;
}

/* ARM64 指令编码 (纯编码器, 不触碰任何可执行内存, 与 KernelPatch 0.13.5 一致) */
static int32_t kpm_branch_relative(uint32_t *buf, uint64_t src_addr, uint64_t dst_addr)
{
#define KPM_B_REL_RANGE ((1 << 25) << 2)
    if (((dst_addr >= src_addr) && (dst_addr - src_addr <= KPM_B_REL_RANGE)) ||
        ((src_addr >= dst_addr) && (src_addr - dst_addr <= KPM_B_REL_RANGE))) {
        buf[0] = 0x14000000u | (((dst_addr - src_addr) & 0x0FFFFFFFu) >> 2u); /* B <label> */
        buf[1] = 0xd503201f; /* NOP */
        return 2;
    }
    return 0;
}

static int32_t kpm_branch_absolute(uint32_t *buf, uint64_t addr)
{
    buf[0] = 0x58000051; /* LDR X17, #8 */
    buf[1] = 0xd61f0220; /* BR X17 */
    buf[2] = (uint32_t)addr;
    buf[3] = (uint32_t)(addr >> 32);
    return 4;
}

static int32_t kpm_ret_absolute(uint32_t *buf, uint64_t addr)
{
    buf[0] = 0x58000051; /* LDR X17, #8 */
    buf[1] = 0xd65f0220; /* RET X17 */
    buf[2] = (uint32_t)addr;
    buf[3] = (uint32_t)(addr >> 32);
    return 4;
}

static long kpm_raw_syscall_common(long nr, const u64 args[6], int nargs)
{
    struct pt_regs regs;
    uintptr_t addr;

    if (nr < 0 || nr >= __NR_syscalls)
        return -ENOSYS;
    addr = (uintptr_t)sys_call_table[nr];
    if (!addr)
        return -ENOSYS;

    memset(&regs, 0, sizeof(regs));
    regs.syscallno = nr;
    regs.regs[8] = nr;
    for (int i = 0; i < nargs && i < 6; i++)
        regs.regs[i] = args[i];

    return ((syscall_fn_t)addr)(&regs);
}

static long kpm_raw_syscall0(long nr)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ 0 }, 0);
}

static long kpm_raw_syscall1(long nr, long arg0)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ (u64)arg0 }, 1);
}

static long kpm_raw_syscall2(long nr, long arg0, long arg1)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ (u64)arg0, (u64)arg1 }, 2);
}

static long kpm_raw_syscall3(long nr, long arg0, long arg1, long arg2)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ (u64)arg0, (u64)arg1, (u64)arg2 }, 3);
}

static long kpm_raw_syscall4(long nr, long arg0, long arg1, long arg2, long arg3)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ (u64)arg0, (u64)arg1, (u64)arg2, (u64)arg3 }, 4);
}

static long kpm_raw_syscall5(long nr, long arg0, long arg1, long arg2, long arg3, long arg4)
{
    return kpm_raw_syscall_common(nr, (const u64[6]){ (u64)arg0, (u64)arg1, (u64)arg2, (u64)arg3, (u64)arg4 }, 5);
}

static long kpm_raw_syscall6(long nr, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5)
{
    return kpm_raw_syscall_common(
        nr, (const u64[6]){ (u64)arg0, (u64)arg1, (u64)arg2, (u64)arg3, (u64)arg4, (u64)arg5 }, 6);
}

static uintptr_t kpm_syscalln_name_addr(int nr, int is_compat)
{
    return 0;
}

static uintptr_t kpm_syscalln_addr(int nr, int is_compat)
{
    if (nr < 0)
        return 0;
#ifdef CONFIG_COMPAT
    if (is_compat) {
        if (nr >= __NR_compat_syscalls)
            return 0;
        return (uintptr_t)compat_sys_call_table[nr];
    }
#endif
    if (nr >= __NR_syscalls)
        return 0;
    return (uintptr_t)sys_call_table[nr];
}

static const char *kpm_kp_feature_list(void)
{
    return "kpm.event,kpm.load_result,kp.symbol_list,kp.feature_list,symbol_lookup_name";
}

static const char *kpm_su_get_path(void)
{
    return "/system/bin/su";
}

/* ---- 兼容占位 (KernelPatch 特有运行时, 仅保证可解析地址且调用不崩溃) ---- */
static int kpm_hook_unsupported(const char *api)
{
    pr_warn_ratelimited("kpm: %s: KernelPatch hotpatch not supported by ReSukiSU (compat stub)\n", api);
    return -EOPNOTSUPP;
}

/* hook.h 系列: inline hook / 函数指针 hook 依赖 hotpatch 文本补丁机制,
 * 在 ReSukiSU 上无法安全实现, 全部为安全占位。hook_t/hook_chain_t 等类型
 * 由模块侧定义, 这里用 void* 保持 ABI 兼容。 */
static int kpm_hook_prepare(void *hook)
{
    return kpm_hook_unsupported("hook_prepare");
}

static void kpm_hook_install(void *hook)
{
}

static void kpm_hook_uninstall(void *hook)
{
}

static int kpm_hook(void *func, void *replace, void **backup)
{
    return kpm_hook_unsupported("hook");
}

static void kpm_unhook(void *func)
{
}

static int kpm_hook_chain_add(void *chain, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("hook_chain_add");
}

static void kpm_hook_chain_remove(void *chain, void *before, void *after)
{
}

static int kpm_hook_wrap(void *func, int32_t argno, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("hook_wrap");
}

static void kpm_hook_unwrap_remove(void *func, void *before, void *after, int remove)
{
}

static void kpm_fp_hook(uintptr_t fp_addr, void *replace, void **backup)
{
}

static void kpm_fp_unhook(uintptr_t fp_addr, void *backup)
{
}

static int kpm_fp_hook_wrap(uintptr_t fp_addr, int32_t argno, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("fp_hook_wrap");
}

static void kpm_fp_hook_unwrap(uintptr_t fp_addr, void *before, void *after)
{
}

static int kpm_hotpatch(void *addrs[], uint32_t values[], int cnt)
{
    return kpm_hook_unsupported("hotpatch");
}

static int kpm_hotpatch_nosync(void *addr, uint32_t value)
{
    return kpm_hook_unsupported("hotpatch_nosync");
}

static int kpm_hook_syscalln(int nr, int narg, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("hook_syscalln");
}

static void kpm_unhook_syscalln(int nr, void *before, void *after)
{
}

static int kpm_hook_compat_syscalln(int nr, int narg, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("hook_compat_syscalln");
}

static void kpm_unhook_compat_syscalln(int nr, void *before, void *after)
{
}

static int kpm_fp_wrap_syscalln(int nr, int narg, int is_compat, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("fp_wrap_syscalln");
}

static void kpm_fp_unwrap_syscalln(int nr, int is_compat, void *before, void *after)
{
}

static int kpm_inline_wrap_syscalln(int nr, int narg, int is_compat, void *before, void *after, void *udata)
{
    return kpm_hook_unsupported("inline_wrap_syscalln");
}

static void kpm_inline_unwrap_syscalln(int nr, int is_compat, void *before, void *after)
{
}

/* 遍历 address_symbol 表统计/导出符号名 (实现 kp_symbol_count/kp_symbol_list) */
int kpm_kp_symbol_count(void);
int kpm_kp_symbol_list(char *out, int size);

static int kpm_kp_kconfig_available(void)
{
    return 0;
}

static int kpm_kp_kconfig_enabled(const char *name)
{
    return 0;
}

static const char *kpm_kp_kconfig_value(const char *name)
{
    return NULL;
}

static const char *kpm_kp_kconfig_data(void)
{
    return NULL;
}

static int kpm_kp_kconfig_size(void)
{
    return 0;
}

static long kpm_kp_control_feature_sc(const char __user *uname, int state)
{
    return -ENOENT;
}

static uint64_t *kpm_pgtable_entry(uint64_t pgd, uint64_t va)
{
    return NULL;
}

static uint64_t kpm_pgtable_phys(uint64_t pgd, uint64_t va)
{
    return 0;
}

static int kpm_write_kstorage(int gid, long did, void *data, int offset, int len, bool data_is_user)
{
    return -EOPNOTSUPP;
}

static const void *kpm_get_kstorage(int gid, long did)
{
    return ERR_PTR(-ENOENT);
}

static int kpm_on_each_kstorage_elem(int gid, int (*cb)(void *, void *), void *udata)
{
    return -EOPNOTSUPP;
}

static int kpm_read_kstorage(int gid, long did, void *data, int offset, int len, bool data_is_user)
{
    return -EOPNOTSUPP;
}

static int kpm_list_kstorage_ids(int gid, long *ids, int idslen, bool data_is_user)
{
    return -EOPNOTSUPP;
}

static int kpm_remove_kstorage(int gid, long did)
{
    return -EOPNOTSUPP;
}

static int kpm_su_add_allow_uid(uid_t uid, uid_t to_uid, const char *scontext)
{
    return -EOPNOTSUPP;
}

static int kpm_su_remove_allow_uid(uid_t uid)
{
    return -EOPNOTSUPP;
}

static int kpm_su_allow_uid_nums(void)
{
    return 0;
}

static int kpm_su_allow_uids(int is_user, uid_t *out_uids, int out_num)
{
    return 0;
}

static int kpm_su_allow_uid_profile(int is_user, uid_t uid, void *out_profile)
{
    return -ENOENT;
}

static int kpm_su_reset_path(const char *path)
{
    return -EOPNOTSUPP;
}

static int kpm_set_ap_mod_exclude(uid_t uid, int exclude)
{
    return -EOPNOTSUPP;
}

static int kpm_list_ap_mod_exclude(uid_t *uids, int len)
{
    return 0;
}

static long kpm_notify_modules_event(const char *event, const char *args, void *__user reserved)
{
    return 0;
}

static void kpm_extra_event_init(const char *event)
{
}

static void kpm_extra_event_init_args(const char *event, const char *args)
{
}

static int kpm_refresh_trusted_manager_uid(void)
{
    return -EOPNOTSUPP;
}

static int kpm_is_trusted_manager_uid_android(uid_t uid)
{
    return uid == (uid_t)ksu_last_manager_appid;
}

static uid_t kpm_get_trusted_manager_uid(void)
{
    return ksu_last_manager_appid;
}

static int kpm_load_ap_package_config(void)
{
    return -EOPNOTSUPP;
}

static int kpm_load_ap_kpm_modules(void)
{
    return -EOPNOTSUPP;
}

static struct CompactAddressSymbol address_symbol[] = {
    { "kallsyms_lookup_name", &kpm_kallsyms_lookup_name_fn },
    { "kallsyms_on_each_symbol", &kpm_kallsyms_on_each_symbol_fn },
    { "printk", &kpm_printk_fn },
    { "compact_find_symbol", &sukisu_compact_find_symbol },
    { "symbol_lookup_name", &sukisu_compact_find_symbol },
    { "compat_copy_to_user", &kpm_compat_copy_to_user },
    { "compat_copy_from_user", &kpm_compat_copy_from_user },
    { "compat_strncpy_from_user", &strncpy_from_user },
    { "kpver", &kpm_kpver },
    { "kf_strcpy", &kf_strcpy },
    { "kf_strncpy", &kf_strncpy },
    { "kf_strlcpy", &kf_strlcpy },
    { "kf_strscpy", &kf_strscpy },
    { "kf_stpcpy", &kf_stpcpy },
    { "kf_strcat", &kf_strcat },
    { "kf_strncat", &kf_strncat },
    { "kf_strlcat", &kf_strlcat },
    { "kf_strlen", &kf_strlen },
    { "kf_strnlen", &kf_strnlen },
    { "kf_strcmp", &kf_strcmp },
    { "kf_strncmp", &kf_strncmp },
    { "kf_strcasecmp", &kf_strcasecmp },
    { "kf_strncasecmp", &kf_strncasecmp },
    { "kf_strchr", &kf_strchr },
    { "kf_strrchr", &kf_strrchr },
    { "kf_strchrnul", &kf_strchrnul },
    { "kf_strnchr", &kf_strnchr },
    { "kf_strstr", &kf_strstr },
    { "kf_strnstr", &kf_strnstr },
    { "kf_strsep", &kf_strsep },
    { "kf_strpbrk", &kf_strpbrk },
    { "kf_strcspn", &kf_strcspn },
    { "kf_strspn", &kf_strspn },
    { "kf_strreplace", &kf_strreplace },
    { "kf_strim", &kf_strim },
    { "kf_skip_spaces", &kf_skip_spaces },
    { "kf_memcpy", &kf_memcpy },
    { "kf_memmove", &kf_memmove },
    { "kf_memset", &kf_memset },
    { "kf_memcmp", &kf_memcmp },
    { "kf_memchr", &kf_memchr },
    { "kf_memchr_inv", &kf_memchr_inv },
    { "kf_memscan", &kf_memscan },
    { "kf_memset16", &kf_memset16 },
    { "kf_memset32", &kf_memset32 },
    { "kf_memset64", &kf_memset64 },
    { "kf_snprintf", &kf_snprintf },
    { "kf_vsnprintf", &kf_vsnprintf },
    { "kf_scnprintf", &kf_scnprintf },
    { "kf_vscnprintf", &kf_vscnprintf },
    { "kf_sprintf", &kf_sprintf },
    { "kf_vsprintf", &kf_vsprintf },
    { "kf_sscanf", &kf_sscanf },
    { "kf_vsscanf", &kf_vsscanf },
    { "kf_strnlen_user", &kf_strnlen_user },
    { "kf_kstrtoll", &kf_kstrtoll },
    { "kf_kstrtoull", &kf_kstrtoull },
    { "kf_kasprintf", &kf_kasprintf },
    { "kf_kvasprintf", &kf_kvasprintf },
    { "kf_match_string", &kf_match_string },
    { "kf_sysfs_streq", &kf_sysfs_streq },
    { "kf_d_path", &kf_d_path },
    { "kf_kallsyms_lookup_name", &kf_kallsyms_lookup_name },
    { "kf_strscpy_pad", &kf_strscpy_pad },
    { "kf_strnchrnul", &kf_strnchrnul },
    { "kf_bcmp", &kf_bcmp },
    { "kf_fortify_panic", &kf_fortify_panic },
    { "kf___sysfs_match_string", &kf___sysfs_match_string },
    { "kf_argv_split", &kf_argv_split },
    { "kf_argv_free", &kf_argv_free },
    { "kf_dump_stack_lvl", &kf_dump_stack_lvl },
    { "kf_dump_stack", &kf_dump_stack },
    { "kf_strncpy_from_user_nofault", &kf_strncpy_from_user_nofault },
    { "kf_strnlen_user_nofault", &kf_strnlen_user_nofault },
    { "kf_strncpy_from_unsafe_user", &kf_strncpy_from_unsafe_user },
    { "kf_strnlen_unsafe_user", &kf_strnlen_unsafe_user },
    { "kf_fget", &kf_fget },
    { "kf_fput", &kf_fput },
    /* KernelPatch 兼容变量 */
    { "sys_call_table", &kpm_sys_call_table },
    { "compat_sys_call_table", &kpm_compat_sys_call_table },
    { "has_syscall_wrapper", &kpm_has_syscall_wrapper },
    { "has_config_compat", &kpm_has_config_compat },
    { "kver", &kpm_kver },
    { "endian", &kpm_endian },
    { "patch_config", &kpm_patch_config },
    { "setup_header", &kpm_setup_header },
    { "task_ext_size", &kpm_task_ext_size },
    { "thread_size", &kpm_thread_size },
    { "thread_info_in_task", &kpm_thread_info_in_task },
    { "sp_el0_is_current", &kpm_sp_el0_is_current },
    { "sp_el0_is_thread_info", &kpm_sp_el0_is_thread_info },
    { "task_in_thread_info_offset", &kpm_task_in_thread_info_offset },
    { "stack_in_task_offset", &kpm_stack_in_task_offset },
    { "stack_end_offset", &kpm_stack_end_offset },
    { "task_struct_offset", &kpm_task_struct_offset },
    { "cred_offset", &kpm_cred_offset },
    { "mm_struct_offset", &kpm_mm_struct_offset },
    { "syscall_name_table", &kpm_syscall_name_table },
    { "compat_syscall_name_table", &kpm_compat_syscall_name_table },
    { "android_is_safe_mode", &kpm_android_is_safe_mode },
    /* 真实等价实现 */
    { "current_uid", &kpm_current_uid },
    { "_task_pt_reg", &kpm_task_pt_reg },
    { "get_random_u64", &kpm_get_random_u64 },
    { "copy_to_user_stack", &kpm_copy_to_user_stack },
    { "branch_relative", &kpm_branch_relative },
    { "branch_absolute", &kpm_branch_absolute },
    { "ret_absolute", &kpm_ret_absolute },
    { "raw_syscall0", &kpm_raw_syscall0 },
    { "raw_syscall1", &kpm_raw_syscall1 },
    { "raw_syscall2", &kpm_raw_syscall2 },
    { "raw_syscall3", &kpm_raw_syscall3 },
    { "raw_syscall4", &kpm_raw_syscall4 },
    { "raw_syscall5", &kpm_raw_syscall5 },
    { "raw_syscall6", &kpm_raw_syscall6 },
    { "syscalln_name_addr", &kpm_syscalln_name_addr },
    { "syscalln_addr", &kpm_syscalln_addr },
    { "kp_feature_list", &kpm_kp_feature_list },
    { "kp_symbol_count", &kpm_kp_symbol_count },
    { "kp_symbol_list", &kpm_kp_symbol_list },
    { "su_get_path", &kpm_su_get_path },
    /* 兼容占位 (KernelPatch 特有运行时) */
    { "kp_kconfig_available", &kpm_kp_kconfig_available },
    { "kp_kconfig_enabled", &kpm_kp_kconfig_enabled },
    { "kp_kconfig_value", &kpm_kp_kconfig_value },
    { "kp_kconfig_data", &kpm_kp_kconfig_data },
    { "kp_kconfig_size", &kpm_kp_kconfig_size },
    { "kp_control_feature_sc", &kpm_kp_control_feature_sc },
    { "pgtable_entry", &kpm_pgtable_entry },
    { "pgtable_phys", &kpm_pgtable_phys },
    { "write_kstorage", &kpm_write_kstorage },
    { "get_kstorage", &kpm_get_kstorage },
    { "on_each_kstorage_elem", &kpm_on_each_kstorage_elem },
    { "read_kstorage", &kpm_read_kstorage },
    { "list_kstorage_ids", &kpm_list_kstorage_ids },
    { "remove_kstorage", &kpm_remove_kstorage },
    { "su_add_allow_uid", &kpm_su_add_allow_uid },
    { "su_remove_allow_uid", &kpm_su_remove_allow_uid },
    { "su_allow_uid_nums", &kpm_su_allow_uid_nums },
    { "su_allow_uids", &kpm_su_allow_uids },
    { "su_allow_uid_profile", &kpm_su_allow_uid_profile },
    { "su_reset_path", &kpm_su_reset_path },
    { "set_ap_mod_exclude", &kpm_set_ap_mod_exclude },
    { "list_ap_mod_exclude", &kpm_list_ap_mod_exclude },
    { "notify_modules_event", &kpm_notify_modules_event },
    { "extra_event_init", &kpm_extra_event_init },
    { "extra_event_init_args", &kpm_extra_event_init_args },
    { "refresh_trusted_manager_uid", &kpm_refresh_trusted_manager_uid },
    { "is_trusted_manager_uid_android", &kpm_is_trusted_manager_uid_android },
    { "get_trusted_manager_uid", &kpm_get_trusted_manager_uid },
    { "load_ap_package_config", &kpm_load_ap_package_config },
    { "load_ap_kpm_modules", &kpm_load_ap_kpm_modules },
    /* hook/syscall hook 系列 (安全占位) */
    { "hook_prepare", &kpm_hook_prepare },
    { "hook_install", &kpm_hook_install },
    { "hook_uninstall", &kpm_hook_uninstall },
    { "hook", &kpm_hook },
    { "unhook", &kpm_unhook },
    { "hook_chain_add", &kpm_hook_chain_add },
    { "hook_chain_remove", &kpm_hook_chain_remove },
    { "hook_wrap", &kpm_hook_wrap },
    { "hook_unwrap_remove", &kpm_hook_unwrap_remove },
    { "fp_hook", &kpm_fp_hook },
    { "fp_unhook", &kpm_fp_unhook },
    { "fp_hook_wrap", &kpm_fp_hook_wrap },
    { "fp_hook_unwrap", &kpm_fp_hook_unwrap },
    { "hotpatch", &kpm_hotpatch },
    { "hotpatch_nosync", &kpm_hotpatch_nosync },
    { "hook_syscalln", &kpm_hook_syscalln },
    { "unhook_syscalln", &kpm_unhook_syscalln },
    { "hook_compat_syscalln", &kpm_hook_compat_syscalln },
    { "unhook_compat_syscalln", &kpm_unhook_compat_syscalln },
    { "fp_wrap_syscalln", &kpm_fp_wrap_syscalln },
    { "fp_unwrap_syscalln", &kpm_fp_unwrap_syscalln },
    { "inline_wrap_syscalln", &kpm_inline_wrap_syscalln },
    { "inline_unwrap_syscalln", &kpm_inline_unwrap_syscalln },
    { "is_run_in_sukisu_ultra", (void *)1 },
    { "is_su_allow_uid", &sukisu_is_su_allow_uid },
    { "get_ap_mod_exclude", &sukisu_get_ap_mod_exclude },
    { "is_uid_should_umount", &sukisu_is_uid_should_umount },
    { "is_current_uid_manager", &sukisu_is_current_uid_manager },
    { "get_manager_uid", &sukisu_get_manager_uid },
    { "sukisu_set_manager_uid", &sukisu_set_manager_uid }
};

int kpm_kp_symbol_count(void)
{
    return ARRAY_SIZE(address_symbol);
}

int kpm_kp_symbol_list(char *out, int size)
{
    int off = 0;
    int i;

    if (!out || size <= 0)
        return -ENOBUFS;
    for (i = 0; i < ARRAY_SIZE(address_symbol); i++) {
        int len = strlen(address_symbol[i].symbol_name) + 1;
        if (off + len >= size)
            return -ENOBUFS;
        memcpy(out + off, address_symbol[i].symbol_name, len - 1);
        out[off + len - 1] = '\n';
        off += len;
    }
    return off;
}

unsigned long sukisu_compact_find_symbol(const char *name)
{
    int i;
    unsigned long addr;

    for (i = 0; i < (sizeof(address_symbol) / sizeof(struct CompactAddressSymbol)); i++) {
        struct CompactAddressSymbol *symbol = &address_symbol[i];

        if (strcmp(name, symbol->symbol_name) == 0)
            return (unsigned long)symbol->addr;
    }

    addr = find_kernel_symbol_exact(name);
    if (addr)
        return addr;

    return 0;
}
EXPORT_SYMBOL(sukisu_compact_find_symbol);
