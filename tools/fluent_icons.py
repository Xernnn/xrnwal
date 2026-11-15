#!/usr/bin/env python3
"""Makes the app's colour icons from Microsoft's Fluent Emoji (MIT licence), Flat style.

Every emoji the app's code names (categories offered, defaults, Mine's tiles) gets a vector drawable,
res/drawable/fe_<code points>.xml, and FluentArt.kt maps the emoji to it. Run it again after adding
an emoji to the code; it fetches only what it doesn't have yet.

    python3 -I tools/fluent_icons.py [--cache DIR]

The drawings are fetched from github.com/microsoft/fluentui-emoji into a cache folder outside the
repo, read as data, and written out as plain paths and colours. Anything in an SVG this script
doesn't understand stops it rather than drawing something wrong.
"""
import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
import unicodedata
import urllib.parse
import urllib.request
from xml.etree import ElementTree

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(REPO, "app", "src", "main", "java")
DRAWABLES = os.path.join(REPO, "app", "src", "main", "res", "drawable")
KOTLIN = os.path.join(SOURCES, "com", "xern", "xrnwallet", "ui", "FluentArt.kt")
TREE_URL = "https://api.github.com/repos/microsoft/fluentui-emoji/git/trees/main?recursive=1"
RAW_URL = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/"
SVG = "{http://www.w3.org/2000/svg}"
DARK_BELOW = 0.32

# Where Fluent's folder name (the emoji's CLDR name) differs from the Unicode character name.
FOLDERS = {
    "☕": "Hot beverage", "⭐": "Star", "✅": "Check mark button", "➕": "Plus", "🅿": "P button",
    "🍕": "Pizza", "🎁": "Wrapped gift", "🏋": "Person lifting weights", "🏠": "House", "🐾": "Paw prints",
    "👋": "Waving hand", "👟": "Running shoe", "👛": "Purse", "💡": "Light bulb", "💵": "Dollar banknote",
    "📈": "Chart increasing", "📉": "Chart decreasing", "🔁": "Repeat button", "🔄": "Counterclockwise arrows button",
    "🔍": "Magnifying glass tilted left", "🔒": "Locked", "🔤": "Input latin letters", "🖼": "Framed picture",
    "🗓": "Spiral calendar", "🛒": "Shopping cart", "🦊": "Fox", "🧧": "Red envelope", "🛋": "Couch and lamp",
    "🌙": "Crescent moon", "⛄": "Snowman without snow", "👕": "T-shirt", "🗺": "World map",
}

EMOJI_LITERAL = re.compile(r'"([^"\\]{1,4})"')
NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"
PATH_DATA = re.compile(r"^[MmLlHhVvCcSsQqTtAaZz0-9eE.,+\-\s]*$")
COLOUR = re.compile(r"^#[0-9A-Fa-f]{6}$|^#[0-9A-Fa-f]{3}$")
NAMED = {"white": "#FFFFFF", "black": "#000000"}


def subjects():
    """Every emoji named in the app's Kotlin, as the app keys them (no variation selector)."""
    found = set()
    for root, _, files in os.walk(SOURCES):
        for name in files:
            if name.endswith(".kt") and name != "FluentArt.kt":
                with open(os.path.join(root, name), encoding="utf-8") as f:
                    for m in EMOJI_LITERAL.finditer(f.read()):
                        s = m.group(1).replace("️", "")
                        if s and all(ord(c) > 0x2000 for c in s) and any(unicodedata.category(c) == "So" for c in s):
                            found.add(s)
    return sorted(found)


def folder_guess(emoji):
    if emoji in FOLDERS:
        return FOLDERS[emoji]
    name = unicodedata.name(emoji[0], "").lower()
    return name[:1].upper() + name[1:]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "xrnwallet-icons"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def flat_paths(cache):
    path = os.path.join(cache, "tree.json")
    if not os.path.exists(path):
        with open(path, "wb") as f:
            f.write(fetch(TREE_URL))
    with open(path, encoding="utf-8") as f:
        tree = json.load(f)["tree"]
    found = {}
    for entry in tree:
        p = entry["path"]
        m = re.fullmatch(r"assets/([^/]+)/(?:Default/)?Flat/[^/]+\.svg", p)
        if m:
            found.setdefault(m.group(1).lower(), p)
    return found


def attr(element, name, default=None):
    style = dict(
        part.split(":", 1) for part in (element.get("style") or "").split(";") if ":" in part
    )
    return element.get(name, style.get(name, default))


def check_colour(value, what):
    if value is None or value == "none":
        return value
    value = NAMED.get(value.strip().lower(), value.strip())
    if not COLOUR.fullmatch(value):
        raise ValueError(f"{what}: colour {value!r} isn't a plain hex colour")
    if len(value) == 4:
        value = "#" + "".join(c * 2 for c in value[1:])
    return value.upper()


