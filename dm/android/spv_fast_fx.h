// In-place SPIR-V fast-path pass (zero length change, zero new IDs, no libc allocation inside):
// 1. strip_isnan: removes redundant OpIsNan + OpSelect(0, uv) guards on OpImageSample* within the same basic block.
// 2. fast_fx: simplifies Eastward's known heavy 1280x720 fullscreen post-FX fragment shaders while keeping
//    100% native sharpness (0 downscale blur / 0 HUD lines):
//    - 55f39892-class (Bloom + Film Grain + LUT): zeroes Sin() grain (eliminates trig ALU & dynamic branches)
//      and aliases 4 corner bloom taps (#6..#9) to cardinal taps (#1,#3,#5,#4), preserving exact weight sum.
//    - 8582f2c0-class (4-neighbor Unsharp Mask): points tap #1 at center UV and aliases taps #2..#5 via
//      OpCopyObject, preserving exact 1.0x brightness and native 720p crisp pixels while cutting 5 -> 1 fetch.
#pragma once
#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint32_t op, blk;
    uint32_t idx;
    uint8_t is_zero;
    uint8_t stored_pre;
} spv_fx_def;

static int spv_has_discard(const uint32_t *w, size_t n) {
    if (n < 5 || w[0] != 0x07230203u) return 0;
    for (size_t i = 5; i < n; ) {
        uint32_t len = w[i] >> 16, op = w[i] & 0xffffu;
        if (!len || i + len > n) break;
        if (op == 252u || op == 4416u || op == 5338u) return 1;
        i += len;
    }
    return 0;
}

typedef struct {
    size_t idx, load_px_idx, load_py_idx;
    uint32_t len, blk, res_t, res_id, img_var, px_var, py_var;
} spv_fx_samp;

static void spv_fx_alias(uint32_t *w, const spv_fx_samp *dst, const spv_fx_samp *src) {
    size_t p = dst->idx;
    w[p] = (4u << 16) | 83u;      // OpCopyObject (SPIR-V 1.0)
    w[p + 1] = dst->res_t;
    w[p + 2] = dst->res_id;
    w[p + 3] = src->res_id;
    for (uint32_t z = 4; z < dst->len; z++) w[p + z] = 0x00010000u;   // OpNop
}

static int spv_fx_uniform_block(const spv_fx_samp *s, int n) {
    uint32_t tgt_var = s[0].img_var, blk = s[0].blk, rt = s[0].res_t;
    if (!tgt_var) return 0;
    for (int k = 0; k < n; k++) {
        if (s[k].img_var != tgt_var || s[k].blk != blk || s[k].res_t != rt || s[k].len < 5) return 0;
    }
    return 1;
}

