# Project History & Complete Context Notes (`Eastward Custom v3`)

> **Purpose / Tujuan:** Dokumen ini adalah catatan pengingat lengkap & ringkasan konteks teknis dari awal pembuatan proyek hingga rilis **Eastward Custom v3 (`v1.1.0`)**, agar seluruh keputusan arsitektur, penyebab masalah versi lama, solusi yang berhasil, serta rencana lanjutan tetap terdokumentasi rapi dan tidak pernah hilang atau terlupakan.

---

## 1. Gambaran Singkat & Target Utama

- **Game Target:** _Eastward_ (Nintendo Switch, Title ID `010071B00F63A000`, SDK 12, modules `main subsdk1 sdk`)
- **Package Android (v3):** `com.eastward.custom` (`minSdkVersion 29` / Android 10, `targetSdkVersion 35` / Android 15+, `arm64-v8a`)
- **Prinsip Utama:**
  1. **Native ARM64, bukan emulator konvensional** (mengeksekusi biner ARM64 game secara langsung di user-space Android lewat engine `DroidPlay Core` / `dm`).
  2. **Ringan & Mandiri di HP:** User cukup menginstal `EastwardCustom.apk` ringan, lalu memilih file `.nsp` dan `prod.keys` miliknya sendiri langsung di HP tanpa perlu PC.
  3. **Lintas Chipset:** Harus berjalan lancar pada perangkat uji utama (**MediaTek Helio G80 / `mt6768`, GPU `Mali-G52 MC2`, Android 10 / Infinix X692**) maupun **MediaTek Dimensity (Mali)** dan **Qualcomm Snapdragon (Adreno + Mesa Turnip)**.
  4. **Kepatuhan Hukum 100%:** Repositori publik hanya menyimpan source code, tooling translasi shader, aset ikon SVG original, dan metadata publik — nol ROM/NSP, nol `prod.keys`, nol `.glslc`, dan nol font berpemilik (`standard.ttf`).

---

## 2. Kronologi Evolusi Proyek (Dari Awal hingga v3)

### Fase 0 — Eksplorasi Awal (PC Game → Switch Dump Legal)
- Proyek dimulai dari keinginan menjalankan game PC indie (*Dimraeth*, *Octopath Traveler II*, dll.) secara native/recomp di HP MediaTek Helio G80 tanpa emulator berat.
- Setelah analisis kompatibilitas x86/Windows vs ARM64, diputuskan menggunakan **versi Nintendo Switch** karena instruksinya sudah **AArch64 (ARM64)** sehingga dapat dijalankan langsung di Android ARM64 tanpa emulasi CPU x86-to-ARM64.
- Game uji yang dipilih secara penuh: **_Eastward_ (`010071B00F63A000`)**, dengan aturan tegas bahwa pengguna harus menyediakan dump `.nsp` & `prod.keys` miliknya sendiri secara legal.

### Fase 1 — Generasi v1 (`dpc` / `droidplay-core-0.8.10`)
- Menggunakan mesin inti **DroidPlay Core (`dm` / turunan `nxnative`)** dengan renderer langsung **NVN-ke-Vulkan** (`gfx_vk.c`, `vk_driver.c`, `vk_start.c`) dan HLE Horizon OS berbahasa C (`dm/host/hle_*.c`).
- **Hasil Positif:** Berhasil booting penuh hingga gameplay di **Helio G80 (`Mali-G52 MC2`)** dengan performa mulus (~26–40 FPS tergantung adegan), jauh lebih ringan dibanding emulator penuh.
- **Kekurangan v1:** Pipeline build & persiapan aset shader/SPV masih bergantung pada skrip desktop/PC (`out_eastward/Eastward.apk`), dan tampilan launcher/menu bawaan masih sangat minimal.

### Fase 2 — Generasi v2 (`eastward-native-android` / basis fork `Galar-Native-Android`)
- Dibuat untuk menjawab kebutuhan **"download APK → masukkan `.nsp` + `prod.keys` langsung di HP"** + menambahkan kontrol sentuh SVG yang keren.
- Memanfaatkan kerangka aplikasi komunitas dari proyek Pokémon (`Galar-Native-Android`) yang dipasangi modul setup Python/C dan UI Android baru.
- **Kenapa v2 Bermasalah & Akhirnya Ditinggalkan:**
  1. **Sisa Kode & Preset Pokémon:** Mengandung banyak workaround, logika, dan preset yang tidak dibuat khusus untuk arsitektur *Eastward*.
  2. **Performa Drop & Controller Bug:** Di perangkat uji Helio G80 yang sama, gameplay terasa berat/lag dibanding v1, kontroler sentuh sempat berkedip-kedip dan terasa delay/lambat, serta memakan waktu lama saat stuck di optimasi lintas fork.
  3. **Identitas Tercampur:** Masih membawa jejak penamaan/aset proyek Pokémon lama di repo & resource.

