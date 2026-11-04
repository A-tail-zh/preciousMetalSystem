import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,existingBatch=false){await page.context().addCookies([{name:'Admin-Token',value:'multi-test',url:'http://127.0.0.1:5173'}]);await page.route('**/dev-api/**',async r=>{const p=new URL(r.request().url()).pathname.replace('/dev-api','');let data:any={code:200,data:[],rows:[],total:0};if(p==='/getInfo')data={code:200,user:{userId:2,userName:'仓储员'},roles:['warehouse'],permissions:['metal:warehouse-order:list','metal:warehouse-order:add','metal:warehouse-order:import','metal:warehouse-order:query']};else if(p==='/getRouters')data.data=[{path:'/metal',component:'Layout',children:[{path:'warehouse-order',component:'metal/warehouse-order/index',name:'MultiTest'}]}];else if(p.endsWith('/number/reserve'))data.data='IN-TEST';else if(p.endsWith('/options/factories'))data.data=[{id:'1',name:'本厂'}];else if(p.endsWith('/options/warehouses'))data.data=[{id:'11',name:'仓库一'},{id:'12',name:'仓库二'}];else if(p.endsWith('/options/work-orders'))data.data=[{id:'21',name:'生产一单'}];else if(p==='/metal/warehouse/batch-planning')data.data={factoryId:'1',workOrderId:'21',itemId:'31',processId:new URL(r.request().url()).searchParams.get('processId'),measurementMode:'PIECE',availableQuantity:8,canCreate:!existingBatch,blockedReason:'前批尚未提交交接',batches:existingBatch?[{id:'71',batchNo:'EXISTING-OPEN-71',version:3,plannedQuantity:8,handoverSubmitted:false,canAppend:true}]:[]};else if(p==='/metal/warehouse/work-order-context')data.data={workOrderId:'21',factoryId:'1',customer:'客户',items:[{id:'31',productId:'41',snapshot:{name:'产品'},plans:[{processId:'51',processName:'首道',sequenceNo:1},{processId:'52',processName:'末道',sequenceNo:2}]}]};else if(p.endsWith('/options/stocks')){const w=new URL(r.request().url()).searchParams.get('warehouseId');data.data=[{id:'S'+w,warehouseId:w,category:'RAW',referenceId:'61',code:'R',name:'原料',organization:'客户',netWeight:'100.000000',availableWeight:'100.000000',quantity:0}];data.entryContext={factoryId:'1',warehouseId:w,workOrderId:'21',itemId:'31',processId:'51',externalEntryCapacity:'8'}}else if(p.endsWith('/outbound-submissions/preview')){const d=r.request().postDataJSON();data.data={...d,entrySummary:{externalEntryCapacity:'8'},groups:d.groups.map((g:any)=>({...g,stocks:[{id:'S'+g.warehouseId,warehouseId:g.warehouseId,category:'RAW',referenceId:'61',code:'R',name:'原料',organization:'客户',netWeight:'100.000000',availableWeight:'100.000000',quantity:0}]})),fieldErrors:[]}}else if(p.endsWith('/options/batches'))data.data=existingBatch?[{id:'71',workOrderId:'21',itemId:'31',processId:'51',status:'OPEN',version:3}]:[];else if(p.endsWith('/options/people'))data.data=[{id:'1',name:'仓储员'}];await r.fulfill({json:data})});await page.goto('/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();return page.getByRole('dialog',{name:'添加出库工单',exact:true})}

test('multiple warehouses create independent tabs and hide preallocated number',async({page})=>{const d=await setup(page);const f=d.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});await f.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'仓库一',exact:true}).click();await page.getByRole('option',{name:'仓库二',exact:true}).click();await page.keyboard.press('Escape');await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toBeVisible();await expect(d.getByRole('tab',{name:'仓库二',exact:true})).toBeVisible();await expect(d.getByRole('textbox').first()).toHaveValue('保存后按仓库生成单号');await d.getByRole('tab',{name:'仓库二',exact:true}).click();await expect(d.getByText('单独设置人员',{exact:true})).toBeVisible();await expect(d.getByText(/明细 0 条/)).toBeVisible()})



