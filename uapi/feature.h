#ifndef __KSU_UAPI_FEATURE_H
#define __KSU_UAPI_FEATURE_H

enum ksu_feature_id {
    KSU_FEATURE_SU_COMPAT = 0,
    KSU_FEATURE_KERNEL_UMOUNT = 1,
    KSU_FEATURE_SULOG = 2,
    KSU_FEATURE_ADB_ROOT = 3,
    KSU_FEATURE_SELINUX_HIDE = 4,
    KSU_FEATURE_NETISOLATE = 5,
    /* 6: 已废弃 (原 KSU_FEATURE_FUSEBPF) —— fusebpf lookup revalidate 直通修复自内核侧
     * v3 补丁起无条件生效, 不再有运行时开关。保留编号占位以免后续 feature id 漂移
     * (KSU_FEATURE_MAX 保持 7: 老管理器查询 id 6 得到"不支持", 而不是无效 id)。 */
    KSU_FEATURE_FUSEBPF = 6,

    KSU_FEATURE_MAX
};

#endif
