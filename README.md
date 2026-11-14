# Chat Vault
**By PaliaAPK HUB** — Android chat-export viewer and local backup organizer.

Chat Vault is a Kotlin + Jetpack Compose Android app for importing chat exports that the user selects, browsing parsed messages, and opening media included in ZIP exports.

## Included in this starter build
- Branded Chat Vault splash screen and custom adaptive launcher icon (chat + shield + backup visual).
- White and mint-green interface with Home, Backups, Chats, Tools, and Settings sections.
- Launch shortcuts for WhatsApp and WhatsApp Business.
- Import WhatsApp-exported TXT files and ZIP exports through Android's system file picker.
- ZIP extraction with path-traversal protection; extracted media remains in app-private storage.
- WhatsApp-style message bubbles, sender/contact filter, message search, and date filter.
- Media and file list for imported ZIPs; opens supported photos, videos, audio, and documents with an installed Android app.
- Backup history and local app-private storage.
- Optional movable floating Chat Vault bubble (requires the user to grant Android's “Display over other apps” permission).
- GitHub Actions workflow to build a debug APK and upload it as a workflow artifact.

## How to export a chat
1. Open WhatsApp or WhatsApp Business.
2. Open the chat, tap the menu, then **More → Export chat**.
3. Choose **Include media** if you want media files included (WhatsApp may create a ZIP).
4. Open Chat Vault and import the TXT or ZIP export.

## Important Android / WhatsApp limitations
- Android apps cannot silently read WhatsApp's private chat database. The user must export a chat or select a backup file.
- Chat Vault is an export viewer and local organizer. It does **not** inject messages into WhatsApp or restore an exported TXT/ZIP as a live WhatsApp conversation.
- Media is opened by another compatible app installed on the device; Chat Vault does not include its own video/audio decoder.
- The floating bubble is optional and requires a special overlay permission.

## Build
- Android Studio / Gradle
- Kotlin 2.0.21
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- Jetpack Compose + Material 3
- Minimum Android: API 26

Package: `com.shanpalia.chatvault`

A push to `main` runs **Android Build**. The debug APK is published under the workflow's **Artifacts** section when the build succeeds.
