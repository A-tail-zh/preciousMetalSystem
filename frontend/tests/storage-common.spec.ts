import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page){
 const requests:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'storage-common-test',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace('/dev-api','');requests.push(path);await route.fulfill({json:path==='/getInfo'?{code:200,user:{userId:1,userName:'admin',nickName:'管理员'},permissions:['*:*:*'],roles:['admin']}:{code:200,data:[],rows:[],total:0}})})
 await page.goto('/metal/storage-warehouses');return requests
}
test('CSV handles Chinese quoted multiline fields and rejects malformed quotes',async({page})=>{
 await setup(page)
 const result=await page.evaluate(async()=>{const path='/src/views/metal/storage-preview/csv.ts';const {parseCsv,csvText}=await import(path);const rows=[['中文,引号"','两行\n内容','=HYPERLINK("a")','  +1']];const text=csvText(['一','二','三','四'],rows);const parsed=parseCsv(text);const errors=[];for(const broken of ['a,"bad','"a"trailing','a,b"c']){try{parseCsv(broken)}catch{errors.push(true)}}return{parsed,errors}})
 expect(result.parsed[1]).toEqual(['中文,引号"','两行\n内容',"'=HYPERLINK(\"a\")","'  +1"]);expect(result.errors).toHaveLength(3)
})
test('warehouse module shares memory across pages and clears on leaving and refreshing',async({page})=>{
 const requests=await setup(page)
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'演示工厂一',exact:true}).click()
 await page.getByRole('button',{name:'添加',exact:true}).click();await page.getByRole('textbox',{name:'仓库名称',exact:true}).fill('仅本轮仓库');await page.getByRole('button',{name:'确认',exact:true}).click()
 await page.getByRole('link',{name:'仓储管理',exact:true}).click()
 await page.getByRole('combobox',{name:'仓库筛选',exact:true}).press('ArrowDown');await expect(page.getByRole('option',{name:'仅本轮仓库',exact:true})).toBeVisible();await page.keyboard.press('Escape')
 await page.getByRole('link',{name:'仓库管理',exact:true}).click();await expect(page.getByRole('cell',{name:'仅本轮仓库',exact:true})).toBeVisible()
 await page.getByRole('link',{name:'生产工单管理',exact:true}).click();await page.waitForURL('**/metal/production-order');await expect(page.locator('.production-order')).toBeVisible();await page.getByRole('link',{name:'仓库管理',exact:true}).click()
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'演示工厂一',exact:true}).click();await expect(page.getByRole('cell',{name:'仅本轮仓库',exact:true})).toHaveCount(0)
 await page.reload();await expect(page.getByRole('button',{name:'添加',exact:true})).toBeDisabled()
 expect(requests.filter(p=>p.startsWith('/metal/')&&!p.startsWith('/metal/options/factories'))).toEqual([])
})
test('storage pages fit mobile and retain table scrolling without old requests',async({page})=>{
 const requests=await setup(page);await page.setViewportSize({width:390,height:844})
 for(const path of ['/metal/storage-warehouses','/metal/storage-inventory','/metal/storage-orders']){
  await page.goto(path)
  await expect(page.getByText('前端预览',{exact:true})).toBeVisible()
  await expect(page.locator('.storage-page')).toHaveCSS('padding-left','12px')
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth+1)).toBe(true)
  await page.screenshot({path:'.runtime/storage-preview-20261109/'+path.split('/').pop()+'-mobile.png',fullPage:true,animations:'disabled'})
 }
 expect(requests.filter(p=>p.startsWith('/metal/'))).toEqual([])
})
