"""Unapproved/variable-layout screening for utility bills and salary slips.

There is deliberately no fake 'matching' against unrelated sample artwork.
The generic pipeline can provide OCR consistency and visible editor indicators,
but never claims approved-layout verification or authenticity.
"""
from pathlib import Path
import re
import numpy as np
from PIL import Image
from src.forensic_analysis import scan_field_surface, inspect_ocr, aggregate_risk, annotate
from src.physical_obstruction import detect_obstructions


def run_layout_free(image_path, kind, output, ocr, validation):
    output=Path(output); (output/'normalized').mkdir(parents=True,exist_ok=True)
    (output/'preprocessing').mkdir(parents=True,exist_ok=True)
    with Image.open(image_path) as f:
        rgb=np.asarray(f.convert('RGB'))
    h,w=rgb.shape[:2]
    Image.fromarray(rgb).save(output/'normalized'/'document.png')
    Image.fromarray(rgb).save(output/'preprocessing'/'cropped_document.png')
    # No fixed ROI, as salary slips/bills differ by issuer. The detector is
    # low-confidence and must never produce template-based findings.
    regions={'document_body':{'x1':int(.055*w),'y1':int(.10*h),'x2':int(.94*w),'y2':int(.93*h),'type':'text'}}
    findings=inspect_ocr(ocr,regions,rgb.shape)
    # Physical object detection needs a real issuer-specific template, so skip
    # skin-colour classification in layout-free mode to avoid massive false positives.
    risk=aggregate_risk(findings,'NOT_PROVIDED')
    annotate(rgb,risk['findings'],output/'highlighted_suspicious_areas.png')
    result={'status':'COMPLETE', 'document_type':kind,'template':None,
        'templateVerificationStatus':'NO_APPROVED_TEMPLATE',
        'referenceComparisonStatus':'NOT_PROVIDED', 'normalized_width':w,'normalized_height':h,
        'suspicious_area_count':risk['finding_count'],'altered_text_localization':{
          'method':'ocr_indicators_only','suspicious_areas':risk['findings'],
          'suspicious_area_count':risk['finding_count']},
        'risk_score':risk['risk_score'],'risk_level':risk['risk_level'],
        'risk_details':risk,'recommended_action':'MANUAL_REVIEW_REQUIRED',
        'coverageWarning':'No approved layout template exists for this issuer/document edition. OCR and field cross-check only; visual tampering checks are limited.'}
    risk['detector_coverage']['not_assessed'] += ['fixed_artwork','template_field_background','physical_occlusion_layout', 'photo_authenticity','signature_authenticity']
    return result
