package de.ingvoss.fotosortierer.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.data.media.MediaAccess
import de.ingvoss.fotosortierer.ui.appContainer
import kotlinx.coroutines.launch

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Öffnet die App-Seite der Systemeinstellungen (zum Ändern abgelehnter Berechtigungen). */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Einführung und Berechtigungsanfrage. Behandelt Teilzugriff (Android 14), dauerhaft abgelehnte
 * Berechtigungen (→ Systemeinstellungen) und den Zugriff auf Originaldaten für die GPS-Position.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer()
    val scope = rememberCoroutineScope()
    val access by container.access.collectAsStateWithLifecycle()
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.refreshAccess()
        scope.launch { container.settings.setPermissionRequested() }
        val level = MediaAccess.level(context)
        if (level == AccessLevel.NONE) {
            val activity = context.findActivity()
            val mainPermission = MediaAccess.permissionsToRequest().first()
            permanentlyDenied = activity != null && !activity.shouldShowRequestPermissionRationale(mainPermission)
        } else {
            container.onActivityStart()
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.onboarding_how), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.onboarding_safety), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.onboarding_permission_why), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.onboarding_location_why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (Build.VERSION.SDK_INT >= 34) {
                Text(
                    stringResource(R.string.onboarding_partial_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            when {
                access == AccessLevel.FULL -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_continue))
                }
                access == AccessLevel.PARTIAL -> {
                    Text(stringResource(R.string.onboarding_partial_active), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_continue))
                    }
                    OutlinedButton(
                        onClick = { launcher.launch(MediaAccess.permissionsToRequest()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.onboarding_change_selection)) }
                    OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_allow_all))
                    }
                }
                permanentlyDenied -> {
                    Text(
                        stringResource(R.string.onboarding_denied_permanently),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start
                    )
                    Button(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
                else -> Button(
                    onClick = { launcher.launch(MediaAccess.permissionsToRequest()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.onboarding_allow)) }
            }
        }
    }
}
