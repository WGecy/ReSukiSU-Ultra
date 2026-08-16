// SPDX-License-Identifier: GPL-2.0
/*
 * netisolate - UID 级联网阻止 (内核态)
 *
 * 原理: netfilter LOCAL_OUT hook, 检查 socket 属主 UID 是否在阻止列表
 *       → 是则 NF_DROP (阻止出站连接)
 *
 * supercall 命令 (通过 KSU supercall ioctl):
 *   CMD_NETISOLATE_ENABLE      启用/禁用 (0/1)
 *   CMD_NETISOLATE_UID_ADD     添加 UID
 *   CMD_NETISOLATE_UID_REMOVE  移除 UID
 *   CMD_NETISOLATE_UID_CLEAR   清空
 *   CMD_NETISOLATE_UID_LIST    查询列表
 *
 * 2026-08-16: ReSukiSU Ultra 联网阻止功能
 */
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/skbuff.h>
#include <linux/net.h>
#include <net/sock.h>
#include <linux/susfs_def.h>

#define NETISOLATE_MAX_UID 256

static unsigned int netisolate_uids[NETISOLATE_MAX_UID];
static unsigned int netisolate_uid_count;
static bool netisolate_enabled;
static DEFINE_SPINLOCK(netisolate_lock);

static bool netisolate_uid_blocked(kuid_t uid)
{
	unsigned int i;
	unsigned int target = from_kuid(&init_user_ns, uid);

	if (!netisolate_enabled || netisolate_uid_count == 0)
		return false;

	spin_lock(&netisolate_lock);
	for (i = 0; i < netisolate_uid_count; i++) {
		if (netisolate_uids[i] == target) {
			spin_unlock(&netisolate_lock);
			return true;
		}
	}
	spin_unlock(&netisolate_lock);
	return false;
}

static unsigned int netisolate_hook(void *priv, struct sk_buff *skb,
				    const struct nf_hook_state *state)
{
	struct sock *sk;

	if (!netisolate_enabled || !skb)
		return NF_ACCEPT;

	sk = skb->sk;
	if (!sk || !sk->sk_uid.val)
		return NF_ACCEPT;

	if (netisolate_uid_blocked(sk->sk_uid)) {
		pr_info("netisolate: blocked uid=%u\n",
			from_kuid(&init_user_ns, sk->sk_uid));
		return NF_DROP;
	}
	return NF_ACCEPT;
}

static struct nf_hook_ops netisolate_ops[] = {
	{
		.hook = netisolate_hook,
		.pf = NFPROTO_INET,
		.hooknum = NF_INET_LOCAL_OUT,
		.priority = NF_IP_PRI_FIRST,
	},
};

/* ===== supercall 接口 ===== */

void netisolate_set_enabled(bool enable)
{
	spin_lock(&netisolate_lock);
	netisolate_enabled = enable;
	spin_unlock(&netisolate_lock);
	pr_info("netisolate: %s\n", enable ? "enabled" : "disabled");
}

int netisolate_uid_add(unsigned int uid)
{
	unsigned int i;

	spin_lock(&netisolate_lock);
	if (netisolate_uid_count >= NETISOLATE_MAX_UID) {
		spin_unlock(&netisolate_lock);
		return -ENOSPC;
	}
	for (i = 0; i < netisolate_uid_count; i++) {
		if (netisolate_uids[i] == uid) {
			spin_unlock(&netisolate_lock);
			return 0;
		}
	}
	netisolate_uids[netisolate_uid_count++] = uid;
	spin_unlock(&netisolate_lock);
	pr_info("netisolate: add uid=%u (count=%u)\n", uid, netisolate_uid_count);
	return 0;
}

int netisolate_uid_remove(unsigned int uid)
{
	unsigned int i, j;
	bool found = false;

	spin_lock(&netisolate_lock);
	for (i = 0; i < netisolate_uid_count; i++) {
		if (netisolate_uids[i] == uid) {
			for (j = i; j < netisolate_uid_count - 1; j++)
				netisolate_uids[j] = netisolate_uids[j + 1];
			netisolate_uid_count--;
			found = true;
			break;
		}
	}
	spin_unlock(&netisolate_lock);
	if (found)
		pr_info("netisolate: remove uid=%u (count=%u)\n", uid, netisolate_uid_count);
	return found ? 0 : -ENOENT;
}

void netisolate_uid_clear(void)
{
	spin_lock(&netisolate_lock);
	netisolate_uid_count = 0;
	spin_unlock(&netisolate_lock);
	pr_info("netisolate: clear all\n");
}

unsigned int netisolate_get_uid_count(void)
{
	return netisolate_uid_count;
}

void netisolate_get_uids(unsigned int *buf, unsigned int max)
{
	unsigned int i;
	unsigned int n = netisolate_uid_count < max ? netisolate_uid_count : max;

	spin_lock(&netisolate_lock);
	for (i = 0; i < n; i++)
		buf[i] = netisolate_uids[i];
	spin_unlock(&netisolate_lock);
}

bool netisolate_is_enabled(void)
{
	return netisolate_enabled;
}

static int __init netisolate_init(void)
{
	int ret;

	ret = nf_register_net_hooks(&init_net, netisolate_ops,
				    ARRAY_SIZE(netisolate_ops));
	if (ret) {
		pr_err("netisolate: nf_register_net_hooks failed (%d)\n", ret);
		return ret;
	}
	pr_info("netisolate: initialized (UID network isolation)\n");
	return 0;
}

static void __exit netisolate_exit(void)
{
	nf_unregister_net_hooks(&init_net, netisolate_ops,
				ARRAY_SIZE(netisolate_ops));
	pr_info("netisolate: exited\n");
}

module_init(netisolate_init);
module_exit(netisolate_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("UID-level network isolation (ReSukiSU Ultra)");
