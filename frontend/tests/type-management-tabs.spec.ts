import {test,expect,type Page} from '@playwright/test'
import {businessNavigation} from '../src/utils/business-navigation'
async function setup(page:Page,{maintain=true,material=true,admin=false}={}){
 const permissions=[...(material?['metal:material:list','metal:material:add','metal:material:edit','metal:material:query']:[]),...(maintain?['list','query','add','edit','remove'].map(a=>`metal:material-type:${a}`):[])]
 const types:any[]=[{id:'10',factoryId:'7',factoryName:'甲厂',name:'原有类型',materialCategory:'旧料',version:0},{id:'11',factoryId:'7',factoryName:'甲厂',name:'被引用类型',materialCategory:'粉料',version:0},{id:'12',factoryId:'8',factoryName:'乙厂',name:'乙厂类型',materialCategory:'块料',version:0}]
 const writes:any[]=[],reads:string[]=[],materials:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'material-types',url:String(test.info().project.use.baseURL)}])
 await page.route('**/{dev-api,prod-api}/**',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname.replace(/^\/(?:dev|prod)-api/,'');let r:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')r={code:200,user:{userId:admin?1:2,userName:'test',factoryId:'7'},roles:[admin?'admin':'staff'],permissions}
  else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:'material',component:'metal/material/index'},{path:'type-management',component:'metal/type-management/index'}]}]
  else if(p==='/metal/filter-options/factories'){r.rows=[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}];r.hasMore=false}
  else if(p.endsWith('/factories'))r.data=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}]
  else if(p==='/metal/metal-types')r.data=[{id:'3',name:'铂'}]
  else if(p==='/metal/options/material-types')r.data=types.filter(t=>t.factoryId===u.searchParams.get('factoryId'))
  else if(p==='/metal/material/list'){r.rows=materials;r.total=materials.length}
  else if(p==='/metal/material'&&req.method()==='POST'){const body=req.postDataJSON();writes.push({path:p,...body});materials.push({id:'30',...body});r.data={id:'30',...body}}
  else if(p.startsWith('/metal/type-management/material')){
   reads.push(u.pathname+u.search)
   if(p.endsWith('/list')){r.rows=types.filter(t=>t.factoryId===u.searchParams.get('factoryId')&&t.name.includes(u.searchParams.get('name')||''));r.total=r.rows.length}
   else if(req.method()==='GET')r.data=types.find(t=>t.id===p.split('/').at(-1))
   else{
    const body=req.postDataJSON();writes.push({path:p,...body})
    if(p.endsWith('/batch-delete')){
     if(body.items.some((i:any)=>i.id==='11'))r={code:409,msg:'类型已被引用，本次未删除任何记录'}
     else for(const item of body.items){const i=types.findIndex(t=>t.id===item.id);if(i>=0)types.splice(i,1)}
    }else if(req.method()==='PUT'){
     const t=types.find(t=>t.id===p.split('/').at(-1))
     if(t.id==='11')r={code:409,msg:'类型已被引用，不能改名、变更分类或删除'}
     else{Object.assign(t,body,{version:t.version+1});r.data=t}
    }else{const t={id:'20',...body,version:0,factoryName:body.factoryId==='7'?'甲厂':'乙厂'};types.push(t);r.data=t}
   }
  }
  await route.fulfill({json:r})
 })
 return{permissions,types,writes,reads,materials}
}
async function open(page:Page){await page.goto('/metal/material');await expect(page.locator('.master-table')).toBeVisible();const factory=page.getByRole('search').getByRole('combobox',{name:'所属工厂',exact:true});if(await factory.count()){await factory.press('ArrowDown');await page.getByRole('option',{name:'甲厂',exact:true}).click()}await page.locator('.master-toolbar').getByRole('button',{name:'添加',exact:true}).click();return page.getByRole('dialog',{name:'添加物料',exact:true})}
async function manage(page:Page){await page.getByRole('button',{name:'维护物料类型',exact:true}).click();return page.getByRole('dialog',{name:'维护物料类型',exact:true})}
test('old address redirects; navigation and direct access require material permission',async({page})=>{
 const s=await setup(page,{material:false})
 expect(businessNavigation({id:2,sessionReady:true,permissions:s.permissions}).flatMap(g=>g.items).some(i=>i.to==='/metal/type-management')).toBe(false)
 await page.goto('/metal/type-management');await expect(page).toHaveURL(/\/401$/)
 expect(s.reads).toEqual([])
})
test('material permission alone cannot maintain types',async({page})=>{
 await setup(page,{maintain:false});const d=await open(page)
 await expect(d.getByRole('combobox',{name:/物料类型名称$/})).toBeEnabled()
 await expect(page.getByRole('button',{name:'维护物料类型',exact:true})).toHaveCount(0)
})
test('create selects new type and category, preserves draft and saves material',async({page})=>{
 const s=await setup(page);const d=await open(page);await d.getByRole('textbox',{name:/物料编码$/}).fill('MAT001')
 const m=await manage(page);await expect(m.getByRole('combobox',{name:/所属工厂$/})).toHaveCount(0)
 await m.getByRole('button',{name:'添加',exact:true}).click();const e=page.getByRole('dialog',{name:'添加物料类型',exact:true})
 await e.getByRole('textbox',{name:/物料类型名称$/}).fill('新粉料');await e.getByRole('combobox',{name:'物料分类',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'粉料',exact:true}).click();await e.getByRole('button',{name:'确定',exact:true}).click()
 await expect(m.getByRole('cell',{name:'新粉料',exact:true})).toBeVisible();await m.getByRole('button',{name:'关闭',exact:true}).click()
 await expect(d.getByRole('textbox',{name:/物料编码$/})).toHaveValue('MAT001');await expect(d.getByRole('textbox',{name:'物料分类',exact:true})).toHaveValue('粉料')
 await d.getByRole('combobox',{name:'金属类型',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'铂',exact:true}).click();await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect.poll(()=>s.materials.length).toBe(1);expect(s.materials[0].materialType).toBe('新粉料');expect(s.materials[0].factoryId).toBe('7')
 expect(s.reads.every(u=>!u.includes('factoryId=8'))).toBe(true)
})
test('edit delete and referenced protection retain material draft',async({page})=>{
 const s=await setup(page);const d=await open(page);await d.getByRole('textbox',{name:/物料编码$/}).fill('DRAFT')
 const m=await manage(page);await m.getByRole('row').filter({hasText:'原有类型'}).getByRole('button',{name:'编辑',exact:true}).click()
 const e=page.getByRole('dialog',{name:'编辑物料类型',exact:true});await e.getByRole('textbox',{name:/物料类型名称$/}).fill('已改名');await e.getByRole('button',{name:'确定',exact:true}).click();await expect(m.getByRole('cell',{name:'已改名',exact:true})).toBeVisible()
 await m.getByRole('row').filter({hasText:'被引用类型'}).getByRole('button',{name:'编辑',exact:true}).click();await e.getByRole('textbox',{name:/物料类型名称$/}).fill('禁止修改');await e.getByRole('button',{name:'确定',exact:true}).click();await expect(e).toContainText('类型已被引用');expect(s.types.find(t=>t.id==='11').name).toBe('被引用类型')
 await e.getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('button',{name:'放弃修改',exact:true}).click()
 for(const name of ['被引用类型','已改名']){await m.getByRole('row').filter({hasText:name}).getByRole('button',{name:'删除',exact:true}).click();await page.locator('.el-message-box').getByRole('button',{name:'删除',exact:true}).click();if(name==='被引用类型')await expect(page.locator('.el-message').filter({hasText:'类型已被引用，本次未删除任何记录'}).last()).toBeVisible();else await expect(m.getByRole('cell',{name,exact:true})).toHaveCount(0)}
 await m.getByRole('button',{name:'关闭',exact:true}).click();await expect(d.getByRole('textbox',{name:/物料编码$/})).toHaveValue('DRAFT')
})
test('factory switch clears selection and maintenance stays in current factory',async({page})=>{
 const s=await setup(page,{admin:true});const d=await open(page)
 for(const [factory,type]of [['甲厂','原有类型'],['乙厂','乙厂类型']]){
  const field=d.getByRole('combobox',{name:/所属工厂$/});await field.press('ArrowDown');await page.locator('#'+await field.getAttribute('aria-controls')).getByRole('option',{name:factory,exact:true}).click()
  await expect(d.getByRole('textbox',{name:'物料分类',exact:true})).toHaveValue('选择物料类型后自动显示')
  const m=await manage(page);await expect(m.getByRole('cell',{name:type,exact:true})).toBeVisible();await expect(m.getByRole('cell',{name:factory==='甲厂'?'乙厂类型':'原有类型',exact:true})).toHaveCount(0);await m.getByRole('button',{name:'关闭',exact:true}).click()
  await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown');await page.getByRole('option',{name:type,exact:true}).click()
 }
 expect(s.reads.some(u=>u.includes('factoryId=8'))).toBe(true)
})
