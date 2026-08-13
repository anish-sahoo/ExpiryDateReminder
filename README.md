# Expiry Date Reminder

Keeps track of what's about to go off, so you find out before you open the fridge
and not after. Food, medicine, warranties, passports, anything with a date on it.

<p align="left">
<a href="https://play.google.com/store/apps/details?id=com.anish.expirydatereminder">
    <img alt="Get it on Google Play"
        height="80"
        src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
</a>
</p>

## About

Created by Anish Sahoo and released under the MIT license.

Version 2.0 is a rewrite of the 2019-2022 Java app. That history is still in this
repo, below the rewrite commit.

## Features

- Scan the printed date off a label with the camera instead of typing it
- Items grouped by urgency, with a count of what needs attention
- Home screen widget, showing more detail the bigger you make it
- Notifications at a lead time and hour you choose
- Photos and notes per item, search, sorting, built-in and custom categories
- Date formats picked from your region, changeable in settings
- Eight languages, and colors that follow your wallpaper on Android 12+

Everything is stored on device. There is no account, and the camera is used only
when you scan.

## Technicalities

- Kotlin 2.4, Compose Multiplatform, Material 3 Expressive
- SQLDelight for the local database, Koin for DI, kotlinx-datetime
- ML Kit Text Recognition, with the GenAI Prompt API where the device supports it
- Glance for the widget, WorkManager for reminders
- minSdk 31 (Android 12), targetSdk 36, JVM 21
- Item photos live in the app's private storage, readable only by the app

Upgrading from 1.5 imports your existing items and photos automatically on first
launch.

Privacy policy in [PrivacyPolicy.md](PrivacyPolicy.md). Notes for contributors in
[AGENTS.md](AGENTS.md).
