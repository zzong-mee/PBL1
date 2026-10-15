"""Run the bundled model on a supplied image; no fake inference or expected outputs substituted.
Usage: python tools/validate_yolox.py /path/to/image.jpg
Requires onnx==1.17.0, onnxruntime==1.20.1, numpy, opencv-python in the Python environment.
"""
import sys
from pathlib import Path
import cv2
import numpy as np
import onnx
import onnxruntime as ort

model = Path(__file__).resolve().parents[1] / "app/src/main/assets/yolox_nano.onnx"
onnx.checker.check_model(onnx.load(str(model)))
session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
assert session.get_inputs()[0].shape == [1, 3, 416, 416]
im = cv2.imread(sys.argv[1])
assert im is not None
h, w = im.shape[:2]
r = min(416 / h, 416 / w)
canvas = np.full((416, 416, 3), 114, np.uint8)
canvas[:int(h*r), :int(w*r)] = cv2.resize(im, (int(w*r), int(h*r)))
output = session.run(None, {session.get_inputs()[0].name: np.ascontiguousarray(canvas.transpose(2,0,1), dtype=np.float32)[None]})[0][0]
assert output.shape == (3549, 85)
index = 0
candidates = []
for stride in (8, 16, 32):
    for gy in range(416//stride):
        for gx in range(416//stride):
            row = output[index]; index += 1
            cls = int(row[5:].argmax()); score = float(row[4] * row[5+cls])
            if score < .35: continue
            x,y = (row[:2] + [gx,gy]) * stride
            bw,bh = np.exp(row[2:4]) * stride
            box = np.clip(np.array([(x-bw/2)/r/w,(y-bh/2)/r/h,(x+bw/2)/r/w,(y+bh/2)/r/h]),0,1)
            if box[2] > box[0] and box[3] > box[1]: candidates.append((score,cls,box))
kept=[]
for item in sorted(candidates, key=lambda x:-x[0]):
    a=item[2]
    def overlaps(other):
        b=other[2]; intersection=max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))
        union=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-intersection
        return item[1] == other[1] and union > 0 and intersection/union > .45
    if not any(overlaps(other) for other in kept): kept.append(item)
print("ONNX shape and graph validated; detections:")
for score,cls,box in kept: print(f"class_id={cls} confidence={score:.3f} box={box.round(3).tolist()}")
if not kept: raise SystemExit("No confident detections in the supplied test image")
