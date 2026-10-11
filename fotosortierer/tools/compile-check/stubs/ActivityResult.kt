@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity.result

import android.content.Intent
import android.content.IntentSender

class IntentSenderRequest private constructor() {
    class Builder(intentSender: IntentSender) {
        fun build(): IntentSenderRequest = IntentSenderRequest()
    }
}

class ActivityResult(val resultCode: Int, val data: Intent?)
