package com.tesla.resukisuultra.magica;

import static com.tesla.resukisuultra.magica.AppZygotePreload.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        var action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"com.tesla.resukisuultra.magica.LAUNCH".equals(action)) {
            return;
        }
        try {
            // 开机应用 IO 调度器配置固化 (管理器选择 → root 写 sysfs)
            IoSchedBootApplier.INSTANCE.apply(context.getApplicationContext());
        } catch (Throwable e) {
            Log.e(TAG, "Failed to apply io scheduler config from boot action: " + action, e);
        }
        try {
            context.startService(new Intent(context, MagicaService.class));
            Log.i(TAG, "MagicaService started from boot action: " + action);
        } catch (Throwable e) {

            Log.e(TAG, "Failed to start MagicaService from boot action: " + action, e);
        }
    }
}
