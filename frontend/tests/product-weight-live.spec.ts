import {test,expect} from '@playwright/test'
import {api,ok,detail,sql} from '../../tools/local-test-client.mjs'
import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {registerPageResources} from './helpers/fixture-registration'
const flat={'内径（cm）':'2','外径（cm）':'4','壁厚（cm）':'0.5','内高（cm）':'3','底厚（cm）':'1'}
const samples:Array<[string,Record<string,string>,string,string]>=[
 ['平底坩埚',flat,'21.20','480.18'],['环',{'外径（cm）':'4','内径（cm）':'2','厚（cm）':'1'},'9.42','213.36'],
 ['杆',{'内径（cm）':'0','外径（cm）':'2','长度（cm）':'1'},'6.28','142.24'],
 ['梯形',{'上内径（cm）':'4','上外径（cm）':'6','壁厚（cm）':'0.5','长内高（cm）':'3','下内径（cm）':'2','夹角（°）':'0','底厚（cm）':'1'},'52.60','1191.39'],
 ['弧底',flat,'21.83','494.45'],['靶',{'内径（cm）':'2','壁厚（cm）':'0.5','底厚（cm）':'1'},'7.07','160.14'],
 ['方形坩埚',{'长（cm）':'5','宽（cm）':'4','高（cm）':'3','内长（cm）':'2','内宽（cm）':'2','内高（cm）':'2'},'52.00','1177.80'],
 ['方形盖',{'长（cm）':'5','宽（cm）':'4','高（cm）':'3'},'60.00','1359.00']
]
test('real eight-type forms calculate, save, preserve legacy data and convert explicitly',async({page,context,baseURL})=>{
 test.skip(process.env.LIVE_PRODUCT_TYPES!=='1','需要独立MySQL、Redis及API')
 if(process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_MYSQL_PORT||!process.env.TEST_API_BASE_URL||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_REDIS_PORT)throw Error('Explicit isolated services required')
 test.setTimeout(180000)
 const f=await workOrderFixture();registerPageResources(page,f);let original:any
 const choose=async(label:string,value:string)=>{await page.getByRole('dialog').getByRole('combobox',{name:new RegExp(label+'$')}).press('ArrowDown');await page.getByRole('option',{name:value,exact:true}).click()}
 try{
  original=ok(await api('/metal/volume-rule',f.admin)).data;ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:'3.14',curvedBottomFactor:'1.03',version:original.version}))
  const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'页面金属'})).id);f.made.metalTypes.push(metal)
  ok(await api('/metal/quote-rule',f.admin,'PUT',{metalTypeId:metal,version:0,density:'22.65',powderPrice:'0',nonPowderPrice:'0',shortagePrice:'0',lossPrice:'0'}))
  await page.route('**/dev-api/**',async route=>{const u=new URL(route.request().url());const response=await route.fetch({url:process.env.TEST_API_BASE_URL+u.pathname.replace('/dev-api','')+u.search});await route.fulfill({response})})
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:baseURL!}]);await page.goto('/metal/product')
  for(const [index,[type,fields,volume,weight]] of samples.entries()){
   await page.setViewportSize({width:1440,height:1000});await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog')
   await d.getByLabel('产品编号',{exact:true}).fill('PV2_UI_'+f.suffix+'_'+index);await d.getByLabel('产品名称',{exact:true}).fill(f.prefix+'页面'+type)
   await choose('金属类型',f.prefix+'页面金属');await choose('产品类型',type)
   for(const [label,value] of Object.entries(fields))await d.getByLabel(label,{exact:true}).fill(value)
   await d.getByRole('heading').click();await expect(d.getByLabel('产品体积（cm³）',{exact:true})).toHaveValue(volume);await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveValue(weight);await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveAttribute('readonly','')
   if(type==='梯形')for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:1000});expect(await d.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy();await page.screenshot({path:`.runtime/product-v2-live-${width}.png`,fullPage:true})}
   await d.getByRole('button',{name:'确定',exact:true}).click();await expect(d).not.toBeVisible()
   const saved=ok(await api('/metal/product/list?code=PV2_UI_'+f.suffix+'_'+index,f.admin)).rows.find((p:any)=>p.code==='PV2_UI_'+f.suffix+'_'+index);expect(saved).toBeTruthy();if(!f.made.products.includes(saved.id))f.made.products.push(saved.id)
   const p=await detail('/metal/product',saved.id,f.admin);expect(Number(p.volume)).toBe(Number(volume));expect(Number(p.weight)).toBe(Number(weight));expect(p.calculationVersion).toBe(2)
  }
  const legacy=String(ok(await api('/metal/product',f.admin,'POST',{metalTypeId:'1',productType:'环',dimensions:{outerDiameter:'4',innerDiameter:'2',thickness:'1'},name:f.prefix+'旧版环',code:'PV2_UI_OLD_'+f.suffix,processes:[]})).id);f.made.products.push(legacy)
  sql(`UPDATE metal_product SET calculation_version=1,dimensions=JSON_OBJECT('outerDiameter','4','innerDiameter','2','height','1.234567'),volume=11.234567,theoretical_weight=77.123456 WHERE product_id=${legacy}`)
  await page.reload();await page.getByRole('row').filter({hasText:f.prefix+'旧版环'}).getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog')
  await expect(d.getByLabel('高度（cm）',{exact:true})).toBeDisabled();await expect(d.getByLabel('理论成品净重（g）',{exact:true})).toHaveValue('77.12')
  await d.getByLabel('产品名称',{exact:true}).fill(f.prefix+'旧版环改名');await d.getByRole('button',{name:'确定',exact:true}).click();await expect(d).not.toBeVisible();expect(Number((await detail('/metal/product',legacy,f.admin)).weight)).toBe(77.123456)
  await page.getByRole('row').filter({hasText:f.prefix+'旧版环改名'}).getByRole('button',{name:'编辑',exact:true}).click();await choose('产品类型','环');await expect(d.getByLabel('高度（cm）',{exact:true})).toHaveCount(0)
  for(const [label,value] of Object.entries(samples[1][1]))await d.getByLabel(label,{exact:true}).fill(value)
  await d.getByRole('button',{name:'确定',exact:true}).click();await expect(d).not.toBeVisible();expect((await detail('/metal/product',legacy,f.admin)).calculationVersion).toBe(2)
 }finally{try{if(original){const current=ok(await api('/metal/volume-rule',f.admin)).data;ok(await api('/metal/volume-rule',f.admin,'PUT',{...original,version:current.version}))}}finally{await f.cleanup()}}
})
