# Privacy Policy for Sponsor Skip

**Effective Date:** May 25, 2026  
**Developer:** Jaival

This Privacy Policy explains how Sponsor Skip ("the App") handles information when you use the application. By installing and using the App, you acknowledge and agree to the data processing practices described in this document.

## 1. Information We Do Not Collect
Sponsor Skip is designed with strict privacy principles. **The Developer does not collect, store, or have access to any of your personal data.** The App does not include any analytics trackers, crash reporters, or telemetry scripts. All processing happens either locally on your device or directly between your device and specific third-party APIs.

## 2. How the App Processes Data
To function correctly, the App requires certain permissions and makes automated background requests. All network requests are performed over HTTPS encryption where supported by destination service. Here is exactly how your data is processed:

### A. Local Media Monitoring (Notification Access)
The App requires the `Notification Access` permission to monitor the active Android `MediaSession`. 
* **What it reads:** The App solely extracts the **Title** of the currently playing media from the official YouTube app.
* **What it ignores:** The App ignores all other notifications, personal messages, emails, and alerts. This media monitoring happens entirely locally on your device.

### B. YouTube Background Search
Because the Android system does not provide the direct Video ID, the App must find it manually.
* **The Process:** The App takes the extracted Title and performs an automated, background HTTP search request directly to `youtube.com`.
* **Privacy Impact:** No Google accounts, login tokens, or cookies are sent. However, because your device connects to YouTube's servers, your IP address may be temporarily exposed to Google LLC, subject to their privacy policies. The request is made directly between your device and YouTube's servers. The request is not routed through, and is never processed or stored on, any servers or infrastructure owned, operated or controlled by the Developer.
* *Although authentication data is not transmitted by the App, requests to YouTube may still allow Google to infer that a device associated with your IP address searched for or accessed particular video metadata.* 

### C. SponsorBlock API Request
Once the App determines the 11-character Video ID, it requests the skip timestamps from the public SponsorBlock database.
* **The Process:** The App sends the Video ID via a GET request to `sponsor.ajay.app`.
* **Skip Count Tracking:** Additionally if a user enables `Skip count tracking` in the app, only UUID of skipped segment is sent via a POST request to `sponsor.ajay.app`.
* **Privacy Impact:** The SponsorBlock API does not receive your YouTube account information, but it may see the Video ID and your device's IP address. This data is handled in accordance with the [SponsorBlock Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796). This request is made directly from your device to SponsorBlock servers. The request is not routed through, and is never processed or stored on, any servers or infrastructure owned, operated or controlled by the Developer. 

### D. App Update Checks (Codeberg)
To ensure you are running the latest version, the App may check for updates periodically in background or on app open or when manually requested in the settings.
* **The Process:** The App makes a network request to the public Codeberg API to check the repository's latest release tag.
* **Privacy Impact:** No personal data or usage metrics are sent. However, your device's IP address may be exposed to Codeberg's servers during the request. This is governed by [Codeberg's Privacy Policy](https://codeberg.org/Codeberg/org/src/branch/main/PrivacyPolicy.md). This request is made directly from your device to Codeberg's servers. The request is not routed through, and is never processed or stored on, any servers or infrastructure owned, operated or controlled by the Developer.

***Note**: While the app uses secure protocols to protect your data during transmission, no method of communication over the internet is completely secure, and the developer cannot guarantee absolute security against unauthorised inception or server-side exploits.*

## 3. Local Data Storage
The App stores user preferences (such as your chosen segment settings, the total time/segments saved) locally on your device using Android's `SharedPreferences` (and Device Protected Storage). This data never leaves your phone. If you uninstall the App or clear its data, this data is permanently deleted.

## 4. Third-Party Services
The App acts as an independent, local client that interacts with external services. The Developer has no control over and is not responsible for the privacy practices or terms of service of these third parties.
* **YouTube / Google LLC:** [Google Privacy Policy](https://policies.google.com/privacy)
* **SponsorBlock:** [SponsorBlock Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796)
* **Codeberg:** [Codeberg Privacy Policy](https://codeberg.org/Codeberg/org/src/branch/main/PrivacyPolicy.md)

Sponsor Skip is an independent third-party application and is not affiliated with, endorsed by, sponsored by, or approved by YouTube, Google LLC, or SponsorBlock.

## 5. Children's Privacy
The App is not directed toward children and the Developer does not knowingly collect personal data from children. Because the App operates locally and does not transmit data to the Developer, we do not have the capability to collect or identify the age of our users.

## 6. Changes to this Privacy Policy
This Privacy Policy may be updated periodically to reflect changes in the App's technical architecture or legal requirements. Because the App operates without user accounts or contact information, we cannot notify you directly of any modifications. We encourage you to periodically review the most current version of this policy in the App's repository. Material changes to this policy become effective upon publication. Your continued use of the App following the publication of changes constitutes your acknowledgment and consent to the revised policy.

## 7. Contact Information
If you have any questions, concerns, or requests regarding this Privacy Policy or the technical operations of Sponsor Skip, you may reach out via:
* **Email:** jaival7909@gmail.com
* **Issue Tracker:** [Sponsor Skip Repository](https://codeberg.org/jaival/Sponsor-Skip/issues)

---

*The Privacy Policy was updated on 20th July, 2026*
