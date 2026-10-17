//! NoMount 内核客户端 —— 同时支持两代内核协议
//!
//! * **v2.x (keyring)**：上游 maxsteeel/nomount 2.x 起的接口。用户态在 4 KiB 页对齐的
//!   `struct nm_payload` 里填好请求，自己 mmap 一页（glibc/bionic 的 malloc 只保证
//!   16 字节对齐），再用 `add_key("nomount", "trigger", &payload_ptr, 8,
//!   KEY_SPEC_SESSION_KEYRING)` 把指针交给内核，内核 `key_preparse` 直接读写该页并回填
//!   `status` / `buffer`。能力更全：whiteout、虚拟目录、按 UID 规则、`uid list`。
//! * **v1.x (Generic Netlink 家族 "nomount")**：旧内核的接口，保留为后备实现，
//!   协议选择见 [`Backend`]。
//!
//! 运行时自动选协议（[`version()`]）：先问 keyring，问不到再退到 netlink —— 同一个
//! ksud 二进制既能配新内核，也能配仍带 v1 hook 的旧内核。

pub mod cli;
pub mod mount;

use std::sync::Mutex;

use anyhow::{Result, anyhow, bail};

/// 与内核 `NOMOUNT_MAGIC_SIG` ("NOMOUNT" 的 ASCII) 一致
const NOMOUNT_MAGIC_SIG: u64 = 0x4E4F_4D4F_554E_54;
const PATH_MAX: usize = 4096;

/* ---- v2 (keyring) 协议常量, 对应内核 enum { NM_CMD_* } ---- */
const NM_CMD_GET_VERSION: u32 = 1;
const NM_CMD_ADD_RULE: u32 = 2;
const NM_CMD_DEL_RULE: u32 = 3;
const NM_CMD_ADD_UID: u32 = 4;
const NM_CMD_DEL_UID: u32 = 5;
const NM_CMD_CLEAR_ALL: u32 = 6;
const NM_CMD_CLEAR_RULES: u32 = 7;
const NM_CMD_CLEAR_UIDS: u32 = 8;
const NM_CMD_GET_LIST: u32 = 9;
const NM_CMD_GET_UIDS: u32 = 10;

/* 规则标志 (内核 nomount.h, v2) */
pub const NM_FLAG_IS_DIR: u32 = 1 << 0;
pub const NM_FLAG_VIRTUAL_DIR: u32 = 1 << 1;
pub const NM_FLAG_WHITEOUT: u32 = 1 << 2;

const KEY_SPEC_SESSION_KEYRING: i32 = -3;
/// aarch64 的 add_key 系统调用号 (各架构 ABI 稳定); 传给 libc::syscall 需要 c_long
const SYS_ADD_KEY: std::os::raw::c_long = 217;

/* ---- v1 (netlink) 协议常量 ---- */
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

/* v1 命令号 / 属性号 */
const V1_CMD_GET_VERSION: u8 = 1;
const V1_CMD_ADD_RULE: u8 = 2;
const V1_CMD_DEL_RULE: u8 = 3;
const V1_CMD_CLEAR_ALL: u8 = 4;
const V1_CMD_ADD_UID: u8 = 5;
const V1_CMD_DEL_UID: u8 = 6;
const V1_CMD_GET_LIST: u8 = 7;
const V1_ATTR_VIRTUAL_PATH: u16 = 1;
const V1_ATTR_REAL_PATH: u16 = 2;
const V1_ATTR_UID: u16 = 4;
const V1_ATTR_VERSION: u16 = 5;
const V1_ATTR_PAYLOAD: u16 = 6;

/// 规则 (ADD/DEL/GET_LIST 共用)。v1 协议只有"虚拟路径 → 真实路径"，
/// whiteout / 虚拟目录 / 按 UID 规则只有 v2 内核支持。
#[derive(Debug, Clone)]
pub struct Rule {
    pub virtual_path: String,
    pub real_path: String,
    /// 0 = 对所有 UID 生效
    pub uid: u32,
    pub flags: u32,
}