### Fase 3 — Generasi v3 (`eastward-custom` / `com.eastward.custom`) — Solusi Final Terpadu
- Diputuskan membangun proyek bersih khusus *Eastward*:
  - **Mengambil mesin terbaik v1:** Inti **DroidPlay Core (`droidplay-core-0.8.10` / `dm`)** yang ringan, kencang, dan stabil pada GPU Mali maupun Adreno.
  - **Mengambil fitur unggulan v2 secara bersih:** Setup `.nsp` + `prod.keys` langsung di HP (`setup_game.py`, `spv_build.py`, `libnx_extract.so`, `libdmsh_xlate.so`) + kontrol vektor SVG yang dirapikan ulang.
  - **Membuang seluruh sisa Pokémon** dan memisahkan repositori publik bersih ke **`Itsneslab/Eastward-Custom-Android`**.

---

## 3. Ringkasan Masalah Teknis & Solusi yang Sudah Diterapkan

| Masalah yang Ditemui | Akar Masalah | Solusi di Eastward Custom (v3) |
| :--- | :--- | :--- |
| **Setup di HP butuh penyimpanan & berisiko crash saat kompilasi shader** | Unpack `RomFS`, fixup kode NSO, dan translasi ribuan shader memakan RAM/IO besar | `setup_game.py` memproses single-process SPIR-V compiler yang *resumable*, progress real-time, menyimpan `RomFS/`, `code/main.fixups.tsv`, `spv/`, dan penanda `ready` di `<ext>/games/010071B00F63A000/`. |
| **Perbedaan GPU Snapdragon vs Dimensity/Helio** | Driver Turnip khusus Adreno akan gagal di GPU Mali; sebaliknya GPU Mali butuh flag khusus Vulkan agar tidak glitch/lambat | **`vk_driver.c` + `setup_game.py`:** Adreno mendukung Turnip `.zip` via `libadrenotools` dengan **auto-fallback** ke System Vulkan jika gagal; Mali otomatis memakai System Vulkan + `DM_VK_MALI_FP16=1`, `DM_VK_CLEAR_LOADOP=1`, `DM_VK_SET_REUSE=1`, `DM_SPV_PRECISE=0`. |
| **Kompatibilitas Android 15+ (16 KB Page Size)** | Library native `.so` tanpa alignment `0x4000` gagal dimuat di perangkat Android 15/16 terbaru | Seluruh **12 `.so` di `lib/arm64-v8a/`** diverifikasi **16 KB (`0x4000`) ELF page-aligned** (built dengan NDK `28.2.13676358`, `minSdk 29`, `targetSdk 35`). |
| **UI Awal & Menu Pause Jadul (v1.0.0)** | Dialog Android klasik terlihat kuno dan launcher awal belum punya identitas & tautan GitHub | **`GameMenu.java`:** Laci dark-glass geser kanan 4 tab (`Quick`, `Controls`, `Graphics`, `System`). **`SetupActivity.java`:** Launcher dark-glass Obsidian/Gold (`#0D0F13` / `#1C1F26` / `#F5C428`), switch pil kustom, serta link langsung ke **`Itsneslab/Eastward-Custom-Android`**. |
| **Kontroler SVG & Kejelasan Tombol** | User menginginkan ikon vektor bergaya RPG Eastward disertai penanda tombol Switch agar tidak bingung | **`Gamepad.java` + `dm/android/app/eastward_icons/*.svg`:** 9 ikon SVG original (`Y` Wajan, `A` Tangan, `X` Bintang, `B` Duo, `R` Tukar Senjata, `ZR` Tukar Karakter, `L` Tas, `+` Pause, `-` Map) + badge huruf Switch di pojok ikon + ikon launcher APK penuh. |
| **Bug Tumpukan Kontroler `2D`/`3D`, Warna Stick/D-Pad, & Layar Penuh Ikon** | Posisi preset `2D`/`3D` bertumpuk dengan `menu`/`swap`, `popup` menu edit tersisa saat ganti mode, warna stick/D-Pad masih abu-abu lama, dan tombol menutupi layar saat diam | **Diperbaiki di `v1.1.0`:** Memisahkan posisi `S_MINUS` & `S_PLUS`, menutup `popup` saat `Gamepad.start()`, menyelaraskan warna Stick & D-Pad ke Obsidian/Emblem Emas (`#FAD44A` / `#F6C634`), serta menambah **auto-redup 8 detik** (`IDLE_DIM_AFTER_MS = 8000`, `IDLE_MIN_MULT = 0.16f`) yang langsung cerah saat layar disentuh. |
| **Kepatuhan Hukum Repo Publik GitHub** | `standard.ttf` terdeteksi sebagai font berpemilik (Microsoft Monotype Arial), plus risiko `.glslc`/`.nsp`/`.keys` ikut ter-commit | Audit & eksklusi total via `.gitignore` (`*.nsp`, `*.xci`, `*.keys`, `*.glslc`, `standard.ttf`, `prebuilt/arm64-v8a/`, `prebuilt/assets/*.zip`, `*.apk`, `*.keystore`); menyertakan hanya `nintendo_ext.ttf` open-source, `LICENSE` (`GPL-3.0-or-later`), `LICENSE-MIT`, `NOTICE`, serta `README.md` / `README.id.md`. |

