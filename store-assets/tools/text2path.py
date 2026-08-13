"""Turn a string into an SVG <path>, since rsvg on macOS ignores our fontconfig dir."""
import sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.misc.transform import Transform

FONTS = {
    "outfit": "/Users/anish/Projects/ExpiryDateReminder2/composeApp/src/main/res/font/outfit_variable.ttf",
    "inter": "/Users/anish/Projects/ExpiryDateReminder2/composeApp/src/main/res/font/inter_variable.ttf",
}
_cache = {}


def load(name, weight):
    key = (name, weight)
    if key not in _cache:
        f = TTFont(FONTS[name])
        f = instantiateVariableFont(f, {"wght": weight}, inplace=True, updateFontNames=False)
        _cache[key] = f
    return _cache[key]


def text_path(s, font="outfit", weight=400, size=100, x=0, y=0, tracking=0.0, fill="#000"):
    f = load(font, weight)
    upem = f["head"].unitsPerEm
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    scale = size / upem
    pen = SVGPathPen(gs)
    cursor = 0.0
    for ch in s:
        gname = cmap.get(ord(ch))
        if gname is None:
            continue
        g = gs[gname]
        g.draw(TransformPen(pen, Transform(1, 0, 0, 1, cursor, 0)))
        cursor += g.width + tracking * upem
    d = pen.getCommands()
    width = cursor * scale
    return (
        f'<path fill="{fill}" transform="translate({x},{y}) scale({scale},{-scale})" d="{d}"/>',
        width,
    )


if __name__ == "__main__":
    spec = json.load(sys.stdin)
    out = []
    for item in spec:
        fill_color = item.pop("fill", "#000")
        p, w = text_path(fill=fill_color, **item)
        out.append({"path": p, "width": w})
    print(json.dumps(out))
