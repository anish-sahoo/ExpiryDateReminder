"""Compose device captures into 1080x1920 (9:16) Play Store screenshots.

Raw captures are 1080x2400, which is 9:20 and outside Play's accepted range, so
they get mounted in a phone frame on a 9:16 canvas rather than uploaded as-is.
"""
import base64
import subprocess
import sys

sys.path.insert(0, "/private/tmp/claude-501/-Users-anish-Projects-ExpiryDateReminder2/e3d1983c-dc5b-4cc5-b0f2-09146ba770a4/scratchpad")
from text2path import text_path

S = "/private/tmp/claude-501/-Users-anish-Projects-ExpiryDateReminder2/e3d1983c-dc5b-4cc5-b0f2-09146ba770a4/scratchpad"
OUT = "/Users/anish/Projects/ExpiryDateReminder2/store-assets"

W, H = 1080, 1920

# Phone frame. The screen is the capture at its own aspect ratio; the bezel is
# drawn around it, and the whole thing runs off the bottom of the canvas.
SCREEN_W = 784
SCREEN_H = round(SCREEN_W * 2400 / 1080)
BEZEL = 18
SCREEN_X = (W - SCREEN_W) // 2
SCREEN_Y = 470
FRAME_X, FRAME_Y = SCREEN_X - BEZEL, SCREEN_Y - BEZEL
FRAME_W, FRAME_H = SCREEN_W + 2 * BEZEL, SCREEN_H + 2 * BEZEL

AMBER = "#FFA51E"

# The app glyph, reused as a background watermark.
GLYPH = (
    "M37,28 a2.5,2.5 0 0,1 2.5,2.5 v5 h-5 v-5 a2.5,2.5 0 0,1 2.5,-2.5 z "
    "M55,28 a2.5,2.5 0 0,1 2.5,2.5 v5 h-5 v-5 a2.5,2.5 0 0,1 2.5,-2.5 z "
    "M35,32 h24 a6,6 0 0,1 6,6 v27 a6,6 0 0,1 -6,6 h-24 a6,6 0 0,1 -6,-6 v-27 a6,6 0 0,1 6,-6 z "
    "M29,43 h36 v4 h-36 z "
    "M57.5,71 a13.5,13.5 0 1,0 27,0 a13.5,13.5 0 1,0 -27,0 z "
    "M69.2,60.5 h3.6 v8.7 h8.5 v3.6 h-12.1 z"
)

FRAMES = [
    {
        "source": "01-list.png",
        "headline": ["See what needs", "attention first"],
        "sub": "Expired and expiring, grouped and counted",
        "gradient": ("#8438FF", "#4A00C2"),
        "target": "01-screenshot-list.png",
    },
    {
        "source": "03-add.png",
        "headline": ["Scan the date", "off the label"],
        "sub": "Point the camera and skip the typing",
        "gradient": ("#5B18D9", "#25085F"),
        "target": "02-screenshot-scan.png",
    },
    {
        "source": "06-widget.png",
        "headline": ["Right on your", "home screen"],
        "sub": "Glance at whatever runs out next",
        "gradient": ("#7A22F0", "#3A0091"),
        "target": "03-screenshot-widget.png",
    },
    {
        "source": "05-settings.png",
        "headline": ["Reminders on", "your schedule"],
        "sub": "How far ahead, and at what time of day",
        "gradient": ("#6200EE", "#2C0070"),
        "target": "04-screenshot-settings.png",
    },
]


def centered(line, size, y, font="outfit", weight=600, fill="#FFFFFF", opacity=None):
    _, width = text_path(line, font=font, weight=weight, size=size)
    path, _ = text_path(line, font=font, weight=weight, size=size, x=(W - width) / 2, y=y, fill=fill)
    if opacity is not None:
        path = path.replace("<path ", f'<path opacity="{opacity}" ')
    return path


for frame in FRAMES:
    with open(f"{S}/shots/{frame['source']}", "rb") as fh:
        capture = base64.b64encode(fh.read()).decode()

    top, bottom = frame["gradient"]
    headline = "\n".join(
        centered(line, 64, 250 + i * 82) for i, line in enumerate(frame["headline"])
    )
    sub = centered(frame["sub"], 31, 400, font="inter", weight=400, opacity="0.72")

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
     width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  <defs>
    <linearGradient id="bg" x1="0.1" y1="0" x2="0.9" y2="1">
      <stop offset="0%" stop-color="{top}"/>
      <stop offset="100%" stop-color="{bottom}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.42" r="0.62">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.16"/>
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="screen">
      <rect x="{SCREEN_X}" y="{SCREEN_Y}" width="{SCREEN_W}" height="{SCREEN_H}" rx="44" ry="44"/>
    </clipPath>
    <filter id="lift" x="-30%" y="-20%" width="160%" height="150%">
      <feDropShadow dx="0" dy="26" stdDeviation="34" flood-color="#12002E" flood-opacity="0.55"/>
    </filter>
    <filter id="soft" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="110"/>
    </filter>
  </defs>

  <rect width="{W}" height="{H}" fill="url(#bg)"/>

  <!-- Blurred blobs rather than a flat gradient: the top third is mostly background,
       and the phone covers everything the eye would otherwise land on. -->
  <ellipse cx="1010" cy="90" rx="330" ry="260" fill="{AMBER}" opacity="0.34" filter="url(#soft)"/>
  <ellipse cx="60" cy="430" rx="290" ry="250" fill="#C9A6FF" opacity="0.30" filter="url(#soft)"/>
  <ellipse cx="{W / 2}" cy="{SCREEN_Y + 120}" rx="520" ry="300" fill="#FFFFFF"
           opacity="0.10" filter="url(#soft)"/>
  <rect width="{W}" height="{H}" fill="url(#glow)"/>

  <rect x="{(W - 88) / 2}" y="142" width="88" height="9" rx="4.5" fill="{AMBER}"/>
{headline}
{sub}

  <g filter="url(#lift)">
    <rect x="{FRAME_X}" y="{FRAME_Y}" width="{FRAME_W}" height="{FRAME_H}" rx="60" ry="60"
          fill="#150F26"/>
  </g>
  <rect x="{FRAME_X}" y="{FRAME_Y}" width="{FRAME_W}" height="{FRAME_H}" rx="60" ry="60"
        fill="none" stroke="#FFFFFF" stroke-opacity="0.22" stroke-width="2"/>
  <g clip-path="url(#screen)">
    <image x="{SCREEN_X}" y="{SCREEN_Y}" width="{SCREEN_W}" height="{SCREEN_H}"
           xlink:href="data:image/png;base64,{capture}"/>
  </g>
</svg>
"""
    path = f"{S}/frame.svg"
    with open(path, "w") as fh:
        fh.write(svg)
    subprocess.run(
        ["rsvg-convert", "-w", str(W), "-h", str(H), path, "-o", f"{OUT}/{frame['target']}"],
        check=True,
    )
    print(frame["target"])