impl Rule {
    pub fn is_whiteout(&self) -> bool {
        self.flags & NM_FLAG_WHITEOUT != 0
    }
    pub fn is_dir(&self) -> bool {
        self.flags & NM_FLAG_IS_DIR != 0
    }
    fn plain(virtual_path: impl Into<String>, real_path: impl Into<String>) -> Self {
        Rule {
            virtual_path: virtual_path.into(),
            real_path: real_path.into(),
            uid: 0,
            flags: 0,
        }
    }
}

/// 这个规则能否用 v1 内核表达 (旧内核只认普通重定向)
fn rule_supported_by_v1(r: &Rule) -> bool {
    r.flags == 0 && r.uid == 0
}

// ============================================================================
//  后端选择
// ============================================================================
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Backend {
    /// keyring 协议 (上游 nomount >= v2.0)
    Keyring,
    /// Generic Netlink 家族 "nomount" (旧内核)
    Netlink,
}

/// 探测内核 NoMount 协议版本。
///
/// 返回 v2 内核对外的版本串 (如 `"21"`, 带 commit 计数时为 `"21 (1234)"`)；
/// v1 内核返回 `"legacy-netlink"`。两种都没有则报错。
fn probe() -> Result<(Backend, String)> {
    let keyring_err = match keyring::version() {
        Ok(v) => return Ok((Backend::Keyring, v)),
        Err(e) => e,
    };
    match netlink::version() {
        Ok(v) => Ok((Backend::Netlink, format!("legacy-netlink (v{v})"))),
        Err(e) => Err(anyhow!(
            "内核未集成 NoMount（keyring: {keyring_err}；netlink: {e}）"
        )),
    }
}

fn backend() -> Result<(Backend, String)> {
    probe()
}

// ============================================================================
//  v2: keyring 传输层
// ============================================================================
mod keyring {
    use super::*;

    /// 内核 `struct nm_payload` (packed, 共 4096 字节)
    #[repr(C, packed)]
    #[derive(Clone, Copy)]
    pub(super) struct NmPayload {
        pub(super) magic: u64,
        pub(super) cmd: u32,
        pub(super) target_uid: u32,
        pub(super) status: i32,
        pub(super) arg1: u32,
        pub(super) data_size: u32,
        pub(super) buffer: [u8; 4068],
    }

    // magic(8)+cmd(4)+target_uid(4)+status(4)+arg1(4)+data_size(4)+buffer(4068) = 4096
    const _: () = assert!(std::mem::size_of::<NmPayload>() == 4096);

    /// 内核 `struct nm_rule_hdr` (packed): flags, uid, v_len, r_len
    pub(super) const RULE_HDR_LEN: usize = 12;

    /// 与 `maxsteeel/nomount` 的 `nm_send_payload()` 等价
    fn send_payload(payload: &mut NmPayload) -> Result<i32> {
        payload.status = -1;
        let ptr = payload as *mut NmPayload as usize as u64;
        let ret = unsafe {
            libc::syscall(
                SYS_ADD_KEY,
                b"nomount\0".as_ptr(),
                b"trigger\0".as_ptr(),
                &ptr as *const u64,
                std::mem::size_of::<u64>(),
                KEY_SPEC_SESSION_KEYRING,
            )
        };
        if ret == -1 {
            let e = std::io::Error::last_os_error();
            // ENOKEY/EOPNOTSUPP: 内核没注册 "nomount" key type
            return Err(anyhow!("add_key 失败: {e}"));
        }
        let status = unsafe { std::ptr::read_volatile(std::ptr::addr_of!(payload.status)) };
        if status < 0 {
            return Err(anyhow!("内核返回错误: {}", -status));
        }
        Ok(status)
    }

    /// 一页对齐的 payload 缓冲 (mmap 单独映射, 保证 4 KiB 对齐)
    struct Page {
        ptr: *mut u8,
    }

    impl Page {
        fn new() -> Result<Self> {
            let ptr = unsafe {
                libc::mmap(
                    std::ptr::null_mut(),
                    PATH_MAX,
                    libc::PROT_READ | libc::PROT_WRITE,
                    libc::MAP_PRIVATE | libc::MAP_ANONYMOUS,
                    -1,
                    0,
                )
            };
            if ptr == libc::MAP_FAILED {
                bail!("mmap 失败: {}", std::io::Error::last_os_error());
            }
            Ok(Page { ptr: ptr as *mut u8 })
        }

