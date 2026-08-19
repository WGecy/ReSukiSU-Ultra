use std::{
    ffi::{CStr, CString, OsStr},
    fs, io,
    os::unix::fs::PermissionsExt,
    path::Path,
};

use anyhow::{Context, Result, bail};

use crate::android::ksucalls::ksuctl;

#[repr(C)]
#[derive(Debug, Clone, Copy, Default)]
pub struct KsuKpmCmd {
    pub control_code: u64,
    pub arg1: u64,
    pub arg2: u64,
    pub result_code: u64,
}

const KSU_IOCTL_KPM: u32 = 0xC0044BC8; // _IOC(READ|WRITE, 'K', 200, 0)
const KPM_LOAD: u64 = 1;
const KPM_UNLOAD: u64 = 2;
const KPM_LIST: u64 = 4;
const KPM_INFO: u64 = 5;

const KPM_DIR: &str = "/data/adb/kpm";
fn buf2str(buf: &[u8]) -> String {
    let end = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
    String::from_utf8_lossy(&buf[..end]).into_owned()
}


pub fn load_module<P>(path: P, args: Option<&str>) -> Result<()>
where
    P: AsRef<Path>,
{
    let path = CString::new(path.as_ref().to_string_lossy().to_string())?;
    let args = args.map_or_else(|| CString::new(String::new()), CString::new)?;

    let mut ret = -1;
    let mut cmd = KsuKpmCmd {
        control_code: u64::from(KPM_LOAD),
        arg1: path.as_ptr() as u64,
        arg2: args.as_ptr() as u64,
        result_code: &raw mut ret as u64,
    };

    ksuctl(KSU_IOCTL_KPM, &raw mut cmd)?;

    if ret < 0 {
        println!("Failed to load kpm: {}", io::Error::from_raw_os_error(ret));
    }
    Ok(())
}

pub fn list() -> Result<()> {
    let mut buf = vec![0u8; 1024];

    let mut ret = -1;
    let mut cmd = KsuKpmCmd {
        control_code: u64::from(KPM_LIST),
        arg1: buf.as_mut_ptr() as u64,
        arg2: buf.len() as u64,
        result_code: &raw mut ret as u64,
    };

    ksuctl(KSU_IOCTL_KPM, &raw mut cmd)?;

    if ret < 0 {
        println!(
            "Failed to get kpm list: {}",
            io::Error::from_raw_os_error(ret)
        );
        return Ok(());
    }

    println!("{}", buf2str(&buf));

    Ok(())
}

pub fn unload_module(name: String) -> Result<()> {
    let name = CString::new(name)?;

    let mut ret = -1;
    let mut cmd = KsuKpmCmd {
        control_code: u64::from(KPM_UNLOAD),
        arg1: name.as_ptr() as u64,
        arg2: 0,
        result_code: &raw mut ret as u64,
    };

    ksuctl(KSU_IOCTL_KPM, &raw mut cmd)?;

    if ret < 0 {
        println!(
            "Failed to unload kpm: {}",
            io::Error::from_raw_os_error(ret)
        );
    }
    Ok(())
}

pub fn info(name: String) -> Result<()> {
    let name = CString::new(name)?;
    let mut buf = vec![0u8; 256];

    let mut ret = -1;
    let mut cmd = KsuKpmCmd {
        control_code: u64::from(KPM_INFO),
        arg1: name.as_ptr() as u64,
        arg2: buf.as_mut_ptr() as u64,
        result_code: &raw mut ret as u64,
    };

    ksuctl(KSU_IOCTL_KPM, &raw mut cmd)?;

    if ret < 0 {
        println!(
            "Failed to get kpm info: {}",
            io::Error::from_raw_os_error(ret)
        );
        return Ok(());
    }
    println!("{}", buf2str(&buf));
    Ok(())
}

