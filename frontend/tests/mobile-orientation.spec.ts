import {test,expect} from '@playwright/test'
test('rotation within mobile breakpoint closes menu and releases scroll lock',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.context().addCookies([{name:'Admin-Token',value:'orientation-fixture',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async r=>{const p=new URL(r.request().url()).pathname;let out:any={code:200,data:[],rows:[],total:0};if(p.endsWith('/getInfo'))out={code:200,user:{userId:2,userName:'验收'},roles:['staff'],permissions:['metal:quotation:list']};else if(p.endsWith('/getRouters'))out.data=[{path:'/metal',component:'Layout',children:[{path:'quotation',component:'metal/quotation/index',name:'RotationQuote'}]}];else if(p.endsWith('/quotation/context'))out.data={rule:{piValue:'3.141593'}};await r.fulfill({json:out})});
 await page.goto('/metal/quotation');const toggle=page.getByRole('button',{name:'打开导航菜单',exact:true}),drawer=page.getByRole('dialog',{name:'导航菜单',exact:true});await toggle.click();await expect(drawer).toBeVisible();
 await page.setViewportSize({width:768,height:390});await expect(drawer).not.toBeVisible();await expect(page.locator('body')).not.toHaveClass(/el-popup-parent--hidden/);expect((await page.locator('.prototype-main').boundingBox())!.width).toBeGreaterThanOrEqual(767);
 await toggle.click();await expect(drawer).toBeVisible();await page.setViewportSize({width:390,height:844});await expect(drawer).not.toBeVisible();await expect(page.locator('body')).not.toHaveClass(/el-popup-parent--hidden/);
})
