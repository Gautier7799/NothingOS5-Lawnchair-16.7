#!/usr/bin/env python3
"""Merge generated Lawnicons resources into a Gradle resource directory.

Usage: merge_lawnicons.py <lawnicons-checkout> [<output-res-dir>]

Without <output-res-dir> the resources are merged into lawnchair/res, which is
the historical CI behaviour. With it, a standalone merged resource tree is
produced for a build directory: lawnchair/res is used as the base layer so that
Lawnchair's own resources and the collision detection stay identical.
"""
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1]).resolve()
GENERATED = SOURCE / "app/src/runtime/res"
BASE = ROOT / "lawnchair/res"
DEST = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else BASE

if not GENERATED.is_dir():
    raise SystemExit(f"Expected generated Lawnicons resources at {GENERATED}")

ICON_MAP = "xml/grayscale_icon_map.xml"

# A separate output directory must be a self-contained resource tree, because
# Gradle/AGP rejects the same resource name coming from two source directories.
if DEST != BASE:
    if DEST.exists():
        shutil.rmtree(DEST)
    shutil.copytree(BASE, DEST)

def merge_values(src: Path, dst: Path) -> None:
    source_tree = ET.parse(src)
    source_root = source_tree.getroot()
    base = BASE / src.relative_to(GENERATED)
    if dst.exists():
        dest_tree = ET.parse(dst)
        dest_root = dest_tree.getroot()
    elif base.exists():
        dest_tree = ET.parse(base)
        dest_root = dest_tree.getroot()
    else:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    if source_root.tag != "resources" or dest_root.tag != "resources":
        raise RuntimeError(f"Unexpected values XML root: {src} / {dst}")
    existing = {(child.tag, child.attrib.get("name")) for child in dest_root}
    for child in source_root:
        key = (child.tag, child.attrib.get("name"))
        if key not in existing:
            dest_root.append(child)
            existing.add(key)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dest_tree.write(dst, encoding="utf-8", xml_declaration=True)

def merge_icon_map(src: Path, dst: Path) -> None:
    source_tree = ET.parse(src)
    source_root = source_tree.getroot()
    base = BASE / ICON_MAP
    if dst.exists():
        dest_tree = ET.parse(dst)
        dest_root = dest_tree.getroot()
    elif base.exists():
        dest_tree = ET.parse(base)
        dest_root = dest_tree.getroot()
    else:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    existing = {node.attrib.get("package"): node for node in dest_root if node.tag == "icon"}
    for node in source_root:
        package = node.attrib.get("package")
        if not package:
            continue
        old = existing.get(package)
        if old is not None:
            dest_root.remove(old)
        dest_root.append(node)
        existing[package] = node
    dst.parent.mkdir(parents=True, exist_ok=True)
    dest_tree.write(dst, encoding="utf-8", xml_declaration=True)

# Lawnchair already defines a few generic themed icons (notably calendar/clock).
# Keep both implementations by giving colliding generated resource files a unique
# Lawnicons prefix and rewriting references before copying them.
# Collisions are always detected against lawnchair/res, so the merged output is
# identical whether DEST is the source tree or a standalone build directory.
rename_map = {}
for src in GENERATED.rglob("*"):
    if not src.is_file() or src.suffix not in {".xml", ".png", ".webp", ".jpg", ".jpeg"}:
        continue
    rel = src.relative_to(GENERATED)
    if rel.parts[0].startswith("values") or rel.as_posix() == ICON_MAP:
        continue
    base = BASE / rel
    if base.exists() and base.read_bytes() != src.read_bytes():
        renamed = rel.with_name("lawnicons_" + rel.name)
        renamed_base = BASE / renamed
        if renamed_base.exists() and renamed_base.read_bytes() != src.read_bytes():
            raise RuntimeError(f"Renamed Lawnicons resource also collides: {renamed_base}")
        rename_map[rel.stem] = renamed.stem

def rewrite_refs(data: bytes) -> bytes:
    text = data.decode("utf-8", errors="ignore")
    for old, new in sorted(rename_map.items(), key=lambda item: -len(item[0])):
        text = re.sub(r"(?<=@drawable/)" + re.escape(old) + r"(?=[^A-Za-z0-9_]|$)", new, text)
        text = re.sub(r"(?<=@mipmap/)" + re.escape(old) + r"(?=[^A-Za-z0-9_]|$)", new, text)
    return text.encode("utf-8")

for src in sorted(GENERATED.rglob("*")):
    if not src.is_file():
        continue
    rel = src.relative_to(GENERATED)
    if rel.parts[0].startswith("values") and src.suffix == ".xml":
        merge_values(src, DEST / rel)
        continue
    if rel.as_posix() == ICON_MAP:
        temp = src.with_name("grayscale_icon_map.lawnchair-merge.xml")
        temp.write_bytes(rewrite_refs(src.read_bytes()))
        try:
            merge_icon_map(temp, DEST / rel)
        finally:
            temp.unlink(missing_ok=True)
        continue

    target_rel = rel
    base = BASE / rel
    if rel.stem in rename_map and base.exists() and base.read_bytes() != src.read_bytes():
        target_rel = rel.with_name("lawnicons_" + rel.name)
    dst = DEST / target_rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    data = rewrite_refs(src.read_bytes()) if src.suffix == ".xml" else src.read_bytes()
    if dst.exists():
        if dst.read_bytes() == data:
            continue
        raise RuntimeError(f"Resource name collision after renaming: {dst}")
    dst.write_bytes(data)

# The SVG processor emits vector drawables that reference these two colors, but
# those colors live in Lawnicons' app resources rather than app/src/runtime/res.
# Define local fallbacks so the merged vectors compile in Lawnchair. The launcher
# applies the actual themed-icon tint when rendering monochrome icons.
colors_file = DEST / "values/colors_lawnicons.xml"
colors_file.parent.mkdir(parents=True, exist_ok=True)
if not colors_file.exists():
    colors_root = ET.Element("resources")
    ET.SubElement(colors_root, "color", {"name": "primaryForeground"}).text = "#FF000000"
    ET.SubElement(colors_root, "color", {"name": "primaryBackground"}).text = "#FFFFFFFF"
    ET.ElementTree(colors_root).write(colors_file, encoding="utf-8", xml_declaration=True)

count = sum(1 for path in GENERATED.rglob("*") if path.is_file())
print(f"Merged {count} generated Lawnicons resource files from {SOURCE} into {DEST}; renamed {len(rename_map)} colliding resources")
