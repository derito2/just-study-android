"""design/splash.md: the private launch scenes (photo_*.svg) stay on the owner's Mac, never in git."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_no_private_splash_scene_is_tracked():
    tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.splitlines()
    assert [f for f in tracked if "assets/splash/photo_" in f] == []