        /// 清零并初始化 magic
        fn reset(&self) -> &mut NmPayload {
            let p = self.ptr as *mut NmPayload;
            unsafe {
                std::ptr::write_bytes(self.ptr, 0, PATH_MAX);
                (*p).magic = NOMOUNT_MAGIC_SIG;
                &mut *p
            }
        }
    }

    impl Drop for Page {
        fn drop(&mut self) {
            unsafe { libc::munmap(self.ptr as *mut libc::c_void, PATH_MAX) };
        }
    }

    /// 串行化: keyring 是进程级资源, 同时发多条 payload 没必要也不安全
    static PAGE_LOCK: Mutex<()> = Mutex::new(());

    pub(super) fn with_payload<R>(f: impl FnOnce(&mut NmPayload) -> Result<R>) -> Result<R> {
        let _guard = PAGE_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let page = Page::new()?;
        let payload = page.reset();
        f(payload)
    }

    /// `NM_CMD_GET_VERSION` 返回的版本串
    pub(super) fn version() -> Result<String> {
        with_payload(|p| {
            p.cmd = NM_CMD_GET_VERSION;
            send_payload(p)?;
            let len = (p.data_size as usize).min(p.buffer.len());
            let s = String::from_utf8_lossy(&p.buffer[..len])
                .trim_end_matches('\0')
                .to_string();
            if s.is_empty() {
                bail!("版本属性为空");
            }
            Ok(s)
        })
    }

    /// 追加一条 ADD 头 + 路径: flags / uid / v_len / r_len / v / r
    pub(super) fn push_add(buf: &mut Vec<u8>, r: &Rule) -> Result<()> {
        let v = r.virtual_path.as_bytes();
        let real = r.real_path.as_bytes();
        if v.is_empty() || v.len() >= PATH_MAX || real.len() >= PATH_MAX {
            bail!("路径长度非法 (虚拟路径不能为空, 且需 < {PATH_MAX})");
        }
        if buf.len() + RULE_HDR_LEN + v.len() + real.len() > 4068 {
            bail!("单次发送的规则过多, 请分批");
        }
        buf.extend_from_slice(&r.flags.to_ne_bytes());
        buf.extend_from_slice(&r.uid.to_ne_bytes());
        buf.extend_from_slice(&(v.len() as u16).to_ne_bytes());
        buf.extend_from_slice(&(real.len() as u16).to_ne_bytes());
        buf.extend_from_slice(v);
        buf.extend_from_slice(real);
        Ok(())
    }

    /// 追加一条 DEL 头: uid / v_len / v
    pub(super) fn push_del(buf: &mut Vec<u8>, virtual_path: &str, uid: u32) {
        let v = virtual_path.as_bytes();
        buf.extend_from_slice(&uid.to_ne_bytes());
        buf.extend_from_slice(&(v.len() as u16).to_ne_bytes());
        buf.extend_from_slice(v);
    }

    pub(super) fn send(buf: &mut NmPayload, cmd: u32, data: &[u8]) -> Result<()> {
        buf.cmd = cmd;
        buf.arg1 = 0;
        buf.data_size = data.len() as u32;
        buf.buffer[..data.len()].copy_from_slice(data);
        send_payload(buf)?;
        Ok(())
    }

    pub(super) fn send_uid(cmd: u32, uid: u32) -> Result<()> {
        with_payload(|p| {
            p.cmd = cmd;
            p.target_uid = uid;
            send_payload(p)?;
            Ok(())
        })
    }

    pub(super) fn send_simple(cmd: u32) -> Result<()> {
        with_payload(|p| {
            p.cmd = cmd;
            send_payload(p)?;
            Ok(())
        })
    }

