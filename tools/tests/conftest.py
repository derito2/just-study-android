"""Run with the web venv: PYTHONPATH=<web>/backend <venv>/python -m pytest tools/tests"""

import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(TOOLS / "golden"), str(TOOLS / "export")]
