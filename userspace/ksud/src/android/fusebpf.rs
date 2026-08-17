//! FUSEBPF 直通修复开关
//! supercall 直接控制内核变量 (与 netisolate 同模式, 可靠)

use anyhow::{Context, Result};
use libc::{SYS_reboot, syscall};
use std::fs;

const KSU_INSTALL_MAGIC1: u64 = 0xDEAD_BEEF;
const SUSFS_MAGIC: u64 = 0xFAFA_FAFA;
const CMD_FUSEBPF_SET: u64 = 0x555d6;

const SYSFS_PARAM: &str = "/sys/module/kernelsu/parameters/fusebpf_fix";

fn fusebpfctl(val: u32) -> i64 {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            CMD_FUSEBPF_SET,
            &val as *const u32 as usize,
        )
    }
}

pub(crate) fn set(enabled: bool) -> Result<()> {
    let ret = fusebpfctl(if enabled { 1 } else { 0 });
    if ret < 0 {
        anyhow::bail!("fusebpf set failed: {ret}");
    }
    // 同步 sysfs 值 (状态展示)
    let _ = fs::write(SYSFS_PARAM, if enabled { "1" } else { "0" });
    Ok(())
}

pub(crate) fn get() -> Result<bool> {
    let v = fs::read_to_string(SYSFS_PARAM)
        .with_context(|| format!("read {SYSFS_PARAM} failed"))?;
    let t = v.trim().to_ascii_lowercase();
    Ok(t == "1" || t == "y" || t == "true" || t == "yes")
}
