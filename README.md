# AutoText Hub - Missed Call Auto-Reply Android App

A full-featured Android application that automatically sends SMS messages to callers when you miss their calls.

## Build Flavors

This project builds **two variants**:

| Flavor | Mode | Distribution | Permissions |
|--------|------|--------------|-------------|
| **play** | Assisted Reply | Google Play Store | No SMS/Call Log (tap-to-send) |
| **sideload** | Full Auto | GitHub/Direct APK | Full auto-SMS via Call Log |

## Features

- **Automatic Missed Call Detection**: Uses `NotificationListenerService` to detect missed call notifications from the system dialer
- **Instant SMS Response**: Automatically sends your custom message to the caller (sideload) or prompts tap-to-send (Play)
- **Multi-Language Support**: Detects missed call notifications in multiple languages (English, Spanish, French, German, Portuguese, Italian, Japanese, Chinese, Korean, Russian)
- **Cooldown Protection**: Prevents duplicate messages with configurable cooldown (default: 5 minutes per number)
- **Number Blocking**: Block specific numbers from receiving auto-replies
- **Message Logging**: Keep track of all sent messages with timestamps
- **Customizable Templates**: Edit your auto-reply message with quick template options
- **Battery Optimization Bypass**: Keeps service running in background
- **Boot Persistence**: Automatically restarts after device reboot

## Requirements

- Android 8.0 (API 26) or higher
- **Sideload version** Required Permissions:
  - `READ_CALL_LOG` - To retrieve missed caller's phone number
  - `READ_PHONE_STATE` - To detect phone state changes
  - `SEND_SMS` - To send auto-reply messages
  - `POST_NOTIFICATIONS` - To show service status (Android 13+)
  - Notification Listener Access - To detect missed call notifications
- **Play Store version**: Only needs Notification Listener Access and POST_NOTIFICATIONS

---

## Build Instructions

### 1. Create Release Keystore (One Time)

```bash
keytool -genkeypair -v -keystore autotexthub-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias autotexthub
```

You'll be prompted for passwords. **Save these securely!**

### 2. Set Environment Variables

```bash
export KEYSTORE_PATH="autotexthub-release.jks"
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="autotexthub"
export KEY_PASSWORD="your_key_password"
```

### 3. Build Release Artifacts

**Play Store (AAB for Google Play):**
```bash
./gradlew clean :app:bundlePlayRelease
```
Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`

**Sideload (APK for direct distribution):**
```bash
./gradlew clean :app:assembleSideloadRelease
```
Output: `app/build/outputs/apk/sideload/release/app-sideload-release.apk`

### 4. Debug Builds (for testing)

```bash
# Play flavor debug
./gradlew assembleSideloadDebug

