"""Render the README walkthrough video for PhotoChoice.

This is an illustrated walkthrough, not a screen recording: it reuses the UI
drawing primitives from ``make_assets.py`` so the video, the header image and the
poster all read as one design system — and so no real photo library ever ends up
in the repository.

Frames are piped straight into ffmpeg as raw RGB; nothing touches the disk except
the finished mp4.

Run:  python docs/assets/make_demo_video.py
Deps: pillow, ffmpeg on PATH (or FFMPEG env var)
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import make_assets as MA

MA.S = 2  # phone screens render at 2x, then downsample into the frame

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.dirname(HERE)
OUT = os.path.join(DOCS, "demo.mp4")

W, H, FPS = 1280, 720, 30
THEME = MA.LIGHT

PHONE_SCALE = 0.40
PHONE_X = 150
TEXT_X = 570

FADE = 0.35          # cross-fade between scenes
TEXT_FADE = 0.30     # copy fade in / out


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #

def vf(path: str, size: int) -> ImageFont.FreeTypeFont:
    """Frame-level font: unscaled, unlike make_assets.font()."""
    return ImageFont.truetype(path, size)


def ease(t: float) -> float:
    """Cubic in-out — the same curve UI animations use."""
    t = max(0.0, min(1.0, t))
    return 4 * t * t * t if t < 0.5 else 1 - pow(-2 * t + 2, 3) / 2


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def fade_alpha(local_t: float, duration: float, fade: float = TEXT_FADE) -> float:
    """Ramp 0→1 at the start and 1→0 at the end of a scene."""
    if local_t < fade:
        return local_t / fade
    if local_t > duration - fade:
        return max(0.0, (duration - local_t) / fade)
    return 1.0


def with_alpha(img: Image.Image, alpha: float) -> Image.Image:
    if alpha >= 1.0:
        return img
    out = img.copy()
    a = out.getchannel("A").point(lambda v: int(v * max(0.0, alpha)))
    out.putalpha(a)
    return out


# --------------------------------------------------------------------------- #
# Phone screen renderers
# --------------------------------------------------------------------------- #

SW, SH = MA.SCREEN_W, MA.SCREEN_H          # 360 x 780 logical points
COLS, GAP = 3, 3
GRID_TOP = MA.px(84)
BAR_H = MA.px(64)
CELL = (MA.px(SW) - MA.px(GAP) * (COLS - 1)) // COLS
ROW = CELL + MA.px(GAP)

# Cells picked during the "Select" scene, in order. Indices are chosen so every
# badge lands inside the visible window once the grid has scrolled to SCROLL_END —
# rows 3..6 are the only fully-unobstructed ones there.
PICKS = [(10, 1), (15, 2), (19, 3)]
LIVE_CELL = 12

_tape_cache: dict[int, Image.Image] = {}


def photo_tape(rows: int = 14) -> Image.Image:
    """Pre-render the scrolling grid once; each frame just crops a window."""
    if rows in _tape_cache:
        return _tape_cache[rows]

    tape = Image.new("RGBA", (MA.px(SW), ROW * rows), THEME.screen_bg + (255,))
    d = ImageDraw.Draw(tape)
    n = 0
    for row in range(rows):
        for col in range(COLS):
            x, y = col * ROW, row * ROW
            if n == 0:
                # camera entry tile
                d.rectangle((x, y, x + CELL, y + CELL), fill=THEME.screen_tile)
                cx, cy = x + CELL // 2, y + CELL // 2
                bw, bh = MA.px(26), MA.px(20)
                d.rounded_rectangle((cx - bw // 2, cy - bh // 2 + MA.px(2),
                                     cx + bw // 2, cy + bh // 2 + MA.px(2)),
                                    MA.px(4), outline=THEME.screen_muted, width=MA.px(1.5))
                d.rounded_rectangle((cx - MA.px(5), cy - bh // 2 - MA.px(2),
                                     cx + MA.px(5), cy - bh // 2 + MA.px(3)),
                                    MA.px(2), fill=THEME.screen_muted)
                d.ellipse((cx - MA.px(5), cy - MA.px(3), cx + MA.px(5), cy + MA.px(7)),
                          outline=THEME.screen_muted, width=MA.px(1.5))
            else:
                tape.paste(MA.photo_tile((CELL, CELL), n - 1, THEME), (x, y))
            n += 1

    _tape_cache[rows] = tape
    return tape


def _status_bar(d: ImageDraw.ImageDraw, colour) -> None:
    theme = MA.Theme(**{**THEME.__dict__, "screen_text": colour})
    MA._status_bar(d, theme)


def _grid_chrome(img: Image.Image, done: int, dim: float = 0.0) -> None:
    """Top bar + bottom action bar, drawn over the scrolled grid."""
    d = ImageDraw.Draw(img)

    d.rectangle((0, 0, MA.px(SW), GRID_TOP), fill=THEME.screen_bg)
    _status_bar(d, THEME.screen_text)
    MA._chevron(d, MA.px(26), MA.px(58), 6, THEME.screen_text)
    f = MA.font(MA.F_SEMI, 16)
    tw = d.textlength("All Photos", font=f)
    d.text((MA.px(SW / 2) - tw / 2 - MA.px(7), MA.px(50)), "All Photos", font=f,
           fill=THEME.screen_text)
    MA._chevron(d, int(MA.px(SW / 2) + tw / 2 + MA.px(6)), MA.px(57), 4,
                THEME.screen_muted, direction="down")

    y0 = MA.px(SH) - BAR_H
    d.rectangle((0, y0, MA.px(SW), MA.px(SH)), fill=THEME.screen_bg)
    d.line((0, y0, MA.px(SW), y0), fill=THEME.hairline, width=MA.S)
    d.text((MA.px(22), y0 + MA.px(18)), "Preview", font=MA.font(MA.F_REG, 14),
           fill=THEME.screen_muted if done else (200, 200, 206))

    bw, bh = MA.px(96), MA.px(34)
    bx = MA.px(SW) - MA.px(22) - bw
    fill = THEME.accent if done else (222, 222, 228)
    d.rounded_rectangle((bx, y0 + MA.px(12), bx + bw, y0 + MA.px(12) + bh), bh // 2, fill=fill)
    label = f"Done ({done})" if done else "Done"
    d.text((bx + bw / 2, y0 + MA.px(12) + bh / 2), label, font=MA.font(MA.F_SEMI, 14),
           fill=(255, 255, 255) if done else (150, 150, 156), anchor="mm")

    if dim > 0:
        veil = Image.new("RGBA", img.size, (0, 0, 0, int(150 * dim)))
        img.alpha_composite(veil)


def screen_grid_scrolled(scroll: int, picked: dict[int, int], done: int,
                         pop: tuple[int, float] | None = None) -> Image.Image:
    """One grid frame: tape window + selection badges + chrome."""
    img = Image.new("RGBA", (MA.px(SW), MA.px(SH)), THEME.screen_bg + (255,))
    tape = photo_tape()

    window_h = MA.px(SH) - GRID_TOP
    scroll = max(0, min(scroll, tape.height - window_h))
    img.paste(tape.crop((0, scroll, tape.width, scroll + window_h)), (0, GRID_TOP))

    d = ImageDraw.Draw(img)
    for idx in range(1, COLS * 14):
        row, col = divmod(idx, COLS)
        y = GRID_TOP + row * ROW - scroll
        if y + CELL < GRID_TOP or y > MA.px(SH):
            continue
        x = col * ROW

        order = picked.get(idx)
        r = MA.px(11)
        bx, by = x + CELL - MA.px(9) - r, y + MA.px(9) + r

        if order:
            grow = 1.0
            if pop and pop[0] == idx:
                # brief overshoot as the badge lands
                p = pop[1]
                grow = 1.0 + 0.45 * (1 - p) if p > 0 else 1.45
            d.rectangle((x, y, x + CELL - 1, y + CELL - 1), outline=THEME.accent, width=MA.px(2))
            rr = int(r * grow)
            d.ellipse((bx - rr, by - rr, bx + rr, by + rr), fill=THEME.accent)
            d.text((bx, by), str(order), font=MA.font(MA.F_SEMI, 12),
                   fill=(255, 255, 255), anchor="mm")
        else:
            d.ellipse((bx - r, by - r, bx + r, by + r), outline=(255, 255, 255, 235),
                      width=MA.px(1.5))

        if idx == LIVE_CELL:
            fl = MA.font(MA.F_BOLD, 9)
            lw = d.textlength("LIVE", font=fl)
            lx, ly = x + MA.px(8), y + CELL - MA.px(22)
            d.rounded_rectangle((lx, ly, lx + lw + MA.px(12), ly + MA.px(15)), MA.px(7),
                                fill=(0, 0, 0, 105))
            d.text((lx + MA.px(6), ly + MA.px(2)), "LIVE", font=fl, fill=(255, 255, 255))

    _grid_chrome(img, done)
    return img


def screen_preview(phase: float = 0.0, holding: float = 0.0) -> Image.Image:
    """Preview screen. `holding` drives the press ring and the 'playing' state."""
    img = Image.new("RGBA", (MA.px(SW), MA.px(SH)), (12, 12, 14, 255))
    d = ImageDraw.Draw(img)

    photo_top, photo_h = MA.px(100), MA.px(560)
    # Shifting the gradient stand-in sells "the clip is playing" without a real video.
    c0, c1 = MA.SWATCHES[4]
    if holding > 0:
        k = 0.35 * phase
        c0 = tuple(int(lerp(a, b, k)) for a, b in zip(c0, MA.SWATCHES[7][0]))
        c1 = tuple(int(lerp(a, b, k)) for a, b in zip(c1, MA.SWATCHES[7][1]))
    photo = MA.desaturate(MA.gradient((MA.px(SW), photo_h), c0, c1), 0.30).convert("RGBA")
    img.paste(photo, (0, photo_top))

    _status_bar(d, (245, 245, 247))
    MA._chevron(d, MA.px(26), MA.px(58), 6, (245, 245, 247))
    d.text((MA.px(SW / 2), MA.px(58)), "4 / 1,193", font=MA.font(MA.F_SEMI, 15),
           fill=(245, 245, 247), anchor="mm")
    r = MA.px(12)
    cx, cy = MA.px(SW - 28), MA.px(58)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=THEME.accent)
    d.text((cx, cy), "4", font=MA.font(MA.F_SEMI, 13), fill=(255, 255, 255), anchor="mm")

    fl = MA.font(MA.F_BOLD, 10)
    lw = d.textlength("LIVE", font=fl)
    ly = photo_top + MA.px(12)
    pulse = int(120 + 90 * holding)
    d.rounded_rectangle((MA.px(20), ly, MA.px(20) + lw + MA.px(16), ly + MA.px(20)),
                        MA.px(10), fill=(THEME.accent + (pulse,)) if holding else (0, 0, 0, 120))
    d.text((MA.px(28), ly + MA.px(3)), "LIVE", font=fl, fill=(255, 255, 255))

    if holding > 0:
        # finger press ring
        pcx, pcy = MA.px(SW // 2), photo_top + photo_h // 2
        for rad, alpha in ((MA.px(34), 105), (MA.px(52 + 20 * phase), int(70 * (1 - phase)))):
            ring = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(ring).ellipse((pcx - rad, pcy - rad, pcx + rad, pcy + rad),
                                         fill=(255, 255, 255, alpha))
            img.alpha_composite(ring)
        d = ImageDraw.Draw(img)

    hint = "Playing…" if holding else "Hold to play"
    fh = MA.font(MA.F_REG, 13)
    hw = d.textlength(hint, font=fh)
    hy = photo_top + photo_h - MA.px(48)
    d.rounded_rectangle((MA.px(SW / 2) - hw / 2 - MA.px(14), hy,
                         MA.px(SW / 2) + hw / 2 + MA.px(14), hy + MA.px(30)),
                        MA.px(15), fill=(0, 0, 0, 130))
    d.text((MA.px(SW / 2), hy + MA.px(15)), hint, font=fh, fill=(245, 245, 247), anchor="mm")

    t_size = MA.px(46)
    for i in range(5):
        x = MA.px(20) + i * (t_size + MA.px(8))
        img.paste(MA.photo_tile((t_size, t_size), i + 2, MA.LIGHT), (x, MA.px(694)),
                  MA.rounded_mask((t_size, t_size), MA.px(6)))
    return img


def screen_crop(t: float) -> Image.Image:
    """Crop screen; `t` (0→1) drives the frame settling from free to 1:1."""
    img = Image.new("RGBA", (MA.px(SW), MA.px(SH)), (12, 12, 14, 255))
    d = ImageDraw.Draw(img)

    _status_bar(d, (245, 245, 247))
    d.text((MA.px(22), MA.px(50)), "Cancel", font=MA.font(MA.F_REG, 15), fill=(200, 200, 206))
    fd = MA.font(MA.F_SEMI, 15)
    d.text((MA.px(SW - 22) - d.textlength("Done", font=fd), MA.px(50)), "Done",
           font=fd, fill=THEME.accent)

    side, top = MA.px(24), MA.px(150)
    full = MA.px(SW) - side * 2
    img.paste(MA.photo_tile((full, full), 7, MA.LIGHT), (side, top))

    e = ease(t)
    inset = int(lerp(0, MA.px(30), e))
    x0, y0 = side + inset, top + inset
    x1, y1 = side + full - inset, top + full - inset

    shade = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    sd.rectangle((side, top, side + full, top + full), fill=(0, 0, 0, int(110 * e)))
    sd.rectangle((x0, y0, x1, y1), fill=(0, 0, 0, 0))
    img.alpha_composite(shade)

    d = ImageDraw.Draw(img)
    d.rectangle((x0, y0, x1, y1), outline=(255, 255, 255, 225), width=MA.px(1.5))
    for i in (1, 2):
        ox, oy = (x1 - x0) * i // 3, (y1 - y0) * i // 3
        d.line((x0 + ox, y0, x0 + ox, y1), fill=(255, 255, 255, 90), width=MA.S)
        d.line((x0, y0 + oy, x1, y0 + oy), fill=(255, 255, 255, 90), width=MA.S)
    hl, hw = MA.px(20), MA.px(3)
    for cx, cy, dx, dy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        d.line((cx, cy, cx + hl * dx, cy), fill=(255, 255, 255), width=hw)
        d.line((cx, cy, cx, cy + hl * dy), fill=(255, 255, 255), width=hw)

    ratios, active = ["Free", "1:1", "3:4", "16:9"], 1 if e > 0.5 else 0
    fr = MA.font(MA.F_REG, 14)
    widths = [d.textlength(r, font=fr) + MA.px(40) for r in ratios]
    x = MA.px(SW) / 2 - sum(widths) / 2
    y = MA.px(600)
    for i, (r, w) in enumerate(zip(ratios, widths)):
        if i == active:
            d.rounded_rectangle((x, y, x + w, y + MA.px(34)), MA.px(17), fill=(255, 255, 255, 30))
        d.text((x + w / 2, y + MA.px(17)), r, font=fr,
               fill=(255, 255, 255) if i == active else (150, 150, 156), anchor="mm")
        x += w
    d.text((MA.px(SW) / 2, MA.px(672)), "Rotate  ·  Reset", font=MA.font(MA.F_REG, 13),
           fill=(120, 120, 126), anchor="mm")
    return img


def screen_compress(t: float) -> Image.Image:
    """Grid blurred and dimmed behind a compression progress sheet."""
    img = screen_grid_scrolled(SCROLL_END, dict(PICKS), len(PICKS))
    img = img.filter(ImageFilter.GaussianBlur(MA.px(9)))
    img.alpha_composite(Image.new("RGBA", img.size, (14, 14, 18, 165)))
    d = ImageDraw.Draw(img)

    done = t > 0.62
    cx, cy, r = MA.px(SW) // 2, MA.px(292), MA.px(46)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(255, 255, 255, 55), width=MA.px(5))
    sweep = min(1.0, t / 0.62)
    if sweep > 0:
        d.arc((cx - r, cy - r, cx + r, cy + r), -90, -90 + int(360 * ease(sweep)),
              fill=THEME.accent, width=MA.px(5))
    if done:
        d.line([(cx - MA.px(16), cy), (cx - MA.px(5), cy + MA.px(12)),
                (cx + MA.px(17), cy - MA.px(13))],
               fill=THEME.accent, width=MA.px(5), joint="curve")
    else:
        d.text((cx, cy), f"{int(sweep * 100)}%", font=MA.font(MA.F_SEMI, 18),
               fill=(255, 255, 255), anchor="mm")

    title = "Compressed 3 of 3" if done else "Compressing…"
    d.text((cx, MA.px(384)), title, font=MA.font(MA.F_SEMI, 17), fill=(255, 255, 255), anchor="mm")
    if done:
        d.text((cx, MA.px(424)), "5.8 MB  →  1.2 MB", font=MA.font(MA.F_REG, 16),
               fill=(215, 215, 221), anchor="mm")
        d.text((cx, MA.px(456)), "1280 px · quality 80", font=MA.font(MA.F_REG, 13),
               fill=(155, 155, 161), anchor="mm")
    return img


# --------------------------------------------------------------------------- #
# Scenes
# --------------------------------------------------------------------------- #

SCROLL_END = ROW * 2 + MA.px(40)


@dataclass
class Scene:
    kind: str
    duration: float
    label: str = ""
    title: str = ""
    body: list = field(default_factory=list)


SCENES = [
    Scene("intro", 2.6),
    Scene("browse", 4.8, "01  BROWSE", "Every album, one grid",
          ["MediaStore buckets, square thumbnails,", "and Paging 3 underneath."]),
    Scene("select", 5.4, "02  SELECT", "Order-aware selection",
          ["Badges show the pick order.", "One to nine, single or multi."]),
    Scene("preview", 4.2, "03  PREVIEW", "Full-screen, swipeable",
          ["Inline video playback, and chrome", "that gets out of the way."]),
    Scene("motion", 5.0, "04  MOTION PHOTO", "Hold to bring it alive",
          ["The embedded clip plays on long-press", "and stops when you let go."]),
    Scene("crop", 4.8, "05  CROP", "Ratios that stay put",
          ["Free, 1:1, 3:4, 9:16, 16:9 —", "single select, image mode."]),
    Scene("compress", 4.4, "06  COMPRESS", "Sized on the way out",
          ["JPEG resize with a size target,", "and small images left untouched."]),
    Scene("outro", 3.2),
]

TOTAL = sum(s.duration for s in SCENES)


def phone_screen(scene: Scene, t: float) -> Image.Image:
    """The 720x1560 screen for a scene at local time `t`."""
    k = scene.kind
    if k in ("intro", "browse"):
        if k == "intro":
            return screen_grid_scrolled(0, {}, 0)
        return screen_grid_scrolled(int(SCROLL_END * ease(t / (scene.duration - 0.6))), {}, 0)

    if k == "select":
        picked, done, pop = {}, 0, None
        for i, (cell, order) in enumerate(PICKS):
            at = 0.9 + i * 1.15
            if t >= at:
                picked[cell] = order
                done = order
                if t - at < 0.22:
                    pop = (cell, (t - at) / 0.22)
        return screen_grid_scrolled(SCROLL_END, picked, done, pop)

    if k == "preview":
        return screen_preview()

    if k == "motion":
        hold_from, hold_to = 1.1, scene.duration - 0.7
        if hold_from <= t <= hold_to:
            span = hold_to - hold_from
            return screen_preview(phase=((t - hold_from) / span * 2) % 1.0, holding=1.0)
        return screen_preview()

    if k == "crop":
        return screen_crop(min(1.0, max(0.0, (t - 0.5) / 1.6)))

    if k == "compress":
        return screen_compress(min(1.0, t / (scene.duration - 0.8)))

    return screen_grid_scrolled(SCROLL_END, dict(PICKS), len(PICKS))


def phone_layer(scene: Scene, t: float) -> Image.Image:
    """Screen wrapped in the bezel and scaled into frame space."""
    return MA.phone(phone_screen(scene, t), THEME, scale=PHONE_SCALE)


_tails: dict[int, Image.Image] = {}


def scene_tail(idx: int) -> Image.Image:
    """Last frame of a scene, kept around to cross-fade into the next one."""
    if idx not in _tails:
        s = SCENES[idx]
        _tails[idx] = phone_layer(s, s.duration - 1.0 / FPS)
    return _tails[idx]


# --------------------------------------------------------------------------- #
# Frame composition
# --------------------------------------------------------------------------- #

_background: Image.Image | None = None


def background() -> Image.Image:
    global _background
    if _background is None:
        bg = Image.new("RGBA", (W, H), THEME.bg + (255,))
        wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(wash).ellipse((W - 780, -320, W + 200, 460), fill=THEME.accent + (30,))
        bg.alpha_composite(wash.filter(ImageFilter.GaussianBlur(96)))
        _background = bg
    return _background


def draw_copy(frame: Image.Image, scene: Scene, alpha: float, slide: float = 0.0) -> None:
    if alpha <= 0.01 or not scene.title:
        return
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x = int(TEXT_X + slide)

    MA.tracked_text(d, (x, 232), scene.label, vf(MA.F_SEMI, 13), THEME.accent, tracking=3)
    d.text((x - 2, 262), scene.title, font=vf(MA.F_LIGHT, 46), fill=THEME.text)
    for i, line in enumerate(scene.body):
        d.text((x, 352 + i * 31), line, font=vf(MA.F_REG, 18), fill=THEME.muted)

    frame.alpha_composite(with_alpha(layer, alpha))


def draw_intro(frame: Image.Image, t: float, duration: float) -> None:
    a = fade_alpha(t, duration, 0.5)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    MA.tracked_text(d, (TEXT_X, 250), "ANDROID LIBRARY", vf(MA.F_SEMI, 13), THEME.accent, tracking=3)
    d.text((TEXT_X - 3, 280), "PhotoChoice", font=vf(MA.F_LIGHT, 62), fill=THEME.text)
    d.text((TEXT_X, 382), "A photo picker that behaves", font=vf(MA.F_REG, 19), fill=THEME.muted)
    d.text((TEXT_X, 410), "like the system one.", font=vf(MA.F_REG, 19), fill=THEME.muted)
    frame.alpha_composite(with_alpha(layer, a))


def draw_outro(frame: Image.Image, t: float, duration: float) -> None:
    a = fade_alpha(t, duration, 0.5)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((W // 2, 296), "PhotoChoice", font=vf(MA.F_LIGHT, 66), fill=THEME.text, anchor="mm")
    d.text((W // 2, 368), "Grid · Albums · Preview · Motion Photo · Crop · Compress",
           font=vf(MA.F_REG, 18), fill=THEME.muted, anchor="mm")
    d.text((W // 2, 420), "github.com/Hu12037102/photo_choice",
           font=vf(MA.F_SEMI, 18), fill=THEME.accent, anchor="mm")

    chips = ("Kotlin", "minSdk 29", "Apache-2.0")
    f = vf(MA.F_SEMI, 13)
    widths = [d.textlength(c, font=f) + 26 for c in chips]
    x = W / 2 - (sum(widths) + 10 * (len(chips) - 1)) / 2
    for chip, w in zip(chips, widths):
        d.rounded_rectangle((x, 468, x + w, 498), 15, outline=THEME.hairline, width=1)
        d.text((x + w / 2, 483), chip, font=f, fill=THEME.muted, anchor="mm")
        x += w + 10

    frame.alpha_composite(with_alpha(layer, a))


def progress_bar(frame: Image.Image, done: float) -> None:
    d = ImageDraw.Draw(frame)
    d.rectangle((0, H - 3, W, H), fill=THEME.hairline + (255,))
    d.rectangle((0, H - 3, int(W * done), H), fill=THEME.accent + (255,))


def compose(scene: Scene, idx: int, local_t: float, elapsed: float) -> Image.Image:
    frame = background().copy()

    phone = phone_layer(scene, local_t)

    # cross-fade the phone from the previous scene's last frame
    if local_t < FADE and idx > 0:
        old = scene_tail(idx - 1)
        if old.size == phone.size:
            phone = Image.blend(old, phone, ease(local_t / FADE))

    # intro: the phone eases up and in
    rise, alpha = 0, 1.0
    if scene.kind == "intro":
        e = ease(min(1.0, local_t / 0.9))
        rise, alpha = int(lerp(28, 0, e)), e
    elif scene.kind == "outro":
        e = ease(min(1.0, local_t / 0.7))
        rise, alpha = int(lerp(0, -24, e)), 1 - e

    y = (H - phone.height) // 2 + rise
    if alpha < 1.0:
        phone = with_alpha(phone, alpha)
    MA.paste_shadowed(frame, phone, (PHONE_X, y), radius=int(MA.px(48) * PHONE_SCALE),
                      blur=46, opacity=int(52 * max(alpha, 0.0)), dy=12)

    if scene.kind == "intro":
        draw_intro(frame, local_t, scene.duration)
    elif scene.kind == "outro":
        draw_outro(frame, local_t, scene.duration)
    else:
        a = fade_alpha(local_t, scene.duration)
        draw_copy(frame, scene, a, slide=lerp(18, 0, ease(min(1.0, local_t / TEXT_FADE))))

    progress_bar(frame, elapsed / TOTAL)
    return frame


# --------------------------------------------------------------------------- #

def ffmpeg_bin() -> str:
    for candidate in (os.environ.get("FFMPEG"), "ffmpeg", r"E:\ffmpeg\bin\ffmpeg.exe"):
        if candidate and (os.path.isfile(candidate) or shutil.which(candidate)):
            return candidate
    sys.exit("ffmpeg not found — set the FFMPEG environment variable")


def main() -> None:
    total_frames = int(TOTAL * FPS)
    print(f"rendering {total_frames} frames ({TOTAL:.1f}s @ {FPS}fps) -> {OUT}")

    proc = subprocess.Popen(
        [ffmpeg_bin(), "-y", "-loglevel", "error",
         "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-c:v", "libx264", "-preset", "slow", "-crf", "21",
         "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT],
        stdin=subprocess.PIPE,
    )

    frame_no = 0
    for idx, scene in enumerate(SCENES):
        n = int(scene.duration * FPS)
        for i in range(n):
            local_t = i / FPS
            frame = compose(scene, idx, local_t, frame_no / FPS)
            proc.stdin.write(frame.convert("RGB").tobytes())
            frame_no += 1
            if frame_no % 60 == 0:
                print(f"  {frame_no}/{total_frames}", flush=True)

    proc.stdin.close()
    if proc.wait() != 0:
        sys.exit("ffmpeg failed")
    print(f"done — {os.path.getsize(OUT) / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
