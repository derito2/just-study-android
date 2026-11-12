"""Fails (exit 1) if a merged AndroidManifest breaks the plan's security rules (docs/PLAN.md, point 8):
a forbidden permission, an exported component other than MainActivity, or a debuggable release.

    python3 tools/ci/check_manifest.py            # the debug and release manifests of :app (after assemble)
    python3 tools/ci/check_manifest.py <file>...  # given files; "release" in the path means a release manifest

Standard library only.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[2]
# The installed APKs only: the test APKs (debugAndroidTest, debugUnitTest) carry androidx.test's own activities.
MANIFESTS = [p for v in ("debug", "release") for p in sorted(ROOT.glob(f"app/build/intermediates/merged_manifest/{v}/*/AndroidManifest.xml"))]
ALLOWED_EXPORTED = {"com.dero.juststudykorean.MainActivity"}
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.GET_ACCOUNTS",
}
FORBIDDEN_PREFIXES = ("android.permission.READ_MEDIA_", "android.permission.ACCESS_FINE_LOCATION",
                      "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION")
# Plan §8: the only Android permissions the app may have (anything else, from us or a library, fails).
ALLOWED_PERMISSIONS = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.USE_BIOMETRIC",
    # Phase 6 (owner's OK, 2026-11-12): ML Kit genai-prompt binds to AICore, the system app that runs Gemini Nano.
    "com.google.android.apps.aicore.service.BIND_SERVICE",
}
OWN_PREFIX = "com.dero.juststudykorean."  # the app's own signature permissions (e.g. DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)

COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")


def problems(path: Path, release: bool) -> list[str]:
    root = ET.parse(path).getroot()
    found = []
    for tag in ("uses-permission", "uses-permission-sdk-23"):
        for node in root.iter(tag):
            name = node.get(ANDROID + "name", "")
            if name in FORBIDDEN or name.startswith(FORBIDDEN_PREFIXES):
                found.append(f"forbidden permission: {name}")
            elif name not in ALLOWED_PERMISSIONS and not name.startswith(OWN_PREFIX):
                found.append(f"permission not allowed: {name}")
    app = root.find("application")
    if app is not None:
        for tag in COMPONENTS:
            for node in app.iter(tag):
                name = node.get(ANDROID + "name", "")
                if node.get(ANDROID + "exported") == "true" and name not in ALLOWED_EXPORTED:
                    found.append(f"exported {tag}: {name}")
        if release and app.get(ANDROID + "debuggable") == "true":
            found.append("release is debuggable")
    return found


def main(argv: list[str] | None = None) -> int:
    paths = [Path(p) for p in (argv if argv is not None else sys.argv[1:])] or MANIFESTS
    if not paths:
        print("no merged manifests found: run ./gradlew assembleDebug assembleRelease first", file=sys.stderr)
        return 2
    failed = False
    for path in paths:
        found = problems(path, release="release" in str(path).lower())
        rel = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
        print(f"{'FAIL' if found else 'OK  '} {rel}")
        for p in found:
            print(f"     {p}")
        failed |= bool(found)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
