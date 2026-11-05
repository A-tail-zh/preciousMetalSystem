import {selectWarehouseOption} from './helpers/warehouse-select'
import {installWarehouseDiagnostics} from './helpers/warehouse-diagnostics'
import {execFileSync} from 'node:child_process';
import {test,expect,type Page,type Locator}from'./helpers/isolated-live-test';import{randomUUID}from'node:crypto';import{warehouseFixture}from'../../tools/warehouse-test-fixture.mjs';import{api,ok,detail,sql}from'../../tools/local-test-client.mjs';
installWarehouseDiagnostics()
async function pick(page:Page,d:Locator,label:string,name:string){return test.step('选择'+label,async()=>{const field=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await selectWarehouseOption(page,field,name)})}
async function supplementFromIssue(page:Page,f:any,issue:string){
 const order=await detail('/metal/warehouse-order',issue,f.admin);
 const filters=page.getByRole('search',{name:'筛选条件'});
 await filters.getByRole('textbox',{name:'单据编号',exact:true}).fill(order.orderNo);
 await filters.getByRole('button',{name:'查询',exact:true}).click();
 await page.locator('.warehouse-page .el-table__body tr').filter({hasText:order.orderNo}).getByRole('button',{name:'物料清单',exact:true}).click();
 await page.getByRole('button',{name:'追加领料',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'添加出库工单',exact:true});
 await expect(dialog).toContainText(f.work.orderNo);return dialog;
}
test('real multi outbound posts one batch from two warehouses and shows sibling documents',async({page})=>{test.setTimeout(120000);const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});try{
const w2=String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'第二库',sortOrder:1,status:'0'})).id);for(const warehouseId of [f.warehouse,w2])await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}],{warehouseId});
await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库工单',exact:true});await pick(page,d,'工厂',f.prefix+'厂A');await pick(page,d,'生产工单',f.work.orderNo);await pick(page,d,'产品明细',f.item.snapshot.name+' 1');await pick(page,d,'工序',f.prefix+'粉料');await pick(page,d,'仓库/库房',f.prefix+'库房');await pick(page,d,'仓库/库房',f.prefix+'第二库');const person=f.prefix+'角色A｜'+f.prefix+'负责人A｜'+f.phoneA;await pick(page,d,'领用人',person);await pick(page,d,'仓管员',person);
async function add(weight:string){await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const l=page.getByRole('dialog',{name:'取物料明细',exact:true});await l.getByRole('combobox').first().click();await page.getByRole('option').filter({hasText:'WH_M1_'+f.suffix}).click();await l.getByLabel('实测取净重(g)',{exact:true}).fill(weight);await l.getByRole('button',{name:'确认',exact:true}).click();await expect(l).not.toBeVisible()}
await add('20');await d.getByRole('tab',{name:f.prefix+'第二库',exact:true}).click();await add('30');await d.getByText('单独设置人员',{exact:true}).click();await d.getByRole('button',{name:'刷新额度',exact:true}).click();await expect(d).toContainText('净重 50.00 g');for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:1000});expect(await d.locator('.el-dialog__body').evaluate(el=>el.scrollWidth<=el.clientWidth)).toBeTruthy();await page.screenshot({path:'.runtime/multi-warehouse-outbound/real-layout-'+width+'.png',fullPage:true})}
const response=page.waitForResponse(r=>r.url().endsWith('/metal/warehouse-order/outbound-submissions')&&r.request().method()==='POST');await d.getByRole('button',{name:'确认',exact:true}).click();const sent=await(await response).json();expect(sent.code).toBe(200);expect(sent.data.orders).toHaveLength(2);const result=page.getByRole('dialog',{name:'出库提交成功',exact:true});await expect(result).toContainText('本次仅保存待确认申请');await f.confirmFlow(sent.data.flowId);const docs=await Promise.all(sent.data.orders.map((o:any)=>detail('/metal/warehouse-order',o.id,f.admin)));expect(docs.map(x=>Number(x.lines[0].netWeight)).sort()).toEqual([20,30]);expect(docs.every(x=>String(x.warehouseFlowId)===String(sent.data.flowId)&&String(x.batchId)===String(sent.data.batchId))).toBeTruthy();await result.getByRole('button',{name:'查看详情',exact:true}).first().click();await expect(result.locator('.el-table__body').last()).toContainText('WH_M1_'+f.suffix);
}finally{await f.cleanup()}})

