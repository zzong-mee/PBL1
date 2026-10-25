"""Extract full MIT KEMAR SOFA at pinned hash. Dependencies numpy/h5py."""
import hashlib,struct,sys
from pathlib import Path
import h5py
import numpy as np
src,out=map(Path,sys.argv[1:])
assert hashlib.sha256(src.read_bytes()).hexdigest()=='2768ac841213a7ae11d1ea7fd0f25a69b39216102dc5dd913ea6ba0f0dc57e28'
with h5py.File(src) as f:
    assert f['Data.SamplingRate'][0]==44100 and np.all(f['Data.Delay'][:]==0)
    points=f['SourcePosition'][:]; ir=f['Data.IR'][:]
    assert ir.shape==(710,2,512) and np.isfinite(ir).all()
    out.write_bytes(b'TEHRIR2\n'+struct.pack('<III',44100,710,512)+b''.join(
        struct.pack('<ff',float(p[0]),float(p[1]))+h.astype('<f4').tobytes() for p,h in zip(points,ir)))
print(hashlib.sha256(out.read_bytes()).hexdigest())
