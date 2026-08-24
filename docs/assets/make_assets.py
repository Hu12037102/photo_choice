"""Generate the README visual assets for PhotoChoice.

Everything is drawn programmatically at @2x and downsampled by the ``width``
attribute in the README, so the assets stay crisp on Retina displays.

The phone screens are *illustrations*, not real screenshots: ``docs/demo.mp4``
contains private photos, and blowing those up into the repository header image
is not something a README should do.

Run:  python docs/assets/make_assets.py
Deps: pillow, qrcode
"""

from __future__ import annotations

import os
import shutil
import subprocess
from dataclasses import dataclass

import qrcode
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from qrcode.constants import ERROR_CORRECT_M
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers.pil import RoundedModuleDrawer

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.dirname(HERE)

APK_URL = "https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"

S = 2  # render scale (@2x)


# --------------------------------------------------------------------------- #
# Palette
# --------------------------------------------------------------------------- #

@dataclass(frozen=True)
class Theme:
    name: str
    bg: tuple
    surface: tuple
    text: tuple
    muted: tuple
    accent: tuple
    hairline: tuple
    # phone chrome
    screen_bg: tuple
    screen_text: tuple
    screen_muted: tuple
    screen_tile: tuple
    bezel: tuple


LIGHT = Theme(
    name="light",
    bg=(251, 251, 253),
    surface=(255, 255, 255),
    text=(29, 29, 31),
    muted=(110, 110, 115),
    accent=(200, 118, 60),
    hairline=(229, 229, 234),
    screen_bg=(255, 255, 255),
    screen_text=(29, 29, 31),
    screen_muted=(142, 142, 147),
    screen_tile=(242, 242, 247),
    bezel=(28, 28, 30),
)

DARK = Theme(
    name="dark",
    bg=(11, 11, 15),
    surface=(26, 26, 30),
    text=(245, 245, 247),
    muted=(134, 134, 139),
    accent=(224, 139, 76),
    hairline=(44, 44, 46),
    screen_bg=(20, 20, 22),
    screen_text=(245, 245, 247),
    screen_muted=(134, 134, 139),
    screen_tile=(38, 38, 42),
    bezel=(58, 58, 62),
)

FONT_DIR = r"C:\Windows\Fonts"
F_LIGHT = os.path.join(FONT_DIR, "segoeuil.ttf")
F_REG = os.path.join(FONT_DIR, "segoeui.ttf")
F_SEMI = os.path.join(FONT_DIR, "seguisb.ttf")
F_BOLD = os.path.join(FONT_DIR, "segoeuib.ttf")


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size * S)


def demo_duration_label(default: str = "0:34") -> str:
    """Real length of docs/demo.mp4, so the poster can't drift out of sync with it."""
    ffprobe = shutil.which("ffprobe") or os.environ.get("FFPROBE") or r"E:\ffmpeg\bin\ffprobe.exe"
    try:
        out = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", os.path.join(DOCS, "demo.mp4")],
            capture_output=True, text=True, timeout=15, check=True,
        )
        secs = round(float(out.stdout.strip()))
        return f"{secs // 60}:{secs % 60:02d}"
    except Exception:
        return default


# --------------------------------------------------------------------------- #
# Drawing helpers
# --------------------------------------------------------------------------- #

def px(v: float) -> int:
    return int(round(v * S))


