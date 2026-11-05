import {test,expect} from './helpers/isolated-live-test'
import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {api,ok,sql} from '../../tools/local-test-client.mjs'
import {fillLiveQuote} from './helpers/live-quotation'
test('连续计算、重复商品、修改和移除后重新计算',async({page,context})=>{
 test.skip(process.env.LIVE_MASTER!=='1','需要真实后端');test.setTimeout(90000)
 const f=await workOrderFixture()
 try{
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:String(test.info().project.use.baseURL)}]);await page.goto('/metal/quotation')
  const dimensions={innerDiameter:'0',outerDiameter:'10',length:'20'}
  await fillLiveQuote(page,f.prefix+'厂A','杆',dimensions,1);await page.getByRole('button',{name:'添加商品',exact:true}).click();await fillLiveQuote(page,f.prefix+'厂A','杆',dimensions,1,1)
  const expected=ok(await api('/metal/quotation/calculate?factoryId='+f.factoryA,f.admin,'POST',{factoryId:f.factoryA,metalTypeId:f.metalA,processingType:'MATERIAL_CUSTOM',productType:'杆',dimensions,quantity:1,powderWeight:'10',nonPowderWeight:'5',lossWeight:'1'})).data
  const combined=(Number(expected.totalPrice)*2).toFixed(2),button=page.getByRole('button',{name:'开始计算',exact:true}),result=page.locator('.quote-results')
  const calculate=async(count:number,total:string)=>{const saving=page.waitForResponse(r=>r.url().endsWith('/metal/quotation/save')&&r.request().method()==='POST');await button.click();const saved=ok(await(await saving).json()).data;expect(saved.items).toHaveLength(count);expect(Number(saved.totalPrice).toFixed(2)).toBe(total);await expect(result.locator('.saved-item')).toHaveCount(count);await expect(result).toContainText(total);return saved}
  for(let i=0;i<3;i++)await calculate(2,combined)
  expect(sql('SELECT COUNT(*) FROM metal_saved_quotation WHERE factory_id='+f.factoryA)).toBe('1')
  await page.locator('.goods-item').nth(1).getByLabel('商品数量',{exact:true}).fill('2');await expect(result).toHaveCount(0)
  const changed=await calculate(2,combined);expect(changed.items[1].quantity).toBe(2);expect(Number(changed.items[1].totalWeight).toFixed(2)).toBe((Number(expected.totalWeight)*2).toFixed(2))
  await page.locator('.goods-item').nth(1).getByRole('button',{name:'移除商品',exact:true}).click();await expect(result).toHaveCount(0);await calculate(1,Number(expected.totalPrice).toFixed(2))
  expect(sql('SELECT COUNT(*) FROM metal_saved_quotation WHERE factory_id='+f.factoryA)).toBe('3')
 }finally{await f.cleanup()}
})
