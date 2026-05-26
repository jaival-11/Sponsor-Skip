<div align="center">

<img src="https://codeberg.org/repo-avatars/6d562eaeec18e10849ff8b460d3c4402c2dbce6b9f00a7438134ad78cc1d6c7c" alt="Sponsor Skip app icon" width="200" />

# Sponsor Skip

### <a href="https://github.com/ajayyy/SponsorBlock">SponsorBlock</a> for native android

<br/>

[![Latest release](https://img.shields.io/gitea/v/release/jaival/Sponsor-Skip?gitea_url=https%3A%2F%2Fcodeberg.org&style=for-the-badge&labelColor=0d1117)](https://codeberg.org/jaival/Sponsor-Skip/releases/latest) [![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge&labelColor=0d1117&color=EA7233)](https://codeberg.org/jaival/Sponsor-Skip/src/branch/main/LICENSE)

<br/>

[![Follow on X](https://img.shields.io/badge/techironic11-1da1f2?style=for-the-badge&labelColor=0d1117&logo=x)](https://x.com/techironic11)[![Telegram Channel](https://img.shields.io/badge/Telegram-Join-0088cc?style=for-the-badge&labelColor=0d1117&logo=telegram)](https://t.me/techironic)[![Email Support](https://img.shields.io/badge/Email-Contact_Me-db4437?style=for-the-badge&labelColor=0d1117&logo=gmail)](mailto:jaival7909@gmail.com)

<br/>

[**Download**](#where-to-get-it) · [**Features**](#features) · [**Support**](#bug-reports-feature-suggestions)

---

## Table of Contents

| Section | Description |
| :--- | :--- |
| [Features](#features) | What the app can do |
| [How It Works](#how-it-works) | The technical workflow |
| [Where to Get It (Download)](#where-to-get-it) | Download links for the latest stable APK |
| [Build from Source](#build-from-source) | Prerequisites and compilation instructions |
| [Contributions](#contributions) | Guidelines for contributing to the repository |
| [Bug Reports & Feature Suggestions](#bug-reports-feature-suggestions) | How to report issues or request new features |
| [Under the Hood](#under-the-hood-for-developers) | Tech stack, core architecture, and third-party libraries |
| [Attributions & Credits](#attributions-credits) | Acknowledgments of third-party tools and creators |
| [Privacy Policy](#privacy-policy) | Link to the complete Privacy Policy |
| [Disclaimer](#disclaimer) | Terms of service, liability, and disclosures |
| [License](#license) | GNU General Public License v3.0 details |

</div>

---

<h2><a id="screenshots"></a>Screenshots</h2>

<img src="https://codeberg.org/attachments/fb302b5b-caf4-4f79-b5c9-2e7a8a65e60f" alt="Home screen" width="30%" />
<img src="https://codeberg.org/attachments/38f342f0-a084-4380-80b8-5120918d59a2" alt="Settings Screen" width="30%" />
<img src="https://codeberg.org/attachments/e7fa8f76-4f8c-4a73-a4e3-cc02d962c133" alt="Permissions Screen" width="30%" />

---

## Features

* **Stock YouTube Support:** Works directly with the official, unmodded YouTube app. No root access, patching, or third-party clients required.
* **Multi-app support:** Other than official youtube app, you can select other apps to implement Sponsor Skip for those apps too.
* **Granular Segment Control:** Fully customize your viewing experience. Choose to automatically skip or ignore any of the standard SponsorBlock categories:
  * Sponsors
  * Unpaid / Self Promotion
  * Interaction Reminder
  * Intermission / Intro Animation
  * Endcards / Credits
  * Preview / Recap
  * Hook / Greetings
  * Tangent / Jokes
  * Music: Non-Music Section
* **Time Saved Tracking:** Keep a count of exactly how many segments you have skipped and the total time you have saved. You can reset the count by tapping on it.
* **Modern & Lightweight UI:** Built with a clean, heavily-rounded Material Design aesthetic with monet themeing. 
* **Privacy Respecting:** It runs locally on your device and does not communicate with unnecessary third parties. No data is collected by developer. For more info refer to [Privacy Policy](#privacy-policy)

---

## How it works

**Sponsor Skip** uses a clever workaround relying on Android's native media APIs.

1. **Detection:** Using the `Notification Listener Service`, the app silently monitors your system's active media sessions. When it detects that the official YouTube app or other apps selected by user is playing a video, it securely reads the media metadata to identify the video.
2. **Video ID:** Since `Notification Listener Service` does not provide video id, the app makes a request to `youtube.com` to get the video id.
3. **Fetching Data:** The app takes that video ID and pings the crowd-sourced [SponsorBlock API](https://sponsor.ajay.app/) to download the exact timestamps of any known segments.
4. **Execution:** As you watch the video, Sponsor Skip tracks the playback progress. The exact millisecond the video enters a blocked segment, the app seamlessly skips the segment.

For more technical details refer [Under the Hood](#under-the-hood-for-developers)

---

## Where to Get It

You can download the latest compiled APK directly from the following sources:

<table>
  <tr>
    <th align="center">Codeberg</th>
    <th align="center">Obtainium </th>  </tr>
  <tr>
    <td align="center">
      <a href="https://codeberg.org/jaival/Sponsor-Skip/releases/latest">
        <img src="https://codeberg.org/attachments/273c5a95-5e92-44cd-8735-c79506210e50" alt="Download from Codeberg Releases" height="50">
    </td>    
    <td align="center">
      <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://codeberg.org/jaival/Sponsor-Skip">
        <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/refs/heads/main/assets/graphics/badge_obtainium.png" alt="Download from Obtainium" height="50">
      </a>
    </td>
  </tr>
</table>
        
## Build from Source

If you prefer to compile the app yourself, you can easily build it from the source code using the included Gradle wrapper. 

**Prerequisites:**
* **Git**
* **JDK 17** (or higher)
* **Android SDK** (Command Line Tools)
* The `ANDROID_HOME` environment variable must be set, pointing to your SDK path.
* You must have accepted the SDK licenses (e.g., running `yes | sdkmanager --licenses`).  
*(Note: You can even build this directly on an Android device using Termux, just like the developer does!)*

```
git clone https://codeberg.org/jaival/Sponsor-Skip.git
cd Sponsor-Skip
./gradlew assembleDebug
```
---

## Contributions

Contributions from the open-source community are always welcome!

1. Fork the repository.
2. Create a new branch for your feature (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'feat: add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request!

---

## Bug Reports & Feature Suggestions

Encountered a bug or have a brilliant idea to make Sponsor Skip even better?

* **Found a bug?**   

Open a [Bug Report](https://codeberg.org/jaival/Sponsor-Skip/issues/new?template=.github%2fISSUE_TEMPLATE%2fbug_report.yml) using the provided template. Please include necessary and correct details so it can be squashed quickly!  

* **Have an idea?**   

Open a [Feature Request](https://codeberg.org/jaival/Sponsor-Skip/issues/new?template=.github%2fISSUE_TEMPLATE%2ffeature_request.yml) using the provided template and describe how it would improve the app. 

### Got any questions?

Feel free to open an [Issue](https://codeberg.org/jaival/Sponsor-Skip/issues/new/choose)

*You can also contact me directly via details given at the top, though [Codeberg Issues](https://codeberg.org/jaival/SponsorSkip/issues) is preferred.*

---

## Under the hood (for Developers)

### Tech Stack

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Coroutines](https://img.shields.io/badge/Coroutines-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Material Design](https://img.shields.io/badge/Material_Design-757575?style=for-the-badge&logo=materialdesign&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A.svg?style=for-the-badge&logo=Gradle&logoColor=white)
![SponsorBlock API](https://img.shields.io/badge/SponsorBlock_API-FF0000?style=for-the-badge&logo=youtube&logoColor=white)

### Core Architecture & APIs

Sponsor Skip is built entirely in **Kotlin** and leverages modern Android development practices to interact seamlessly with external applications without requiring root or modifications.

* **Media Session Interception:** The app utilizes the `NotificationListenerService` API. Instead of looking at notification text, it binds to the active media session created by the official YouTube app or other apps selected by the user. This allows Sponsor Skip to securely read the Video Title from the media metadata and track the current playback position in milliseconds.
* **Title-to-ID Resolution (HTML Scraping):** Because Android's media session does not expose the raw video URL, the app must resolve the Title into an ID manually. It does this by executing a silent, unauthenticated background search:
  1. The app URL-encodes the title and fires a native `OkHttp` GET request to `https://www.youtube.com/results?search_query=[TITLE]`.
  2. To prevent YouTube from blocking the request, the app spoofs a standard desktop Chrome `User-Agent`.
  3. The app downloads the raw HTML of the search page and runs a Regular Expression (`/watch\?v=([a-zA-Z0-9_-]{11})`) to instantly scrape the 11-character Video ID of the top result.
* **SponsorBlock API:** Once a video ID is intercepted, the app makes an asynchronous network request to the public [SponsorBlock API](https://sponsor.ajay.app/) to retrieve the start and end timestamps for all community-submitted segments.
* **MediaController Transport Controls:** When the player's current position enters a blocked segment, Sponsor Skip uses `MediaController.TransportControls.seekTo()` to command the Android OS to fast-forward the YouTube player to the end of the segment.
* **Asynchronous Execution:** All network calls, background polling, and update checks are handled cleanly via **Kotlin Coroutines** (`lifecycleScope.launch`) to ensure the main UI thread is never blocked.

### Third-Party Libraries

To keep the application as lightweight as possible, external dependencies are kept to an absolute minimum:
* **[AboutLibraries](https://github.com/mikepenz/AboutLibraries):** By mikepenz. Used to dynamically parse and display the open-source licenses of internal dependencies in the "Credits" UI.

---

## Attributions & Credits

This project stands on the shoulders of giants. A massive thank you to the developers, projects, and communities that make Sponsor Skip possible:

### 1. The Core Engine
* **[SponsorBlock](https://sponsor.ajay.app/):** Created by Ajay Ramachandran ([@ajayyy](https://github.com/ajayyy)) and the incredible SponsorBlock community. This app acts as a specialized Android client for their public API. Without their massive, crowd-sourced database of video timestamps, Sponsor Skip would not exist.

### 2. Third-Party Libraries
* **[AboutLibraries](https://github.com/mikepenz/AboutLibraries):** By Mike Penz. This fantastic library powers the in-app "Credits" screen, elegantly parsing and displaying all the open-source licenses for the Android components used in this project.
* **[Kotlin](https://kotlinlang.org/) & [Android Jetpack](https://developer.android.com/jetpack):** By JetBrains and Google. These provide the robust framework, UI components, and background coroutines that allow the app to run efficiently.

### 3. Assets & Tools
* **[Material Design Icons](https://fonts.google.com/icons):** By Google. All the internal UI iconography (the Settings gear, Feature Request flask, Contact email, etc.) are sourced from the official Material Design vector library.

### 4. Networking & Infrastructure
* **[OkHttp](https://square.github.io/okhttp/) & [Gson](https://github.com/google/gson):** (By Square and Google). Essential libraries utilized for handling the asynchronous network requests and JSON parsing required to communicate with the SponsorBlock API.

### 5. [Sameera](https://github.com/sameerasw)
* Thank you for helping out with app documentation and guidance related to publishing 🙏❤️.


---

## Privacy Policy
Read complete Privacy Policy (human-readable) at [Privacy Policy](PRIVACY.md).  
  
*The [Privacy Policy](PRIVACY.md) was last updated on 25th May, 2026.*

---

## Disclaimer

### Third-Party Affiliation
Sponsor Skip is an independent, open-source project. It is not affiliated with, endorsed by, or in any way officially connected to YouTube, Google LLC, or the SponsorBlock project. All product and company names are trademarks™ or registered® trademarks of their respective holders. Use of them does not imply any affiliation with or endorsement by them.

### Liability and Warranty
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.

### User Responsibility & Platform ToS
This application acts as a neutral, local automation tool. By utilizing Sponsor Skip, you acknowledge that you are solely responsible for ensuring your use of the software complies with the Terms of Service of any third-party platforms accessed (including YouTube and SponsorBlock). The developer assumes no liability for any account restrictions, IP bans, or ToS violations incurred by the user.

###  AI Disclaimer
This project— including the codebase, documentation was developed with the assistance of AI. (P.s. I am new to this, apologies!)

---

## License
Sponsor Skip is licensed under the [GNU General Public License v3.0](LICENSE).  
Copyright © 2026 Jaival

---

<div align="center">

**Made with ❤️ by [Jaival](https://codeberg.org/jaival)**

</div>
