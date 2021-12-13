/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.companion.cts

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Handler
import android.util.Log
import java.util.Collections.synchronizedMap

sealed class CompanionService<T : CompanionService<T>>(
    private val instanceHolder: InstanceHolder<T>
) : CompanionDeviceService() {
    @Volatile var isBound: Boolean = false
        private set(isBound) {
            Log.d(TAG, "$this.isBound=$isBound")
            if (!isBound && !connectedDevices.isEmpty())
                error("Unbinding while there are connected devices")
            field = isBound
        }

    val connectedDevices: Collection<AssociationInfo>
        get() = _connectedDevices.values

    val associationIdsForConnectedDevices: Collection<Int>
        get() = _connectedDevices.keys

    private val _connectedDevices: MutableMap<Int, AssociationInfo> =
            synchronizedMap(mutableMapOf())

    override fun onCreate() {
        Log.d(TAG, "$this.onCreate()")
        super.onCreate()
        instanceHolder.instance = this as T
    }

    override fun onBindCompanionDeviceService(intent: Intent) {
        Log.d(TAG, "$this.onBindCompanionDeviceService()")
        isBound = true
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d(TAG, "$this.onDevice_Appeared(), association=$associationInfo")
        _connectedDevices[associationInfo.id] = associationInfo
        super.onDeviceAppeared(associationInfo)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.d(TAG, "$this.onDevice_Disappeared(), association=$associationInfo")
        _connectedDevices.remove(associationInfo.id)
        super.onDeviceDisappeared(associationInfo)
    }

    // For now, we need to "post" a Runnable that sets isBound to false to the Main Thread's
    // Handler, because this may be called between invocations of
    // CompanionDeviceService.Stub.onDeviceAppeared() and the "real"
    // CompanionDeviceService.onDeviceAppeared(), which would cause an error() in isBound setter.
    override fun onUnbind(intent: Intent?) = super.onUnbind(intent)
            .also {
                Log.d(TAG, "$this.onUnbind()")
                Handler.getMain().post { isBound = false }
            }

    override fun onDestroy() {
        Log.d(TAG, "$this.onDestroy()")
        instanceHolder.instance = null
        super.onDestroy()
    }
}

sealed class InstanceHolder<T : CompanionService<T>> {
    // Need synchronization, because the setter will be called from the Main thread, while the
    // getter is expected to be called mostly from the instrumentation thread.
    var instance: T? = null
        @Synchronized internal set
        @Synchronized get

    val isBound: Boolean
        get() = instance?.isBound ?: false

    val connectedDevices: Collection<AssociationInfo>
        get() = instance?.connectedDevices ?: emptySet()

    val associationIdsForConnectedDevices: Collection<Int>
        get() = instance?.associationIdsForConnectedDevices ?: emptySet()

    fun waitForBind(timeout: Long = 1000) {
        if (!waitFor(timeout = timeout) { isBound })
            throw AssertionError("Service hasn't been bound")
    }

    fun waitForUnbind(timeout: Long) {
        if (!waitFor(timeout = timeout) { !isBound })
            throw AssertionError("Service hasn't been unbound")
    }

    fun waitAssociationToAppear(associationId: Int, timeout: Long = 1000) {
        val appeared = waitFor(timeout = timeout) {
            associationIdsForConnectedDevices.contains(associationId)
        }
        if (!appeared) throw AssertionError("Association with $associationId hasn't \"appeared\" ")
    }

    fun waitAssociationToDisappear(associationId: Int, timeout: Long = 1000) {
        val gone = waitFor(timeout = timeout) {
            !associationIdsForConnectedDevices.contains(associationId)
        }
        if (!gone) throw AssertionError("Association with $associationId hasn't \"disappeared\" ")
    }
}

class PrimaryCompanionService : CompanionService<PrimaryCompanionService>(Companion) {
    companion object : InstanceHolder<PrimaryCompanionService>()
}

class SecondaryCompanionService : CompanionService<SecondaryCompanionService>(Companion) {
    companion object : InstanceHolder<SecondaryCompanionService>()
}