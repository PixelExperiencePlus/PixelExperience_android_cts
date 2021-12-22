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

package android.companion.cts.common

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationPending
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnDeviceFound
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnFailure
import android.content.IntentSender
import android.util.Log

class RecordingCallback : CompanionDeviceManager.Callback() {
    private val _invocations: MutableList<CallbackMethodInvocation<*>> = mutableListOf()
    val invocations: List<CallbackMethodInvocation<*>>
        get() = _invocations

    override fun onDeviceFound(intentSender: IntentSender) {
        recordInvocation(OnDeviceFound, intentSender)
    }

    override fun onAssociationPending(intentSender: IntentSender) {
        recordInvocation(OnAssociationPending, intentSender)
    }

    override fun onAssociationCreated(associationInfo: AssociationInfo) {
        recordInvocation(OnAssociationCreated, associationInfo)
    }

    override fun onFailure(error: CharSequence?) = recordInvocation(OnFailure, error)

    private fun recordInvocation(method: CallbackMethod, param: Any? = null) {
        Log.d(TAG, "Callback.$method(), param=$param")
        _invocations.add(CallbackMethodInvocation(method, param))
    }

    fun waitForInvocation(timeout: Long = 1_000) {
        if (!waitFor(timeout = timeout, interval = 100) { invocations.isNotEmpty() })
            throw AssertionError("Callback hasn't been invoked")
    }

    fun clearRecordedInvocations() = _invocations.clear()

    enum class CallbackMethod {
        OnDeviceFound, OnAssociationPending, OnAssociationCreated, OnFailure
    }

    data class CallbackMethodInvocation<T>(val method: CallbackMethod, val arg: T) {
        val intentSender: IntentSender
            get() = when (method) {
                OnDeviceFound, OnAssociationPending -> arg as IntentSender
                else -> error("Method does not have \"intentSender\" argument.")
            }

        val associationInfo: AssociationInfo
            get() = when (method) {
                OnAssociationCreated -> arg as AssociationInfo
                else -> error("Method does not have \"associationInfo\" argument.")
            }

        val error: CharSequence?
            get() = when (method) {
                OnFailure -> arg as CharSequence?
                else -> error("Method does not have \"error\" argument.")
            }
    }
}