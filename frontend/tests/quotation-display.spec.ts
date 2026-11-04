import {test,expect,type Page} from '@playwright/test'
const hidden=['单件体积','密度','粉料单价','非粉料单价','欠料单价','理论损失单价','π','弧底系数','粉料加工费','非粉料加工费','欠料产品费用','历史基础单价']
async function mount(page:Page,legacy=false){
 await page.route('**/dev-api/**',route=>route.request().url().endsWith('/image')?route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64')}):route.fulfill({json:{code:200,data:[],rows:[],total:0}}))
 await page.goto('/login');await page.waitForLoadState('networkidle')
 await page.evaluate(async old=>{
  const vuePath='/node_modules/.vite/deps/vue.js',epPath='/node_modules/.vite/deps/element-plus.js',componentPath='/src/views/metal/quotation/QuotationDetail.vue'
  const vue=await import(vuePath),ep=await import(epPath),c=await import(componentPath)
  const item={category:'铱平底坩埚',processingTypeName:'欠料定制',quantity:10,dimensions:{innerDiameter:'7.70'},dimensionFields:[{key:'innerDiameter',label:'内径',unit:'cm'}],powderWeight:'100',nonPowderWeight:'200',lossWeight:'300',volume:'260.80',unitWeight:'5907.12',totalWeight:'59071.20',powderAmount:'123.00',nonPowderAmount:'456.00',productAmount:'1181424.00',lossAmount:'6000.00',totalPrice:'1187424.00',...(old?{unitPrice:'1.234567'}:{}),rule:{density:'22.65',powderPrice:'10',nonPowderPrice:'15',shortagePrice:'20',lossPrice:'10',basePrice:'9'},volumeRule:{piValue:'3.14',curvedBottomFactor:'1.03'}}
  const quote={id:'1',quoteNo:'QT-DISPLAY',factoryName:'测试厂',createTime:'2026-11-04',snapshotVersion:old?1:2,totalPrice:'1187424.00',items:[item]}
  const host=document.createElement('div');host.id='display-test';host.style.cssText='position:absolute;inset:0;background:white;padding:16px;box-sizing:border-box';document.body.replaceChildren(host)
  vue.createApp({render:()=>vue.h(c.default,{quote,onDownload:(value:any)=>(window as any).__downloaded=value})}).use(ep.default).mount(host)
 },legacy)
 await expect(page.locator('.quotation-detail')).toBeVisible();await expect(page.locator('.quotation-image .el-image')).toBeVisible()
}
for(const width of [1920,1440,768,390])test(`quote hides calculation details at ${width}`,async({page})=>{
 await page.setViewportSize({width,height:900});await mount(page)
 const detail=page.locator('.quotation-detail');for(const label of hidden)await expect(detail).not.toContainText(label)
 for(const label of ['内径','单件理论重量','总理论重量','损失费用','商品金额'])await expect(detail).toContainText(label)
 await expect(detail.locator('.quote-total')).toContainText('1187424.00')
 await page.screenshot({path:`../.runtime/quotation-display-20261104/quote-${width}.png`,fullPage:true,animations:'disabled'})
})
test('legacy details retain quote unit price but hide calculation inputs',async({page})=>{await mount(page,true);for(const label of hidden)await expect(page.locator('.quotation-detail')).not.toContainText(label);await expect(page.locator('.quotation-detail')).toContainText('历史单件报价')})
test('download still receives unchanged snapshot and final totals',async({page})=>{await mount(page);await page.getByRole('button',{name:'下载报价单'}).click();const value=await page.evaluate(()=>(window as any).__downloaded);expect(value.items[0].rule.density).toBe('22.65');expect(value.items[0].productAmount).toBe('1181424.00');expect(value.totalPrice).toBe('1187424.00')})
