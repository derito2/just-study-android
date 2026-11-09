"""tools/ci/check_manifest.py on sample merged manifests (no Android build needed)."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))

import check_manifest  # noqa: E402

CLEAN = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.dero.juststudykorean">
    <uses-permission android:name="com.dero.juststudykorean.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    <application android:name="com.dero.juststudykorean.JustStudyApp" android:allowBackup="false">
        <activity android:name="com.dero.juststudykorean.MainActivity" android:exported="true" />
        <provider android:name="androidx.startup.InitializationProvider" android:exported="false" />
        <service android:name="androidx.room.MultiInstanceInvalidationService" android:exported="false" />
    </application>
</manifest>
"""


def write(tmp_path, text, name="AndroidManifest.xml"):
    path = tmp_path / name
    path.write_text(text)
    return path


def test_clean_manifest_passes(tmp_path):
    assert check_manifest.problems(write(tmp_path, CLEAN), release=True) == []
    assert check_manifest.main([str(write(tmp_path, CLEAN))]) == 0


def test_internet_fails(tmp_path):
    bad = CLEAN.replace("<application", '<uses-permission android:name="android.permission.INTERNET" />\n    <application')
    path = write(tmp_path, bad)
    assert check_manifest.problems(path, release=False) == ["forbidden permission: android.permission.INTERNET"]
    assert check_manifest.main([str(path)]) == 1


def test_read_media_and_location_fail(tmp_path):
    bad = CLEAN.replace(
        "<application",
        '<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n    <application',
    )
    assert len(check_manifest.problems(write(tmp_path, bad), release=False)) == 2


def test_other_exported_component_fails(tmp_path):
    bad = CLEAN.replace(
        'android:name="androidx.startup.InitializationProvider" android:exported="false"',
        'android:name="androidx.startup.InitializationProvider" android:exported="true"',
    )
    assert check_manifest.problems(write(tmp_path, bad), release=False) == [
        "exported provider: androidx.startup.InitializationProvider"
    ]


def test_debuggable_release_fails_but_debug_is_fine(tmp_path):
    path = write(tmp_path, CLEAN.replace("<application ", '<application android:debuggable="true" '))
    assert check_manifest.problems(path, release=True) == ["release is debuggable"]
    assert check_manifest.problems(path, release=False) == []
