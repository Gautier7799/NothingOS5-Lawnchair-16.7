#!/usr/bin/env python3
"""Merge generated Lawnicons icon resources into Lawnchair for an APK build."""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1]).resolve()
GENERATED = SOURCE / "app/src/runtime/res"
DEST = ROOT / "lawnchair/res"

if not GENERATED.is_dir():
    raise SystemExit(f"Expected generated Lawnicons resources at {GENERATED}")

def merge_values(src: Path, dst: Path) -> None:
    source_tree = ET.parse(src)
    source_root = source_tree.getroot()
    if not dst.exists():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    dest_tree = ET.parse(dst)
    dest_root = dest_tree.getroot()
    if source_root.tag != "resources" or dest_root.tag != "resources":
        raise RuntimeError(f"Unexpected values XML root: {src} / {dst}")
    existing = {(child.tag, child.attrib.get("name")) for child in dest_root}
    for child in source_root:
        key = (child.tag, child.attrib.get("name"))
        if key not in existing:
            dest_root.append(child)
            existing.add(key)
    dest_tree.write(dst, encoding="utf-8", xml_declaration=True)

def merge_icon_map(src: Path, dst: Path) -> None:
    source_tree = ET.parse(src)
    source_root = source_tree.getroot()
    if not dst.exists():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    dest_tree = ET.parse(dst)
    dest_root = dest_tree.getroot()
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
    dest_tree.write(dst, encoding="utf-8", xml_declaration=True)

for src in sorted(GENERATED.rglob("*")):
    if not src.is_file():
        continue
    rel = src.relative_to(GENERATED)
    dst = DEST / rel
    if rel.parts[0].startswith("values") and src.suffix == ".xml":
        merge_values(src, dst)
    elif rel.as_posix() == "xml/grayscale_icon_map.xml":
        merge_icon_map(src, dst)
    else:
        dst.parent.mkdir(parents=True, exist_ok=True)
        if dst.exists():
            if dst.read_bytes() == src.read_bytes():
                continue
            raise RuntimeError(f"Resource name collision; refusing to overwrite: {dst}")
        shutil.copy2(src, dst)

count = sum(1 for path in GENERATED.rglob("*") if path.is_file())
print(f"Merged {count} generated Lawnicons resource files from {SOURCE} into {DEST}")
