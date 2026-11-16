import {test,expect,type Page,type Locator} from '@playwright/test'
const field=(d:Locator,label:string)=>d.locator('.el-form-item').filter({has:d.page().locator('label').filter({hasText:new RegExp('^'+label+'$')})})
async function options(d:Locator,label:string){const f=field(d,label);await f.locator('.el-select__wrapper').click();return d.page().locator('[id="'+await f.getByRole('combobox').getAttribute('aria-controls')+'"]')}
async function pick(d:Locator,label:string,name:string){await (await options(d,label)).getByRole('option',{name,exact:true}).click()}
async function setup(page:Page,outbound=false){
 await page.context().addCookies([{name:'Admin-Token',value:'output-stage-test',url:'http://127.0.0.1:5173'}]);const requests:string[]=[]
 const item=(id:string,n:number)=>({id,productId:id,snapshot:{code:id,name:id,specification:'快照规格'},plans:Array.from({length:n},(_,i)=>({processId:id+(i+1),processName:id+'工序'+(i+1),sequenceNo:i+1,measurementMode:i===0?'WEIGHT':'PIECE'}))})
 let releaseContext:(()=>void)|undefined,waitContext:Promise<void>|undefined
 await page.route('**/dev-api/**',async r=>{const u=new URL(r.request().url()),p=u.pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
 if(p==='/getInfo')out={code:200,user:{userId:2,userName:'仓储员'},roles:['warehouse'],permissions:['metal:warehouse-order:list','metal:warehouse-order:add']}
 else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'warehouse-order',component:'metal/warehouse-order/index',name:'OutputTest'}]}]
 else if(p.endsWith('/number/reserve'))out.data='TEST'
 else if(p.endsWith('/options/factories'))out.data=[{id:'F',name:'本厂'}]
 else if(p.endsWith('/options/work-orders')){const type=u.searchParams.get('outputType');requests.push(type??'ALL');out.data=(type==='SEMI_IN'?['M']:['S','M']).map(id=>({id,name:id==='S'?'单工序工单':'混合工单'}))}
 else if(p.endsWith('/options/warehouses'))out.data=[{id:'W',name:'库房'}]
 else if(p.endsWith('/options/stocks'))out={code:200,data:[{id:'S',choiceKey:'S:R',releaseId:'R',category:'SEMI',measurementMode:'WEIGHT',referenceQuantity:33,quantity:null,availableQuantity:0,netWeight:'600',availableWeight:'200',releaseRemainingWeight:'200',releaseRemainingQuantity:0,warehouseId:'W',referenceId:'多步产品',code:'P',name:'粉料',specification:'规格',originBatchId:'SOURCE',originBatchStatus:'SETTLED',originProcessName:'粉料',originWorkOrderNo:'来源工单'}],entryContext:{factoryId:'F',warehouseId:'W',workOrderId:u.searchParams.get('workOrderId'),itemId:u.searchParams.get('itemId'),processId:u.searchParams.get('processId'),externalEntryCapacity:'32'}};
 else if(p.endsWith('/work-order-context')){const id=u.searchParams.get('workOrderId'),wait=waitContext;waitContext=undefined;if(wait)await wait;out.data={factoryId:'F',workOrderId:id,customer:'客户',items:id==='S'?[item('单步产品',1)]:[item('单步产品',1),item('多步产品',3)]}}
 await r.fulfill({json:out})})
 await page.goto('/metal/warehouse-order');if(!outbound)await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:outbound?'添加出库工单':'添加入库工单',exact:true});await expect(d).toBeVisible();return{d,requests,holdContext(){waitContext=new Promise<void>(r=>releaseContext=r);return()=>releaseContext?.()}}
}

for(const width of [1920,1440,768,390])test('weight inbound optional reference count at '+width,async({page})=>{await page.setViewportSize({width,height:900});const{d}=await setup(page);await pick(d,'入库类型','半成品入库');await pick(d,'生产工单','混合工单');await pick(d,'产品明细','多步产品 1');await pick(d,'工序','多步产品工序1');await pick(d,'仓库/库房','库房');await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await pick(line,'半成品','多步产品 / 多步产品 / 快照规格');await expect(line.getByLabel('数量(件)',{exact:true})).toHaveCount(0);const ref=line.getByLabel('参考件数（选填）',{exact:true});await expect(ref).toBeVisible();await line.getByLabel('实测净重(g)',{exact:true}).fill('600');await ref.fill('1.5');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toContainText('参考件数须为1～1000000的整数');await ref.fill('');await expect(line.locator('.el-form-item__error')).toHaveCount(0);await page.screenshot({path:'../.runtime/weight-managed-semi/reference-input-'+width+'.png',fullPage:true});await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();await expect(d).toContainText('参考：未记录');expect((await d.boundingBox())!.width).toBeLessThanOrEqual(width);await page.screenshot({path:'../.runtime/weight-managed-semi/inbound-'+width+'.png',fullPage:true});});

test('powder inventory grams and external eligibility never masquerade as 32 pieces',async({page})=>{const{d}=await setup(page,true);await pick(d,'生产工单','混合工单');await pick(d,'产品明细','多步产品 2');await pick(d,'工序','多步产品工序2');await pick(d,'仓库/库房','库房');await expect(d).toContainText('重量型半成品按库存及释放净重领用');await expect(d).not.toContainText('可外部接入32件');await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'取物料明细',exact:true});await (await options(line,'库存物料')).getByRole('option').filter({hasText:'粉料'}).click();await expect(line.getByLabel('参考件数（选填）',{exact:true})).toBeVisible();await line.getByLabel('实测取净重(g)',{exact:true}).fill('100');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();await expect(d).toContainText('本单外部领用净重：100.000000g');});


test('reference adjustment input cannot leak into the next adjustment',async({page,context})=>{
 await context.addCookies([{name:'Admin-Token',value:'test',url:'http://127.0.0.1:5173'}]);
 const stock={id:'S',factoryId:'F',category:'SEMI',measurementMode:'WEIGHT',code:'POWDER',name:'粉料',netWeight:'600',version:1,ownershipStatus:'NORMAL'};
 await page.route('**/dev-api/**',async r=>{const p=new URL(r.request().url()).pathname.replace('/dev-api','');let out:any={code:200,data:[]};
 if(p==='/getInfo')out={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']};
 else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'InventoryTest'}]}];
 else if(p==='/metal/inventory/list')out={code:200,rows:[stock],total:1};
 else if(p==='/metal/inventory/S')out.data=stock;
 await r.fulfill({json:out});});
 await page.goto('/metal/inventory');await page.getByRole('tab',{name:'半成品',exact:true}).click();
 const open=()=>page.locator('.warehouse-table').getByRole('button',{name:'库存调整',exact:true}).click();await open();
 const d=page.getByRole('dialog',{name:'库存调整',exact:true});const ref=d.getByLabel('参考件数（选填）',{exact:true});
 await ref.fill('33');await d.getByRole('button',{name:'取消',exact:true}).click();await open();await expect(ref).toHaveValue('');
});

