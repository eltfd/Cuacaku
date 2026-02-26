package com.weather.forecast.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weather.forecast.update.UpdateState

/**
 * Dialog pembaruan aplikasi — ditampilkan saat versi baru tersedia.
 *
 * ## Tampilan Berdasarkan State
 *
 * ### UpdateState.Available
 * - Menampilkan versi baru, ukuran file, dan catatan rilis
 * - Tombol "Update Sekarang" dan "Nanti"
 *
 * ### UpdateState.Downloading
 * - Menampilkan progress indicator
 * - Informasi bahwa file sedang diunduh
 *
 * ### UpdateState.ReadyToInstall
 * - Konfirmasi bahwa download selesai
 * - Meminta pengguna menyelesaikan instalasi di dialog sistem
 *
 * ### UpdateState.Error
 * - Menampilkan pesan error
 * - Tombol untuk menutup dialog
 *
 * @param state State update saat ini dari [AppUpdateManager]
 * @param onUpdate Callback saat tombol "Update Sekarang" ditekan
 * @param onDismiss Callback saat dialog ditutup / "Nanti" ditekan
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateState.Available -> {
            UpdateAvailableDialog(
                state = state,
                onUpdate = onUpdate,
                onDismiss = onDismiss
            )
        }

        is UpdateState.Downloading -> {
            DownloadingDialog()
        }

        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(onDismiss = onDismiss)
        }

        is UpdateState.Error -> {
            ErrorDialog(
                message = state.message,
                onDismiss = onDismiss
            )
        }

        else -> {
            // Idle, Checking, NotAvailable — tidak menampilkan dialog
        }
    }
}

/**
 * Dialog saat versi baru tersedia.
 * Menampilkan info versi, ukuran file, catatan rilis, dan tombol aksi.
 */
@Composable
private fun UpdateAvailableDialog(
    state: UpdateState.Available,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Pembaruan Tersedia",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info versi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Versi terbaru",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "v${state.latestVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Ukuran file
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ukuran",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(state.fileSize),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Catatan rilis (jika ada)
                if (state.releaseNotes.isNotBlank()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Catatan Rilis:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.releaseNotes.take(300) +
                                if (state.releaseNotes.length > 300) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Nanti")
            }
        }
    )
}

/**
 * Dialog saat APK sedang diunduh.
 * Non-dismissable, menampilkan progress indicator.
 */
@Composable
private fun DownloadingDialog() {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable saat download */ },
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
        },
        title = {
            Text(
                text = "Mengunduh Pembaruan",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Mohon tunggu, file pembaruan sedang diunduh...\n" +
                        "Anda dapat melihat progres di panel notifikasi.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {} // Tidak ada tombol saat downloading
    )
}

/**
 * Dialog saat APK sudah didownload dan intent install telah diluncurkan.
 */
@Composable
private fun ReadyToInstallDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Siap Dipasang",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "File pembaruan telah diunduh.\n" +
                        "Ikuti petunjuk di layar untuk menyelesaikan pembaruan.\n\n" +
                        "Aplikasi akan otomatis melakukan UPDATE jika sudah terinstall, " +
                        "atau INSTALL baru jika belum ada.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

/**
 * Dialog saat terjadi error dalam proses update.
 */
@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Gagal Memperbarui",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

/**
 * Format ukuran file ke human-readable string.
 *
 * @param bytes Ukuran file dalam bytes
 * @return String format: "1.95 MB", "500 KB", dsb.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
