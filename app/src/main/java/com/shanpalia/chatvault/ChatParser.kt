package com.shanpalia.chatvault

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class ChatMessage(
    val id: Int,
    val date: Date?,
    val sender: String,
    val body: String,
    val attachment: File? = null,
    val system: Boolean = false
)

data class ImportedChat(val title: String, val messages: List<ChatMessage>, val mediaFiles: List<File>)

object ChatParser {
    private val lineStart = Regex(
        """^(\u200e?\[)?(\d{1,4}[./-]\d{1,2}[./-]\d{2,4},?\s+\d{1,2}:\d{2}(?:\s?[APap][Mm])?)(?:\])?\s+-\s+(.*)$"""
    )
    private val formats = listOf(
        "dd/MM/yy, HH:mm", "dd/MM/yy, h:mm a", "dd/MM/yyyy, HH:mm", "dd/MM/yyyy, h:mm a",
        "d/M/yy, HH:mm", "d/M/yy, h:mm a", "MM/dd/yy, HH:mm", "MM/dd/yy, h:mm a",
        "dd-MM-yy, HH:mm", "dd-MM-yyyy, HH:mm", "yyyy-MM-dd, HH:mm",
        "dd.MM.yy, HH:mm", "dd.MM.yyyy, HH:mm"
    )

    fun load(file: File, destination: File): ImportedChat {
        val textFile = if (file.extension.equals("zip", true)) {
            val out = File(destination, "extracted_${System.currentTimeMillis()}").apply { mkdirs() }
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val target = File(out, entry.name).canonicalFile
                    require(target.path.startsWith(out.canonicalPath + File.separator) || target == out) { "Invalid ZIP entry" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    }
                }
            }
            out.walkTopDown().firstOrNull { it.isFile && it.extension.equals("txt", true) }
        } else file
        val media = if (file.extension.equals("zip", true)) {
            val root = textFile?.parentFile ?: destination
            root.walkTopDown().filter { it.isFile && !it.extension.equals("txt", true) }.toList()
        } else emptyList()
        val text = textFile?.readText(Charsets.UTF_8) ?: file.readText(Charsets.UTF_8)
        return ImportedChat(file.name.substringAfter('-', file.name), parse(text, media), media)
    }

    fun parse(text: String, media: List<File> = emptyList()): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        var date: Date? = null
        var sender = ""
        val body = StringBuilder()
        fun flush() {
            if (body.isNotEmpty()) {
                val raw = body.toString().trim()
                val attached = media.firstOrNull { raw.contains(it.name, true) || raw.contains("<attached:", true) }
                result += ChatMessage(result.size, date, sender, raw, attached, sender.isBlank())
                body.clear()
            }
        }
        text.removePrefix("\uFEFF").lineSequence().forEach { line ->
            val match = lineStart.find(line)
            if (match != null) {
                flush()
                date = parseDate(match.groupValues[2])
                val payload = match.groupValues[3]
                val split = payload.indexOf(": ")
                if (split > 0) {
                    sender = payload.substring(0, split).trim()
                    body.append(payload.substring(split + 2))
                } else {
                    sender = ""
                    body.append(payload)
                }
            } else if (body.isNotEmpty()) {
                body.append('\n').append(line)
            } else if (line.isNotBlank()) {
                body.append(line)
                sender = ""
            }
        }
        flush()
        return result
    }

    private fun parseDate(value: String): Date? {
        formats.forEach { pattern ->
            try {
                val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
                return formatter.parse(value.replace(Regex("\\s+"), " ").trim())
            } catch (_: Exception) { }
        }
        return null
    }
}