def gradient(size: tuple[int, int], c0: tuple, c1: tuple, diagonal: bool = True) -> Image.Image:
    """Cheap smooth gradient: paint a 2x2 seed and let BICUBIC do the work."""
    seed = Image.new("RGB", (2, 2))
    if diagonal:
        mid_a = tuple((a * 2 + b) // 3 for a, b in zip(c0, c1))
        mid_b = tuple((a + b * 2) // 3 for a, b in zip(c0, c1))
        seed.putdata([c0, mid_a, mid_b, c1])
    else:
        seed.putdata([c0, c0, c1, c1])
    return seed.resize(size, Image.BICUBIC)


def desaturate(img: Image.Image, amount: float) -> Image.Image:
    """Pull colours toward grey; keeps the placeholder tiles from looking candy-ish."""
    grey = img.convert("L").convert("RGB")
    return Image.blend(img, grey, amount)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def shadow(size: tuple[int, int], radius: int, blur: int, opacity: int, spread: int = 0) -> Image.Image:
    """Soft drop shadow layer sized to fit the blur, returned as RGBA."""
    pad = blur * 3
    layer = Image.new("RGBA", (size[0] + pad * 2, size[1] + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        (pad - spread, pad - spread, pad + size[0] + spread, pad + size[1] + spread),
        radius + spread,
        fill=(0, 0, 0, opacity),
    )
    return layer.filter(ImageFilter.GaussianBlur(blur)), pad


def paste_shadowed(base: Image.Image, img: Image.Image, xy: tuple[int, int],
                   radius: int, blur: int, opacity: int, dy: int = 0) -> None:
    sh, pad = shadow(img.size, radius, blur, opacity)
    base.alpha_composite(sh, (xy[0] - pad, xy[1] - pad + dy))
    base.alpha_composite(img, xy)


def tracked_text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str,
                 fnt: ImageFont.FreeTypeFont, fill: tuple, tracking: int = 0,
                 anchor_mid: bool = False) -> int:
    """Draw text with manual letter-spacing. Returns the drawn width."""
    total = sum(draw.textlength(ch, font=fnt) + tracking for ch in text) - tracking
    x, y = xy
    if anchor_mid:
        x -= total / 2
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += draw.textlength(ch, font=fnt) + tracking
    return int(total)


# --------------------------------------------------------------------------- #
# Photo placeholders
# --------------------------------------------------------------------------- #

# Muted gradient pairs standing in for photos — no real imagery, no privacy leak.
SWATCHES = [
    ((255, 211, 165), (253, 141, 133)),
    ((168, 237, 234), (254, 214, 227)),
    ((196, 233, 168), (150, 214, 161)),
    ((161, 196, 253), (194, 233, 251)),
    ((251, 194, 235), (166, 193, 238)),
    ((255, 236, 210), (252, 182, 159)),
    ((224, 195, 252), (142, 197, 252)),
    ((246, 211, 101), (253, 160, 133)),
    ((186, 200, 224), (146, 163, 196)),
    ((210, 233, 216), (168, 201, 190)),
    ((255, 214, 198), (233, 176, 178)),
    ((197, 213, 245), (169, 184, 224)),
]


def photo_tile(size: tuple[int, int], idx: int, theme: Theme) -> Image.Image:
    c0, c1 = SWATCHES[idx % len(SWATCHES)]
    img = gradient(size, c0, c1)
    img = desaturate(img, 0.30 if theme.name == "light" else 0.42)
    if theme.name == "dark":
        img = Image.blend(img, Image.new("RGB", size, (0, 0, 0)), 0.22)
    return img.convert("RGBA")


# --------------------------------------------------------------------------- #
# Phone screens
# --------------------------------------------------------------------------- #

SCREEN_W, SCREEN_H = 360, 780  # logical points; multiplied by S internally


def _screen_base(theme: Theme) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (px(SCREEN_W), px(SCREEN_H)), theme.screen_bg + (255,))
    return img, ImageDraw.Draw(img)


def _status_bar(draw: ImageDraw.ImageDraw, theme: Theme, y: int = 18) -> None:
    f = font(F_SEMI, 12)
    draw.text((px(22), px(y)), "9:41", font=f, fill=theme.screen_text)
    # signal / wifi / battery, abstracted to three small marks
    x = px(SCREEN_W - 74)
    for h in (5, 8, 11):
        draw.rounded_rectangle((x, px(y + 13) - px(h), x + px(3), px(y + 13)), px(1),
                               fill=theme.screen_text)
        x += px(5)
    draw.rounded_rectangle((px(SCREEN_W - 50), px(y + 3), px(SCREEN_W - 28), px(y + 13)),
                           px(3), outline=theme.screen_text, width=max(1, S))
    draw.rounded_rectangle((px(SCREEN_W - 48), px(y + 5), px(SCREEN_W - 34), px(y + 11)),
                           px(2), fill=theme.screen_text)


def _chevron(draw: ImageDraw.ImageDraw, cx: int, cy: int, size: int, colour: tuple,
             direction: str = "left", width: int = 2) -> None:
    s = px(size)
    w = max(1, px(width))
    if direction == "left":
        draw.line([(cx + s // 2, cy - s), (cx - s // 2, cy), (cx + s // 2, cy + s)],
                  fill=colour, width=w, joint="curve")
    else:  # down
        draw.line([(cx - s, cy - s // 2), (cx, cy + s // 2), (cx + s, cy - s // 2)],
                  fill=colour, width=w, joint="curve")


def screen_grid(theme: Theme) -> Image.Image:
    """The picker grid: camera tile, selection badges, LIVE badge, action bar."""
    img, draw = _screen_base(theme)
    _status_bar(draw, theme)

    # top bar
    _chevron(draw, px(26), px(58), 6, theme.screen_text)
    f_title = font(F_SEMI, 16)
    title = "All Photos"
    tw = draw.textlength(title, font=f_title)
    draw.text((px(SCREEN_W / 2) - tw / 2 - px(7), px(50)), title, font=f_title,
              fill=theme.screen_text)
    _chevron(draw, int(px(SCREEN_W / 2) + tw / 2 + px(6)), px(57), 4, theme.screen_muted,
             direction="down")

    # grid
    cols, gap, pad = 3, 3, 0
    top = px(84)
    cell = (px(SCREEN_W) - px(gap) * (cols - 1) - px(pad) * 2) // cols
    selected = {1: 1, 4: 2, 6: 3}
    live_cell = 5
    n = 0
    for row in range(6):
        for col in range(cols):
            x = px(pad) + col * (cell + px(gap))
            y = top + row * (cell + px(gap))
            if y > px(SCREEN_H):
                break

            if n == 0:
                # camera entry tile
                draw.rectangle((x, y, x + cell, y + cell), fill=theme.screen_tile)
                cx, cy = x + cell // 2, y + cell // 2
                bw, bh = px(26), px(20)
                draw.rounded_rectangle((cx - bw // 2, cy - bh // 2 + px(2),
                                        cx + bw // 2, cy + bh // 2 + px(2)),
                                       px(4), outline=theme.screen_muted, width=max(1, px(1.5)))
                draw.rounded_rectangle((cx - px(5), cy - bh // 2 - px(2),
                                        cx + px(5), cy - bh // 2 + px(3)),
                                       px(2), fill=theme.screen_muted)
                draw.ellipse((cx - px(5), cy - px(3), cx + px(5), cy + px(7)),
                             outline=theme.screen_muted, width=max(1, px(1.5)))
                n += 1
                continue

            tile = photo_tile((cell, cell), n - 1, theme)
            img.paste(tile, (x, y))

            # selection order badge
            order = selected.get(n)
            r = px(11)
            bx, by = x + cell - px(9) - r, y + px(9) + r
            if order:
                # selected cells get a thin accent frame, like the real grid
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), outline=theme.accent,
                               width=max(2, px(2)))
                draw.ellipse((bx - r, by - r, bx + r, by + r), fill=theme.accent)
                draw.text((bx, by), str(order), font=font(F_SEMI, 12),
                          fill=(255, 255, 255), anchor="mm")
            else:
                draw.ellipse((bx - r, by - r, bx + r, by + r),
                             outline=(255, 255, 255, 235), width=max(1, px(1.5)))

            if n == live_cell:
                fl = font(F_BOLD, 9)
                lw = draw.textlength("LIVE", font=fl)
                lx, ly = x + px(8), y + cell - px(22)
                draw.rounded_rectangle((lx, ly, lx + lw + px(12), ly + px(15)), px(7),
                                       fill=(0, 0, 0, 105))
                draw.text((lx + px(6), ly + px(2)), "LIVE", font=fl, fill=(255, 255, 255))
            n += 1

    # bottom action bar
    bar_h = px(64)
    by0 = px(SCREEN_H) - bar_h
    draw.rectangle((0, by0, px(SCREEN_W), px(SCREEN_H)), fill=theme.screen_bg)
    draw.line((0, by0, px(SCREEN_W), by0), fill=theme.hairline, width=max(1, S))
    fb = font(F_REG, 14)
    draw.text((px(22), by0 + px(18)), "Preview", font=fb, fill=theme.screen_muted)
    btn_w, btn_h = px(96), px(34)
    bx0 = px(SCREEN_W) - px(22) - btn_w
    draw.rounded_rectangle((bx0, by0 + px(12), bx0 + btn_w, by0 + px(12) + btn_h),
                           btn_h // 2, fill=theme.accent)
    draw.text((bx0 + btn_w / 2, by0 + px(12) + btn_h / 2), "Done (3)",
              font=font(F_SEMI, 14), fill=(255, 255, 255), anchor="mm")
    return img


def screen_preview(theme: Theme) -> Image.Image:
    """Full-screen preview with the LIVE badge — always dark chrome, like the real one."""
    img = Image.new("RGBA", (px(SCREEN_W), px(SCREEN_H)), (12, 12, 14, 255))
    draw = ImageDraw.Draw(img)

    photo_top, photo_h = px(100), px(560)
    photo = photo_tile((px(SCREEN_W), photo_h), 4, LIGHT)
    img.paste(photo, (0, photo_top))

    dark_chrome = Theme(**{**theme.__dict__, "screen_text": (245, 245, 247),
                           "screen_muted": (170, 170, 176)})
    _status_bar(draw, dark_chrome)
    _chevron(draw, px(26), px(58), 6, (245, 245, 247))
    draw.text((px(SCREEN_W / 2), px(58)), "4 / 1,193", font=font(F_SEMI, 15),
              fill=(245, 245, 247), anchor="mm")
    r = px(12)
    cx, cy = px(SCREEN_W - 28), px(58)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=theme.accent)
    draw.text((cx, cy), "4", font=font(F_SEMI, 13), fill=(255, 255, 255), anchor="mm")

    # LIVE badge, inside the photo area
    fl = font(F_BOLD, 10)
    lw = draw.textlength("LIVE", font=fl)
    ly = photo_top + px(12)
    draw.rounded_rectangle((px(20), ly, px(20) + lw + px(16), ly + px(20)), px(10),
                           fill=(0, 0, 0, 120))
    draw.text((px(28), ly + px(3)), "LIVE", font=fl, fill=(255, 255, 255))

    hint = "Hold to play"
    fh = font(F_REG, 13)
    hw = draw.textlength(hint, font=fh)
    hy = photo_top + photo_h - px(48)
    draw.rounded_rectangle((px(SCREEN_W / 2) - hw / 2 - px(14), hy,
                            px(SCREEN_W / 2) + hw / 2 + px(14), hy + px(30)),
                           px(15), fill=(0, 0, 0, 130))
    draw.text((px(SCREEN_W / 2), hy + px(15)), hint, font=fh, fill=(245, 245, 247),
              anchor="mm")

    # filmstrip
    t_size = px(46)
    for i in range(5):
        x = px(20) + i * (t_size + px(8))
        tile = photo_tile((t_size, t_size), i + 2, LIGHT)
        img.paste(tile, (x, px(694)), rounded_mask((t_size, t_size), px(6)))
    return img


def screen_crop(theme: Theme) -> Image.Image:
    """Crop screen with rule-of-thirds guides and the aspect-ratio rail."""
    img = Image.new("RGBA", (px(SCREEN_W), px(SCREEN_H)), (12, 12, 14, 255))
    draw = ImageDraw.Draw(img)

    dark_chrome = Theme(**{**theme.__dict__, "screen_text": (245, 245, 247)})
    _status_bar(draw, dark_chrome)
    draw.text((px(22), px(50)), "Cancel", font=font(F_REG, 15), fill=(200, 200, 206))
    fd = font(F_SEMI, 15)
    draw.text((px(SCREEN_W - 22) - draw.textlength("Done", font=fd), px(50)), "Done",
              font=fd, fill=theme.accent)

    # image + square crop window
    top, side = px(150), px(24)
    box = px(SCREEN_W) - side * 2
    photo = photo_tile((box, box), 7, LIGHT)
    img.paste(photo, (side, top))
    draw.rectangle((side, top, side + box, top + box), outline=(255, 255, 255, 225),
                   width=max(1, px(1.5)))
    for i in (1, 2):
        o = box * i // 3
        draw.line((side + o, top, side + o, top + box), fill=(255, 255, 255, 90), width=max(1, S))
        draw.line((side, top + o, side + box, top + o), fill=(255, 255, 255, 90), width=max(1, S))
    # corner handles
    hl, hw = px(20), max(2, px(3))
    for cx, cy, dx, dy in ((side, top, 1, 1), (side + box, top, -1, 1),
                           (side, top + box, 1, -1), (side + box, top + box, -1, -1)):
        draw.line((cx, cy, cx + hl * dx, cy), fill=(255, 255, 255), width=hw)
        draw.line((cx, cy, cx, cy + hl * dy), fill=(255, 255, 255), width=hw)

    # aspect rail
    ratios = ["Free", "1:1", "3:4", "16:9"]
    active = 1
    fr = font(F_REG, 14)
    widths = [draw.textlength(r, font=fr) + px(40) for r in ratios]
    x = px(SCREEN_W) / 2 - sum(widths) / 2
    y = px(600)
    for i, (r, w) in enumerate(zip(ratios, widths)):
        if i == active:
            draw.rounded_rectangle((x, y, x + w, y + px(34)), px(17), fill=(255, 255, 255, 30))
        draw.text((x + w / 2, y + px(17)), r, font=fr,
                  fill=(255, 255, 255) if i == active else (150, 150, 156), anchor="mm")
        x += w

    # rotate / reset row
    fa = font(F_REG, 13)
    draw.text((px(SCREEN_W) / 2, px(672)), "Rotate  ·  Reset", font=fa,
              fill=(120, 120, 126), anchor="mm")
    return img


# --------------------------------------------------------------------------- #
# Phone frame
# --------------------------------------------------------------------------- #

def phone(screen: Image.Image, theme: Theme, scale: float = 1.0) -> Image.Image:
    """Wrap a screen render in a thin bezel with rounded corners."""
    bezel = px(9)
    radius = px(40)
    w, h = screen.size
    if scale != 1.0:
        w, h = int(w * scale), int(h * scale)
        screen = screen.resize((w, h), Image.LANCZOS)
        bezel = int(bezel * scale)
        radius = int(radius * scale)

    out = Image.new("RGBA", (w + bezel * 2, h + bezel * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((0, 0, out.width - 1, out.height - 1), radius + bezel,
                        fill=theme.bezel + (255,))
    inner_r = max(px(4), radius - int(bezel * 0.4))
    out.paste(screen, (bezel, bezel), rounded_mask(screen.size, inner_r))

    # subtle top highlight on the bezel
    hl = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).rounded_rectangle((0, 0, out.width - 1, out.height - 1),
                                         radius + bezel, outline=(255, 255, 255, 34),
                                         width=max(1, px(1)))
    out.alpha_composite(hl)
    return out


# --------------------------------------------------------------------------- #
# Assets
# --------------------------------------------------------------------------- #

def make_hero(theme: Theme) -> Image.Image:
    W, H = px(1200), px(560)
    img = Image.new("RGBA", (W, H), theme.bg + (255,))

    # a barely-there accent wash in the top-right, Apple-style
    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wash)
    a = 26 if theme.name == "light" else 34
    wd.ellipse((W - px(760), -px(340), W + px(180), px(500)), fill=theme.accent + (a,))
    img.alpha_composite(wash.filter(ImageFilter.GaussianBlur(px(90))))

    d = ImageDraw.Draw(img)
    left = px(72)

    tracked_text(d, (left, px(138)), "ANDROID LIBRARY", font(F_SEMI, 13), theme.accent,
                 tracking=px(3))

    d.text((left - px(4), px(170)), "PhotoChoice", font=font(F_LIGHT, 76), fill=theme.text)

    d.text((left, px(300)), "Grid, albums, full-screen preview,", font=font(F_REG, 22),
           fill=theme.muted)
    d.text((left, px(336)), "crop, compression — and Motion Photo.", font=font(F_REG, 22),
           fill=theme.muted)

    # meta chips
    x = left
    for chip in ("Kotlin", "minSdk 29", "Apache-2.0"):
        f = font(F_SEMI, 13)
        w = d.textlength(chip, font=f) + px(26)
        d.rounded_rectangle((x, px(408), x + w, px(408) + px(30)), px(15),
                            outline=theme.hairline, width=max(1, px(1)))
        d.text((x + w / 2, px(408) + px(15)), chip, font=f, fill=theme.muted, anchor="mm")
        x += w + px(10)

    # Three phones. The grid sits front and centre; preview and crop tuck in behind
    # it and get a light veil so they read as a back layer rather than competing.
    for screen_fn, sc, x0, y0, blur, opacity, veil in (
        (screen_preview, 0.44, px(670), px(128), px(42), 40, 0.12),
        (screen_crop, 0.44, px(945), px(128), px(42), 40, 0.12),
        (screen_grid, 0.50, px(796), px(80), px(58), 58, 0.0),
    ):
        p = phone(screen_fn(theme), theme, scale=sc)
        if veil:
            wash_layer = Image.new("RGBA", p.size, theme.bg + (255,))
            wash_layer.putalpha(p.getchannel("A"))
            p = Image.blend(p, wash_layer, veil)
        paste_shadowed(img, p, (x0, y0), radius=int(px(48) * sc), blur=blur,
                       opacity=opacity, dy=px(14))
    return img


def make_poster(theme: Theme) -> Image.Image:
    W, H = px(800), px(450)
    img = Image.new("RGBA", (W, H), theme.bg + (255,))

    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(wash).ellipse((px(300), -px(180), px(980), px(420)),
                                 fill=theme.accent + (24 if theme.name == "light" else 32,))
    img.alpha_composite(wash.filter(ImageFilter.GaussianBlur(px(80))))

    d = ImageDraw.Draw(img)

    # phone on the left, vertically centred
    scale = 0.46
    p = phone(screen_grid(theme), theme, scale=scale)
    px0, py0 = px(88), (H - p.height) // 2
    paste_shadowed(img, p, (px0, py0), radius=int(px(48) * scale), blur=px(50),
                   opacity=54, dy=px(14))

    # play button centred on the phone
    r = px(40)
    cx, cy = px0 + p.width // 2, py0 + p.height // 2
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((cx - r - px(8), cy - r - px(8), cx + r + px(8), cy + r + px(8)),
                                 fill=(0, 0, 0, 90))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(px(18))))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 255, 255, 246))
    t = px(16)
    d.polygon([(cx - t + px(5), cy - t - px(2)), (cx - t + px(5), cy + t + px(2)),
               (cx + t + px(8), cy)], fill=(29, 29, 31))

    # copy on the right
    tx = px(372)
    tracked_text(d, (tx, px(150)), "WALKTHROUGH", font(F_SEMI, 13), theme.accent,
                 tracking=px(3))
    d.text((tx - px(3), px(178)), "See it in motion", font=font(F_LIGHT, 40), fill=theme.text)
    d.text((tx, px(250)), "Grid, albums, preview, Motion Photo,", font=font(F_REG, 17),
           fill=theme.muted)
    d.text((tx, px(278)), "crop and compression — end to end.",
           font=font(F_REG, 17), fill=theme.muted)

    # play affordance in the copy column (glyph drawn, not typed: Segoe UI has no U+25B6)
    label = f"Play  ·  {demo_duration_label()}"
    fb = font(F_SEMI, 14)
    bw = int(d.textlength(label, font=fb)) + px(72)
    bh, by = px(38), px(322)
    d.rounded_rectangle((tx, by, tx + bw, by + bh), bh // 2, fill=theme.accent)
    gx, gy, g = tx + px(26), by + bh // 2, px(6)
    d.polygon([(gx - g, gy - g - px(1)), (gx - g, gy + g + px(1)), (gx + g + px(2), gy)],
              fill=(255, 255, 255))
    d.text((tx + px(46), gy), label, font=fb, fill=(255, 255, 255), anchor="lm")
    return img


def make_architecture(theme: Theme) -> Image.Image:
    """Wide banner for the article's architecture section.

    Left half is a vertical host-app → builder → contract/activity → fragments
    flow drawn as labelled capsules; right half is the routed-to grid phone.
    Used once, in place of a second hero, so the article never repeats art.
    """
    W, H = px(1200), px(560)
    img = Image.new("RGBA", (W, H), theme.bg + (255,))
    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(wash).ellipse((-px(220), -px(280), px(720), px(540)),
                                 fill=theme.accent + (22 if theme.name == "light" else 30,))
    img.alpha_composite(wash.filter(ImageFilter.GaussianBlur(px(90))))
    d = ImageDraw.Draw(img)

    tracked_text(d, (px(56), px(60)), "ARCHITECTURE", font(F_SEMI, 13), theme.accent,
                 tracking=px(3))
    d.text((px(54), px(90)), "Builder → Contract → Activity", font=font(F_LIGHT, 32),
           fill=theme.text)

    # labelled flow capsules, left column, top to bottom
    row_x = px(56)
    f_label = font(F_SEMI, 14)
    f_sub = font(F_REG, 12)
    bands = (
        ("Host App", "declares + requests permission"),
        ("Builder",  "config → PhotoChoiceConfig"),
        ("Contract", "Intent extra, setResult"),
        ("Activity", "grid · preview · crop"),
        ("Paging 3", "MediaStore keyset"),
    )
    y = px(180)
    h = px(38)
    for title, sub in bands:
        d.rounded_rectangle((row_x, y, row_x + px(336), y + h), px(12),
                            fill=theme.surface, outline=theme.hairline,
                            width=max(1, px(1)))
        d.text((row_x + px(16), y + h // 2 - px(2)), title, font=f_label,
               fill=theme.text, anchor="lm")
        d.text((row_x + px(176), y + h // 2 - px(2)), sub, font=f_sub,
               fill=theme.muted, anchor="lm")
        if title != "Paging 3":
            arrow_y = y + h + px(2)
            d.line((row_x + px(168), arrow_y, row_x + px(168), arrow_y + px(12)),
                   fill=theme.accent, width=max(1, px(1.5)))
        y += h + px(16)

    p = phone(screen_grid(theme), theme, scale=0.58)
    paste_shadowed(img, p, (px(700), (H - p.height) // 2),
                   radius=int(px(48) * 0.58), blur=px(52), opacity=52, dy=px(12))
    return img


def make_live_preview(theme: Theme) -> Image.Image:
    """Two phones side by side: grid with a LIVE badge vs. the long-press preview.

    Illustrates the "badge on the grid, clip plays in preview" promise in one
    frame, so the article does not need a second copy of the poster art.
    """
    W, H = px(780), px(560)
    img = Image.new("RGBA", (W, H), theme.bg + (255,))
    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(wash).ellipse((-px(160), -px(220), px(400), px(500)),
                                 fill=theme.accent + (20 if theme.name == "light" else 28,))
    img.alpha_composite(wash.filter(ImageFilter.GaussianBlur(px(70))))
    d = ImageDraw.Draw(img)

    scale = 0.50
    pg = phone(screen_grid(theme), theme, scale=scale)
    pp = phone(screen_preview(theme), theme, scale=scale)
    gy = (H - pg.height) // 2
    paste_shadowed(img, pg, (px(50), gy), radius=int(px(48) * scale), blur=px(44),
                   opacity=50, dy=px(10))
    paste_shadowed(img, pp, (px(580), gy), radius=int(px(48) * scale), blur=px(44),
                   opacity=50, dy=px(10))

    cx = W // 2  # text block centred over the whole frame
    tracked_text(d, (cx - px(56), px(150)), "LIVE BADGE", font(F_SEMI, 12), theme.accent,
                 tracking=px(3))
    d.text((cx - px(58), px(176)), "Grid to preview", font=font(F_LIGHT, 24), fill=theme.text)
    d.text((cx - px(58), px(214)), "Long-press to play", font=font(F_REG, 13), fill=theme.muted)
    return img


def make_qr() -> Image.Image:
    """One QR for both colour schemes.

    A QR is always dark-on-white — inverting it costs scan reliability on some
    readers — so a "dark variant" would only differ in its frame. A white card
    with a soft shadow already reads correctly on both a near-white and a
    near-black page, which is exactly how every OS renders one.
    """
    # Error correction M, not H: the URL is 70 chars, and H pushes the symbol to
    # ~45 modules — at the 200px the README renders it that leaves ~4px per module,
    # which is where phone scanners start to struggle. M keeps it comfortably coarser.
    qr = qrcode.QRCode(error_correction=ERROR_CORRECT_M, box_size=px(12), border=0)
    qr.add_data(APK_URL)
    qr.make(fit=True)
    code = qr.make_image(
        image_factory=StyledPilImage,
        module_drawer=RoundedModuleDrawer(radius_ratio=1.0),
        fill_color=(20, 20, 22),
        back_color=(255, 255, 255),
    ).convert("RGBA")

    quiet = px(34)          # >= 4 modules of quiet zone, per the QR spec
    card_pad = px(26)
    inner = code.width + quiet * 2
    size = inner + card_pad * 2

    card = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle((0, 0, size - 1, size - 1), px(44),
                                           fill=(255, 255, 255, 255))
    sh, pad = shadow((size, size), px(44), px(22), 46)
    canvas = Image.new("RGBA", (size + pad * 2, size + pad * 2), (0, 0, 0, 0))
    canvas.alpha_composite(sh, (0, px(8)))
    canvas.alpha_composite(card, (pad, pad))
    canvas.alpha_composite(code, (pad + card_pad + quiet, pad + card_pad + quiet))

    # Accent corner ticks — the one flourish. Kept on the card padding, clear of
    # the quiet zone so they can never interfere with decoding.
    d = ImageDraw.Draw(canvas)
    o, ln, w = pad + px(16), px(30), max(2, px(4))
    far = pad + size - px(16)
    for cx, cy, dx, dy in ((o, o, 1, 1), (far, o, -1, 1), (o, far, 1, -1), (far, far, -1, -1)):
        d.line((cx, cy, cx + ln * dx, cy), fill=LIGHT.accent, width=w)
        d.line((cx, cy, cx, cy + ln * dy), fill=LIGHT.accent, width=w)

    return canvas


# --------------------------------------------------------------------------- #

def save(img: Image.Image, name: str) -> None:
    path = os.path.join(DOCS, name)
    img.save(path, "PNG", optimize=True)
    print(f"{name:32s} {img.size[0]}x{img.size[1]}  {os.path.getsize(path) / 1024:7.1f} KB")


def main() -> None:
    for theme, suffix in ((LIGHT, "light"), (DARK, "dark")):
        save(make_hero(theme), f"hero-{suffix}.png")
        save(make_poster(theme), f"demo-poster{'' if suffix == 'light' else '-dark'}.png")
        save(make_architecture(theme), f"arch-{suffix}.png")
        save(make_live_preview(theme), f"live-preview-{suffix}.png")
    save(make_qr(), "qr-sample-apk.png")


if __name__ == "__main__":
    main()
