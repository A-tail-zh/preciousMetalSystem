type NavigationUser = {id: string | number; sessionReady: boolean; permissions: string[]}
type NavigationItem = {title: string; to: string; permission: string}
const menuGroups=[
 {key:'base',title:'基础信息管理',items:[{title:'工厂管理',to:'/metal/factory',permission:'metal:factory:list'},{title:'产品管理',to:'/metal/product',permission:'metal:product:list'},{title:'产线管理',to:'/metal/production-line',permission:'metal:production-line:list'},{title:'物料管理',to:'/metal/material',permission:'metal:material:list'},{title:'工序管理',to:'/metal/process',permission:'metal:process:list'},{title:'客户管理',to:'/metal/customer',permission:'metal:customer:list'}]},
 {key:'quote',title:'报价管理',items:[{title:'报价规则管理',to:'/metal/quote-rule',permission:'metal:quote-rule:list'},{title:'自动报价单',to:'/metal/quotation',permission:'metal:quotation:list'}]},
 {key:'orders',title:'工单管理',items:[{title:'生产工单管理',to:'/metal/production-order',permission:'metal:work-order:list'}]},
 {key:'warehouse',title:'仓储管理',items:[{title:'仓储管理',to:'/metal/storage-inventory',permission:'metal:inventory:list'},{title:'仓储工单管理',to:'/metal/storage-orders',permission:'metal:warehouse-order:list'},{title:'仓库管理',to:'/metal/storage-warehouses',permission:'metal:warehouse:list'}]},
 {key:'user',title:'用户管理',items:[{title:'人员管理',to:'/system/user',permission:'system:user:list'},{title:'角色管理',to:'/system/role',permission:'system:role:list'}]},
 {key:'logs',title:'系统管理',items:[{title:'操作日志',to:'/audit/operations',permission:'monitor:operlog:list'},{title:'登录日志',to:'/audit/logins',permission:'monitor:logininfor:list'},{title:'系统运行日志',to:'/audit/runtime',permission:'monitor:runtime-log:list'},{title:'日志配置',to:'/audit/config',permission:'monitor:log-config:query'}]},
 {key:'alert',title:'预警管理',items:[{title:'预警记录',to:'/metal/warning-record',permission:'metal:warning-record:list'}]}
]
export function businessNavigation(user: NavigationUser) {
 const can=(permission:string)=>user.sessionReady&&user.permissions.some(p=>p==='*:*:*'||p===permission)
 return menuGroups.map(group=>({...group,
  items:group.items.filter(item=>can(item.permission)&&(group.key!=='logs'||item.title==='操作日志'||String(user.id)==='1')),
  entry:undefined as NavigationItem|undefined
 })).filter(group=>group.items.length||group.entry)
}
