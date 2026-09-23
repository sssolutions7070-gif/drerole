"""Verifies the face and phone detectors against real images (not mocks), and confirms the
service falls back to the Haar-cascade/heuristic methods when the model files are absent.
Run with: pytest test_detection.py
"""
import os

import cv2
import numpy as np
import pytest

import app

TEST_IMAGES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'test_images')

requires_models = pytest.mark.skipif(
    not app.FACE_DNN_AVAILABLE or not app.YOLO_AVAILABLE,
    reason="Model files not present; run download_models.py first.",
)


@pytest.fixture
def proctor():
    return app.Proctor('test-assignment', 1, 'Test')


@pytest.fixture
def face_image():
    img = cv2.imread(os.path.join(TEST_IMAGES_DIR, 'face_sample.jpg'))
    assert img is not None, "face_sample.jpg failed to load"
    return img


@pytest.fixture
def phone_image():
    img = cv2.imread(os.path.join(TEST_IMAGES_DIR, 'phone_sample.jpg'))
    assert img is not None, "phone_sample.jpg failed to load"
    return img


@pytest.fixture
def blank_frame():
    return np.zeros((480, 640, 3), dtype=np.uint8)


@requires_models
class TestFaceDetection:
    def test_detects_a_real_face(self, proctor, face_image):
        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        faces = proctor.detect_faces(gray, face_image)
        assert len(faces) == 1

    def test_returns_a_plausible_bounding_box(self, proctor, face_image):
        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        (x, y, w, h) = list(proctor.detect_faces(gray, face_image))[0]
        assert w > 0 and h > 0
        assert x >= 0 and y >= 0
        assert x + w <= face_image.shape[1]
        assert y + h <= face_image.shape[0]

    def test_no_false_positive_on_a_blank_frame(self, proctor, blank_frame):
        gray = cv2.cvtColor(blank_frame, cv2.COLOR_BGR2GRAY)
        assert len(proctor.detect_faces(gray, blank_frame)) == 0


@requires_models
class TestPhoneDetection:
    def test_detects_a_real_phone(self, proctor, phone_image):
        box = proctor.detect_mobile_phone(phone_image, [])
        assert box is not None
        x, y, w, h = box
        assert w > 0 and h > 0

    def test_no_false_positive_on_a_blank_frame(self, proctor, blank_frame):
        assert proctor.detect_mobile_phone(blank_frame, []) is None

    def test_no_false_positive_on_a_face_only_image(self, proctor, face_image):
        # A close-up portrait with no phone in it should not trigger the phone detector.
        assert proctor.detect_mobile_phone(face_image, []) is None


class TestFallbackWhenModelsAreMissing:
    """These run regardless of whether the real models are installed - they simulate absence."""

    def test_face_falls_back_to_haar_cascade(self, monkeypatch, face_image):
        monkeypatch.setattr(app, 'FACE_DNN_AVAILABLE', False)
        proctor_without_dnn = app.Proctor('fallback-test', 1, 'Test')
        assert proctor_without_dnn.face_net is None

        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        faces = proctor_without_dnn.detect_faces(gray, face_image)
        assert len(faces) >= 1

    def test_phone_falls_back_to_heuristic_without_crashing(self, monkeypatch, blank_frame):
        monkeypatch.setattr(app, 'YOLO_AVAILABLE', False)
        proctor_without_yolo = app.Proctor('fallback-test', 1, 'Test')
        assert proctor_without_yolo.yolo_net is None

        # Should not raise, regardless of the result.
        proctor_without_yolo.detect_mobile_phone(blank_frame, [])
