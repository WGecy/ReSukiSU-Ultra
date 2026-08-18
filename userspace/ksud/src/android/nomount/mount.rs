//! NoMount 模块注入 (替代 NoMount 模块的 metamount.sh)
//! 开机遍历 /data/adb/modules/* 的可注入目录, 通过 netlink 批量注入 VFS 路径重定向规则

use std::fs;
use std::path::Path;

use anyhow::{Context, Result};
use log::{info, warn};

use super::NmSocket;

const MODULES_DIR: &str = "/data/adb/modules";
const TARGET_PARTITIONS: [&str; 6] = ["system", "vendor", "product", "system_ext", "odm", "oem"];
const ALIAS_PARTITIONS: [&str; 5] = ["vendor", "product", "system_ext", "odm", "oem"];

/// 模块信息 (管理器模块卡片)
#[derive(Debug)]
pub struct ModuleInfo {
    pub id: String,
    pub name: String,
    pub disabled: bool,
    pub file_count: usize,
    pub loaded: usize,
}

/// 开机注入: 遍历所有模块的可注入文件, 批量添加重定向规则
pub fn inject_modules() -> Result<()> {
    let modules_dir = Path::new(MODULES_DIR);
    if !modules_dir.is_dir() {
        return Ok(());
    }

    let mut all_rules: Vec<(String, String)> = Vec::new();
    for entry in fs::read_dir(modules_dir).context("读取模块目录失败")? {
        let entry = entry?;
        let mod_name = entry.file_name().to_string_lossy().into_owned();
        let mod_dir = entry.path();
        if mod_name == "nomount" || !mod_dir.is_dir() {
            continue;
        }
        // 禁用/移除/跳过挂载的模块不注入
        if mod_dir.join("disable").exists()
            || mod_dir.join("remove").exists()
            || mod_dir.join("skip_mount").exists()
        {
            continue;
        }
        all_rules.extend(collect_module_rules(&mod_dir));
    }

    if all_rules.is_empty() {
        info!("nomount: 没有可注入的模块文件");
        return Ok(());
    }

    info!("nomount: 注入 {} 条重定向规则", all_rules.len());
    let s = NmSocket::new()?;
    s.add_rules_batch(&all_rules)?;
    Ok(())
}

/// 热加载单个模块 (遍历其可注入文件并注入)
pub fn load_module(id: &str) -> Result<usize> {
    let mod_dir = Path::new(MODULES_DIR).join(id);
    if !mod_dir.is_dir() {
        anyhow::bail!("模块不存在: {id}");
    }
    let rules = collect_module_rules(&mod_dir);
    if rules.is_empty() {
        info!("nomount: 模块 {id} 没有可注入文件");
        return Ok(0);
    }
    let s = NmSocket::new()?;
    s.add_rules_batch(&rules)?;
    info!("nomount: 热加载 {id}: {} 条规则", rules.len());
    Ok(rules.len())
}

/// 热卸载单个模块 (按 real 路径过滤移除其全部规则)
pub fn unload_module(id: &str) -> Result<usize> {
    let prefix = format!("{MODULES_DIR}/{id}/");
    let s = NmSocket::new()?;
    let rules = s.list()?;
    let targets: Vec<String> = rules
        .into_iter()
        .filter(|(_, real)| real.starts_with(&prefix))
        .map(|(v, _)| v)
        .collect();
    let n = targets.len();
    if n == 0 {
        info!("nomount: 模块 {id} 没有已注入的规则");
        return Ok(0);
    }
    for v in &targets {
        s.mutate_rule(3, &encode_del_payload(v))?;
    }
    info!("nomount: 热卸载 {id}: 移除 {n} 条规则");
    Ok(n)
}

