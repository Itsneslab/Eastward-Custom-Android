#!/usr/bin/env python3
"""Builds eastward-custom/out/EastwardCustom.apk from clean C/Java/Python sources."""
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

PROJ = Path(__file__).resolve().parent
ROOT = Path(os.environ.get('EASTWARD_ROOT', str(PROJ.parent)))
SDK = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(ROOT / 'dpc_home/sdk'))
NDK = Path(os.environ.get('ANDROID_NDK_HOME') or str(SDK / 'ndk/28.2.13676358'))
_EXE = '.exe' if os.name == 'nt' else ''
_BAT = '.bat' if os.name == 'nt' else ''
CMAKE = SDK / f'cmake/3.22.1/bin/cmake{_EXE}'
NINJA = SDK / f'cmake/3.22.1/bin/ninja{_EXE}'
BT = SDK / 'build-tools/36.0.0'
ANDROID_JAR = SDK / 'platforms/android-35/android.jar'
JDK = Path(os.environ.get('JAVA_HOME') or str(ROOT / 'dpc_home/localappdata/nxnative/android/tools/jdk'))

BUILD_NATIVE = PROJ / 'build-arm64'
OUT_DIR = PROJ / 'out'
WORK = PROJ / 'build-apk'


def run(cmd, env=None):
    print('+', ' '.join(str(x) for x in cmd), flush=True)
    r = subprocess.run([str(x) for x in cmd], env=env)
    if r.returncode != 0:
        sys.exit(r.returncode)


def build_native():
    if not (BUILD_NATIVE / 'build.ninja').exists():
        run([
            CMAKE, '-G', 'Ninja',
            '-S', PROJ / 'dm/android',
            '-B', BUILD_NATIVE,
            f'-DCMAKE_MAKE_PROGRAM={NINJA}',
            f'-DCMAKE_TOOLCHAIN_FILE={NDK}/build/cmake/android.toolchain.cmake',
            '-DANDROID_ABI=arm64-v8a',
            '-DANDROID_PLATFORM=android-29',
            '-DGAME=010071B00F63A000',
        ])
    run([CMAKE, '--build', BUILD_NATIVE, '--target', 'dmhost_app', 'adrenotools', 'hook_impl', 'main_hook'])