    /// GET_LIST: 每次返回一页, `arg1` 为下一条起始索引; 循环到 `data_size == 0`
    pub(super) fn list() -> Result<Vec<Rule>> {
        with_payload(|p| {
            let mut out = Vec::new();
            p.cmd = NM_CMD_GET_LIST;
            loop {
                p.arg1 = 0;
                p.data_size = 0;
                send_payload(p)?;
                let size = (p.data_size as usize).min(p.buffer.len());
                if size == 0 {
                    break;
                }
                let mut off = 0usize;
                while off + RULE_HDR_LEN <= size {
                    let flags = u32::from_ne_bytes(p.buffer[off..off + 4].try_into().unwrap());
                    let uid = u32::from_ne_bytes(p.buffer[off + 4..off + 8].try_into().unwrap());
                    let v_len =
                        u16::from_ne_bytes(p.buffer[off + 8..off + 10].try_into().unwrap()) as usize;
                    let r_len =
                        u16::from_ne_bytes(p.buffer[off + 10..off + 12].try_into().unwrap()) as usize;
                    let v_start = off + RULE_HDR_LEN;
                    let r_start = v_start + v_len;
                    if r_start + r_len > size {
                        bail!("GET_LIST 返回的规则数据被截断");
                    }
                    out.push(Rule {
                        virtual_path: String::from_utf8_lossy(&p.buffer[v_start..r_start])
                            .into_owned(),
                        real_path: String::from_utf8_lossy(&p.buffer[r_start..r_start + r_len])
                            .into_owned(),
                        uid,
                        flags,
                    });
                    off = r_start + r_len;
                }
            }
            Ok(out)
        })
    }

    /// 内核侧隔离 UID 列表 (v2 新增 NM_CMD_GET_UIDS)
    pub(super) fn list_uids() -> Result<Vec<u32>> {
        with_payload(|p| {
            let mut out = Vec::new();
            p.cmd = NM_CMD_GET_UIDS;
            loop {
                p.arg1 = 0;
                p.data_size = 0;
                send_payload(p)?;
                let size = (p.data_size as usize).min(p.buffer.len());
                if size == 0 {
                    break;
                }
                for c in p.buffer[..size].chunks_exact(4) {
                    out.push(u32::from_ne_bytes(c.try_into().unwrap()));
                }
            }
            Ok(out)
        })
    }
}

// ============================================================================
//  v1: Generic Netlink 家族 "nomount" 传输层 (旧内核后备)
// ============================================================================
mod netlink {
    use super::*;

    pub(super) struct NmSocket {
        fd: i32,
        family_id: u16,
    }

    impl NmSocket {
        pub(super) fn new() -> Result<Self> {
            let fd =
                unsafe { libc::socket(libc::AF_NETLINK, libc::SOCK_RAW, libc::NETLINK_GENERIC) };
            if fd < 0 {
                bail!(
                    "netlink socket 创建失败: {}",
                    std::io::Error::last_os_error()
                );
            }
            let mut addr: libc::sockaddr_nl = unsafe { std::mem::zeroed() };
            addr.nl_family = libc::AF_NETLINK as u16;
            addr.nl_pid = 0;
            addr.nl_groups = 0;
            let ret = unsafe {
                libc::bind(
                    fd,
                    &addr as *const libc::sockaddr_nl as *const libc::sockaddr,
                    std::mem::size_of::<libc::sockaddr_nl>() as libc::socklen_t,
                )
            };
            if ret < 0 {
                unsafe { libc::close(fd) };
                bail!("netlink bind 失败: {}", std::io::Error::last_os_error());
            }
            let family_id = get_family_id(fd)?;
            Ok(NmSocket { fd, family_id })
        }

        /// 发送带 ACK 的 genl 命令; 失败直接返回 Err
        pub(super) fn command(&self, cmd: u8, atype: u16, payload: &[u8]) -> Result<()> {
            let rx = self.send_cmd(cmd, atype, payload, NLM_F_REQUEST | NLM_F_ACK)?;
            check_error(&rx)
        }

        /// 发送 genl 命令, 返回首个响应 buffer
        fn send_cmd(&self, cmd: u8, atype: u16, payload: &[u8], flags: u16) -> Result<Vec<u8>> {
            let has_attr = !payload.is_empty() || atype != 0;
            let total_len =
                NLMSG_HDRLEN + GENL_HDRLEN + if has_attr { 4 + payload.len() } else { 0 };
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

            let written =
                unsafe { libc::write(self.fd, buf.as_ptr() as *const libc::c_void, buf.len()) };
            if written < 0 {
                bail!("netlink write 失败: {}", std::io::Error::last_os_error());
            }

            let mut rx = vec![0u8; RX_BUF_SIZE];
            let n =
                unsafe { libc::read(self.fd, rx.as_mut_ptr() as *mut libc::c_void, RX_BUF_SIZE) };
            if n < 0 {
                bail!("netlink read 失败: {}", std::io::Error::last_os_error());
            }
            rx.truncate(n as usize);
            Ok(rx)
        }

