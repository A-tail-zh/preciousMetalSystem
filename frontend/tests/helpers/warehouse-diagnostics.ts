import {writeFile} from 'node:fs/promises'
import {test,type Page} from '@playwright/test'
const logs=new WeakMap<Page,any[]>()
const interesting=(url:string)=>/\/metal\/warehouse\/(work-order-context|options\/(stocks|batches))|\/metal\/warehouse-order\/outbound-submissions/.test(url)
function safeUrl(url:string){const u=new URL(url);return u.pathname+'?'+new URLSearchParams([...u.searchParams].filter(([k])=>['factoryId','warehouseId','workOrderId','itemId','processId'].includes(k))).toString()}
export function installWarehouseDiagnostics(){
 // Full traces are opt-in: normal regressions keep lightweight timings and failure screenshots.
 test.use({trace:process.env.WAREHOUSE_TRACE==='1'?'retain-on-failure':'off'})
 test.beforeEach(async({page})=>{
  const events:any[]=[];logs.set(page,events);const add=(e:any)=>{events.push({at:Date.now(),...e});if(events.length>150)events.shift()}
  page.on('request',r=>{if(interesting(r.url()))add({kind:'request',method:r.method(),url:safeUrl(r.url())})})
  page.on('response',r=>{if(interesting(r.url()))add({kind:'response',status:r.status(),url:safeUrl(r.url())})})
  page.on('requestfailed',r=>{if(interesting(r.url()))add({kind:'requestfailed',url:safeUrl(r.url()),error:r.failure()?.errorText})})
  page.on('pageerror',e=>add({kind:'pageerror',message:e.message}))
  await page.addInitScript(()=>{const events:any[]=[];(window as any).__warehouseSelectEvents=events;document.addEventListener('click',e=>{const field=(e.target as Element)?.closest?.('.el-form-item'),wrapper=(e.target as Element)?.closest?.('.el-select__wrapper'),label=field?.querySelector('label')?.textContent?.trim();if(!wrapper||!['工厂','生产工单','产品明细','工序','仓库/库房'].includes(label??''))return;const take=(phase:string)=>{const input=field?.querySelector('[role=combobox]');events.push({at:Date.now(),label,phase,controls:input?.getAttribute('aria-controls'),expanded:input?.getAttribute('aria-expanded'),disabled:(input as HTMLInputElement)?.disabled,wrapperDisabled:wrapper.classList.contains('is-disabled'),selection:field?.querySelector('.el-select__selected-item')?.textContent?.trim()});if(events.length>100)events.shift()};take('before');queueMicrotask(()=>take('after'))},true)})
 })
 test.afterEach(async({page},info)=>{if(info.status===info.expectedStatus)return;const selections=await page.evaluate(()=>(window as any).__warehouseSelectEvents??[]).catch(()=>[]);const path=info.outputPath('warehouse-selection-timing.json');await writeFile(path,JSON.stringify({events:logs.get(page),selections},null,2));await info.attach('warehouse-selection-timing',{path,contentType:'application/json'})})
}
