import {test,expect} from './helpers/isolated-live-test'
import {login,api} from '../../tools/local-test-client.mjs'
for(const width of [1440,390])test(`material editor shows all types ${width}`,async({page,context})=>{
 const token=await login();try{
 const factories=(await api('/metal/options/factories',token)).data;test.skip(!factories.length,'Needs an isolated factory fixture');
 const materialTypes=(await api('/metal/options/material-types?factoryId='+factories[0].id,token)).data.map((t:{name:string})=>t.name);test.skip(!materialTypes.length,'Needs a maintained material type fixture');
 await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.setViewportSize({width,height:1000});
 await page.goto('/metal/material');await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加物料',exact:true});await expect(d).toBeVisible();
 await d.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:factories[0].name,exact:true}).click();
 if(width>480)expect((await d.boundingBox())!.width).toBeGreaterThanOrEqual(850);
 await d.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^物料类型$/})}).locator('.el-select__wrapper').click();const wrap=d.locator('.el-select-dropdown__wrap:visible');await expect(wrap).toBeVisible();
 await expect(wrap.getByRole('option')).toHaveCount(materialTypes.length);
 await page.screenshot({path:`../.runtime/material-editor-${width}.png`,fullPage:true});await wrap.getByRole('option',{name:materialTypes.at(-1),exact:true}).click();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
 }finally{await api('/logout',token,'POST')}
})
