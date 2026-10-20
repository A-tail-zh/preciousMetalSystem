import {test,expect} from '@playwright/test';
import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs';
import {detail,api,ok} from '../../tools/local-test-client.mjs';
test('未结算半成品不出现在出库选项，结算后可选择',async({page,context})=>{
 test.skip(process.env.LIVE_WAREHOUSE!=='1','需要本地后端');test.setTimeout(90000);page.setDefaultTimeout(10000);const f=await warehouseFixture();
 const choose=async(scope:any,label:string,name:string)=>{const field=scope.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await field.locator('.el-select__wrapper').first().click();const control=await field.getByRole('combobox').first().getAttribute('aria-controls');await page.locator('#'+control).getByRole('option',{name:new RegExp('(^|｜)'+name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'(｜|$)')}).click();await page.keyboard.press('Escape')};
 try{
  const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'10',quantity:0}]);const stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;const batch=await f.createBatch(f.processA,5);await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'5',quantity:0}],{batchId:batch});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'4',quantity:5}],{batchId:batch});
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/warehouse-order');
  const open=async()=>{await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加出库工单',exact:true});await choose(dialog,'工厂',f.prefix+'厂A');await choose(dialog,'仓库/库房',f.prefix+'库房');await choose(dialog,'出库类型','半成品出库');await dialog.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox').first().click();return {dialog,line}};
  let view=await open();await expect(page.getByRole('option').filter({hasText:f.prefix+'产品A'})).toHaveCount(0);await page.keyboard.press('Escape');await view.line.getByRole('button',{name:'取消',exact:true}).click();await view.dialog.getByRole('button',{name:'取消',exact:true}).click();
  ok(await api('/metal/process-batch/'+batch+'/settle',f.admin,'POST',{version:(await detail('/metal/process-batch',batch,f.admin)).version,completedQuantity:5}));view=await open();await expect(page.getByRole('option').filter({hasText:f.prefix+'产品A'})).toHaveCount(1);await page.keyboard.press('Escape');await view.line.getByRole('button',{name:'取消',exact:true}).click();await view.dialog.getByRole('button',{name:'取消',exact:true}).click();
 }finally{await f.cleanup();}
});
