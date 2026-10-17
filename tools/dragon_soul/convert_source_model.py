"""Import the actual 1ba source via the maintained asset generator."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("generate_assets.py")),run_name="__main__")
