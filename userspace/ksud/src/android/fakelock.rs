//! 隐藏 Bootloader 解锁状态
//!
//! 管理器写 /data/adb/ksu/fakelock/enabled 标志文件,
//! ksud 每次开机自动重设属性 (持久生效).
use std::path::Path;

use anyhow::{Context, Result};
use prop_rs_android::{resetprop::ResetProp, sys_prop};

const FLAG_FILE: &str = "/data/adb/ksu/fakelock/enabled";

const PATCH_LIST: &[(&str, &str)] = &[
    ("ro.boot.vbmeta.device_state", "locked"),
    ("ro.boot.verifiedbootstate", "green"),
    ("ro.boot.flash.locked", "1"),
    ("ro.boot.veritymode", "enforcing"),
    ("vendor.boot.vbmeta.device_state", "locked"),
    ("vendor.boot.verifiedbootstate", "green"),
    ("ro.boot.vbmeta.invalidate_on_error", "yes"),
    ("ro.boot.vbmeta.avb_version", "1.0"),
    ("ro.boot.vbmeta.hash_alg", "sha256"),
    ("ro.boot.vbmeta.size", "4096"),
    ("ro.boot.warranty_bit", "0"),
    ("ro.warranty_bit", "0"),
    ("ro.vendor.boot.warranty_bit", "0"),
    ("ro.vendor.warranty_bit", "0"),
    ("sys.oem_unlock_allowed", "0"),
    ("ro.build.type", "user"),
    ("ro.build.tags", "release-keys"),
    ("ro.secureboot.lockstate", "locked"),
    ("ro.debuggable", "0"),
    ("ro.force.debuggable", "0"),
    ("ro.secure", "1"),
    ("ro.adb.secure", "1"),
    ("ro.boot.realmebootstate", "green"),
    ("ro.boot.realme.lockstate", "1"),
    ("persist.logd.size", ""),
    ("persist.logd.size.crash", ""),
    ("persist.logd.size.system", ""),
    ("persist.logd.size.main", ""),
];

const BOOT_KEYS: &[&str] = &["ro.bootmode", "ro.boot.bootmode", "vendor.boot.bootmode"];

/// 开关文件存在时应用伪装 (开机自动调用)
pub(crate) fn apply_if_enabled() -> Result<()> {
    if !Path::new(FLAG_FILE).exists() {
        return Ok(());
    }

    sys_prop::init().context("Failed to initialize system property API")?;
    let rp = ResetProp {
        skip_svc: true,
        persistent: false,
        persist_only: false,
        verbose: false,
        show_context: false,
        rebuild: false,
    };

    for (key, value) in PATCH_LIST {
        if rp.get(key).is_some() {
            let _ = rp.set(key, value);
        }
    }

    // bootmode 含 recovery → unknown (防 recovery 检测)
    for key in BOOT_KEYS {
        if let Some(val) = rp.get(key) {
            if val.contains("recovery") {
                let _ = rp.set(key, "unknown");
            }
        }
    }

    log::info!("fakelock: props applied");
    Ok(())
}
