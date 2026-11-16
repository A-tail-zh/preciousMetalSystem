import fs from 'node:fs';import assert from 'node:assert/strict';import {execFileSync} from 'node:child_process';
import {warehouseFixture} from './warehouse-test-fixture.mjs';import {api,ok,detail,login,sql} from './local-test-client.mjs';
const f=await warehouseFixture();let productId,quoteId;
const dims={innerDiameter:'12.3',innerHeight:'8.7',wallThickness:'0.25',bottomThickness:'0.4',rHeight:'1.2'};
const getFile=async(path,file,token=f.admin)=>{const response=await fetch('http://127.0.0.1:8080'+path,{headers:{Authorization:'Bearer '+token}});assert(response.ok);const bytes=Buffer.from(await response.arrayBuffer());assert(bytes[0]===137||bytes[0]===80,'Expected PNG or XLSX');fs.writeFileSync(file,bytes);return bytes;};
try{
 productId=ok(await api('/metal/product',f.admin,'POST',{code:f.prefix+'-IMAGE',name:'尺寸图片隔离验收',category:'铱坩埚弧底',dimensions:dims,processes:[]})).id;
 const product=await getFile('/metal/product/'+productId+'/specification-image','.runtime/curved-bottom-filled.png');
 const saved=ok(await api('/metal/quotation/save',f.admin,'POST',{requestId:crypto.randomUUID(),factoryId:f.factoryA,items:[0,1,2].map(()=>({category:'铱杆实心',quantity:2,dimensions:{diameter:'2',length:'3'}}))})).data;quoteId=saved.id;
 // The current application has no curved-bottom pricing formula. Modify only this isolated
 // fixture snapshot to exercise image download; its monetary values are test values, not a curved-bottom price.
 saved.items[0].category='铱坩埚弧底';saved.items[0].dimensions=dims;
 saved.items[2].category='铱坩埚弧底';saved.items[2].dimensions={...dims,innerDiameter:'20'};
 const hex=Buffer.from(JSON.stringify(saved.items),'utf8').toString('hex');assert(/^\d+$/.test(String(quoteId)));
 sql("UPDATE metal_saved_quotation SET item_snapshot=CONVERT(0x"+hex+" USING utf8mb4) WHERE quote_id="+quoteId+" AND factory_id="+f.factoryA);
 const first=await getFile('/metal/quotation/orders/'+quoteId+'/items/1/image','.runtime/quotation-template-item1.png');assert(first.equals(product));
 await getFile('/metal/quotation/orders/'+quoteId+'/items/3/image','.runtime/quotation-template-item3.png');
 await getFile('/metal/quotation/orders/'+quoteId+'/download','.runtime/quotation-with-template.xlsx');
 const validate=execFileSync('python',['-c',
 "import zipfile,xml.etree.ElementTree as E,pathlib; z=zipfile.ZipFile('.runtime/quotation-with-template.xlsx'); ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main','d':'http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing','a':'http://schemas.openxmlformats.org/drawingml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}; root=E.fromstring(z.read('xl/worksheets/sheet1.xml')); assert [m.attrib['ref'] for m in root.findall('s:mergeCells/s:mergeCell',ns)]==['A1:C1','D1:F1','G1:I1']; drawing=E.fromstring(z.read('xl/drawings/drawing1.xml')); anchors=drawing.findall('d:twoCellAnchor',ns); assert [int(a.find('d:from/d:row',ns).text) for a in anchors]==[5,6,7]; assert all(int(a.find('d:from/d:col',ns).text)==9 for a in anchors); media=[z.read(p) for p in z.namelist() if p.startswith('xl/media/')]; assert len(media)==3; assert pathlib.Path('.runtime/quotation-template-item1.png').read_bytes() in media; assert pathlib.Path('.runtime/quotation-template-item3.png').read_bytes() in media; ss=E.fromstring(z.read('xl/sharedStrings.xml')); strings=[''.join(t.text or '' for t in s.findall('.//s:t',ns)) for s in ss]; cells={c.attrib['r']:strings[int(c.find('s:v',ns).text)] if c.attrib.get('t')=='s' else c.find('s:v',ns).text for c in root.findall('.//s:c',ns) if c.find('s:v',ns) is not None}; assert cells['J5']=='产品图片'; assert cells['F6']==cells['F7']==cells['F8']; print('PASS XLSX: three embedded PNGs, correct rows, unchanged amounts and merged header')"],{encoding:'utf8'});console.log(validate.trim());
 const menus=ok(await api('/metal/options/menus',f.admin)).data.filter(m=>m.perms==='metal:quotation:list');const role=await detail('/system/role',f.roleB,f.admin);ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[...new Set([...role.menuIds,...menus.map(m=>m.id)])]}));
 const other=await login(f.phoneB,f.password);try{assert.equal((await api('/metal/quotation/orders/'+quoteId+'/items/1/image',other)).code,403);}finally{await api('/logout',other,'POST');}
 console.log('PASS 产品 dimensions 自动填图、报价快照图片、跨厂访问拒绝；仅验证模板展示，不模拟弧底报价计算');
}finally{if(quoteId)sql('DELETE FROM metal_saved_quotation WHERE quote_id='+quoteId+' AND factory_id='+f.factoryA);if(productId)ok(await api('/metal/product/'+productId,f.admin,'DELETE'));await f.cleanup();}
