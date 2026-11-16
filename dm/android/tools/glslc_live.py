"""Persistent on-device live shader translator for runtime-captured .glslc blobs.

Protocol on stdin/stdout (one command per line):
  PING -> PONG
  SYNC -> OK <translated_count>
  QUIT -> OK 0 (and exit)
"""
import argparse
import concurrent.futures as cf
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
GFX_TOOLS = HERE.parents[1] / 'gfx' / 'tools'
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
if str(GFX_TOOLS) not in sys.path:
    sys.path.insert(0, str(GFX_TOOLS))
os.environ.setdefault('DM_GAME', '010071B00F63A000')

import dmsh_ampere  # noqa: E402
import shader_split  # noqa: E402
import spv_android as sa  # noqa: E402
import spv_build  # noqa: E402


def load_facts(facts_path: Path):
    facts = {}
    if facts_path and facts_path.is_file():
        for ln in facts_path.read_text(encoding='utf-8', errors='replace').splitlines():
            if ln.strip() and not ln.startswith('#'):
                parts = ln.split('\t', 1)
                if len(parts) == 2:
                    facts[parts[0]] = json.loads(parts[1])
    return facts


def is_shader_current(spv_dir: Path, key: str, stage: str) -> bool:
    rp = spv_dir / f'{key}.res.json'
    sp = spv_dir / f'{key}.spv'
    if not rp.is_file() or not sp.is_file():
        return False
    try:
        rj = json.loads(rp.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return False
    if rj.get('android') != sa.ANDROID_CHAIN:
        return False
    if stage in ('ts', 'ms') and (rj.get('mesh_emu', {}) or {'v': 0}).get('v') != spv_build.meshemu_v():
        return False
    return True


def sync_once(capture_dir: Path, spv_dir: Path, xlate_bin: str, work_root: Path, facts: dict, seen_files: set) -> int:
    if not capture_dir.is_dir():
        return 0
    spv_dir.mkdir(parents=True, exist_ok=True)
    candidates = sorted(
        p for p in capture_dir.iterdir()
        if p.is_file() and p.name.endswith('.glslc') and p.name not in seen_files
    )
    if not candidates:
        return 0

    batch = Path(tempfile.mkdtemp(prefix='live_', dir=str(work_root)))
    try:
        bin_dir = batch / 'bin'
        tout = batch / 'out'
        bin_dir.mkdir()
        tout.mkdir()

        jobs = {}
        tfs = {}
        file_keys = {}

        for p in candidates:
            try:
                raw = p.read_bytes()
            except OSError:
                continue
            entries = shader_split.glslc_shaders(raw, p.name, need=False)
            keys_for_file = []
            for _, stage, ctl, code in entries:
                if stage not in ('vs', 'ps', 'gs', 'tc', 'te', 'cs', 'ts', 'ms'):
                    continue
                if ctl[:4] != spv_build.CTL_MAGIC:
                    continue
                key = spv_build.key_of(ctl, code)
                keys_for_file.append(key)
                if is_shader_current(spv_dir, key, stage):
                    continue
                if key in jobs:
                    continue
                j, why = spv_build.job(key, stage, code, ctl, str(bin_dir), facts)
                if not j:
                    print(f'[glslc_live] skip {key}: {why}', file=sys.stderr, flush=True)
                    continue
                jobs[key] = j
                if stage == 'vs' and not dmsh_ampere.is_nvn2(ctl):
                    tf = spv_build.tf_layout(key, ctl)
                    if tf:
                        tfs[key] = tf
            if keys_for_file:
                file_keys[p.name] = keys_for_file

        if not jobs:
            for fn in file_keys:
                seen_files.add(fn)
            return 0

        keys = sorted(jobs)
        workers = 2 if len(keys) >= 4 else 1
        chunks = [keys[i::workers] for i in range(workers)]
        chunks = [c for c in chunks if c]
        xl = [xlate_bin] if not xlate_bin.endswith('.dll') else ['dotnet', xlate_bin]

        def run_chunk(chunk):
            jp = batch / f'job_{abs(hash(chunk[0]))}.json'
            jp.write_text(json.dumps([jobs[k] for k in chunk]), encoding='utf-8')
            return subprocess.run(xl + [str(jp), str(tout)], capture_output=True, text=True, errors='replace')

        def log_failures(res):
            for ln in (res.stderr or '').splitlines() + (res.stdout or '').splitlines():
                if '[RED]' in ln:
                    print(f'[glslc_live] {ln}', file=sys.stderr, flush=True)

        if len(chunks) == 1:
            log_failures(run_chunk(chunks[0]))
        else:
            with cf.ThreadPoolExecutor(len(chunks)) as ex:
                for res in ex.map(run_chunk, chunks):
                    log_failures(res)

        # Second pass for vertex shaders whose inputs are only used as integers.
        again = []
        for k in keys:
            if jobs[k]['stage'] != 'vs':
                continue
            sp = tout / f'{k}.spv'
            if not sp.is_file():
                continue
            found = spv_build.int_inputs_of(sp)
            extra = {loc: typ for loc, typ in found.items() if loc not in jobs[k]['attr']}
            if extra:
                jobs[k]['attr'].update(extra)
                again.append(k)
        if again:
            log_failures(run_chunk(again))

        done = sorted(p.name[:-4] for p in tout.glob('*.spv') if not p.name.endswith('.reader.spv'))
        for k in done:
            if k in tfs or 'mesh_stage' in jobs[k]:
                rp = tout / f'{k}.res.json'
                try:
                    rj = json.loads(rp.read_text(encoding='utf-8'))
                except (OSError, ValueError):
                    continue
                if k in tfs:
                    rj['tf'] = tfs[k]
                if 'mesh_stage' in jobs[k]:
                    rj['mesh'] = {'info': jobs[k]['mesh_stage'], 'lay': jobs[k]['stage_memory']}
                rp.write_text(json.dumps(rj), encoding='utf-8')

        made = 0
        for k in done:
            try:
                _, lines = sa.post_one(tout, spv_dir, k)
                for ln in lines:
                    print(f'[glslc_live] {ln}', file=sys.stderr, flush=True)
                if (spv_dir / f'{k}.res.json').is_file():
                    made += 1
            except Exception as exc:
                print(f'[glslc_live] post {k} failed: {exc}', file=sys.stderr, flush=True)

        for fn, fkeys in file_keys.items():
            if all((spv_dir / f'{k}.res.json').is_file() for k in fkeys):
                seen_files.add(fn)

        if made:
            print(f'[glslc_live] live-translated {made} shader(s) from {len(file_keys)} capture(s)', file=sys.stderr, flush=True)
        return made
    finally:
        shutil.rmtree(batch, ignore_errors=True)


def main():
    os.environ.setdefault('DM_NO_FORK', '1')
    ap = argparse.ArgumentParser()
    ap.add_argument('capture_dir')
    ap.add_argument('spv_dir')
    ap.add_argument('xlate_bin')
    ap.add_argument('work_dir')
    ap.add_argument('--facts', default='')
    args = ap.parse_args()

    capture_dir = Path(args.capture_dir)
    spv_dir = Path(args.spv_dir)
    work_root = Path(args.work_dir)
    work_root.mkdir(parents=True, exist_ok=True)
    for old in work_root.glob('live_*'):
        shutil.rmtree(old, ignore_errors=True)

    facts = load_facts(Path(args.facts)) if args.facts else {}
    seen_files = set()

    print('READY', flush=True)
    for raw_ln in sys.stdin:
        cmd = raw_ln.strip().upper()
        if not cmd:
            continue
        if cmd == 'PING':
            print('PONG', flush=True)
        elif cmd == 'QUIT':
            print('OK 0', flush=True)
            break
        elif cmd == 'SYNC':
            try:
                n = sync_once(capture_dir, spv_dir, args.xlate_bin, work_root, facts, seen_files)
                print(f'OK {n}', flush=True)
            except Exception as exc:
                print(f'[glslc_live] error: {exc}', file=sys.stderr, flush=True)
                print('ERR 0', flush=True)
        else:
            print('ERR 0', flush=True)


if __name__ == '__main__':
    main()