        pub(super) fn version(&self) -> Result<u32> {
            let rx = self.send_cmd(V1_CMD_GET_VERSION, 0, &[], NLM_F_REQUEST | NLM_F_ACK)?;
            check_error(&rx)?;
            get_attr_u32(&rx, V1_ATTR_VERSION).ok_or_else(|| anyhow!("版本属性缺失"))
        }

        /// 批量添加重定向规则 (cmd 2, 负载为多条 [u32:0][u16:v_len][u16:r_len][v][r] 拼接)
        pub(super) fn add_rules_batch(&self, rules: &[Rule]) -> Result<()> {
            if rules.is_empty() {
                return Ok(());
            }
            let mut payload = Vec::new();
            for r in rules {
                if !rule_supported_by_v1(r) {
                    bail!("内核为旧版 NoMount (netlink)，不支持 whiteout/虚拟目录/按 UID 规则");
                }
                let vb = r.virtual_path.as_bytes();
                let rb = r.real_path.as_bytes();
                payload.extend_from_slice(&0u32.to_ne_bytes());
                payload.extend_from_slice(&(vb.len() as u16).to_ne_bytes());
                payload.extend_from_slice(&(rb.len() as u16).to_ne_bytes());
                payload.extend_from_slice(vb);
                payload.extend_from_slice(rb);
            }
            let rx = self.send_cmd(V1_CMD_ADD_RULE, V1_ATTR_PAYLOAD, &payload, NLM_F_REQUEST | NLM_F_ACK)?;
            check_error(&rx)?;
            Ok(())
        }

        pub(super) fn clear(&self) -> Result<()> {
            let rx = self.send_cmd(V1_CMD_CLEAR_ALL, 0, &[], NLM_F_REQUEST | NLM_F_ACK)?;
            check_error(&rx)?;
            Ok(())
        }

