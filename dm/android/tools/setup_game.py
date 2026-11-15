#!/usr/bin/env python3
"""On-device setup for Eastward Custom (010071B00F63A000).

Uses DroidPlay v1's clean lightweight target:
  1. Unpack NSP/XCI via nx_extract -> ExeFS/, RomFS/, aoc/, icon.jpg, control.nacp
  2. Rewrite code modules (main, subsdk1, sdk) via dm/tools/patch_nso.py (DM_TARGET=android) -> <out>/code/
  3. Split shaders via dm/gfx/tools/shader_split.py -> translate via dm/android/tools/spv_build.py + dmsh_xlate -> <out>/spv/
  4. Move RomFS/, install fonts/, write <out>/defaults.txt and <out>/ready
"""
import argparse
import os
import re
import shutil
import signal
import struct
import subprocess
import sys

TITLE_ID = 0x010071B00F63A000
TITLE_HEX = '010071B00F63A000'
MODULES = ('main', 'subsdk1', 'sdk')

HERE = os.path.dirname(os.path.abspath(__file__))
DM = os.path.normpath(os.path.join(HERE, '../..'))


def read_chain():
    hdr = os.path.join(DM, 'gfx', 'nvn', 'dmsh_chain.h')
    try:
        m = re.search(r'#define\s+DMSH_CHAIN\s+"([^"]+)"', open(hdr, encoding='utf-8').read())
        return m.group(1) if m else '1a0d0a59a263'
    except OSError:
        return '1a0d0a59a263'


def explain_extract_failure(log_path):
    try:
        tail = open(log_path, encoding='utf-8', errors='replace').read()[-8000:]
    except OSError:
        return '', False
    run = tail[tail.rfind('$ '):] if '$ ' in tail else tail
    m = re.search(r'Exception: (.+?) is incomplete: it has (\d+) of its (\d+) bytes \(([\d.]+)%\)', run)
    if m:
        return f'{m.group(1)} is incomplete ({m.group(4)}%): copy the whole file again.', False
    if '2002-4604' in run or 'Hash error' in run or 'VerificationFailed' in run or 'fails its hash check' in run:
        return 'dump file failed checksum check: copy it again cleanly, or tap Set up anyway.', True
    if 'MissingKeyException' in run or 'KeyNotFound' in run or 'Unable to decrypt' in run:
        return 'missing key in prod.keys/title.keys: dump keys again from your Switch.', False
    if 'No space left' in run or 'ENOSPC' in run:
        return 'not enough free storage space.', False
    if 'is not an NSP or XCI' in run or 'has a damaged header' in run:
        return 'selected file is not a valid Switch NSP/XCI.', False
    return '', False


