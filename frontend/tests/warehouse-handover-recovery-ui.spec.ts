import {test,expect,type Page} from '@playwright/test'

async function openHandover(page:Page,options:{outputType?:string;isLast?:boolean;warehouses?:{id:string;name:string}[];fresh?:boolean}={}){
 let submitted:any
 const batch={batchId:'B',productId:'P',version:2,measurementMode:'PIECE',outputType:'FINISHED_IN',plannedQuantity:10,canSubmit:true,product:{name:'产品P'},sources:[{lineId:'SOURCE',measurementMode:'WEIGHT',category:'SEMI',recipientId:'42',confirmedBy:'42',warehouseId:'W',netWeight:'50',orderNo:'OUT1',code:'SEMI1'}],flow:{groups:[{processorId:'42',type:'FINISHED_IN',warehouseId:'W',keeperId:'K',lines:[{processorWeight:'48',line:{category:'FINISHED',quantity:10}}]},{processorId:'42',type:'SEMI_RETURN',warehouseId:'W',keeperId:'K',lines:[{processorWeight:'3',line:{category:'SEMI',sourceLineId:'SOURCE',netWeight:'3',quantity:0,referenceQuantity:5}}]}]}}
 if(options.fresh)Object.assign(batch,{outputType:options.outputType,isLast:options.isLast,flow:undefined});
 await page.context().addCookies([{name:'Admin-Token',value:'handover-recovery-mock',url:process.env.TEST_FRONTEND_BASE_URL||'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace('/dev-api','');let response:any={code:200,data:[],rows:[],total:0};
  if(path==='/getInfo')response={code:200,user:{userId:42,userName:'actor'},roles:['demo'],permissions:['*:*:*']}
  else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'RecoveryInventory'}]}]
  else if(path.endsWith('/options/factories'))response.data=[{id:'F',name:'本厂'}]
  else if(path.endsWith('/options/warehouses'))response.data=options.warehouses??[{id:'W',name:'仓库W'}]
  else if(path.endsWith('/options/people'))response.data=[{id:'K',name:'仓管员',roleNames:'仓管',phone:'13800000000'}]
  else if(path.endsWith('/handover-batches'))response.data=['B','C'].map(id=>({id,orderNo:'工单'+id,batchNo:id,processName:'加工'}))
  else if(path.endsWith('/handover-context/B'))response.data=batch
  else if(path.endsWith('/handover-context/C'))response={code:500,msg:'批次C加载失败'}
  else if(path.endsWith('/handover/B')){submitted=route.request().postDataJSON();response.data={id:'FLOW'}}
  await route.fulfill({json:response})
 })
 await page.goto('/metal/inventory');await expect(page.locator('.warehouse-page')).toBeVisible();await page.evaluate(async()=>{const component=(document.querySelector('.warehouse-page') as any).__vueParentComponent;await component.refs.handoverDialog.open()});const dialog=page.getByRole('dialog',{name:'加工产出交接',exact:true});await dialog.locator('.el-select__wrapper').nth(1).click();await page.getByRole('option',{name:'工单B / B / 加工',exact:true}).click();await expect(dialog.getByText('产品P',{exact:false})).toBeVisible();return {dialog,submitted:()=>submitted}
}
test('failed batch selection hides old weights and disables the actual submit button',async({page})=>{const {dialog,submitted}=await openHandover(page);await dialog.getByText('已逐项核对本人的全部产出及全部余料，或明确无余料',{exact:true}).click();const batch=dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:'本人已实领的加工批次'})});await batch.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'工单C / C / 加工',exact:true}).click();await expect(dialog.locator('.el-alert')).toContainText('批次C加载失败');await expect(dialog.locator('.el-table')).toHaveCount(0);await expect(dialog.getByRole('checkbox')).toHaveCount(0);await expect(dialog.getByRole('button',{name:'本人提交全部交接',exact:true})).toBeDisabled();expect(submitted()).toBeUndefined()})
test('reopened weight-managed return displays and submits reference 5 without effective pieces',async({page})=>{const {dialog,submitted}=await openHandover(page);const reference=dialog.locator('.return-row .el-form-item').filter({has:page.locator('label').filter({hasText:'参考件数（可空）'})});await expect(reference.locator('input')).toHaveValue('5');await dialog.getByText('已逐项核对本人的全部产出及全部余料，或明确无余料',{exact:true}).click();await dialog.getByRole('button',{name:'本人提交全部交接',exact:true}).click();await expect.poll(()=>submitted()?.documents.find((d:any)=>d.type==='SEMI_RETURN')?.lines[0]).toMatchObject({quantity:0,referenceQuantity:5});await expect(dialog).not.toBeVisible()})

