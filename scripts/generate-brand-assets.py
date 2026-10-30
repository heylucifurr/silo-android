#!/usr/bin/env python3
"""Regenerate every launcher icon and the in-app wordmark from one logo file.

Silo ships 26 brand files. Editing them by hand is error-prone because the TV
ones are pinned by pixel-accurate tests in
``androidTvApp/src/androidUnitTest/.../TvLauncherIconAssetsTest.kt``. This
script turns a single source mark into all of them at the right size, opacity
and safe-zone geometry.

Usage (from the repository root)::

    python scripts/generate-brand-assets.py --mark path/to/weflix-mark.png
    python scripts/generate-brand-assets.py --mark mark.png --wordmark lockup.png --bg 0034FB
    python scripts/generate-brand-assets.py --mark mark.png --dry-run

Requires Pillow (``python -m pip install --user Pillow``).

Inputs
------
--mark
    The logo mark, on a **transparent** background. That is what makes the
    adaptive-icon foreground valid: Android composites it over the background
    colour, and the asset test requires the four corners, the four edge
    midpoints and the centre to be fully transparent. A mark with a baked-in
    square tile is rejected by that test.
--wordmark
    Optional horizontal lockup (mark + product name) for the in-app wordmark,
    also on a transparent background, used at 764x400.

Transparent padding around the source is trimmed automatically, so a logo
sitting in the middle of a large canvas is fine.
"""

from __future__ import annotations

import argparse
import math
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - guidance only
    sys.exit("Pillow is required: python -m pip install --user Pillow")

REPO_ROOT = Path(__file__).resolve().parent.parent

# Density bucket -> pixel size. TV legacy icons are larger than phone ones.
PHONE_LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
TV_LEGACY = {"mdpi": 80, "hdpi": 120, "xhdpi": 160, "xxhdpi": 240, "xxxhdpi": 320}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

WORDMARK_SIZE = (764, 400)
TV_BANNER_SIZE = (320, 180)

# Adaptive icons reserve a 66/108 safe zone that the launcher may mask, and the
# mask is a CIRCLE, so the safe zone's corners are not safe. The mark's bounding
# box is therefore fitted inside the circle inscribed in that zone, and then
# scaled by MARK_FILL: at 1.0 the box corners touch the circle exactly, which
# reads as cramped and leaves nothing for the mask's antialiasing.
SAFE_ZONE_FRACTION = 66 / 108
MARK_FILL = 0.80

# The mark's height as a fraction of the canvas, just inside that zone. The
# legacy square icons use the same fit so the adaptive and the legacy icon agree
# whichever one a launcher chooses to show.
MARK_HEIGHT_FRACTION = 0.60

# The TV banner must keep its lockup inside Fire TV's centred square crop and
# never run edge to edge, so it is sized to this fraction of the banner width.
BANNER_WIDTH_FRACTION = 0.52


def load_mark(path: Path) -> Image.Image:
    """Open [path], convert to RGBA, and trim transparent padding."""
    if not path.is_file():
        sys.exit(f"image not found: {path}")
    image = Image.open(path).convert("RGBA")
    if image.getextrema()[3][1] == 0:
        sys.exit(f"{path} is fully transparent; nothing to draw")
    bbox = image.split()[3].getbbox()
    return image.crop(bbox) if bbox else image


def parse_bg(raw: str) -> tuple[int, int, int]:
    value = raw.strip().lstrip("#")
    if len(value) == 8:  # tolerate AARRGGBB
        value = value[2:]
    if len(value) != 6:
        sys.exit(f"--bg must be a 6-digit hex colour (got {raw!r})")
    try:
        return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]
    except ValueError:
        sys.exit(f"--bg must be a 6-digit hex colour (got {raw!r})")


def fitted(source: Image.Image, box: tuple[int, int]) -> Image.Image:
    """Scale [source] to fit inside [box], preserving its aspect ratio."""
    width, height = box
    scale = min(width / source.width, height / source.height)
    target = (max(1, round(source.width * scale)), max(1, round(source.height * scale)))
    if target == source.size:
        return source
    return source.resize(target, Image.LANCZOS)


def safe_radius(size: int) -> float:
    """Radius of the circle inscribed in the adaptive safe zone."""
    return size * SAFE_ZONE_FRACTION / 2


def fitted_to_safe_circle(source: Image.Image, size: int) -> Image.Image:
    """Scale [source] so its bounding box sits inside the mask's circle.

    Blocks are the problem: a wide wordmark fitted to the safe zone's full
    width has its left and right ends outside the inscribed circle, so a
    circular launcher mask slices off the first and last letters. Solving
    ``(w/2)^2 + (h/2)^2 <= (fill * radius)^2`` for the declared aspect keeps
    every corner of the box inside the mask.
    """
    radius = safe_radius(size) * MARK_FILL
    aspect = source.width / source.height
    height = 2 * radius / math.sqrt(aspect * aspect + 1)
    scale = height / source.height
    target = (max(1, round(source.width * scale)), max(1, round(height)))
    if target == source.size:
        return source
    return source.resize(target, Image.LANCZOS)


