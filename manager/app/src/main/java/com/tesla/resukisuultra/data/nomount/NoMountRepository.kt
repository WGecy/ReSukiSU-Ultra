package com.tesla.resukisuultra.data.nomount

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NoMountRepository(
    private val helper: NoMountHelper,
) {
    private val mutex = Mutex()

    suspend fun isSupported(): Boolean = helper.isSupported()

    suspend fun getStatus(): NoMountStatus? = helper.getStatus()

    suspend fun listRules(): List<NoMountRule> = helper.listRules()

    suspend fun addRule(virtual: String, real: String): Boolean = mutex.withLock {
        helper.addRule(virtual, real)
    }

    suspend fun removeRule(virtual: String): Boolean = mutex.withLock {
        helper.removeRule(virtual)
    }

    suspend fun clearRules(): Boolean = mutex.withLock {
        helper.clearRules()
    }

    suspend fun removeRules(virtualPaths: List<String>): Boolean = mutex.withLock {
        helper.removeRules(virtualPaths)
    }

    suspend fun listModules(): List<NoMountModule> = helper.listModules()

    suspend fun loadModule(moduleId: String): Boolean = mutex.withLock {
        helper.loadModule(moduleId)
    }

    suspend fun unloadModule(moduleId: String): Boolean = mutex.withLock {
        helper.unloadModule(moduleId)
    }

    suspend fun listExclusions(): List<Long> = helper.listExclusions()

    suspend fun addExclusion(uid: Long): Boolean = mutex.withLock {
        helper.addExclusion(uid)
    }

    suspend fun removeExclusion(uid: Long): Boolean = mutex.withLock {
        helper.removeExclusion(uid)
    }
}
