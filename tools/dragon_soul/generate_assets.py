"""Authoritative Dragon Soul asset build: imports and details the user's 1ba rig."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("enhance_1ba.py")), run_name="__main__")
