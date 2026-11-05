import zipfile,xml.etree.ElementTree as ET,sys
src,dst,ref=sys.argv[1:4]
net=sys.argv[4] if len(sys.argv)>4 else '12.345678'
gross=sys.argv[5] if len(sys.argv)>5 else ''
impurity=sys.argv[6] if len(sys.argv)>6 else ''
ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
with zipfile.ZipFile(src) as zin,zipfile.ZipFile(dst,'w',zipfile.ZIP_DEFLATED) as zout:
 strings=[]
 if 'xl/sharedStrings.xml' in zin.namelist():
  strings=[''.join(t.text or '' for t in cell.findall('.//{'+ns+'}t')) for cell in ET.fromstring(zin.read('xl/sharedStrings.xml')).findall('{'+ns+'}si')]
 for info in zin.infolist():
  data=zin.read(info.filename)
  if info.filename=='xl/worksheets/sheet1.xml':
   root=ET.fromstring(data);sheet=root.find('{'+ns+'}sheetData');row=ET.SubElement(sheet,'{'+ns+'}row',{'r':'2'})
   headers=[]
   for cell in sheet.find('{'+ns+'}row').findall('{'+ns+'}c'):
    value=cell.find('{'+ns+'}v');headers.append(strings[int(value.text)] if cell.get('t')=='s' else ''.join(t.text or '' for t in cell.findall('.//{'+ns+'}t')))
   fields={'物料／产品ID':ref,'毛重(g)':gross,'杂质(g)':impurity,'实测净重(g)':net,'材料归属（客户料/厂料）':'厂料','客户ID':'','铱粉来源（原生/回收再生）':'原生','用途':'','备注':'import test'}
   values=[fields.get(label,'') for label in headers] if headers and headers[0]=='物料／产品ID' else ['',ref,'','RAW','',net,'0',gross,impurity,'','','','import test']
   for i,v in enumerate(values):
    c=ET.SubElement(row,'{'+ns+'}c',{'r':chr(65+i)+'2','t':'inlineStr'});text=ET.SubElement(ET.SubElement(c,'{'+ns+'}is'),'{'+ns+'}t');text.text=v
   data=ET.tostring(root)
  zout.writestr(info,data)