async function pick(page:Page,d:any,label:string,name:string){const f=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await f.locator('.el-select__wrapper').click();const id=await f.getByRole('combobox').getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name:name==='仓储员'?/仓储员/:name,exact:name!=='仓储员'}).click();await page.keyboard.press('Escape')}

async function ready(page:Page,existingBatch=false){const d=await setup(page,existingBatch);await pick(page,d,'生产工单','生产一单');await pick(page,d,'产品明细','产品 1');await pick(page,d,'工序','首道');if(!existingBatch)await d.getByPlaceholder('填写本批件数，不是整单剩余件数').fill('8');await pick(page,d,'仓库/库房','仓库一');await pick(page,d,'仓库/库房','仓库二');await pick(page,d,'领用人','仓储员');await pick(page,d,'仓管员','仓储员');return d}

async function add(page:Page,d:any,w='10'){await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const l=page.getByRole('dialog',{name:'取物料明细',exact:true});await l.getByRole('combobox').first().click();await page.getByRole('option',{name:/原料：R/}).click();await l.getByLabel('实测取净重(g)',{exact:true}).fill(w);await l.getByRole('button',{name:'确认',exact:true}).click();await expect(l).not.toBeVisible()}

test('switching outbound warehouse and refreshing keeps all drafts',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('tab',{name:'仓库二',exact:true}).click();await add(page,d,'20');await d.getByRole('button',{name:'刷新额度',exact:true}).click();await expect(d).toContainText('净重 30.00 g');await expect(d.locator('.el-table__body')).toContainText('20');await d.getByRole('tab',{name:'仓库一',exact:true}).click();await expect(d.locator('.el-table__body')).toContainText('10');await d.getByRole('button',{name:'取消',exact:true}).click();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveCount(0)})

test('outbound uses one post, optional people clear remains null, failed group retains lines',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('tab',{name:'仓库二',exact:true}).click();await add(page,d,'20');await pick(page,d,'审核人','仓储员');await d.getByText('单独设置人员',{exact:true}).click();const field=d.locator('.inbound-people-grid .el-form-item').filter({has:page.locator('label').filter({hasText:/^审核人$/})});await field.locator('.el-select__wrapper').hover();await field.locator('.el-select__clear').click();let calls=0;await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',r=>{calls++;const body=r.request().postDataJSON();expect(body.groups).toHaveLength(2);expect(body.operatorIds).toEqual([]);expect(body.groups[1].peopleOverride.reviewerId).toBeNull();return r.fulfill({json:{code:500,msg:'仓库一：余额不足',fieldErrors:[{field:'groups[0].lines[0].netWeight',message:'余额不足',code:'INSUFFICIENT'}]}})});await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d.getByRole('alert')).toContainText('余额不足');await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveAttribute('aria-selected','true');await expect(d.locator('.el-table__body')).toContainText('10');expect(calls).toBe(1)})

test('outbound empty group blocks and locates warehouse',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d.getByRole('alert')).toContainText('仓库二：请添加物料明细');await expect(d.getByRole('tab',{name:'仓库二',exact:true})).toHaveAttribute('aria-selected','true')})

for(const width of [1920,1440,768,390])test('outbound layout '+width,async({page})=>{await page.setViewportSize({width,height:1000});const d=await ready(page);await d.getByText('单独设置人员',{exact:true}).click();await expect(d.locator('.inbound-people-grid')).toBeVisible();expect(await d.locator('.el-dialog__body').evaluate(el=>el.scrollWidth<=el.clientWidth)).toBeTruthy();await page.screenshot({path:'.runtime/multi-warehouse-outbound/layout-'+width+'.png',fullPage:true})})



