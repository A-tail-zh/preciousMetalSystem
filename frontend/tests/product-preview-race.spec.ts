import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,preview:(type:string)=>Promise<any>){
 const calls:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'preview-race-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname.replace('/dev-api','');let result:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')result={code:200,user:{userId:2,userName:'preview-user',factoryId:'7'},roles:['staff'],permissions:['*:*:*']}
  else if(p==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index'}]}]
  else if(p==='/metal/options/factories')result.data=[{id:'7',name:'产品测试厂'}];else if(p==='/metal/metal-types')result.data=[{id:'1',name:'铱'}]
  else if(p==='/metal/product/calculation-rule')result.data=[{metalTypeId:'1',density:'22.65',piValue:'3.14'}]
  else if(p==='/metal/product/calculation-preview'){const type=req.postDataJSON().productType;calls.push(type);result=await preview(type)}
  await route.fulfill({json:result})
 })
 await page.goto('/metal/product');await page.getByRole('button',{name:'添加',exact:true}).click()
 const d=page.getByRole('dialog');await d.getByRole('combobox',{name:/金属类型$/}).press('ArrowDown');await page.getByRole('option',{name:'铱',exact:true}).click()
 return {d,calls}
}
async function choose(page:Page,type:string){await page.getByRole('dialog').getByRole('combobox',{name:/产品类型$/}).press('ArrowDown');await page.getByRole('option',{name:type,exact:true}).click()}
async function rod(page:Page){await choose(page,'杆');const d=page.getByRole('dialog');await d.getByLabel('内径（cm）',{exact:true}).fill('0');await d.getByLabel('外径（cm）',{exact:true}).fill('2');await d.getByLabel('长度（cm）',{exact:true}).fill('1');await d.getByRole('heading').click()}
test('late preview cannot replace a newer type result',async({page})=>{
 let release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve)
 const {d,calls}=await setup(page,async type=>{if(type==='杆'){await gate;return {code:200,data:{volume:'6.28',weight:'142.24'}}}return {code:200,data:{volume:'1.00',weight:'22.65'}}})
 try{
  await rod(page);await expect.poll(()=>calls.includes('杆')).toBeTruthy();await choose(page,'方形盖')
  for(const label of ['长（cm）','宽（cm）','高（cm）'])await d.getByLabel(label,{exact:true}).fill('1')
  await d.getByRole('heading').click();await expect(d.getByLabel('产品体积（cm³）',{exact:true})).toHaveValue('1.00');release()
  await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveValue('22.65');await expect(d.getByLabel('外径（cm）',{exact:true})).toHaveCount(0)
 }finally{release()}
})
test('closing and reopening invalidates an in-flight preview',async({page})=>{
 let release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve)
 const {d,calls}=await setup(page,async()=>{await gate;return {code:200,data:{volume:'6.28',weight:'142.24'}}})
 try{await rod(page);await expect.poll(()=>calls.length).toBeGreaterThan(0);await d.getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();release();await expect(d.getByLabel('产品体积（cm³）',{exact:true})).toHaveValue('');await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveValue('')}finally{release()}
})
test('preview failure clears the result and keeps dimension input',async({page})=>{
 const {d}=await setup(page,async()=>({code:500,msg:'夹角或尺寸无效，请核对'}));await rod(page)
 await expect(d.getByRole('alert')).toContainText('夹角或尺寸无效');await expect(d.getByLabel('外径（cm）',{exact:true})).toHaveValue('2.00');await expect(d.getByLabel('产品体积（cm³）',{exact:true})).toHaveValue('')
})
