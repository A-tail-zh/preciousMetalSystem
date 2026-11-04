import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,permissions=['*:*:*'],locked=false){
 const posts:any[]=[],filters:string[]=[],metals=[{id:'1',name:'铱'},{id:'2',name:'铂'}]
 const row={id:'9',factoryId:'7',factoryName:'物料测试厂',version:0,code:'MAT9',materialType:'旧料',specification:'旧杆规格',metalTypeId:'1',metalTypeName:'铱',classificationLocked:locked}
 await page.context().addCookies([{name:'Admin-Token',value:'material-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const r=route.request(),url=new URL(r.url()),path=url.pathname.replace('/dev-api','');let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:2,userName:'material-user',factoryId:'7'},roles:['material'],permissions}
  else if(path==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:'material',component:'metal/material/index'}]}]
  else if(path==='/metal/options/factories')body.data=[{id:'7',name:'物料测试厂'}];else if(path==='/metal/metal-types'){if(r.method()==='POST'){posts.push({path,data:r.postDataJSON()});metals.push({id:'3',name:r.postDataJSON().name.trim()});body.id='3'}else body.data=metals}
  else if(path==='/metal/material/list'){filters.push(url.search);body.rows=[row];body.total=1}
  else if(/^\/metal\/material\/9/.test(path))body.data=row
  else if(path==='/metal/material'){posts.push({path,data:r.postDataJSON()});body.id='10'}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/material');await expect(page.locator('.master-table')).toBeVisible();return {posts,filters}
}
async function choose(page:Page,dialog:any,label:string,name:string){await dialog.getByRole('combobox',{name:new RegExp(label+'$')}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}

for(const width of [1920,1440,768,390])test('material has five fields and three types at '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});const {posts}=await setup(page)
 for(const label of ['物料编码','物料类型','规格型号','金属类型'])await expect(page.getByRole('columnheader',{name:label,exact:true})).toBeVisible()
 await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加物料',exact:true})
 await d.getByLabel('物料编码',{exact:true}).fill('NEW');await choose(page,d,'物料类型','粉料');await choose(page,d,'金属类型','铂')
 expect(await d.locator('.el-form-item').evaluateAll(items=>items.map(i=>i.querySelector('label')?.textContent?.trim()))).toEqual(['物料编码','所属工厂','物料类型','规格型号','金属类型'])
 expect(await d.evaluate(e=>e.getBoundingClientRect().left>=0&&e.getBoundingClientRect().right<=innerWidth+1)).toBeTruthy()
 await page.screenshot({path:`../.runtime/material-20261102/material-${width}.png`,fullPage:true})
 await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>posts.length).toBe(1)
 expect(posts[0].data).toMatchObject({code:'NEW',materialType:'粉料',metalTypeId:'2'});expect(posts[0].data).not.toHaveProperty('name')
})
test('canonical type requires a metal and preserves draft when adding a shared metal',async({page})=>{
 const {posts}=await setup(page,['metal:material:list','metal:material:add'])
 await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加物料',exact:true})
 await d.getByLabel('物料编码',{exact:true}).fill('DRAFT');await choose(page,d,'物料类型','块料');await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect(d.locator('.el-form-item__error')).toContainText('金属类型');expect(posts).toHaveLength(0)
 await d.getByRole('button',{name:'添加金属类型',exact:true}).click();const metal=page.getByRole('dialog',{name:'添加金属类型',exact:true})
 await metal.getByRole('textbox').fill('钌');await metal.getByRole('button',{name:'保存',exact:true}).click();await expect(metal).not.toBeVisible()
 await expect(d.getByLabel('物料编码',{exact:true})).toHaveValue('DRAFT');await expect(d.locator('.metal-type-control')).toContainText('钌')
 await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>posts.length).toBe(2)
 expect(posts[1].data).toMatchObject({materialType:'块料',metalTypeId:'3'})
})
test('referenced classification is locked while code and specification remain editable',async({page})=>{
 await setup(page,undefined,true);await page.getByRole('row').filter({hasText:'MAT9'}).getByRole('button',{name:'编辑',exact:true}).click()
 const d=page.getByRole('dialog',{name:'编辑物料',exact:true});await expect(d.getByRole('combobox',{name:/物料类型$/})).toBeDisabled();await expect(d.getByRole('combobox',{name:/金属类型$/})).toBeDisabled()
 await expect(d.getByLabel('物料编码',{exact:true})).toBeEnabled();await expect(d.getByLabel('规格型号',{exact:true})).toBeEnabled();await expect(d).toContainText('物料已有库存或仓储引用')
})
test('query-only account can view material but cannot create shared metals',async({page})=>{
 await setup(page,['metal:material:list','metal:material:query']);await page.getByRole('row').filter({hasText:'MAT9'}).getByRole('button',{name:'查看',exact:true}).click()
 const d=page.getByRole('dialog',{name:'查看物料',exact:true});await expect(d.getByLabel('物料编码',{exact:true})).toBeDisabled();await expect(d.getByRole('button',{name:'添加金属类型',exact:true})).toHaveCount(0)
})
test('filter submits independent metal and material type',async({page})=>{
 const {filters}=await setup(page);const toolbar=page.getByRole('search')
 await toolbar.getByRole('combobox',{name:'金属类型',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'铂',exact:true}).click()
 await toolbar.getByRole('combobox',{name:'物料类型',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'旧料',exact:true}).click()
 await toolbar.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>filters.at(-1)).toContain('metalTypeId=2');expect(decodeURIComponent(filters.at(-1)!)).toContain('materialType=旧料')
})
