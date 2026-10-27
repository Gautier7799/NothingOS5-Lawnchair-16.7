#!/usr/bin/env python3
"""Merge generated Lawnicons resources into Lawnchair for an APK build."""
from pathlib import Path
import re
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

# Lawnchair already defines a few generic themed icons (notably calendar/clock).
# Keep both implementations by giving colliding generated resource files a unique
# Lawnicons prefix and rewriting references before copying them.
rename_map = {}
for src in GENERATED.rglob("*"):
    if not src.is_file() or src.suffix not in {".xml", ".png", ".webp", ".jpg", ".jpeg"}:
        continue
    rel = src.relative_to(GENERATED)
    if rel.parts[0].startswith("values") or rel.as_posix() == "xml/grayscale_icon_map.xml":
        continue
    dst = DEST / rel
    if dst.exists() and dst.read_bytes() != src.read_bytes():
        renamed = rel.with_name("lawnicons_" + rel.name)
        renamed_dst = DEST / renamed
        if renamed_dst.exists() and renamed_dst.read_bytes() != src.read_bytes():
            raise RuntimeError(f"Renamed Lawnicons resource also collides: {renamed_dst}")
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
        # Merge values resources while preserving Lawnchair definitions on name conflicts.
        merge_values(src, DEST / rel)
        continue
    if rel.as_posix() == "xml/grayscale_icon_map.xml":
        # Rewrite map references to any renamed Lawnicons drawables.
        temp = src.with_name("grayscale_icon_map.lawnchair-merge.xml")
        temp.write_bytes(rewrite_refs(src.read_bytes()))
        try:
            merge_icon_map(temp, DEST / rel)
        finally:
            temp.unlink(missing_ok=True)
        continue

    target_rel = rel
    dst = DEST / target_rel
    if rel.stem in rename_map and dst.exists() and dst.read_bytes() != src.read_bytes():
        target_rel = rel.with_name("lawnicons_" + rel.name)
        dst = DEST / target_rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    data = rewrite_refs(src.read_bytes()) if src.suffix == ".xml" else src.read_bytes()
    if dst.exists():
        if dst.read_bytes() == data:
            continue
        raise RuntimeError(f"Resource name collision after renaming: {dst}")
    dst.write_bytes(data)

count = sum(1 for path in GENERATED.rglob("*") if path.is_file())
print(f"Merged {count} generated Lawnicons resource files from {SOURCE} into {DEST}; renamed {len(rename_map)} colliding resources")
