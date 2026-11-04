import {test,expect} from '@playwright/test'
for(const width of [1920,1440,768,390])test(`紧凑业务列表布局 ${width}px`,async({page})=>{
 await page.setViewportSize({width,height:900})
 await page.context().addCookies([{name:'Admin-Token',value:'compact-ui-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');let data:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')data={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/getRouters')data.data=[{path:'/metal',component:'Layout',children:[{path:'factory',component:'metal/factory/index',name:'CompactFactory',meta:{title:'工厂管理',noCache:true}}]}]
  if(path==='/metal/factory/list')data={code:200,total:1,rows:[{factoryId:'1',factoryName:'精密制造工厂',area:'100',address:'制造园区',introduction:'生产测试',productionLines:[],version:0,updateTime:'2026-10-24 08:00:00'}]}
  await route.fulfill({json:data})
 })
 await page.goto('/metal/factory');await expect(page.getByRole('cell',{name:'精密制造工厂',exact:true})).toBeVisible()
 const metrics=await page.evaluate(()=>{
  const box=(s:string)=>document.querySelector(s)!.getBoundingClientRect()
  return {header:box('.prototype-header').height,paginationGap:box('.factory-pagination').top-box('.factory-table').bottom,sidebar:document.querySelector('.prototype-sidebar')?.getBoundingClientRect().width??0,overflow:document.documentElement.scrollWidth>innerWidth,shellHeight:box('.prototype-shell').height}
 })
 expect(metrics.header).toBeLessThanOrEqual(64)
 expect(metrics.paginationGap).toBeLessThanOrEqual(24)
 expect(metrics.overflow).toBe(false)
 expect(metrics.shellHeight).toBeLessThanOrEqual(901)
 if(width>768){expect(metrics.sidebar).toBe(220);await expect(page.locator('.factory-table').getByRole('button',{name:'删除',exact:true})).toBeInViewport({ratio:1})}
 else await expect(page.getByRole('button',{name:'打开导航菜单'})).toBeVisible()
 await page.screenshot({path:`../.runtime/ui-polish/compact-factory-${width}.png`,fullPage:true})
})
