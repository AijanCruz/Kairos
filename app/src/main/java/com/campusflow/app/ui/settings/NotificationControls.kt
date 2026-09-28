package com.campusflow.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.campusflow.app.ui.AppViewModel
import androidx.core.net.toUri

@Composable
fun NotificationControls(vm: AppViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var exact by remember { mutableStateOf(vm.container.reminders.exactAllowed()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        vm.container.refreshReminders()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exact = vm.container.reminders.exactAllowed()
        vm.container.refreshReminders()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
                Text(if (granted) "Permiso de Android concedido" else "Activa el permiso para recibir avisos", style = MaterialTheme.typography.bodySmall)
            }
            Switch(enabled, { vm.perform { vm.container.preferences.setNotifications(it); vm.container.refreshReminders() } })
        }
        if (!granted) Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Permitir notificaciones") }
        Text(if (exact) "Precisión: alarmas exactas habilitadas" else "Precisión: Android puede retrasar los avisos. Habilita alarmas exactas para usar la hora elegida.", style = MaterialTheme.typography.bodyMedium)
        if (!exact && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
        }) { Text("Habilitar alarmas exactas") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Ajustes de notificaciones de Android") }
        Text("Los avisos siguen activos al cerrar la app. Si usas «Forzar detención», ábrela otra vez para restaurarlos. En Samsung, evita incluir CampusFlow entre las apps en suspensión profunda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
