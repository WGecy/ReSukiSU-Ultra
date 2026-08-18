package com.tesla.resukisuultra.magica

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * IO 调度器固化专用开机接收器 (独立于 auto_jailbreak 控制的 BootCompletedReceiver)
 * 开机应用固化调度器 (DataStore io_scheduler → root 写 sd* / nvme* 盘)
 */
class IoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        try {
            IoSchedBootApplier.apply(context.applicationContext)
            Log.i(TAG, "IoSchedBootApplier applied from boot action: $action")
        } catch (e: Throwable) {
            Log.e(TAG, "apply io scheduler failed: $action", e)
        }
    }

    private companion object {
        const val TAG = "IoBootReceiver"
    }
}
