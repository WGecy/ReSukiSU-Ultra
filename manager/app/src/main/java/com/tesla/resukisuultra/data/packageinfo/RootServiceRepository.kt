package com.tesla.resukisuultra.data.packageinfo

import android.app.Application
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class RootServiceRepository(
    private val application: Application,
) {
    private val TAG = "RootServiceRepo"
    private val requestMutex = Mutex()

    // 包安装/卸载/更新 → 失效缓存 (新 App 能立刻刷新出来)
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            cachedPackages = null
            cacheTimestamp = 0
        }
    }

    init {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        application.registerReceiver(packageReceiver, filter)
    }

    // 进程内缓存 30 分钟 (packageReceiver 在包变化时失效)
    @Volatile
    private var cachedPackages: List<PackageInfo>? = null
    @Volatile
    private var cacheTimestamp: Long = 0

    /**
     * 根本修复: 直接使用 app 自身 PackageManager (QUERY_ALL_PACKAGES, 主用户全量应用)。
     * 不再依赖 RootService (KsuService) — root 服务频繁 bind/start 会被系统限制,
     * 表现为列表为空/一直转圈。主用户场景下 pm 足够稳定且永不阻塞。
     */
    suspend fun getInstalledPackages(forceRefresh: Boolean = false): List<PackageInfo> = requestMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPackages != null && now - cacheTimestamp < 30 * 60 * 1000) {
            return cachedPackages!!
        }
        val result = loadFromPackageManager()
        cachedPackages = result
        cacheTimestamp = System.currentTimeMillis()
        result
    }

    private suspend fun loadFromPackageManager(): List<PackageInfo> =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    application.packageManager.getInstalledPackages(
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    application.packageManager.getInstalledPackages(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "PackageManager 获取应用列表失败", e)
                emptyList()
            }
        }
}
