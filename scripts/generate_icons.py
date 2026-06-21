"""
Builds every Android icon file Helga needs, straight from the original
full logo (icon artwork + "VALKYRIE" wordmark on a flat background).

It auto-detects the icon vs. the text by scanning for horizontal bands of
content separated by a gap of pure background — the tallest band is taken
as the icon, the text band below it is discarded. No manual cropping needed,
even if the source image changes later.

Run locally:
    pip install pillow numpy --break-system-packages
    python3 scripts/generate_icons.py assets/logo_source.png

This is also run automatically by .github/workflows/build.yml before every
build, so the icon always reflects whatever is committed at assets/logo_source.*
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

NOISE_THRESHOLD = 60          # per-pixel color distance to count as "not background"
ROW_CONTENT_FRACTION = 0.01   # min fraction of row width that must differ from bg to count as content
FOREGROUND_SCALE = 0.62       # adaptive icon safe-zone scale


def detect_background_color(img: np.ndarray) -> np.ndarray:
    # Sample all four corners and average — more robust than a single pixel
    # if there's any JPEG compression noise right at the very corner.
    h, w, _ = img.shape
    corners = [img[0, 0], img[0, w - 1], img[h - 1, 0], img[h - 1, w - 1]]
    return np.mean(corners, axis=0).astype(int)


def find_content_bands(mask: np.ndarray, axis_len: int, min_fraction: float):
    counts = mask.sum(axis=1) if mask.ndim == 2 else mask
    threshold = axis_len * min_fraction
    has_content = counts > threshold

    bands = []
    in_band = False
    start = 0
    for i, v in enumerate(has_content):
        if v and not in_band:
            start = i
            in_band = True
        elif not v and in_band:
            bands.append((start, i))
            in_band = False
    if in_band:
        bands.append((start, len(has_content)))
    return bands


def extract_icon_only(source: Image.Image) -> Image.Image:
    """Finds the tallest horizontal content band (the icon artwork) and
    crops it out, discarding any text band(s) above or below it."""
    arr = np.array(source.convert("RGB"))
    bg = detect_background_color(arr)
    diff = np.abs(arr.astype(int) - bg).sum(axis=2)
    mask = diff > NOISE_THRESHOLD

    row_bands = find_content_bands(mask, arr.shape[1], ROW_CONTENT_FRACTION)
    if not row_bands:
        raise ValueError("Could not detect any artwork — check NOISE_THRESHOLD or the source image.")

    # The icon is the tallest band; any wordmark/text band will be shorter.
    icon_band = max(row_bands, key=lambda b: b[1] - b[0])
    row_start, row_end = icon_band

    # Now find the horizontal extent of content within just those rows.
    col_mask = mask[row_start:row_end, :]
    col_counts = col_mask.sum(axis=0)
    col_threshold = (row_end - row_start) * ROW_CONTENT_FRACTION
    has_col_content = col_counts > col_threshold
    col_indices = np.where(has_col_content)[0]
    col_start, col_end = col_indices.min(), col_indices.max() + 1

    cropped = source.crop((int(col_start), int(row_start), int(col_end), int(row_end)))

    # Pad to a square canvas (centered, background-filled) so downstream
    # resizing doesn't distort the artwork's aspect ratio.
    bg_color = tuple(int(c) for c in bg)
    side = max(cropped.width, cropped.height)
    # add a little breathing room around the artwork
    side = int(side * 1.15)
    square = Image.new("RGB", (side, side), bg_color)
    offset = ((side - cropped.width) // 2, (side - cropped.height) // 2)
    square.paste(cropped, offset)
    return square


def circular_mask(img: Image.Image) -> Image.Image:
    size = img.size
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def extract_foreground(img: Image.Image, bg_color, threshold=40) -> Image.Image:
    """Keys out the flat background color for the adaptive-icon foreground layer."""
    img = img.convert("RGBA")
    arr = np.array(img)
    diff = np.abs(arr[:, :, :3].astype(int) - np.array(bg_color[:3])).sum(axis=2)
    arr[:, :, 3] = np.where(diff < threshold * 3, 0, arr[:, :, 3])
    return Image.fromarray(arr, "RGBA")


def pad_to_safe_zone(img: Image.Image, canvas_size: int, scale: float = FOREGROUND_SCALE) -> Image.Image:
    target_size = int(canvas_size * scale)
    resized = img.resize((target_size, target_size), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = (canvas_size - target_size) // 2
    canvas.paste(resized, (offset, offset), resized)
    return canvas


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/generate_icons.py path/to/source_logo.(png|jpg)")
        sys.exit(1)

    source_path = Path(sys.argv[1])
    raw = Image.open(source_path).convert("RGB")

    icon_only = extract_icon_only(raw)
    bg_color = icon_only.getpixel((1, 1))

    foreground_master = extract_foreground(icon_only, bg_color)

    out_root = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("app/src/main/res")

    for folder, size in DENSITIES.items():
        d = out_root / folder
        d.mkdir(parents=True, exist_ok=True)

        square = icon_only.resize((size, size), Image.LANCZOS)
        square.save(d / "ic_launcher.png")

        round_icon = circular_mask(square)
        round_icon.save(d / "ic_launcher_round.png")

        fg = pad_to_safe_zone(foreground_master, size)
        fg.save(d / "ic_launcher_foreground.png")

    # Keep the adaptive-icon background color in sync with the actual logo background.
    hex_color = "#{:02X}{:02X}{:02X}".format(*[int(c) for c in bg_color[:3]])
    colors_xml = out_root / "values" / "colors.xml"
    colors_xml.parent.mkdir(parents=True, exist_ok=True)
    colors_xml.write_text(
        f'<resources>\n'
        f'    <color name="app_background">#FFF8D6</color>\n'
        f'    <color name="ic_launcher_background">{hex_color}</color>\n'
        f'</resources>\n'
    )

    print(f"Icon background detected as {hex_color}")
    print(f"Generated icons for {len(DENSITIES)} densities under {out_root}")


if __name__ == "__main__":
    main()
