#!/usr/bin/env python3
"""Convert a Hald CLUT PNG into a .cube 3D LUT.

    python3 scripts/hald_to_cube.py input.png output.cube --size 33 --title "Golden"

Needs Pillow and NumPy (pip install pillow numpy).
"""
import argparse

import numpy as np
from PIL import Image


def load_hald(path):
    img = np.asarray(Image.open(path).convert("RGB"), dtype=np.float64) / 255.0
    width = img.shape[1]
    level = round(width ** (1 / 3))
    if level ** 3 != width or img.shape[0] != width:
        raise SystemExit(f"{path}: {img.shape[1]}x{img.shape[0]} is not a Hald CLUT")
    n = level * level
    # Hald order: red changes fastest, then green, then blue.
    return img.reshape(-1, 3).reshape(n, n, n, 3)  # indexed [b][g][r]


def resample(cube, size):
    n = cube.shape[0]
    pos = np.linspace(0, n - 1, size)
    lo = np.floor(pos).astype(int)
    hi = np.minimum(lo + 1, n - 1)
    f = pos - lo

    def lerp_axis(a, axis):
        a_lo = np.take(a, lo, axis=axis)
        a_hi = np.take(a, hi, axis=axis)
        shape = [1] * a.ndim
        shape[axis] = size
        w = f.reshape(shape)
        return a_lo * (1 - w) + a_hi * w

    out = cube
    for axis in range(3):
        out = lerp_axis(out, axis)
    return out  # still [b][g][r]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("output")
    p.add_argument("--size", type=int, default=33)
    p.add_argument("--title", default=None)
    p.add_argument("--comment", action="append", default=[])
    args = p.parse_args()

    lut = resample(load_hald(args.input), args.size)
    with open(args.output, "w") as fh:
        for c in args.comment:
            fh.write(f"# {c}\n")
        if args.title:
            fh.write(f'TITLE "{args.title}"\n')
        fh.write(f"LUT_3D_SIZE {args.size}\n")
        for rgb in lut.reshape(-1, 3):  # red fastest, as .cube expects
            fh.write("%.6f %.6f %.6f\n" % tuple(np.clip(rgb, 0, 1)))


if __name__ == "__main__":
    main()
