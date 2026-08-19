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
    return copy_to_user(to, from, n);
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

static struct CompactAddressSymbol address_symbol[] = { { "kallsyms_lookup_name", &kallsyms_lookup_name },
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
                                                        { "is_run_in_sukisu_ultra", (void *)1 },
                                                        { "is_su_allow_uid", &sukisu_is_su_allow_uid },
                                                        { "get_ap_mod_exclude", &sukisu_get_ap_mod_exclude },
                                                        { "is_uid_should_umount", &sukisu_is_uid_should_umount },
                                                        { "is_current_uid_manager", &sukisu_is_current_uid_manager },
                                                        { "get_manager_uid", &sukisu_get_manager_uid },
                                                        { "sukisu_set_manager_uid", &sukisu_set_manager_uid } };

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
