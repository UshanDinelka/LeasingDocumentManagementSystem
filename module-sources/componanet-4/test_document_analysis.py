"""Run from the module directory: python -m unittest test_document_analysis -v."""
import unittest
from PIL import Image, ImageOps, ImageEnhance
from src.analysis_preflight import register_document, check_document_text, _ordered, _valid_quad
from src.document_profiles import TEMPLATE_FILES, PROFILES
from src.document_fields import enrich_ocr
from src.ocr_validation import parse_date_candidate
from src.risk_scoring import calculate_ocr_score, calculate_risk_score

TITLES = {"NIC": "SRI LANKA NATIONAL IDENTITY CARD", "DRIVING_LICENCE": "SRI LANKA DRIVING LICENCE",
    "BANK_STATEMENT": "TRANSACTION HISTORY ACCOUNT BALANCE", "BUSINESS_REGISTRATION": "CERTIFICATE OF INCORPORATION OF PRIVATE COMPANY",
    "VEHICLE_CR_BOOK": "CERTIFICATE OF REGISTRATION OF MOTOR VEHICLE"}


class DocumentAnalysisTests(unittest.TestCase):
    def test_exactly_five_profiles(self):
        self.assertEqual(set(PROFILES), set(TITLES))

    def test_template_files_present(self):
        for path in TEMPLATE_FILES.values(): self.assertTrue(path.is_file(), path.name)

    def test_titles_for_all_five(self):
        for kind, title in TITLES.items():
            with self.subTest(kind=kind):
                self.assertIsNone(check_document_text({"fullText": title, "lines": [{}, {}]}, kind))

    def test_empty_ocr(self):
        self.assertEqual(check_document_text({"fullText": "", "lines": []}, "NIC")[0], "OCR_INCOMPLETE")

    def test_wrong_title(self):
        self.assertEqual(check_document_text({"fullText": TITLES['DRIVING_LICENCE'], "lines": [{}, {}]}, "NIC")[0], "DOCUMENT_TYPE_UNVERIFIED")

    def test_other_types_excluded(self):
        for kind in ['SALARY_SLIP', 'UTILITY_BILL', 'NIC_COPY']:
            self.assertEqual(check_document_text({"fullText": TITLES['NIC'], "lines": [{}, {}]}, kind)[0], "NOT_SUPPORTED")

    def test_matching_templates(self):
        for kind, path in TEMPLATE_FILES.items():
            with self.subTest(kind=kind), Image.open(path) as template:
                aligned, info = register_document(template, template, kind)
                self.assertIsNotNone(aligned, info)
                self.assertEqual(info['status'], 'VERIFIED')
                self.assertGreaterEqual(info['inliers'], 12)
                self.assertEqual(aligned.size, template.size)
                crop = info.pop('_croppedImage'); self.assertGreater(crop.width, 20); crop.close(); aligned.close()

    def test_blank_is_not_a_match(self):
        with Image.open(TEMPLATE_FILES['NIC']) as template:
            result, _ = register_document(Image.new('RGB', template.size, 'white'), template, 'NIC')
            self.assertIsNone(result)

    def test_different_document_type_is_not_a_match(self):
        with Image.open(TEMPLATE_FILES['NIC']) as template, Image.open(TEMPLATE_FILES['DRIVING_LICENCE']) as image:
            result, _ = register_document(image, template, 'NIC')
            self.assertIsNone(result)

    def test_rotated_capture(self):
        with Image.open(TEMPLATE_FILES['NIC']) as template:
            for angle in [90, 180, 270]:
                with self.subTest(angle=angle):
                    aligned, info = register_document(template.rotate(angle, expand=True), template, 'NIC')
                    self.assertIsNotNone(aligned, info)

    def test_surrounding_margin(self):
        with Image.open(TEMPLATE_FILES['NIC']) as template:
            aligned, info = register_document(ImageOps.expand(template, border=80, fill='#555555'), template, 'NIC')
            self.assertIsNotNone(aligned, info)

    def test_lighting_change(self):
        with Image.open(TEMPLATE_FILES['NIC']) as template:
            aligned, info = register_document(ImageEnhance.Brightness(template).enhance(.8), template, 'NIC')
            self.assertIsNotNone(aligned, info)

    def test_geometry_rejects_degenerate_quad(self):
        self.assertFalse(_valid_quad([[0, 0], [1, 0], [1, 1], [0, 1]], (500, 500)))

    def test_account_candidates(self):
        fields = enrich_ocr({'fullText': 'Account Number: 123456789\nBalance 71,861.63', 'fields': {}}, 'BANK_STATEMENT')['fields']
        self.assertIn('123456789', fields['accountNumberCandidates'])
        self.assertIn('71,861.63', fields['amountCandidates'])

    def test_company_candidates(self):
        fields = enrich_ocr({'fullText': 'Company No: PV1234567', 'fields': {}}, 'BUSINESS_REGISTRATION')['fields']
        self.assertEqual(fields['companyNumberCandidates'], ['PV1234567'])

    def test_chassis_candidates(self):
        fields = enrich_ocr({'fullText': 'Chassis No: TESTABCDE12', 'fields': {}}, 'VEHICLE_CR_BOOK')['fields']
        self.assertEqual(fields['chassisNumberCandidates'], ['TESTABCDE12'])

    def test_spaced_calendar_date(self):
        self.assertEqual(str(parse_date_candidate('25 . 10 . 2002')), '2002-10-25')

    def test_missing_fields_do_not_add_points(self):
        self.assertEqual(calculate_ocr_score({'issues': [{'code': 'NAME_MISSING'}, {'code': 'FIELD_NOT_EXTRACTED'}]}), 0)

    def test_non_photo_document_weights(self):
        result = calculate_risk_score({'photo_analysis': None, 'symbol_analysis': {}}, {'issues': []})
        self.assertNotIn('photo', result['detector_coverage']['active'])
        self.assertNotIn('symbol', result['detector_coverage']['active'])
        self.assertAlmostEqual(sum(x['weight'] for x in result['weighted_breakdown'].values()), 1.0)


if __name__ == '__main__': unittest.main()
