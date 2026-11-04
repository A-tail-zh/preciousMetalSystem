<template>
<section class="quote-page">
 <el-form ref="quoteForm" :model="{items,factoryId}" label-width="160px" label-position="left" scroll-to-error :disabled="calculating||!canSave">
 <el-form-item class="quote-factory" label="所属工厂" prop="factoryId" :rules="[requiredRule('所属工厂',true,true)]"><el-select v-if="permission.isAdmin.value" v-model="factoryId" :disabled="calculating||!!pendingPayload" aria-label="所属工厂" placeholder="请选择所属工厂" filterable><el-option v-for="factory in factories" :key="factory.id" :label="factory.name" :value="factory.id"/></el-select><el-input v-else :model-value="factories.find(f=>f.id===factoryId)?.name??''" aria-label="所属工厂" readonly/></el-form-item>
 <div v-for="(item,index) in items" :key="item.key" class="goods-item">
  <el-form-item label="加工类型" :prop="`items.${index}.processingType`" :rules="[requiredRule('加工类型',true,true)]"><el-select aria-label="加工类型" v-model="item.processingType" placeholder="请选择加工类型" @change="processingChanged(item)"><el-option v-for="type in processingTypes" :key="type.value" :value="type.value" :label="type.label"/></el-select></el-form-item>
  <el-form-item label="金属类型" :prop="`items.${index}.metalTypeId`" :rules="[requiredRule('金属类型',true,true)]"><el-select aria-label="金属类型" v-model="item.metalTypeId" filterable placeholder="请选择金属类型"><el-option v-for="metal in metals" :key="metal.id" :value="metal.id" :label="metal.name"/></el-select></el-form-item>
  <el-form-item label="产品类型" :prop="`items.${index}.productType`" :rules="[requiredRule('产品类型',true,true)]"><el-select aria-label="产品类型" v-model="item.productType" placeholder="请选择产品类型" @change="categoryChanged(item)"><el-option v-for="type in productTypes" :key="type" :value="type"/></el-select></el-form-item>
  <el-form-item label="商品数量" :prop="`items.${index}.quantity`" :rules="integerRules('商品数量')"><el-input aria-label="商品数量" v-model="item.quantity" inputmode="numeric"/></el-form-item>
  <el-form-item v-for="field in weightFields(item)" :key="field.key" :label="field.label+'（g）'" :prop="`items.${index}.${field.key}`" :rules="[fieldRule(v=>decimalError(v,field.label))]"><BusinessDecimalInput :aria-label="field.label+'（g）'" v-model="item[field.key]" inputmode="decimal"/></el-form-item>
  <el-form-item v-for="d in dimensions(item)" :key="d.key" :label="d.label+'（'+(d.unit??'cm')+'）'" :prop="`items.${index}.dimensions.${d.key}`" :rules="[fieldRule(v=>decimalError(v,d.label,{positive:!d.allowZero}))]"><BusinessDecimalInput :aria-label="d.label+'（'+(d.unit??'cm')+'）'" v-model="item.dimensions[d.key]" inputmode="decimal"/></el-form-item>
  <el-button v-if="items.length>1" link type="danger" @click="items.splice(index,1)">移除商品</el-button>
 </div>
 <div class="calculate-row"><el-button type="primary" @click="items.push(blank())">添加商品</el-button><el-button type="primary" :loading="calculating" :disabled="loading||loadError||factoryLoading||!factoryId||!canSave" @click="calculate(false)">开始计算</el-button></div>
 </el-form>
 <p v-if="loadError" role="alert">{{saved?'规则刷新失败，当前规则状态未知；保留上次已保存报价，正在自动重试。':'金属及规则加载失败，正在自动重试。'}}</p>
 <p v-if="factoryError" role="alert">工厂加载失败，正在自动重试。</p>
 <QuotationDetail v-if="saved" :quote="saved" class="quote-results" @download="downloadQuote"/>
 <section class="history"><h3>历史报价</h3><div class="history-query"><el-input v-model="query.quoteNo" aria-label="历史报价单号" placeholder="报价单号" maxlength="32"/><el-select v-if="permission.isAdmin.value" v-model="query.factoryId" aria-label="历史工厂" clearable placeholder="全部工厂"><el-option v-for="factory in factories" :key="factory.id" :label="factory.name" :value="factory.id"/></el-select><el-button class="list-action list-action--primary" :disabled="historyLoading" @click="query.pageNum=1;loadHistoryList()">查询</el-button></div>
  <el-table v-loading="historyLoading" :data="historyRows" border><el-table-column prop="quoteNo" label="报价单号" min-width="190"/><el-table-column prop="factoryName" label="所属工厂" min-width="140"/><el-table-column prop="categories" label="商品类别" min-width="160"/><el-table-column prop="totalPrice" label="金额（元）" min-width="140" :formatter="decimalColumn"/><el-table-column prop="createTime" label="保存时间" min-width="170"/><el-table-column label="操作" min-width="140"><template #default="{row}"><el-button class="list-action list-action--primary list-action--row" link type="primary" @click="viewHistory(row.id)">查看</el-button><el-button class="list-action list-action--download list-action--row" link type="primary" @click="downloadQuote(row)">下载</el-button></template></el-table-column></el-table>
  <p v-if="historyError" role="alert">历史报价加载失败 <el-button link @click="loadHistoryList">重试</el-button></p>
  <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="historyTotal" layout="total, sizes, prev, pager, next" @size-change="query.pageNum=1;loadHistoryList()" @current-change="loadHistoryList"/>
 </section>
 <el-dialog v-model="historyVisible" title="历史报价详情" width="min(1200px,96vw)" destroy-on-close @closed="history=undefined;detailEpoch++"><QuotationDetail v-if="history" :quote="history" @download="downloadQuote"/></el-dialog>
