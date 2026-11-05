import {expect,type Page,type Locator} from './isolated-live-test'
import {productDimensions} from '../../src/views/metal/master-config'

export async function chooseQuoteOption(page:Page,scope:Locator,label:string,value:string){
 const field=scope.getByRole('combobox',{name:label,exact:true})
 await expect(field).toBeEnabled();await field.press('ArrowDown')
 await page.getByRole('option',{name:value,exact:true}).click()
}

export async function fillLiveQuote(page:Page,factory:string,shape:string,dimensions:Record<string,string>,quantity=2,index=0){
 if(index===0)await chooseQuoteOption(page,page.locator('.quote-factory'),'所属工厂',factory)
 const item=page.locator('.goods-item').nth(index)
 for(const [label,value] of [['加工类型','来料定制'],['金属类型','铱'],['产品类型',shape]])await chooseQuoteOption(page,item,label,value)
 await item.getByLabel('商品数量',{exact:true}).fill(String(quantity))
 for(const field of productDimensions[shape])await item.getByLabel(field.label+'（'+(field.unit??'cm')+'）',{exact:true}).fill(dimensions[field.key])
 for(const [label,value] of [['来料粉料重量（g）','10'],['来料非粉料重量（g）','5'],['理论损失重量（g）','1']])await item.getByLabel(label,{exact:true}).fill(value)
 return item
}
