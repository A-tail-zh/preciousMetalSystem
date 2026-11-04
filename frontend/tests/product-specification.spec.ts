import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {test,expect} from '@playwright/test'
import {api,ok,sql} from '../../tools/local-test-client.mjs'
test('product specification is derived, persisted and fully displayed',async({page,context})=>{
 test.setTimeout(90000)
 test.skip(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_API_BASE_URL||new URL(process.env.TEST_API_BASE_URL).port==='8080'||!process.env.TEST_FRONTEND_BASE_URL||new URL(process.env.TEST_FRONTEND_BASE_URL).port==='5173','此实时用例需要显式隔离数据库、后端及前端')
 const fixture=await workOrderFixture(),token=fixture.admin;let id='';const code='SPEC-'+Date.now();
 const dimensions={innerDiameter:'10',outerDiameter:'12',wallThickness:'0.2',innerHeight:'8',bottomThickness:'0.3'};
 const expected='内径10cm；外径12cm；壁厚0.2cm；内高8cm；底厚0.3cm';
 try{
  id=String(ok(await api('/metal/product',token,'POST',{code,name:fixture.prefix+'尺寸回显验收',metalTypeId:'1',productType:'弧底',dimensions,processes:[],specification:'不应保存的手填型号'})).id);
  fixture.made.products.push(id);const detail=ok(await api('/metal/product/'+id,token)).data;expect(detail.specification).toBe(expected);
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe(expected);
  await context.addCookies([{name:'Admin-Token',value:token,url:process.env.TEST_FRONTEND_BASE_URL!}]);
  await page.goto('/metal/product');const row=page.locator('.master-table .el-table__row').filter({hasText:code});
  await expect(row).toContainText(expected);await row.getByRole('button',{name:'查看',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'查看产品',exact:true});await expect(dialog.locator('.product-specification-input textarea')).toHaveValue(expected);
  const item=dialog.locator('.el-form-item').filter({hasText:'规格型号'});await expect(item.locator('textarea')).toHaveCount(1);await expect(item.locator('textarea')).toBeDisabled();
  await page.screenshot({path:'../.runtime/product-specification-display.png',fullPage:true,animations:'disabled'});
  await dialog.getByRole('button',{name:'关闭',exact:true}).click();
  await row.getByRole('button',{name:'编辑',exact:true}).click();dialog=page.getByRole('dialog',{name:'编辑产品',exact:true});
  await dialog.locator('.el-form-item').filter({hasText:'内高（cm）'}).locator('input').fill('9');
  await expect(dialog.locator('.el-form-item').filter({hasText:'规格型号'})).toHaveCount(0);
  const saved=page.waitForRequest(r=>r.method()==='PUT'&&new URL(r.url()).pathname.endsWith('/metal/product'),{timeout:10000});
  await dialog.getByRole('button',{name:'确定',exact:true}).click();
  expect((await saved).postDataJSON()).not.toHaveProperty('specification');
  await expect(dialog).not.toBeVisible();await expect(row).toContainText(expected.replace('内高8cm','内高9cm'));
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe(expected.replace('内高8cm','内高9cm'));
  const current=ok(await api('/metal/product/'+id,token)).data;
  ok(await api('/metal/product',token,'PUT',{...current,category:null,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'10.5'},specification:'再次篡改'}));
  expect(ok(await api('/metal/product/'+id,token)).data.specification).toBe('内径0cm；外径2cm；长度10.5cm');
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe('内径0cm；外径2cm；长度10.5cm');
  console.log('PASS 新增回显：'+expected+'；编辑内高后：9cm；切换类别后：内径0cm；外径2cm；长度10.5cm；客户端手填未保存');
 }finally{await fixture.cleanup()}
})
