import {test,expect,type Page} from '@playwright/test'
import {productWeight} from '../src/utils/product-volume'

async function setup(page:Page,product?:Record<string,unknown>){
 const posts:any[]=[];if(product)product={factoryId:'7',factoryName:'产品测试厂',...product}
 await page.context().addCookies([{name:'Admin-Token',value:'product-weight-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:2,userName:'weight-user',factoryId:'7'},roles:['staff'],permissions:['*:*:*']}
  else if(path==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index'}]}]
  else if(path==='/metal/options/factories')body.data=[{id:'7',name:'产品测试厂'}];else if(path==='/metal/metal-types')body.data=[{id:'1',name:'铱'},{id:'2',name:'铂'},{id:'3',name:'钌'}]
  else if(path==='/metal/product/calculation-rule')body.data=[{metalTypeId:'1',piValue:'3.14',density:'22.65'},{metalTypeId:'2',piValue:'3.14',density:'21.45'}]
  else if(path==='/metal/product/list'){body.rows=product?[product]:[];body.total=body.rows.length}
  else if(path==='/metal/product/9')body.data=product
  else if(path==='/metal/product/calculation-preview'){const data=req.postDataJSON(),density=data.metalTypeId==='1'?'22.65':data.metalTypeId==='2'?'21.45':'';body.data={volume:'0.03',weight:productWeight('0.03',density)}}
  else if(path==='/metal/product'&&['POST','PUT'].includes(req.method())){posts.push(req.postDataJSON());body.id='9'}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/product');return posts
}
async function choose(page:Page,label:string,value:string){await page.getByRole('dialog').getByRole('combobox',{name:new RegExp(label+'$')}).press('ArrowDown');await page.getByRole('option',{name:value,exact:true}).click()}

test('weight multiplies rounded volume exactly and preserves missing values',()=>{
 expect(productWeight('0.0314','22.65')).toBe('0.68')
 expect(productWeight('0.03','21.45')).toBe('0.64')
 expect(productWeight('0.05','0.10')).toBe('0.01')
 expect(productWeight(null,'22.65')).toBeNull()
 expect(productWeight('3.14','')).toBeNull()
})
for(const width of [1920,1440,768,390])test('product weight is read-only and follows volume and metal at '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});const posts=await setup(page)
 await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog'),weight=d.getByLabel('理论成品净重（g）',{exact:true})
 await d.getByLabel('产品编号',{exact:true}).fill('WEIGHT');await d.getByLabel('产品名称',{exact:true}).fill('自动净重')
 await choose(page,'金属类型','铱');await choose(page,'产品类型','环')
 await d.getByLabel('外径（cm）',{exact:true}).fill('0.3');await d.getByLabel('内径（cm）',{exact:true}).fill('0.1');await d.getByLabel('厚（cm）',{exact:true}).fill('0.5')
 await expect(weight).toHaveAttribute('readonly','');await expect(weight).toHaveValue('0.68')
 await choose(page,'金属类型','铂');await expect(weight).toHaveValue('0.64')
 await choose(page,'金属类型','钌');await expect(weight).toHaveValue('');await expect(d.getByText('缺少体积或金属密度，暂不计算净重；可先保存产品资料。')).toHaveCount(0)
 await choose(page,'金属类型','铱');await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>posts.length).toBe(1)
 expect(posts[0]).toMatchObject({calculationVersion:2,productType:'环'});expect(posts[0].weight).toBeUndefined()
})
const historical={id:'9',version:0,code:'OLD',name:'历史产品',category:'铱杆实心',metalTypeId:'1',productType:'杆实心',dimensions:{diameter:'0.20',length:'1.00'},volume:'0.054321',weight:'99.123456',processes:[]}
test('editing a legacy product preserves its historical weight',async({page})=>{
 const posts=await setup(page,historical);await page.getByRole('row').filter({hasText:'历史产品'}).getByRole('button',{name:'编辑',exact:true}).click()
 const d=page.getByRole('dialog');await expect(d.getByLabel('产品体积（cm³）',{exact:true})).toHaveValue('0.05');await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveValue('99.12')
 await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0].weight).toBeUndefined()
})
test('viewing historical weight leaves its stored value untouched',async({page})=>{
 const posts=await setup(page,historical);await page.getByRole('row').filter({hasText:'历史产品'}).getByRole('button',{name:'查看',exact:true}).click()
 await expect(page.getByRole('dialog').getByLabel('理论成品净重（g）',{exact:true})).toHaveValue('99.12');expect(posts).toHaveLength(0)
})
