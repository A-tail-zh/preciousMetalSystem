import {test,expect} from '@playwright/test'
import {login,api,ok,sql} from '../../tools/local-test-client.mjs'
test('product specification is derived, persisted and fully displayed',async({page,context})=>{
 test.setTimeout(90000)
 const token=await login();let id='';const code='SPEC-'+Date.now();
 const dimensions={innerDiameter:'10',innerHeight:'8',wallThickness:'0.2',bottomThickness:'0.3',rHeight:'1'};
 const expected='内径10cm；内高8cm；壁厚0.2cm；底厚0.3cm；R高度1cm';
 try{
  id=String(ok(await api('/metal/product',token,'POST',{code,name:'尺寸回显验收',category:'铱坩埚弧底',dimensions,processes:[],specification:'不应保存的手填型号'})).id);
  const detail=ok(await api('/metal/product/'+id,token)).data;expect(detail.specification).toBe(expected);
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe(expected);
  await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);
  await page.goto('/metal/product');const row=page.locator('.master-table .el-table__row').filter({hasText:code});
  await expect(row).toContainText(expected);await row.getByRole('button',{name:'查看',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'查看产品',exact:true});await expect(dialog.locator('.product-specification-input textarea')).toHaveValue(expected);
  const item=dialog.locator('.el-form-item').filter({hasText:'规格型号'});await expect(item.locator('textarea')).toHaveCount(1);await expect(item.locator('textarea')).toBeDisabled();
  await page.screenshot({path:'../.runtime/product-specification-display.png',fullPage:true,animations:'disabled'});
  await dialog.getByRole('button',{name:'关闭',exact:true}).click();
  await row.getByRole('button',{name:'编辑',exact:true}).click();dialog=page.getByRole('dialog',{name:'编辑产品',exact:true});
  await dialog.locator('.el-form-item').filter({hasText:'内高（cm）'}).locator('input').fill('9');
  await expect(dialog.locator('.el-form-item').filter({hasText:'规格型号'})).toHaveCount(0);
  const saved=page.waitForRequest(r=>r.method()==='PUT'&&new URL(r.url()).pathname.endsWith('/metal/product'));
  await dialog.getByRole('button',{name:'确定',exact:true}).click();expect((await saved).postDataJSON()).not.toHaveProperty('specification');
  await expect(dialog).not.toBeVisible();await expect(row).toContainText(expected.replace('内高8cm','内高9cm'));
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe(expected.replace('内高8cm','内高9cm'));
  const current=ok(await api('/metal/product/'+id,token)).data;
  ok(await api('/metal/product',token,'PUT',{...current,category:'铱杆实心',dimensions:{diameter:'2',length:'10.5'},specification:'再次篡改'}));
  expect(ok(await api('/metal/product/'+id,token)).data.specification).toBe('直径2cm；长度10.5cm');
  expect(Buffer.from(sql('SELECT HEX(specification) FROM metal_product WHERE product_id='+id),'hex').toString('utf8')).toBe('直径2cm；长度10.5cm');
  console.log('PASS 新增回显：'+expected+'；编辑内高后：9cm；切换类别后：直径2cm；长度10.5cm；客户端手填未保存');
 }finally{if(id)ok(await api('/metal/product/'+id,token,'DELETE'));await api('/logout',token,'POST')}
})
