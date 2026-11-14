package com.shanpalia.chatvault

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF16A887)
private val Ink = Color(0xFF17352F)
private val Page = Color(0xFFF5FAF8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backupDir = File(filesDir, "backups").apply { mkdirs() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Mint, background = Page, surface = Color.White, onSurface = Ink)) {
                ChatVaultHome(backupDir) { uri -> importFile(uri, backupDir) }
            }
        }
    }

    private fun importFile(uri: Uri, directory: File): File? {
        val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "chat-export.txt"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(directory, System.currentTimeMillis().toString() + "-" + safeName)
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            destination.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) {
            destination.delete()
            null
        }
    }
}

@Composable
private fun ChatVaultHome(backupDir: File, importFile: (Uri) -> File?) {
    var refresh by remember { mutableIntStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (importFile(uri) != null) {
                refresh++
                status = "Backup imported"
            } else status = "Import failed"
        }
    }
    val backups = remember(refresh) {
        backupDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    if (selectedFile != null) {
        ChatFileScreen(selectedFile!!, { selectedFile = null })
        return
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Chat, null, tint = Color.White, modifier = Modifier.background(Mint, RoundedCornerShape(14.dp)).padding(10.dp).size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Chat Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("By PaliaAPK HUB", fontSize = 12.sp, color = Mint)
                }
            }
        },
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Text("Your chats, safely saved.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Import an exported WhatsApp or WhatsApp Business chat and open it anytime.", color = Color(0xFFD0E5DF), fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Chat Backup")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Backup History", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text(backups.size.toString() + " saved", color = Mint, fontSize = 13.sp)
                }
            }
            if (backups.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.Archive, null, tint = Mint, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No backups yet", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("Your imported chats will appear here.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(backups, key = { it.absolutePath }) { file ->
                    BackupItem(file) { selectedFile = file }
                }
            }
            item {
                Text(
                    "Chat Vault imports files you explicitly select. Android apps cannot silently read WhatsApp's private chat database.",
                    color = Color.Gray, fontSize = 11.sp
                )
            }
        }
    }
    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(2000)
            status = null
        }
    }
}

@Composable
private fun BackupItem(file: File, onOpen: () -> Unit) {
    val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable { onOpen() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.FolderOpen, null, tint = Mint, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name.substringAfter('-', file.name), color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(date, color = Color.Gray, fontSize = 11.sp)
            Text((file.length() / 1024).toString() + " KB", color = Mint, fontSize = 11.sp)
        }
        Text("OPEN", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ChatFileScreen(file: File, onBack: () -> Unit) {
    val textContent = remember(file) {
        runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { "This file is saved, but this format cannot be displayed as plain text yet." }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFEAF3EF))) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable { onBack() }, color = Mint, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(file.name.substringAfter('-', file.name), color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Imported chat backup", color = Color.Gray, fontSize = 11.sp)
            }
        }
        Text(
            textContent,
            Modifier.fillMaxSize().padding(14.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
            color = Ink, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
}