test('refresh flags reduced balance without changing drafts',async({page})=>{const d=await ready(page);await add(page,d);await page.route('**/dev-api/metal/warehouse-order/outbound-submissions/preview',r=>{const body=r.request().postDataJSON();return r.fulfill({json:{code:200,data:{...body,groups:body.groups.map((g:any)=>({...g,stocks:[]})),entrySummary:{externalEntryCapacity:'0'},fieldErrors:[{field:'groups[0].lines[0].netWeight',message:'第一仓余额不足，请核对第1行',code:'INSUFFICIENT'}]}}})});await d.getByRole('button',{name:'刷新额度',exact:true}).click();await expect(d.getByRole('alert')).toContainText('余额不足');await expect(d.locator('.invalid-draft-row')).toHaveCSS('background-color','rgb(255, 240, 240)');await expect(d.locator('.el-table__body')).toContainText('10')})

test('imports send all warehouse drafts and reject group errors without clearing input',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('tab',{name:'仓库二',exact:true}).click();let captured='';await page.route('**/dev-api/metal/warehouse-order/import',r=>{captured=r.request().postData()??'';return r.fulfill({json:{code:500,msg:'第二仓超出共享额度',fieldErrors:[{field:'groups[1].lines[0].quantity',message:'共享额度不足',code:'INSUFFICIENT'}]}})});await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});await expect.poll(()=>captured).toContain('submissionDraft');expect(captured).toContain('S11');await d.getByRole('tab',{name:'仓库一',exact:true}).click();await expect(d.locator('.el-table__body')).toContainText('10')})

test('late preview cannot restore closed outbound groups',async({page})=>{const d=await ready(page);let started=false,release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve);await page.route('**/dev-api/metal/warehouse-order/outbound-submissions/preview',async r=>{started=true;const body=r.request().postDataJSON();await gate;await r.fulfill({json:{code:200,data:{...body,groups:[],entrySummary:{externalEntryCapacity:'999'},fieldErrors:[]}}})});await d.getByRole('button',{name:'刷新额度',exact:true}).click();await expect.poll(()=>started).toBe(true);await d.getByRole('button',{name:'取消',exact:true}).click();release();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveCount(0);await expect(d).not.toContainText('999')})



test('all independent warehouses allow empty common recipient and keeper',async({page})=>{const d=await ready(page);await add(page,d);await d.getByText('单独设置人员',{exact:true}).click();await d.getByRole('tab',{name:'仓库二',exact:true}).click();await add(page,d);await d.getByText('单独设置人员',{exact:true}).click();for(const label of ['领用人','仓管员']){const field=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await field.locator('.el-select__wrapper').hover();await field.locator('.el-select__clear').click()}let body:any;await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',r=>{body=r.request().postDataJSON();return r.fulfill({json:{code:200,data:{batchNo:'B-TEST',orders:[]}}})});await d.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByRole('dialog',{name:'出库提交成功',exact:true})).toBeVisible();expect(body.recipientId).toBeNull();expect(body.keeperId).toBeNull();expect(body.groups.every((g:any)=>g.peopleOverride.recipientId==='1'&&g.peopleOverride.keeperId==='1')).toBeTruthy()})

test('logout in another tab discards outbound warehouse groups',async({page})=>{const d=await ready(page);await add(page,d);const other=await page.context().newPage();await other.goto('/login');await other.evaluate(async()=>{const path='/src/utils/auth.ts';const auth=await import(path);auth.removeToken()});await page.evaluate(()=>{window.dispatchEvent(new Event('storage'));window.dispatchEvent(new Event('focus'))});await expect(d).not.toBeVisible();await other.close()})



test('import error locates another warehouse and preserves its rows',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('tab',{name:'仓库二',exact:true}).click();await page.route('**/dev-api/metal/warehouse-order/import',r=>r.fulfill({json:{code:500,msg:'第一仓余额不足',fieldErrors:[{field:'groups[0].lines[0].netWeight',message:'第一仓余额不足',code:'INSUFFICIENT'}]}}));await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveAttribute('aria-selected','true');await expect(d.getByRole('alert')).toContainText('余额不足');await expect(d.locator('.el-table__body')).toContainText('10')})



test('pending outbound save releases busy after logout and allows reopening',async({page})=>{const d=await ready(page);await add(page,d);await d.getByRole('tab',{name:'仓库二',exact:true}).click();await add(page,d);let started=false,release!:()=>void;const gate=new Promise<void>(r=>release=r);await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',async r=>{started=true;await gate;await r.fulfill({json:{code:200,data:{batchNo:'OLD-BATCH',orders:[]}}})});await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>started).toBe(true);await page.evaluate(async()=>{const path='/src/utils/auth.ts';(await import(path)).removeToken();window.dispatchEvent(new Event('focus'))});await expect(d).not.toBeVisible();release();await page.evaluate(async()=>{const path='/src/utils/auth.ts';(await import(path)).setToken('multi-test')});await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();await expect(d).toBeVisible();await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveCount(0);await expect(page.getByRole('dialog',{name:'出库提交成功',exact:true})).not.toBeVisible()})



test('changing process clears old single warehouse row errors',async({page})=>{const d=await ready(page);const warehouse=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});await warehouse.locator('.el-tag__close').last().click();await add(page,d);await page.route('**/dev-api/metal/warehouse-order/import',r=>r.fulfill({json:{code:500,msg:'旧工序库存错误',fieldErrors:[{field:'groups[0].lines[0].netWeight',message:'旧工序库存错误',code:'INSUFFICIENT'}]}}));await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});await expect(d.locator('.invalid-draft-row')).toHaveCount(1);const process=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^工序$/})});await process.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'末道',exact:true}).click();await page.getByRole('dialog',{name:'变更仓储信息',exact:true}).getByRole('button',{name:'确定',exact:true}).click();await add(page,d);await expect(d.locator('.invalid-draft-row')).toHaveCount(0)})



