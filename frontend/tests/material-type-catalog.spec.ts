import {test,expect,type Page,type Locator} from '@playwright/test'

async function setup(page:Page,admin=false){
 const state={failed:false,options:{'7':['颗粒料'],'8':['丝材']} as Record<string,string[]>,holdFactory:'',release:()=>{},started:false,posts:[] as any[],queries:[] as string[]}
 await page.context().addCookies([{name:'Admin-Token',value:'catalog-ui',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),u=new URL(req.url()),path=u.pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:admin?1:2,userName:'catalog-user'},roles:['staff'],permissions:admin?['*:*:*']:['metal:material:list','metal:material:query','metal:material:add','metal:material:edit']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'material',component:'metal/material/index'}]}]
  else if(path==='/metal/options/factories')out.data=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}]
  else if(path==='/metal/filter-options/factories'){out.rows=[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}];out.hasMore=false}
  else if(path==='/metal/options/material-types'){
   const factory=u.searchParams.get('factoryId')!;const names=[...(state.options[factory]??[])];const failed=state.failed
   if(state.holdFactory===factory){state.started=true;await new Promise<void>(resolve=>state.release=resolve)}
   out=failed?{code:500,msg:'目录加载失败'}:{code:200,data:names.map((name,i)=>({id:String(i+1),name,materialCategory:({'颗粒料':'粉料','丝材':'块料','回收料':'旧料'} as Record<string,string>)[name]??null}))}
  }else if(path==='/metal/metal-types')out.data=[{id:'3',name:'铂'}]
  else if(path==='/metal/material/list'){state.queries.push(u.search);out.rows=[{id:'9',code:'LEGACY',factoryId:'7',factoryName:'甲厂',materialType:'历史粉料',metalTypeId:'3',metalTypeName:'铂',version:0}];out.total=1}
  else if(path.startsWith('/metal/material/9'))out.data={id:'9',code:'LEGACY',factoryId:'7',factoryName:'甲厂',materialType:'历史粉料',metalTypeId:'3',metalTypeName:'铂',version:0}
  else if(path==='/metal/material'&&req.method()!=='GET'){state.posts.push(req.postDataJSON());out.id='10'}
  await route.fulfill({json:out})
 })
 await page.goto('/metal/material');await expect(page.locator('.master-table')).toBeVisible();return state
}
async function choose(page:Page,root:Locator,label:string,name:string){await root.getByRole('combobox',{name:new RegExp(label+'$')}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
async function add(page:Page){await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加物料',exact:true});await d.getByLabel('物料编码',{exact:true}).fill('CUSTOM');return d}

test('material-only account selects custom factory type for entry and filter',async({page})=>{
 const state=await setup(page),d=await add(page)
 await choose(page,d,'物料类型名称','颗粒料');await choose(page,d,'金属类型','铂');await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect.poll(()=>state.posts.length).toBe(1);expect(state.posts[0]).toMatchObject({materialType:'颗粒料',factoryId:'7'})
 const search=page.getByRole('search');await choose(page,search,'物料类型名称','颗粒料');await search.getByRole('button',{name:'查询',exact:true}).click()
 await expect.poll(()=>decodeURIComponent(state.queries.at(-1)??'')).toContain('materialType=颗粒料')
 await search.getByRole('button',{name:'重置',exact:true}).click();await expect(search.getByRole('combobox',{name:/物料类型名称$/})).toHaveValue('')
})
test('failed option refresh preserves input, blocks save and supports retry',async({page})=>{
 const state=await setup(page),d=await add(page);await choose(page,d,'物料类型名称','颗粒料');state.failed=true
 await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown')
 await expect(d.getByRole('button',{name:'重试加载物料类型',exact:true})).toBeVisible();await d.getByRole('combobox',{name:/物料类型名称$/}).press('Escape')
 await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled();await expect(d.getByLabel('物料编码',{exact:true})).toHaveValue('CUSTOM')
 state.failed=false;await d.getByRole('button',{name:'重试加载物料类型',exact:true}).click();await expect(d.getByRole('button',{name:'确定',exact:true})).toBeEnabled()
})
test('switching factory clears choices and ignores a late prior-factory response',async({page})=>{
 const state=await setup(page,true),d=await add(page);state.holdFactory='7'
 await choose(page,d,'所属工厂','甲厂');await expect.poll(()=>state.started).toBe(true)
 await choose(page,d,'所属工厂','乙厂');state.holdFactory='';state.release()
 await choose(page,d,'物料类型名称','丝材');await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown')
 await expect(page.getByRole('option',{name:'颗粒料',exact:true})).toHaveCount(0)
 await d.getByRole('combobox',{name:/物料类型名称$/}).press('Escape');await choose(page,d,'所属工厂','甲厂')
 await expect(d.getByRole('combobox',{name:/物料类型名称$/})).toHaveValue('')
})
test('historical unregistered type remains a disabled choice on its existing record',async({page})=>{
 const state=await setup(page);await page.getByRole('row').filter({hasText:'LEGACY'}).getByRole('button',{name:'编辑',exact:true}).click()
 const d=page.getByRole('dialog',{name:'编辑物料',exact:true});await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown')
 await expect(page.getByRole('option',{name:'历史粉料（历史类型）',exact:true})).toBeDisabled();await d.getByRole('combobox',{name:/物料类型名称$/}).press('Escape')
 await d.getByLabel('规格型号',{exact:true}).fill('仅修改规格');await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect.poll(()=>state.posts.length).toBe(1);expect(state.posts[0].materialType).toBe('历史粉料')
})
test('removed selection is preserved but cannot be saved until reselected',async({page})=>{
 const state=await setup(page),d=await add(page);await choose(page,d,'物料类型名称','颗粒料');state.options['7']=['新类型']
 await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown');await expect(d.getByText('所选类型已失效，请重新选择',{exact:true})).toBeVisible()
 await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled();await page.getByRole('option',{name:'新类型',exact:true}).click()
 await expect(d.getByRole('button',{name:'确定',exact:true})).toBeEnabled()
})
test('empty directory never falls back to fixed options and unscoped admin filter is disabled',async({page})=>{
 const state=await setup(page,true);await expect(page.getByRole('search').getByRole('combobox',{name:/物料类型名称$/})).toBeDisabled()
 state.options['7']=[];const d=await add(page);await choose(page,d,'所属工厂','甲厂');await d.getByRole('combobox',{name:/物料类型名称$/}).press('ArrowDown')
 await expect(d.getByText('本厂暂无物料类型，请先在类型管理中添加',{exact:true})).toBeVisible();await expect(page.getByRole('option',{name:'粉料',exact:true})).toHaveCount(0);await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled()
})

test('material category follows selected type, resets with factory, and stays readonly',async({page})=>{
 const state=await setup(page,true);state.options['7']=['颗粒料','回收料','未分类料'];const d=await add(page)
 await choose(page,d,'所属工厂','甲厂')
 const category=d.getByRole('textbox',{name:'物料分类',exact:true});await expect(category).toHaveAttribute('readonly','')
 for(const [type,value] of [['颗粒料','粉料'],['回收料','旧料'],['未分类料','未设置']]){await choose(page,d,'物料类型名称',type);await expect(category).toHaveValue(value)}
 await choose(page,d,'所属工厂','乙厂');await expect(category).toHaveValue('选择物料类型后自动显示')
 await choose(page,d,'物料类型名称','丝材');await expect(category).toHaveValue('块料')
})

test('category filter submits each classification and reset clears it',async({page})=>{
 const state=await setup(page,true),search=page.getByRole('search')
 await expect(page.getByRole('columnheader',{name:'物料类型名称',exact:true})).toBeVisible()
 await expect(page.getByRole('columnheader',{name:'物料分类',exact:true})).toBeVisible()
 for(const category of ['旧料','粉料','块料']){await choose(page,search,'物料分类',category);await search.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>decodeURIComponent(state.queries.at(-1)??'')).toContain('materialCategory='+category)}
 await search.getByRole('button',{name:'重置',exact:true}).click();await expect(search.getByRole('combobox',{name:'物料分类',exact:true})).toHaveValue('');await expect.poll(()=>state.queries.at(-1)??'').not.toContain('materialCategory')
})
