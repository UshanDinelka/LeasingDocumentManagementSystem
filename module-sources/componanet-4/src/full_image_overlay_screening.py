"""Unsupervised full-document candidate-rectangle screening.

This does not use trained alteration examples or fixed name coordinates. It
examines overlapping windows at two scales to reduce missed regions caused by
tile-local illumination. This check flags only *visibly abnormal* rectangular
patches; seamless alterations are impossible to prove without a reference.
"""
from PIL import Image
from src.opaque_patch_detection import detect_opaque_text_overlays

BUILD = "full-image-opaque-windows-v1"


def detect_anywhere_opaque_patches(image_path):
    with Image.open(image_path) as source:
        width, height = source.size
    regions = {}
    for rows, columns in ((3, 3),):
        for row in range(rows):
            for column in range(columns):
                left = max(int(width*.02), int(width*(column-.25)/columns))
                top = max(int(height*.02), int(height*(row-.25)/rows))
                right = min(int(width*.98), int(width*(column+1.25)/columns))
                bottom = min(int(height*.98), int(height*(row+1.25)/rows))
                regions[f"full_image_{rows}x{columns}_{row}_{column}"] = {
                    "type": "text", "x1": left, "y1": top,
                    "x2": right, "y2": bottom,
                }
    all_findings = detect_opaque_text_overlays(image_path, regions)
    accepted = []
    for finding in sorted(all_findings, key=lambda item: item.get('score', 0), reverse=True):
        def overlap(a, b):
            dx = max(0, min(a['x2'], b['x2'])-max(a['x1'], b['x1']))
            dy = max(0, min(a['y2'], b['y2'])-max(a['y1'], b['y1']))
            area1 = max(1,(a['x2']-a['x1'])*(a['y2']-a['y1']))
            area2 = max(1,(b['x2']-b['x1'])*(b['y2']-b['y1']))
            return dx*dy/min(area1,area2)
        if any(overlap(finding, other) > .55 for other in accepted):
            continue
        finding['region'] = 'anywhere_in_document'
        finding['detectionMethod'] = BUILD
        finding['reason'] = ('Local, unusually uniform rectangular patch found without using predefined field positions. '
                             'May be a pasted label, ordinary printing, glare, or display artifact; human review required.')
        accepted.append(finding)
    return accepted[:15]
