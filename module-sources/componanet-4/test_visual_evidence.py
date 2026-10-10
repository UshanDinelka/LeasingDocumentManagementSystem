"""Regression checks for fixed artwork; run with the existing five templates."""
import json
import tempfile
import unittest
from pathlib import Path
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
from src.analysis_pipeline import extract_all_regions
from src.document_profiles import ROOT, TEMPLATE_FILES
from src.visual_evidence import analyse_visual_evidence, score_evidence


class VisualEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.folder = Path(self.workspace.name)
        self.config = json.loads((ROOT / "config/document_regions.json").read_text())

    def tearDown(self):
        self.workspace.cleanup()

    def measure(self, kind, image):
        submitted = self.folder / "submitted.png"
        image.save(submitted)
        metadata = extract_all_regions(kind, TEMPLATE_FILES[kind], submitted, self.config, self.folder)
        return analyse_visual_evidence(TEMPLATE_FILES[kind], submitted, kind, metadata)

    def test_unchanged_all_five(self):
        for kind,path in TEMPLATE_FILES.items():
            with self.subTest(kind=kind), Image.open(path) as original:
                self.assertEqual(self.measure(kind,original)['suspicious_area_count'],0)

    def test_brightness_and_compression_all_five(self):
        for kind,path in TEMPLATE_FILES.items():
            with Image.open(path) as original:
                for factor in [.75,1.15]:
                    with self.subTest(kind=kind,factor=factor):
                        changed = ImageEnhance.Brightness(original).enhance(factor)
                        changed.save(self.folder/'compressed.jpg',quality=70)
                        with Image.open(self.folder/'compressed.jpg') as submitted:
                            result = self.measure(kind,submitted)
                        self.assertEqual(result['suspicious_area_count'],0,result['suspicious_areas'])

    def test_blur_all_five(self):
        for kind,path in TEMPLATE_FILES.items():
            with self.subTest(kind=kind),Image.open(path) as original:
                result = self.measure(kind,original.filter(ImageFilter.GaussianBlur(1.2)))
                self.assertEqual(result['suspicious_area_count'],0,result['suspicious_areas'])

    def test_mild_colour_cast_and_gradual_shadow_all_five(self):
        for kind,path in TEMPLATE_FILES.items():
            with Image.open(path) as original:
                array=np.asarray(original.convert('RGB')).astype(float)
                h,w=array.shape[:2]
                variants={'warm':array*np.array([1.06,.98,.90]),
                          'shadow':array*(.72+.28*np.linspace(0,1,w))[None,:,None]}
                for name,pixels in variants.items():
                    with self.subTest(kind=kind,variant=name):
                        result=self.measure(kind,Image.fromarray(np.clip(pixels,0,255).astype('uint8')))
                        self.assertEqual(result['suspicious_area_count'],0,result['suspicious_areas'])
                        self.assertEqual(result['comparison_status'],'ASSESSED')

    def test_fixed_artwork_overlay_all_five(self):
        targets = {'NIC': (.14,.075,.20,.19), 'DRIVING_LICENCE': (.80,.43,.94,.66),
                   'BANK_STATEMENT': (.04,.05,.24,.12), 'BUSINESS_REGISTRATION': (.44,.06,.58,.17),
                   'VEHICLE_CR_BOOK': (.15,.078,.35,.125)}
        for kind,path in TEMPLATE_FILES.items():
            with self.subTest(kind=kind),Image.open(path) as original:
                array=np.array(original.convert('RGB'))
                h,w=array.shape[:2];x1,y1,x2,y2=targets[kind]
                box=(round(x1*w),round(y1*h),round(x2*w),round(y2*h))
                array[box[1]:box[3],box[0]:box[2]]=[190,20,160]
                result=self.measure(kind,Image.fromarray(array))
                self.assertGreater(result['suspicious_area_count'],0,result)
                overlaps=[max(0,min(box[2],a['x2'])-max(box[0],a['x1']))*max(0,min(box[3],a['y2'])-max(box[1],a['y1'])) for a in result['suspicious_areas']]
                self.assertGreater(max(overlaps)/((box[2]-box[0])*(box[3]-box[1])),.7)

    def test_different_personal_content_not_scored(self):
        for kind in ['NIC','DRIVING_LICENCE']:
            with self.subTest(kind=kind),Image.open(TEMPLATE_FILES[kind]) as original:
                array=np.array(original.convert('RGB'));h,w=array.shape[:2]
                for region in self.config[kind]['regions'].values():
                    if region['type'] not in {'photo','signature'}: continue
                    x1,y1,x2,y2=[round(region[k]*(w if k.startswith('x') else h)) for k in ['x1','y1','x2','y2']]
                    array[y1+8:y2-8,x1+8:x2-8]=[70,90,110]
                result=self.measure(kind,Image.fromarray(array))
                self.assertEqual(result['suspicious_area_count'],0,result['suspicious_areas'])

    def test_ocr_formats_do_not_imply_visual_alteration(self):
        evidence={'suspicious_areas':[],'analysed_pixel_fraction':.4}
        score=score_evidence(evidence,{'issues':[{'code':'INVALID_NIC_FORMAT'}]})
        self.assertEqual(score['risk_score'],0)
        self.assertGreater(score['component_scores']['ocr_format'],0)
        self.assertEqual(score['risk_level'],'LOW')


if __name__ == '__main__':
    unittest.main()