for(const warehouseCount of [1,2])test(`existing open batch imports successfully with ${warehouseCount} warehouse(s)`,async({page})=>{

 const d=await ready(page,true);

 if(warehouseCount===1){const warehouses=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});await warehouses.locator('.el-tag__close').last().click()}

 await add(page,d,'10');

 if(warehouseCount===2)await d.getByRole('tab',{name:'仓库二',exact:true}).click();

 let captured:{outerBatchId:string|null,draft:any}|undefined;

 await page.route('**/dev-api/metal/warehouse-order/import',async route=>{

  const raw=route.request().postData()??'';

  const outerBatchId=raw.match(/name="batchId"\r\n\r\n([^\r]+)/)?.[1]??null;

  const draft=JSON.parse(raw.match(/name="submissionDraft"\r\n\r\n([^\r]+)/)![1]);

  captured={outerBatchId,draft};

  if(outerBatchId!==draft.batchId){await route.fulfill({json:{code:500,msg:'导入与多仓草稿的生产关联不一致，请重新选择'}});return}

  const warehouseId=warehouseCount===1?'11':'12';

  await route.fulfill({json:{code:200,data:[{inventoryId:'S'+warehouseId,category:'RAW',referenceId:'61',code:'R',name:'原料',netWeight:'7.000000',quantity:0}]}});

 });

 await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});

 await expect.poll(()=>captured).toBeDefined();

 expect(captured!.outerBatchId).toBe(captured!.draft.batchId);

 expect(captured!.draft).toMatchObject({batchAction:'APPEND',batchId:'71',batchVersion:3}); // Import, preview and save use the explicitly selected batch.

 expect(captured!.draft.groups).toHaveLength(warehouseCount);

 await expect(d.locator('.el-table__body')).toContainText('7.00');

 if(warehouseCount===2){await d.getByRole('tab',{name:'仓库一',exact:true}).click();await expect(d.locator('.el-table__body')).toContainText('10')}

 else await expect(d.locator('.el-table__body')).toContainText('10');

 await expect(d.getByRole('alert')).not.toBeVisible();

});