def num(value, what):
    if value is None or not re.fullmatch(NUMBER, value.strip()):
        raise ValueError(f"{what}: {value!r} isn't a number")
    return float(value)


def fmt(x):
    return f"{x:.4f}".rstrip("0").rstrip(".") if x != int(x) else str(int(x))


def shape_path(el, what):
    tag = el.tag.removeprefix(SVG)
    if tag == "path":
        d = el.get("d", "")
        if not PATH_DATA.fullmatch(d):
            raise ValueError(f"{what}: path data has something other than commands and numbers")
        return d
    if tag == "circle":
        cx, cy, r = (num(el.get(k, "0"), what) for k in ("cx", "cy", "r"))
        return f"M{fmt(cx - r)},{fmt(cy)}a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0z"
    if tag == "ellipse":
        cx, cy, rx, ry = (num(el.get(k, "0"), what) for k in ("cx", "cy", "rx", "ry"))
        return f"M{fmt(cx - rx)},{fmt(cy)}a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0z"
    if tag == "rect":
        x, y = num(el.get("x", "0"), what), num(el.get("y", "0"), what)
        w, h = num(el.get("width"), what), num(el.get("height"), what)
        rx = el.get("rx") or el.get("ry")
        ry = el.get("ry") or el.get("rx")
        if rx is None:
            return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
        rx, ry = min(num(rx, what), w / 2), min(num(ry, what), h / 2)
        return (f"M{fmt(x + rx)},{fmt(y)}h{fmt(w - 2 * rx)}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(ry)}"
                f"v{fmt(h - 2 * ry)}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(ry)}h{fmt(2 * rx - w)}"
                f"a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(-ry)}v{fmt(2 * ry - h)}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(-ry)}z")
    raise ValueError(f"{what}: <{tag}> isn't drawn by this script")


def group_attrs(transform, what):
    """A VectorDrawable <group>'s attributes for an SVG transform; None for no transform."""
    if not transform:
        return None
    out = {}
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", transform):
        values = [float(v) for v in re.findall(NUMBER, args)]
        if name == "translate":
            out["android:translateX"] = fmt(values[0])
            out["android:translateY"] = fmt(values[1] if len(values) > 1 else 0)
        elif name == "rotate":
            out["android:rotation"] = fmt(values[0])
            if len(values) == 3:
                out["android:pivotX"], out["android:pivotY"] = fmt(values[1]), fmt(values[2])
        elif name == "scale":
            out["android:scaleX"] = fmt(values[0])
            out["android:scaleY"] = fmt(values[1] if len(values) > 1 else values[0])
        elif name == "matrix" and len(values) == 6:
            a, b, c, d, e, f = values
            if abs(b) < 1e-9 and abs(c) < 1e-9:
                out.update({"android:scaleX": fmt(a), "android:scaleY": fmt(d),
                            "android:translateX": fmt(e), "android:translateY": fmt(f)})
            else:
                raise ValueError(f"{what}: a rotating or skewing matrix() isn't drawn by this script")
        else:
            raise ValueError(f"{what}: transform {name}() isn't drawn by this script")
    if len(re.findall(r"\w+\s*\(", transform)) > 1:
        raise ValueError(f"{what}: more than one transform at once isn't drawn by this script")
    return out


