import {test,expect} from '@playwright/test'
import {login,api} from '../../tools/local-test-client.mjs'
test('line dialog displays ten factory and product choices before scrolling',async({page,context})=>{
 const token=await login();try{
 await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);
 await page.route('**/metal/options/factories',route=>route.fulfill({json:{code:200,data:Array.from({length:15},(_,i)=>({id:String(8000+i),name:'布局验收工厂'+(i+1)}))}}));
 await page.route('**/metal/options/products',route=>route.fulfill({json:{code:200,data:Array.from({length:15},(_,i)=>({id:String(9000+i),name:'布局验收产品'+(i+1)}))}}));
 await page.goto('/metal/production-line');await page.getByRole('button',{name:'添加',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'添加产线',exact:true});await expect(dialog).toBeVisible();
 await dialog.locator('.line-factory-select .el-select__wrapper').click();
 const factories=dialog.locator('.line-factory-select .el-select-dropdown__wrap');await expect(factories).toBeVisible();
 const factoryVisible=await factories.evaluate(e=>{const r=e.getBoundingClientRect();return [...e.querySelectorAll('.el-select-dropdown__item')].filter(o=>{const b=o.getBoundingClientRect();return b.top>=r.top&&b.bottom<=r.bottom&&b.bottom<=innerHeight}).length});
 expect(factoryVisible).toBeGreaterThanOrEqual(9);expect(factoryVisible).toBeLessThanOrEqual(11);
 expect(await factories.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy();
 await page.screenshot({path:'../.runtime/line-factory-layout.png',fullPage:true,animations:'disabled'});
 await factories.getByRole('option',{name:'布局验收工厂2',exact:true}).click();
 await expect(dialog.locator('.line-factory-select')).toContainText('布局验收工厂2');
 await dialog.locator('.line-product-select .el-select__wrapper').click();
 const wrap=dialog.locator('.line-product-select .el-select-dropdown__wrap');await expect(wrap).toBeVisible();
 await page.screenshot({path:'../.runtime/line-product-layout.png',fullPage:true,animations:'disabled'});
 const box=await dialog.boundingBox();expect(box!.width).toBeGreaterThan(850);
 const visible=await wrap.evaluate(e=>{const r=e.getBoundingClientRect();return [...e.querySelectorAll('.el-select-dropdown__item')].filter(o=>{const b=o.getBoundingClientRect();return b.top>=r.top&&b.bottom<=r.bottom&&b.bottom<=innerHeight}).length});
 expect(visible).toBeGreaterThanOrEqual(9);expect(visible).toBeLessThanOrEqual(11);
 expect(await wrap.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy();
 console.log('PASS 产品列表滚动前完整可见 '+visible+' 项；弹窗宽度 '+box!.width);
 }finally{await api('/logout',token,'POST')}
})