for(const warehouseCount of [1,2])for(const result of ['success','error'])test(`late import ${result} cannot affect reopened form with ${warehouseCount} warehouse(s)`,async({page})=>{

 const d=await ready(page,true);

 if(warehouseCount===1){const warehouses=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});await warehouses.locator('.el-tag__close').last().click()}

 await add(page,d,'10');

 let started=false,release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve);

 await page.route('**/dev-api/metal/warehouse-order/import',async route=>{started=true;await gate;await route.fulfill({json:result==='error'?{code:500,msg:'旧批次导入失败'}:{code:200,data:[{inventoryId:'S11',category:'RAW',referenceId:'61',code:'OLD-IMPORT',name:'旧批次导入行',netWeight:'7.000000',quantity:0}]}})});

 const response=page.waitForResponse(r=>r.url().endsWith('/metal/warehouse-order/import'));

 await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});

 await expect.poll(()=>started).toBe(true);

 await d.getByRole('button',{name:'取消',exact:true}).click();

 await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();await expect(d).toBeVisible();

 release();await(await response).finished();await page.waitForTimeout(300); // Allow the XHR error interceptor and message transition to finish.

 await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));

 await expect(page.locator('.el-message--error')).toHaveCount(0);

 await expect(d).not.toContainText('旧批次导入行');

 await expect(d.getByRole('tab',{name:'仓库一',exact:true})).toHaveCount(0);

 await expect(d.getByRole('button',{name:'导入模板',exact:true})).toBeDisabled(); // New form still has no selected warehouse.

});

for(const code of [200,500])test(`obsolete import response ${code} is canceled before UI handling`,async({page})=>{

 await setup(page,true);

 await page.route('**/dev-api/metal/warehouse-order/import',r=>r.fulfill({json:code===200?{code:200,data:[]}:{code:500,msg:'旧批次导入失败'}}));

 const outcome=await page.evaluate(async()=>{const path='/src/api/metal/warehouse.ts',api=await import(path);try{await api.importWarehouseLines(new File(['fixture'],'out.xlsx'),{factoryId:'1',warehouseId:'11',type:'PRODUCTION_OUT',workOrderId:'21',itemId:'31',processId:'51',batchId:null,submissionDraft:{factoryId:'1',workOrderId:'21',itemId:'31',processId:'51',batchId:null,batchVersion:null,groups:[{warehouseId:'11',lines:[]}]}},()=>false);return 'resolved'}catch(error:any){return error.name}});

 expect(outcome).toBe('CanceledError');

 await expect(page.locator('.el-message--error')).toHaveCount(0);

});

async function fullOutbound(page:Page){const d=await ready(page,true);await add(page,d,'100');await d.getByRole('tab',{name:'仓库二',exact:true}).click();await add(page,d,'100');return d}

async function expectFrozen(d:any){await expect(d.getByRole('alert')).toHaveCount(1);await expect(d.getByRole('alert')).toContainText('提交结果未知，请重试原请求');await expect(d.getByRole('alert')).not.toContainText(/Network Error|Request failed/);await expect(d.getByRole('button',{name:'确认',exact:true})).toBeDisabled();await expect(d.getByRole('button',{name:'取消',exact:true})).toBeDisabled();await expect(d.getByRole('combobox',{name:/出库日期/})).toBeDisabled();await expect(d.getByRole('button',{name:'重试原请求',exact:true})).toBeEnabled()}



