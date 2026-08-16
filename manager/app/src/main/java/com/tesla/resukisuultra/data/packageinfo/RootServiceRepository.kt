package com.tesla.resukisuultra.data.packageinfo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.resukisu.rootService.IKsuInterface
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

    // 进程内缓存: 避免频繁 start/bind/stop root 服务 (被系统限制 → 超时 unavailable)
    @Volatile
    private var cachedPackages: List<PackageInfo>? = null
    @Volatile
    private var cacheTimestamp: Long = 0

    suspend fun getInstalledPackages(forceRefresh: Boolean = false): List<PackageInfo> = requestMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPackages != null && now - cacheTimestamp < 5 * 60 * 1000) {
            return cachedPackages!!
        }

        val intent = Intent(application, KsuService::class.java)
        try {
            val binder = withTimeoutOrNull(15000.milliseconds) {
                connectService(intent)
            } ?: throw IllegalStateException("Root service unavailable")

            val result = withContext(Dispatchers.IO) {
                val service = IKsuInterface.Stub.asInterface(binder)
                val total = service.packageCount
                buildList {
                    var start = 0
                    while (start < total) {
                        val page = service.getPackages(start, 100)
                        if (page.isEmpty()) break
                        addAll(page)
                        start += page.size
                    }
                }
            }
            cachedPackages = result
            cacheTimestamp = System.currentTimeMillis()
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("Root service unavailable", error)
        } finally {
            stopService(intent)
        }
    }

    private suspend fun connectService(intent: Intent): IBinder? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (continuation.isActive) {
                            continuation.resume(binder)
                        } else {
                            stopServiceOnMain(intent)
                        }
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                continuation.invokeOnCancellation { stopServiceOnMain(intent) }
                runCatching {
                    RootService.bind(intent, Shell.EXECUTOR, connection)
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private suspend fun stopService(intent: Intent) =
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching { RootService.stop(intent) }
    }

    private fun stopServiceOnMain(intent: Intent) {
        ContextCompat.getMainExecutor(application).execute {
            runCatching { RootService.stop(intent) }
        }
    }
}
