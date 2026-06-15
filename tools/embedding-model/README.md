# Embedding model export

Reproducible export of the visual-similarity embedding model bundled at
`src/main/resources/models/mobilenetv3-small.onnx` and loaded by
`OnnxEmbeddingModel` (the `GroupingMode.Similarity` lens).

## What it is

- **Architecture:** `mobilenetv3_small_100` backbone from [`timm`](https://github.com/huggingface/pytorch-image-models),
  ImageNet-pretrained, classifier removed (`num_classes=0`) so the single ONNX
  output is the global-average-pooled feature vector.
- **Weights:** timm's own `mobilenetv3_small_100.lamb_in1k` checkpoint, **Apache-2.0**
  (timm-trained, not a ported third-party weight). Safe to redistribute in the DMG.
- **Input:** `pixel_values`, `[batch, 3, 224, 224]`, ImageNet-normalized RGB, NCHW.
- **Output:** `embedding`, `[batch, 1024]`. The Kotlin side L2-normalizes it and
  clusters on cosine distance.

## Reproduce

CPU-only; no GPU needed. Requires Python 3.13 (the pinned wheels target cp313).

```bash
cd tools/embedding-model
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python export_mobilenetv3.py        # writes ../../src/main/resources/models/mobilenetv3-small.onnx
```

The export is deterministic — same `timm` version + weight tag yields the same
graph and weights.

## Expected output

```
output dimension: 1024
wrote .../mobilenetv3-small.onnx (5.80 MB)
sha256: 794b8710e860ec326006e6f034be12188ea58247ec04831b2bd4f9f05540e366
```

If you re-export and the SHA-256 differs, a dependency version drifted — check
`requirements.txt` is pinned and update this hash deliberately (and bump the
model `id` in `OnnxEmbeddingModel` so the on-disk embedding cache re-keys).

## Swapping the model

To try a different backbone, change `WEIGHT_TAG` in `export_mobilenetv3.py`,
re-export, update this README's hash, and bump `OnnxEmbeddingModel.DEFAULT_ID`.
`dimensions` is probed from the graph at load time, so a different feature width
needs no Kotlin change. Keep the input contract (224px square, ImageNet norm,
NCHW) or update `OnnxEmbeddingModel.preprocess` to match.
