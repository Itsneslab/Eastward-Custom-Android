# Eastward Custom Android (v3)

[![Language: English](https://img.shields.io/badge/lang-English-blue.svg)](README.md)
[![Language: Indonesia](https://img.shields.io/badge/lang-Bahasa%20Indonesia-red.svg)](README.id.md)
[![Rilis Terbaru](https://img.shields.io/badge/Download-APK%20(Releases)-gold.svg)](https://github.com/Itsneslab/Eastward-Custom-Android/releases/latest)
[![Lisensi: GPL v3](https://img.shields.io/badge/License-GPLv3+-green.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/Android-10%20hingga%2015%2B%20(API%2029--35)-brightgreen.svg)](dm/android/app/AndroidManifest.xml)
[![Arsitektur](https://img.shields.io/badge/ABI-arm64--v8a%20(16KB%20aligned)-orange.svg)](dm/android/CMakeLists.txt)

**Runtime kompatibilitas native ARM64 mandiri, layer translasi NVN-ke-Vulkan, dan launcher Android dark-glass modern yang dibangun khusus untuk _Eastward_ (`010071B00F63A000`).**

> [!IMPORTANT]
> **Kepatuhan Hukum & Hak Cipta Ketat:**
> Repositori ini **HANYA berisi source code, resource UI, tooling translasi shader, dan aset ikon vektor SVG original**.
> - **Tidak ada file game Nintendo Switch berhak cipta** (`.nsp`, `.xci`, `.nca`), kunci dekripsi (`prod.keys`, `title.keys`), dump `RomFS`/`ExeFS`, shader game hasil capture (`.glslc`), maupun font sistem berpemilik (`standard.ttf`) yang disertakan atau didistribusikan.
> - Pengguna wajib menyediakan file backup yang di-dump dari **salinan game Eastward (`010071B00F63A000`) dan konsol milik sendiri secara legal**.
> - _Eastward_ adalah merek dagang Pixpil / Chucklefish. _Nintendo Switch_ adalah merek dagang Nintendo Co., Ltd. Proyek ini adalah runtime preservasi open-source independen dan tidak berafiliasi dengan maupun didukung oleh Pixpil, Chucklefish, atau Nintendo.

---

## Daftar Isi

- [Mengapa v3 Dibuat (Perbandingan Arsitektur)](#mengapa-v3-dibuat-perbandingan-arsitektur)
- [Cara Kerja vs Emulator vs NSXRecomp vs DroidPlay](#cara-kerja-vs-emulator-vs-nsxrecomp-vs-droidplay)
- [Fitur Utama (v3 — `com.eastward.custom`)](#fitur-utama-v3--comeastwardcustom)
- [Kompatibilitas Hardware & GPU (Snapdragon & Dimensity)](#kompatibilitas-hardware--gpu-snapdragon--dimensity)
- [Struktur Project](#struktur-project)
- [Panduan Setup di HP & Kontrol Layar](#panduan-setup-di-hp--kontrol-layar)
- [Cara Build Dari Source Code](#cara-build-dari-source-code)
- [Catatan Sejarah & Konteks Lengkap Proyek](PROJECT_NOTES.md)
- [Kontributor & Kredit](#kontributor--kredit)
- [Lisensi & Pemberitahuan Hukum](#lisensi--pemberitahuan-hukum)

---

## Mengapa v3 Dibuat (Perbandingan Arsitektur)

Eksperimen komunitas sebelumnya membungkus _Eastward_ di dalam kerangka proyek generik atau bekas port game Pokémon (v1/v2), sehingga menyisakan workaround game lain, preset kontrol yang tidak cocok, dialog menu bawaan jadul, serta konfigurasi GPU lintas-chipset yang rentan crash.

**Eastward Custom (v3)** dipisahkan total menjadi aplikasi khusus dengan package `com.eastward.custom` yang fokus 100% pada Title ID `010071B00F63A000`:
- Tanpa hack game lain atau sisa preset tombol dari judul berbeda.
- Semua 12 library native `arm64-v8a` telah diverifikasi memiliki **16 KB ELF page alignment (`0x4000`)** untuk Android 15+.
- Ketahanan Vulkan lintas GPU yang mendukung **Qualcomm Adreno (Mesa Turnip + fallback otomatis ke driver sistem)** dan **ARM Mali / MediaTek Dimensity / Exynos**.
- Launcher dark-glass modern (`SetupActivity`) + menu geser kanan (`GameMenu`) + ikon vektor RPG khusus Eastward + auto-redup tombol otomatis setelah 8 detik layar idle.

---

## Cara Kerja vs Emulator vs NSXRecomp vs DroidPlay

Banyak port komunitas lain (seperti proyek Pokémon Switch di Android) menyebut **DroidPlay** atau **NSXRecomp**. Berikut posisi sebenarnya **Eastward Custom (v3)**:

| Pendekatan / Proyek | Model Eksekusi CPU/Binary | Pipeline Grafis | Dipakai di Eastward Custom? |
| :--- | :--- | :--- | :--- |
| **Emulator Tradisional** (Yuzu, Ryujinx, dll.) | Virtualisasi JIT / NCE penuh terhadap seluruh OS & hardware Switch | Dekompilasi shader saat runtime + emulasi GPU berat | **Tidak** |
| **NSXRecomp / nxrecomp** | **Rekompilasi Statis**: mengangkat kode mesin AArch64 Switch menjadi source code **C++**, lalu dikompilasi ulang dengan Clang/NDK | Backend translasi grafis khusus | **Tidak** |
| **DroidPlay Core (`dpc` / `dm` — `droidplay-core-0.8.10`)** | **Runtime Biner ARM64 Langsung (`dm`)**: melakukan patch relokasi NSO langsung di HP (`patch_nso.py` / `loader.c`) dan mengeksekusi instruksi ARM64 secara langsung di atas layanan HLE C Horizon OS (`dm/host/hle_*.c`) | Renderer langsung **NVN-ke-Vulkan** (`gfx_vk.c`, `gfx_vk_*.inc`) + translator **SPIR-V** (`spv_build.py` / `spv_android.py` / `libdmsh_xlate.so`) | **Ya — menjadi mesin inti v3!** |

### Bagaimana v3 memadukan DroidPlay Core (`dm`) dengan Shell Kustom Eastward?
1. **Kenapa pakai DroidPlay Core (`dm`) alih-alih NSXRecomp?** HP Android sudah berarsitektur `arm64-v8a`. `DroidPlay Core` (`dm`) menjalankan instruksi AArch64 game langsung di user-space Android setelah relokasi alamat dan fixup ABI — bebas dari beban virtualisasi emulator **sekaligus** tidak perlu membangkitkan jutaan baris C++ hasil lifting statis seperti `NSXRecomp`.
2. **Kenapa membangun shell v3 baru alih-alih melanjutkan fork v2?** Eksperimen v2 sebelumnya memakai cangkang komunitas turunan Pokémon (`Galar-Native`), sehingga membawa beban tambahan, kedip kontroler, dan hack game lain. **Eastward Custom (v3)** mempertahankan **mesin & backend Vulkan `DroidPlay` (`dm`) yang ringan & kencang**, lalu mengganti UI Android, kontrol SVG, serta setup `.nsp` + `prod.keys` khusus untuk Title ID `010071B00F63A000` (`dm/games/010071B00F63A000/`, `EXPECTED_CHAIN = 1a0d0a59a263`).

---

## Fitur Utama (v3 — `com.eastward.custom`)

- **Setup Hub `.nsp` + `prod.keys` Langsung di HP (`SetupActivity`):**
  - Antarmuka dark-glass Obsidian & Emas Eastward (`#0D0F13` / `#1C1F26` / `#F5C428`).
  - Pilih file `.nsp` dan `prod.keys` langsung dari penyimpanan HP — `libnx_extract.so` beserta skrip Python (`setup_game.py` & `spv_android.py`) akan mengekstrak, mem-patch biner (`main.fixups.tsv`), dan mengompilasi shader SPIR-V (`spv/`) langsung di HP.
  - Compiler SPIR-V single-process yang bisa dilanjutkan jika terhenti, lengkap dengan progress bar dan log instalasi real-time.
  - Dilengkapi **Pengelola Driver Vulkan `.zip`** (`DriverPackages.java`) serta **Backup & Restore Save Data `.zip`** (`SaveManager.java`).
  - Tautan langsung ke **GitHub repositori** tersedia di layar awal aplikasi.
- **Kontrol Layar Vektor SVG Khusus Eastward (`Gamepad.java`):**
  - 9 ikon vektor RPG original (`dm/android/app/eastward_icons/`) lengkap dengan **lencana huruf tombol Switch di sudut ikon** (`Y`, `A`, `X`, `B`, `R`, `ZR`, `L`, `+`, `-`).
  - **Analog Kiri & D-Pad** bertema serasi Obsidian & Emas (`#FAD44A` / `#F6C634`).
  - Preset tata letak **`2D` dan `3D` bebas tumpukan** + mode **`Edit Layout`** untuk geser dan ubah ukuran tombol sesuka hati.
  - **Auto-redup 8 detik (`IDLE_DIM_AFTER_MS = 8000`):** seluruh kontrol otomatis meredup halus setelah 8 detik layar tidak disentuh agar tampilan pixel-art bersih, lalu langsung terang kembali seketika layar disentuh.
- **Menu Pause Modern Dalam Game (`GameMenu.java`):**
  - Laci geser kanan dengan 4 tab rapi: **Quick**, **Controls**, **Graphics**, dan **System**.
  - Pengaturan cepat FPS 30/60, FSR, Shader Skip / Frame Cache, Dynamic Resolution (`ue_dynres_android.c`), serta getaran haptik (`haptics_android.c`).
- **Audio & Video Latensi Rendah:**
  - Backend audio native Android **AAudio** (`coreaudio_aaudio.c`, `hle_audren.c`).
  - Pemutaran cutscene terintegrasi dengan dekoder AV1/VPX (`av1_android.c`, `hle_movie.c`, `libvpx`).

---

## Kompatibilitas Hardware & GPU (Snapdragon & Dimensity)

| Chipset / Vendor GPU | Strategi Driver Default | Perlindungan & Stabilitas Bawaan |
| :--- | :--- | :--- |
| **Qualcomm Snapdragon (Adreno 6xx / 7xx / 8xx)** | Driver Vulkan kustom **Mesa Turnip** via `libadrenotools` (mendukung impor `.zip`) | **Auto-fallback** ke driver **System Vulkan** bawaan Android jika Turnip gagal inisialisasi atau error swapchain |
| **MediaTek Dimensity / Samsung Exynos (ARM Mali / Xclipse)** | Driver **System Vulkan** bawaan Android | Menggunakan setelan aman khusus Mali (`DM_VK_MALI_FP16=1`, `DM_VK_CLEAR_LOADOP=1`, `DM_VK_SET_REUSE=1`) |
| **Versi Android** | Android **10 – 15+** (`minSdk 29`, `targetSdk 35`) | Seluruh 12 file `.so` telah terverifikasi **16 KB (`0x4000`) ELF page-aligned** |

---

## Struktur Project

```text
Eastward-Custom-Android/
├── LICENSE                         # Lisensi GNU General Public License v3+
├── LICENSE-MIT                     # Pemberitahuan lisensi MIT untuk komponen hulu d2r-mobile / nxnative
├── NOTICE                          # Atribusi kontributor, pihak ketiga, dan kepatuhan hukum
├── README.md                       # Dokumentasi Bahasa Inggris
├── README.id.md                    # Dokumentasi Bahasa Indonesia (file ini)
├── VERSION                         # String versi rilis (eastward-custom-1.0)
├── build_apk.py                    # Entry point portabel build Android (SDK/NDK/JDK via env)
│
├── dm/
│   ├── host/                       # Runtime ARM64 & Horizon OS HLE inti
│   │   ├── main.c, entry.S         # Bootstrap host & entry point eksekusi ARM64 guest
│   │   ├── loader.c                # Relokasi biner NSO/NRO & loader patch fixup
│   │   ├── hle.c, hle_svc.c        # Emulasi kernel SVC (thread, memori, IPC, sinkronisasi)
│   │   ├── hle_fs.c, save_sync.c   # Virtualisasi RomFS & penyimpanan SaveFS
│   │   ├── hle_audio.c, hle_audren.c # Renderer audio & mixer PCM HLE
│   │   ├── hle_hid.c, hle_touch.c  # Input gamepad, Pro Controller, dan layar sentuh
│   │   ├── hle_movie*.c, hle_codec*.c # HLE pemutar video/cutscene
│   │   ├── hle_nvdrv.c, hle_glslc.c # HLE nvdrv & layanan shader
│   │   └── hle_os.c, hle_time.c, hle_account.c, hle_sdk16.c, crash.c ...
│   │
│   ├── gfx/                        # Antarmuka grafis NVN & Display VI
│   │   ├── hle_nv.c, hle_vi.c      # Antrean perintah grafis NVN & layer tampilan VI
│   │   ├── nvn/                    # State NVN, descriptor & metadata shader
│   │   ├── tex/                    # Konversi format & swizzle tekstur (ASTC/BCn)
│   │   └── tools/                  # Utilitas inspeksi grafis offline
│   │
│   ├── android/                    # Integrasi platform Android & backend Vulkan
│   │   ├── CMakeLists.txt          # Skrip CMake untuk libdmhost_010071B00F63A000.so & library native
│   │   ├── front_android.c         # Siklus hidup Activity/Surface Android & jembatan JNI
│   │   ├── vk_driver.c, vk_start.c # Loader Adreno Turnip + fallback System Vulkan
│   │   ├── gfx_vk.c, gfx_vk.h      # Implementasi inti renderer Vulkan
│   │   ├── gfx_vk_*.inc            # Modul render pass, memory pool, dekode BC, eviksi, shadow/vsync
│   │   ├── coreaudio_aaudio.c      # Backend audio Android AAudio
│   │   ├── av1_android.c, media_android.c, haptics_android.c, ue_dynres_android.c
│   │   ├── shaders/                # Shader GLSL Vulkan bantu (compute/blit/upscale)
│   │   ├── compat/, pyshim/        # Shim runtime Python tertanam untuk proses Setup di HP
│   │   │
│   │   ├── app/                    # Source code Java/XML & aset APK Android
│   │   │   ├── AndroidManifest.xml # Package com.eastward.custom (minSdk 29, targetSdk 35)
│   │   │   ├── eastward_icons/     # 9 ikon vektor SVG RPG original untuk Eastward
│   │   │   ├── assets/icons/       # Hasil render PNG tajam & emblem (via gen_assets.py)
│   │   │   ├── res/                # Ikon launcher aplikasi (mdpi..xxxhdpi)
│   │   │   └── java/com/eastward/custom/
│   │   │       ├── SetupActivity.java   # Setup Hub dark-glass & pemilih file .nsp/.keys
│   │   │       ├── GameActivity.java    # Host tampilan Vulkan fullscreen (proses :game)
│   │   │       ├── Gamepad.java         # Kontrol sentuh, lencana SVG, preset 2D/3D & redup 8 detik
│   │   │       ├── GameMenu.java        # Laci menu pause 4-tab modern
│   │   │       ├── GameSettings.java    # Pengaturan persisten env.txt & path folder
│   │   │       ├── DriverPackages.java  # Pengelola driver Turnip / Vulkan .zip
│   │   │       └── SaveManager.java     # Fitur Backup & Restore save data .zip
│   │   │
│   │   └── tools/                  # Skrip Python untuk Setup di HP & Build APK
│   │       ├── setup_game.py       # Ekstrak .nsp + prod.keys pengguna, terapkan patch & shader
│   │       ├── spv_android.py      # Translasi shader game menjadi SPIR-V ramah GPU Android
│   │       ├── spv_build.py, spv_mesh.py, spv_meshemu.py
│   │       ├── gen_assets.py       # Merender eastward_icons/*.svg -> assets/icons/*.png & mipmap
│   │       └── build_apk.py, host_setup.py, device_lock.py
│   │
│   ├── games/
│   │   └── 010071B00F63A000/       # Profil khusus Eastward (TANPA data berhak cipta!)
│   │       ├── game.tsv            # Profil flag runtime judul Eastward
│   │       ├── dmsh_chain.h        # Fingerprint chain shader (1a0d0a59a263)
│   │       ├── pso_manifest.tsv, pso_pairs.tsv, shader_facts.tsv, shader_map.tsv, ...
│   │       └── glslc_capture/      # (Diabaikan Git — hanya diisi lokal di HP saat setup)
│   │
│   ├── third_party/                # Pustaka open-source (bcdec, libadrenotools, libvpx, stb_vorbis, vulkan-headers)
│   └── tools/                      # Alat analisis NSO/RomFS, patching (patch_nso.py) & shader
│
└── prebuilt/
    └── assets/
        └── font/
            └── nintendo_ext.ttf    # Font pengganti open-source bersih untuk glif tombol Switch
```

---

## Panduan Setup di HP & Kontrol Layar

### Langkah Setup Pertama Kali di Android
1. Pasang **`EastwardCustom.apk`** (`com.eastward.custom`) di HP Android 10–15+ (`arm64-v8a`).
2. Buka aplikasi **Eastward Custom** untuk masuk ke layar **Setup Hub**.
3. Tekan **Select `.nsp`** lalu pilih file backup `.nsp` Eastward milikmu sendiri.
4. Tekan **Select `prod.keys`** lalu pilih file `prod.keys` dari konsol Switch milikmu.
5. *(Opsional — khusus Snapdragon Adreno)* Tekan **Driver `.zip`** untuk mengimpor driver Mesa Turnip.
6. Tekan **Run Setup** dan tunggu proses ekstraksi, patching biner, serta kompilasi shader SPIR-V selesai.
7. Setelah status siap, tekan **Launch Eastward** untuk mulai bermain!

### Pemetaan Ikon Kontrol SVG Eastward

Setiap tombol menampilkan ilustrasi vektor SVG yang jelas beserta **lencana huruf Switch di pojok ikon**, dan meredup otomatis setelah **8 detik** tanpa sentuhan:

| Tombol Switch | Ilustrasi SVG | Fungsi di Eastward |
| :---: | :--- | :--- |
| **Y** | Wajan (`3_attack_pan.svg`) | Serangan Wajan / Aksi Utama |
| **A** | Tangan (`0_hand_interact.svg`) | Interaksi / Bicara / Konfirmasi |
| **X** | Kilau Bintang (`2_ability_star.svg`) | Kekuatan Psikis Sam |
| **B** | Siluet Duo (`1_group_chars.svg`) | Kumpul Karakter / Batal |
| **R** | Senjata Tukar (`5_swap_weapon.svg`) | Ganti Senjata / Bom |
| **ZR** | Ganti Karakter (`7_swap_char.svg`) | Tukar Karakter (John ↔ Sam) |
| **L** | Ransel (`4_bag_inventory.svg`) | Tas / Inventaris |
| **+** | Garis Menu (`8_menu_pause.svg`) | Menu Pause / Pengaturan |
| **−** | Pin Peta (`9_map_pin.svg`) | Peta Dunia |

---

## Cara Build Dari Source Code

### Kebutuhan Lingkungan
- Python **3.10+** (opsional `Pillow` / `cairosvg` bila ingin merender ulang aset PNG dari SVG)
- **Android SDK** (`platforms/android-35`, `build-tools/36.0.0`)
- **Android NDK** (`28.2.13676358`)
- **JDK 11+**
- Dependensi runtime Python ARM64 prabangun pada `prebuilt/arm64-v8a/` & `prebuilt/assets/python.zip` (dikecualikan dari Git agar ukuran repositori ringan)

Atur variabel lingkungan (atau letakkan toolchain di `../dpc_home/`):
```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-ndk/28.2.13676358
export JAVA_HOME=/path/to/jdk-17
```

### Perintah Build
```bash
# 1. Generate ulang ikon PNG & mipmap dari file SVG
python dm/android/tools/gen_assets.py

# 2. Kompilasi library native ARM64, resource APK, DEX, dan tanda tangan APK
python build_apk.py
```

Hasil APK siap instal akan berada di `out/EastwardCustom.apk` (`com.eastward.custom`).

---

## Kontributor & Kredit

Proyek ini terwujud berkat kolaborasi pengembang, AI engineering co-pilot, serta karya proyek open-source hulu:

| Kontributor / Proyek | Peran dalam Eastward Custom (v3) |
| :--- | :--- |
| **[Itsneslab](https://github.com/Itsneslab)** | Pemilik & Maintainer Proyek, Lead Produk Android v3, desain arah UI/UX, pengujian perangkat keras, dan pengelolaan repositori |
| **Google Antigravity (AI Engineering Co-pilot)** | Implementasi UI dark-glass Setup Hub & menu pause 4 tab (`GameMenu`), kontrol sentuh SVG ber-lencana tombol & auto-redup 8 detik, perbaikan preset `2D`/`3D`, validasi 16 KB ELF alignment, serta dokumentasi bilingual |
| **[jhaizhou](https://github.com/jhaizhou)** | Kreator hulu `d2r-mobile` / `nxnative` sebagai fondasi runtime ARM64 langsung & layanan HLE Horizon OS |
| **Kontributor Galar Native** | Referensi awal implementasi runtime Android & pemuatan driver Vulkan |
| **Pustaka Pihak Ketiga (`dm/third_party/`)** | `bcdec`, `libadrenotools`, `libvpx`, `stb_vorbis`, dan `Vulkan-Headers` |

---

## Lisensi & Pemberitahuan Hukum

- **Lisensi Utama:** GNU General Public License v3.0 atau lebih baru — lihat [LICENSE](LICENSE).
- **Komponen Hulu MIT:** Bagian kode yang berasal dari `d2r-mobile` / `nxnative` dan `Galar-Native` tetap tersedia juga di bawah Lisensi MIT — lihat [LICENSE-MIT](LICENSE-MIT).
- **Detail Hak Cipta & Merek Dagang Lengkap:** Lihat [NOTICE](NOTICE).
