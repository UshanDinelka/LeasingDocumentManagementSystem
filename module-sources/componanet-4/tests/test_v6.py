"""Controlled test vectors. Do not interpret as field accuracy measures."""
import base64
import hashlib
import os
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from src.analysis_pipeline import run_analysis_pipeline
from src.document_profiles import TEMPLATE_FILES
from src.customer_crosscheck import compare_customer
from src.physical_obstruction import detect_obstructions

ROOT=Path(__file__).resolve().parents[1]
NIC=ROOT/'data/genuine/nic_genuine_01.jpg'


class CustomerComparisonTests(unittest.TestCase):
    customer={'customerId':7,'nic':'200229900760','fullName':'Kamala Perera'}

    def check(self, text, **fields):
        return compare_customer({'fullText':text,'lines':[], 'fields':fields},self.customer,'NIC')

    def test_nic_matches_registration(self):
        data=self.check('NATIONAL IDENTITY CARD\nNIC 200229900760\nName: Kamala Perera')
        self.assertEqual(data['fields']['nic']['status'],'MATCH')
        self.assertEqual(data['fields']['fullName']['status'],'MATCH')

    def test_edited_nic_disagrees_with_registration(self):
        data=self.check('NATIONAL IDENTITY CARD\nNIC 200229900761\nName: Kamala Perera')
        self.assertEqual(data['fields']['nic']['status'],'MISMATCH')
        self.assertEqual(data['status'],'DISCREPANCY_FOUND')

    def test_ambiguous_does_not_claim_mismatch(self):
        data=self.check('NIC 200229900760\nNIC 200229900761')
        self.assertEqual(data['fields']['nic']['status'],'AMBIGUOUS_OCR')
        self.assertFalse(data['discrepancies'])

    def test_ocr_unreadable_does_not_claim_mismatch(self):
        data=self.check('NIC 2002299OO76O')
        self.assertEqual(data['fields']['nic']['status'],'NOT_EXTRACTED')
        self.assertFalse(data['discrepancies'])

    def test_forged_fields_object_cannot_invent_nic(self):
        data=self.check('No NIC characters are visible',nicNumber='200229900760')
        self.assertEqual(data['fields']['nic']['status'],'NOT_EXTRACTED')


class ExpandedTemplateTests(unittest.TestCase):
    def test_all_approved_templates_can_run(self):
        with tempfile.TemporaryDirectory() as d:
            for kind,template in TEMPLATE_FILES.items():
                with self.subTest(kind=kind):
                    result=run_analysis_pipeline(template,kind,Path(d)/kind,
                        ocr_validation={'ocr_text_present':True,'issues':[]},ocr={})
                    self.assertEqual(result['status'],'COMPLETE')
                    self.assertEqual(result['risk_level'],'LOW')

    def test_synthetic_opaque_object_covers_field(self):
        with tempfile.TemporaryDirectory() as d:
            changed=Image.open(NIC).convert('RGB')
            ImageDraw.Draw(changed).rectangle((480,290,730,405),fill=(70,70,70))
            path=Path(d)/'opaque.png';changed.save(path)
            result=run_analysis_pipeline(path,'NIC',Path(d)/'result',
                ocr_validation={'ocr_text_present':True,'issues':[]},ocr={})
            self.assertEqual(result['status'],'COMPLETE')
            self.assertTrue(any(f['code']=='POSSIBLE_OPAQUE_FIELD_COVERING'
                for f in result['risk_details']['findings']))

    def test_variable_layout_types_are_labelled(self):
        with tempfile.TemporaryDirectory() as d:
            for kind in ('SALARY_SLIP','UTILITY_BILL'):
                result=run_analysis_pipeline(NIC,kind,Path(d)/kind,
                    ocr_validation={'ocr_text_present':True,'issues':[]},ocr={'fullText':'Statement items'})
                self.assertEqual(result['status'],'COMPLETE')
                self.assertEqual(result['templateVerificationStatus'],'NO_APPROVED_TEMPLATE')

    def test_synthetic_finger_shape_over_name(self):
        with tempfile.TemporaryDirectory() as d:
            changed=Image.open(NIC).convert('RGB')
            ImageDraw.Draw(changed).rounded_rectangle((490,270,710,430),radius=65,
                    fill=(195,127,90),outline=(144,80,70),width=3)
            path=Path(d)/'finger.png';changed.save(path)
            result=run_analysis_pipeline(path,'NIC',Path(d)/'result',
                ocr_validation={'ocr_text_present':True,'issues':[]},ocr={})
            self.assertEqual(result['status'],'COMPLETE')
            self.assertTrue(any(f['code']=='POSSIBLE_PHYSICAL_OCCLUSION'
                for f in result['risk_details']['findings']))

if __name__=='__main__':unittest.main()
