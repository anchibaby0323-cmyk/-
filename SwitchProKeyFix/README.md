# SwitchProKeyFix

Android 16 / HyperOS 3 helper for Nintendo Switch Pro Controller (VID 057e, PID 2009).

Goal: one-tap swap of A↔B and X↔Y using an Android key-layout override, with Shizuku for privileged file access.

Safety design:
- Does not modify /system, /vendor, or /odm.
- Uses /data/system/devices/keylayout/ only if writable through Shizuku.
- Creates a version-specific keylayout override when possible.
- Keeps all other mappings unchanged by copying the device's existing Switch Pro .kl file first, then swapping only BUTTON_A/B/X/Y.
- Restore removes only the app-created override.

Build:
- GitHub Actions workflow included under .github/workflows/build-switchprokeyfix.yml
- Debug APK is uploaded as an artifact after a successful build.
