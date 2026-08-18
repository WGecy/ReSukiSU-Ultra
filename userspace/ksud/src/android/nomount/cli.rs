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
}

pub fn run_main(args: NoMountArgs) -> Result<()> {
    match args.command {
        NoMountSubCommands::Status => {
            match nomount::version() {
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
            }
        }
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
        NoMountSubCommands::Add { virtual_path, real_path } => {
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
    }
}

fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\").replace('"', "\\\"")
}
