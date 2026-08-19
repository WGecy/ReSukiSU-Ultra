#ifndef __KSU_H_SUPERCALL
#define __KSU_H_SUPERCALL

#include <linux/types.h>
#include <linux/uaccess.h>

// IOCTL handler types
typedef int (*ksu_ioctl_handler_t)(void __user *arg);
typedef bool (*ksu_perm_check_t)(void);

// IOCTL command mapping
struct ksu_ioctl_cmd_map {
    unsigned int cmd;
    const char *name;
    ksu_ioctl_handler_t handler;
    ksu_perm_check_t perm_check; // Permission check function
};

// Install KSU fd to current process
int ksu_install_fd(void);

void ksu_supercalls_init(void);
void ksu_supercalls_exit(void);
#endif // __KSU_H_SUPERCALL

// KPM (内核补丁模块) 协议
struct ksu_kpm_cmd {
    u64 control_code;
    u64 arg1;
    u64 arg2;
    u64 result_code;
};

#define SUKISU_KPM_LOAD   1
#define SUKISU_KPM_UNLOAD 2
#define SUKISU_KPM_LIST   4
#define SUKISU_KPM_INFO   5

#define KSU_IOCTL_KPM _IOC(_IOC_READ | _IOC_WRITE, 'K', 200, 0)
