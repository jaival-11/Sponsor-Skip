# Agent Instructions for Sponsor Skip

You are an expert Android and Kotlin developer assisting with the "Sponsor Skip" repository. You are operating via the Antigravity (agy) CLI and will apply modifications directly to the codebase. 

## Code Style & Best Practices
* **Language:** Strictly use Kotlin. Avoid Java unless absolutely necessary for a specific low-level interoperability requirement.
* **UI/UX:** Use standard native Android components (Material Design 3). Ensure all new activities respect Monet dynamic theming (`DynamicColors.applyToActivitiesIfAvailable`).
* **Permissions UX:** Do not create custom "pre-permission" dialog boxes (e.g., "We need this permission because..."). Immediately request the system permission. If the user denies it, handle the fallback gracefully.
* **Error Handling:** Never swallow exceptions silently in the background services. Route background errors to `AppLogger.log()` so they can be diagnosed in the Debug Activity.
* **Coroutines:** Use Kotlin Coroutines (`lifecycleScope`, `CoroutineScope(Dispatchers.IO)`) for all network and disk I/O. Never block the main thread.

## Hard Rules for Modification
1. **License Headers:** Every new `.kt` file MUST begin with the standard GPLv3 header attributed to Jaival (2026).
2. **Storage Discipline:** Be hyper-aware of Android Data vs. Cache storage. 
   - Never instantiate a `WebView` unless absolutely strictly required, as it permanently eats ~20MB of user data.
   - Any temporary files (like downloaded APKs or fetched JSON dumps) MUST have a rock-solid deletion mechanism.
3. **Scraper Integrity:** The YouTube HTML scraping logic in `MediaNotificationService.kt` is highly sensitive. Do not alter the User-Agent, Regex patterns, or network request structure unless explicitly fixing a scraper bug. 
4. **Service Synchronization:** Any changes to background/foreground services must route through `SettingsManager.kt` to ensure the UI toggles, the Master Switch, and the actual service state remain perfectly synchronized.
5. **Modification Method:** Apply changes directly to files using your CLI capabilities. Do not output bash/termux injection scripts (`cat << 'EOF'`) unless explicitly asked to generate a shell script.

## Core Header Template
```kotlin
/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright © 2026 Jaival
 */

