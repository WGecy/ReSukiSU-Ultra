//! NoMount netlink 客户端 (移植自 maxsteeel/nomount userspace/src/nm.c)
//! 通过 NETLINK_GENERIC 家族 "nomount" 与内核通信

pub mod cli;
pub mod mount;

use std::mem::size_of;

use anyhow::{Result, anyhow, bail};

const NLMSG_HDRLEN: usize = 16;
const GENL_HDRLEN: usize = 4;
const GENL_ID_CTRL: u16 = 16;
const CTRL_CMD_GETFAMILY: u8 = 3;
const CTRL_ATTR_FAMILY_ID: u16 = 1;
const CTRL_ATTR_FAMILY_NAME: u16 = 2;

const NLMSG_ERROR: u16 = 2;
const NLMSG_DONE: u16 = 3;

const NLM_F_REQUEST: u16 = 1;
const NLM_F_ACK: u16 = 4;
const NLM_F_DUMP: u16 = 0x300;

const RX_BUF_SIZE: usize = 32 * 1024;

struct NmSocket {
    fd: i32,
    family_id: u16,
}

impl NmSocket {
    fn new() -> Result<Self> {
        let fd = unsafe { libc::socket(libc::AF_NETLINK, libc::SOCK_RAW, libc::NETLINK_GENERIC) };
        if fd < 0 {
            bail!("netlink socket 创建失败: {}", std::io::Error::last_os_error());
        }
        let mut addr: libc::sockaddr_nl = unsafe { std::mem::zeroed() };
        addr.nl_family = libc::AF_NETLINK as u16;
        addr.nl_pid = 0;
        addr.nl_groups = 0;
        let ret = unsafe {
            libc::bind(
                fd,
                &addr as *const libc::sockaddr_nl as *const libc::sockaddr,
                size_of::<libc::sockaddr_nl>() as libc::socklen_t,
            )
        };
        if ret < 0 {
            unsafe { libc::close(fd) };
            bail!("netlink bind 失败: {}", std::io::Error::last_os_error());
        }
        let family_id = get_family_id(fd)?;
        Ok(NmSocket { fd, family_id })
    }

    /// 发送 genl 命令, 返回首个响应 buffer
    fn send_cmd(
        &self,
        cmd: u8,
        atype: u16,
        payload: &[u8],
        flags: u16,
    ) -> Result<Vec<u8>> {
        let has_attr = !payload.is_empty() || atype != 0;
        let total_len = NLMSG_HDRLEN + GENL_HDRLEN + if has_attr { 4 + payload.len() } else { 0 };
        let mut buf = vec![0u8; total_len];
        // nlmsghdr
        buf[0..4].copy_from_slice(&(total_len as u32).to_ne_bytes()); // nlmsg_len
        buf[4..6].copy_from_slice(&self.family_id.to_ne_bytes()); // nlmsg_type
        buf[6..8].copy_from_slice(&flags.to_ne_bytes()); // nlmsg_flags
        // genlmsghdr: cmd, version=1, reserved=0
        buf[16] = cmd;
        buf[17] = 1;
        // nla
        if has_attr {
            let nla_len = 4 + payload.len();
            buf[20..22].copy_from_slice(&(nla_len as u16).to_ne_bytes());
            buf[22..24].copy_from_slice(&atype.to_ne_bytes());
            buf[24..].copy_from_slice(payload);
        }

        let written = unsafe { libc::write(self.fd, buf.as_ptr() as *const libc::c_void, buf.len()) };
        if written < 0 {
            bail!("netlink write 失败: {}", std::io::Error::last_os_error());
        }

        let mut rx = vec![0u8; RX_BUF_SIZE];
        let n = unsafe { libc::read(self.fd, rx.as_mut_ptr() as *mut libc::c_void, RX_BUF_SIZE) };
        if n < 0 {
            bail!("netlink read 失败: {}", std::io::Error::last_os_error());
        }
        rx.truncate(n as usize);
        Ok(rx)
    }

    /// 版本 (cmd 1) → u32
    fn version(&self) -> Result<u32> {
        let rx = self.send_cmd(1, 0, &[], NLM_F_REQUEST | NLM_F_ACK)?;
        check_error(&rx)?;
        get_attr_u32(&rx, 5).ok_or_else(|| anyhow!("版本属性缺失"))
    }

