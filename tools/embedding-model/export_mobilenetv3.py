#!/usr/bin/env python3
"""Export MobileNetV3-Small to an ONNX visual-embedding model.

This produces the blob bundled at
`src/main/resources/models/mobilenetv3-small.onnx` and loaded by
`OnnxEmbeddingModel`. The model is the ImageNet-pretrained `mobilenetv3_small_100`
backbone from `timm`, with its 1000-way classifier removed (`num_classes=0`) so
the graph's single output is the global-average-pooled feature vector. That
vector's cosine distance is what the similarity grouper clusters on.

Provenance / license: the weights are timm's own `mobilenetv3_small_100.lamb_in1k`
checkpoint (Apache-2.0, see the timm model card). The export is deterministic:
same timm version + weight tag -> same graph and weights.

Reproduce (see requirements.txt for pinned versions):

    python -m venv .venv && source .venv/bin/activate
    pip install -r requirements.txt
    python export_mobilenetv3.py --out ../../src/main/resources/models/mobilenetv3-small.onnx

The script prints the output dimension and the SHA-256 of the written file;
record the hash in README.md so a re-export can be verified byte-for-byte.
"""

import argparse
import hashlib
from pathlib import Path

import timm
import torch

# Pin the exact pretrained checkpoint so the export is reproducible. This is a
# timm-trained (Apache-2.0) weight, not a ported third-party one.
WEIGHT_TAG = "mobilenetv3_small_100.lamb_in1k"
INPUT_NAME = "pixel_values"   # [N, 3, 224, 224], ImageNet-normalized RGB, NCHW
OUTPUT_NAME = "embedding"     # [N, num_features]
OPSET = 17
EDGE = 224


def build_model() -> torch.nn.Module:
    # num_classes=0 drops the classifier; forward() then returns the pooled
    # feature vector (the embedding) rather than class logits.
    model = timm.create_model(WEIGHT_TAG, pretrained=True, num_classes=0)
    model.eval()
    return model


def export(out_path: Path) -> None:
    model = build_model()
    with torch.no_grad():
        dim = int(model(torch.zeros(1, 3, EDGE, EDGE)).shape[-1])
    print(f"output dimension: {dim}")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.zeros(1, 3, EDGE, EDGE)
    torch.onnx.export(
        model,
        dummy,
        str(out_path),
        input_names=[INPUT_NAME],
        output_names=[OUTPUT_NAME],
        # Batch is dynamic so a future batched extractor can feed N frames at once;
        # the app embeds one frame per call today.
        dynamic_axes={INPUT_NAME: {0: "batch"}, OUTPUT_NAME: {0: "batch"}},
        opset_version=OPSET,
        do_constant_folding=True,
    )

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    size_mb = out_path.stat().st_size / (1024 * 1024)
    print(f"wrote {out_path} ({size_mb:.2f} MB)")
    print(f"sha256: {digest}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parents[2]
        / "src/main/resources/models/mobilenetv3-small.onnx",
        help="Destination .onnx path (default: the bundled resource location).",
    )
    args = parser.parse_args()
    export(args.out)


if __name__ == "__main__":
    main()
