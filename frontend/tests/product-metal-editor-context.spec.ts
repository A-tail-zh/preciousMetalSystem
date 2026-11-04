import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,kind:'product'|'material',delay:'post'|'refresh'|null){
 const label=kind==='product'?'产品':'物料',root='/metal/'+kind
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message))
 let release!:()=>void,started=false,finished=false,created=false
 const gate=new Promise<void>(resolve=>release=resolve)
 await page.context().addCookies([{name:'Admin-Token',value:'metal-context-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','')
  let body:any={code:200,data:[],rows:[],total:0},delayed=false
  if(path==='/getInfo')body={code:200,user:{userId:2,userName:'metal-user',factoryId:'7'},roles:['staff'],permissions:['*:*:*']}
  else if(path==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:kind,component:`metal/${kind}/index`}]}]
  else if(path==='/metal/options/factories')body.data=[{id:'7',name:'产品测试厂'}];else if(path==='/metal/metal-types'){
   if(req.method()==='POST'){
    if(delay==='post'){delayed=true;started=true;await gate}
    created=true;body.id='2'
   }else{
    if(created&&delay==='refresh'&&!started){delayed=true;started=true;await gate}
    body.data=[{id:'1',name:'铱'},...(created?[{id:'2',name:'草稿A新增金属'}]:[])]
   }
  }else if(path==='/metal/product/calculation-rule')body.data=[{metalTypeId:'1',metalTypeName:'铱',factoryId:'7',density:'22.65',version:0},...(created?[{metalTypeId:'2',metalTypeName:'草稿A新增金属',factoryId:'7',density:null,version:0}]:[])]
  await route.fulfill({json:body});if(delayed)finished=true
 })
 await page.goto(root)
 const parent=()=>page.getByRole('dialog',{name:'添加'+label,exact:true})
 async function open(){await page.getByRole('button',{name:'添加',exact:true}).click();return parent()}
 async function addMetal(){
  await parent().getByRole('button',{name:'添加金属类型',exact:true}).click()
  const metal=page.getByRole('dialog',{name:'添加金属类型',exact:true})
  await metal.getByLabel('金属名称',{exact:true}).fill('草稿A新增金属')
  await metal.getByRole('button',{name:'保存',exact:true}).click()
 }
 return {label,parent,open,addMetal,release,errors,started:()=>started,finished:()=>finished}
}

for(const kind of ['product','material'] as const){
 test(kind+' keeps the later draft when old metal refresh completes',async({page})=>{
  const f=await setup(page,kind,'refresh')
  try{
   await f.open();await f.addMetal();await expect.poll(f.started).toBe(true)
   await f.parent().getByRole('button',{name:'取消',exact:true}).click();await f.open()
   await f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true}).fill('DRAFT-B')
   f.release();await expect.poll(f.finished).toBe(true);await page.waitForTimeout(150)
   await expect(f.parent().locator('.metal-type-control')).toContainText('请选择金属类型')
   await expect(f.parent().locator('.metal-type-control')).not.toContainText('草稿A新增金属')
   await expect(f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true})).toHaveValue('DRAFT-B')
   expect(f.errors).toEqual([])
  }finally{f.release()}
 })

 for(const delay of ['post','refresh'] as const)test(kind+' ignores old metal '+delay+' after account changes',async({page})=>{
  const f=await setup(page,kind,delay)
  try{
   await f.open();await f.addMetal();await expect.poll(f.started).toBe(true)
   await page.evaluate(async()=>{
    const auth='/src/utils/auth.ts',path='/src/store/modules/user.ts',user=(await import(path)).default()
    ;(await import(auth)).setToken('next-account')
    user.token='next-account';user.id=3
   })
   await expect(f.parent()).not.toBeVisible();await f.open()
   await f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true}).fill('NEXT-ACCOUNT')
   f.release();await expect.poll(f.finished).toBe(true);await page.waitForTimeout(150)
   await expect(f.parent().locator('.metal-type-control')).not.toContainText('草稿A新增金属')
   await expect(f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true})).toHaveValue('NEXT-ACCOUNT')
   expect(f.errors).toEqual([])
  }finally{f.release()}
 })

 test(kind+' selects a new metal in the same draft',async({page})=>{
  const f=await setup(page,kind,null);await f.open()
  await f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true}).fill('SAME-DRAFT')
  await f.addMetal()
  await page.evaluate(async()=>{let vm=(document.querySelector('.master-page') as any)?.__vueParentComponent;while(vm&&vm.type?.__name!=='MasterDataPage')vm=vm.parent;if(!vm)throw Error('MasterDataPage is not mounted');await vm.setupState.probeEditor({isCurrent:()=>true,quiet:true})})
  await expect(f.parent().getByRole('button',{name:'确定',exact:true})).toBeEnabled()
  await expect(f.parent().locator('.metal-type-control')).toContainText('草稿A新增金属')
  await expect(f.parent().getByLabel(kind==='product'?'产品编号':'物料编码',{exact:true})).toHaveValue('SAME-DRAFT')
  expect(f.errors).toEqual([])
 })
}
