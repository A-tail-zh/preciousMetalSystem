import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,admin=true){
 const lists:string[]=[];await page.context().addCookies([{name:'Admin-Token',value:'material-scope-test',url:String(test.info().project.use.baseURL)}]);
 await page.route('**/dev-api/**',async route=>{const u=new URL(route.request().url()),p=u.pathname.replace('/dev-api','');let r:any={code:200,data:[],rows:[],total:0};
 if(p==='/getInfo')r={code:200,user:{userId:admin?1:2,userName:'test'},roles:[admin?'admin':'staff'],permissions:['metal:material:list','metal:material:add']};
 else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:'material',component:'metal/material/index'}]}];
 else if(p.endsWith('/factories')){if(admin)expect(u.searchParams.has('factoryId')).toBe(false);r.data=r.rows=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}]}
 else if(p==='/metal/options/material-types')r.data=[{id:'1',name:'厂内类型',materialCategory:'粉料'}];
 else if(p==='/metal/metal-types')r.data=[{id:'1',name:'铂'}];
 else if(p==='/metal/material/list'){lists.push(u.search);r.rows=[{id:'1',factoryId:u.searchParams.get('factoryId'),code:'厂内物料',materialType:'厂内类型'}];r.total=1}
 await route.fulfill({json:r})});await page.goto('/metal/material');return lists
}
async function factory(page:Page,name:string){await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
test('admin must select factory; changing factory clears all filters and queries only the selected factory',async({page})=>{
 const lists=await setup(page);const toolbar=page.getByRole('search');
 for(const name of ['物料编码','物料类型名称','物料分类','规格型号','金属类型'])await expect(toolbar.getByLabel(name,{exact:true})).toBeDisabled();
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeDisabled();expect(lists).toHaveLength(0);
 await factory(page,'甲厂');await expect.poll(()=>lists.length).toBeGreaterThan(0);expect(lists.every(x=>x.includes('factoryId=7'))).toBe(true);
 await toolbar.getByLabel('物料编码',{exact:true}).fill('CODE');await toolbar.getByLabel('规格型号',{exact:true}).fill('SPEC');
 await toolbar.getByRole('combobox',{name:'物料分类',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'粉料',exact:true}).click();
 await factory(page,'乙厂');await expect.poll(()=>lists.at(-1)).toContain('factoryId=8');
 for(const name of ['物料编码','物料类型名称','物料分类','规格型号','金属类型'])await expect(toolbar.getByLabel(name,{exact:true})).toHaveValue('');
 expect(lists.at(-1)).not.toContain('CODE');await page.getByRole('button',{name:'重置',exact:true}).click();await expect(page.getByRole('button',{name:'查询',exact:true})).toBeDisabled();await expect(page.getByText('厂内物料',{exact:true})).toHaveCount(0)
})
test('ordinary user uses fixed factory and reset retains it',async({page})=>{
 const lists=await setup(page,false);await expect.poll(()=>lists.length).toBeGreaterThan(0);await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'重置',exact:true}).click();expect(lists.every(x=>x.includes('factoryId=7'))).toBe(true);await expect(page.getByRole('button',{name:'查询',exact:true})).toBeEnabled()
})
