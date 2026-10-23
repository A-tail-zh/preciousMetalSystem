import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {test,expect} from '@playwright/test'
import {api,login,ok,detail} from '../../tools/local-test-client.mjs'
import {productVolume} from '../src/utils/product-volume'
test('真实自动体积、只读保存和报价',async({page,context})=>{
 test.skip(process.env.LIVE_MASTER!=='1','需要本地后端');test.setTimeout(90000)
 const fixture=await workOrderFixture(),token=fixture.admin,suffix=Date.now(),ids:string[]=[]
 const rule=ok(await api('/metal/product/calculation-rule',token)).data
 const samples:Record<string,Record<string,string>>={铱杆实心:{diameter:'10',length:'20'},铱杆空心:{outerDiameter:'12',innerDiameter:'10',length:'20'},铱环:{outerDiameter:'12',innerDiameter:'10',height:'2'},铱坩埚平底:{innerDiameter:'10',innerHeight:'20',wallThickness:'1',bottomThickness:'2'}}
 try{
  for(const [category,dimensions]of Object.entries(samples)){
   const response=ok(await api('/metal/product',token,'POST',{code:`CALC${suffix}_${ids.length}`,name:`${fixture.prefix}计算验收${suffix}_${category}`,category,dimensions,volume:'999',processes:[]}));ids.push(response.id);fixture.made.products.push(String(response.id))
   expect((await detail('/metal/product',response.id,token)).volume).toBe(productVolume(category,dimensions,rule.piValue))
  }
  await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/product')
  const row=page.getByRole('row').filter({hasText:`${fixture.prefix}计算验收${suffix}_铱杆实心`});await row.getByRole('button',{name:'编辑',exact:true}).click();const dialog=page.getByRole('dialog'),volume=dialog.getByLabel('产品体积（cm³）',{exact:true})
  await expect(volume).toHaveAttribute('readonly','');await expect(volume).toHaveValue(productVolume('铱杆实心',samples.铱杆实心,rule.piValue)!)
  await dialog.getByLabel('直径（cm）',{exact:true}).fill('12');const updated=productVolume('铱杆实心',{diameter:'12',length:'20'},rule.piValue)!
  await expect(volume).toHaveValue(updated);await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible();expect((await detail('/metal/product',ids[0],token)).volume).toBe(updated)
  await page.goto('/metal/quotation')
  const choose=async(label:string,name:string)=>{await page.locator('.el-form-item').filter({hasText:label}).locator('.el-select__wrapper').click();await page.getByRole('option',{name,exact:true}).click()}
  await choose('所属工厂',fixture.prefix+'厂A');await choose('商品类型','铱杆实心');await page.getByLabel('商品数量',{exact:true}).fill('2');await page.getByLabel('直径：',{exact:true}).fill('12');await page.getByLabel('长度：',{exact:true}).fill('20');await page.getByRole('button',{name:'开始计算',exact:true}).click()
  const calculated=ok(await api('/metal/quotation/calculate',token,'POST',{category:'铱杆实心',dimensions:{diameter:'12',length:'20'},quantity:2})).data
  for(const value of [updated,calculated.unitPrice,calculated.totalPrice])await expect(page.locator('.quote-results')).toContainText(value)
  await page.getByLabel('直径：',{exact:true}).fill('13');await expect(page.locator('.quote-results')).toHaveCount(0)
  await page.getByRole('button',{name:'添加商品',exact:true}).click();await expect(page.locator('.goods-item')).toHaveCount(2)
  const second=page.locator('.goods-item').nth(1);await second.locator('.el-select__wrapper').click();await second.getByRole('option',{name:'铱环',exact:true}).click();await second.getByLabel('商品数量',{exact:true}).fill('1');await second.getByLabel('外径：',{exact:true}).fill('12');await second.getByLabel('内径：',{exact:true}).fill('10');await second.getByLabel('高度：',{exact:true}).fill('2');await page.getByRole('button',{name:'开始计算',exact:true}).click();await expect(page.locator('.quotation-results-table .el-table__body .el-table__row')).toHaveCount(2);await expect(page.locator('.quotation-results-table .el-table__body .el-table__row').nth(1)).toContainText('铱环')
  await page.screenshot({path:'../docs/screenshots/quotation-cm-multiple.png',fullPage:true});await second.getByRole('button',{name:'移除商品',exact:true}).click()
  await choose('商品类型','铱坩埚弧底');await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeDisabled();await expect(page.getByText('该类别暂无计算公式',{exact:true})).toBeVisible()
 }finally{for(const id of ids)ok(await api('/metal/product/'+id,token,'DELETE'));await fixture.cleanup()}
})
