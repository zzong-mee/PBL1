"""Extract MIT KEMAR horizontal HRIRs. Requires numpy/h5py; input is a verified local SOFA.
Usage: python tools/extract_hrtf.py input.sofa output.bin
Accompany output with MIT_KEMAR_LICENSE.txt and provenance; no per-ear normalization.
"""
import hashlib, struct, sys
from pathlib import Path
import h5py
import numpy as np
source, destination = map(Path, sys.argv[1:])
expected = '2768ac841213a7ae11d1ea7fd0f25a69b39216102dc5dd913ea6ba0f0dc57e28'
assert hashlib.sha256(source.read_bytes()).hexdigest() == expected, 'Unexpected SOFA source'
with h5py.File(source) as f:
    assert float(f['Data.SamplingRate'][0]) == 44100
    assert np.all(f['Data.Delay'][:] == 0), 'Do not drop nonzero SOFA delays'
    assert f['ReceiverPosition'][0,1,0] > 0 and f['ReceiverPosition'][1,1,0] < 0
    positions = f['SourcePosition'][:]
    records = []
    for azimuth in range(0,360,45):
        indices = np.flatnonzero(np.isclose(positions[:,0],azimuth) & np.isclose(positions[:,1],0))
        assert len(indices) == 1
        ir = f['Data.IR'][indices[0]]
        assert ir.shape == (2,512) and np.isfinite(ir).all()
        records.append(struct.pack('<i',azimuth) + ir.astype('<f4').tobytes())
    destination.write_bytes(b'TEHRIR1\n' + struct.pack('<III',44100,8,512) + b''.join(records))
print('source sha256:',hashlib.sha256(source.read_bytes()).hexdigest())
print('output sha256:',hashlib.sha256(destination.read_bytes()).hexdigest())
