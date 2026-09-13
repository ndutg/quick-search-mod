# Quick Search – Privacy Policy

Last updated: 2026-09-13

Quick Search is an Android app and optional home screen launcher that lets you search apps, contacts, calendar events, device files, notes, settings, and the web from a single screen. This policy explains what data the app accesses, how it is used, and your choices.

## Data the app accesses
- Search input: Text you type is processed on-device to show local results. It is not stored after the session, except as described under Web suggestions, AI features, and external searches below.
- Web suggestions (optional): When web suggestions are enabled, the text you type is sent to Google's search suggestion service (suggestqueries.google.com) to show suggested searches. This is on by default in the Google Play version and off by default in the F-Droid version, and can be turned off in settings.
- Apps: App names, package IDs, launch counts, and last-used timestamps (requires Usage Access) to rank and show recently used apps.
- Contacts (optional): Contact names, phone numbers, and your chosen “preferred number” when you enable contact search.
- Calendar (optional): Event titles, start and end times, all-day status, and recurrence rules from your device calendars, to search and show upcoming events. Quick Search only reads calendar data; it does not create, change, or delete events.
- Files (optional): File names, types, URIs, and basic metadata to show matching files; no file contents are read.
- Notes: Notes you create in Quick Search are stored locally on your device.
- Notification access (optional): When you enable Notification Dots, Quick Search uses Android's NotificationListenerService only to see which apps currently have notifications, so it can show a dot on their icons. It checks the app package and user profile of each notification. It does not read notification content such as titles or messages, and nothing is stored or shared.
- Accessibility service (optional): When you enable the Lock Screen gesture, Quick Search uses Android's AccessibilityService API only to perform the system Lock Screen action after you double-tap the Home screen. Through this service, Quick Search does not read, collect, store, or share screen content or other personal data.
- App lock (optional): If you lock apps, Quick Search uses Android's system biometric or screen-lock prompt to confirm it is you. The prompt is handled by Android; Quick Search only receives whether authentication succeeded and never accesses your fingerprint, face, or PIN data.
- Preferences: Hidden/pinned apps, contacts, files, nicknames, section order, layout choices, aliases, custom search engines, pinned notification shortcuts, and widget settings stored locally. Sensitive items such as AI provider API keys are stored with EncryptedSharedPreferences when available.
- AI features (optional): If you add an API key for an AI provider (Google Gemini, OpenAI, Anthropic, Groq, Meta, or a custom OpenAI-compatible endpoint you configure) and use AI answers or AI-powered tools (such as weather and dictionary lookups), your query and any personal context you provide are sent to that provider to generate a response. For weather, the location you enter or save is included. Request details may be logged to your device's logcat for troubleshooting, with API keys redacted, and are not sent to the developer.
- Network calls: Other web searches you launch (Google, Maps, Play, Reddit, YouTube, Amazon, ChatGPT/Perplexity/Grok links, custom search engines, etc.) are opened in the chosen browser/app and handled under those providers' policies.

## How we use data
- Provide search results for apps, contacts, calendar events, files, notes, settings, and shortcuts.
- Show notification dots, lock apps, and perform the Lock Screen gesture when you enable those features.
- Maintain your preferences (pinned/hidden items, nicknames, filters, widget look, shortcuts).
- Generate AI answers through the provider you choose, when you request them.
- Launch external search providers you pick; those providers receive the query you submit.

## Data sharing and transfers
- We do not sell data, run ads, or use third-party analytics. No backend operated by the developer receives your data.
- AI requests are sent over HTTPS directly from your device to the AI provider whose API key you added.
- Web suggestions, when enabled, send your typed text to Google.
- Queries you open with other search engines are sent to those providers at your direction.
- The app also makes these requests that do not include your personal data:
  - Daily currency exchange rates from the European Central Bank (ecb.europa.eu) for the currency converter.
  - Release notes from GitHub (api.github.com).
  - Website icons from Google (google.com/s2/favicons) when you add a custom search engine.
  - In the Google Play version, Google Play's in-app review and update services.

## Storage and retention
- Preferences, notes, and caches are stored locally on your device. API keys are stored encrypted when the device supports it; personal context is stored locally in preferences.
- Some preferences (such as layout and pinned items) may be included in Android's device backup to your Google account if you have backup enabled. API keys, usage history, launch counts, and hidden-item lists are excluded from backup.
- App, contact, calendar, and file data are refreshed from the device as needed. Clearing the app's data or uninstalling removes locally stored preferences, notes, and caches. You can also clear cached apps from in-app settings.

## Permissions
- Usage Access (required): Needed to list and rank recently used apps.
- QUERY_ALL_PACKAGES: Declared to show installed apps in search results and the launcher grid.
- Contacts (optional): Needed to search and act on your contacts.
- Phone calls (optional): Lets Quick Search place a call directly when you tap call on a contact. Without it, calls open in your dialer instead.
- Calendar (optional): Needed to search and show your calendar events. Read-only.
- Storage/Media and All files access (optional): Needed to search file names across your device storage. File contents are not read.
- Notification access (optional): Used only for Notification Dots, as described above. You grant it in Android settings and can revoke it at any time.
- Accessibility service (optional): Used only to lock the device when you invoke the configured double-tap Home gesture. You must explicitly agree before Quick Search opens Android's Accessibility settings, and you can disable the service at any time in Android settings.
- Notifications (optional): Used to show shortcuts and notes you pin to the notification shade.
- Expand status bar: Used to open the notification shade when you use the configured gesture.
- Request app uninstall: Used to open Android's uninstall dialog when you choose to uninstall an app. Android always asks you to confirm.
- Biometric: Used through Android's system prompt for app lock, as described above.
- Vibration: Used for haptic feedback.
- Network: Used for web suggestions, AI features, currency rates, release notes, website icons, and external searches, as described above.

## Security
- Local processing by default; HTTPS is used for outbound requests.
- API keys are stored with EncryptedSharedPreferences where supported and excluded from backups. No backend operated by the developer stores your data.

## Your choices
- Turn off web suggestions in settings to stop sending typed text to Google.
- Do not add an AI provider API key if you do not want queries sent to AI providers; remove keys and personal context in settings to stop further use.
- Turn off or decline Contacts, Phone, Calendar, and Storage permissions to keep those data types inaccessible.
- Leave Notification Dots off, or revoke Quick Search's notification access in Android settings.
- Choose “Not now” when asked about Accessibility access, or disable “Quick Search Lock Screen” in Android's Accessibility settings, to keep the Lock Screen gesture inactive.
- Clear app data or uninstall to remove local preferences, notes, and caches; use in-app options to clear cached apps or edit pinned/hidden items.

## Children
Quick Search is not directed to children under 13 and should not be used by them.

## Changes
We may update this policy. Material changes will be reflected by updating the “Last updated” date above.

## Contact
Questions? Contact us at tejakarlapudi.apps@gmail.com.
