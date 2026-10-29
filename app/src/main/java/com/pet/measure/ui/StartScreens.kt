package com.pet.measure.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pet.measure.R
import com.pet.measure.ar.ArStartResult
import com.pet.measure.ui.theme.Pet

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onGrant: () -> Unit) {
    val context = LocalContext.current
    CenteredMessage(
        icon = Icons.Rounded.CameraAlt,
        title = stringResource(R.string.permission_title),
        body = stringResource(R.string.permission_body),
        action = stringResource(if (permanentlyDenied) R.string.permission_settings else R.string.permission_grant),
    ) {
        if (permanentlyDenied) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            onGrant()
        }
    }
}

@Composable
fun ArProblemScreen(problem: ArStartResult, onRetry: () -> Unit) {
    val context = LocalContext.current
    when (problem) {
        ArStartResult.NeedsInstall -> CenteredMessage(
            icon = Icons.Rounded.GetApp,
            title = stringResource(R.string.ar_needed_title),
            body = stringResource(R.string.ar_needed_body),
            action = stringResource(R.string.retry),
        ) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.core")))
            } catch (_: ActivityNotFoundException) {
            }
            onRetry()
        }
        ArStartResult.Unsupported -> CenteredMessage(
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.app_name),
            body = stringResource(R.string.ar_unsupported),
            action = null,
            onAction = {},
        )
        is ArStartResult.Failed -> CenteredMessage(
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.app_name),
            body = stringResource(R.string.ar_error, problem.message),
            action = stringResource(R.string.retry),
            onAction = onRetry,
        )
        else -> Unit
    }
}

@Composable
private fun CenteredMessage(icon: ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pet.StoneHigh, Pet.Night)))
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(Pet.Lavender.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Pet.Lavender, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Pet.Muted, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Pet.Ochre, contentColor = MaterialTheme.colorScheme.onSecondary),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(action, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
