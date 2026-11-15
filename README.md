# Eastward Custom Android (v3)

[![Language: English](https://img.shields.io/badge/lang-English-blue.svg)](README.md)
[![Language: Indonesia](https://img.shields.io/badge/lang-Bahasa%20Indonesia-red.svg)](README.id.md)
[![Latest Release](https://img.shields.io/badge/Download-APK%20(Releases)-gold.svg)](https://github.com/Itsneslab/Eastward-Custom-Android/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3+-green.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/Android-10%20to%2015%2B%20(API%2029--35)-brightgreen.svg)](dm/android/app/AndroidManifest.xml)
[![Architecture](https://img.shields.io/badge/ABI-arm64--v8a%20(16KB%20aligned)-orange.svg)](dm/android/CMakeLists.txt)

**A dedicated, standalone native ARM64 compatibility runtime, NVN-to-Vulkan translation layer, and modern dark-glass Android touch launcher built exclusively for _Eastward_ (`010071B00F63A000`).**

> [!IMPORTANT]
> **Strict Legal & Copyright Compliance:**
> This repository contains **source code, UI resources, shader translation tooling, and original SVG icon assets only**.
> - **No copyrighted Nintendo Switch game dumps** (`.nsp`, `.xci`, `.nca`), decryption keys (`prod.keys`, `title.keys`), `RomFS`/`ExeFS` archives, captured game shaders (`.glslc`), or proprietary system fonts (`standard.ttf`) are included or distributed.
> - You must supply files dumped from **your own legally owned copy of Eastward (`010071B00F63A000`)** and console keys.
> - _Eastward_ is a trademark of Pixpil / Chucklefish. _Nintendo Switch_ is a trademark of Nintendo Co., Ltd. This project is an independent open-source technical preservation runtime and is not affiliated with or endorsed by Pixpil, Chucklefish, or Nintendo.

---

## Table of Contents

- [Why v3 Exists (Architecture Comparison)](#why-v3-exists-architecture-comparison)
- [How It Works vs Emulator vs NSXRecomp vs DroidPlay](#how-it-works-vs-emulator-vs-nsxrecomp-vs-droidplay)
- [Key Features (v3 — `com.eastward.custom`)](#key-features-v3--comeastwardcustom)
- [Hardware & GPU Compatibility](#hardware--gpu-compatibility)
- [Project Structure](#project-structure)
- [On-Device Setup & Controls Guide](#on-device-setup--controls-guide)
- [Building From Source](#building-from-source)
- [Project History & Engineering Notes](PROJECT_NOTES.md)
- [Contributors & Credits](#contributors--credits)
- [License & Legal Notices](#license--legal-notices)

---

## Why v3 Exists (Architecture Comparison)

Earlier community experiments packaged _Eastward_ inside generic multi-title or Pokémon-derived Android shells (v1/v2), leaving behind unrelated game workarounds, mismatched presets, stock dialog menus, and fragile cross-GPU defaults.

**Eastward Custom (v3)** separates cleanly into its own dedicated package (`com.eastward.custom`) focused solely on Title ID `010071B00F63A000`:
- Zero unrelated game hacks or leftover UI/controller presets.
- Clean 16 KB ELF page alignment (`0x4000`) verified across all packaged `arm64-v8a` shared libraries for Android 15+ devices.
- Cross-GPU Vulkan resilience supporting both **Qualcomm Adreno (Mesa Turnip + automatic system driver fallback)** and **ARM Mali / MediaTek Dimensity / Exynos**.
- Modern dark-glass launcher (`SetupActivity`) and right-edge slide-in pause drawer (`GameMenu`), paired with custom-crafted Eastward SVG icons and smart 8-second idle control dimming.

---

## How It Works vs Emulator vs NSXRecomp vs DroidPlay

Understanding where **Eastward Custom (v3)** sits in the Android/Switch native ecosystem:

| Approach / Project | Execution Model | Graphics Pipeline | Used in Eastward Custom? |
| :--- | :--- | :--- | :--- |
| **Traditional Emulators** (Yuzu, Ryujinx, etc.) | Dynamic JIT / NCE virtualization of full Switch console OS & hardware | Runtime shader decompilation + heavy GPU emulation | **No** |
| **NSXRecomp / nxrecomp** | **Static Recompilation**: lifts Switch AArch64 machine code into C++ source files, then recompiles via Clang/NDK | Custom graphics translation / host backend | **No** |
| **DroidPlay Core (`dpc` / `dm` — `droidplay-core-0.8.10`)** | **Direct ARM64 Binary Runtime (`dm`)**: patches NSO executables cleanly on-device (`patch_nso.py` / `loader.c`) and executes ARM64 instructions directly over C Horizon OS HLE services (`dm/host/hle_*.c`) | Direct **NVN-to-Vulkan** renderer (`gfx_vk.c`, `gfx_vk_*.inc`) + Python/native **SPIR-V translation** (`spv_build.py` / `spv_android.py` / `libdmsh_xlate.so`) | **Yes — Core engine underneath v3!** |

### How v3 combines DroidPlay Core (`dm`) with a Custom Eastward Shell
1. **Why DroidPlay Core (`dm`) instead of NSXRecomp?** Android phones are already `arm64-v8a`. `DroidPlay Core` (`dm`) runs Eastward's native AArch64 instructions directly in user space after ELF/NSO relocation and ABI fixups — avoiding emulator CPU virtualization **and** avoiding generating massive lifted C++ codebases like `NSXRecomp`.
2. **Why build a clean v3 shell instead of the older v2 fork?** Earlier v2 tests reused a Pokémon-derived community shell (`Galar-Native`), causing extra overhead, controller flicker, and unrelated game hacks. **Eastward Custom (v3)** keeps the fast **DroidPlay (`dm`) runtime & Vulkan backend** while replacing the Android UI, SVG controls, and on-device `.nsp` + `prod.keys` setup cleanly for Title ID `010071B00F63A000` (`dm/games/010071B00F63A000/`, `EXPECTED_CHAIN = 1a0d0a59a263`).

---

## Key Features (v3 — `com.eastward.custom`)

- **On-Device `.nsp` + `prod.keys` Setup Hub (`SetupActivity`):**
  - Dark-glass Obsidian & Eastward Gold UI (`#0D0F13` / `#1C1F26` / `#F5C428`).
  - Pick your legally owned `.nsp` dump and `prod.keys` directly from Android storage — `libnx_extract.so` + Python toolchain (`setup_game.py` & `spv_android.py`) unpack, patch (`main.fixups.tsv`), and translate shaders (`spv/`) cleanly on the device.
  - Resumable single-process SPIR-V compiler UI with real-time progress bar and live setup log.
  - Built-in **Vulkan Driver `.zip` Manager** (`DriverPackages.java`) and **Save Backup / Restore `.zip`** (`SaveManager.java`).
  - Direct **GitHub repository link** right inside the launcher.
- **Custom Ergonomic Eastward Touch Overlay (`Gamepad.java`):**
  - 9 original vector SVG RPG action icons (`dm/android/app/eastward_icons/`) paired with clear **Switch keycap corner badges** (`Y`, `A`, `X`, `B`, `R`, `ZR`, `L`, `+`, `-`).
  - Matching **Obsidian & Eastward Gold (`#FAD44A` / `#F6C634`)** Left Analog Stick and D-Pad.
  - Non-overlapping **`2D` and `3D` controller presets** + live drag/resize custom layout editor (`Edit Layout`).
  - **8-second idle auto-dimming** (`IDLE_DIM_AFTER_MS = 8000`): idle buttons fade gently out of the way for unobstructed pixel art exploration and snap back immediately on any screen touch.
- **Modern Slide-In In-Game Menu (`GameMenu.java`):**
  - Slide-in right drawer with 4 organized tabs: **Quick**, **Controls**, **Graphics**, and **System**.
  - On-the-fly toggles for 30/60 FPS, FSR, Shader Skip / Frame Cache, Dynamic Resolution (`ue_dynres_android.c`), and haptic feedback (`haptics_android.c`).
- **Low-Latency Audio & Video:**
  - Native **AAudio** low-latency audio pipeline (`coreaudio_aaudio.c`, `hle_audren.c`).
  - Integrated video playback & AV1/VPX decoding (`av1_android.c`, `hle_movie.c`, `libvpx`).

---

## Hardware & GPU Compatibility

| SoC / GPU Vendor | Default Driver Strategy | Built-In Safety & Stability Behavior |
| :--- | :--- | :--- |
| **Qualcomm Snapdragon (Adreno 6xx / 7xx / 8xx)** | Mesa **Turnip** custom Vulkan driver via `libadrenotools` (user-installable `.zip` supported) | Automatic fallback to Android **System Vulkan** if Turnip initialization, surface, or swapchain creation fails |
| **MediaTek Dimensity / Samsung Exynos (ARM Mali / Xclipse)** | Android **System Vulkan** driver | Mali-safe shader & renderpass defaults (`DM_VK_MALI_FP16=1`, `DM_VK_CLEAR_LOADOP=1`, `DM_VK_SET_REUSE=1`) |
| **Android OS Requirements** | Android **10 – 15+** (`minSdk 29`, `targetSdk 35`) | All 12 native `.so` libraries verified **16 KB (`0x4000`) ELF page-aligned** for Android 15+ devices |

---

## Project Structure

```text
Eastward-Custom-Android/
├── LICENSE                         # GNU General Public License v3+
├── LICENSE-MIT                     # MIT license notice for upstream d2r-mobile / nxnative portions
├── NOTICE                          # Attribution, contributors, and legal compliance notice
├── README.md                       # English documentation (this file)
├── README.id.md                    # Bahasa Indonesia documentation
├── VERSION                         # Current release string (eastward-custom-1.0)
├── build_apk.py                    # Portable top-level Android build entry point (SDK/NDK/JDK via env)
│
├── dm/
│   ├── host/                       # Core Horizon OS HLE & ARM64 runtime
│   │   ├── main.c, entry.S         # Host bootstrap & ARM64 guest context entry
│   │   ├── loader.c                # Switch NSO/NRO relocation & binary fixup loader
│   │   ├── hle.c, hle_svc.c        # Kernel SVC emulation (threads, memory, IPC, sync)
│   │   ├── hle_fs.c, save_sync.c   # Virtual RomFS & SaveFS persistence
│   │   ├── hle_audio.c, hle_audren.c # Audio renderer & PCM mixer HLE
│   │   ├── hle_hid.c, hle_touch.c  # Controller, Pro Controller, and touchscreen input
│   │   ├── hle_movie*.c, hle_codec*.c # Cutscene/video codec HLE
│   │   ├── hle_nvdrv.c, hle_glslc.c # nvdrv & shader service HLE
│   │   └── hle_os.c, hle_time.c, hle_account.c, hle_sdk16.c, crash.c ...
│   │
│   ├── gfx/                        # NVN & Display VI graphics interfaces
│   │   ├── hle_nv.c, hle_vi.c      # NVN graphics command queue & VI display layers
│   │   ├── nvn/                    # NVN state, descriptors & shader metadata helpers
│   │   ├── tex/                    # Texture format & swizzle conversion (ASTC/BCn)
│   │   └── tools/                  # Offline graphics inspection helpers
│   │
│   ├── android/                    # Android platform integration & Vulkan backend
│   │   ├── CMakeLists.txt          # CMake build for libdmhost_010071B00F63A000.so & native libs
│   │   ├── front_android.c         # Android Activity/Surface lifecycle & JNI bridge
│   │   ├── vk_driver.c, vk_start.c # Adreno Turnip + System Vulkan loader & fallback logic
│   │   ├── gfx_vk.c, gfx_vk.h      # Core Vulkan renderer implementation
│   │   ├── gfx_vk_*.inc            # Modular render passes, pools, BC decode, eviction, shadow/vsync
│   │   ├── coreaudio_aaudio.c      # Android AAudio backend
│   │   ├── av1_android.c, media_android.c, haptics_android.c, ue_dynres_android.c
│   │   ├── shaders/                # Auxiliary Vulkan GLSL shaders (compute/blit/upscale)
│   │   ├── compat/, pyshim/        # Embedded Python runtime shims for on-device setup
│   │   │
│   │   ├── app/                    # Android APK Java/XML source & assets
│   │   │   ├── AndroidManifest.xml # Package com.eastward.custom (minSdk 29, targetSdk 35)
│   │   │   ├── eastward_icons/     # 9 custom Eastward RPG vector SVG icons
│   │   │   ├── assets/icons/       # Generated crisp PNG overlays & emblem (via gen_assets.py)
│   │   │   ├── res/                # Launcher icons (mdpi..xxxhdpi)
│   │   │   └── java/com/eastward/custom/
│   │   │       ├── SetupActivity.java   # Modern dark-glass Setup Hub & file importer
│   │   │       ├── GameActivity.java    # Fullscreen Vulkan surface host (:game process)
│   │   │       ├── Gamepad.java         # Touch controller, SVG badges, 2D/3D presets & 8s fade
│   │   │       ├── GameMenu.java        # Slide-in 4-tab dark-glass pause drawer
│   │   │       ├── GameSettings.java    # Persistent env.txt settings & directory layout
│   │   │       ├── DriverPackages.java  # Turnip / custom Vulkan .zip driver manager
│   │   │       └── SaveManager.java     # Save data .zip backup & restore helper
│   │   │
│   │   └── tools/                  # On-device & build-time Python scripts
│   │       ├── setup_game.py       # Unpacks user .nsp + prod.keys, applies fixups & builds shaders
│   │       ├── spv_android.py      # Translates user shaders to Android-safe Vulkan SPIR-V
│   │       ├── spv_build.py, spv_mesh.py, spv_meshemu.py
│   │       ├── gen_assets.py       # Renders eastward_icons/*.svg -> assets/icons/*.png & mipmaps
│   │       └── build_apk.py, host_setup.py, device_lock.py
│   │
│   ├── games/
│   │   └── 010071B00F63A000/       # Eastward title profile (NO proprietary game data!)
│   │       ├── game.tsv            # Title runtime flags & compatibility profile
│   │       ├── dmsh_chain.h        # Shader pipeline chain fingerprint (1a0d0a59a263)
│   │       ├── pso_manifest.tsv, pso_pairs.tsv, shader_facts.tsv, shader_map.tsv, ...
│   │       └── glslc_capture/      # (Gitignored — populated locally during user setup only)
│   │
│   ├── third_party/                # Open-source dependencies (bcdec, libadrenotools, libvpx, stb_vorbis, vulkan-headers)
│   └── tools/                      # NSO/RomFS inspection, patching (patch_nso.py) & shader tools
│
└── prebuilt/
    └── assets/
        └── font/
            └── nintendo_ext.ttf    # Clean open-source Switch button/glyph stand-in font
```

---

## On-Device Setup & Controls Guide

### First-Time Setup on Android
1. Install **`EastwardCustom.apk`** (`com.eastward.custom`) on your Android 10–15+ (`arm64-v8a`) device.
2. Open **Eastward Custom** to launch the dark-glass **Setup Hub**.
3. Tap **Select `.nsp`** and choose your legally dumped Eastward `.nsp` file.
4. Tap **Select `prod.keys`** and choose your Switch console keys.
5. *(Optional — Snapdragon Adreno only)* Tap **Driver `.zip`** to import a Mesa Turnip Vulkan driver package.
6. Tap **Run Setup** and wait for extraction, binary fixups, and SPIR-V shader translation to complete.
7. Once ready, tap **Launch Eastward**!

### Default Eastward RPG Touch Controls

All buttons display crisp custom SVG illustrations with their corresponding Switch keycap corner badge, and automatically dim after **8 seconds** without screen touches:

| Switch Button | SVG Illustration | Eastward Action |
| :---: | :--- | :--- |
| **Y** | Frying Pan (`3_attack_pan.svg`) | Pan Attack / Primary Action |
| **A** | Hand (`0_hand_interact.svg`) | Interact / Confirm / Talk |
| **X** | Sparkle Star (`2_ability_star.svg`) | Sam's Psychic Ability |
| **B** | Duo Silhouette (`1_group_chars.svg`) | Group / Regroup / Cancel |
| **R** | Swapping Blades (`5_swap_weapon.svg`) | Swap Weapon / Bomb |
| **ZR** | Dual Portraits (`7_swap_char.svg`) | Swap Character (John ↔ Sam) |
| **L** | Backpack (`4_bag_inventory.svg`) | Bag / Inventory |
| **+** | Pause Bars (`8_menu_pause.svg`) | In-Game Menu / Pause |
| **−** | Map Pin (`9_map_pin.svg`) | World Map |

---

## Building From Source

### Prerequisites
- Python **3.10+** (with `Pillow` / `cairosvg` optional if regenerating icons via `gen_assets.py`)
- **Android SDK** (`platforms/android-35`, `build-tools/36.0.0`)
- **Android NDK** (`28.2.13676358`)
- **JDK 11+**
- Prebuilt ARM64 Python & runtime dependencies placed under `prebuilt/arm64-v8a/` & `prebuilt/assets/python.zip` (excluded from Git to keep the repo lightweight)

Set environment variables (or configure paths automatically via `../dpc_home/`):
```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-ndk/28.2.13676358
export JAVA_HOME=/path/to/jdk-17
```

### Build Commands
```bash
# 1. Regenerate PNG icon assets and mipmaps from SVG sources
python dm/android/tools/gen_assets.py

# 2. Compile native ARM64 libraries, package resources, DEX, and sign APK
python build_apk.py
```

The final APK is written to `out/EastwardCustom.apk` (`com.eastward.custom`), ready for `adb install -r out/EastwardCustom.apk`.

---

## Contributors & Credits

This project would not exist without both dedicated maintainers and upstream open-source pioneers:

| Contributor / Project | Role in Eastward Custom (v3) |
| :--- | :--- |
| **[Itsneslab](https://github.com/Itsneslab)** | Project Maintainer, Android v3 Product Lead, UI/UX direction, on-device testing, and repository stewardship |
| **Google Antigravity (AI Engineering Co-pilot)** | Full v3 dark-glass Setup Hub & 4-tab `GameMenu`, Eastward vector SVG badge layout & 8s idle dimming, `2D`/`3D` preset fixes, 16 KB ELF alignment hardening, and bilingual documentation |
| **[jhaizhou](https://github.com/jhaizhou)** | Creator of upstream `d2r-mobile` / `nxnative` direct ARM64 runtime & Horizon OS HLE foundations |
| **Galar Native Contributors** | Early Android runtime & Vulkan driver loader engineering references |
| **Third-Party Libraries (`dm/third_party/`)** | `bcdec`, `libadrenotools`, `libvpx`, `stb_vorbis`, and `Vulkan-Headers` |

---

## License & Legal Notices

- **Primary License:** GNU General Public License v3.0 or later — see [LICENSE](LICENSE).
- **Upstream MIT Portions:** Portions derived from `d2r-mobile` / `nxnative` and early `Galar-Native` remain additionally available under the MIT License — see [LICENSE-MIT](LICENSE-MIT).
- **Full Copyright & Trademark Disclaimers:** See [NOTICE](NOTICE).
