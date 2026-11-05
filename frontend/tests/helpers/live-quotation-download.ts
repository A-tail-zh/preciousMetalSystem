import {test,expect,type Page,type BrowserContext} from './isolated-live-test'
import {workOrderFixture} from '../../../tools/work-order-test-fixture.mjs'
import {api,ok,detail,sql,login} from '../../../tools/local-test-client.mjs'
import {fillLiveQuote} from './live-quotation'
import {execFileSync} from 'node:child_process'
import {createHash} from 'node:crypto'

export async function verifyLiveQuotationDownload(page:Page,context:BrowserContext,shape:string,dimensions:Record<string,string>){
 const f=await workOrderFixture()
 try{
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:String(test.info().project.use.baseURL)}]);await page.goto('/metal/quotation')
  await fillLiveQuote(page,f.prefix+'厂A',shape,dimensions)
  const saving=page.waitForResponse(r=>r.url().endsWith('/metal/quotation/save')&&r.request().method()==='POST')
  await page.getByRole('button',{name:'开始计算',exact:true}).click();const saved=ok(await(await saving).json()).data
  await expect(page.locator('.quote-results')).toContainText(saved.quoteNo)
  await expect(page.locator('.quote-results .el-image img')).toBeVisible()
  for(const [key,value] of Object.entries(dimensions))expect(saved.items[0].dimensions[key]).toBe(Number(value).toFixed(2))
  const image=await fetch(process.env.TEST_API_BASE_URL+`/metal/quotation/orders/${saved.id}/items/1/image`,{headers:{Authorization:'Bearer '+f.admin}})
  expect(image.status).toBe(200);const hash=createHash('sha256').update(Buffer.from(await image.arrayBuffer())).digest('hex')
  const rule=ok(await api('/metal/quote-rule?metalTypeId='+f.metalA+'&factoryId='+f.factoryA,f.admin)).data
  ok(await api('/metal/quote-rule',f.admin,'PUT',{...rule,density:'25.00',lossPrice:'99.00'}))
  const frozen=ok(await api('/metal/quotation/orders/'+saved.id,f.admin)).data
  expect(frozen.items).toEqual(saved.items);expect(frozen.totalPrice).toBe(saved.totalPrice)
  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('.quote-results').getByRole('button',{name:'下载报价单',exact:true}).click()])
  expect(download.suggestedFilename()).toBe(saved.quoteNo+'.xlsx')
  const file=test.info().outputPath('quotation.xlsx');await download.saveAs(file)
  const parsed=JSON.parse(execFileSync('python',['-c',`import zipfile,xml.etree.ElementTree as E,json,sys,hashlib
with zipfile.ZipFile(sys.argv[1]) as z:
 n={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
 strings=[''.join(t.text or '' for t in s.findall('.//m:t',n)) for s in E.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si',n)]
 cells={c.attrib['r']:strings[int(c.find('m:v',n).text)] if c.attrib.get('t')=='s' else c.find('m:v',n).text for c in E.fromstring(z.read('xl/worksheets/sheet1.xml')).findall('.//m:c',n) if c.find('m:v',n) is not None}
 media=[p for p in z.namelist() if p.startswith('xl/media/')]
 print(json.dumps({'cells':cells,'images':[hashlib.sha256(z.read(p)).hexdigest() for p in media]},ensure_ascii=True))`,file],{encoding:'utf8'}))
  expect(parsed.cells.A1).toBe('报价单号：'+saved.quoteNo)
  expect(parsed.cells.A2).toContain('所属工厂：'+f.prefix+'厂A');expect(parsed.cells.A2).toContain('保存时间：'+saved.createTime)
  const texts=Object.values(parsed.cells).join('\n')
  expect(texts).toContain('铱'+shape);expect(texts).toContain('数量：2')
  for(const field of saved.items[0].dimensionFields)expect(texts).toContain(field.label+'：'+saved.items[0].dimensions[field.key]+field.unit)
  expect(texts).toContain('单件理论重量（g）：'+saved.items[0].unitWeight)
  expect(texts).toContain('损失费用（元）：'+saved.items[0].lossAmount)
  expect(texts).toContain('商品金额（元）：'+saved.items[0].totalPrice)
  expect(texts).toContain('总报价（元）：'+Number(saved.totalPrice).toFixed(2));expect(parsed.images).toEqual([hash])
  const before=sql('SELECT COUNT(*) FROM metal_saved_quotation WHERE factory_id='+f.factoryA)
  await Promise.all([page.waitForEvent('download'),page.locator('.quote-results').getByRole('button',{name:'下载报价单',exact:true}).click()])
  expect(sql('SELECT COUNT(*) FROM metal_saved_quotation WHERE factory_id='+f.factoryA)).toBe(before)
  const menus=ok(await api('/metal/options/menus',f.admin)).data.filter((m:any)=>m.perms==='metal:quotation:list')
  const role=await detail('/system/role',f.roleB,f.admin)
  ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[...new Set([...role.menuIds,...menus.map((m:any)=>m.id)])]}))
  const other=await login(f.phoneB,f.password)
  try{expect((await api('/metal/quotation/orders/'+saved.id+'/download',other)).code).toBe(403)}finally{await api('/logout',other,'POST')}
 }finally{await f.cleanup()}
}
