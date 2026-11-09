import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,admin=true,path='type-management'){
 const rows:any[]=[];const writes:any[]=[];let next=1;
 await page.context().addCookies([{name:'Admin-Token',value:'process-types',url:String(test.info().project.use.baseURL)}]);
 await page.route('**/dev-api/**',async route=>{const req=route.request(),u=new URL(req.url()),p=u.pathname.replace('/dev-api','');let r:any={code:200,data:[],rows:[],total:0};
 if(p==='/getInfo')r={code:200,user:{userId:admin?1:2,userName:'test'},roles:[admin?'admin':'staff'],permissions:admin?['*:*:*']:['metal:process-type:list']};
 else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path,component:`metal/${path}/index`}]}];
 else if(p.endsWith('/factories'))r.data=r.rows=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}];
 else if(p==='/metal/type-management/product/formula-options')r.data=['环'];
 else if(p.endsWith('/list')){r.rows=rows.filter(x=>x.factoryId===u.searchParams.get('factoryId')&&x.name.includes(u.searchParams.get('name')||''));r.total=r.rows.length}
 else if(p==='/metal/type-management/process'&&req.method()==='POST'){const data=req.postDataJSON();writes.push(data);r.data={...data,id:String(next++),version:0,factoryName:'甲厂',updatedAt:'2026-11-09T12:00:00'};rows.push(r.data)}
 else if(p.endsWith('/batch-delete')){const data=req.postDataJSON();writes.push(data);for(const item of data.items){const i=rows.findIndex(x=>x.id===item.id);if(i>=0)rows.splice(i,1)}}
 else if(p.startsWith('/metal/type-management/process/')){const row=rows.find(x=>x.id===p.split('/').at(-1));if(req.method()==='PUT'){const data=req.postDataJSON();writes.push(data);Object.assign(row,data,{version:row.version+1})}r.data=row}
 await route.fulfill({json:r})});await page.goto('/metal/'+path);return{rows,writes}
}
test('fourth process tab shares CRUD layout, scope and version',async({page})=>{
 const state=await setup(page);await expect(page.getByRole('tab')).toHaveText(['金属类型','产品类型','物料类型','工序类型']);await page.getByRole('tab',{name:'工序类型',exact:true}).click();
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeDisabled();
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'甲厂',exact:true}).click();
 await page.getByRole('button',{name:'添加',exact:true}).click();let dialog=page.getByRole('dialog');await expect(dialog.getByText('工序类型名称',{exact:true})).toBeVisible();await dialog.getByRole('textbox',{name:'工序类型名称',exact:true}).fill('打磨');await dialog.getByRole('button',{name:'确定',exact:true}).click();
 await expect(page.getByRole('cell',{name:'打磨',exact:true})).toBeVisible();await expect(page.getByRole('cell',{name:'2026-11-09 12:00:00'})).toBeVisible();
 await page.getByRole('row').filter({has:page.getByRole('cell',{name:'打磨',exact:true})}).getByRole('button',{name:'编辑'}).click();await dialog.getByRole('textbox',{name:'工序类型名称',exact:true}).fill('精磨');await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(page.getByRole('cell',{name:'精磨',exact:true})).toBeVisible();expect(state.writes.at(-1).version).toBe(0);
 await page.getByRole('row').filter({has:page.getByRole('cell',{name:'精磨',exact:true})}).getByRole('button',{name:'删除'}).click();await page.getByRole('dialog',{name:'删除确认'}).getByRole('button',{name:'删除',exact:true}).click();await expect(page.getByRole('cell',{name:'精磨',exact:true})).toHaveCount(0);expect(state.writes.at(-1).items[0].version).toBe(1);
})
test('process view permission alone opens fixed factory tab without write buttons',async({page})=>{
 await setup(page,false);await expect(page.getByRole('tab')).toHaveText(['工序类型']);await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'查询',exact:true})).toBeEnabled();
})

test('quick process type create/delete uses independent permission and current version',async({page})=>{
 await setup(page,true,'process');let options:any[]=[];const writes:any[]=[];
 await page.route('**/dev-api/metal/process/types**',async route=>{const req=route.request();let data:any=options;if(req.method()==='POST'){data={id:'50',name:req.postDataJSON().name,version:0};options.push(data)}else if(req.method()==='DELETE'){writes.push(new URL(req.url()).searchParams.get('version'));options=[]}await route.fulfill({json:{code:200,data}})});
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'甲厂',exact:true}).click();
 await page.getByRole('button',{name:'添加工序类型',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加工序类型'});await dialog.getByRole('textbox').fill('快捷打磨');await dialog.getByRole('button',{name:'确定',exact:true}).click();
 await page.getByRole('combobox',{name:'工序类型',exact:true}).press('ArrowDown');await expect(page.getByRole('option',{name:'快捷打磨',exact:true})).toBeVisible();await page.keyboard.press('Escape');
 options[0].name='类型管理改名';options[0].version=2;
 await page.getByRole('button',{name:'添加工序类型',exact:true}).click();await expect(dialog.getByRole('cell',{name:'类型管理改名',exact:true})).toBeVisible();await dialog.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('dialog',{name:'删除工序类型',exact:true}).getByRole('button',{name:'删除',exact:true}).click();await expect.poll(()=>writes.length).toBe(1);expect(writes[0]).toBe('2');
})