test('lost committed outbound response retries the same request despite exhausted stock',async({page})=>{

 const d=await fullOutbound(page);let committed=false,commits=0;const bodies:any[]=[];let previews=0;

 const original={submissionId:'91',batchId:'71',batchNo:'EXISTING-OPEN-71',orders:[{id:'101',warehouseId:'11',warehouseName:'仓库一',orderNo:'OUT-ORIGINAL-1'},{id:'102',warehouseId:'12',warehouseName:'仓库二',orderNo:'OUT-ORIGINAL-2'}]};

 await page.route('**/dev-api/metal/warehouse-order/outbound-submissions/preview',r=>{previews++;const draft=r.request().postDataJSON();return r.fulfill({json:{code:200,data:{...draft,groups:draft.groups.map((g:any)=>({...g,stocks:[]})),entrySummary:{externalEntryCapacity:'0'},fieldErrors:committed?[{field:'groups[0].lines[0].inventoryId',message:'库存余额已扣完',code:'INSUFFICIENT'}]:[]}}})});

 await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',async r=>{bodies.push(r.request().postDataJSON());if(!committed){committed=true;commits++;await r.abort('failed')}else await r.fulfill({json:{code:200,data:original}})});

 await d.getByRole('button',{name:'确认',exact:true}).click();await expectFrozen(d);await expect.poll(()=>bodies.length).toBe(1);

 const previewsBeforeRetry=previews;

 await d.getByRole('button',{name:'重试原请求',exact:true}).click();

 const result=page.getByRole('dialog',{name:'出库提交成功',exact:true});await expect(result).toContainText('OUT-ORIGINAL-1');await expect(result).toContainText('OUT-ORIGINAL-2');await expect(result).toContainText('EXISTING-OPEN-71');

 expect(bodies).toHaveLength(2);expect(bodies[1]).toEqual(bodies[0]);expect(commits).toBe(1);expect(previews).toBe(previewsBeforeRetry);

});

for(const failure of ['network','http'])test(`outbound ${failure} failure shows Chinese and keeps both frozen warehouse drafts`,async({page})=>{

 const d=await fullOutbound(page),bodies:any[]=[];let commits=0;

 await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',async r=>{bodies.push(r.request().postDataJSON());if(bodies.length===1){if(failure==='network')await r.abort('failed');else await r.fulfill({status:503,body:'Service Unavailable'});return}commits++;await r.fulfill({json:{code:200,data:{submissionId:'91',orders:[{id:'101',warehouseName:'仓库一',orderNo:'OUT-1'},{id:'102',warehouseName:'仓库二',orderNo:'OUT-2'}]}}})});

 await d.getByRole('button',{name:'确认',exact:true}).click();await expectFrozen(d);await expect(d.locator('.el-table__body')).toContainText('100');expect(bodies).toHaveLength(1);expect(bodies[0].groups.map((g:any)=>g.warehouseId)).toEqual(['11','12']);expect(bodies[0].groups.every((g:any)=>Number(g.lines[0].netWeight)===100)).toBe(true);expect(bodies[0].requestId).toMatch(/^[0-9a-f-]{36}$/);

 await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(page.getByRole('dialog',{name:'出库提交成功',exact:true})).toContainText('OUT-2');expect(bodies).toHaveLength(2);expect(bodies[1]).toEqual(bodies[0]);expect(commits).toBe(1);

});

for(const failure of ['network','http'])test(`outbound import ${failure} error stays Chinese and preserves drafts`,async({page})=>{

 const d=await fullOutbound(page);

 await page.route('**/dev-api/metal/warehouse-order/import',r=>failure==='network'?r.abort('failed'):r.fulfill({status:503,body:'Service Unavailable'}));

 await d.locator('input[type=file]').setInputFiles({name:'out.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fixture')});

 await expect(d.getByRole('alert')).toContainText('导入失败，请核对当前仓库物料后重试');await expect(d.getByRole('alert')).not.toContainText('Network Error');await expect(d.getByRole('alert')).not.toContainText('Request failed');

 for(const warehouse of ['仓库一','仓库二']){await d.getByRole('tab',{name:warehouse,exact:true}).click();await expect(d.locator('.el-table__body')).toContainText('100')}

});





