import pathlib
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'


class ImportFixtureTest(unittest.TestCase):
    def build(self, headers):
        with tempfile.TemporaryDirectory(prefix='metal-import-') as directory:
            root = pathlib.Path(directory)
            strings = ET.Element('sst', xmlns=NS)
            sheet = ET.Element('worksheet', xmlns=NS)
            row = ET.SubElement(ET.SubElement(sheet, 'sheetData'), 'row', r='1')
            for index, label in enumerate(headers):
                ET.SubElement(ET.SubElement(strings, 'si'), 't').text = label
                ET.SubElement(ET.SubElement(row, 'c', r=chr(65+index)+'1', t='s'), 'v').text = str(index)
            source, destination = root/'source.xlsx', root/'result.xlsx'
            with zipfile.ZipFile(source, 'w') as archive:
                archive.writestr('xl/sharedStrings.xml', ET.tostring(strings))
                archive.writestr('xl/worksheets/sheet1.xml', ET.tostring(sheet))
            subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('build-warehouse-import-fixture.py')), str(source), str(destination), '42'], check=True)
            with zipfile.ZipFile(destination) as archive:
                result = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
            return {cell.get('r'): cell.find('.//{'+NS+'}t').text or '' for cell in result.findall('.//{'+NS+'}row[@r="2"]/{'+NS+'}c')}

    def test_current_raw_template_keeps_identity_ownership_and_origin_columns(self):
        cells = self.build(['物料／产品ID', '毛重(g)', '杂质(g)', '实测净重(g)', '材料归属（客户料/厂料）', '客户ID', '铱粉来源（原生/回收再生）', '用途', '备注'])
        self.assertEqual([cells[k] for k in ['A2', 'D2', 'E2', 'F2', 'G2']], ['42', '12.345678', '厂料', '', '原生'])

    def test_legacy_template_retains_its_original_column_order(self):
        cells = self.build(['legacy']*13)
        self.assertEqual([cells[k] for k in ['B2', 'D2', 'F2', 'G2']], ['42', 'RAW', '12.345678', '0'])


if __name__ == '__main__':
    unittest.main()
