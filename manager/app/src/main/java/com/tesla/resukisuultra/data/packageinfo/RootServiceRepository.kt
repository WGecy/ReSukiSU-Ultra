package com.tesla.resukisuultra.data.packageinfo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.resukisu.rootService.IKsuInterface
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class RootServiceRepository(
    private val application: Application,
) {
    private val TAG = "RootServiceRepo"
    private val requestMutex = Mutex()

    // 常驻 root shell (复用会话, 避免频繁启动新 root 进程 → 系统限制导致 bind 超时)
    @Volatile
    private var globalShell: Shell? = null
    private fun rootShell(): Shell = globalShell ?: synchronized(this) {
        globalShell ?: KsuCliRepository(application).getRootShell().also { globalShell = it }
    }

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

    // 进程内缓存: 避免频繁 bind (有 packageReceiver 在包变化时失效)
    @Volatile
    private var cachedPackages: List<PackageInfo>? = null
    @Volatile
    private var cacheTimestamp: Long = 0

    suspend fun getInstalledPackages(forceRefresh: Boolean = false): List<PackageInfo> = requestMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPackages != null && now - cacheTimestamp < 30 * 60 * 1000) {
            return cachedPackages!!
        }
        val result: List<PackageInfo> = try {
            loadFromRootService()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "root 服务获取应用列表失败, 回退 PackageManager", error)
            loadFromPackageManager()
        }
        cachedPackages = result
        cacheTimestamp = System.currentTimeMillis()
        result
    }

    // 上游 KernelSU 模式: bindOrTask 复用 root shell 会话拉起服务 (不频繁启新进程)
    // binder 死亡 → unbind → 重连 → 重试一次
    private suspend fun loadFromRootService(): List<PackageInfo> = withContext(Dispatchers.IO) {
        val (binder, connection) = connectKsuService()
        val ifaceBinder = binder ?: throw IllegalStateException("Root service unavailable")
        try {
            var iface = IKsuInterface.Stub.asInterface(ifaceBinder)
            val total = try {
                iface.packageCount
            } catch (_: Exception) {
                iface = reconnectKsuService(connection)
                iface.packageCount
            }
            buildList {
                var start = 0
                while (start < total) {
                    val page = withTimeoutOrNull(5000.milliseconds) {
                        try {
                            iface.getPackages(start, 100)
                        } catch (_: Exception) {
                            iface = reconnectKsuService(connection)
                            iface.getPackages(start, 100)
                        }
                    } ?: throw IllegalStateException("getPackages timeout")
                    if (page.isEmpty()) break
                    addAll(page)
                    start += page.size
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching { RootService.unbind(connection) }
            }
        }
    }

    private suspend fun reconnectKsuService(old: ServiceConnection): IKsuInterface {
        withContext(Dispatchers.Main.immediate) {
            runCatching { RootService.unbind(old) }
        }
        return IKsuInterface.Stub.asInterface(connectKsuService().first)
    }

    private suspend fun connectKsuService(): Pair<IBinder?, ServiceConnection> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (cont.isActive) cont.resume(null to this)
                    }

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (cont.isActive) cont.resume(binder to this)
                    }
                }

                cont.invokeOnCancellation {
                    runCatching { RootService.unbind(connection) }
                }

                val intent = Intent(application, KsuService::class.java)
                try {
                    val task = RootService.bindOrTask(intent, Shell.EXECUTOR, connection)
                    task?.let { rootShell().execTask(it) }
                } catch (error: Throwable) {
                    Log.w(TAG, "bindOrTask failed", error)
                    if (cont.isActive) cont.resume(null to connection)
                }
            }
        }

    // 回退: app 自身 PackageManager (QUERY_ALL_PACKAGES, 主用户所有应用)
    private suspend fun loadFromPackageManager(): List<PackageInfo> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getInstalledPackages(
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getInstalledPackages(0)
            }
        }
}
