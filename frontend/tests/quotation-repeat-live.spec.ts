import {test,expect} from '@playwright/test'
import {api,login,ok} from '../../tools/local-test-client.mjs'
test('连续计算、重复商品、修改和移除后重新计算',async({page,context})=>{
 test.skip(process.env.LIVE_MASTER!=='1','需要真实后端');test.setTimeout(60000)
 const token=await login()
 try{
  await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.goto('/metal/quotation')
  const fill=async(index:number)=>{const item=page.locator('.goods-item').nth(index);await item.locator('.el-select__wrapper').click();await item.getByRole('option',{name:'铱杆实心',exact:true}).click();await item.getByLabel('商品数量',{exact:true}).fill('1');await item.getByLabel('直径：',{exact:true}).fill('10');await item.getByLabel('长度：',{exact:true}).fill('20')}
  await fill(0);await page.getByRole('button',{name:'添加商品',exact:true}).click();await fill(1)
  const expected=ok(await api('/metal/quotation/calculate',token,'POST',{category:'铱杆实心',dimensions:{diameter:'10',length:'20'},quantity:1})).data
  const price=(BigInt(expected.totalPrice.replace('.',''))*2n).toString().padStart(7,'0');const combined=price.slice(0,-6)+'.'+price.slice(-6)
  const button=page.getByRole('button',{name:'开始计算',exact:true}),result=page.locator('.quote-results')
  for(let i=0;i<3;i++){await button.click();await expect(result).toContainText('商品2：');await expect(result.locator('strong')).toHaveText(`总报价：${combined} 元`)}
  await page.locator('.goods-item').nth(1).getByLabel('商品数量',{exact:true}).fill('2');await expect(result).toHaveCount(0);await button.click();await expect(result).toContainText('商品2：')
  await page.locator('.goods-item').nth(1).getByRole('button',{name:'移除商品',exact:true}).click();await expect(result).toHaveCount(0);await button.click();await expect(result.locator('strong')).toHaveText(`总报价：${expected.totalPrice} 元`)
 }finally{await api('/logout',token,'POST')}
})
