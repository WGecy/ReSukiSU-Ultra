//! FUSEBPF 直通修复开关
//! 写 /sys/module/kernelsu/parameters/fusebpf_fix (KSU 内核 module_param)

use anyhow::{Context, Result};
use std::fs;

const SYSFS_PARAM: &str = "/sys/module/kernelsu/parameters/fusebpf_fix";

pub(crate) fn set(enabled: bool) -> Result<()> {
    fs::write(SYSFS_PARAM, if enabled { "1" } else { "0" })
        .with_context(|| format!("write {SYSFS_PARAM} failed"))
}

pub(crate) fn get() -> Result<bool> {
    let v = fs::read_to_string(SYSFS_PARAM)
        .with_context(|| format!("read {SYSFS_PARAM} failed"))?;
    let t = v.trim().to_ascii_lowercase();
    Ok(t == "1" || t == "y" || t == "true" || t == "yes")
}