# Sideload flavor debug  
./gradlew assemblePlayDebug
```

---

## Distribution

### Google Play Store (play flavor)

1. Create app in [Play Console](https://play.google.com/console)
2. Upload AAB to **Internal Testing** first
3. Complete required forms:
   - Data Safety declaration
   - Privacy Policy URL
   - App content rating
4. Store listing: screenshots, descriptions, icon
5. Roll out: Internal → Closed → Production

### Internet/Sideload Distribution (sideload flavor)

**Option A: GitHub Releases**
1. Create a GitHub repo
2. Upload signed APK to Releases
3. Add landing page with download link + QR code

**Option B: Firebase App Distribution**
- Great for beta testers

**Option C: Direct hosting**
- Upload APK to your website

---

## Installation

### From APK (Sideload)

1. Transfer APK to device
2. Enable "Install from unknown sources" in Settings
3. Open APK and install

### Initial Setup

1. **Launch the app** and grant all requested permissions
2. **Enable Notification Access**: 
   - Go to Settings → Notification Access → Enable "AutoText Hub"
3. **Disable Battery Optimization**:
   - The app will prompt you to disable battery optimization
   - This ensures the service keeps running in the background
4. **Customize your message**:
   - Tap "Edit Message Template" to personalize your auto-reply

## Project Structure

```
AutoTextHub/
├── app/
│   ├── src/
│   │   ├── main/                             # Shared code (both flavors)
│   │   │   ├── AndroidManifest.xml           # Base manifest
│   │   │   ├── java/com/charlotteservicehub/autotext/
│   │   │   │   ├── data/
│   │   │   │   │   ├── AutoTextPreferences.kt    # Settings storage
│   │   │   │   │   ├── MessageLog.kt             # Log data model
│   │   │   │   │   └── MessageLogDatabase.kt     # SQLite database
│   │   │   │   ├── receiver/
│   │   │   │   │   ├── BootReceiver.kt           # Boot completion handler
│   │   │   │   │   ├── SmsDeliveredReceiver.kt   # Delivery status
│   │   │   │   │   └── SmsSentReceiver.kt        # Send status
│   │   │   │   ├── service/
│   │   │   │   │   └── MissedCallListenerService.kt  # Core service
│   │   │   │   └── ui/
│   │   │   │       ├── MainActivity.kt           # Main dashboard
│   │   │   │       ├── MessageTemplatesActivity.kt # Edit messages
│   │   │   │       └── SettingsActivity.kt       # App settings
│   │   │   └── res/
│   │   │       ├── layout/                       # UI layouts
│   │   │       ├── drawable/                     # Icons and shapes
│   │   │       ├── values/                       # Colors, strings, themes
│   │   │       └── menu/                         # Menu resources
│   │   ├── play/                             # Play Store flavor
│   │   │   └── AndroidManifest.xml           # Removes SMS/CallLog permissions
│   │   └── sideload/                         # Sideload flavor
│   │       └── AndroidManifest.xml           # Keeps all permissions
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── autotexthub-release.jks                   # Release keystore (generate this!)
```

## How It Works

### Sideload Flavor (Full Auto)
1. **Notification Monitoring**: The `MissedCallListenerService` listens for all system notifications
2. **Missed Call Detection**: When a notification from a known dialer package contains "Missed call" (in any supported language), it triggers the auto-reply flow
3. **Number Retrieval**: The service queries the `CallLog` for the most recent missed call within the last 60 seconds
4. **SMS Sending**: If a valid number is found and not in cooldown/blocked, the message is sent via `SmsManager`
5. **Logging**: All sent messages are logged to a local SQLite database

### Play Flavor (Assisted Reply)
1. **Notification Monitoring**: Same as above
2. **Missed Call Detection**: Same as above
3. **Number Extraction**: Extracts phone number directly from the notification text (no CallLog access)
4. **Tap-to-Send Prompt**: Shows a notification with a "Send Reply" button that opens the SMS app with pre-filled message
5. **User Taps**: User confirms by tapping, which opens the default SMS app

## Compliance Notes

- **Include opt-out**: Always include "Reply STOP to opt out" in your message (US TCPA compliance)
- **Play Store**: Distribution on Google Play requires being approved as a default SMS app or meeting Play's communication app policies
- **Sideloading**: For personal/enterprise use, sideloading bypasses these restrictions

## Customization

### Default Message Template
```
Hey! It's Charlotte Service Hub—sorry I missed your call. 
You can text me here with what you need (pics welcome). 
Reply STOP to opt out.
```

### Adding Support for More Dialer Apps

Edit `MissedCallListenerService.kt` and add package names to:
```kotlin
private val DIALER_PACKAGES = setOf(
    "com.google.android.dialer",
    "com.android.server.telecom",
    // Add your device's dialer package here
)
```

### Adding More Language Support

Edit the `MISSED_CALL_KEYWORDS` list in `MissedCallListenerService.kt`:
```kotlin
private val MISSED_CALL_KEYWORDS = listOf(
    "missed call",
    "llamada perdida",
    // Add more translations here
)
```

## Troubleshooting

### Service Not Detecting Missed Calls

1. Ensure Notification Access is enabled for the app
2. Check that battery optimization is disabled
3. Verify the dialer package name is in the `DIALER_PACKAGES` list
4. Check logcat for debugging: `adb logcat -s MissedCallListener`

### SMS Not Sending

1. Verify `SEND_SMS` permission is granted
2. Check if the device has cellular service
3. Ensure the phone number is valid
4. Check if the number is in the blocked list

### Service Stops After Some Time

1. Disable battery optimization
2. Lock the app in recent apps (varies by device)
3. On some devices (Xiaomi, Huawei), additional settings may be required

## License

Private/Enterprise use. Not for public distribution without authorization.

## Support

For issues or feature requests, contact Charlotte Service Hub support.