        /// 规则列表 (cmd 7, DUMP) → Vec<Rule> (v1 无 flags/uid)
        pub(super) fn list(&self) -> Result<Vec<Rule>> {
            let mut rules = Vec::new();
            let mut buf = self.send_cmd(V1_CMD_GET_LIST, 0, &[], NLM_F_REQUEST | NLM_F_DUMP)?;
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
                        let v = get_attr_string(&buf[offset..offset + msg_len], V1_ATTR_VIRTUAL_PATH);
                        let r = get_attr_string(&buf[offset..offset + msg_len], V1_ATTR_REAL_PATH);
                        if let (Some(v), Some(r)) = (v, r) {
                            rules.push(Rule::plain(v, r));
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
        let mut buf = [0u8; NLMSG_HDRLEN + GENL_HDRLEN + 4 + 8];
        let total_len = buf.len();
        buf[0..4].copy_from_slice(&(total_len as u32).to_ne_bytes());
        buf[4..6].copy_from_slice(&GENL_ID_CTRL.to_ne_bytes());
        buf[6..8].copy_from_slice(&NLM_F_REQUEST.to_ne_bytes());
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

    pub(super) fn version() -> Result<u32> {
        NmSocket::new()?.version()
    }

    fn check_error(rx: &[u8]) -> Result<()> {
        if rx.len() >= NLMSG_HDRLEN && u16_from(rx, 4) == NLMSG_ERROR && rx.len() >= 20 {
            let code = i32_from(rx, 16);
            if code != 0 {
                bail!("内核返回错误: {}", -code);
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
        if s.is_empty() { None } else { Some(s) }
    }
}

// ============================================================================
//  对外 API (按后端分发)
// ============================================================================

/// 内核 NoMount 版本 (v2 为整数串, v1 为 `legacy-netlink (v10)`)
pub fn version() -> Result<String> {
    Ok(backend()?.1)
}

/// 规则列表
pub fn list() -> Result<Vec<Rule>> {
    match backend()?.0 {
        Backend::Keyring => keyring::list(),
        Backend::Netlink => netlink::NmSocket::new()?.list(),
    }
}

/// 批量添加规则 (一条 payload 内多条)
pub fn add_rules_batch(rules: &[Rule]) -> Result<()> {
    if rules.is_empty() {
        return Ok(());
    }
    match backend()?.0 {
        Backend::Keyring => keyring::with_payload(|p| {
            let mut buf = Vec::new();
            for r in rules {
                keyring::push_add(&mut buf, r)?;
            }
            keyring::send(p, NM_CMD_ADD_RULE, &buf)
        }),
        Backend::Netlink => netlink::NmSocket::new()?.add_rules_batch(rules),
    }
}

/// 批量删除 (按虚拟路径; v2 额外按 uid 定位)
pub fn del_rules_batch(virtual_paths: &[String], uids: &[u32]) -> Result<()> {
    if virtual_paths.is_empty() {
        return Ok(());
    }
    match backend()?.0 {
        Backend::Keyring => keyring::with_payload(|p| {
            let mut buf = Vec::new();
            for (i, v) in virtual_paths.iter().enumerate() {
                keyring::push_del(&mut buf, v, uids.get(i).copied().unwrap_or(0));
            }
            keyring::send(p, NM_CMD_DEL_RULE, &buf)
        }),
        // v1 的 DEL 负载只有 [u16:v_len][v] (无 uid), 逐条发
        Backend::Netlink => {
            let s = netlink::NmSocket::new()?;
            for v in virtual_paths {
                let vb = v.as_bytes();
                let mut payload = Vec::with_capacity(2 + vb.len());
                payload.extend_from_slice(&(vb.len() as u16).to_ne_bytes());
                payload.extend_from_slice(vb);
                s.command(V1_CMD_DEL_RULE, V1_ATTR_PAYLOAD, &payload)?;
            }
            Ok(())
        }
    }
}

pub fn add(virtual_path: &str, real_path: &str) -> Result<()> {
    add_rules_batch(&[Rule::plain(virtual_path, real_path)])?;
    // 手动规则持久化 (开机重放)
    persist_add_rule(virtual_path, real_path);
    Ok(())
}

pub fn remove(virtual_path: &str) -> Result<()> {
    del_rules_batch(&[virtual_path.to_string()], &[0])?;
    persist_remove_rules(&[virtual_path.to_string()]);
    Ok(())
}

/// 批量移除规则 (同步持久化)
pub fn remove_rules_batch(virtuals: &[String]) -> Result<()> {
    if virtuals.is_empty() {
        return Ok(());
    }
    del_rules_batch(virtuals, &[])?;
    persist_remove_rules(virtuals);
    Ok(())
}

pub fn clear() -> Result<()> {
    match backend()?.0 {
        Backend::Keyring => keyring::send_simple(NM_CMD_CLEAR_ALL)?,
        Backend::Netlink => netlink::NmSocket::new()?.clear()?,
    }
    let _ = std::fs::remove_file(CUSTOM_RULES_FILE);
    Ok(())
}

/// UID 隔离: 该 uid 的进程看到原始文件系统
pub fn block_uid(uid: u32) -> Result<()> {
    match backend()?.0 {
        Backend::Keyring => keyring::send_uid(NM_CMD_ADD_UID, uid),
        Backend::Netlink => {
            let s = netlink::NmSocket::new()?;
            s.command(V1_CMD_ADD_UID, V1_ATTR_UID, &uid.to_ne_bytes())
        }
    }
}

pub fn unblock_uid(uid: u32) -> Result<()> {
    match backend()?.0 {
        Backend::Keyring => keyring::send_uid(NM_CMD_DEL_UID, uid),
        Backend::Netlink => {
            let s = netlink::NmSocket::new()?;
            s.command(V1_CMD_DEL_UID, V1_ATTR_UID, &uid.to_ne_bytes())
        }
    }
}

/// 内核侧隔离 UID 列表 (只有 v2 内核提供)
pub fn list_uids() -> Result<Vec<u32>> {
    match backend()?.0 {
        Backend::Keyring => keyring::list_uids(),
        Backend::Netlink => bail!("旧版 NoMount 内核不提供 uid 列表查询"),
    }
}

pub fn clear_rules() -> Result<()> {
    match backend()?.0 {
        Backend::Keyring => keyring::send_simple(NM_CMD_CLEAR_RULES),
        // v1 没有"只清规则"命令, 用 clear all 代替
        Backend::Netlink => netlink::NmSocket::new()?.clear(),
    }
}

pub fn clear_uids() -> Result<()> {
    match backend()?.0 {
        Backend::Keyring => keyring::send_simple(NM_CMD_CLEAR_UIDS),
        Backend::Netlink => bail!("旧版 NoMount 内核不提供 uid 清空命令"),
    }
}

/// 总开关状态文件 (1=启用)
pub const ENABLED_FILE: &str = "/data/adb/ksu/nomount/enabled";

pub fn is_enabled() -> bool {
    std::fs::read_to_string(ENABLED_FILE)
        .map(|s| s.trim() == "1")
        .unwrap_or(false)
}

pub fn set_enabled(enabled: bool) -> Result<()> {
    let _ = std::fs::create_dir_all("/data/adb/ksu/nomount");
    std::fs::write(ENABLED_FILE, if enabled { "1" } else { "0" })?;
    if enabled {
        // 启用: 注入模块规则 + 重放自定义规则
        crate::android::nomount::mount::inject_modules()?;
        replay_custom_rules()?;
    } else {
        // 禁用: 清空全部规则
        clear()?;
    }
    Ok(())
}

/// 持久化排除列表 (uid 空白/逗号分隔)
pub const EXCLUSION_FILE: &str = "/data/adb/ksu/nomount/.exclusion_list";

/// 手动自定义规则持久化文件 (v<TAB>r 每行) — 开机重放
pub const CUSTOM_RULES_FILE: &str = "/data/adb/ksu/nomount/custom_rules";

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
    let _ = std::fs::create_dir_all("/data/adb/ksu/nomount");
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
    let batch: Vec<Rule> = rules
        .iter()
        .map(|(v, r)| Rule::plain(v.clone(), r.clone()))
        .collect();
    add_rules_batch(&batch)?;
    log::info!("nomount: 重放 {} 条自定义规则", batch.len());
    Ok(())
}

/// 聚合快照: 一次返回全部数据 (管理器进入页面只调一次)
pub fn snapshot() -> Result<String> {
    let backend = backend()?;
    let version = backend.1;
    let rules = list().unwrap_or_default();
    let modules = crate::android::nomount::mount::modules().unwrap_or_default();
    let exclusions = read_exclusions().unwrap_or_default();

    let mut out = String::from("{\"supported\": true, \"proto\": ");
    out.push_str(match backend.0 {
        Backend::Keyring => "\"keyring\"",
        Backend::Netlink => "\"netlink\"",
    });
    out.push_str(", \"version\": ");
    out.push_str(&format!("\"{}\"", cli::escape_json(&version)));
    out.push_str(", \"modules\": [");
    for (i, m) in modules.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!(
            "{{\"id\": \"{}\", \"name\": \"{}\", \"version\": \"{}\", \"author\": \"{}\", \"description\": \"{}\", \"disabled\": {}, \"file_count\": {}, \"loaded\": {}}}",
            cli::escape_json(&m.id),
            cli::escape_json(&m.name),
            cli::escape_json(&m.version),
            cli::escape_json(&m.author),
            cli::escape_json(&m.description),
            m.disabled,
            m.file_count,
            m.loaded
        ));
    }
    out.push_str("], \"rules\": [");
    for (i, r) in rules.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!(
            "{{\"virtual\": \"{}\", \"real\": \"{}\", \"uid\": {}, \"whiteout\": {}, \"virtual_dir\": {}}}",
            cli::escape_json(&r.virtual_path),
            cli::escape_json(&r.real_path),
            r.uid,
            r.is_whiteout(),
            r.flags & NM_FLAG_VIRTUAL_DIR != 0
        ));
    }
    out.push_str("], \"exclusions\": [");
    for (i, u) in exclusions.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&u.to_string());
    }
    out.push_str("]}");
    Ok(out)
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
    std::fs::create_dir_all("/data/adb/ksu/nomount")?;
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
