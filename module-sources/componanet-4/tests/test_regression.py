"""Synthetic regression tests: measured indicators, not a fraud-detection benchmark.
Run from project root: python -m unittest discover -s tests -v
"""
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance
from src.analysis_pipeline import run_analysis_pipeline

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / 'data/genuine/nic_genuine_01.jpg'
DRIVER = ROOT / 'data/genuine/driving_licence_genuine_01.jpg'


class DocumentScreeningRegressions(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.work = Path(self.tmp.name)

    def run_document(self, path, label, ref=None, ocr=None, kind='NIC'):
        return run_analysis_pipeline(path, kind, self.work / label,
            ocr_validation={'ocr_text_present':True, 'issues':[]},
            ocr=ocr or {}, trusted_reference_path=ref)

    def changed(self, region, label):
        img = Image.open(SAMPLE).convert('RGB')
        draw = ImageDraw.Draw(img)
        if label == 'photo':
            draw.rectangle(region, fill=(210,190,150))
            draw.ellipse((122,245,235,360), fill=(130,110,100))
        else:
            draw.rectangle(region, fill=(244,240,230))
            draw.text((region[0]+3,region[1]+3), 'NEW FIELD 12345', fill=(40,32,21))
        path=self.work / f'edited_{label}.png'
        img.save(path)
        return path

    def test_clean_nic_low(self):
        d=self.run_document(SAMPLE,'clean')
        self.assertEqual(d['status'],'COMPLETE')
        self.assertEqual(d['risk_level'],'LOW')
        self.assertEqual(d['referenceComparisonStatus'],'NOT_PROVIDED')

    def test_clean_driving_licence_low(self):
        d=self.run_document(DRIVER,'driver',kind='DRIVING_LICENCE')
        self.assertEqual(d['status'],'COMPLETE')
        self.assertEqual(d['risk_level'],'LOW')

    def test_replaced_name_background_without_reference(self):
        path=self.changed((500,300,720,335),'name')
        d=self.run_document(path,'edited_name')
        self.assertGreaterEqual(d['risk_score'],35)
        self.assertTrue(any(f['code']=='FIELD_BACKGROUND_DISCONTINUITY' for f in d['risk_details']['findings']))

    def test_replaced_id_without_reference(self):
        path=self.changed((734,128,898,155),'id')
        d=self.run_document(path,'edited_id')
        self.assertGreaterEqual(d['risk_score'],35)

    def test_photo_paste_seam_without_reference(self):
        path=self.changed((90,225,270,405),'photo')
        d=self.run_document(path,'edited_photo')
        self.assertTrue(any(f['code']=='RECTANGULAR_OVERLAY_EDGE' and f['region']=='photo' for f in d['risk_details']['findings']))

    def test_name_change_with_trusted_reference(self):
        path=self.changed((500,300,720,335),'name_ref')
        d=self.run_document(path,'matched',ref=SAMPLE)
        self.assertEqual(d['referenceComparisonStatus'],'ASSESSED')
        self.assertEqual(d['risk_level'],'HIGH')
        self.assertTrue(any(f['code']=='REFERENCE_REGION_CHANGED' and f['region']=='name' for f in d['risk_details']['findings']))

    def test_ordinary_recapture_variation(self):
        img=Image.open(SAMPLE).convert('RGB')
        img=ImageEnhance.Brightness(img).enhance(1.07)
        img=ImageEnhance.Contrast(img).enhance(.94)
        path=self.work/'recapture.jpg'; img.save(path,quality=76)
        d=self.run_document(path,'recapture',ref=SAMPLE)
        self.assertEqual(d['status'],'COMPLETE')
        self.assertNotEqual(d['risk_level'],'HIGH')

    def test_editor_ui_markers(self):
        ocr={'fullText':'SRI LANKA NATIONAL IDENTITY CARD Show toolbar Calibri Background fill'}
        d=self.run_document(SAMPLE,'editor',ocr=ocr)
        self.assertEqual(d['risk_level'],'HIGH')
        self.assertTrue(any(f['code']=='VISIBLE_EDITOR_ARTIFACTS' for f in d['risk_details']['findings']))


if __name__ == '__main__': unittest.main()