for(const warehouseCount of [1,2])test(`real import into existing OPEN batch with ${warehouseCount} warehouse(s)`,async({page})=>{
 test.setTimeout(120000);
 const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
 try{
  const warehouses=[{id:f.warehouse,name:f.prefix+'库房'}];
  if(warehouseCount===2)warehouses.push({id:String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'第二库',sortOrder:1,status:'0'})).id),name:f.prefix+'第二库'});
  const stocks=[];
  for(const warehouse of warehouses){const orderId=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}],{warehouseId:warehouse.id});stocks.push((await detail('/metal/warehouse-order',orderId,f.admin)).lines[0].inventoryId)}
  const existingBatch=await f.createBatch(f.processA,10);const firstIssue=await f.post('PRODUCTION_OUT',[{inventoryId:stocks[0],netWeight:'1',quantity:0}],{workOrderId:f.wo,itemId:f.item.id,processId:f.processA,batchId:existingBatch});
  const batchId=(await detail('/metal/warehouse-order',firstIssue,f.admin)).batchId;
  expect((await detail('/metal/process-batch',batchId,f.admin)).status).toBe('OPEN');
  const template=await page.request.get('/dev-api/metal/warehouse-order/template?factoryId='+f.factoryA,{headers:{Authorization:'Bearer '+f.admin}});
  expect(template.ok()).toBeTruthy();expect(template.headers()['content-type']).toContain('spreadsheet');
  const importedWarehouse=warehouses.at(-1)!;
  const values=[stocks.at(-1),f.mat1,'','RAW','','7','0','','','','','','existing OPEN batch import','','','',''];
  const workbook=execFileSync('python',['-c',
`import io,json,sys,zipfile,xml.etree.ElementTree as E
ns='{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
source=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()))
sheet=E.fromstring(source.read('xl/worksheets/sheet1.xml'))
row=E.SubElement(sheet.find(ns+'sheetData'),ns+'row',{'r':'2'})
for i,value in enumerate(json.loads(sys.argv[1])):
 cell=E.SubElement(row,ns+'c',{'r':chr(65+i)+'2','t':'inlineStr'})
 E.SubElement(E.SubElement(cell,ns+'is'),ns+'t').text=str(value)
output=io.BytesIO()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
 for name in source.namelist(): target.writestr(name,E.tostring(sheet) if name=='xl/worksheets/sheet1.xml' else source.read(name))
sys.stdout.buffer.write(output.getvalue())`,JSON.stringify(values)],{input:await template.body()});
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);
  await page.goto('/metal/warehouse-order');
  const d=await supplementFromIssue(page,f,firstIssue);
  for(const warehouse of warehouses)await pick(page,d,'仓库/库房',warehouse.name);
  const person=f.prefix+'角色A｜'+f.prefix+'负责人A｜'+f.phoneA;
  await pick(page,d,'领用人',person);await pick(page,d,'仓管员',person);
  await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();
  const line=page.getByRole('dialog',{name:'取物料明细',exact:true});await line.getByRole('combobox').first().click();await page.getByRole('option').filter({hasText:'WH_M1_'+f.suffix}).click();await line.getByLabel('实测取净重(g)',{exact:true}).fill('10');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();
  if(warehouseCount===2)await d.getByRole('tab',{name:importedWarehouse.name,exact:true}).click();
  await expect(d.locator('.line-toolbar').getByRole('button',{name:'导入模板',exact:true})).toBeEnabled();
  const importedResponse=page.waitForResponse(r=>r.url().endsWith('/metal/warehouse-order/import')&&r.request().method()==='POST');
  await d.locator('input[type=file]').setInputFiles({name:'existing-batch.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:workbook});
  const imported=await importedResponse;
  const preview=await imported.json();expect(preview.code,preview.msg).toBe(200);expect(preview.data[0].inventoryId).toBe(stocks.at(-1));
  await expect(d.locator('.el-table__body').getByRole('row').filter({hasText:'existing OPEN batch import'}).getByRole('cell',{name:'7.00',exact:true})).toBeVisible();
  if(warehouseCount===2){await d.getByRole('tab',{name:warehouses[0].name,exact:true}).click();await expect(d.locator('.el-table__body')).toContainText('10')}
  else await expect(d.locator('.el-table__body')).toContainText('10');
  // Import validates against the existing batch, but neither posts stock nor creates a second batch.
  expect(Number((await detail('/metal/inventory',stocks[0],f.admin)).netWeight)).toBe(99);
  if(warehouseCount===2)expect(Number((await detail('/metal/inventory',stocks[1],f.admin)).netWeight)).toBe(100);
  const savePath='/metal/warehouse-order/outbound-submissions';
  const savedResponse=page.waitForResponse(r=>r.url().endsWith(savePath)&&r.request().method()==='POST');
  await d.getByRole('button',{name:'确认',exact:true}).click();
  const saved=await(await savedResponse).json();expect(saved.code,saved.msg).toBe(200);
  await f.confirmFlow(saved.data.flowId);const ids=saved.data.orders.map((o:any)=>o.id);
  const docs=await Promise.all(ids.map((id:string)=>detail('/metal/warehouse-order',id,f.admin)));
  expect(docs).toHaveLength(warehouseCount);expect(docs.every(doc=>String(doc.batchId)===String(batchId))).toBeTruthy();
  const context=ok(await api('/metal/process-batch/'+batchId+'/completion-context',f.admin)).data;
  expect(Number(context.weights.issued)).toBe(18);
  expect(context.status).toBe('OPEN');
 }finally{await f.cleanup()}
});
for(const warehouseCount of [1,2,3])test(`real committed outbound retries with ${warehouseCount} warehouse(s) and zero stock left`,async({page})=>{
 test.setTimeout(120000);page.setDefaultTimeout(10000);const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
 try{
  // Make the dependent-select loading transition deterministic in the three-warehouse case.
  if(warehouseCount===3)await page.route('**/dev-api/metal/warehouse/work-order-context?**',async route=>{const response=await route.fetch();await new Promise(resolve=>setTimeout(resolve,500));await route.fulfill({response})});
  const warehouses=[{id:f.warehouse,name:f.prefix+'库房'}];
  for(let i=2;i<=warehouseCount;i++)warehouses.push({id:String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'第'+i+'库',sortOrder:i,status:'0'})).id),name:f.prefix+'第'+i+'库'});
  const stocks=[];for(const {id:warehouseId} of warehouses){const id=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}],{warehouseId});stocks.push((await detail('/metal/warehouse-order',id,f.admin)).lines[0].inventoryId)}
  const existingBatch=await f.createBatch(f.processA,10);const issued=await f.post('PRODUCTION_OUT',[{inventoryId:stocks[0],netWeight:'1',quantity:0}],{workOrderId:f.wo,itemId:f.item.id,processId:f.processA,batchId:existingBatch});
  const batchId=(await detail('/metal/warehouse-order',issued,f.admin)).batchId;
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/warehouse-order');
  const d=await supplementFromIssue(page,f,issued);for(const warehouse of warehouses)await pick(page,d,'仓库/库房',warehouse.name);
  const person=f.prefix+'角色A｜'+f.prefix+'负责人A｜'+f.phoneA;await pick(page,d,'领用人',person);await pick(page,d,'仓管员',person);
  async function add(weight:string){await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'取物料明细',exact:true});await line.getByRole('combobox').first().click();await page.getByRole('option').filter({hasText:'WH_M1_'+f.suffix}).click();await line.getByLabel('实测取净重(g)',{exact:true}).fill(weight);await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible()}
  await add('99');for(const warehouse of warehouses.slice(1)){await d.getByRole('tab',{name:warehouse.name,exact:true}).click();await add('100')}
  const path='/metal/warehouse-order/outbound-submissions';
  let observed!:()=>void;const firstCommitted=new Promise<void>(resolve=>{observed=resolve});const requests:any[]=[],responses:any[]=[];
  await page.route('**/dev-api'+path,async route=>{
   requests.push(route.request().postDataJSON());const forwarded=await route.fetch();const result=await forwarded.json();responses.push(result);
   if(requests.length===1){expect(result.code,result.msg).toBe(200);await f.confirmFlow(result.data.flowId);await route.abort('failed');observed()}else await route.fulfill({response:forwarded});
  });
  await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).toBeVisible();await expect.poll(()=>responses.length,{timeout:20000}).toBe(1);expect(responses[0].code,responses[0].msg).toBe(200);await expect(d.getByRole('button',{name:'重试原请求',exact:true})).toBeEnabled();expect(responses).toHaveLength(1);
  const first=responses[0].data;expect(first.orders).toHaveLength(warehouseCount);expect(String(first.batchId)).toBe(String(batchId));
  for(const stock of stocks)expect(Number((await detail('/metal/inventory',stock,f.admin)).netWeight)).toBe(0);
  await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(d).not.toBeVisible();{const result=page.getByRole('dialog',{name:'出库提交成功',exact:true});await expect(result).toContainText('本次仅保存待确认申请');for(const order of first.orders)await expect(result).toContainText(order.orderNo)}
  expect(requests).toHaveLength(2);expect(requests[1]).toEqual(requests[0]);expect(responses[1].code,responses[1].msg).toBe(200);expect(responses[1].data.flowId).toBe(first.flowId);expect(responses[1].data.orders.map(({status,...o}:any)=>o)).toEqual(first.orders.map(({status,...o}:any)=>o))
  const orderIds=first.orders.map((o:any)=>o.id).join(',');
  expect(sql(`SELECT COUNT(*) FROM metal_warehouse_order WHERE order_id IN (${orderIds}) AND factory_id=${f.factoryA}`)).toBe(String(warehouseCount));
  const ledgerCount=()=>sql(`SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id IN (${orderIds})`);expect(ledgerCount()).toBe(String(warehouseCount));
  const replayed=await Promise.all([0,1].map(()=>api(path,f.admin,'POST',requests[0])));for(const replay of replayed){expect(replay.code,replay.msg).toBe(200);expect(replay.data.flowId).toBe(first.flowId);expect(replay.data.orders.map(({status,...o}:any)=>o)).toEqual(first.orders.map(({status,...o}:any)=>o))}
  const changed=await api(path,f.admin,'POST',{...requests[0],orderDate:requests[0].orderDate==='2026-10-25'?'2026-10-24':'2026-10-25'});expect(changed.code).toBe(409);expect(changed.msg).toContain('内容');
  if(warehouseCount>1){const single={...requests[0],warehouseId:f.warehouse,lines:requests[0].groups[0].lines};delete single.groups;const switched=await api('/metal/warehouse-order',f.admin,'POST',single);expect(switched.code).toBe(409);expect(switched.msg).toMatch(/[\u3400-\u9fff]/)}
  const insufficient=await api(path,f.admin,'POST',{...requests[0],requestId:randomUUID()});expect(insufficient.code).not.toBe(200);expect(insufficient.msg).toMatch(/[\u3400-\u9fff]/);expect(ledgerCount()).toBe(String(warehouseCount));
  expect(sql(`SELECT COUNT(*) FROM metal_warehouse_flow WHERE factory_id=${f.factoryA} AND request_id='${requests[0].requestId}'`)).toBe('1');for(const stock of stocks)expect(Number((await detail('/metal/inventory',stock,f.admin)).netWeight)).toBe(0);
  const context=ok(await api('/metal/process-batch/'+batchId+'/completion-context',f.admin)).data;expect(Number(context.weights.issued)).toBe(warehouseCount*100);expect(context.status).toBe('OPEN');
 }finally{await f.cleanup()}
});

