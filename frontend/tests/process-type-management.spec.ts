import {test,expect,type Page} from '@playwright/test'
const api=(path:string)=>path.replace(/^\/(?:dev|prod)-api/,'')
async function setup(page:Page,mode:'maintain'|'readonly'='maintain'){
 const options:any[]=[];const writes:any[]=[];let next=50
 const perms=mode==='maintain'
  ?['metal:process:list','metal:process:add','metal:process:edit','metal:process:remove','metal:process-type:list','metal:process-type:add','metal:process-type:edit','metal:process-type:remove']
  :['metal:process:list','metal:process-type:list']
 await page.context().addCookies([{name:'Admin-Token',value:'process-types',url:String(test.info().project.use.baseURL||'http://127.0.0.1:5173')}]);
 await page.route('**/{dev-api,prod-api}/**',async route=>{const req=route.request(),p=api(new URL(req.url()).pathname);let r:any={code:200,data:[],rows:[],total:0};
  if(p==='/getInfo')r={code:200,user:{userId:2,userName:'test',factoryId:'7'},roles:['staff'],permissions:perms};
  else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:'process',component:'metal/process/index'},{path:'type-management',component:'metal/type-management/index'}]}];
  else if(p.endsWith('/factories'))r.data=r.rows=[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}];
  else if(p==='/metal/process/list'){r.rows=[];r.total=0}
  else if(p==='/metal/process/types'){
   if(req.method()==='GET')r.data=options
   else if(req.method()==='POST'){const body=req.postDataJSON();writes.push({method:'POST',...body});const row={id:String(next++),name:body.name,version:0,factoryId:body.factoryId};options.push(row);r.data=row}
  }
  else if(p.startsWith('/metal/process/types/')){
   const id=p.split('/').at(-1)!;const row=options.find(x=>x.id===id)
   if(req.method()==='PUT'){const body=req.postDataJSON();writes.push({method:'PUT',id,...body});Object.assign(row,{name:body.name,version:row.version+1});r.data=row}
   else if(req.method()==='DELETE'){writes.push({method:'DELETE',id,version:new URL(req.url()).searchParams.get('version')});const i=options.findIndex(x=>x.id===id);if(i>=0)options.splice(i,1);r.data=true}
  }
  else if(p.startsWith('/metal/type-management/process'))r={code:400,msg:'工序类型请在工序管理中维护'}
  else if(p.endsWith('/list')&&p.includes('type-management')){r.rows=[];r.total=0}
  await route.fulfill({json:r})});
 await page.goto('/metal/process');await expect(page.locator('.master-table')).toBeVisible();return{options,writes}
}
test('process page maintains types with create edit and delete',async({page})=>{
 test.setTimeout(60000)
 const state=await setup(page);
 await page.getByRole('button',{name:'维护工序类型',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'维护工序类型'});
 await dialog.getByPlaceholder('请输入工序类型名称').fill('快捷打磨');
 await dialog.getByRole('button',{name:'添加',exact:true}).click();
 await expect.poll(()=>state.options.some(o=>o.name==='快捷打磨')).toBeTruthy();
 await page.getByRole('button',{name:'维护工序类型',exact:true}).click();
 await expect(dialog.getByRole('cell',{name:'快捷打磨',exact:true})).toBeVisible();
 await dialog.getByRole('button',{name:'编辑',exact:true}).click();
 const editDialog=page.getByRole('dialog',{name:'编辑工序类型'});
 await expect(editDialog).toBeVisible();
 await editDialog.getByPlaceholder('请输入工序类型名称').fill('精磨');
 await editDialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect.poll(()=>state.writes.some(w=>w.method==='PUT'&&w.name==='精磨')).toBeTruthy();
 await expect(dialog.getByRole('cell',{name:'精磨',exact:true})).toBeVisible();
 await dialog.getByRole('row').filter({hasText:'精磨'}).getByRole('button',{name:'删除',exact:true}).click();
 await page.locator('.el-message-box').getByRole('button',{name:'删除',exact:true}).click();
 await expect.poll(()=>state.writes.some(w=>w.method==='DELETE'&&String(w.version)==='1')).toBeTruthy();
})
test('process list permission alone hides type maintenance button',async({page})=>{
 await setup(page,'readonly');
 await expect(page.getByRole('button',{name:'维护工序类型',exact:true})).toHaveCount(0);
})

test('process editor keeps type selector and inline maintenance together',async({page})=>{
 const state=await setup(page)
 await page.locator('.master-toolbar').getByRole('button',{name:'添加',exact:true}).click()
 const editor=page.getByRole('dialog',{name:'添加工序',exact:true})
 await editor.getByRole('textbox',{name:/工序名称$/}).fill('草稿工序')
 const button=editor.getByRole('button',{name:'维护工序类型',exact:true})
 await expect(button).toBeVisible()
 await page.screenshot({path:test.info().outputPath('process-editor.png'),animations:'disabled'})
 const select=await editor.locator('.material-type-control .el-select').boundingBox(),bounds=await button.boundingBox()
 expect(select).toBeTruthy();expect(bounds).toBeTruthy();expect(Math.abs(select!.y+select!.height/2-bounds!.y-bounds!.height/2)).toBeLessThan(1)
 await button.click();const maintenance=page.getByRole('dialog',{name:'维护工序类型',exact:true})
 await maintenance.getByRole('textbox').fill('表单新增类型');await maintenance.getByRole('button',{name:'添加',exact:true}).click()
 await expect.poll(()=>state.options.some(o=>o.name==='表单新增类型')).toBe(true)
 await expect(editor.getByRole('textbox',{name:/工序名称$/})).toHaveValue('草稿工序')
 await editor.getByRole('combobox',{name:/工序类型$/}).press('ArrowDown')
 await expect(editor.getByRole('option',{name:'表单新增类型',exact:true})).toBeVisible()
})
