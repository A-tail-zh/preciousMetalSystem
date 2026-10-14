import zipfile,xml.etree.ElementTree as ET,sys
src,dst,ref=sys.argv[1:]
ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
with zipfile.ZipFile(src) as zin,zipfile.ZipFile(dst,'w',zipfile.ZIP_DEFLATED) as zout:
 for info in zin.infolist():
  data=zin.read(info.filename)
  if info.filename=='xl/worksheets/sheet1.xml':
   root=ET.fromstring(data);sheet=root.find('{'+ns+'}sheetData');row=ET.SubElement(sheet,'{'+ns+'}row',{'r':'2'})
   values=['',ref,'','RAW','','12.345678','0','','','','','','import test']
   for i,v in enumerate(values):
    c=ET.SubElement(row,'{'+ns+'}c',{'r':chr(65+i)+'2','t':'inlineStr'});text=ET.SubElement(ET.SubElement(c,'{'+ns+'}is'),'{'+ns+'}t');text.text=v
   data=ET.tostring(root)
  zout.writestr(info,data)