test('single outbound freezes an unknown result and replays it before fresh stock checks',async({page})=>{

 const d=await ready(page,true),warehouses=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});

 await warehouses.locator('.el-tag__close').last().click();await add(page,d,'100');

 let committed=false,stockReads=0,commits=0;const bodies:any[]=[];

 await page.route('**/dev-api/metal/warehouse/options/stocks?**',async r=>{stockReads++;if(committed)await r.fulfill({json:{code:200,data:[],entryContext:{factoryId:'1',warehouseId:'11',workOrderId:'21',itemId:'31',processId:'51',externalEntryCapacity:'8'}}});else await r.fallback()});

 await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',async r=>{bodies.push(r.request().postDataJSON());if(!committed){committed=true;commits++;await r.abort('failed')}else await r.fulfill({json:{code:200,data:{batchId:'71',flowId:'91',orders:[{id:'101',orderNo:'OUT101'}]}}})});

 await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>bodies.length).toBe(1);await expectFrozen(d);await expect(d.getByRole('combobox',{name:/出库日期/})).toHaveValue(bodies[0].orderDate);

 await d.getByRole('button',{name:'确认',exact:true}).dispatchEvent('click');expect(bodies).toHaveLength(1);

 const beforeRetry=stockReads;await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(d).not.toBeVisible();expect(bodies).toHaveLength(2);expect(bodies[1]).toEqual(bodies[0]);expect(stockReads).toBe(beforeRetry);expect(commits).toBe(1);

});



test('single outbound known business rejection permits correction after an exact unknown retry',async({page})=>{

 const d=await ready(page,true),warehouses=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})});await warehouses.locator('.el-tag__close').last().click();await add(page,d,'40');

 const date=d.getByRole('combobox',{name:/出库日期/}),original=await date.inputValue(),changed=original==='2026-10-20'?'2026-10-19':'2026-10-20';let stockReads=0,commits=0;const bodies:any[]=[];

 await page.route('**/dev-api/metal/warehouse/options/stocks?**',async r=>{stockReads++;await r.fulfill({json:{code:200,data:[{id:'S11',warehouseId:'11',category:'RAW',referenceId:'61',code:'R',name:'原料',organization:'客户',netWeight:'60.000000',availableWeight:'60.000000',quantity:0}],entryContext:{factoryId:'1',warehouseId:'11',workOrderId:'21',itemId:'31',processId:'51',externalEntryCapacity:'8'}}})});

 await page.route('**/dev-api/metal/warehouse-order/outbound-submissions',async r=>{bodies.push(r.request().postDataJSON());if(bodies.length===1)await r.abort('failed');else if(bodies.length===2)await r.fulfill({json:{code:500,msg:'隔离业务拒绝：请核对单据日期'}});else{commits++;await r.fulfill({json:{code:200,data:{batchId:'71',flowId:'91',orders:[{id:'101',orderNo:'OUT101'}]}}})}});

 await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>bodies.length).toBe(1);await expectFrozen(d);const readsBeforeRetry=stockReads;await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(d.getByRole('alert')).toContainText('隔离业务拒绝');expect(bodies[1]).toEqual(bodies[0]);expect(stockReads).toBe(readsBeforeRetry);await expect(date).toBeEnabled();await expect(d.getByRole('button',{name:'重试原请求',exact:true})).toHaveCount(0);

 await date.fill(changed);await date.press('Enter');await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).not.toBeVisible();expect(bodies).toHaveLength(3);expect(bodies[2]).toEqual({...bodies[0],orderDate:changed});expect(stockReads).toBeGreaterThan(readsBeforeRetry);expect(commits).toBe(1);

});







const nonProductionTypes=[{type:'PRODUCT_OUT',label:'产品出库',category:'FINISHED'},{type:'SEMI_OUT',label:'半成品出库',category:'SEMI'},{type:'EXCHANGE_OUT',label:'还料出库',category:'RAW'}];

