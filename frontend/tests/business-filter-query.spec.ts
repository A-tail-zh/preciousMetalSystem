import {test,expect} from '@playwright/test'

test('remote factory choices retain the typed query when loading refreshes the dropdown',async({page,baseURL})=>{
 const queries:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'filter-query-mock',url:baseURL!}])
 await page.route('**/dev-api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/dev-api','')
  let response:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'FilterInventory'}]}]
  else if(path==='/metal/filter-options/factories'){
   const keyword=url.searchParams.get('keyword')??'';queries.push(keyword)
   response={code:200,rows:keyword?[{id:'2',name:'Needle factory'}]:[{id:'1',name:'Other factory'}],hasMore:false}
  }
  await route.fulfill({json:response})
 })
 await page.goto('/metal/inventory')
 const filters=page.getByRole('search',{name:'筛选条件'}),input=filters.getByRole('combobox',{name:'工厂',exact:true})
 await input.click();await expect(page.getByRole('option',{name:'Other factory',exact:true})).toBeVisible()
 await input.fill('Needle')
 await page.getByRole('option',{name:'Needle factory',exact:true}).click()
 await filters.getByRole('button',{name:'查询',exact:true}).click()
 await expect(filters.getByText('Needle factory',{exact:true})).toBeVisible()
 expect(queries.slice(queries.indexOf('Needle'))).not.toContain('')
})
