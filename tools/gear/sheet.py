"""Review sheet for several gear assets. python tools/gear/sheet.py out.png armor|item id1 id2 ..."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview as p  # noqa: E402
from PIL import Image  # noqa: E402

A = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/sourceofmystery'


def main(out, kind, ids, size=500):
    views = [(0, 5), (-40, 15)] if kind == 'item' else [(-30, 14), (0, 8), (150, 14)]
    W = Image.new('RGB', (size * len(ids), size * len(views)), (236, 238, 241))
    for i, n in enumerate(ids):
        d, q = p.load(A / f'geo/{kind}/{n}.geo.json')
        if kind == 'armor':
            q = q + p.mannequin()
        t = Image.open(A / f'textures/{"gear" if kind == "item" else kind}/{n}.png')
        for j, (yaw, pitch) in enumerate(views):
            W.paste(p.render(d, q, t, yaw, pitch, size=size, extent=19 if kind == 'item' else 21), (i * size, j * size))
    W.save(out)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
