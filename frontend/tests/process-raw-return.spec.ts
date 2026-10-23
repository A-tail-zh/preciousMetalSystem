import {test,expect,type Page,type Locator} from '@playwright/test'
import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {detail,sql} from '../../tools/local-test-client.mjs'
const pick=async(page:Page,d:Locator,label:string,name:string)=>{const f=d.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await f.locator('.el-select__wrapper').click();const id=await f.getByRole('combobox').getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name,exact:true}).click()}
async function setup(page:Page){
 const f=await warehouseFixture();
 try{
  const rawDoc=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'1000',quantity:0}]);const raw=(await detail('/metal/warehouse-order',rawDoc,f.admin)).lines[0].inventoryId;
  const b1=await f.createBatch(f.processA,10);await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'1000',quantity:0}],{batchId:b1});const s=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'990',quantity:10}],{batchId:b1,settle:true,completedQuantity:10});const semi=(await detail('/metal/warehouse-order',s,f.admin)).lines[0].inventoryId;
  const b2=await f.createBatch(f.processB,10);await f.post('PRODUCTION_OUT',[{inventoryId:semi,netWeight:'990',quantity:10}],{batchId:b2});await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'980',quantity:10}],{batchId:b2});
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/warehouse-order');await page.getByRole('tab',{name:'还库单',exact:true}).click();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();
  const d=page.getByRole('dialog',{name:'添加还库工单',exact:true});await expect(d).toBeVisible();await pick(page,d,'工厂',f.prefix+'厂A');await pick(page,d,'还库类型','加工余料还库');
  await pick(page,d,'生产工单',f.work.orderNo);await pick(page,d,'产品明细',f.prefix+'产品A 1');await pick(page,d,'工序',f.prefix+'焊接');
  await expect(d.getByText(/剩余可登记 10.000000g/)).toBeVisible();await pick(page,d,'仓库/库房',f.prefix+'库房');
  return {f,d,b2};
 }catch(e){await f.cleanup();throw e}
}
for(const width of [1920,1440,768,390])test(`加工余料真实录入布局 ${width}px`,async({page})=>{
 test.setTimeout(60000);await page.setViewportSize({width,height:1000});const s=await setup(page);
 try{
  await expect(s.d.getByLabel('客户',{exact:true})).toHaveValue(s.f.prefix);await expect(s.d.getByLabel('客户',{exact:true})).toHaveAttribute('readonly','');
  await s.d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});
  await pick(page,line,'回收原材料','WH_M1_'+s.f.suffix+' / 铱粉 / 夹具');await line.getByLabel('实测净重(g)',{exact:true}).fill('8');await line.getByRole('button',{name:'确认',exact:true}).click();
  await expect(line).not.toBeVisible();await expect(s.d.getByText(/剩余可登记 2.000000g/)).toBeVisible();await expect(s.d).not.toContainText('对应出库工单');
  const completed=s.d.getByLabel('实际完成数量',{exact:true}),settle=s.d.getByRole('checkbox',{name:'全部领回料已登记，确认结算',exact:true});
  await expect(completed).toHaveCount(0);await settle.locator("..").click();await expect(completed).toBeInViewport();
  const quantityBox=await completed.boundingBox(),checkboxBox=await settle.locator('..').boundingBox();
  expect(quantityBox).not.toBeNull();expect(checkboxBox).not.toBeNull();if(width>=1440)expect(Math.abs(quantityBox!.y-checkboxBox!.y)).toBeLessThan(30);
  await settle.locator("..").click();await expect(completed).toHaveCount(0);await settle.locator("..").click();await expect(completed).toBeInViewport();
  await page.screenshot({path:`../.runtime/process-raw-return/layout-${width}.png`,fullPage:true,animations:'disabled'});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  expect(await s.d.locator('.el-dialog__body').evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
  if(width===1440){
   const selected=await s.d.locator('.el-select').count();expect(selected).toBeGreaterThan(3);
   await pick(page,s.d,'经办人',s.f.prefix+'角色A｜'+s.f.prefix+'负责人A｜'+s.f.phoneA);await page.keyboard.press('Escape');await pick(page,s.d,'仓管员',s.f.prefix+'角色A｜'+s.f.prefix+'负责人A｜'+s.f.phoneA);await s.d.getByLabel('还库原因',{exact:true}).fill('浏览器隔离测试加工余料');
   await s.d.getByRole('button',{name:'确认',exact:true}).click();await expect(s.d.locator('.settlement-quantity .el-form-item__error')).toContainText('实际完成数量须为1～1000000的整数');await completed.fill('10');
   const saved=page.waitForResponse(r=>r.url().endsWith('/metal/warehouse-order')&&r.request().method()==='POST');await s.d.getByRole('button',{name:'确认',exact:true}).click();const savedResponse=await saved;expect(savedResponse.request().postDataJSON()).toMatchObject({settle:true,completedQuantity:10});expect((await savedResponse.json()).code).toBe(200);await expect(s.d).not.toBeVisible();
   expect(sql('SELECT SUM(l.net_weight) FROM metal_process_raw_return_line r JOIN metal_warehouse_order_line l ON l.line_id=r.line_id WHERE r.batch_id='+s.b2)).toBe('8.000000');
  }
 }finally{await s.f.cleanup()}
})

test('加工余料超过批次额度时前端不添加明细',async({page})=>{
 test.setTimeout(60000);const s=await setup(page);try{await s.d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await pick(page,line,'回收原材料','WH_M1_'+s.f.suffix+' / 铱粉 / 夹具');await line.getByLabel('实测净重(g)',{exact:true}).fill('10.000001');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByText('加工余料本单合计最多可还10.000000g',{exact:true})).toBeVisible();await expect(line).toBeVisible();expect(sql('SELECT COUNT(*) FROM metal_process_raw_return_line WHERE batch_id='+s.b2)).toBe('0')}finally{await s.f.cleanup()}
})
