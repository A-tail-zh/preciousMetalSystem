import {test,expect} from '@playwright/test'

for(const existing of [false,true])test(`product picks up a later density without losing input existing=${existing}`,async({page,baseURL})=>{
 let density:string|null=null,version=0,fail=false,reads=0;const writes:any[]=[]
 const product=()=>({id:'9',factoryId:'7',factoryName:'产品测试厂',version,code:'P9',name:'原名称',metalTypeId:'1',productType:'杆',calculationVersion:2,dimensions:{innerDiameter:'0.00',outerDiameter:'2.00',length:'1.00'},volume:'6.28',weight:version?'62.80':null,processes:[]})
 await page.context().addCookies([{name:'Admin-Token',value:'density-test',url:baseURL!}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')body={code:200,user:{userId:2,userName:'density-user',factoryId:'7'},roles:['staff'],permissions:['*:*:*']}
  if(p==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index',name:'Product'}]}]
  if(p==='/metal/options/factories')body.data=[{id:'7',name:'产品测试厂'}]
  if(p==='/metal/options/product-types')body.data=[{id:'1',name:'杆'}]
  if(p==='/metal/metal-types')body.data=[{id:'1',name:'金'}]
  if(p==='/metal/product/calculation-rule'){reads++;body=fail?{code:500,msg:'密度读取失败'}:{code:200,data:[{metalTypeId:'1',density,version:density?1:0}]}}
  if(p==='/metal/product/list')body={code:200,rows:existing?[product()]:[],total:existing?1:0}
  if(p==='/metal/product/9')body.data=product()
  if(p==='/metal/product/calculation-preview')body.data={volume:'6.28',weight:density?'62.80':null}
  if(p==='/metal/product'&&req.method()!=='GET')writes.push(req.postDataJSON())
  await route.fulfill({json:body})
 })
 await page.goto('/metal/product');if(existing)await page.getByRole('row').filter({hasText:'P9'}).getByRole('button',{name:'编辑',exact:true}).click();else await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:existing?'编辑产品':'添加产品',exact:true});await d.getByLabel('产品名称',{exact:true}).fill('保留输入')
 if(!existing){for(const[label,name]of [['金属类型','金'],['产品类型','杆']]){await d.getByRole('combobox',{name:new RegExp(label+'$')}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}for(const[label,value]of [['内径（cm）','0'],['外径（cm）','2'],['长度（cm）','1']])await d.getByLabel(label,{exact:true}).fill(value);await d.getByRole('heading').click()}
 const weight=d.getByLabel('理论成品净重（g）',{exact:true});await expect(weight).toHaveValue('')
 const before=reads;density='10.00';if(existing)version=1;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect.poll(()=>reads).toBeGreaterThan(before);await expect(weight).toHaveValue('62.80');await expect(d.getByLabel('产品名称',{exact:true})).toHaveValue('保留输入')
 if(existing){await expect(d.getByText('资料已变化，请关闭后重新加载并核对，当前输入已保留',{exact:true})).toBeVisible();await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled()}
 fail=true;const again=reads;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect.poll(()=>reads).toBeGreaterThan(again);await expect(weight).toHaveValue('62.80');await expect(d.getByLabel('产品名称',{exact:true})).toHaveValue('保留输入');expect(writes).toHaveLength(0)
})
