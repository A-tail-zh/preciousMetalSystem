import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,baseURL:string,grants={value:['*:*:*']},ordinary=false){
 await page.context().addCookies([{name:'Admin-Token',value:'permission-ui-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  let r:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')r={code:200,user:{userId:ordinary?9:1,userName:ordinary?'worker':'admin',factoryId:ordinary?'101':null},roles:[ordinary?'worker':'admin'],permissions:grants.value}
  if(path==='/getRouters')r.data=[{path:'/system',name:'System',component:'Layout',children:grants.value.includes('*:*:*')?[{path:'role',name:'Role',component:'metal/role/index',meta:{title:'角色管理'}}]:[]}]
  if(path==='/metal/options/factories')r.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/options/menus')r.data=[{id:'1',parentId:'0',name:'产品管理'},{id:'2',parentId:'1',name:'产品管理query',perms:'metal:product:query'},{id:'3',parentId:'1',name:'产品管理add',perms:'metal:product:add'},{id:'4',parentId:'1',name:'产品管理/edit',perms:'metal:product:edit'},{id:'5',parentId:'1',name:'产品管理remove',perms:'metal:product:remove'},{id:'6',parentId:'1',name:'产品管理/export',perms:'metal:product:export'}]
  if(r.rows?.length)r.total=r.rows.length
  await route.fulfill({json:r})
 })
}
test('permission tree translates actions with and without separators',async({page,baseURL})=>{
 await setup(page,baseURL!);await page.goto('/system/role')
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const tree=page.getByRole('region',{name:'菜单和按钮权限列表'})
 for(const label of ['产品管理查看','产品管理新增','产品管理/修改','产品管理删除','产品管理/导出'])await expect(tree.getByText(label,{exact:true})).toBeVisible()
 expect(await tree.innerText()).not.toMatch(/query|add|edit|remove|export/)
})
