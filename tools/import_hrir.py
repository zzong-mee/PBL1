"""Convert SADIE II D2 48kHz SOFA to Third Eye binary HRIR asset.
Requires numpy and h5py. Obtain source and LICENSE.txt from
https://zenodo.org/records/12092466 . Copyright 2018 University of York.
Modified format and azimuth convention; Apache-2.0.
"""
import argparse
import pathlib
import struct
import h5py
import numpy as np

p = argparse.ArgumentParser()
p.add_argument("sofa", type=pathlib.Path)
p.add_argument("--output", type=pathlib.Path, default=pathlib.Path("app/src/main/assets/hrir.bin"))
args = p.parse_args()
with h5py.File(args.sofa, "r") as f:
    ir = np.asarray(f["Data.IR"], dtype=np.float32)
    pos = np.asarray(f["SourcePosition"], dtype=float)
    rate = np.asarray(f["Data.SamplingRate"])
    delay = np.asarray(f["Data.Delay"], dtype=float)
    def attr(ds, key):
        v = ds.attrs[key]
        return v.decode() if isinstance(v, bytes) else str(v)
    assert attr(f["SourcePosition"], "Type").lower() == "spherical"
    assert "degree" in attr(f["SourcePosition"], "Units").lower()
    assert np.all(rate == 48000)
    assert ir.ndim == 3 and ir.shape[1] == 2 and 1 <= ir.shape[2] <= 2048
    assert pos.shape == (ir.shape[0], 3)
    assert np.isfinite(ir).all() and np.isfinite(pos).all()
    # Never silently discard additional SOFA delay metadata.
    if not np.all(delay == 0):
        raise ValueError("Nonzero Data.Delay requires explicit unit-aware conversion")
    receivers = np.asarray(f["ReceiverPosition"]).reshape(2, 3)
    assert receivers[0, 1] > 0 and receivers[1, 1] < 0, "Verify ear ordering"
    assert np.all(np.any(ir != 0, axis=2))
    az = (-pos[:,0]+180)%360-180
    el = pos[:,1]
    assert np.all(np.abs(el) <= 90)
    assert 1 <= len(az) <= 10000
args.output.parent.mkdir(parents=True, exist_ok=True)
with args.output.open("wb") as out:
    out.write(struct.pack(">4sIII", b"HRIR", 48000, len(az), ir.shape[2]))
    for i in range(len(az)):
        out.write(struct.pack(">ff", az[i], el[i]))
        out.write(ir[i].astype(">f4").tobytes())
print(f"Converted {len(az)} directions to {args.output}")
