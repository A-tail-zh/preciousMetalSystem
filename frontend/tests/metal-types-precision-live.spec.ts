import {test,expect} from './helpers/isolated-live-test'
import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {chooseQuoteOption} from './helpers/live-quotation'
import {api,detail,ok} from '../../tools/local-test-client.mjs'

test('registered real product and rule forms share a new metal and preserve the draft',async({page,context})=>{
 test.skip(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||process.env.TEST_REDIS_PORT==='6380','需显式指定隔离数据库和Redis');test.setTimeout(120000)
 const f=await warehouseFixture();let originalPi:{piValue:string;version:number}|undefined;
 try{
  originalPi=ok(await api('/metal/volume-rule?factoryId='+f.factoryA,f.admin)).data;
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/product');await page.getByRole('button',{name:'添加',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'添加产品',exact:true});const code='METAL_UI_'+f.suffix;
  await chooseQuoteOption(page,dialog,'所属工厂',f.prefix+'厂A');await dialog.getByLabel(/产品编号$/).fill(code);await dialog.getByLabel(/产品名称$/).fill(f.prefix+'新金属杆');await dialog.getByRole('button',{name:'添加金属类型',exact:true}).click();
  const metalDialog=page.getByRole('dialog',{name:'添加金属类型',exact:true}),metalName=f.prefix+'金属';await metalDialog.getByRole('textbox').fill(metalName);
  const added=page.waitForResponse(r=>r.url().endsWith('/metal/metal-types')&&r.request().method()==='POST');await metalDialog.getByRole('button',{name:'保存',exact:true}).click();const metalBody=await(await added).json();expect(metalBody.code).toBe(200);f.made.metalTypes.push(String(metalBody.id));await expect(metalDialog).not.toBeVisible();
  // Wait through a real background refresh before choosing a product shape.
  await page.waitForTimeout(6500);await expect(dialog.getByRole('combobox',{name:/产品类型$/})).toBeEnabled();
  await expect(dialog.getByLabel(/产品名称$/)).toHaveValue(f.prefix+'新金属杆');await dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^产品类型$/})}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:'杆',exact:true}).click();
  await dialog.getByLabel(/内径（cm）$/).fill('0');await dialog.getByLabel(/外径（cm）$/).fill('2.005');await dialog.getByLabel(/长度（cm）$/).fill('1');
  const saved=page.waitForResponse(r=>r.url().endsWith('/metal/product')&&r.request().method()==='POST');await dialog.getByRole('button',{name:'确定',exact:true}).click();const productBody=await(await saved).json();expect(productBody.code).toBe(200);f.made.products.push(String(productBody.id));await expect(dialog).not.toBeVisible();
  const product=await detail('/metal/product',productBody.id,f.admin);expect(product.metalTypeId).toBe(String(metalBody.id));expect(product.productType).toBe('杆');expect(product.dimensions.outerDiameter).toBe('2.01');expect(Number(product.volume).toFixed(2)).toBe((Number(originalPi.piValue)*2.01**2/2).toFixed(2));
  await page.goto('/metal/quote-rule');await chooseQuoteOption(page,page.locator('.factory-form'),'所属工厂',f.prefix+'厂A');await page.getByRole('button',{name:'增加报价',exact:true}).click();await expect(page.getByLabel('密度（g/cm³）',{exact:true})).toBeFocused();await page.getByRole('combobox',{name:'金属类型',exact:true}).press('ArrowDown');await page.getByRole('option',{name:metalName,exact:true}).click();
  for(const[label,value]of [['密度（g/cm³）','21.455'],['粉料加工单价（元/g）','1.235'],['非粉料加工单价（元/g）','2'],['欠料加工单价（元/g）','0'],['理论损失单价（元/g）','4']])await page.getByLabel(label,{exact:true}).fill(value);
  const configured=page.waitForResponse(r=>r.url().endsWith('/metal/quote-rule')&&r.request().method()==='PUT');await page.getByRole('button',{name:'保存配置',exact:true}).click();expect((await(await configured).json()).code).toBe(200);
  const rule=ok(await api('/metal/quote-rule?metalTypeId='+metalBody.id+'&factoryId='+f.factoryA,f.admin)).data;expect(rule).toMatchObject({density:'21.46',powderPrice:'1.24',shortagePrice:'0.00'});
  await page.goto('/metal/product');await page.getByRole('row').filter({hasText:code}).getByRole('button',{name:'编辑',exact:true}).click();
  const edit=page.getByRole('dialog',{name:'编辑产品',exact:true}),weight=edit.getByLabel(/理论成品净重（g）$/);
  await expect(weight).toHaveAttribute('readonly','');await edit.getByLabel(/长度（cm）$/).fill('2');await edit.getByLabel(/长度（cm）$/).press('Tab');
  await expect(edit.getByLabel(/产品体积（cm³）$/)).toHaveValue((Number(originalPi.piValue)*2.01**2).toFixed(2));await expect(weight).toHaveValue((Math.round(Number(originalPi.piValue)*2.01**2*100)/100*21.46).toFixed(2));
  const updated=page.waitForResponse(r=>r.url().endsWith('/metal/product')&&r.request().method()==='PUT');await edit.getByRole('button',{name:'确定',exact:true}).click();expect((await(await updated).json()).code).toBe(200);
  const calculated=await detail('/metal/product',productBody.id,f.admin);expect(Number(calculated.volume).toFixed(2)).toBe((Number(originalPi.piValue)*2.01**2).toFixed(2));expect(Number(calculated.weight).toFixed(2)).toBe((Math.round(Number(originalPi.piValue)*2.01**2*100)/100*21.46).toFixed(2));
  await page.goto('/metal/quote-rule');await chooseQuoteOption(page,page.locator('.factory-form'),'所属工厂',f.prefix+'厂A');await page.getByLabel('π值',{exact:true}).fill('3.20');
  const sharedSave=page.waitForResponse(r=>r.url().includes('/metal/volume-rule')&&r.request().method()==='PUT');await page.getByRole('button',{name:'保存参数',exact:true}).click();expect((await(await sharedSave).json()).code).toBe(200);await page.getByRole('article',{name:metalName+'报价规则',exact:true}).getByRole('button',{name:'编辑',exact:true}).click();await expect(page.getByLabel('密度（g/cm³）',{exact:true})).toHaveValue('21.46');await page.getByRole('dialog',{name:'编辑报价规则',exact:true}).getByRole('button',{name:'取消',exact:true}).click();
  expect(ok(await api('/metal/volume-rule?factoryId='+f.factoryA,f.admin)).data.piValue).toBe('3.20');expect(ok(await api('/metal/quote-rule?metalTypeId='+metalBody.id+'&factoryId='+f.factoryA,f.admin)).data.density).toBe('21.46');
  await page.goto('/metal/quotation');await chooseQuoteOption(page,page.locator('.quote-factory'),'所属工厂',f.prefix+'厂A');const item=page.locator('.goods-item');await chooseQuoteOption(page,item,'加工类型','来料定制');await chooseQuoteOption(page,item,'金属类型',metalName);await chooseQuoteOption(page,item,'产品类型','杆');await page.getByLabel('商品数量',{exact:true}).fill('2');await page.getByLabel('内径（cm）',{exact:true}).fill('0');await page.getByLabel('外径（cm）',{exact:true}).fill('2');await page.getByLabel('长度（cm）',{exact:true}).fill('1');for(const label of ['来料粉料重量（g）','来料非粉料重量（g）','理论损失重量（g）'])await page.getByLabel(label,{exact:true}).fill('0');await page.getByRole('button',{name:'开始计算',exact:true}).click();await expect(page.locator('.quote-results')).toContainText('137.34');
  const preserved=await detail('/metal/product',productBody.id,f.admin);expect(preserved).toEqual(calculated);
 }finally{try{if(originalPi){const current=ok(await api('/metal/volume-rule?factoryId='+f.factoryA,f.admin)).data;if(current.piValue!==originalPi.piValue)ok(await api('/metal/volume-rule?factoryId='+f.factoryA,f.admin,'PUT',{factoryId:f.factoryA,piValue:originalPi.piValue,curvedBottomFactor:(originalPi as any).curvedBottomFactor,version:current.version}))}}finally{await f.cleanup()}}
})
