# Chat Vault

**WhatsApp & WhatsApp Business chat backup viewer**  
By PaliaAPK HUB

Chat Vault is an Android app for importing user-exported chat archives and viewing saved conversations locally.

## Planned features
- WhatsApp and WhatsApp Business export-import guidance
- Local backup history
- Chat-style viewer for imported TXT exports
- ZIP archive selection and attachment browsing (where included in the export)
- Optional floating shortcut that opens Chat Vault actions
- Privacy-first local storage

## Important platform limitation
Android apps cannot silently read WhatsApp's private chat database or trigger a private in-app backup. Chat Vault works with files the user explicitly exports or selects through Android's document picker. Floating overlays are optional shortcuts and do not read WhatsApp content.

## Tech
- Kotlin
- Jetpack Compose
- AndroidX
- Minimum SDK 26

## Build
Open this repository in Android Studio and sync Gradle. Build the app module.

## Package
com.shanpalia.chatvault
