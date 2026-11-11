import {test,expect,type Page,type Locator} from '@playwright/test'

async function openEditor(page:Page,baseURL:string){
 const writes:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'route-table-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  if(route.request().method()!=='GET')writes.push(path)
  let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/getRouters')body={code:200,data:[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index'}]}]}
  if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]}
  if(path==='/metal/filter-options/factories')body={code:200,rows:[{id:'101',name:'本厂'}],hasMore:false}
  if(path==='/metal/options/processes')body={code:200,data:[{id:'11',name:'普通一',semanticCode:'NORMAL'},{id:'12',name:'普通二',semanticCode:'NORMAL'},{id:'13',name:'酸洗',semanticCode:'ACID_WASH'},{id:'14',name:'融定',semanticCode:'SMELTING'}]}
  if(path==='/metal/storage-config/materials')body={code:200,data:[{id:'31',code:'M31',name:'金属料',form:'METAL',measurementMode:'WEIGHT'}]}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/product')
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown')
 await page.getByRole('option',{name:'本厂',exact:true}).click()
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const editor=page.getByRole('dialog',{name:'添加产品',exact:true})
 await expect(editor).toBeVisible()
 return {editor,table:editor.getByRole('table',{name:'工序与产出配置',exact:true}),writes}
}

async function choose(page:Page,picker:Locator,name:string){
 await picker.press('ArrowDown')
 const id=await picker.getAttribute('aria-controls')
 await page.locator(`[id="${id}"]`).getByRole('option',{name,exact:true}).click()
}

test('seven-column route table preserves loss and process identity when moving and deleting',async({page,baseURL})=>{
 const {table,writes}=await openEditor(page,baseURL!)
 await expect(table.getByRole('columnheader')).toHaveText(['序号','加工工序','理论损耗(%)','计量方式','处理规则','产出类别','操作'])
 for(let i=0;i<2;i++)await table.getByRole('button',{name:'添加工序',exact:true}).click()
 await choose(page,table.getByRole('combobox',{name:'第1道加工工序',exact:true}),'普通一')
 await choose(page,table.getByRole('combobox',{name:'第2道加工工序',exact:true}),'普通二')
 await table.getByRole('textbox',{name:'第1道工序理论损耗（%）',exact:true}).fill('1.25')
 await table.getByRole('textbox',{name:'第2道工序理论损耗（%）',exact:true}).fill('2.50')
 await expect(table.getByRole('textbox',{name:'第2道工序产出类别',exact:true})).toHaveValue('成品')
 await table.getByRole('button',{name:'上移第2道工序',exact:true}).click()
 await expect(table.locator('.route-main').first()).toContainText('普通二')
 await expect(table.getByRole('textbox',{name:'第1道工序理论损耗（%）',exact:true})).toHaveValue('2.50')
 await expect(table.getByRole('textbox',{name:'第2道工序理论损耗（%）',exact:true})).toHaveValue('1.25')
 await expect(table.getByRole('button',{name:'上移第1道工序',exact:true})).toBeDisabled()
 await expect(table.getByRole('button',{name:'下移第2道工序',exact:true})).toBeDisabled()
 await table.screenshot({path:'.runtime/process-route-table-normal.png',animations:'disabled'})
 await table.getByRole('button',{name:'删除第1道工序',exact:true}).click()
 await expect(table.locator('.route-main')).toHaveCount(1)
 await expect(table.locator('.route-main')).toContainText('普通一')
 await expect(table.getByRole('textbox',{name:'第1道工序产出类别',exact:true})).toHaveValue('成品')
 expect(writes).toEqual([])
})

test('acid wash retains material details, fixed rules and derived measurement in the table',async({page,baseURL})=>{
 const {editor,table,writes}=await openEditor(page,baseURL!)
 for(let i=0;i<2;i++)await table.getByRole('button',{name:'添加工序',exact:true}).click()
 await choose(page,table.getByRole('combobox',{name:'第1道加工工序',exact:true}),'酸洗')
 await choose(page,table.getByRole('combobox',{name:'第2道加工工序',exact:true}),'普通二')
 await expect(table.getByRole('textbox',{name:'第1道工序处理规则',exact:true})).toHaveValue('酸洗')
 await expect(table.getByRole('textbox',{name:'第1道工序处理规则',exact:true})).not.toBeEditable()
 await expect(table.getByRole('textbox',{name:'第1道工序产出类别',exact:true})).toHaveValue('原材料')
 await choose(page,table.getByRole('combobox',{name:'第1道工序产出物料',exact:true}),'M31 / 金属料')
 await expect(table.getByRole('textbox',{name:'第1道工序计量方式',exact:true})).toHaveValue('计重')
 await expect(table.getByRole('textbox',{name:'第1道工序产出形态',exact:true})).toHaveValue('金属料')
 await editor.screenshot({path:'.runtime/process-route-table-acid.png',animations:'disabled'})
 await page.setViewportSize({width:720,height:900})
 expect(await editor.locator('.route-table-wrap').evaluate(el=>el.scrollWidth>el.clientWidth)).toBe(true)
 expect(writes).toEqual([])
})
