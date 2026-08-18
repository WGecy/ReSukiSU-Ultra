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
}
