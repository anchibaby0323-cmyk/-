# SwitchProKeyFix v0.2.0

Android 16 / HyperOS 3 helper for Nintendo Switch Pro Controller HAC-013 (`057e:2009`).

## What changed in v0.2.0

The old key-layout override approach was removed because this POCO / HyperOS build denies Shizuku shell access to `/data/system/devices/keylayout` and the active controller layout is the read-only `/system/usr/keylayout/Vendor_057e_Product_2009.kl`.

v0.2.0 instead uses a real input bridge:

1. Shizuku UserService runs as shell UID.
2. Finds the Switch Pro gamepad under `/dev/input/event*` by VID/PID.
3. Uses `EVIOCGRAB` so Android does not receive the original controller twice.
4. Creates a virtual Xbox 360 controller with Linux `/dev/uinput`.
5. Converts the Switch Pro raw face-button semantics so the final Android result follows the printed Nintendo labels:
   - physical A -> Android A
   - physical B -> Android B
   - physical X -> Android X
   - physical Y -> Android Y
6. Passes sticks, shoulder buttons, stick clicks and hat/d-pad through. ZL/ZR digital buttons are converted to Xbox trigger axes.

No root and no `/system` modification.

## Important

This is the first hardware-test build of the evdev/uinput bridge. The two device-specific permissions that still need real-phone verification are:

- shell read + `EVIOCGRAB` on the Switch Pro `/dev/input/eventX`
- shell create access to `/dev/uinput`

The app has a **診斷手把 / uinput** button that reports both before starting the bridge.

## Build

GitHub Actions workflow: `.github/workflows/build-switchprokeyfix.yml`

The debug APK is uploaded as the `SwitchProKeyFix-debug-apk` workflow artifact.