def main():
    ap = argparse.ArgumentParser(description='Eastward Custom on-device game setup')
    ap.add_argument('--keys')
    ap.add_argument('--base')
    ap.add_argument('--update')
    ap.add_argument('--dlc', action='append', default=[])
    ap.add_argument('--out', required=True)
    ap.add_argument('--work', required=True)
    ap.add_argument('--bin', required=True)
    ap.add_argument('--ext-font')
    ap.add_argument('--std-font')
    ap.add_argument('--allow-damaged', action='store_true')
    ap.add_argument('--shaders-only', action='store_true')
    a = ap.parse_args()

    if not a.shaders_only and not (a.keys and a.base):
        ap.error('--keys and --base are required (unless --shaders-only)')

    os.makedirs(a.work, exist_ok=True)
    os.makedirs(a.out, exist_ok=True)
    log_path = os.path.join(a.work, 'setup.log')
    log = open(log_path, 'a', encoding='utf-8', buffering=1)
    py = sys.executable

    env = dict(os.environ, PYTHONUNBUFFERED='1')
    env['DM_GAME'] = TITLE_HEX
    env['DM_TARGET'] = 'android'
    env['DM_REWRITE_J'] = '1'
    env['DM_NO_FORK'] = '1'
    env['WORK'] = a.work
    env['PATH'] = a.bin + os.pathsep + env.get('PATH', '')

    steps = ['unpack the game', 'rewrite the code', 'translate the shaders', 'finish']

    def step(i):
        print(f'STEP {i}/{len(steps)} {steps[i - 1]}', flush=True)
        log.write(f'== step {i}/{len(steps)}: {steps[i - 1]}\n')

    def run(cmd, stream_progress=False, progress_scale=None, extra_env=None):
        e = dict(env, **(extra_env or {}))
        log.write('$ ' + ' '.join(map(str, cmd)) + '\n')
        p = subprocess.Popen(list(map(str, cmd)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             text=True, errors='replace', env=e)
        for ln in p.stdout:
            log.write(ln)
            if stream_progress and ln.startswith('progress:'):
                val = ln.split(':', 1)[1].strip()
                if '/' in val:
                    done, tot = val.split('/', 1)
                    try:
                        d, t = int(done), max(1, int(tot))
                        if progress_scale:
                            base_p, span_p = progress_scale
                            pct = base_p + int(span_p * min(d, t) / t)
                            print(f'PROGRESS {pct}/100', flush=True)
                        else:
                            print(f'PROGRESS {d}/{t}', flush=True)
                    except ValueError:
                        pass
        rc = p.wait()
        if rc != 0:
            name = os.path.basename(str(cmd[1] if str(cmd[0]) == py else cmd[0]))
            if rc < 0:
                try:
                    sig = signal.Signals(-rc).name
                except ValueError:
                    sig = f'signal {-rc}'
                raise RuntimeError(f'{name} stopped by {sig} ({rc}); see {log_path}')
            hint, allow = explain_extract_failure(log_path) if name == 'nx_extract' else ('', False)
            if allow and not a.allow_damaged:
                print('OFFER allow-damaged', flush=True)
            raise RuntimeError(f'{name} failed ({rc}){": " + hint if hint else ""}; see {log_path}')

    ex = os.path.join(a.work, 'extract')
    exefs = os.path.join(ex, 'ExeFS')
    romfs_ex = os.path.join(ex, 'RomFS')
    romfs_out = os.path.join(a.out, 'RomFS')
    code_out = os.path.join(a.out, 'code')
    spv_out = os.path.join(a.out, 'spv')
    ready_file = os.path.join(a.out, 'ready')

    if not a.shaders_only:
        step(1)
        keys = os.path.join(a.keys, 'prod.keys')
        if not os.path.isfile(keys):
            raise RuntimeError(f'prod.keys not found in {a.keys}')
        shutil.rmtree(ex, ignore_errors=True)
        aoc_tmp = os.path.join(a.work, 'aoc')
        shutil.rmtree(aoc_tmp, ignore_errors=True)

        files = [f for f in [a.base, a.update] + a.dlc if f]
        need = sum(os.path.getsize(f) for f in files if os.path.exists(f)) + (512 << 20)
        free = shutil.disk_usage(a.work).free
        if free < need:
            raise RuntimeError(f'need ~{need / (1 << 30):.1f} GB free, only {free / (1 << 30):.1f} GB available')

        cmd = [os.path.join(a.bin, 'nx_extract'), keys, files[0], '-', ex, '--aoc-dir', aoc_tmp]
        for f in files[1:]:
            cmd += ['--more', f]
        if a.allow_damaged:
            cmd.append('--allow-damaged')
        run(cmd)

        if not os.path.isfile(os.path.join(exefs, 'main')):
            raise RuntimeError('no ExeFS/main found in dump')
        try:
            nacp = open(os.path.join(ex, 'control.nacp'), 'rb').read()
            tid = struct.unpack_from('<Q', nacp, 0x3038)[0]
            tname = nacp[:0x200].split(b'\0')[0].decode('utf-8', 'replace')
        except (OSError, struct.error):
            tid, tname = 0, ''
        if tid and tid != TITLE_ID:
            raise RuntimeError(f'wrong game: {tname or "unknown"} ({tid:016X}); this app is exclusively for Eastward ({TITLE_HEX})')

        with open(os.path.join(exefs, 'main'), 'rb') as f:
            build_id = f.read(0x50)[0x40:0x50].hex()
        log.write(f'verified Eastward main build ID: {build_id}\n')

        try:
            os.remove(ready_file)
        except FileNotFoundError:
            pass
        for fname in ('icon.jpg', 'control.nacp'):
            src_f = os.path.join(ex, fname)
            if os.path.isfile(src_f):
                shutil.copy2(src_f, os.path.join(a.out, fname))

        aoc_dst = os.path.join(a.out, 'aoc')
        shutil.rmtree(aoc_dst, ignore_errors=True)
        if os.path.isdir(aoc_tmp) and any(os.scandir(aoc_tmp)):
            shutil.move(aoc_tmp, aoc_dst)
        # Reclaim copied SAF dump storage immediately now that ExeFS/RomFS are extracted.
        shutil.rmtree(os.path.join(a.work, 'import'), ignore_errors=True)

        step(2)
        shutil.rmtree(code_out, ignore_errors=True)
        os.makedirs(code_out, exist_ok=True)
        for i, m in enumerate(MODULES):
            mp = os.path.join(exefs, m)
            if os.path.isfile(mp):
                print(f'PROGRESS {i}/{len(MODULES)}', flush=True)
                run([py, os.path.join(DM, 'tools', 'patch_nso.py'), mp, code_out, m])
        print(f'PROGRESS {len(MODULES)}/{len(MODULES)}', flush=True)
        main_src = os.path.join(exefs, 'main')
        romfs_src = romfs_ex
    else:
        try:
            os.remove(ready_file)
        except FileNotFoundError:
            pass
        romfs_src = romfs_out if os.path.isdir(romfs_out) else romfs_ex
        exefs_main = os.path.join(exefs, 'main')
        main_patched = os.path.join(code_out, 'main.patched.bin')
        main_src = exefs_main if os.path.isfile(exefs_main) else main_patched
        if not (os.path.isdir(romfs_src) and os.path.isfile(main_patched)):
            raise RuntimeError('cannot re-translate shaders: RomFS or code/main.patched.bin missing')

    step(3)
    pkg = os.path.join(a.work, 'shader_pkg')
    shutil.rmtree(pkg, ignore_errors=True)
    os.makedirs(pkg, exist_ok=True)
    map_out = os.path.join(a.work, 'shader_map.tsv')
    facts_out = os.path.join(a.work, 'shader_facts.tsv')
    pairs_out = os.path.join(a.work, 'shader_pairs.tsv')
    dlc_dirs = []
    aoc_dst = os.path.join(a.out, 'aoc')
    if os.path.isdir(aoc_dst):
        dlc_dirs = sorted(e.path for e in os.scandir(aoc_dst) if e.is_dir())
    bundled_glslc = os.path.join(DM, 'games', TITLE_HEX, 'glslc_capture')
    user_glslc = os.path.join(a.out, 'glslc_capture')
    os.makedirs(user_glslc, exist_ok=True)
    if os.path.isdir(bundled_glslc):
        for fn in os.listdir(bundled_glslc):
            if fn.endswith('.glslc'):
                dst_g = os.path.join(user_glslc, fn)
                if not os.path.isfile(dst_g):
                    shutil.copy2(os.path.join(bundled_glslc, fn), dst_g)
    split_cmd = [
        py, os.path.join(DM, 'gfx', 'tools', 'shader_split.py'),
        romfs_src, main_src, pkg, map_out, facts_out, pairs_out, '', ','.join(dlc_dirs)
    ]
    run(split_cmd, stream_progress=True, progress_scale=(0, 30), extra_env={'DM_GLSLC_CAPTURE': user_glslc})

    shutil.rmtree(spv_out, ignore_errors=True)
    os.makedirs(spv_out, exist_ok=True)
    j = max(1, min(4, int(os.environ.get('J', '2'))))
    xlate = os.path.join(a.bin, 'dmsh_xlate')
    spv_cmd = [
        py, os.path.join(DM, 'android', 'tools', 'spv_build.py'),
        pkg, spv_out, '--xlate', xlate, '--facts', facts_out, '-j', str(j)
    ]
    run(spv_cmd, stream_progress=True, progress_scale=(30, 70))

    spv_files = [f for f in os.listdir(spv_out) if f.endswith('.spv') and not f.endswith('.z.spv')]
    if not spv_files:
        log.write('warning: 0 pre-captured .glslc shaders found yet; boot once to capture via DM_GLSLC_CAPTURE, then re-run shader translation\n')
    else:
        log.write(f'translated {len(spv_files)} SPIR-V shaders into {spv_out}\n')
    print('PROGRESS 100/100', flush=True)

    step(4)
    if (not a.shaders_only or not os.path.isdir(romfs_out)) and os.path.isdir(romfs_ex):
        shutil.rmtree(romfs_out, ignore_errors=True)
        shutil.move(romfs_ex, romfs_out)

    font_dir = os.path.join(a.out, 'font')
    os.makedirs(font_dir, exist_ok=True)
    if a.std_font and os.path.isfile(a.std_font):
        shutil.copy2(a.std_font, os.path.join(font_dir, 'standard.ttf'))
    else:
        for cand in (
            os.path.join(DM, 'android', 'assets', 'standard.ttf'),
            '/system/fonts/Roboto-Regular.ttf',
            '/system/fonts/NotoSansCJK-Regular.ttc',
        ):
            if os.path.isfile(cand):
                shutil.copy2(cand, os.path.join(font_dir, 'standard.ttf'))
                break
    ext_src = a.ext_font if (a.ext_font and os.path.isfile(a.ext_font)) else os.path.join(DM, 'android', 'assets', 'nintendo_ext.ttf')
    if os.path.isfile(ext_src):
        shutil.copy2(ext_src, os.path.join(font_dir, 'nintendo_ext.ttf'))

    defaults_path = os.path.join(a.out, 'defaults.txt')
    with open(defaults_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join([
            '# Tuned defaults for Eastward Custom (DroidPlay v1 Vulkan backend)',
            'DM_LANG=en-US',
            'DM_OPMODE=0',
            'DM_VK_CLEAR_LOADOP=1',
            'DM_VK_MALI_FP16=1',
            'DM_VK_SET_REUSE=1',
            'DM_SPV_PRECISE=0',
            'DM_VK_PIN=c0',
            'DM_VK_PIN_DONE=3f',
            f'DM_GLSLC_CAPTURE={user_glslc}',
        ]) + '\n')

    shutil.rmtree(ex, ignore_errors=True)
    shutil.rmtree(pkg, ignore_errors=True)
    print('PROGRESS 1/1', flush=True)
    chain = read_chain()
    with open(ready_file, 'w', encoding='utf-8', newline='\n') as f:
        f.write(chain + '\n')
    print('DONE ' + chain, flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as ex:
        print('FAILED ' + str(ex), flush=True)
        sys.exit(1)