const warehouses=[{id:'OTHER',name:'其他仓库'},{id:'RAW',name:'原材料仓库'},{id:'FINISHED',name:'成品仓库'},{id:'SEMI',name:'半成品仓库'}];
for(const scenario of [
 {outputType:'SEMI_IN',warehouse:'半成品仓库',id:'SEMI'},
 {outputType:'FINISHED_IN',warehouse:'成品仓库',id:'FINISHED'},
 {outputType:'RAW_PROCESS_IN',warehouse:'原材料仓库',id:'RAW'},
 {isLast:false,warehouse:'半成品仓库',id:'SEMI'},
])test(`new ${scenario.outputType??'legacy intermediate'} output selects its warehouse instead of the first option`,async({page})=>{
 const {dialog,submitted}=await openHandover(page,{...scenario,warehouses,fresh:true});
 const rows=dialog.locator('.el-table__body-wrapper .el-table__row');
 await expect(rows.first().locator('.el-select').first()).toContainText(scenario.warehouse);
 await dialog.getByRole('button',{name:'追加产出仓库',exact:true}).click();
 await expect(rows.nth(1).locator('.el-select').first()).toContainText(scenario.warehouse);
 await rows.nth(1).getByRole('button',{name:'移除',exact:true}).click();
 if(scenario.outputType==='SEMI_IN'||scenario.outputType==='FINISHED_IN'){
  await rows.first().locator('.el-select').nth(1).click();
  await page.getByRole('option',{name:/仓管员/}).click();
  await dialog.getByRole('textbox',{name:'加工称重（g）',exact:true}).fill('48');
  await dialog.getByText('已逐项核对本人的全部产出及全部余料，或明确无余料',{exact:true}).click();
  await dialog.getByRole('button',{name:'本人提交全部交接',exact:true}).click();
  await expect.poll(()=>submitted()?.documents[0]).toMatchObject({type:scenario.outputType,warehouseId:scenario.id});
 }
});

for(const scenario of [
 {name:'missing',warehouses:warehouses.filter(w=>w.id!=='SEMI')},
 {name:'ambiguous',warehouses:[...warehouses,{id:'SEMI2',name:'半成品仓库'}]},
])test(`${scenario.name} output warehouse remains empty and blocks submission`,async({page})=>{
 const {dialog,submitted}=await openHandover(page,{outputType:'SEMI_IN',warehouses:scenario.warehouses,fresh:true});
 const row=dialog.locator('.el-table__body-wrapper .el-table__row').first();
 await expect(row.locator('.el-select').first()).toContainText('请选择');
 await row.locator('.el-select').nth(1).click();
 await page.getByRole('option',{name:/仓管员/}).click();
 await dialog.getByRole('textbox',{name:'加工称重（g）',exact:true}).fill('48');
 await dialog.getByText('已逐项核对本人的全部产出及全部余料，或明确无余料',{exact:true}).click();
 await dialog.getByRole('button',{name:'本人提交全部交接',exact:true}).click();
 await expect(dialog.locator('.el-alert')).toContainText('仓库');
 expect(submitted()).toBeUndefined();
});

test('reopening an existing handover preserves its recorded warehouse',async({page})=>{
 const {dialog}=await openHandover(page,{warehouses:[...warehouses,{id:'W',name:'原交接仓库'}]});
 await expect(dialog.locator('.el-table__body-wrapper .el-table__row').first().locator('.el-select').first()).toContainText('原交接仓库');
});
