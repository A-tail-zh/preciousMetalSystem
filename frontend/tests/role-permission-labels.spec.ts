import {test,expect} from '@playwright/test'

test('role permission tree displays Chinese actions and saves original menu IDs',async({page})=>{
 const actions=[['query','查看'],['add','新增'],['edit','修改'],['remove','删除'],['adjust','调整'],['reverse','冲销'],['import','导入'],['settle','结算'],['handle','处理']]
 const menus=[{id:'3000',parentId:'0',name:'基础信息管理'},{id:'4010',parentId:'3000',name:'仓库管理'},...actions.map(([action],index)=>({id:String(4011+index),parentId:'4010',name:'仓库管理/'+action,perms:'metal:warehouse:'+action})),{id:'4020',parentId:'3000',name:'库存管理'},{id:'4021',parentId:'4020',name:'库存管理/query',perms:'metal:inventory:query'},{id:'4025',parentId:'4020',name:'库存管理/adjust',perms:'metal:inventory:adjust'},{id:'4100',parentId:'3000',name:'已有中文权限'},{id:'4101',parentId:'3000',name:'自定义/future'}]
 let saved:any
 await page.context().addCookies([{name:'Admin-Token',value:'role-label-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const request=route.request(),pathname=new URL(request.url()).pathname.replace('/dev-api','')
  let result:any={code:200,data:[],rows:[],total:0}
  if(pathname==='/getInfo')result={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']}
  else if(pathname==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'role',component:'metal/role/index',name:'RoleLabelTest'}]}]
  else if(pathname==='/metal/options/factories')result.data=[{id:'7',name:'测试工厂'}]
  else if(pathname==='/metal/options/menus')result.data=menus
  else if(pathname==='/system/role'&&request.method()==='POST')saved=request.postDataJSON()
  await route.fulfill({json:result})
 })
 await page.goto('/metal/role')
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog'),tree=dialog.getByRole('region',{name:'菜单和按钮权限列表'})
 for(const [action,label]of actions){
  await expect(tree.getByText('仓库管理/'+label,{exact:true})).toBeVisible()
  await expect(tree.getByText('仓库管理/'+action,{exact:true})).toHaveCount(0)
 }
 await expect(tree.getByText('库存管理/查看',{exact:true})).toBeVisible()
 await expect(tree.getByText('库存管理/调整',{exact:true})).toBeVisible()
 for(const label of ['基础信息管理','仓库管理','已有中文权限','自定义/future'])await expect(tree.getByText(label,{exact:true})).toBeVisible()
 await dialog.getByLabel('角色名称',{exact:true}).fill('中文权限测试')
 await dialog.locator('.el-form-item').filter({hasText:'所属工厂'}).locator('.el-select__wrapper').click()
 await page.getByRole('option',{name:'测试工厂',exact:true}).click()
 const permission=tree.locator('.el-tree-node__content').filter({has:page.getByText('库存管理/查看',{exact:true})})
 await permission.locator('.el-checkbox').click()
 await expect(permission.getByRole('checkbox')).toBeChecked()
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(saved.menuIds).toEqual(['4021'])
})
