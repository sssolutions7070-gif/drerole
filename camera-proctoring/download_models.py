"""Downloads the pretrained models the proctoring service uses for face and phone detection.
Run once before starting app.py (the Dockerfile does this at build time). If these files are
missing, app.py falls back to the older, less accurate Haar-cascade/heuristic detectors instead
of failing to start.
"""
import os
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

FILES = [
    (
        "models/face_detector/deploy.prototxt",
        "https://raw.githubusercontent.com/opencv/opencv/master/samples/dnn/face_detector/deploy.prototxt",
    ),
    (
        "models/face_detector/res10_300x300_ssd_iter_140000_fp16.caffemodel",
        "https://raw.githubusercontent.com/opencv/opencv_3rdparty/dnn_samples_face_detector_20180205_fp16/"
        "res10_300x300_ssd_iter_140000_fp16.caffemodel",
    ),
    (
        "models/yolo/yolov4-tiny.cfg",
        "https://raw.githubusercontent.com/AlexeyAB/darknet/master/cfg/yolov4-tiny.cfg",
    ),
    (
        "models/yolo/yolov4-tiny.weights",
        "https://github.com/AlexeyAB/darknet/releases/download/yolov4/yolov4-tiny.weights",
    ),
    (
        "models/yolo/coco.names",
        "https://raw.githubusercontent.com/pjreddie/darknet/master/data/coco.names",
    ),
]


def download(relative_path, url):
    destination = os.path.join(BASE_DIR, relative_path)
    if os.path.exists(destination) and os.path.getsize(destination) > 0:
        print(f"already present: {relative_path}")
        return

    os.makedirs(os.path.dirname(destination), exist_ok=True)
    print(f"downloading {relative_path} ...")
    urllib.request.urlretrieve(url, destination)
    print(f"  -> {os.path.getsize(destination)} bytes")


if __name__ == "__main__":
    for relative_path, url in FILES:
        try:
            download(relative_path, url)
        except Exception as e:
            print(f"WARNING: failed to download {relative_path}: {e}")
            print("app.py will fall back to the Haar-cascade/heuristic detectors for this model.")
