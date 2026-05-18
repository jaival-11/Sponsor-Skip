# Privacy Policy for Sponsor Skip

**Effective Date:** May 20, 2026  
**Developer:** Jaival

This Privacy Policy explains how Sponsor Skip ("the App") handles information when you use the application. By installing and using the App, you agree to the data processing practices described in this document.

## 1. Information We Do Not Collect
Sponsor Skip is designed with strict privacy principles. **The Developer does not collect, store, transmit, or have access to any of your personal data, usage habits, or viewing history.** There are no analytics trackers, crash reporters, or telemetry scripts embedded within this App. All processing happens either locally on your device or directly between your device and specific third-party APIs.

## 2. How the App Processes Data
To function correctly, the App requires certain permissions and makes automated background requests. Here is exactly how your data is processed:

### A. Local Media Monitoring (Notification Access)
The App requires the `Notification Access` permission to monitor the active Android `MediaSession`. 
* **What it reads:** The App solely extracts the **Title** of the currently playing media from the official YouTube app.
* **What it ignores:** The App ignores all other notifications, personal messages, emails, and alerts. This media monitoring happens entirely locally on your device.

### B. YouTube Background Search
Because the Android system does not provide the direct Video ID, the App must find it manually.
* **The Process:** The App takes the extracted Title and performs an automated, background HTTP search request directly to `youtube.com`.
* **Privacy Impact:** This request is made anonymously. No Google accounts, login tokens, or cookies are sent. However, because your device connects to YouTube's servers, your IP address may be temporarily exposed to Google LLC, subject to their privacy policies.

### C. SponsorBlock API Request
Once the App determines the 11-character Video ID, it requests the skip timestamps from the public SponsorBlock database.
* **The Process:** The App sends the Video ID via a GET request to `sponsor.ajay.app`.
* **Privacy Impact:** This data is sent anonymously. The SponsorBlock API does not receive your YouTube account information, but it may see the Video ID and your device's IP address. This data is handled in accordance with the [SponsorBlock Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796).

### D. App Update Checks (Codeberg)
To ensure you are running the latest version, the App checks for updates when opened or when manually requested in the settings.
* **The Process:** The App makes a network request to the public Codeberg API to check the repository's latest release tag.
* **Privacy Impact:** No personal data or usage metrics are sent. However, your device's IP address may be exposed to Codeberg's servers during the request. This is governed by [Codeberg's Privacy Policy](https://codeberg.org/Codeberg/org/src/branch/main/PrivacyPolicy.md).

## 3. Local Data Storage
The App stores user preferences (such as your chosen segment settings, privacy consent, and the total time/segments saved) locally on your device using Android's `SharedPreferences` (and Device Protected Storage). This data never leaves your phone. If you uninstall the App or clear its data, this data is permanently deleted.

## 4. Third-Party Services
The App acts as an independent, local client that interacts with external services. The Developer is not responsible for the privacy practices or terms of service of these third parties:
* **YouTube / Google LLC:** [Google Privacy Policy](https://policies.google.com/privacy)
* **SponsorBlock:** [SponsorBlock Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796)
* **Codeberg:** [Codeberg Privacy Policy](https://codeberg.org/Codeberg/org/src/branch/main/PrivacyPolicy.md)

## 5. Children's Privacy
The App does not knowingly process personal data from children under the age of 13. Because the App operates locally and does not transmit data to the Developer, we do not have the capability to collect or identify the age of our users.

## 6. Changes to this Privacy Policy
This Privacy Policy may be updated periodically without prior notice to reflect changes in the App's technical architecture or legal requirements. By continuing to use the App, you automatically accept any revisions to this policy. The most current version will always be available in the App's repository.

## 7. Contact Information
If you have any questions, concerns, or requests regarding this Privacy Policy or the technical operations of Sponsor Skip, you may reach out via:
* **Email:** jaival7909@gmail.com
* **Issue Tracker:** [Sponsor Skip Repository](https://codeberg.org/jaival/Sponsor-Skip/issues)

---
*Note: This Privacy Policy was drafted with the assistance of AI to ensure clarity, thoroughness, and adherence to standard open-source privacy principles.*