def legacy_icon(mark: Image.Image, size: int, bg: tuple[int, int, int]) -> Image.Image:
    """An opaque square icon: the mark centred on the brand colour.

    Legacy (pre-adaptive) launcher icons must be opaque, so this is built on an
    RGB canvas and has no alpha channel at all. The TV test asserts that.
    """
    canvas = Image.new("RGB", (size, size), bg)
    scaled = fitted_to_safe_circle(mark, size)
    canvas.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
    return canvas


def foreground(mark: Image.Image, size: int) -> Image.Image:
    """A transparent canvas with the mark centred inside the adaptive safe zone."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scaled = fitted_to_safe_circle(mark, size)
    canvas.alpha_composite(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2))
    return canvas


def banner(lockup: Image.Image, bg: tuple[int, int, int]) -> Image.Image:
    """An opaque 16:9 banner with the lockup inside the centred square crop."""
    canvas = Image.new("RGB", TV_BANNER_SIZE, bg)
    width = round(TV_BANNER_SIZE[0] * BANNER_WIDTH_FRACTION)
    scaled = fitted(lockup, (width, round(TV_BANNER_SIZE[1] * 0.55)))
    canvas.paste(scaled, ((TV_BANNER_SIZE[0] - scaled.width) // 2, (TV_BANNER_SIZE[1] - scaled.height) // 2), scaled)
    return canvas


def wordmark_asset(lockup: Image.Image) -> Image.Image:
    """The in-app lockup, centred on the 764x400 canvas the UI code expects."""
    canvas = Image.new("RGBA", WORDMARK_SIZE, (0, 0, 0, 0))
    scaled = fitted(lockup, WORDMARK_SIZE)
    canvas.alpha_composite(
        scaled,
        ((WORDMARK_SIZE[0] - scaled.width) // 2, (WORDMARK_SIZE[1] - scaled.height) // 2),
    )
    return canvas


def save(image: Image.Image, root: Path, relative: str, written: list[Path]) -> None:
    target = root / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, "PNG", optimize=True)
    written.append(target)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Regenerate every launcher icon and the in-app wordmark from one logo file.",
    )
    parser.add_argument("--mark", required=True, type=Path, help="logo mark PNG on a transparent background")
    parser.add_argument("--wordmark", type=Path, help="optional horizontal lockup PNG for the in-app wordmark")
    parser.add_argument("--bg", default="010D9F", help="adaptive-icon background colour (default: 010D9F)")
    parser.add_argument("--out", type=Path, help="output root (default: the repository root)")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="write into a temporary directory instead of the repository",
    )
    args = parser.parse_args()

    mark = load_mark(args.mark)
    wordmark = load_mark(args.wordmark) if args.wordmark else None
    bg = parse_bg(args.bg)
    lockup = wordmark if wordmark is not None else mark

    if args.dry_run:
        root = Path(tempfile.mkdtemp(prefix="brand-assets-"))
        print(f"dry run: writing to {root}")
    else:
        root = (args.out or REPO_ROOT).resolve()

    phone_res = "androidApp/src/androidMain/res"
    tv_res = "androidTvApp/src/androidMain/res"
    written: list[Path] = []

    for res in (phone_res, tv_res):
        save(wordmark_asset(lockup), root, f"{res}/drawable/silo_wordmark.png", written)
    for density, size in PHONE_LEGACY.items():
        save(legacy_icon(mark, size, bg), root, f"{phone_res}/mipmap-{density}/ic_launcher.png", written)
    for density, size in TV_LEGACY.items():
        save(legacy_icon(mark, size, bg), root, f"{tv_res}/mipmap-{density}/ic_launcher.png", written)
    for res in (phone_res, tv_res):
        for density, size in FOREGROUND.items():
            save(foreground(mark, size), root, f"{res}/mipmap-{density}/ic_launcher_foreground.png", written)
    save(banner(lockup, bg), root, f"{tv_res}/drawable/tv_banner.png", written)

    for path in written:
        print(f"  {path.relative_to(root) if root in path.parents else path}")
    print(f"\n{len(written)} files written.")

    if not args.dry_run:
        print(
            "\nNext:\n"
            "  1. Set `silo_icon_background` in both res/values/colors.xml to the same --bg colour.\n"
            "  2. Replace androidTvApp/src/androidMain/res/drawable/silo_mark.xml with your own\n"
            "     vector, or delete its uses in TvMarqueeSetupSteps.kt.\n"
            "  3. Verify:  .\\gradlew.bat :androidTvApp:testDebugUnitTest --tests '*TvLauncherIconAssetsTest'\n\n"
            "Note: that test encodes the old artwork's shape and palette, so a mark that is not tall,\n"
            "or has no warm accent colour, will fail it until the expectations are updated."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


# 66/108 safe zone (0.611) that the launcher may mask; staying just inside it
# keeps the mark legible under every launcher shape. The legacy square icons
# use the same fraction so the two agree whichever one a launcher shows.
MARK_HEIGHT_FRACTION = 0.60

# The TV banner must keep its lockup inside Fire TV's centred square crop and
# never run edge to edge, so it is sized to this fraction of the banner width.
BANNER_WIDTH_FRACTION = 0.52
