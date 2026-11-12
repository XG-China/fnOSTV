"""生成 fnOSTV 的启动图标与 TV banner。

标记（logo mark）与 app 内 FnosLogoMarkView 的几何一致：
外圈带缺口的圆环 + 中心镂空 + 顶部两个箭头（“播放/前进”意象）。

用法（Windows）：
    python scripts/generate-icons.py

生成物：
    app/src/main/res/mipmap-*/ic_launcher.png   （48/72/96/144/192）
    app/src/main/res/drawable-xhdpi/app_banner.png（320x180，Android TV 用）
"""

import os
import math

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BRAND = (0x00, 0x66, 0xFF, 255)      # 品牌蓝（与网页/深色主题一致）
WHITE = (0xFF, 0xFF, 0xFF, 255)
INK = (0x10, 0x18, 0x20, 255)        # 深色底

SS = 4  # 超采样倍数，抗锯齿


def _mark(draw, s, left, top, color, hole):
    """按 0..1 相对坐标绘制标记（与 FnosLogoMarkView 相同比例）。"""
    x = lambda v: left + s * v
    y = lambda v: top + s * v

    # 外圈：118° 起顺时针 292°
    draw.pieslice([x(0.16), y(0.28), x(0.78), y(0.90)], 118, 118 + 292, fill=color)
    # 中心镂空
    r = s * 0.13
    draw.ellipse([x(0.46) - r, y(0.58) - r, x(0.46) + r, y(0.58) + r], fill=hole)
    # 两个箭头
    draw.polygon([(x(0.48), y(0.12)), (x(0.66), y(0.12)), (x(0.56), y(0.34)), (x(0.38), y(0.34))], fill=color)
    draw.polygon([(x(0.74), y(0.12)), (x(0.92), y(0.12)), (x(0.80), y(0.34)), (x(0.62), y(0.34))], fill=color)


def _new(size):
    return Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))


def _down(img, size):
    return img.resize((size, size), Image.LANCZOS)


def icon(size, rounded=True):
    big = _new(size)
    d = ImageDraw.Draw(big)
    S = size * SS
    if rounded:
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.20), fill=BRAND)
    else:
        d.rectangle([0, 0, S, S], fill=BRAND)
    # 标记占画布 82%，居中（小尺寸下更容易辨认）
    mark = S * 0.82
    left = (S - mark) / 2.0
    top = (S - mark) / 2.0
    _mark(d, mark, left, top, WHITE, BRAND)
    return _down(big, size)


def banner(width, height):
    big = Image.new("RGBA", (width * SS, height * SS), INK)
    d = ImageDraw.Draw(big)
    W, H = width * SS, height * SS

    mark = H * 0.62
    left = W * 0.07
    top = (H - mark) / 2.0
    _mark(d, mark, left, top, (0x4C, 0x8D, 0xFF, 255), INK)

    text = "fnOSTV"
    font = None
    for size in range(int(H * 0.30), 8, -1):
        try:
            candidate = ImageFont.load_default(size=size * SS)
        except TypeError:
            candidate = ImageFont.load_default()
        box = d.textbbox((0, 0), text, font=candidate)
        if box[2] - box[0] <= W * 0.52:
            font = candidate
            break
    if font is not None:
        box = d.textbbox((0, 0), text, font=font)
        tw, th = box[2] - box[0], box[3] - box[1]
        tx = left + mark + W * 0.05
        ty = (H - th) / 2.0 - box[1]
        d.text((tx, ty), text, font=font, fill=WHITE)

    return big.resize((width, height), Image.LANCZOS)


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

def main():
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES, "mipmap-" + density)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, "ic_launcher.png")
        icon(size).save(path, optimize=True)
        print("wrote", os.path.relpath(path, ROOT))

    # Android TV / Leanback 需要 320x180 banner（xhdpi）
    banner_dir = os.path.join(RES, "drawable-xhdpi")
    os.makedirs(banner_dir, exist_ok=True)
    banner_path = os.path.join(banner_dir, "app_banner.png")
    banner(320, 180).convert("RGB").save(banner_path, optimize=True)
    print("wrote", os.path.relpath(banner_path, ROOT))


if __name__ == "__main__":
    main()
