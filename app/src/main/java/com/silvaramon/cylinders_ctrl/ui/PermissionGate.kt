package com.silvaramon.cylinders_ctrl.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Câmera obrigatória; localização aceita precisa ou aproximada. */
private fun Context.hasRequiredPermissions(): Boolean =
    isGranted(Manifest.permission.CAMERA) &&
        (isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION))

/**
 * Solicita câmera e localização ao abrir o app. Enquanto não forem concedidas,
 * exibe apenas uma mensagem explicando as permissões necessárias.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.hasRequiredPermissions()) }
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequested = true
        granted = context.hasRequiredPermissions()
        permanentlyDenied = !granted && activity != null && isPermanentlyDenied(activity)
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(REQUIRED_PERMISSIONS)
    }

    // Reavalia ao voltar das configurações do sistema.
    LifecycleResumeEffect(Unit) {
        granted = context.hasRequiredPermissions()
        if (granted) permanentlyDenied = false
        onPauseOrDispose { }
    }

    if (granted) {
        content()
    } else {
        PermissionRequiredScreen(
            showSettingsButton = hasRequested && permanentlyDenied,
            onRequest = { launcher.launch(REQUIRED_PERMISSIONS) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    }
}

/** Negada e o sistema não exibirá mais o diálogo ("não perguntar novamente"). */
private fun isPermanentlyDenied(activity: Activity): Boolean {
    val cameraBlocked = !activity.isGranted(Manifest.permission.CAMERA) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    val locationBlocked = !activity.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
        !activity.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_FINE_LOCATION,
        ) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    return cameraBlocked || locationBlocked
}

@Composable
private fun PermissionRequiredScreen(
    showSettingsButton: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Permissões necessárias", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Para usar o aplicativo é preciso permitir:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "• Câmera: para ler a identificação e o QR Code dos cilindros.\n" +
                    "• Localização: para registrar o local onde o envio foi finalizado.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showSettingsButton) {
                Text(
                    "As permissões foram negadas. Habilite-as nas configurações do aplicativo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir configurações")
                }
            } else {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("Conceder permissões")
                }
            }
        }
    }
}
