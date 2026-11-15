# Eastward Custom Android (v3)

**Standalone native ARM64 runtime, NVN-to-Vulkan translation layer, and modern touch/setup hub for Eastward (`010071B00F63A000`) on Android.**

> [!IMPORTANT]
> **Strict Legal Compliance:** This repository contains **source code only**. No Nintendo Switch game files (`.nsp` / `.xci` / `.nca`), decryption keys (`prod.keys` / `title.keys`), `RomFS`/`ExeFS` dumps, captured shaders (`.glslc`), or proprietary fonts are included. You must supply dumps from your own legally owned copy of the game and console.

## Highlights (v3 — `com.eastward.custom`)

- **Dedicated Eastward Runtime:** Built cleanly without unrelated title workarounds or stock leftover presets; targets Android 10–15+ (`minSdk 29`, `targetSdk 35`) with 16 KB ELF page alignment (`0x4000`) verified across all native ARM64 shared libraries.
- **Cross-GPU Vulkan Backend:**
  - **Qualcomm Adreno (Snapdragon):** Supports custom Mesa **Turnip** Vulkan drivers via `libadrenotools` with automatic fallback to the system Vulkan driver on initialization or swapchain errors.
  - **ARM Mali (MediaTek Dimensity / Exynos):** Uses the system Vulkan driver cleanly with Mali-safe defaults (`DM_VK_MALI_FP16=1`, `DM_VK_CLEAR_LOADOP=1`, `DM_VK_SET_REUSE=1`).
- **On-Device `.nsp` + `prod.keys` Setup Hub:** Modern dark-glass launcher (`SetupActivity`) supporting game file selection, `prod.keys` import, Vulkan driver `.zip` installation, save `.zip` backup/restore, and resumable single-process SPIR-V translation.
- **Modern In-Game UI & Vector Touch Overlay:**
  - Right-edge slide-in dark-glass pause drawer (`GameMenu`) with Quick, Controls, Graphics, and System tabs.
  - Ergonomic Eastward RPG touch overlay featuring vector SVG action icons (`Y` Pan Attack, `A` Interact, `X` Sam Sparkle, `B` Group, `R` Swap Weapon, `ZR` Swap Character, `L` Bag, `+` Pause, `-` Map) with Switch keycap corner badges, matching gold/obsidian Analog Stick & D-Pad styling, non-overlapping `2D` & `3D` presets, custom drag/resize layout editor, and automatic low-opacity dimming after 8 seconds of idle screen time (wakes instantly on any touch).

## Building From Source

1. Place Android SDK (`platforms/android-35`, `build-tools/36.0.0`), NDK `28.2.13676358`, JDK 11+, and prebuilt Python/toolchain runtime dependencies under `../dpc_home/` & `prebuilt/` (or set `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME`, `JAVA_HOME`).
2. Run:
   ```bash
   python dm/android/tools/gen_assets.py
   python build_apk.py
   ```
3. Output signed APK will be generated at `out/EastwardCustom.apk` (`com.eastward.custom`).

## License & Attribution

Licensed under **GPL-3.0-or-later** ([LICENSE](LICENSE)), incorporating MIT-licensed upstream components ([LICENSE-MIT](LICENSE-MIT)) from [`d2r-mobile` / `nxnative`](https://github.com/jhaizhou) and `Galar-Native-Android`. See [NOTICE](NOTICE) for complete copyright, third-party dependency notices, and trademark disclaimers.
