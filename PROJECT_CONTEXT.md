# Project Context: Sponsor Skip

## Overview
Sponsor Skip is a native Android application written in Kotlin that automatically skips SponsorBlock segments in YouTube videos (and Spotify via Spot SponsorBlock). It operates without requiring root access or modified YouTube APKs by utilizing Android's `NotificationListenerService` and `MediaController` APIs to track playback and inject seek commands.

**Current Version:** v1.2.0-dev.3 (Code 10)
**License:** GPLv3 (Copyright (C) 2026 Jaival)
**Target:** Modern Android (Material Design, Dynamic Colors/Monet)

## Core Architecture & Workflow
1. **Detection (`MediaNotificationService.kt`):** 
   - Hooks into the active media session using `NotificationListenerService`.
   - Extracts the video title (or Media ID for Spotify).
2. **Extraction / Scraping:**
   - Because standard media controllers don't expose YouTube Video IDs, the app queries `youtube.com/results` using the scraped title.
   - Parses the HTML via Regex (`/watch\?v=([a-zA-Z0-9_-]{11})`) to extract the exact Video ID.
3. **Segment Fetching:**
   - Pings the community API (`sponsor.ajay.app/api/skipSegments`).
   - Retrieves timestamps for user-selected categories (Sponsor, Intro, Outro, etc.).
4. **Action:**
   - Tracks live playback position. When a boundary is crossed, issues a `transportControls.seekTo()` command to skip the segment.

## Recent Architectural Milestones & Fixes
* **Strict Search Engine:** Added an optional "Strict search" toggle in `MoreActivity`. When enabled, it wraps the YouTube query in the `intitle:"[title]"` operator to drastically reduce false positives.
* **Storage Leak Fix (Two-Stage Housekeeping):** Fixed a bug where the in-app `UpdateManager` bloated the app's Data folder by ~23MB per update. 
  - *Dismiss Guard:* If a user cancels an update, the downloaded APK is immediately deleted.
  - *Startup Sweeper:* `MainActivity` checks for and deletes residual `.apk` files in `getExternalFilesDir` upon boot.
* **Foreground Service (`SkipperForegroundService.kt`):** Implemented to prevent aggressive OEM battery optimizers (MIUI, ColorOS, etc.) from killing the scraper. 
  - Synced perfectly with the Master Toggle via `SettingsManager`.
  - Notification UX is optimized: Instead of a persistent dead notification, clicking it fires an Intent directly to `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`, allowing the user to hide it cleanly.
* **Streamlined Permissions:** Removed custom pre-permission dialogs in favor of directly calling standard Android system permission prompts for `POST_NOTIFICATIONS`.

## Key Components
* **`SettingsManager.kt`:** The brain of the app. Wraps SharedPreferences and handles the synchronization of the Master Switch, Foreground Service, and Strict Search states.
* **`AppLogger.kt`:** Custom debug logging system that writes to a local file (`skipper_logs.txt`) for user debugging and issue reporting.
* **`UpdateManager.kt`:** Custom in-app updater that parses GitHub releases.
* **`AppSelectionDialog.kt`:** Allows users to bind the service to custom/third-party YouTube clients.

