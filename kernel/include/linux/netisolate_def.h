/* SPDX-License-Identifier: GPL-2.0 */
/* netisolate supercall 命令定义 (ReSukiSU-Ultra) */
#ifndef KSU_NETISOLATE_DEF_H
#define KSU_NETISOLATE_DEF_H

/* 走 SUSFS_MAGIC reboot supercall 通道 */
#define CMD_NETISOLATE_ENABLE     0x555d0
#define CMD_NETISOLATE_UID_ADD    0x555d1
#define CMD_NETISOLATE_UID_REMOVE 0x555d2
#define CMD_NETISOLATE_UID_CLEAR  0x555d3
#define CMD_NETISOLATE_UID_LIST   0x555d4
#define CMD_NETISOLATE_GET_STATE  0x555d5

#endif /* KSU_NETISOLATE_DEF_H */
