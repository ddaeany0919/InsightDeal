import os
import sys

_BACKEND_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)

for _path in (_PROJECT_ROOT, _BACKEND_DIR):
    if _path not in sys.path:
        sys.path.insert(0, _path)

collect_ignore = [
    "scrapers/test_ppomppu.py",
    "scripts/test_fmkorea.py",
]