</section>
</template>
<script setup lang="ts">
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import QuotationDetail from './QuotationDetail.vue'
import {ref,reactive,computed,watch,onMounted,onBeforeUnmount} from 'vue'
import type {FormInstance} from 'element-plus'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
import {requiredRule,integerRules,fieldRule,decimalError,applyFieldErrors} from '@/utils/input-validation'
import {productFields,productTypes} from '../master-config'
import {decimalColumn,roundDecimal} from '@/utils/business-decimal'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {getToken} from '@/utils/auth'
import {createUUID} from '@/utils/uuid'
const user=useUserStore(),permission=useActionPermission(),canSave=computed(()=>permission.has('metal:quotation:add'))
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const sessionCurrent=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()
let key=0,epoch=0,loadEpoch=0,listEpoch=0,detailEpoch=0,sessionEpoch=0,disposed=false,ruleSnapshot='',armed=false,refreshPending=false,retryPending=false
let refreshTimer:ReturnType<typeof setInterval>|undefined,pendingPayload:Record<string,any>|undefined
const processingTypes=[{value:'MATERIAL_CUSTOM',label:'来料定制'},{value:'DIRECT_CUSTOM',label:'直接定制'},{value:'SHORTAGE_CUSTOM',label:'欠料定制'}]
const materialFields=[{key:'powderWeight',label:'来料粉料重量'},{key:'nonPowderWeight',label:'来料非粉料重量'}] as const
const lossField={key:'lossWeight',label:'理论损失重量'} as const
const blank=()=>({key:++key,processingType:'',metalTypeId:'',productType:'',quantity:'',powderWeight:'',nonPowderWeight:'',lossWeight:'',dimensions:{} as Record<string,string>})
const items=ref([blank()]),metals=ref<{id:string;name:string}[]>([]),factories=ref<{id:string;name:string}[]>([]),factoryId=ref(''),quoteForm=ref<FormInstance>(),loading=ref(false),loadError=ref(false),factoryLoading=ref(false),calculating=ref(false),saved=ref<Record<string,any>>()
const historyRows=ref<Record<string,any>[]>([]),historyTotal=ref(0),historyLoading=ref(false),historyError=ref(false),history=ref<Record<string,any>>(),historyVisible=ref(false)
const query=reactive({quoteNo:'',factoryId:'',pageNum:1,pageSize:16})
const factoryError=ref(false)
function weightFields(item:ReturnType<typeof blank>){return item.processingType==='SHORTAGE_CUSTOM'?[lossField]:item.processingType?[...materialFields,lossField]:[]}
function processingChanged(item:ReturnType<typeof blank>){if(item.processingType==='SHORTAGE_CUSTOM'){item.powderWeight='';item.nonPowderWeight=''}}
function dimensions(item:{productType:string}){return productFields(item)}
function categoryChanged(item:ReturnType<typeof blank>){item.dimensions=Object.fromEntries(dimensions(item).map(d=>[d.key,'']))}
function invalidate(){epoch++;saved.value=undefined;pendingPayload=undefined;armed=false;refreshPending=false;retryPending=false}
watch([items,factoryId],invalidate,{deep:true,flush:'sync'})
async function load(){
 if(loading.value||!sessionCurrent()||!factoryId.value)return
 const round=++loadEpoch,session=sessionEpoch,account=identity(),factory=factoryId.value,current=()=>sessionCurrent()&&account===identity()&&factory===factoryId.value&&round===loadEpoch&&session===sessionEpoch;loading.value=true
 try{
  const r:any=await request({url:'/metal/quotation/context',params:{factoryId:factory},isContextCurrent:current});if(!current())return
  const snapshot=JSON.stringify([r.data.metalTypes,r.data.rules,r.data.volumeRule])
  if(ruleSnapshot&&snapshot!==ruleSnapshot&&(armed||calculating.value||pendingPayload))refreshPending=true
  ruleSnapshot=snapshot;metals.value=r.data.metalTypes;loadError.value=false
 }catch{if(current())loadError.value=true}finally{if(current())loading.value=false}
 if(current()&&!loadError.value&&!calculating.value&&(refreshPending||retryPending))void calculate(true)
}
function refreshRules(){if(document.visibilityState!=='hidden'&&permission.has('metal:quotation:list')){void load();if(factoryError.value)void loadFactories()}}
async function loadFactories(){if(factoryLoading.value||!sessionCurrent())return;const session=sessionEpoch,account=identity(),current=()=>sessionCurrent()&&account===identity()&&session===sessionEpoch;factoryLoading.value=true;try{const r:any=await request({url:'/metal/quotation/factories',isContextCurrent:current});if(current()){factoryError.value=false;factories.value=r.data;if(!permission.isAdmin.value&&!factoryId.value&&r.data.length===1)factoryId.value=r.data[0].id}}catch{if(current())factoryError.value=true}finally{if(current())factoryLoading.value=false}}
async function calculate(automatic=false){
 if(!sessionCurrent()||calculating.value||loading.value||loadError.value||!canSave.value||!factoryId.value||automatic&&!armed&&!pendingPayload)return
 const session=sessionEpoch,draft=epoch,account=identity()
 if(!await quoteForm.value?.validate().catch(()=>false)||!sessionCurrent()||account!==identity()||session!==sessionEpoch||draft!==epoch)return
 const current=()=>sessionCurrent()&&account===identity()&&session===sessionEpoch&&draft===epoch&&canSave.value
 const retrying=!!pendingPayload
 if(!pendingPayload)pendingPayload={requestId:createUUID(),factoryId:factoryId.value,items:items.value.map(item=>({processingType:item.processingType,...Object.fromEntries(weightFields(item).map(field=>[field.key,roundDecimal(item[field.key])])),metalTypeId:item.metalTypeId,productType:item.productType,quantity:Number(item.quantity),dimensions:Object.fromEntries(Object.entries(item.dimensions).map(([k,v])=>[k,roundDecimal(v)]))}))}
 const payload=pendingPayload;refreshPending=refreshPending&&retrying;retryPending=false;calculating.value=true;saved.value=undefined
 try{
  const r:any=await request({url:'/metal/quotation/save',method:'post',headers:{repeatSubmit:false},isContextCurrent:current,data:payload})
  if(!current())return
  saved.value=r.data;pendingPayload=undefined;armed=true;void loadHistoryList()
 }catch(error:any){if(current()){retryPending=!!error?.isAxiosError&&(!error.response||error.response.status>=500);if(!retryPending)refreshPending=false;applyFieldErrors(quoteForm.value,error)}}finally{if(current())calculating.value=false}
 if(current()&&refreshPending&&!retryPending)void calculate(true)
}
async function loadHistoryList(){
 if(!permission.has('metal:quotation:list'))return
 const round=++listEpoch,session=sessionEpoch,account=identity(),current=()=>sessionCurrent()&&account===identity()&&round===listEpoch&&session===sessionEpoch;historyLoading.value=true
 try{const r:any=await request({url:'/metal/quotation/orders',params:{...query,factoryId:query.factoryId||undefined},isContextCurrent:current});if(current()){historyRows.value=r.rows;historyTotal.value=r.total;historyError.value=false}}catch{if(current())historyError.value=true}finally{if(current())historyLoading.value=false}
}
async function viewHistory(id:string){const round=++detailEpoch,session=sessionEpoch,account=identity(),current=()=>sessionCurrent()&&account===identity()&&round===detailEpoch&&session===sessionEpoch;history.value=undefined;try{const r:any=await request({url:'/metal/quotation/orders/'+id,isContextCurrent:current});if(current()){history.value=r.data;historyVisible.value=true}}catch{}}
async function downloadQuote(quote:Record<string,any>){const session=sessionEpoch,account=identity(),current=()=>sessionCurrent()&&account===identity()&&session===sessionEpoch;try{const blob:Blob=await request({url:'/metal/quotation/orders/'+quote.id+'/download',responseType:'blob',isContextCurrent:current});if(!current())return;if(blob.type.includes('json')){ElMessage.error('报价单下载失败，请重试');return}const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=quote.quoteNo+'.xlsx';a.click();URL.revokeObjectURL(url)}catch{}}
permission.onChange(()=>{sessionEpoch++;invalidate();loadEpoch++;listEpoch++;detailEpoch++;ruleSnapshot='';loading.value=false;loadError.value=false;factoryLoading.value=false;factoryError.value=false;calculating.value=false;history.value=undefined;historyVisible.value=false;historyRows.value=[];historyTotal.value=0;metals.value=[];factories.value=[];factoryId.value='';items.value=[blank()];if(permission.has('metal:quotation:list'))start()})
function start(){void loadFactories().then(load).catch(()=>{});void loadHistoryList()}
watch(factoryId,()=>{++loadEpoch;loading.value=false;ruleSnapshot='';metals.value=[];for(const item of items.value)item.metalTypeId='';void load()})
onMounted(()=>{start();refreshTimer=setInterval(refreshRules,15000);window.addEventListener('focus',refreshRules);document.addEventListener('visibilitychange',refreshRules)})
onBeforeUnmount(()=>{disposed=true;sessionEpoch++;invalidate();loadEpoch++;listEpoch++;detailEpoch++;clearInterval(refreshTimer);window.removeEventListener('focus',refreshRules);document.removeEventListener('visibilitychange',refreshRules)})
</script>
<style scoped>
.quote-page{padding:16px;background:var(--business-canvas);box-sizing:border-box;min-height:calc(var(--design-height,100vh) - 56px)}.goods-item{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-items:start;gap:0 24px;background:#fff;padding:24px;margin-top:16px;border:1px solid var(--business-border);border-radius:8px;max-width:1100px}.goods-item :deep(.el-input),.goods-item :deep(.el-select){width:100%}.goods-item .el-form-item{min-width:0}.notice{grid-column:1/-1;color:#909399}.calculate-row{display:flex;gap:12px;margin-top:16px;flex-wrap:wrap}.result-table{margin-top:24px}.history{margin-top:32px}.history-query{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px}.history-query .el-input,.history-query .el-select{width:240px;max-width:100%}.quote-results{margin-top:24px}.quote-factory{max-width:650px;margin-top:16px}.quote-factory .el-select{width:100%}.history .el-pagination{margin-top:16px;max-width:100%;overflow-x:auto}@media(max-width:700px){.goods-item{grid-template-columns:minmax(0,1fr);padding:16px}.quote-page{padding:12px}}
</style>
