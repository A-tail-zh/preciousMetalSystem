import {test,expect,type Page,type Locator} from '@playwright/test'

async function choose(page:Page,picker:Locator,name:string){
 await picker.press('ArrowDown')
 const id=await picker.getAttribute('aria-controls')
 await page.locator(`[id="${id}"]`).getByRole('option',{name,exact:true}).click()
}

async function fixture(page:Page,baseURL:string){
 let material:any={id:'9',factoryId:'101',factoryName:'本厂',code:'M009',materialType:'粉料',metalTypeId:'5',metalTypeName:'铱',version:0,storageConfiguration:{form:null,measurementMode:null,locked:false}}
 const saved:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'material-config-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:'material',component:'metal/material/index'}]}]
  if(path==='/metal/options/factories')body.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/filter-options/factories')body={code:200,rows:[{id:'101',name:'本厂'}],hasMore:false}
  if(path==='/metal/options/material-types')body.data=[{id:'1',name:'粉料',materialCategory:'原材料'}]
  if(path==='/metal/metal-types')body.data=[{id:'5',name:'铱'}]
  if(path==='/metal/material/list')body={code:200,rows:[material],total:1}
  if(path==='/metal/material/9/edit-context'||path==='/metal/material/9')body.data=material
  if(path==='/metal/material'&&route.request().method()==='PUT'){
   const payload=route.request().postDataJSON();saved.push(payload);material={...material,...payload,version:material.version+1}
  }
  await route.fulfill({json:body})
 })
 await page.goto('/metal/material')
 await choose(page,page.getByRole('combobox',{name:'所属工厂',exact:true}),'本厂')
 async function edit(){await page.getByRole('row').filter({hasText:'M009'}).getByRole('button',{name:'编辑',exact:true}).click();const dialog=page.getByRole('dialog',{name:'编辑物料',exact:true});await expect(dialog).toBeVisible();return dialog}
 return {edit,saved}
}

test('missing historical configuration stays blank; choosing powder fixes weight and persists on reopen',async({page,baseURL})=>{
 const {edit,saved}=await fixture(page,baseURL!)
 const dialog=await edit()
 await expect(dialog.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'材料形态',exact:true})})).not.toContainText('粉状')
 await expect(dialog.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'计量方式',exact:true})})).not.toContainText('计重')
 await choose(page,dialog.getByRole('combobox',{name:'材料形态',exact:true}),'粉状')
 await expect(dialog.getByRole('combobox',{name:'计量方式',exact:true})).toBeDisabled()
 await expect(dialog.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'计量方式',exact:true})})).toContainText('计重')
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(saved[0].storageConfiguration).toMatchObject({form:'POWDER',measurementMode:'WEIGHT'})
 const reopened=await edit()
 await expect(reopened.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'材料形态',exact:true})})).toContainText('粉状')
 await expect(reopened.getByRole('combobox',{name:'计量方式',exact:true})).toBeDisabled()
})

test('explicit metal and piece configuration is preserved regardless of material name',async({page,baseURL})=>{
 const {edit,saved}=await fixture(page,baseURL!)
 const dialog=await edit()
 await choose(page,dialog.getByRole('combobox',{name:'材料形态',exact:true}),'金属料')
 await expect(dialog.getByRole('combobox',{name:'计量方式',exact:true})).toBeEnabled()
 await choose(page,dialog.getByRole('combobox',{name:'计量方式',exact:true}),'计件')
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(saved[0].storageConfiguration).toMatchObject({form:'METAL',measurementMode:'PIECE'})
 const reopened=await edit()
 await expect(reopened.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'材料形态',exact:true})})).toContainText('金属料')
 await expect(reopened.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'计量方式',exact:true})})).toContainText('计件')
})
