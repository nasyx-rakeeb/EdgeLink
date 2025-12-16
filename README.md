#EdgeLinkEdgeLink is a lightweight Desktop Window Manager implementation for Android. It runs as a system service to provide a floating window environment, allowing apps to run in resizable, freeform containers on top of the standard Android UI.

Designed for LineageOS and AOSP-based ROMs, it mimics features found in Samsung DeX or Motorola Ready For, but implemented natively using Android's `VirtualDisplay` and `WindowManager` APIs.

##Features* **Floating Windows:** Run apps in freeform windows with resize handles and maximize/restore controls.
* **Edge Sidebar:** A global slide-out launcher accessible from anywhere (customizable position).
* **Smart Task Management:**
* **Single Focus Mode:** Minimizes background windows automatically to keep the workspace clean.
* **Bubble Stacking:** Minimized apps stack vertically on the screen edge.
* **Pinning:** Keep frequently used apps at the top of the sidebar.


* **Dynamic Orientation:** Automatically adjusts window sizes for Portrait and Landscape modes.
* **Overlay Support:** Works on top of fullscreen apps (e.g., YouTube, VLC).

##ArchitectureEdgeLink is not a standard launcher; it is a privileged system application.

1. **Service-Based:** `EdgeLinkService` runs in the background and manages the `WindowManager` views.
2. **Virtual Displays:** It creates a unique `VirtualDisplay` for each floating app to render the content off the main stack.
3. **Input Injection:** Uses `TouchInjector` to pass touch events from the overlay window into the virtual display.

##Integration GuideSince EdgeLink requires `INJECT_EVENTS` and `MANAGE_ACTIVITY_TASKS` permissions, it **must** be installed as a system app (`priv-app`). It cannot be installed as a standard APK on a non-rooted device.

###1. Add to Source TreeClone the repo into `packages/apps/EdgeLink`:

```bash
git clone https://github.com/your-username/EdgeLink packages/apps/EdgeLink

```

###2. Update Device MakefileAdd the package and its permissions to your device's makefile (e.g., `device/xiaomi/veux/device.mk`):

```makefile
# EdgeLink Desktop Mode
PRODUCT_PACKAGES += \
    EdgeLink \
    privapp_permissions_edgelink

```

###3. BuildBuild your ROM as usual. The `Android.bp` file handles the compilation and permission XML copying.

```bash
m bacon

```

##Usage1. **Open Sidebar:** Swipe the handle (default: left edge) to open the launcher.
2. **Launch App:** Tap an app in the sidebar to open it in a floating window.
3. **Minimize:** Tap the `-` icon in the window header. The app will shrink to a bubble on the right edge.
4. **Maximize:** Tap the bubble to restore the window.
5. **Resize:** Drag the handle in the bottom-right corner of a window.
6. **Pin Apps:** Go to Settings -> App Management -> Pin Apps to keep favorites at the top of the sidebar.

##SettingsA dedicated Settings panel is included to customize:

* Sidebar Position (Left/Right).
* Handle Size, Opacity, and Vertical Offset.
* Panel Transparency and Height.

##Requirements* (Tested on LineageOS 23).
* `platform` signature keys (or whitelisted priv-app permissions).

## License
[MIT License](LICENSE)