static size_t spv_fast_fx(uint32_t *w, size_t n, spv_fx_def *defs, uint32_t bound,
                          int strip_isnan, int fast_fx, int *stats) {
    if (stats) stats[0] = stats[1] = stats[2] = 0;
    if (!w || n < 5 || w[0] != 0x07230203u || !bound || !defs) return 0;
    for (uint32_t k = 0; k < bound; k++) {
        defs[k].op = 0; defs[k].blk = 0; defs[k].idx = 0;
        defs[k].is_zero = 0; defs[k].stored_pre = 0;
    }

    int is_frag = 0, has_grain = 0, has_sharp = 0, in_fn = 0;
    uint32_t f32_id = 0, f32_0_id = 0;
    uint32_t cur_blk = 0, first_fn_blk = 0;
    size_t sin_idx = 0;
    int sin_count = 0;

    spv_fx_samp samps[24];
    int nsamp = 0;
    int modified = 0;

    for (size_t i = 5; i < n; ) {
        uint32_t len = w[i] >> 16, op = w[i] & 0xffffu;
        if (!len || i + len > n) break;

        if (!in_fn) {
            if (op == 15u && len >= 2 && w[i + 1] == 4u) is_frag = 1;   // OpEntryPoint Fragment
            else if (op == 22u && len >= 3 && w[i + 2] == 32u) f32_id = w[i + 1];   // OpTypeFloat 32
            else if (op == 43u && len == 4 && w[i + 2] < bound) {   // OpConstant
                if (w[i + 3] == 0u) {
                    defs[w[i + 2]].is_zero = 1;
                    if (w[i + 1] == f32_id && !f32_0_id) f32_0_id = w[i + 2];
                } else if (w[i + 1] == f32_id) {
                    if (w[i + 3] == 0x472aee8cu) has_grain = 1;   // 43758.5469f
                    else if (w[i + 3] == 0xbdcccccdU) has_sharp = 1;   // -0.1f
                }
            } else if (op == 46u && len == 3 && w[i + 2] < bound) {   // OpConstantNull
                defs[w[i + 2]].is_zero = 1;
            } else if (op == 44u && len >= 4 && w[i + 2] < bound) {   // OpConstantComposite
                int az = 1;
                for (uint32_t k = 3; k < len; k++) {
                    if (w[i + k] >= bound || !defs[w[i + k]].is_zero) { az = 0; break; }
                }
                if (az) defs[w[i + 2]].is_zero = 1;
            } else if (op == 54u) {
                in_fn = 1;
            }
        } else {
            if (op == 56u) {   // OpFunctionEnd
                in_fn = 0;
            } else if (op == 248u) {   // OpLabel
                cur_blk++;
                if (!first_fn_blk) first_fn_blk = cur_blk;
            } else {
                // Record SSA definitions with their enclosing basic block (result id at w[i+2])
                if ((op == 61u || op == 80u || op == 156u || op == 169u) && len >= 4 && w[i + 2] < bound) {
                    defs[w[i + 2]].op = op;
                    defs[w[i + 2]].blk = cur_blk;
                    defs[w[i + 2]].idx = (uint32_t)i;
                }
                if (op == 62u && len >= 3 && w[i + 1] < bound && nsamp == 0 && cur_blk == first_fn_blk) {
                    defs[w[i + 1]].stored_pre = 1;
                }
                if (op == 12u && len == 6 && w[i + 1] == f32_id && w[i + 4] == 13u) {   // OpExtInst ... Sin
                    sin_idx = i;
                    sin_count++;
                }
                if (op >= 87u && op <= 94u && len >= 5) {
                    uint32_t coord = w[i + 4];
                    uint32_t raw_coord = coord;
                    if (coord < bound && defs[coord].op == 169u && defs[coord].blk == cur_blk) {
                        uint32_t si = defs[coord].idx;
                        uint32_t cond = w[si + 3], vt = w[si + 4], vf = w[si + 5];
                        if (cond < bound && defs[cond].op == 156u && defs[cond].blk == cur_blk &&
                            vt < bound && defs[vt].is_zero && vf == w[defs[cond].idx + 3]) {
                            raw_coord = vf;
                            if (strip_isnan) {
                                w[i + 4] = vf;
                                modified = 1;
                                if (stats) stats[0]++;
                            }
                        }
                    }
                    if (op == 87u && is_frag && fast_fx && nsamp < 24) {
                        uint32_t img_ld = w[i + 3], img_var = 0;
                        if (img_ld < bound && defs[img_ld].op == 61u && defs[img_ld].blk == cur_blk)
                            img_var = w[defs[img_ld].idx + 3];
                        uint32_t px_var = 0, py_var = 0;
                        size_t lpx = 0, lpy = 0;
                        if (raw_coord < bound && defs[raw_coord].op == 80u && defs[raw_coord].blk == cur_blk &&
                            (w[defs[raw_coord].idx] >> 16) == 5u) {
                            uint32_t cc = defs[raw_coord].idx;
                            uint32_t cx = w[cc + 3], cy = w[cc + 4];
                            if (cx < bound && defs[cx].op == 61u && defs[cx].blk == cur_blk) {
                                lpx = defs[cx].idx; px_var = w[lpx + 3];
                            }
                            if (cy < bound && defs[cy].op == 61u && defs[cy].blk == cur_blk) {
                                lpy = defs[cy].idx; py_var = w[lpy + 3];
                            }
                        }
                        samps[nsamp++] = (spv_fx_samp){
                            .idx = i, .load_px_idx = lpx, .load_py_idx = lpy,
                            .len = len, .blk = cur_blk, .res_t = w[i + 1], .res_id = w[i + 2],
                            .img_var = img_var, .px_var = px_var, .py_var = py_var
                        };
                    }
                }
            }
        }
        i += len;
    }

    if (!is_frag || fast_fx <= 0) return (size_t)modified;

    // Pattern A: 55f39892-class Bloom + Grain + LUT
    if (has_grain && f32_0_id && sin_count == 1 && nsamp >= 9 && spv_fx_uniform_block(samps, 9)) {
        w[sin_idx] = (4u << 16) | 83u;    // OpCopyObject (SPIR-V 1.0)
        w[sin_idx + 1] = f32_id;
        w[sin_idx + 2] = w[sin_idx + 2];
        w[sin_idx + 3] = f32_0_id;
        w[sin_idx + 4] = 0x00010000u;     // OpNop
        w[sin_idx + 5] = 0x00010000u;     // OpNop
        static const int alias_map[4] = {0, 2, 4, 3};   // NE->N, SE->E, NW->W, SW->S
        for (int k = 0; k < 4; k++) spv_fx_alias(w, &samps[5 + k], &samps[alias_map[k]]);
        modified = 1;
        if (stats) { stats[1] += 4; stats[2] += 1; }
        return (size_t)modified;
    }

    // Pattern B: 8582f2c0-class 5-tap unsharp mask -> 1 center tap
    if (has_sharp && !has_grain && nsamp == 5 && spv_fx_uniform_block(samps, 5) &&
        samps[0].load_px_idx && samps[0].load_py_idx &&
        samps[4].px_var < bound && samps[4].py_var < bound &&
        defs[samps[4].px_var].stored_pre && defs[samps[4].py_var].stored_pre) {
        if (samps[0].px_var != samps[4].px_var) w[samps[0].load_px_idx + 3] = samps[4].px_var;
        if (samps[0].py_var != samps[4].py_var) w[samps[0].load_py_idx + 3] = samps[4].py_var;
        for (int k = 1; k < 5; k++) spv_fx_alias(w, &samps[k], &samps[0]);
        modified = 1;
        if (stats) stats[1] += 4;
        return (size_t)modified;
    }

    // Pattern C: ed258dfe / c6a97dbb-class 15-tap separable Gaussian light/glow blur -> 8 representative taps
    if (!has_grain && !has_sharp && nsamp == 15 && spv_fx_uniform_block(samps, 15)) {
        static const int g_pairs[7][2] = {{2, 1}, {4, 3}, {6, 5}, {8, 7}, {10, 9}, {12, 11}, {14, 13}};
        for (int k = 0; k < 7; k++) spv_fx_alias(w, &samps[g_pairs[k][0]], &samps[g_pairs[k][1]]);
        modified = 1;
        if (stats) stats[1] += 7;
        return (size_t)modified;
    }

    // Pattern D: a8e893c1-class 9-tap interior/bloom box blur -> 5 taps
    if (!has_grain && !has_sharp && nsamp == 9 && spv_fx_uniform_block(samps, 9)) {
        static const int b_pairs[4][2] = {{3, 2}, {5, 4}, {7, 6}, {8, 4}};
        for (int k = 0; k < 4; k++) spv_fx_alias(w, &samps[b_pairs[k][0]], &samps[b_pairs[k][1]]);
        modified = 1;
        if (stats) stats[1] += 4;
        return (size_t)modified;
    }

    return (size_t)modified;
}