---

## 4. Hubungan `Eastward Custom` vs `DroidPlay` vs `NSXRecomp`

Catatan klarifikasi penting agar tidak rancu di masa depan:
- **Memakai `DroidPlay Core`? YA.** Dasar engine runtime di dalam repo ini berasal dari `droidplay-core-0.8.10` (`dm/` runtime, `dm/host/`, `dm/android/vk_start.c`, `setup_game.py` — terlihat juga pada header `defaults.txt`: `# Tuned defaults for Eastward Custom (DroidPlay v1 Vulkan backend)`).
- **Memakai `NSXRecomp`? TIDAK.** `NSXRecomp` mengubah biner Switch menjadi source code **C++** statis (static recompilation), sedangkan `DroidPlay` / `dm` langsung merelokasi & menjalankan biner **ARM64** Switch di Android dengan kernel/service HLE C + NVN-to-Vulkan.
- **Memakai `Galar-Native` (v2)? TIDAK LAGI.** Shell Pokémon v2 sudah dibuang sepenuhnya pada v3 demi performa, kestabilan input, dan kerapian arsitektur.

---

## 5. Status Lingkungan & Checklist Lanjutan

### Kondisi Terverifikasi (Update Terakhir: `v1.1.0`)
- **APK Build:** `out/EastwardCustom.apk` (`16.82 MiB`, `com.eastward.custom`, `versionCode=6`, `versionName=1.1.0`) terkompilasi & terpasang bersih di perangkat uji **Infinix X692 (`mt6768`, `Mali-G52 MC2`, Android 10 API 29)**.
- **Data Game Lokal di HP Uji:** Folder game di HP sudah berstatus **`ready`** (`RomFS/`, `code/`, `font/`, `spv/`, `defaults.txt`, `vk_start.txt` dengan `pipelines_failed=0`), sehingga upgrade ke `v1.1.0` langsung siap dimainkan tanpa perlu unpack ulang `.nsp`.
- **Repositori GitHub Baru:** `https://github.com/Itsneslab/Eastward-Custom-Android` (Public) berisi dokumentasi 2 bahasa (`README.md` + `README.id.md`), `NOTICE` lengkap dengan kredit kontributor, dan source code bersih legal.

### Agenda / Catatan Opsional Berikutnya
1. **Hapus Repo Lama (`Eastward-Native-Android`):** Token CLI saat ini belum memiliki scope `delete_repo`, sehingga penghapusan aman dilakukan lewat **GitHub Web → Settings → Danger Zone → Delete this repository** (atau jalankan `gh auth refresh -s delete_repo`).
2. **Uji Lintas Perangkat (Snapdragon & Dimensity):** Coba APK `v1.1.0` di HP Snapdragon (tes Turnip + fallback) dan Dimensity untuk memantau `vk_start.txt` (`pipelines_failed`, `device_lost`) serta FPS nyata di area padat game.
3. **Sinkronisasi Metadata Kecil:** Jika suatu saat merapikan `dm/games/010071B00F63A000/game.tsv`, field `app_repo` / `app_name` (baris 8–10) dapat diperbarui ke `github.com/Itsneslab/Eastward-Custom-Android` dan `Eastward Custom` (kosmetik metadata, tidak memengaruhi runtime `SetupActivity.java` yang sudah memakai URL v3).
4. **Update Performa / Fitur Lanjutan:** Karena `com.eastward.custom` kini terpisah rapi dan mendukung `install -r` (timpa APK tanpa menghapus folder `RomFS`/`spv` maupun `saves`), setiap update UI atau tuning Vulkan di masa depan dapat langsung di-install dan dites dalam hitungan detik.
