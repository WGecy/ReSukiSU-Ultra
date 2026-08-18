//! ksud nomount 子命令 — NoMount VFS 路径重定向管理

use anyhow::Result;
use clap::{Args, Subcommand};

use crate::android::nomount;

#[derive(Debug, Args)]
pub struct NoMountArgs {
    #[command(subcommand)]
    pub command: NoMountSubCommands,
}

#[derive(Debug, Subcommand)]
pub enum NoMountSubCommands {
    /// 内核支持状态 + 版本
    Status,
    /// 规则列表 (--json 输出 JSON)
    List {
        /// JSON 输出
        #[arg(long)]
        json: bool,
    },
    /// 添加重定向规则 (虚拟路径 → 真实路径)
    Add {
        /// 虚拟路径 (被访问的路径)
        virtual_path: String,
        /// 真实路径 (实际文件)
        real_path: String,
    },
    /// 移除重定向规则
    Remove {
        /// 虚拟路径
        virtual_path: String,
    },
    /// 清空全部规则
    Clear,
    /// 手动触发模块注入 (开机自动执行, 手动用于重新注入)
    Inject,
    /// 模块列表 (可注入模块 + 规则数, JSON)
    Modules {
        /// JSON 输出
        #[arg(long)]
        json: bool,
    },
    /// 热加载模块 (注入其全部文件规则)
    Load {
        /// 模块 id (目录名)
        module_id: String,
    },
    /// 热卸载模块 (移除其全部规则)
    Unload {
        /// 模块 id (目录名)
        module_id: String,
    },
    /// 批量移除规则 (只清自定义, 不动模块注入)
    RemoveMany {
        /// 虚拟路径列表
        #[arg(required = true)]
        virtual_paths: Vec<String>,
    },
    /// 设置总开关 (0/1)
    SetEnabled {
        /// 1=启用 0=禁用
        enabled: u8,
    },
    /// 查询总开关状态
    IsEnabled,
    /// 聚合快照 (一次返回全部数据)
    Snapshot,
    /// 切换模块禁用状态 (写 disable 文件)
    ModuleDisable {
        /// 模块 id (目录名)
        module_id: String,
        /// 1=禁用 0=启用
        disabled: u8,
    },
    /// UID 排除列表
    ExcludeList {
        /// JSON 输出
        #[arg(long)]
        json: bool,
    },
    /// 添加 UID 排除 (该 uid 进程不做重定向)
    ExcludeAdd {
        /// uid
        uid: u32,
    },
    /// 移除 UID 排除
    ExcludeRemove {
        /// uid
        uid: u32,
    },
}

pub fn run_main(args: NoMountArgs) -> Result<()> {
    match args.command {
        NoMountSubCommands::Status => match nomount::version() {
            Ok(v) => {
                println!("supported: true");
                println!("version: {v}");
                Ok(())
            }
            Err(e) => {
                println!("supported: false");
                println!("error: {e}");
                Ok(())
            }
        },
        NoMountSubCommands::List { json } => {
            let rules = nomount::list()?;
            if json {
                let mut out = String::from("[");
                for (i, (v, r)) in rules.iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    out.push_str(&format!(
                        "\n  {{\n    \"virtual\": \"{}\",\n    \"real\": \"{}\"\n  }}",
                        escape_json(v),
                        escape_json(r)
                    ));
                }
                if !rules.is_empty() {
                    out.push('\n');
                }
                out.push(']');
                println!("{out}");
            } else {
                for (v, r) in &rules {
                    println!("{v} -> {r}");
                }
            }
            Ok(())
        }
        NoMountSubCommands::Add {
            virtual_path,
            real_path,
        } => {
            nomount::add(&virtual_path, &real_path)?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::Remove { virtual_path } => {
            nomount::remove(&virtual_path)?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::Clear => {
            nomount::clear()?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::Inject => {
            nomount::mount::inject_modules()?;
            println!("injected");
            Ok(())
        }
        NoMountSubCommands::Modules { json } => {
            let mods = nomount::mount::modules()?;
            if json {
                let mut out = String::from("[");
                for (i, m) in mods.iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    out.push_str(&format!(
                        "{{\"id\": \"{}\", \"name\": \"{}\", \"version\": \"{}\", \"author\": \"{}\", \"description\": \"{}\", \"disabled\": {}, \"file_count\": {}, \"loaded\": {}}}",
                        escape_json(&m.id),
                        escape_json(&m.name),
                        escape_json(&m.version),
                        escape_json(&m.author),
                        escape_json(&m.description),
                        m.disabled,
                        m.file_count,
                        m.loaded
                    ));
                }
                out.push(']');
                println!("{out}");
            } else {
                for m in &mods {
                    println!("{}\t{}\t{}\t{}", m.id, m.name, m.file_count, m.loaded);
                }
            }
            Ok(())
        }
        NoMountSubCommands::Load { module_id } => {
            let n = nomount::mount::load_module(&module_id)?;
            println!("loaded: {n}");
            Ok(())
        }
        NoMountSubCommands::Unload { module_id } => {
            let n = nomount::mount::unload_module(&module_id)?;
            println!("unloaded: {n}");
            Ok(())
        }
        NoMountSubCommands::RemoveMany { virtual_paths } => {
            nomount::remove_rules_batch(&virtual_paths)?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::SetEnabled { enabled } => {
            nomount::set_enabled(enabled == 1)?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::IsEnabled => {
            println!(
                "{}",
                if nomount::is_enabled() {
                    "true"
                } else {
                    "false"
                }
            );
            Ok(())
        }
        NoMountSubCommands::Snapshot => {
            let s = nomount::snapshot()?;
            println!("{s}");
            Ok(())
        }
        NoMountSubCommands::ModuleDisable {
            module_id,
            disabled,
        } => {
            let flag = std::path::Path::new("/data/adb/modules")
                .join(&module_id)
                .join("disable");
            if disabled == 1 {
                std::fs::write(&flag, "")?;
            } else {
                let _ = std::fs::remove_file(&flag);
            }
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::ExcludeList { json } => {
            let uids = nomount::exclude_list()?;
            if json {
                let arr = uids
                    .iter()
                    .map(|u| u.to_string())
                    .collect::<Vec<_>>()
                    .join(",");
                println!("[{arr}]");
            } else {
                for u in &uids {
                    println!("{u}");
                }
            }
            Ok(())
        }
        NoMountSubCommands::ExcludeAdd { uid } => {
            nomount::exclude_add(uid)?;
            println!("ok");
            Ok(())
        }
        NoMountSubCommands::ExcludeRemove { uid } => {
            nomount::exclude_remove(uid)?;
            println!("ok");
            Ok(())
        }
    }
}

pub fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\").replace('"', "\\\"")
}
