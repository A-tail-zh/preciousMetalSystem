import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {test,expect} from './helpers/isolated-live-test'
import {api,ok,detail} from '../../tools/local-test-client.mjs'
import {fillLiveQuote} from './helpers/live-quotation'

test('真实自动体积、只读保存和报价',async({page,context})=>{
 test.skip(process.env.LIVE_MASTER!=='1','需要真实后端');test.setTimeout(120000)
 const f=await workOrderFixture(),ids:string[]=[],token=f.admin
 try{
  const rule=ok(await api('/metal/volume-rule?factoryId='+f.factoryA,token)).data,pi=Number(rule.piValue)
  const samples=[
   {shape:'杆',dimensions:{innerDiameter:'0',outerDiameter:'10',length:'20'},volume:pi*1000},
   {shape:'杆',dimensions:{innerDiameter:'10',outerDiameter:'12',length:'20'},volume:pi*440},
   {shape:'环',dimensions:{innerDiameter:'10',outerDiameter:'12',thickness:'2'},volume:pi*22},
   {shape:'平底坩埚',dimensions:{innerDiameter:'10',outerDiameter:'12',wallThickness:'1',innerHeight:'20',bottomThickness:'2'},volume:pi*292}
  ]
  for(const [index,sample] of samples.entries()){
   const response=ok(await api('/metal/product',token,'POST',{factoryId:f.factoryA,metalTypeId:f.metalA,calculationVersion:2,code:'CALC'+f.suffix+'_'+index,name:f.prefix+'计算'+index,productType:sample.shape,dimensions:sample.dimensions,volume:'999',processes:[]}))
   ids.push(String(response.id));f.made.products.push(String(response.id))
   expect(Number((await detail('/metal/product',response.id,token)).volume).toFixed(2)).toBe(sample.volume.toFixed(2))
  }
  await context.addCookies([{name:'Admin-Token',value:token,url:String(test.info().project.use.baseURL)}]);await page.goto('/metal/product')
  const row=page.getByRole('row').filter({hasText:f.prefix+'计算0'});await row.getByRole('button',{name:'编辑',exact:true}).click()
  const dialog=page.getByRole('dialog'),volume=dialog.getByLabel('产品体积（cm³）',{exact:true})
  await expect(volume).toHaveAttribute('readonly','');await expect(volume).toHaveValue((pi*1000).toFixed(2))
  await dialog.getByLabel('外径（cm）',{exact:true}).fill('12');const updated=(pi*1440).toFixed(2)
  await expect(volume).toHaveValue(updated);await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
  expect(Number((await detail('/metal/product',ids[0],token)).volume).toFixed(2)).toBe(updated)
  await page.goto('/metal/quotation')
  const first=await fillLiveQuote(page,f.prefix+'厂A','杆',{innerDiameter:'0',outerDiameter:'12',length:'20'})
  const saving=page.waitForResponse(r=>r.url().endsWith('/metal/quotation/save')&&r.request().method()==='POST');await page.getByRole('button',{name:'开始计算',exact:true}).click();const saved=ok(await(await saving).json()).data;expect(Number(saved.items[0].volume).toFixed(2)).toBe(updated);await expect(page.locator('.quote-results')).toContainText(saved.items[0].unitWeight)
  await first.getByLabel('外径（cm）',{exact:true}).fill('13');await expect(page.locator('.quote-results')).toHaveCount(0)
  await page.getByRole('button',{name:'添加商品',exact:true}).click()
  const second=await fillLiveQuote(page,f.prefix+'厂A','环',{innerDiameter:'10',outerDiameter:'12',thickness:'2'},1,1)
  await page.getByRole('button',{name:'开始计算',exact:true}).click();await expect(page.locator('.quote-results .saved-item')).toHaveCount(2)
  await expect(page.locator('.quote-results .saved-item').nth(1)).toContainText('铱环')
  await second.getByRole('button',{name:'移除商品',exact:true}).click();await expect(page.locator('.goods-item')).toHaveCount(1)
 }finally{await f.cleanup()}
})