for(const c of [{type:'PRODUCT_OUT',label:'产品出库',incoming:'FINISHED_IN',category:'FINISHED'},{type:'SEMI_OUT',label:'半成品出库',incoming:'SEMI_IN',category:'SEMI'},{type:'EXCHANGE_OUT',label:'还料出库',incoming:'RAW_IN',category:'RAW'}])test(c.type==='EXCHANGE_OUT'?'standalone customer return is rejected without stock postings':'real '+c.type+' committed lost response replays without duplicate postings',async({page})=>{
 test.setTimeout(120000);const f=await warehouseFixture();
 try{
  const seed=[{category:c.category,referenceId:c.category==='RAW'?f.mat1:f.productA,netWeight:'100',quantity:c.category==='RAW'?0:2}];const incoming=c.category==='RAW'?await f.post('RAW_IN',seed):await f.opening(seed),stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库工单',exact:true});const choose=async(label:string,name:string)=>{const field=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await selectWarehouseOption(page,field,name)};await choose('工厂',f.prefix+'厂A');await choose('出库类型',c.label);await choose('仓库/库房',f.prefix+'库房');const person=f.prefix+'角色A｜'+f.prefix+'负责人A｜'+f.phoneA;await choose('经办人',person);await choose('仓管员',person);if(['SEMI_OUT','PRODUCT_OUT'].includes(c.type))await choose('领用人',person);
  await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox').first().click();await page.getByRole('option').filter({hasText:c.category==='RAW'?'WH_M1_'+f.suffix:f.prefix+'产品A'}).click();await line.getByLabel('实测净重(g)',{exact:true}).fill('100');if(c.category!=='RAW')await line.getByLabel('数量(件)',{exact:true}).fill('2');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();
  let observed!:()=>void;const firstCommitted=new Promise<void>(resolve=>{observed=resolve});const requests:any[]=[],responses:any[]=[];await page.route('**/dev-api/metal/warehouse-order',async r=>{requests.push(r.request().postDataJSON());const forwarded=await r.fetch(),body=await forwarded.json();if(c.type==='PRODUCT_OUT'&&requests.length===1)await new Promise(resolve=>setTimeout(resolve,5500));responses.push(body);if(c.type==='EXCHANGE_OUT'){await r.fulfill({response:forwarded});observed()}else if(requests.length===1){expect(body.code,body.msg).toBe(200);if(body.flowId)await f.confirmFlow(body.flowId);await r.abort('failed');observed()}else await r.fulfill({response:forwarded})});await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>responses.length,{timeout:20000}).toBe(1);if(c.type==='EXCHANGE_OUT'){expect(responses[0].code).not.toBe(200);expect(responses[0].msg).toContain('客户余料收尾');await expect(d).toBeVisible();for(let i=0;i<2;i++)expect((await api('/metal/warehouse-order',f.admin,'POST',requests[0])).code).not.toBe(200);expect(Number((await detail('/metal/inventory',stock,f.admin)).netWeight)).toBe(100);expect(sql(`SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id=${f.factoryA} AND type='EXCHANGE_OUT'`)).toBe('0');return}expect(responses[0].code,responses[0].msg).toBe(200);await expect(d).toBeVisible();await expect(d.getByRole('button',{name:'重试原请求',exact:true})).toBeEnabled();
  expect(Number((await detail('/metal/inventory',stock,f.admin)).netWeight)).toBe(0);await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(d).not.toBeVisible();expect(requests).toHaveLength(2);expect(requests[1]).toEqual(requests[0]);expect(responses[1].code,responses[1].msg).toBe(200);expect(responses[1].id).toBe(responses[0].id);
  const replays=await Promise.all([0,1].map(()=>api('/metal/warehouse-order',f.admin,'POST',requests[0])));for(const replay of replays){expect(replay.code,replay.msg).toBe(200);expect(replay.id).toBe(responses[0].id)}
  const changed=await api('/metal/warehouse-order',f.admin,'POST',{...requests[0],orderDate:requests[0].orderDate==='2026-10-20'?'2026-10-19':'2026-10-20'});expect(changed.code).not.toBe(200);expect(changed.msg).toContain('内容');const insufficient=await api('/metal/warehouse-order',f.admin,'POST',{...requests[0],requestId:randomUUID()});expect(insufficient.code).not.toBe(200);expect(insufficient.msg).toMatch(/[\u3400-\u9fff]/);
  expect(sql(`SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id=${f.factoryA} AND type='${c.type}'`)).toBe('1');expect(sql(`SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id=${responses[0].id}`)).toBe('1');const finalStock=await detail('/metal/inventory',stock,f.admin);expect(Number(finalStock.netWeight)).toBe(0);expect(Number(finalStock.quantity)).toBe(0);const doc=await detail('/metal/warehouse-order',responses[0].id,f.admin);expect(doc.type).toBe(c.type);expect(doc.batchId??null).toBeNull();
 }finally{await f.cleanup()}
});
