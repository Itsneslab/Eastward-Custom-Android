#!/usr/bin/env python3
"""
Generate Eastward Custom Android visual assets:
1. 4x-supersampled transparent RGBA PNGs from eastward_icons/*.svg into app/assets/icons/
2. High-res launcher icons (ic_launcher.png & ic_launcher_round.png) into app/res/mipmap-*/
"""

import math
import os
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

TOOLS_DIR = Path(__file__).resolve().parent
APP_DIR = TOOLS_DIR.parent / 'app'
SVG_DIR = APP_DIR / 'eastward_icons'
ASSETS_ICONS_DIR = APP_DIR / 'assets' / 'icons'
RES_DIR = APP_DIR / 'res'

# Switch button slot name -> source SVG filename
SVG_MAP = {
    'a': '0_hand_interact.svg',
    'b': '1_group_chars.svg',
    'x': '2_ability_star.svg',
    'y': '3_attack_pan.svg',
    'l': '4_bag_inventory.svg',
    'r': '5_swap_weapon.svg',
    'zr': '7_swap_char.svg',
    'plus': '8_menu_pause.svg',
    'minus': '9_map_pin.svg',
}

MIPMAPS = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}


def _color_val(c):
    if not c or c == 'none':
        return None
    if c.lower().startswith('#f') or c.lower() == 'white':
        return 255
    if c.lower().startswith('#0') or c.lower() == 'black':
        return 0
    return 255


def rasterize_svg_alpha(svg_path, size=128, ss=4):
    """Rasterize a 128x128 #fff/#000 SVG into an L-mode alpha mask at `size`."""
    w_ss = size * ss
    scale = w_ss / 128.0
    im = Image.new('L', (w_ss, w_ss), 0)
    dr = ImageDraw.Draw(im)

    root = ET.parse(svg_path).getroot()

    def sc(v):
        return float(v) * scale

    def draw_elem(el):
        tag = el.tag.split('}')[-1]
        if tag == 'g':
            for ch in el:
                draw_elem(ch)
            return
        fill = _color_val(el.attrib.get('fill', '#fff'))
        stroke = _color_val(el.attrib.get('stroke'))
        sw = max(1, int(round(sc(el.attrib.get('stroke-width', '0')))))

        if tag == 'circle':
            cx, cy, r = sc(el.attrib['cx']), sc(el.attrib['cy']), sc(el.attrib['r'])
            box = [cx - r, cy - r, cx + r, cy + r]
            if fill is not None:
                dr.ellipse(box, fill=fill)
            if stroke is not None and sw > 0:
                dr.ellipse(box, outline=stroke, width=sw)
        elif tag == 'rect':
            x, y = sc(el.attrib['x']), sc(el.attrib['y'])
            w, h = sc(el.attrib['width']), sc(el.attrib['height'])
            rx = sc(el.attrib.get('rx', '0'))
            box = [x, y, x + w, y + h]
            if fill is not None:
                if rx > 0:
                    dr.rounded_rectangle(box, radius=rx, fill=fill)
                else:
                    dr.rectangle(box, fill=fill)
            if stroke is not None and sw > 0:
                if rx > 0:
                    dr.rounded_rectangle(box, radius=rx, outline=stroke, width=sw)
                else:
                    dr.rectangle(box, outline=stroke, width=sw)
        elif tag == 'line':
            x1, y1 = sc(el.attrib['x1']), sc(el.attrib['y1'])
            x2, y2 = sc(el.attrib['x2']), sc(el.attrib['y2'])
            c = stroke if stroke is not None else 255
            dr.line([(x1, y1), (x2, y2)], fill=c, width=sw)
            if el.attrib.get('stroke-linecap') == 'round':
                r = sw * 0.5
                dr.ellipse([x1 - r, y1 - r, x1 + r, y1 + r], fill=c)
                dr.ellipse([x2 - r, y2 - r, x2 + r, y2 + r], fill=c)
        elif tag in ('polygon', 'polyline'):
            raw = el.attrib['points'].replace(',', ' ').split()
            pts = [(sc(raw[i]), sc(raw[i + 1])) for i in range(0, len(raw), 2)]
            if tag == 'polygon':
                if fill is not None:
                    dr.polygon(pts, fill=fill)
                if stroke is not None and sw > 0:
                    dr.line(pts + [pts[0]], fill=stroke, width=sw, joint='curve')
            else:
                c = stroke if stroke is not None else 255
                dr.line(pts, fill=c, width=sw, joint='curve')
                if el.attrib.get('stroke-linecap') == 'round':
                    r = sw * 0.5
                    for px, py in (pts[0], pts[-1]):
                        dr.ellipse([px - r, py - r, px + r, py + r], fill=c)

    for child in root:
        draw_elem(child)

    return im.resize((size, size), Image.Resampling.LANCZOS)


