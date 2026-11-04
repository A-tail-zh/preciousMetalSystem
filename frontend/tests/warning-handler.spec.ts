import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,options:{nickName?:string;canHandle?:boolean;status?:string;sourceVoid?:boolean}={}){
 const user={userId:2,userName:'operator-account',nickName:options.nickName??'张三'}
 const warning={id:'31',version:1,processName:'测试工序',thresholdValue:'1',theoreticalLoss:'1',averageLoss:'2',orderNo:'WARNING-HANDLER-TEST',status:options.status??'PENDING',sourceVoid:options.sourceVoid??false,result:'',handlerName:'',handledTime:''}
 const submissions:unknown[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'warning-handler-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','')
  let response:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')response={code:200,user,roles:['operator'],permissions:['metal:warning-record:list','metal:warning-record:query',...(options.canHandle===false?[]:['metal:warning-record:handle'])]}
  else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'warning-record',component:'metal/warning-record/index',name:'WarningHandlerTest'}]}]
  else if(path==='/metal/warning-record/list')response={code:200,rows:[warning],total:1}
  else if(path==='/metal/warning-record/31')response.data=warning
  else if(path==='/metal/warning-record/31/handle'){
   const body=route.request().postDataJSON();submissions.push(body)
   Object.assign(warning,{status:'HANDLED',version:2,result:body.reason,handlerName:user.nickName||user.userName,handledTime:'2026-11-03 10:00:00'})
  }
  await route.fulfill({json:response})
 })
 await page.goto('/metal/warning-record')
 const open=async()=>{
  await page.getByRole('row').filter({hasText:warning.orderNo}).getByRole('button',{name:/^(去处理|查看)$/}).click()
  return page.getByRole('dialog',{name:'预警记录详情',exact:true})
 }
 return{user,warning,submissions,open}
}

test('pending warning displays current name as read-only text and preserves saved handler for another viewer',async({page})=>{
 const s=await setup(page),dialog=await s.open()
 const handler=dialog.getByText('处理人：张三',{exact:true})
 await expect(handler).toBeVisible()
 await expect(handler.locator('input,textarea,[contenteditable=true]')).toHaveCount(0)
 await dialog.getByPlaceholder('填写处理结果').fill('已核实，后续改进')
 await dialog.getByRole('button',{name:'确认处理',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(s.submissions).toEqual([{version:1,reason:'已核实，后续改进'}])
 Object.assign(s.user,{userId:3,userName:'another-operator',nickName:'李四'})
 await page.reload()
 await s.open()
 await expect(dialog.getByText('处理结果：已核实，后续改进；处理人：张三；时间：2026-11-03 10:00:00',{exact:true})).toBeVisible()
 await expect(dialog.getByText('处理人：李四',{exact:true})).toHaveCount(0)
 await expect(dialog.getByPlaceholder('填写处理结果')).toHaveCount(0)
})

test('empty current name falls back to login account',async({page})=>{
 const s=await setup(page,{nickName:''}),dialog=await s.open()
 await expect(dialog.getByText('处理人：operator-account',{exact:true})).toBeVisible()
})

for(const scenario of [
 {name:'without handle permission',canHandle:false},
 {name:'already handled',status:'HANDLED'},
 {name:'void source',sourceVoid:true}
])test(`warning ${scenario.name} has no editable handling area`,async({page})=>{
 const s=await setup(page,scenario),dialog=await s.open()
 await expect(dialog).toBeVisible()
 await expect(dialog.getByText('处理人：张三',{exact:true})).toHaveCount(0)
 await expect(dialog.getByPlaceholder('填写处理结果')).toHaveCount(0)
 await expect(dialog.getByRole('button',{name:'确认处理',exact:true})).toHaveCount(0)
 expect(s.submissions).toHaveLength(0)
})
