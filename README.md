# Lnkee Android

Native Android wrapper for [lnkee.ir](https://lnkee.ir/).

- Minimum Android version: Android 7.0 (API 24)
- Application ID: `ir.lnkee.app`
- Entry URL: `https://lnkee.ir/`
- Native Persian connection screen for offline and server-load failures (no browser error page)

Open this folder in Android Studio and choose **Build > Build APK(s)**. The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

For Play Store release, create a signing key and use Android Studio's **Generate Signed App Bundle / APK** option.

## Updating without uninstalling

Android installs a new APK over an existing app only when its application ID and signing key are the same and its `versionCode` is higher. The GitHub workflow automatically increments `versionCode` with each run.

To enable signed release builds, add these repository secrets in **Settings → Secrets and variables → Actions**. Never commit the keystore to the repository:

- `ANDROID_KEYSTORE_BASE64`: Base64 contents of the `.jks` or `.keystore` signing file
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password
- `ANDROID_KEY_ALIAS`: Key alias
- `ANDROID_KEY_PASSWORD`: Key password

After those secrets are configured, the workflow creates `app-release.apk`. Install that signed release once; later release APKs will update it without uninstalling.