/// 模块卡片数据 (管理器)
pub fn modules() -> Result<Vec<ModuleInfo>> {
    let modules_dir = Path::new(MODULES_DIR);
    if !modules_dir.is_dir() {
        return Ok(Vec::new());
    }
    let s = NmSocket::new()?;
    let rules = s.list().unwrap_or_default();
    let mut out = Vec::new();
    for entry in fs::read_dir(modules_dir).context("读取模块目录失败")? {
        let entry = entry?;
        let id = entry.file_name().to_string_lossy().into_owned();
        let mod_dir = entry.path();
        if id == "nomount" || !mod_dir.is_dir() {
            continue;
        }
        let file_count = collect_module_rules(&mod_dir).len();
        if file_count == 0 {
            continue;
        }
        let loaded = rules
            .iter()
            .filter(|(_, real)| real.starts_with(&format!("{MODULES_DIR}/{id}/")))
            .count();
        let name = read_module_name(&mod_dir).unwrap_or_else(|| id.clone());
        let disabled = mod_dir.join("disable").exists();
        out.push(ModuleInfo {
            id,
            name,
            disabled,
            file_count,
            loaded,
        });
    }
    Ok(out)
}

fn read_module_name(mod_dir: &Path) -> Option<String> {
    let prop = fs::read_to_string(mod_dir.join("module.prop")).ok()?;
    prop.lines()
        .find(|l| l.starts_with("name="))
        .map(|l| l["name=".len()..].trim().to_string())
}

fn encode_del_payload(v: &str) -> Vec<u8> {
    let vb = v.as_bytes();
    let mut payload = Vec::with_capacity(2 + vb.len());
    payload.extend_from_slice(&(vb.len() as u16).to_ne_bytes());
    payload.extend_from_slice(vb);
    payload
}

/// 收集模块目录下的全部可注入规则 (含别名)
pub fn collect_module_rules(mod_dir: &Path) -> Vec<(String, String)> {
    let mut rules = Vec::new();
    for partition in TARGET_PARTITIONS {
        let pdir = mod_dir.join(partition);
        if pdir.is_dir() {
            collect_rules(&pdir, mod_dir, &mut rules);
        }
    }
    rules
}

/// 递归收集目录下的文件/符号链接 → (virtual, real) 规则 (含别名)
fn collect_rules(dir: &Path, mod_dir: &Path, rules: &mut Vec<(String, String)>) {
    let entries = match fs::read_dir(dir) {
        Ok(e) => e,
        Err(e) => {
            warn!("nomount: 读取目录失败 {}: {e}", dir.display());
            return;
        }
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let ft = match fs::symlink_metadata(&path) {
            Ok(ft) => ft,
            Err(_) => continue,
        };
        if ft.is_dir() {
            // 递归 (symlink_metadata: 符号链接不算目录, 不跟随)
            collect_rules(&path, mod_dir, rules);
            continue;
        }
        if !ft.is_file() && !ft.file_type().is_symlink() {
            continue;
        }
        let rel = match path.strip_prefix(mod_dir) {
            Ok(r) => r.to_string_lossy().into_owned(),
            Err(_) => continue,
        };
        let virtual_path = format!("/{rel}");
        let real_path = path.to_string_lossy().into_owned();
        rules.push((virtual_path.clone(), real_path.clone()));

        // 别名 (移植 metamount.sh 逻辑)
        if rel.starts_with("system/") {
            // system/vendor/* → /vendor/* (模块在非 system 路径下没有对应文件时)
            let alias = &rel["system/".len()..];
            let is_alias_partition = ALIAS_PARTITIONS
                .iter()
                .any(|p| alias.starts_with(&format!("{p}/")));
            if is_alias_partition && !mod_dir.join(alias).exists() {
                rules.push((format!("/{alias}"), real_path.clone()));
            }
        } else {
            // vendor/* → /system/vendor/* (模块 system 下没有对应文件时)
            let is_alias_partition = ALIAS_PARTITIONS
                .iter()
                .any(|p| rel.starts_with(&format!("{p}/")));
            if is_alias_partition && !mod_dir.join("system").join(&rel).exists() {
                rules.push((format!("/system/{rel}"), real_path));
            }
        }
    }
}
