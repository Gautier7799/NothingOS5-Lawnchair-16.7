#!/usr/bin/env python3
"""Tests for scripts/merge_lawnicons.py.

The merge script runs as a build step, so these tests invoke it as a subprocess
against a synthetic Lawnicons checkout and a synthetic lawnchair/res tree.
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "merge_lawnicons.py"

ICON_MAP = """<?xml version="1.0" encoding="utf-8"?>
<icons>
    <icon drawable="@drawable/icon_spotify" package="com.spotify.music" />
</icons>
"""
CALENDAR_GENERATED = "<vector/>"
CALENDAR_BASE = "<vector-base/>"


def build_fixture(root: Path) -> tuple[Path, Path, Path]:
    """Create a fake lawnicons checkout, source tree and output directory."""
    repo = root / "repo"
    (repo / "lawnchair/res/drawable").mkdir(parents=True)
    (repo / "lawnchair/res/xml").mkdir(parents=True)
    (repo / "lawnchair/res/values").mkdir(parents=True)
    (repo / "lawnchair/res/drawable/themed_icon_calendar_1.xml").write_text(CALENDAR_BASE)
    (repo / "lawnchair/res/xml/grayscale_icon_map.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<icons>\n'
        '    <icon drawable="@array/grayscale_calendar_icons" package="com.google.android.calendar" />\n'
        "</icons>\n"
    )
    (repo / "lawnchair/res/values/colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <color name="base_only">#FF000000</color>\n'
        "</resources>\n"
    )

    checkout = root / "checkout"
    generated = checkout / "app/src/runtime/res"
    (generated / "drawable").mkdir(parents=True)
    (generated / "xml").mkdir(parents=True)
    # Same name, different content: must be renamed and must not overwrite the base file.
    (generated / "drawable/themed_icon_calendar_1.xml").write_text(CALENDAR_GENERATED)
    (generated / "drawable/icon_spotify.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'android:fillColor="@color/primaryForeground" />'
    )
    (generated / "xml/grayscale_icon_map.xml").write_text(ICON_MAP)

    out = root / "out"
    return repo, checkout, out


class MergeLawniconsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.repo, self.checkout, self.out = build_fixture(self.root)

    def tearDown(self):
        self.tmp.cleanup()

    def run_merge(self, repo: Path, checkout: Path, out: Path) -> subprocess.CompletedProcess:
        # The script resolves lawnchair/res relative to its own location, so the
        # fixture is exercised through a copy placed inside the fake repo.
        script = repo / "scripts" / "merge_lawnicons.py"
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_bytes(SCRIPT.read_bytes())
        return subprocess.run(
            [sys.executable, str(script), str(checkout), str(out)],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_merged_output_is_a_complete_resource_tree(self):
        result = self.run_merge(self.repo, self.checkout, self.out)
        self.assertEqual(result.returncode, 0, result.stderr)

        # Base resources are copied so the tree can replace lawnchair/res entirely.
        self.assertTrue((self.out / "values/colors.xml").is_file())
        self.assertIn("base_only", (self.out / "values/colors.xml").read_text())
        # Generated-only resources are added.
        self.assertTrue((self.out / "drawable/icon_spotify.xml").is_file())

    def test_colliding_resource_is_renamed_and_base_is_preserved(self):
        result = self.run_merge(self.repo, self.checkout, self.out)
        self.assertEqual(result.returncode, 0, result.stderr)

        self.assertEqual(
            (self.out / "drawable/themed_icon_calendar_1.xml").read_text(), CALENDAR_BASE
        )
        renamed = self.out / "drawable/lawnicons_themed_icon_calendar_1.xml"
        self.assertTrue(renamed.is_file())
        self.assertEqual(renamed.read_text(), CALENDAR_GENERATED)

    def test_icon_map_keeps_lawnchair_entries(self):
        result = self.run_merge(self.repo, self.checkout, self.out)
        self.assertEqual(result.returncode, 0, result.stderr)

        merged = (self.out / "xml/grayscale_icon_map.xml").read_text()
        self.assertIn("com.google.android.calendar", merged)
        self.assertIn("com.spotify.music", merged)

    def test_color_fallback_does_not_override_existing_colors(self):
        result = self.run_merge(self.repo, self.checkout, self.out)
        self.assertEqual(result.returncode, 0, result.stderr)

        colors = (self.out / "values/colors_lawnicons.xml").read_text()
        self.assertIn("primaryForeground", colors)
        self.assertNotIn("base_only", colors)

    def test_repeated_run_is_stable(self):
        first = self.run_merge(self.repo, self.checkout, self.out)
        self.assertEqual(first.returncode, 0, first.stderr)
        snapshot = {
            path.relative_to(self.out).as_posix(): path.read_bytes()
            for path in self.out.rglob("*")
            if path.is_file()
        }

        second = subprocess.run(
            [sys.executable, str(self.repo / "scripts/merge_lawnicons.py"),
             str(self.checkout), str(self.out)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(second.returncode, 0, second.stderr)
        after = {
            path.relative_to(self.out).as_posix(): path.read_bytes()
            for path in self.out.rglob("*")
            if path.is_file()
        }
        self.assertEqual(snapshot, after)


if __name__ == "__main__":
    unittest.main()
