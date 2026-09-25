# AnchorWatch

Android GPS anchor alarm for overnight watches.

Drop the hook on your current GPS fix, set a swing radius, and keep a live OpenStreetMap view of the boat track inside the circle. If the boat stays outside the circle long enough to beat GPS jitter, the phone rings on the alarm stream and vibrates.

No Google Maps key. No account. No ads. Location never leaves the phone.

## Features

- Settable swing radius in feet or meters (25-500 ft)
- Map with hook, boat, radius circle, and track
- Foreground service so the watch keeps running with the screen off
- Dwell timer (3-20 s) before the alarm to cut false wakes
- Shows GPS accuracy so you can size the circle as rode + 2x GPS error
- Test alarm, silence, and weigh-anchor from the app or the notification
- Dark night-watch UI

## Install the APK

1. Open [Actions](https://github.com/jimgrok-maker/AnchorWatch/actions) on this repo
2. Open the latest **Build APK** run
3. Download the **AnchorWatch** artifact and unzip it
4. Copy `app-release.apk` to the phone
5. On the phone: Settings - allow install from that file manager / browser
6. Open the APK and install

The release APK is signed with the Android debug key so you can sideload it. It is not Play Store signed.

## First-night setup

Android will try to sleep GPS. Before you turn in:

1. Grant **precise location**
2. Tap **All-the-time location** and set Location permission to **Allow all the time**
3. Tap **Ignore battery saver** and allow unrestricted battery
4. Tap the bell to test that the alarm is loud enough with the screen off
5. Wait for a GPS lock, set radius, tap **Drop anchor**

Leave the phone where it can see sky - not down in a closed locker.

## Build locally

Open the project in Android Studio (Ladybug / Koala or newer) or run:

```
gradle :app:assembleRelease
```

APK lands at `app/build/outputs/apk/release/app-release.apk`.

## How the alarm decides

Distance is great-circle from the stored hook to the latest GPS fix. The watch only alarms after the boat has been outside the circle for the dwell time. Silencing stops the sound; it does not weigh anchor. Weighing anchor stops the service.

This is a skipper aid, not a substitute for a proper anchor watch.
