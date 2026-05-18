# Privacy Policy for Sponsor Skip

Sponsor Skip is a privacy-first, open-source application. As the developer, I (Jaival) do not collect, store, or transmit any of your personal data, telemetry, or analytics.

## How Data is Processed

* **YouTube Search Scraping:** To function natively without root, the app reads the currently playing media title from your Android system. It securely sends this title directly to `youtube.com` via a background search request to scrape the underlying Video ID.
* **SponsorBlock API:** Once the Video ID is extracted, it is sent securely to the public SponsorBlock API (`sponsor.ajay.app`) to fetch the skip timestamps.
* **Updates:** The app periodically checks the Codeberg repository (`codeberg.org/jaival/Sponsor-Skip`) to verify if new releases are available.

## Local Storage
Your application settings, skip statistics (time saved), and debug logs (if explicitly enabled by you) are stored locally on your device and are never transmitted to me or any third party.