def make_dm_zip(dst_zip: Path):
    dm = PROJ / 'dm'
    fonts = PROJ / 'prebuilt/assets/font'
    with zipfile.ZipFile(dst_zip, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        # 1. Eastward profile & metadata (exclude glslc_capture/*.glslc for legal compliance)
        for f in (dm / 'games/010071B00F63A000').rglob('*'):
            if not f.is_file():
                continue
            rel = f.relative_to(dm)
            if 'glslc_capture' in rel.parts or f.suffix.lower() == '.glslc':
                continue
            z.write(f, rel.as_posix())
        z.write(dm / 'games/game.py', 'games/game.py')
        z.write(dm / 'games/profile_guess.py', 'games/profile_guess.py')
        z.write(dm / 'gfx/nvn/dmsh_chain.h', 'gfx/nvn/dmsh_chain.h')
        z.write(dm / 'gfx/nvn/dmsh_abi.h', 'gfx/nvn/dmsh_abi.h')

        # 2. Pure-Python rewrite & shader translation scripts
        for sub in ('tools', 'gfx/tools', 'android/tools', 'android/pyshim'):
            base = dm / sub
            for f in base.rglob('*'):
                if f.is_file() and '__pycache__' not in f.parts and f.suffix in ('.py', '.txt'):
                    z.write(f, f.relative_to(dm).as_posix())

        # 3. Open-source Switch button glyph stand-in font (system Roboto handles UI text)
        ext_font = fonts / 'nintendo_ext.ttf'
        if ext_font.is_file():
            z.write(ext_font, 'android/assets/nintendo_ext.ttf')


def build_dex(classes_dir: Path, dex_dir: Path):
    java_root = PROJ / 'dm/android/app/java'
    java_files = sorted(java_root.rglob('*.java'))
    classes_dir.mkdir(parents=True, exist_ok=True)
    dex_dir.mkdir(parents=True, exist_ok=True)

    javac = JDK / 'bin/javac.exe'
    run([
        javac, '--release', '11',
        '-classpath', ANDROID_JAR,
        '-d', classes_dir,
        *java_files,
    ])

    class_files = sorted(classes_dir.rglob('*.class'))
    d8 = BT / 'd8.bat'
    env = dict(os.environ, JAVA_HOME=str(JDK), PATH=f'{JDK / "bin"}{os.pathsep}{os.environ.get("PATH", "")}')
    run([
        d8,
        '--min-api', '29',
        '--lib', ANDROID_JAR,
        '--output', dex_dir,
        *class_files,
    ], env=env)


def ensure_keystore(ks: Path):
    if ks.is_file():
        return
    ks.parent.mkdir(parents=True, exist_ok=True)
    keytool = JDK / 'bin/keytool.exe'
    run([
        keytool, '-genkeypair', '-v',
        '-keystore', ks,
        '-storepass', 'android',
        '-alias', 'eastward',
        '-keypass', 'android',
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=EastwardCustom,O=EastwardCustom,C=ID',
    ])


def main():
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    build_native()

    classes_dir = WORK / 'classes'
    dex_dir = WORK / 'dex'
    build_dex(classes_dir, dex_dir)

    assets_dir = WORK / 'assets'
    assets_dir.mkdir()
    make_dm_zip(assets_dir / 'dm.zip')
    shutil.copy2(PROJ / 'prebuilt/assets/python.zip', assets_dir / 'python.zip')
    icons_src = PROJ / 'dm/android/app/assets/icons'
    if icons_src.is_dir():
        shutil.copytree(icons_src, assets_dir / 'icons')

    res_dir = PROJ / 'dm/android/app/res'
    compiled_res = WORK / 'compiled_res.zip'
    aapt2_args = [
        BT / 'aapt2.exe', 'link',
        '--manifest', PROJ / 'dm/android/app/AndroidManifest.xml',
        '-I', ANDROID_JAR,
        '--min-sdk-version', '29',
        '--target-sdk-version', '35',
    ]
    if res_dir.is_dir():
        run([BT / 'aapt2.exe', 'compile', '--dir', res_dir, '-o', compiled_res])
        aapt2_args.extend(['-R', compiled_res])
    aapt2_args.extend(['-o', WORK / 'base.apk'])

    base_apk = WORK / 'base.apk'
    run(aapt2_args)

    unaligned = WORK / 'unaligned.apk'
    native_built = [
        BUILD_NATIVE / 'libdmhost_010071B00F63A000.so',
        BUILD_NATIVE / 'libadrenotools.so',
        BUILD_NATIVE / 'libhook_impl.so',
        BUILD_NATIVE / 'libmain_hook.so',
    ]
    prebuilt_libs = sorted((PROJ / 'prebuilt/arm64-v8a').glob('*.so'))

    with zipfile.ZipFile(base_apk, 'r') as zin, zipfile.ZipFile(unaligned, 'w') as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        for dx in sorted(dex_dir.glob('*.dex')):
            zout.write(dx, dx.name, compress_type=zipfile.ZIP_DEFLATED)
        for so in native_built + prebuilt_libs:
            zout.write(so, f'lib/arm64-v8a/{so.name}', compress_type=zipfile.ZIP_DEFLATED)
        for asset in sorted(assets_dir.rglob('*')):
            if asset.is_file():
                rel = asset.relative_to(assets_dir).as_posix()
                zout.write(asset, f'assets/{rel}', compress_type=zipfile.ZIP_STORED)

    aligned = WORK / 'aligned.apk'
    run([BT / 'zipalign.exe', '-p', '-f', '4', unaligned, aligned])

    ks = OUT_DIR / 'debug.keystore'
    ensure_keystore(ks)
    final_apk = OUT_DIR / 'EastwardCustom.apk'
    env = dict(os.environ, JAVA_HOME=str(JDK), PATH=f'{JDK / "bin"}{os.pathsep}{os.environ.get("PATH", "")}')
    run([
        BT / 'apksigner.bat', 'sign',
        '--ks', ks,
        '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android',
        '--ks-key-alias', 'eastward',
        '--out', final_apk,
        aligned,
    ], env=env)
    sz = final_apk.stat().st_size / (1024 * 1024)
    print(f'SUCCESS: {final_apk} ({sz:.2f} MiB)')


if __name__ == '__main__':
    main()
