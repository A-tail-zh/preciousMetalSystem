import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,readOnly=false){
 await page.context().addCookies([{name:'Admin-Token',value:'measurement-fixture',url:'http://127.0.0.1:5173'}])
 const processes=[{id:'1',name:'工序甲',processTypeName:'酸洗'},{id:'2',name:'工序乙',processTypeName:'融定'},{id:'3',name:'工序丙',processTypeName:'焊接'}]
 const row={id:'10',name:'计量测试产品',code:'MP10',version:0,metalTypeId:'1',productType:'杆',calculationVersion:2,dimensions:{innerDiameter:'0',outerDiameter:'1',length:'2'},processes:processes.map((p,i)=>({processId:p.id,processName:p.name,measurementMode:i===0?'PIECE':'WEIGHT',loss:'1.00',lossUnit:'PERCENT'}))}
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index',name:'Product'}]}]
  else if(path==='/metal/product/list')out={code:200,rows:[row],total:1}
  else if(path==='/metal/options/processes')out.data=processes
  else if(path.includes('/metal/product/10'))out.data=row
  await route.fulfill({json:out})
 })
 await page.goto('/metal/product')
 await page.locator('.master-table').getByRole('button',{name:readOnly?'查看':'编辑',exact:true}).click()
 await expect(page.getByRole('dialog')).toBeVisible()
 return page.getByRole('dialog')
}
for(const width of [1920,1440,768,390])test(`新工艺计量自动锁定且不按工序名称猜测 ${width}`,async({page})=>{
 await page.setViewportSize({width,height:1000});const dialog=await setup(page)
 for(const [index,text] of ['按重量管理（g）','按件管理（件）','按件管理（件）'].entries()){
  const input=dialog.getByRole('textbox',{name:`第${index+1}道产出计量方式`,exact:true})
  await expect(input).toHaveValue(text);await expect(input).toHaveAttribute('readonly')
 }
 await expect(dialog.getByText(/请按实际加工顺序添加工序/)).toHaveCount(0)
 expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBe(true)
})
test('查看现有产品时不覆盖保存的计量值',async({page})=>{
 const dialog=await setup(page,true)
 await expect(dialog.getByRole('textbox',{name:'第1道产出计量方式',exact:true})).toHaveValue('按件管理（件）')
 await expect(dialog.getByRole('textbox',{name:'第2道产出计量方式',exact:true})).toHaveValue('按重量管理（g）')
})
