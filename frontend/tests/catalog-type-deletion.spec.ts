import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,kind:'process'|'product',permissions=['*:*:*']){
 const origin=String(test.info().project.use.baseURL),types=[{id:'10',name:'可删除类型'},{id:'11',name:'已引用类型'}],deletes:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'type-delete-mock',url:origin}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')out={code:200,user:{userId:2,userName:'tester',factoryId:'7'},roles:['factory'],permissions}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:kind,component:`metal/${kind}/index`}]}]
  else if(p==='/metal/options/factories')out.data=[{id:'7',name:'测试工厂'}]
  else if(p==='/metal/process/types'||p==='/metal/metal-types')out.data=types
  else if(req.method()==='DELETE'){
   deletes.push(p);if(p.endsWith('/11'))out={code:500,msg:'类型已被引用，不能删除'}
   else types.splice(types.findIndex(t=>t.id===p.split('/').pop()),1)
  }
  else if(!['GET','HEAD'].includes(req.method()))throw new Error('Unexpected mock business request: '+p)
  await route.fulfill({json:out})
 })
 await page.goto(`/metal/${kind}`);await expect(page.locator('.master-table')).toBeVisible()
 return{deletes}
}
async function open(page:Page,kind:'process'|'product'){
 if(kind==='product'){await page.getByRole('button',{name:'添加',exact:true}).click();await expect(page.getByText('仅使用本厂目录',{exact:false})).toHaveCount(0)}
 await page.getByRole('button',{name:kind==='process'?'添加工序类型':'添加金属类型',exact:true}).click()
 const d=page.getByRole('dialog',{name:kind==='process'?'添加工序类型':'添加金属类型',exact:true});await expect(d.locator('.type-management-table')).toContainText('可删除类型');return d
}
for(const kind of ['process','product'] as const){
 test(`${kind}: cancel, protected failure and successful delete update options without losing input`,async({page})=>{
  const state=await setup(page,kind),d=await open(page,kind)
  await d.getByRole('textbox').fill('尚未保存的名称')
  await d.getByRole('row').filter({hasText:'可删除类型'}).getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:kind==='process'?'删除工序类型':'删除金属类型',exact:true}).getByRole('button',{name:'取消',exact:true}).click();expect(state.deletes).toHaveLength(0)
  await d.getByRole('row').filter({hasText:'已引用类型'}).getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:kind==='process'?'删除工序类型':'删除金属类型',exact:true}).getByRole('button',{name:'删除',exact:true}).click()
  await expect(page.getByText('类型已被引用，不能删除',{exact:true})).toBeVisible();await expect(d.getByRole('textbox')).toHaveValue('尚未保存的名称')
  await expect(d.getByRole('row').filter({hasText:'已引用类型'})).toBeVisible()
  await d.getByRole('row').filter({hasText:'可删除类型'}).getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:kind==='process'?'删除工序类型':'删除金属类型',exact:true}).getByRole('button',{name:'删除',exact:true}).click()
  await expect(d.getByRole('row').filter({hasText:'可删除类型'})).toHaveCount(0);await expect(d.getByRole('textbox')).toHaveValue('尚未保存的名称');expect(state.deletes).toHaveLength(2)
  if(kind==='process')await expect(d).not.toContainText('保存后可在工序')
 })
 test(`${kind}: add permission does not grant delete`,async({page})=>{await setup(page,kind,[`metal:${kind}:list`,`metal:${kind}:add`]);const d=await open(page,kind);await expect(d.getByRole('button',{name:'删除',exact:true})).toHaveCount(0)})
 test(`${kind}: logout while confirming cannot issue delete`,async({page})=>{
  const state=await setup(page,kind),d=await open(page,kind);await d.getByRole('row').filter({hasText:'可删除类型'}).getByRole('button',{name:'删除',exact:true}).click()
  await page.evaluate(async()=>{const path='/src/store/modules/user.ts';(await import(path)).default().sessionReady=false})
  const confirm=page.getByRole('dialog',{name:kind==='process'?'删除工序类型':'删除金属类型',exact:true});await confirm.getByRole('button',{name:'删除',exact:true}).click();await expect(d).not.toBeVisible();expect(state.deletes).toHaveLength(0)
 })
}
for(const kind of ['process','product'] as const)for(const width of [1920,1440,768,390])test(`${kind} type management layout ${width}`,async({page})=>{
 await page.setViewportSize({width,height:900});await setup(page,kind);const d=await open(page,kind);const b=await d.boundingBox();expect(b!.x).toBeGreaterThanOrEqual(0);expect(b!.x+b!.width).toBeLessThanOrEqual(width)
 await page.screenshot({path:`../.runtime/type-management-20261104/${kind}-${width}.png`,fullPage:true,animations:'disabled'})
})

test('deleting the selected process type clears only that applied filter',async({page})=>{
 await setup(page,'process');const select=page.getByRole('combobox',{name:'工序类型',exact:true});await select.click();await page.getByRole('option',{name:'可删除类型',exact:true}).click();await page.getByRole('button',{name:'查询',exact:true}).click()
 const d=await open(page,'process');await d.getByRole('row').filter({hasText:'可删除类型'}).getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('dialog',{name:'删除工序类型',exact:true}).getByRole('button',{name:'删除',exact:true}).click()
 await expect(d.getByRole('row').filter({hasText:'可删除类型'})).toHaveCount(0);await d.getByRole('button',{name:'取消',exact:true}).click();await expect(select).toHaveValue('')
})
