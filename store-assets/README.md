# Play Store assets for 2.0

Everything here is generated. Regenerate rather than hand-edit: the icon and
feature graphic come from the same vector as the launcher icon, so they cannot
drift out of sync with the app.

| File | Where it goes in Play Console | Play's requirement |
|---|---|---|
| `play-icon-512.png` | Main store listing → App icon | 512×512 PNG, under 1 MB, no transparency |
| `feature-graphic-1024x500.png` | Main store listing → Feature graphic | 1024×500 PNG, under 15 MB |
| `01`–`04-screenshot-*.png` | Main store listing → Phone screenshots | 2–8 images, 9:16 or 16:9, each side 320–3840 px |
| `raw/` | Nothing. Source captures, kept for recomposing. | |

The framed screenshots are 1080×1920, which is exactly 9:16. The raw captures
are 1080×2400 and **Play will reject them** — that is 9:20, outside the allowed
range. This is why they get composed onto a canvas rather than uploaded as-is.

## The look

Each screenshot is a headline, a supporting line, and the capture mounted in a
phone frame with a drop shadow. The background is a violet gradient with blurred
amber and lilac blobs behind it, since the top third is bare background and a
flat fill there reads as empty.

The gradient shifts between the four so the carousel does not look like the same
image four times, but stays inside the app's violet. The amber rule above each
headline is the one accent color, carried over from the icon.

## What the screenshots show

Captured from a debug build on a Pixel emulator (API 36) with seeded data, on
2026-08-11. Real UI, real database, no mockups.

1. Item list, grouped by urgency
2. Edit sheet, showing the scan button
3. The widget on a home screen
4. Settings

## Regenerating

The emulator work is manual, but the composition is not:

```sh
# icon and feature graphic
rsvg-convert -w 512 -h 512 -b '#6200EE' icon512.svg -o play-icon-512.png
rsvg-convert -w 1024 -h 500 feature.svg -o feature-graphic-1024x500.png

# screenshots, from raw/ captures
python3 compose_shots.py
```

The sources are in `tools/`. Text is converted to outlines with `text2path.py`
before rendering, because librsvg on macOS goes through CoreText and ignores
fontconfig: a plain `font-family="Outfit"` silently falls back to Helvetica. Both
scripts have absolute paths baked in, so fix those if the project moves.

## Not done

- **Tablet screenshots.** Only needed if the listing declares tablet support.
  Requires a tablet AVD.
- **Item photos.** Every item in the screenshots shows the empty photo
  placeholder, because there are no real product photos to seed. Taking four or
  five pictures of actual packaging and attaching them before recapturing would
  make the list and detail screens look considerably better.
- **The detail screen** is not in the set for the same reason: it is dominated by
  an empty "Add photo" panel.
