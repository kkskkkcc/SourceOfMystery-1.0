# Quaternius Universal Animation Library — local reference

Author / primary release: https://quaternius.com/packs/universalanimationlibrary.html

Downloaded glTF conversion: https://github.com/J-Ponzo/gltf-universal-animation-library

The included LICENSE is CC0. `quaternius_reference.py` reads the original sampled
transforms and SLERP interpolation. `combat_review.py` and `daily_motion.py` reuse
spine/neck/shoulder motion from Spell_Simple_Enter, Spell_Simple_Idle_Loop,
Spell_Simple_Shoot, Jump_Start and Jump_Land, retargeted through world-rest deltas.
PMX hand/foot targets are solved separately to preserve contact and staff grip.
This does not use extracted Dark Souls or Genshin combat animation data.
