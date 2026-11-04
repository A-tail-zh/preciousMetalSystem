import {test,expect} from '@playwright/test'
for(const kind of ['process-batch','work-report'])for(const contract of ['qualified','historical','raw-task'])test(kind+' RAW quantity '+contract,async({page})=>{
 const routePath=kind==='process-batch'?'measured-batch-fixture':kind
 const qualified=contract==='qualified',row={id:'B',batchId:'B',factoryId:'F',orderNo:'WO-RAW',outputStage:'RAW',measurementMode:'PIECE',effectiveMeasurementMode:'PIECE',measurementPolicyVersion:contract==='historical'?2:3,taskType:contract==='raw-task'?'RAW_PROCESSING':'PRODUCTION',plannedQuantity:10,completedQuantity:8,averageLoss:'1.25',issuedWeight:'100',outputWeight:'90',lossWeight:'10',status:kind==='process-batch'?'SETTLED':'NORMAL',materials:[]}
 await page.context().addCookies([{name:'Admin-Token',value:'raw-stage-test',url:'http://127.0.0.1:5197'}]);await page.route('**/dev-api/**',async route=>{
  const p=new URL(route.request().url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')out={code:200,user:{userId:2,userName:'批次员'},roles:['factory'],permissions:['metal:'+kind+':list','metal:'+kind+':query']}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:routePath,component:'metal/'+kind+'/index',name:'RawStage'+kind}]}]
  else if(p==='/metal/'+kind+'/list')out={code:200,rows:[row],total:1}
  else if(p==='/metal/'+kind+'/B')out.data=row
  await route.fulfill({json:out})
 });await page.goto('/metal/'+routePath);await expect(page.locator('.warehouse-table')).toBeVisible()
 if(kind==='process-batch')await page.evaluate(()=>{
  // The real legacy URL redirects; mock its injected route to exercise this retained Page branch.
  const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router
  router.currentRoute.value={...router.currentRoute.value,path:'/metal/process-batch',fullPath:'/metal/process-batch'};window.dispatchEvent(new Event('focus'))
 })
 const table=page.locator('.warehouse-table');await expect(table).toContainText('WO-RAW')
 const heading=kind==='process-batch'?'完成数量':'实际完成件数'
 if(qualified){await expect(table.locator('thead')).toContainText(heading);await expect(table.locator('tbody')).toContainText('8');await table.getByRole('button',{name:'查看',exact:true}).click();const detail=page.getByRole('dialog');await expect(detail).toContainText(heading);await expect(detail).toContainText('8')}
 else{await expect(table.locator('thead')).not.toContainText(heading);await expect(table.locator('thead')).not.toContainText('平均损耗')}
})
