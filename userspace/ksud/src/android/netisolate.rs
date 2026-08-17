//! netisolate: UID 级联网阻止 (内核态, 走 SUSFS_MAGIC supercall 通道)
//!
//! 管理器写 /data/adb/ksu/netisolate/{enabled,uids} 文件持久化,
//! ksud 每次开机从文件加载并调内核 supercall 实时生效.
use std::fs;

use anyhow::Result;
use libc::{SYS_reboot, syscall};

const KSU_INSTALL_MAGIC1: u64 = 0xDEAD_BEEF;
const SUSFS_MAGIC: u64 = 0xFAFA_FAFA;

pub(crate) const CMD_NETISOLATE_ENABLE: u64 = 0x555d0;
pub(crate) const CMD_NETISOLATE_UID_ADD: u64 = 0x555d1;
#[allow(dead_code)]
pub(crate) const CMD_NETISOLATE_UID_REMOVE: u64 = 0x555d2;
pub(crate) const CMD_NETISOLATE_UID_CLEAR: u64 = 0x555d3;
pub(crate) const CMD_NETISOLATE_UID_LIST: u64 = 0x555d4;
pub(crate) const CMD_NETISOLATE_GET_STATE: u64 = 0x555d5;

const CONFIG_DIR: &str = "/data/adb/ksu/netisolate";

fn netisolatectl<T>(cmd: u64, arg: &mut T) -> i64 {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            std::ptr::from_mut::<T>(arg),
        )
        .into()
    }
}

pub(crate) fn set_enabled(enabled: bool) -> Result<()> {
    let mut val: u32 = if enabled { 1 } else { 0 };
    let ret = netisolatectl(CMD_NETISOLATE_ENABLE, &mut val);
    if ret < 0 {
        anyhow::bail!("netisolate enable failed: {ret}");
    }
    Ok(())
}

pub(crate) fn uid_add(uid: u32) -> Result<()> {
    let mut val = uid;
    let ret = netisolatectl(CMD_NETISOLATE_UID_ADD, &mut val);
    if ret < 0 {
        anyhow::bail!("netisolate uid add failed: {ret}");
    }
    Ok(())
}

pub(crate) fn uid_remove(uid: u32) -> Result<()> {
    let mut val = uid;
    let ret = netisolatectl(CMD_NETISOLATE_UID_REMOVE, &mut val);
    if ret < 0 {
        anyhow::bail!("netisolate uid remove failed: {ret}");
    }
    Ok(())
}

pub(crate) fn uid_clear() -> Result<()> {
    let mut val: u32 = 0;
    let ret = netisolatectl(CMD_NETISOLATE_UID_CLEAR, &mut val);
    if ret < 0 {
        anyhow::bail!("netisolate uid clear failed: {ret}");
    }
    Ok(())
}

/// 开机加载: 读 /data/adb/ksu/netisolate/ 配置 → supercall 应用到内核 (纯内核 REJECT)
pub(crate) fn apply_from_files() -> Result<()> {
    let enabled_path = format!("{CONFIG_DIR}/enabled");
    let uids_path = format!("{CONFIG_DIR}/uids");

    let enabled = match fs::read_to_string(&enabled_path) {
        Ok(s) => s.trim() == "1",
        Err(_) => false,
    };
    set_enabled(enabled)?;
    uid_clear()?;
    if let Ok(s) = fs::read_to_string(&uids_path) {
        for line in s.lines() {
            let t = line.trim();
            if let Ok(uid) = t.parse::<u32>() {
                let _ = uid_add(uid);
            }
        }
    }
    log::info!("netisolate: applied from files (enabled={enabled})");
    Ok(())
}