    /// 批量添加重定向规则 (cmd 2, 负载为多条 [u32:0][u16:v_len][u16:r_len][v][r] 拼接)
    pub fn add_rules_batch(&self, rules: &[(String, String)]) -> Result<()> {
        if rules.is_empty() {
            return Ok(());
        }
        let mut payload = Vec::new();
        for (v, r) in rules {
            let vb = v.as_bytes();
            let rb = r.as_bytes();
            payload.extend_from_slice(&0u32.to_ne_bytes());
            payload.extend_from_slice(&(vb.len() as u16).to_ne_bytes());
            payload.extend_from_slice(&(rb.len() as u16).to_ne_bytes());
            payload.extend_from_slice(vb);
            payload.extend_from_slice(rb);
        }
        let rx = self.send_cmd(2, 6, &payload, NLM_F_REQUEST | NLM_F_ACK)?;
        check_error(&rx)?;
        Ok(())
    }

    /// 清空 (cmd 4)
    fn clear(&self) -> Result<()> {
        let rx = self.send_cmd(4, 0, &[], NLM_F_REQUEST | NLM_F_ACK)?;
        check_error(&rx)?;
        Ok(())
    }

    /// 添加/删除重定向规则 (cmd 2=add, 3=del)
    /// add 负载: [u32:0][u16:v_len][u16:r_len][v][r]
    /// del 负载: [u16:v_len][v]
    fn mutate_rule(&self, cmd: u8, payload: &[u8]) -> Result<()> {
        let rx = self.send_cmd(cmd, 6, payload, NLM_F_REQUEST | NLM_F_ACK)?;
        check_error(&rx)?;
        Ok(())
    }

    /// 规则列表 (cmd 7, DUMP) → [(virtual, real)]
    fn list(&self) -> Result<Vec<(String, String)>> {
        let mut rules = Vec::new();
        let mut buf = self.send_cmd(7, 0, &[], NLM_F_REQUEST | NLM_F_DUMP)?;
        loop {
            if buf.is_empty() {
                break;
            }
            let mut offset = 0usize;
            let mut consumed = false;
            while offset + NLMSG_HDRLEN <= buf.len() {
                let msg_len = u32_from(&buf, offset) as usize;
                if msg_len < NLMSG_HDRLEN || offset + msg_len > buf.len() {
                    break;
                }
                consumed = true;
                let msg_type = u16_from(&buf, offset + 4);
                if msg_type == NLMSG_DONE || msg_type == NLMSG_ERROR {
                    return Ok(rules);
                }
                if msg_type == self.family_id {
                    let v = get_attr_string(&buf[offset..offset + msg_len], 1);
                    let r = get_attr_string(&buf[offset..offset + msg_len], 2);
                    if let (Some(v), Some(r)) = (v, r) {
                        rules.push((v, r));
                    }
                }
                offset += msg_len;
            }
            if !consumed {
                break;
            }
            // 继续读 (dump 多消息)
            let mut rx = vec![0u8; RX_BUF_SIZE];
            let n = unsafe {
                libc::read(self.fd, rx.as_mut_ptr() as *mut libc::c_void, RX_BUF_SIZE)
            };
            if n <= 0 {
                break;
            }
            rx.truncate(n as usize);
            buf = rx;
        }
        Ok(rules)
    }
}

fn get_family_id(fd: i32) -> Result<u16> {
    let mut buf = vec![0u8; NLMSG_HDRLEN + GENL_HDRLEN + 4 + 8];
    let total_len = buf.len();
    buf[0..4].copy_from_slice(&(total_len as u32).to_ne_bytes());
    buf[4..6].copy_from_slice(&GENL_ID_CTRL.to_ne_bytes());
    buf[6..8].copy_from_slice(&(NLM_F_REQUEST as u16).to_ne_bytes());
    buf[16] = CTRL_CMD_GETFAMILY;
    buf[17] = 1;
    let name = b"nomount\0"; // 家族名需 null 结尾 (与 nm.c len=8 一致)
    let nla_len = 4 + name.len();
    buf[20..22].copy_from_slice(&(nla_len as u16).to_ne_bytes());
    buf[22..24].copy_from_slice(&CTRL_ATTR_FAMILY_NAME.to_ne_bytes());
    buf[24..24 + name.len()].copy_from_slice(name);

    let written = unsafe { libc::write(fd, buf.as_ptr() as *const libc::c_void, buf.len()) };
    if written < 0 {
        bail!("netlink write 失败: {}", std::io::Error::last_os_error());
    }
    let mut rx = vec![0u8; RX_BUF_SIZE];
    let n = unsafe { libc::read(fd, rx.as_mut_ptr() as *mut libc::c_void, RX_BUF_SIZE) };
    if n < 0 {
        bail!("netlink read 失败: {}", std::io::Error::last_os_error());
    }
    rx.truncate(n as usize);
    check_error(&rx)?;
    get_attr_u16(&rx, CTRL_ATTR_FAMILY_ID)
        .ok_or_else(|| anyhow!("nomount 家族不存在 (内核未集成 NoMount)"))
}

