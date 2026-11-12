import {test,expect,type Page} from '@playwright/test'
const FIXED=['平底坩埚','环','杆','梯形','弧底','靶','方形坩埚','方形盖']
async function setup(page:Page,admin=false){
 await page.context().addCookies([{name:'Admin-Token',value:'product-catalog',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{const req=route.request(),u=new URL(req.url()),p=u.pathname.replace('/dev-api','');let result:any={code:200,data:[],rows:[],total:0}
 if(p==='/getInfo')result={code:200,user:{userId:admin?1:2,userName:'product-user',factoryId:'7'},roles:['staff'],permissions:admin?['*:*:*']:['metal:product:list','metal:product:query','metal:product:add','metal:product:edit']}
 else if(p==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index'}]}]
 else if(p==='/metal/options/factories')result.data=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}]
 else if(p==='/metal/filter-options/factories'){result.rows=[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}];result.hasMore=false}
 else if(p==='/metal/options/product-types')result.data=FIXED.map(name=>({id:name,name}))
 else if(p==='/metal/product/list'){result.rows=[{id:'9',factoryId:'7',factoryName:'甲厂',code:'HISTORY',name:'历史产品',productType:'旧类型',calculationVersion:2,version:0,metalTypeId:'3',dimensions:{length:'1',width:'1',height:'1'},processes:[]}];result.total=1}
 else if(p==='/metal/product/9')result.data={id:'9',factoryId:'7',factoryName:'甲厂',code:'HISTORY',name:'历史产品',productType:'旧类型',calculationVersion:2,version:0,metalTypeId:'3',dimensions:{length:'1',width:'1',height:'1'},processes:[]}
 else if(p==='/metal/metal-types')result.data=[{id:'3',name:'铂'}]
 else if(p==='/metal/product/calculation-rule')result.data=[{metalTypeId:'3',density:null,version:0}]
 await route.fulfill({json:result})});await page.goto('/metal/product');await expect(page.locator('.master-table')).toBeVisible()
}
async function open(page:Page){await page.getByRole('button',{name:'添加',exact:true}).click();return page.getByRole('dialog',{name:'添加产品',exact:true})}
test('product editor and filter expose the fixed eight product types',async({page})=>{
 await setup(page);const d=await open(page);await d.getByRole('combobox',{name:/产品类型$/}).press('ArrowDown')
 for(const type of FIXED)await expect(page.getByRole('option',{name:type,exact:true})).toBeVisible()
 await page.getByRole('option',{name:'环',exact:true}).click();await d.getByRole('button',{name:'取消',exact:true}).click()
 const search=page.getByRole('search');await search.getByRole('combobox',{name:'产品类型',exact:true}).press('ArrowDown')
 for(const type of FIXED)await expect(page.getByRole('option',{name:type,exact:true})).toBeVisible()
})
test('factory switch keeps the fixed product types available',async({page})=>{
 await setup(page,true);const d=await open(page);await expect(d.getByRole('combobox',{name:/产品类型$/})).toBeDisabled()
 await d.getByRole('combobox',{name:/所属工厂$/}).press('ArrowDown');await page.getByRole('option',{name:'乙厂',exact:true}).click()
 await d.getByRole('combobox',{name:/产品类型$/}).press('ArrowDown')
 for(const type of FIXED)await expect(page.getByRole('option',{name:type,exact:true})).toBeVisible()
})
test('historical invalid product type stays visible but blocks save until reselected',async({page})=>{
 await setup(page);await page.getByRole('row').filter({hasText:'HISTORY'}).getByRole('button',{name:'编辑',exact:true}).click()
 const d=page.getByRole('dialog',{name:'编辑产品',exact:true})
 await d.getByRole('combobox',{name:/产品类型$/}).press('ArrowDown')
 await expect(page.getByRole('option',{name:'旧类型（已失效，请重新选择）',exact:true})).toBeDisabled()
 await expect(page.getByRole('option',{name:'环',exact:true})).toBeVisible()
 await expect(d.getByText('所选类型已失效，请重新选择',{exact:true})).toBeVisible()
 await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled()
})
