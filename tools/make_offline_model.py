#!/usr/bin/env python3
"""
Builds the small offline Bangla voice model that the app downloads from Settings.

Input : the official sherpa-onnx model folder
        (sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09, Apache-2.0, from Alpha Cephei's Vosk).
Output: <out>/tf-bangla-offline.zip  (encoder+joiner shrunk to int8, about 22 MB)

Before the zip is accepted it is test-run on the two sample recordings that come with the model;
if the Bangla text is not what it should be, this script fails and the app build stops.
Run by .github/workflows/build-apk.yml. No secrets involved.
"""
import hashlib
import os
import sys
import wave
import zipfile

import numpy as np
import sherpa_onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

src, out = sys.argv[1], sys.argv[2]
work = os.path.join(out, "int8")
os.makedirs(work, exist_ok=True)

for name in ("encoder", "joiner"):
    quantize_dynamic(os.path.join(src, f"{name}.onnx"), os.path.join(work, f"{name}.int8.onnx"), weight_type=QuantType.QUInt8)
for name in ("decoder.onnx", "tokens.txt"):
    with open(os.path.join(src, name), "rb") as f, open(os.path.join(work, name), "wb") as g:
        g.write(f.read())
with open(os.path.join(work, "NOTICE.txt"), "w", encoding="utf-8") as f:
    f.write(
        "TF Voice Typer - offline Bangla speech model\n\n"
        "Derived from: sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09 (official k2-fsa/sherpa-onnx release),\n"
        "which packages alphacep/vosk-model-small-streaming-bn (Alpha Cephei). License: Apache-2.0.\n"
        "Change: encoder and joiner weights were dynamically quantized to int8 with ONNX Runtime.\n"
    )


def recognize(wav_path):
    r = sherpa_onnx.OnlineRecognizer.from_transducer(
        tokens=os.path.join(work, "tokens.txt"),
        encoder=os.path.join(work, "encoder.int8.onnx"),
        decoder=os.path.join(work, "decoder.onnx"),
        joiner=os.path.join(work, "joiner.int8.onnx"),
        num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search",
    )
    w = wave.open(wav_path)
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    s = r.create_stream()
    s.accept_waveform(16000, a)
    s.accept_waveform(16000, np.zeros(8000, dtype=np.float32))
    s.input_finished()
    while r.is_ready(s):
        r.decode_stream(s)
    return r.get_result(s)


# Smoke test: both sample sentences must come out as nearly the same Bangla as the original (fp32) model gives
import difflib
import unicodedata

EXPECTED = {
    "0.wav": "এদিকে মিঃ তুগলক অরবিন কেজরিওয়াল এবং তার দল আপনায় ঘণ্টায় টুইট করছেন ক্রেডিটের জন্য",
    "1.wav": "তোমার দেশ তোমার জন্য কি করতে পারে তা জিজ্ঞেস করো না বরং তুমি তোমার দেশের জন্য কি করতে পারো তা জিজ্ঞেস করুম",
}
for wav_name, want in EXPECTED.items():
    got = recognize(os.path.join(src, "test_wavs", wav_name))
    got = got if isinstance(got, str) else getattr(got, "text", str(got))
    ratio = difflib.SequenceMatcher(None, unicodedata.normalize("NFC", got), unicodedata.normalize("NFC", want)).ratio()
    print(wav_name, f"similarity {ratio:.3f}:", got)
    assert ratio >= 0.9, f"int8 model gives wrong text for {wav_name}"

zip_path = os.path.join(out, "tf-bangla-offline.zip")
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for name in ("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt", "NOTICE.txt"):
        z.write(os.path.join(work, name), name)

data = open(zip_path, "rb").read()
print("zip size:", len(data), "sha256:", hashlib.sha256(data).hexdigest())
assert 5_000_000 < len(data) < 60_000_000, "model zip has an unexpected size"
