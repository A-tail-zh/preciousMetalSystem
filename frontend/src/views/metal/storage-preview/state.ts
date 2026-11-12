import { computed, reactive, watch } from 'vue'
import router from '@/router'
import useUserStore from '@/store/modules/user'
export type Row = { id: string; factoryId: string; [key: string]: any }
export const preview = reactive({ factoryId: '', identity: 'keeper' as 'keeper' | 'processor', factories: [] as Row[], warehouses: [] as Row[], customers: [] as Row[], people: [] as Row[], materials: [] as Row[], products: [] as Row[], productionOrders: [] as Row[], inventory: [] as Row[], orders: [] as Row[] })
let signature = '', sequence = 0
const pad = (n: number) => String(n).padStart(2, '0')
export function now() { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` }
export function today() { return now().slice(0,10) }
export function uid(prefix = 'preview') { return `${prefix}-${Date.now()}-${++sequence}` }
export const isStoragePath = (path: string) => /^\/metal\/storage-(warehouses|inventory|orders)\/?$/.test(path)
function reset() { signature = ''; sequence = 0; preview.factoryId = ''; preview.identity = 'keeper'; for(const key of ['factories','warehouses','customers','people','materials','products','productionOrders','inventory','orders'] as const) preview[key] = [] }
router.afterEach((to, from, failure) => { if(!failure && isStoragePath(from.path) && !isStoragePath(to.path)) reset() })
function seed(factories: Row[]) {
  preview.factories = factories
  for(const factory of factories) {
    const f = factory.id, id = (key: string) => `${f}-${key}`
    preview.warehouses.push(...['原料库','成品库'].map((name,i) => ({id:id(`warehouse-${i}`),factoryId:f,name,sort:i,status:'0',updatedAt:now()})))
    preview.customers.push({id:id('customer'),factoryId:f,name:'演示客户',companyName:'演示精密材料公司',address:'演示地址一号',contactName:'演示联系人',contactPhone:'13800000000',department:'采购部'})
    preview.people.push(...(['keeper','processor'] as const).map((role,i)=>({id:id(role),factoryId:f,name:i?'演示加工人员':'演示库管',role,status:'0'})))
    preview.materials.push(...['粉料','块料','旧料'].map((category,i)=>({id:id(`material-${i}`),factoryId:f,code:`M00${i+1}`,name:`钨${category}`,materialType:`钨${category}`,materialCategory:category,specification:i?'标准块':'200目',metalType:'钨'})))
    for(const [index,name] of ['杆','环'].entries()) {
      const processes=[{id:id(`process-${index}-0`),name:'压制',measurement:index?'WEIGHT':'PIECE'},{id:id(`process-${index}-1`),name:'烧结',measurement:index?'WEIGHT':'PIECE'}]
      preview.products.push({id:id(`product-${index}`),factoryId:f,code:`P00${index+1}`,name:`钨${name}`,productType:name,metalType:'钨',specification:index?'Φ20×5':'Φ10×100',processes})
      preview.productionOrders.push({id:id(`production-${index}`),factoryId:f,number:`PREVIEW-GD-${index+1}`,completed:false,products:[{productId:id(`product-${index}`),quantity:10,processes:processes.map(p=>({...p,completed:false}))}]})
    }
    for(const category of ['RAW','PRODUCT','SEMI']) {
      for(let i=0;i<3;i++) {
        const source=category==='RAW'?preview.materials.find(r=>r.id===id(`material-${i}`))!:preview.products.find(r=>r.id===id(`product-${i%2}`))!
        const own=i===2
        preview.inventory.push({id:id(`${category}-${i}`),factoryId:f,category,warehouseId:id(`warehouse-${category==='RAW'?0:1}`),sourceId:source.id,code:source.code,name:source.name,materialType:source.materialType||'',materialCategory:source.materialCategory||'',productType:source.productType||'',metalType:source.metalType,specification:source.specification,customerId:own?'':id('customer'),customerName:own?'工厂': '演示客户',companyName:own?'工厂自有':'演示精密材料公司',processName:category==='SEMI'?'压制':'',netWeight:(100+i*25).toFixed(2),quantity:10+i,measurement:category==='RAW'||i%2?'WEIGHT':'PIECE',updatedAt:now()})
      }
    }
    const stock=preview.inventory.find(r=>r.factoryId===f&&r.category==='RAW')!
    const line={id:id('seed-line'),factoryId:f,warehouseId:stock.warehouseId,sourceId:stock.sourceId,inventoryId:stock.id,code:stock.code,name:stock.name,specification:stock.specification,category:'RAW',grossWeight:'55.00',impurity:'5.00',netWeight:'50.00',quantity:5,purity:'99.90',remark:'演示领料',customerId:id('customer'),customerName:'演示客户',companyName:'演示精密材料公司'}
    preview.orders.push({id:id('out-seed'),factoryId:f,kind:'OUT',type:'PRODUCTION_OUT',status:'COMPLETED',number:`PREVIEW-OUT-${today().replaceAll('-','')}-000001`,date:today(),warehouseIds:[stock.warehouseId],lines:[line],keeperId:id('keeper'),processorId:id('processor'),productionOrderId:id('production-0'),productId:id('product-0'),processId:id('process-0-0'),batchNumber:`PREVIEW-BATCH-${f}-1`,createdAt:now(),updatedAt:now()})
  }
}
export function useStoragePreview() {
  const user=useUserStore(), isAdmin=computed(()=>String(user.id)==='1')
  watch(()=>[user.sessionReady,user.id,user.factoryId,user.sessionEpoch,user.token,user.permissions.join('|')],()=>{
    const key=JSON.stringify([user.id,user.factoryId,user.sessionEpoch,user.token,user.permissions])
    if(!user.sessionReady){reset();return}
    if(signature===key)return
    reset();signature=key
    const factories=isAdmin.value?[{id:'demo-a',factoryId:'demo-a',name:'演示工厂一'},{id:'demo-b',factoryId:'demo-b',name:'演示工厂二'}]:user.factoryId?[{id:String(user.factoryId),factoryId:String(user.factoryId),name:'所属工厂（演示）'}]:[]
    seed(factories);if(!isAdmin.value)preview.factoryId=factories[0]?.id||''
  },{immediate:true,flush:'sync'})
  const can=(permission:string)=>user.sessionReady&&user.permissions.some(p=>p==='*:*:*'||p===permission)
  function changeFactory(id:string){if(preview.factories.some(f=>f.id===id)&&(isAdmin.value||id===String(user.factoryId)))preview.factoryId=id}
  const canAct=(role:'keeper'|'processor')=>can(role==='keeper'?'metal:warehouse-order:accept':'metal:warehouse-order:receive')
  const canReview=(row:Row)=>row.factoryId===preview.factoryId&&can('metal:warehouse-order:list')&&((row.status==='PENDING_RECEIPT'&&canAct('processor'))||(row.status==='PENDING_KEEPER'&&canAct('keeper')))
  return {state:preview,can,isAdmin,changeFactory,canAct,canReview}
}
