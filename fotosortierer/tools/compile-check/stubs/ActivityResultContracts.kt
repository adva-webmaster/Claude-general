@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest

abstract class ActivityResultContract<I, O>

class ActivityResultContracts private constructor() {
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class StartIntentSenderForResult : ActivityResultContract<IntentSenderRequest, ActivityResult>()
    class CreateDocument(mimeType: String) : ActivityResultContract<String, Uri?>()
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
}