fn check_error(rx: &[u8]) -> Result<()> {
    if rx.len() >= NLMSG_HDRLEN && u16_from(rx, 4) == NLMSG_ERROR {
        if rx.len() >= 20 {
            let code = i32_from(rx, 16);
            if code != 0 {
                bail!("内核返回错误: {}", -code);
            }
        }
    }
    Ok(())
}

fn u16_from(buf: &[u8], off: usize) -> u16 {
    let mut b = [0u8; 2];
    b.copy_from_slice(&buf[off..off + 2]);
    u16::from_ne_bytes(b)
}

fn u32_from(buf: &[u8], off: usize) -> u32 {
    let mut b = [0u8; 4];
    b.copy_from_slice(&buf[off..off + 4]);
    u32::from_ne_bytes(b)
}

fn i32_from(buf: &[u8], off: usize) -> i32 {
    let mut b = [0u8; 4];
    b.copy_from_slice(&buf[off..off + 4]);
    i32::from_ne_bytes(b)
}

/// 遍历 nla 属性 (attr 起点 = msg + 20)
fn get_attr_payload(msg: &[u8], atype: u16) -> Option<&[u8]> {
    let mut off = NLMSG_HDRLEN + GENL_HDRLEN;
    while off + 4 <= msg.len() {
        let nla_len = u16_from(msg, off) as usize;
        if nla_len < 4 || off + nla_len > msg.len() {
            break;
        }
        let t = u16_from(msg, off + 2);
        if t == atype {
            return Some(&msg[off + 4..off + nla_len]);
        }
        off += (nla_len + 3) & !3;
    }
    None
}

fn get_attr_u16(msg: &[u8], atype: u16) -> Option<u16> {
    let p = get_attr_payload(msg, atype)?;
    if p.len() >= 2 {
        Some(u16_from(p, 0))
    } else {
        None
    }
}

fn get_attr_u32(msg: &[u8], atype: u16) -> Option<u32> {
    let p = get_attr_payload(msg, atype)?;
    if p.len() >= 4 {
        Some(u32_from(p, 0))
    } else {
        None
    }
}

fn get_attr_string(msg: &[u8], atype: u16) -> Option<String> {
    let p = get_attr_payload(msg, atype)?;
    let s = String::from_utf8_lossy(p).into_owned();
    let s = s.trim_end_matches('\0').to_string();
    if s.is_empty() {
        None
    } else {
        Some(s)
    }
}

pub fn version() -> Result<String> {
    let s = NmSocket::new()?;
    let v = s.version()?;
    Ok(v.to_string())
}

pub fn list() -> Result<Vec<(String, String)>> {
    let s = NmSocket::new()?;
    s.list()
}

pub fn add(virtual_path: &str, real_path: &str) -> Result<()> {
    let s = NmSocket::new()?;
    let v = virtual_path.as_bytes();
    let r = real_path.as_bytes();
    let mut payload = Vec::with_capacity(8 + v.len() + r.len());
    payload.extend_from_slice(&0u32.to_ne_bytes());
    payload.extend_from_slice(&(v.len() as u16).to_ne_bytes());
    payload.extend_from_slice(&(r.len() as u16).to_ne_bytes());
    payload.extend_from_slice(v);
    payload.extend_from_slice(r);
    s.mutate_rule(2, &payload)?;
    // 手动规则持久化 (开机重放)
    persist_add_rule(virtual_path, real_path);
    Ok(())
}

pub fn remove(virtual_path: &str) -> Result<()> {
    let s = NmSocket::new()?;
    let v = virtual_path.as_bytes();
    let mut payload = Vec::with_capacity(2 + v.len());
    payload.extend_from_slice(&(v.len() as u16).to_ne_bytes());
    payload.extend_from_slice(v);
    s.mutate_rule(3, &payload)?;
    persist_remove_rules(&[virtual_path.to_string()]);
    Ok(())
}

/// 批量移除规则 (cmd 3, 负载为多条 [u16:v_len][v] 拼接) — 同步持久化
pub fn remove_rules_batch(virtuals: &[String]) -> Result<()> {
    if virtuals.is_empty() {
        return Ok(());
    }
    let s = NmSocket::new()?;
    let mut payload = Vec::new();
    for v in virtuals {
        payload.extend_from_slice(&encode_del_payload(v));
    }
    let rx = s.send_cmd(3, 6, &payload, NLM_F_REQUEST | NLM_F_ACK)?;
    check_error(&rx)?;
    persist_remove_rules(virtuals);
    Ok(())
}

