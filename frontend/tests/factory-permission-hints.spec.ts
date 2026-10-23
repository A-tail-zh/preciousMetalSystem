import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,kind:'factory'|'product'|'line',permissions=['*:*:*'],userId=1){
 await page.context().addCookies([{name:'Admin-Token',value:'permission-hints-test',url:'http://127.0.0.1:5173'}])
 const requests:string[]=[]
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');requests.push(path)
  const row={id:'7',factoryId:'7',factoryName:'独立测试厂',name:'产品甲',code:'P7',area:'1',address:'地址',introduction:'',version:0,productionLines:[],productionLineIds:[],dimensions:{},processes:[{processId:'2',processName:'粉料',loss:'0'}]}
  let r:any={code:200,msg:'操作成功',data:[]}
  if(path==='/getInfo')r={...r,user:{userId,userName:'tester',nickName:'测试人员'},roles:userId===1?['admin']:['staff'],permissions}
  else if(path==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:kind==='line'?'production-line':kind,component:kind==='factory'?'metal/factory/index':`metal/${kind==='line'?'production-line':kind}/index`,name:'TestPage',meta:{title:'测试',noCache:true}}]}]
  else if(path.endsWith('/list'))r={...r,rows:[row],total:1}
  else if(path==='/metal/options/processes')r.data=[{id:'2',name:'粉料'}]
  else if(path==='/metal/options/factories')r.data=[{id:'7',name:'独立测试厂'}]
  else if(/\/7(?:\/edit-context)?$/.test(path))r.data=row
  await route.fulfill({json:r})
 })
 await page.goto(`/metal/${kind==='line'?'production-line':kind}`);await expect(page.locator('.el-table')).toBeVisible()
 return requests
}
test('工厂新增不显示产线且不请求产线候选',async({page})=>{
 const requests=await setup(page,'factory');await page.getByRole('button',{name:'添加',exact:true}).click()
 await expect(page.getByRole('dialog')).toBeVisible();await expect(page.getByLabel('关联产线',{exact:true})).toHaveCount(0)
 expect(requests).not.toContain('/metal/factory/line-options')
})
test('普通账号有工厂新增删除权限标记仍隐藏管理员入口',async({page})=>{
 await setup(page,'factory',['metal:factory:list','metal:factory:add','metal:factory:remove'],9)
 for(const name of ['添加','删除'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0)
 await expect(page.getByRole('columnheader',{name:'操作',exact:true})).toHaveCount(0)
})
test('产品理论损耗默认0时也有每件单位和固定说明',async({page})=>{
 await setup(page,'product');await page.getByRole('row').filter({hasText:'产品甲'}).getByRole('button',{name:'编辑',exact:true}).click()
 const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible()
 await expect(dialog.getByText('理论损耗（g/件）',{exact:true})).toBeVisible()
 await expect(dialog.getByText(/理论损耗为该工序每件产品的预计损耗/)).toBeVisible()
 await expect(dialog.getByRole('textbox',{name:'第1道工序理论损耗（g/件）'})).toHaveValue('0')
})
test('仅编辑工厂账号通过编辑上下文加载，不依赖普通详情查询',async({page})=>{
 const requests=await setup(page,'factory',['metal:factory:list','metal:factory:edit'],9)
 await page.getByRole('row').filter({hasText:'独立测试厂'}).getByRole('button',{name:'编辑',exact:true}).click()
 await expect(page.getByRole('dialog').getByLabel('工厂名称',{exact:true})).toHaveValue('独立测试厂')
 expect(requests).toContain('/metal/factory/7/edit-context');expect(requests).not.toContain('/metal/factory/7')
})
test('权限变化立即隐藏按钮并关闭旧编辑表单，再授权可以重新显示',async({page})=>{
 await setup(page,'factory');await page.getByRole('button',{name:'添加',exact:true}).click()
 await expect(page.getByRole('dialog')).toBeVisible()
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const{default:useUser}=await import(path);useUser().permissions=['metal:factory:list']})
 await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const{default:useUser}=await import(path);useUser().permissions=['*:*:*']})
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeVisible()
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const{default:useUser}=await import(path);useUser().sessionReady=false})
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
})
test('新产线工厂必选，不能保留未分配状态',async({page})=>{
 await setup(page,'line');await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog');await expect(dialog.getByLabel('所属工厂',{exact:true})).toBeVisible()
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog.getByText('请选择所属工厂',{exact:true})).toHaveCount(2)
})
test('保存响应等待期间失去会话会关闭旧表单，迟到响应不能刷新列表',async({page})=>{
 const requests=await setup(page,'factory');let release!:()=>void;const blocked=new Promise<void>(r=>release=r);let saving=false
 await page.route('**/dev-api/metal/factory',async route=>{saving=true;await blocked;await route.fulfill({json:{code:200,msg:'操作成功',factoryId:'8'}})})
 await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog')
 await dialog.getByLabel('工厂名称',{exact:true}).fill('不得跨会话保存');await dialog.getByLabel('占地面积（万㎡）',{exact:true}).fill('1');await dialog.getByLabel('详细地址',{exact:true}).fill('测试地址')
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>saving).toBeTruthy()
 const queryCount=requests.filter(p=>p==='/metal/factory/list').length
 const response=page.waitForResponse(r=>r.url().endsWith('/metal/factory')&&r.request().method()==='POST')
 try{await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const{default:useUser}=await import(path);useUser().sessionReady=false});await expect(dialog).not.toBeVisible()}finally{release()}
 await response;await page.waitForLoadState('networkidle');expect(requests.filter(p=>p==='/metal/factory/list')).toHaveLength(queryCount)
 await expect(page.getByText('添加成功',{exact:true})).toHaveCount(0)
})
test('历史ID为1的未分配产线也允许管理员补选工厂',async({page})=>{
 await setup(page,'line')
 await page.route('**/dev-api/metal/production-line/list*',r=>r.fulfill({json:{code:200,rows:[{id:'1',code:'L1',name:'旧产线',factoryId:null,productIds:[],version:0}],total:1}}))
 await page.route('**/dev-api/metal/production-line/1',r=>r.fulfill({json:{code:200,data:{id:'1',code:'L1',name:'旧产线',factoryId:null,productIds:[],version:0}}}))
 await page.reload();await page.getByRole('row').filter({hasText:'旧产线'}).getByRole('button',{name:'编辑',exact:true}).click()
 await expect(page.getByRole('dialog').getByLabel('所属工厂',{exact:true})).toBeEnabled()
 const dialog=page.getByRole('dialog');await dialog.locator('.line-factory-select .el-select__wrapper').click();await page.getByRole('option',{name:'独立测试厂',exact:true}).click();await page.keyboard.press('Escape')
 let payload:any;await page.route('**/dev-api/metal/production-line',async r=>{payload=r.request().postDataJSON();await r.fulfill({json:{code:200}})})
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>payload?.factoryId).toBe('7');expect(payload.id).toBe('1')
})
test('相同身份和权限的刷新不清空正在编辑的表单',async({page})=>{
 await setup(page,'factory');await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByLabel('工厂名称',{exact:true}).fill('保留输入')
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const{default:useUser}=await import(path);useUser().permissions=[...useUser().permissions]})
 await expect(dialog.getByLabel('工厂名称',{exact:true})).toHaveValue('保留输入')
})
for(const width of [1920,1440,768,390])test(`产品工序提示在${width}px不溢出且已有数值不变`,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page,'product')
 await page.getByRole('row').filter({hasText:'产品甲'}).getByRole('button',{name:'编辑',exact:true}).click()
 const dialog=page.getByRole('dialog');await expect(dialog.getByText(/理论损耗为该工序每件产品的预计损耗/)).toBeVisible()
 await expect(dialog.getByRole('textbox',{name:'第1道工序理论损耗（g/件）'})).toHaveValue('0')
 const bounds=await dialog.boundingBox();expect(bounds!.x).toBeGreaterThanOrEqual(0);expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(width+1)
 expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy()
 await page.screenshot({path:`../.runtime/product-process-hints-${width}.png`,fullPage:true,animations:'disabled'})
})
for(const width of [1920,1440,768])test(`工序控件与操作按钮在${width}px水平对齐`,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page,'product')
 await page.getByRole('row').filter({hasText:'产品甲'}).getByRole('button',{name:'编辑',exact:true}).click()
 const dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'添加工序',exact:true}).click();await dialog.getByRole('button',{name:'添加工序',exact:true}).click()
 for(const row of await dialog.locator('.process-step').all()){
  const select=await row.locator('.el-select__wrapper').boundingBox(),input=await row.locator('.el-input__wrapper').boundingBox()
  expect(Math.abs(select!.y-input!.y)).toBeLessThanOrEqual(1)
  for(const button of await row.getByRole('button').all()){const box=await button.boundingBox();expect(Math.abs(box!.y-select!.y)).toBeLessThanOrEqual(1);expect(Math.abs(box!.height-select!.height)).toBeLessThanOrEqual(1)}
 }
 await page.screenshot({path:`../.runtime/process-step-aligned-${width}.png`,fullPage:true,animations:'disabled'})
})
test('390px工序操作按钮整组排列且不横向溢出',async({page})=>{
 await page.setViewportSize({width:390,height:1000});await setup(page,'product');await page.getByRole('row').filter({hasText:'产品甲'}).getByRole('button',{name:'编辑',exact:true}).click()
 const dialog=page.getByRole('dialog');const row=dialog.locator('.process-step').first();const boxes=await Promise.all((await row.getByRole('button').all()).map(b=>b.boundingBox()))
 expect(Math.max(...boxes.map(b=>b!.y))-Math.min(...boxes.map(b=>b!.y))).toBeLessThanOrEqual(1)
 expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy();await page.screenshot({path:'../.runtime/process-step-aligned-390.png',fullPage:true,animations:'disabled'})
})