def make_rgba_icon(alpha_mask, color=(255, 255, 255)):
    out = Image.new('RGBA', alpha_mask.size, (color[0], color[1], color[2], 0))
    out.putalpha(alpha_mask)
    return out


def make_emblem(size=256):
    """Render an Eastward emblem (golden pan + Sam sparkle inside warm charcoal badge)."""
    ss = 4
    w = size * ss
    img = Image.new('RGBA', (w, w), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)

    # Radial-like layered warm glow background disk
    cx = cy = w * 0.5
    dr.ellipse([w * 0.06, w * 0.06, w * 0.94, w * 0.94], fill=(24, 24, 30, 255))
    dr.ellipse([w * 0.12, w * 0.12, w * 0.88, w * 0.88], fill=(38, 33, 24, 255))
    dr.ellipse([w * 0.22, w * 0.22, w * 0.78, w * 0.78], fill=(54, 44, 20, 255))

    # Crisp double gold rings
    dr.ellipse([w * 0.06, w * 0.06, w * 0.94, w * 0.94], outline=(245, 196, 40, 255), width=max(4, int(w * 0.048)))
    dr.ellipse([w * 0.135, w * 0.135, w * 0.865, w * 0.865], outline=(252, 224, 118, 175), width=max(2, int(w * 0.014)))

    # Frying Pan SVG centered + tinted warm ivory-gold (includes built-in sparkle)
    pan_mask = rasterize_svg_alpha(SVG_DIR / '3_attack_pan.svg', size=int(size * 0.74), ss=4)
    pan_shadow = make_rgba_icon(pan_mask.filter(ImageFilter.GaussianBlur(radius=1.8)), (8, 6, 3))
    pan_gold = make_rgba_icon(pan_mask, (255, 240, 178))

    down = img.resize((size, size), Image.Resampling.LANCZOS)
    ox = int((size - pan_gold.width) * 0.50)
    oy = int((size - pan_gold.height) * 0.52)
    down.alpha_composite(pan_shadow, (ox + max(1, size // 64), oy + max(2, size // 48)))
    down.alpha_composite(pan_gold, (ox, oy))
    return down


def make_launcher_icon(size, round_icon=False):
    ss = 4
    w = size * ss
    base = Image.new('RGBA', (w, w), (0, 0, 0, 0))
    dr = ImageDraw.Draw(base)
    pad = int(w * 0.06)

    if round_icon:
        dr.ellipse([pad, pad, w - pad, w - pad], fill=(20, 22, 28, 255), outline=(245, 196, 40, 255), width=max(4, int(w * 0.05)))
        dr.ellipse([w * 0.14, w * 0.14, w * 0.86, w * 0.86], fill=(32, 28, 22, 255))
    else:
        rad = int(w * 0.24)
        dr.rounded_rectangle([pad, pad, w - pad, w - pad], radius=rad, fill=(20, 22, 28, 255), outline=(245, 196, 40, 255), width=max(4, int(w * 0.05)))
        dr.rounded_rectangle([w * 0.12, w * 0.12, w * 0.88, w * 0.88], radius=int(rad * 0.78), fill=(30, 27, 20, 255), outline=(250, 214, 90, 90), width=max(2, int(w * 0.015)))

    out = base.resize((size, size), Image.Resampling.LANCZOS)

    # Pan artwork centered inside launcher card (built-in SVG sparkle included)
    pan_sz = max(24, int(size * 0.70))
    pan_mask = rasterize_svg_alpha(SVG_DIR / '3_attack_pan.svg', size=pan_sz, ss=4)
    pan_shadow = make_rgba_icon(pan_mask.filter(ImageFilter.GaussianBlur(radius=max(1.0, size / 48.0))), (0, 0, 0))
    pan_fg = make_rgba_icon(pan_mask, (255, 240, 178))
    ox = int((size - pan_sz) * 0.50)
    oy = int((size - pan_sz) * 0.52)
    out.alpha_composite(pan_shadow, (ox + max(1, size // 64), oy + max(2, size // 42)))
    out.alpha_composite(pan_fg, (ox, oy))
    return out


def generate_all():
    ASSETS_ICONS_DIR.mkdir(parents=True, exist_ok=True)
    for key, svg_name in SVG_MAP.items():
        mask = rasterize_svg_alpha(SVG_DIR / svg_name, size=128, ss=4)
        make_rgba_icon(mask, (255, 255, 255)).save(ASSETS_ICONS_DIR / f'{key}.png')

    make_emblem(192).save(ASSETS_ICONS_DIR / 'emblem.png')

    for folder, px in MIPMAPS.items():
        mdir = RES_DIR / folder
        mdir.mkdir(parents=True, exist_ok=True)
        make_launcher_icon(px, round_icon=False).save(mdir / 'ic_launcher.png')
        make_launcher_icon(px, round_icon=True).save(mdir / 'ic_launcher_round.png')


if __name__ == '__main__':
    generate_all()
    print('Generated Eastward SVG icons + launcher mipmaps:', ASSETS_ICONS_DIR)