def convert(svg_bytes, what):
    root = ElementTree.fromstring(svg_bytes)
    if root.tag != SVG + "svg":
        raise ValueError(f"{what}: not an SVG")
    box = [num(v, what) for v in (root.get("viewBox") or "").replace(",", " ").split()]
    if len(box) != 4 or box[0] or box[1]:
        raise ValueError(f"{what}: viewBox {root.get('viewBox')!r}")
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             f"<!-- Fluent Emoji (Flat), {what}. Copyright (c) Microsoft Corporation, MIT licence. Made by tools/fluent_icons.py. -->",
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{fmt(box[2])}dp" android:height="{fmt(box[3])}dp"',
             f'    android:viewportWidth="{fmt(box[2])}" android:viewportHeight="{fmt(box[3])}">']

    def walk(el, depth, inherited_fill, inherited_opacity):
        tag = el.tag.removeprefix(SVG)
        if tag in ("defs", "title", "desc", "metadata"):
            if tag == "defs" and len(el):
                raise ValueError(f"{what}: <defs> with content (gradients or clips) isn't drawn by this script")
            return
        for banned in ("clip-path", "mask", "filter"):
            if el.get(banned):
                raise ValueError(f"{what}: {banned} isn't drawn by this script")
        own = attr(el, "fill")
        fill = check_colour(own, what) if own is not None else inherited_fill
        opacity = inherited_opacity * float(attr(el, "opacity", "1"))
        group = group_attrs(el.get("transform"), what)
        pad = "    " * depth
        if group:
            lines.append(pad + "<group " + " ".join(f'{k}="{v}"' for k, v in group.items()) + ">")
            depth += 1
            pad = "    " * depth
        if tag in ("svg", "g"):
            for child in el:
                walk(child, depth, fill, opacity)
        else:
            if attr(el, "stroke") not in (None, "none"):
                raise ValueError(f"{what}: strokes aren't drawn by this script")
            if fill == "none":
                return  # an outline-only shape, which these files don't use
            if fill is None:
                fill = "#000000"  # SVG's default fill
            alpha = opacity * float(attr(el, "fill-opacity", "1"))
            extra = ""
            if alpha < 0.999:
                extra += f' android:fillAlpha="{fmt(round(alpha, 4))}"'
            if attr(el, "fill-rule") == "evenodd":
                extra += ' android:fillType="evenOdd"'
            lines.append(f'{pad}<path android:fillColor="{fill}"{extra}')
            lines.append(f'{pad}    android:pathData="{shape_path(el, what)}" />')
        if group:
            lines.append("    " * (depth - 1) + "</group>")

    for child in root:
        walk(child, 1, check_colour(root.get("fill"), what), 1.0)
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def png_pixels(data):
    """RGBA rows from an 8-bit RGBA, non-interlaced PNG, which is what rsvg-convert writes."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, width, height = 8, b"", 0, 0
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if (depth, colour, interlace) != (8, 6, 0):
                raise ValueError("expected 8-bit RGBA without interlacing")
        elif kind == b"IDAT":
            idat += chunk
        pos += 12 + length
    raw, stride, rows, prev = zlib.decompress(idat), width * 4, [], bytearray(width * 4)
    for y in range(height):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b, c = prev[i], prev[i - 4] if i >= 4 else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                p_ = a + b - c
                pa, pb, pc = abs(p_ - a), abs(p_ - b), abs(p_ - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    return rows


def brightness(svg_path):
    """How light a drawing is, 0 to 1, over its painted pixels; None without rsvg-convert."""
    if not shutil.which("rsvg-convert"):
        return None
    with tempfile.TemporaryDirectory() as folder:
        out = os.path.join(folder, "icon.png")
        subprocess.run(["rsvg-convert", "-w", "48", "-h", "48", svg_path, "-o", out], check=True)
        with open(out, "rb") as f:
            rows = png_pixels(f.read())
    total = weight = 0.0
    for row in rows:
        for i in range(0, len(row), 4):
            r, g, b, a = row[i:i + 4]
            if a:
                total += a / 255 * (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
                weight += a / 255
    return total / weight if weight else 1.0


def resource_name(emoji):
    return "fe_" + "_".join(f"{ord(c):x}" for c in emoji)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--cache", default=os.path.expanduser("~/.cache/xrnwallet-fluent"))
    args = p.parse_args()
    os.makedirs(args.cache, exist_ok=True)
    flat = flat_paths(args.cache)
    made, missing, dark = {}, [], []
    for emoji in subjects():
        folder = folder_guess(emoji).lower()
        path = flat.get(folder)
        if not path:
            missing.append(f"{emoji} (looked for {folder_guess(emoji)!r})")
            continue
        cached = os.path.join(args.cache, resource_name(emoji) + ".svg")
        if not os.path.exists(cached):
            with open(cached, "wb") as f:
                f.write(fetch(RAW_URL + urllib.parse.quote(path)))
        with open(cached, "rb") as f:
            xml = convert(f.read(), f"{emoji} {path.split('/')[1]}")
        with open(os.path.join(DRAWABLES, resource_name(emoji) + ".xml"), "w", encoding="utf-8") as f:
            f.write(xml)
        made[emoji] = resource_name(emoji)
        light = brightness(cached)
        if light is not None and light < DARK_BELOW:
            dark.append(emoji)
    with open(KOTLIN, "w", encoding="utf-8") as f:
        f.write("package com.xern.xrnwallet.ui\n\nimport com.xern.xrnwallet.R\n\n")
        f.write("// Made by tools/fluent_icons.py from Microsoft's Fluent Emoji (Flat), MIT licence. Don't edit by hand.\n\n")
        f.write("/** The colour drawing for each emoji the app names, keyed without variation selectors. */\n")
        f.write("internal val fluentDrawables: Map<String, Int> = mapOf(\n")
        for emoji, name in sorted(made.items()):
            f.write(f'    "{emoji}" to R.drawable.{name},\n')
        f.write(")\n\n")
        f.write(f"/** Drawings mostly darker than {DARK_BELOW} (0 black, 1 white): on a dark circle they need a lighter one. */\n")
        f.write("internal val fluentDark: Set<String> = setOf(" + ", ".join(f'"{e}"' for e in sorted(dark)) + ")\n")
    print(f"dark: {' '.join(sorted(dark)) or 'none'}")
    print(f"{len(made)} drawings written; {len(missing)} emoji without one: {', '.join(missing) or 'none'}")


if __name__ == "__main__":
    sys.exit(main())