async function nonProductionDraft(page:Page,c:{type:string,label:string,category:string}){

 const d=await setup(page);let empty=false,reads=0;const choose=async(label:string,name:string)=>{const f=d.locator('.order-header-form>.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await f.locator('.el-select__wrapper').click();const id=await f.getByRole('combobox').getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name:name==='仓储员'?/仓储员/:name,exact:name!=='仓储员'}).click();if(label==='经办人')await page.keyboard.press('Escape')};

 await page.route('**/dev-api/metal/warehouse/options/stocks**',async r=>{reads++;await r.fulfill({json:{code:200,data:empty?[]:[{id:'S11',warehouseId:'11',category:c.category,referenceId:'61',code:'RETRY_STOCK',name:'重试物料',organization:'客户',netWeight:'100.000000',quantity:c.category==='RAW'?0:2,measurementMode:'PIECE'}]}})});

 await choose('出库类型',c.label);await choose('仓库/库房','仓库一');await choose('经办人','仓储员');await choose('仓管员','仓储员');if(['SEMI_OUT','PRODUCT_OUT'].includes(c.type))await choose('领用人','仓储员');

 await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox').first().click();await page.getByRole('option').filter({hasText:'RETRY_STOCK'}).click();await line.getByLabel('实测净重(g)',{exact:true}).fill('100');if(c.category!=='RAW')await line.getByLabel('数量(件)',{exact:true}).fill('2');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();

 return{d,get reads(){return reads},set empty(v:boolean){empty=v}};

}

for(const c of nonProductionTypes){

 test(c.type+' unknown result freezes fields and explicitly retries the original body',async({page})=>{

  const s=await nonProductionDraft(page,c),bodies:any[]=[];let commits=0;await page.route('**/dev-api/metal/warehouse-order',async r=>{bodies.push(r.request().postDataJSON());if(bodies.length===1){s.empty=true;commits++;await r.abort('failed')}else await r.fulfill({json:{code:200,id:'ORIGINAL'}})});

  await s.d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>bodies.length).toBe(1);await expectFrozen(s.d);await expect(s.d.getByRole('combobox',{name:/出库日期/})).toHaveValue(bodies[0].orderDate);await s.d.getByRole('button',{name:'确认',exact:true}).dispatchEvent('click');expect(bodies).toHaveLength(1);

  const reads=s.reads;await s.d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(s.d).not.toBeVisible();expect(bodies).toHaveLength(2);expect(bodies[1]).toEqual(bodies[0]);expect(s.reads).toBe(reads);expect(commits).toBe(1);

 });

 test(c.type+' first save still refreshes stock and blocks an exhausted draft',async({page})=>{

  const s=await nonProductionDraft(page,c);let posts=0;await page.route('**/dev-api/metal/warehouse-order',r=>{posts++;return r.fulfill({json:{code:200,id:'SHOULD_NOT_POST'}})});s.empty=true;await s.d.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByText('所选库存已失效或不属于当前库房，请重新选择',{exact:true})).toBeVisible();await expect(s.d).toBeVisible();expect(posts).toBe(0);await expect(s.d.locator('.el-table__body')).toContainText('RETRY_STOCK');

 });

 test(c.type+' unchanged uncommitted retry still reaches server stock validation',async({page})=>{

  const s=await nonProductionDraft(page,c),bodies:any[]=[],stockReadsAtPost:number[]=[];await page.route('**/dev-api/metal/warehouse-order',async r=>{bodies.push(r.request().postDataJSON());stockReadsAtPost.push(s.reads);if(bodies.length===1){s.empty=true;await r.abort('failed')}else await r.fulfill({json:{code:500,msg:'库存可领余额不足，请刷新核对'}})});await s.d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>bodies.length).toBe(1);await expectFrozen(s.d);const reads=s.reads;await s.d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect(page.locator('.el-message--error').filter({hasText:'库存可领余额不足，请刷新核对'})).toBeVisible();expect(bodies).toHaveLength(2);expect(bodies[1]).toEqual(bodies[0]);expect(stockReadsAtPost[1]).toBe(reads);await expect(s.d).toBeVisible();await expect(s.d.getByRole('combobox',{name:/出库日期/})).toBeEnabled();await expect(s.d.getByRole('button',{name:'重试原请求',exact:true})).toHaveCount(0);await expect(s.d.getByRole('button',{name:'重新核对候选',exact:true})).toBeVisible();

 });

}