fn encode_del_payload(v: &str) -> Vec<u8> {
    let vb = v.as_bytes();
    let mut payload = Vec::with_capacity(2 + vb.len());
    payload.extend_from_slice(&(vb.len() as u16).to_ne_bytes());
    payload.extend_from_slice(vb);
    payload
}

pub fn clear() -> Result<()> {
    let s = NmSocket::new()?;
    s.clear()?;
    let _ = std::fs::remove_file(CUSTOM_RULES_FILE);
    Ok(())
}

/// UID 排除: 指定 uid 的进程不做路径重定向 (cmd 5=block, 6=unblock, atype 4, payload uid)
pub fn block_uid(uid: u32) -> Result<()> {
    let s = NmSocket::new()?;
    let rx = s.send_cmd(5, 4, &uid.to_ne_bytes(), NLM_F_REQUEST | NLM_F_ACK)?;
    check_error(&rx)?;
    Ok(())
}

pub fn unblock_uid(uid: u32) -> Result<()> {
    let s = NmSocket::new()?;
    let rx = s.send_cmd(6, 4, &uid.to_ne_bytes(), NLM_F_REQUEST | NLM_F_ACK)?;
    check_error(&rx)?;
    Ok(())
}

/// 持久化排除列表 (web 界面: /data/adb/nomount/.exclusion_list — uid 空白/逗号分隔)
pub const EXCLUSION_FILE: &str = "/data/adb/nomount/.exclusion_list";

/// 手动自定义规则持久化文件 (v<TAB>r 每行) — 开机重放
pub const CUSTOM_RULES_FILE: &str = "/data/adb/nomount/custom_rules";

fn read_custom_rules() -> Vec<(String, String)> {
    let content = match std::fs::read_to_string(CUSTOM_RULES_FILE) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };
    content
        .lines()
        .filter_map(|line| {
            let (v, r) = line.split_once('\t')?;
            if v.is_empty() || r.is_empty() {
                None
            } else {
                Some((v.to_string(), r.to_string()))
            }
        })
        .collect()
}

fn write_custom_rules(rules: &[(String, String)]) {
    let content = rules
        .iter()
        .map(|(v, r)| format!("{v}\t{r}"))
        .collect::<Vec<_>>()
        .join("\n");
    let _ = std::fs::create_dir_all("/data/adb/nomount");
    if let Err(e) = std::fs::write(CUSTOM_RULES_FILE, content) {
        log::warn!("nomount: 写 custom_rules 失败: {e}");
    }
}

fn persist_add_rule(v: &str, r: &str) {
    let mut rules = read_custom_rules();
    rules.retain(|(ov, _)| ov != v);
    rules.push((v.to_string(), r.to_string()));
    write_custom_rules(&rules);
}

fn persist_remove_rules(virtuals: &[String]) {
    let mut rules = read_custom_rules();
    rules.retain(|(ov, _)| !virtuals.contains(ov));
    write_custom_rules(&rules);
}

/// 开机重放手动自定义规则 (在模块注入后调用)
pub fn replay_custom_rules() -> Result<()> {
    let rules = read_custom_rules();
    if rules.is_empty() {
        return Ok(());
    }
    let s = NmSocket::new()?;
    s.add_rules_batch(&rules)?;
    log::info!("nomount: 重放 {} 条自定义规则", rules.len());
    Ok(())
}

fn read_exclusions() -> Result<Vec<u32>> {
    let content = match std::fs::read_to_string(EXCLUSION_FILE) {
        Ok(c) => c,
        Err(_) => return Ok(Vec::new()),
    };
    let mut uids = Vec::new();
    for part in content.split(|c: char| c.is_whitespace() || c == ',') {
        if let Ok(uid) = part.trim().parse::<u32>() {
            if !uids.contains(&uid) {
                uids.push(uid);
            }
        }
    }
    Ok(uids)
}

fn write_exclusions(uids: &[u32]) -> Result<()> {
    std::fs::create_dir_all("/data/adb/nomount")?;
    let content = uids
        .iter()
        .map(|u| u.to_string())
        .collect::<Vec<_>>()
        .join("\n");
    std::fs::write(EXCLUSION_FILE, content)?;
    Ok(())
}

pub fn exclude_list() -> Result<Vec<u32>> {
    read_exclusions()
}

pub fn exclude_add(uid: u32) -> Result<()> {
    let mut uids = read_exclusions()?;
    if !uids.contains(&uid) {
        uids.push(uid);
        write_exclusions(&uids)?;
    }
    block_uid(uid)
}

pub fn exclude_remove(uid: u32) -> Result<()> {
    let mut uids = read_exclusions()?;
    uids.retain(|&u| u != uid);
    write_exclusions(&uids)?;
    unblock_uid(uid)
}
