<template>
 <section class="production-progress" aria-label="生产工单进度" :aria-busy="loading">
  <header><h2>生产工单进度</h2></header>
  <div class="filters"><el-input v-model="query" maxlength="32" clearable placeholder="搜索工单号" aria-label="搜索工单号" size="small" @input="search"/><el-select v-model="status" aria-label="工单状态" placeholder="全部进行状态" size="small" @change="filterChanged"><el-option label="全部进行状态" value=""/><el-option label="未开工" value="CREATED"/><el-option label="生产中" value="IN_PROGRESS"/></el-select></div>
  <div ref="listEl" class="order-list">
   <div v-if="failed" class="progress-error" role="alert">生产进度加载失败，将自动重试</div>
   <div v-else-if="!rows.length" class="progress-empty">{{loading||!ready?'正在加载生产进度…':query||status?'暂无符合条件的工单':'暂无未开工或生产中的工单'}}</div>
   <button v-for="order in rows" :key="order.id" class="order-row" :aria-label="'查看工单 '+order.orderNo" @click="openDetail(order)">
    <span class="identity"><strong>{{order.orderNo}}</strong><small :title="productSummary(order)">{{productSummary(order)}}</small></span>
    <span class="progress"><span><span>{{summary(order)}}</span><b v-if="percent(order)!==null">{{percent(order)}}%</b></span><span v-if="percent(order)!==null" class="track"><i :style="{width:Math.min(100,percent(order)!)+'%'}"/></span></span>
    <span class="status" :class="{active:order.status==='IN_PROGRESS'}">{{order.status==='IN_PROGRESS'?'生产中':'未开工'}}<span aria-hidden="true">›</span></span>
   </button>
  </div>
  <div class="pagination"><span>共 {{total}} 单</span><div><el-button text :disabled="loading||pageNum<=1" aria-label="上一页工单" @click="changePage(pageNum-1)">‹</el-button><span>{{pageNum}} / {{pages}}</span><el-button text :disabled="loading||pageNum>=pages" aria-label="下一页工单" @click="changePage(pageNum+1)">›</el-button><label>到 <input v-model="jumpPage" type="number" min="1" :max="pages" aria-label="跳转页码" @keydown.enter="jump"/> 页</label><el-button text :disabled="loading" @click="jump">跳转</el-button></div></div>
  <el-dialog v-model="detailVisible" title="工单进度详情" width="min(760px, 94vw)" append-to-body destroy-on-close class="dashboard-progress-dialog">
   <template v-if="selected">
    <div class="detail-heading"><strong>{{selected.orderNo}}</strong><span>{{selected.factoryName}}</span><el-tag size="small">{{selected.status==='IN_PROGRESS'?'生产中':'未开工'}}</el-tag></div>
    <template v-if="product"><div class="product-heading"><strong>{{product.productName}}</strong><span>{{product.specification}}</span><span>成品 {{product.completedQuantity===null?'按重量计量':`${product.completedQuantity}/${product.plannedQuantity} 件`}}</span></div>
     <p v-if="!selected.flowVersion" class="progress-empty">暂无执行数据（历史预览工单）</p>
     <ol v-else class="process-chain" aria-label="工序进度"><li v-for="step in visibleSteps" :key="step.id"><span :title="step.processName">{{step.processName}}</span><b>{{step.measurementMode==='PIECE'?`${step.completedQuantity}/${product.plannedQuantity}`:step.measurementMode==='WEIGHT'?'按重量计量':'暂无计量配置'}}</b></li></ol>
     <div v-if="selected.flowVersion&&detailSteps.length>4" class="detail-paging"><el-button size="small" :disabled="stepPage===0" @click="stepPage--">上一组工序</el-button><span>{{stepPage+1}} / {{Math.ceil(detailSteps.length/4)}}</span><el-button size="small" :disabled="(stepPage+1)*4>=detailSteps.length" @click="stepPage++">下一组工序</el-button></div>
    </template><p v-else class="progress-empty">暂无产品明细</p>
   </template>
   <template #footer><div class="detail-paging"><el-button :disabled="productIndex===0" @click="productIndex--;stepPage=0">上一产品</el-button><span>产品 {{selected?.products.length?productIndex+1:0}} / {{selected?.products.length??0}}</span><el-button :disabled="productIndex+1>=(selected?.products.length??0)" @click="productIndex++;stepPage=0">下一产品</el-button></div></template>
  </el-dialog>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,watch,onMounted,onBeforeUnmount} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
type Step={id:string;processName:string;measurementMode:string|null;completedQuantity:string}
type Product={id:string;productName:string;specification:string|null;plannedQuantity:string;completedQuantity:string|null;steps:Step[]}
type Order={id:string;orderNo:string;factoryName:string;status:string;flowVersion:number;products:Product[]}
const props=defineProps<{factoryId:string;ready:boolean}>()
const rows=ref<Order[]>([]),total=ref(0),pageNum=ref(1),pageSize=ref(3),loading=ref(false),failed=ref(false),query=ref(''),status=ref(''),jumpPage=ref('1'),listEl=ref<HTMLElement>()
const selected=ref<Order>(),detailVisible=ref(false),productIndex=ref(0),stepPage=ref(0)
const pages=computed(()=>Math.max(1,Math.ceil(total.value/pageSize.value))),product=computed(()=>selected.value?.products[productIndex.value])
const detailSteps=computed<Step[]>(()=>product.value?[...product.value.steps,{id:'finished',processName:'成品',measurementMode:product.value.completedQuantity===null?'WEIGHT':'PIECE',completedQuantity:product.value.completedQuantity??'0'}]:[])
const visibleSteps=computed(()=>detailSteps.value.slice(stepPage.value*4,stepPage.value*4+4))
let epoch=0,disposed=false,timer:ReturnType<typeof setInterval>|undefined,searchTimer:ReturnType<typeof setTimeout>|undefined,observer:ResizeObserver|undefined
function productSummary(order:Order){return `${order.factoryName} · ${order.products[0]?.productName??'暂无产品'}${order.products.length>1?` 等${order.products.length}种产品（首项进度）`:''}`}
function summary(order:Order){const p=order.products[0];return !order.flowVersion?'暂无执行数据':!p?'暂无产品':p.completedQuantity===null?'按重量计量':`${p.completedQuantity} / ${p.plannedQuantity} 件`}
function percent(order:Order){const p=order.products[0];if(!order.flowVersion||!p||p.completedQuantity===null||!/^\d+$/.test(p.completedQuantity)||!/^\d+$/.test(p.plannedQuantity)||BigInt(p.plannedQuantity)===0n)return null;return Number(BigInt(p.completedQuantity)*100n/BigInt(p.plannedQuantity))}
function openDetail(order:Order){selected.value=order;productIndex.value=0;stepPage.value=0;detailVisible.value=true}
async function load(){
 if(!props.ready)return
 const current=++epoch,valid=()=>!disposed&&current===epoch
 loading.value=true;failed.value=false
 try{
  const response=await request({url:'/metal/dashboard/production-progress',params:{factoryId:props.factoryId||undefined,pageNum:pageNum.value,pageSize:pageSize.value,orderNo:query.value.trim()||undefined,status:status.value||undefined},quiet:true,isContextCurrent:valid})
  if(!valid())return
  if(!Array.isArray(response.data?.rows)||!Number.isSafeInteger(response.data.total)||response.data.total<0)throw Error('进度响应无效')
  rows.value=response.data.rows;total.value=response.data.total
  if(pageNum.value>pages.value){pageNum.value=pages.value;await load()}
  jumpPage.value=String(pageNum.value)
  if(detailVisible.value){const fresh=rows.value.find(x=>x.id===selected.value?.id);if(fresh){selected.value=fresh;productIndex.value=Math.min(productIndex.value,Math.max(0,fresh.products.length-1));stepPage.value=0}else detailVisible.value=false}
 }catch{if(valid()){failed.value=true;rows.value=[];total.value=0;detailVisible.value=false}}
 finally{if(valid())loading.value=false}
}
function changePage(page:number){pageNum.value=page;rows.value=[];detailVisible.value=false;void load()}
function jump(){const value=Number(jumpPage.value);if(!Number.isInteger(value)||value<1||value>pages.value){ElMessage.warning('请输入有效页码');return}changePage(value)}
function filterChanged(){clearTimeout(searchTimer);epoch++;pageNum.value=1;rows.value=[];total.value=0;detailVisible.value=false;void load()}
function search(){epoch++;rows.value=[];detailVisible.value=false;clearTimeout(searchTimer);searchTimer=setTimeout(filterChanged,300)}
watch(()=>[props.factoryId,props.ready],()=>{epoch++;clearTimeout(searchTimer);rows.value=[];total.value=0;pageNum.value=1;loading.value=false;failed.value=false;detailVisible.value=false;void load()},{immediate:true})
onMounted(()=>{observer=new ResizeObserver(()=>{if(!listEl.value)return;const size=Math.max(1,Math.min(5,Math.floor(listEl.value.clientHeight/48)));if(size!==pageSize.value){pageSize.value=size;filterChanged()}});if(listEl.value)observer.observe(listEl.value);timer=setInterval(()=>{if(!loading.value&&!document.hidden)void load()},30000)})
onBeforeUnmount(()=>{disposed=true;epoch++;clearInterval(timer);clearTimeout(searchTimer);observer?.disconnect()})
</script>
<style scoped>
.production-progress{min-width:0;min-height:0;display:flex;flex-direction:column;background:var(--el-bg-color,#fff);border:1px solid #dce3ee;border-radius:9px;padding:18px;color:var(--el-text-color-primary)}header{display:flex;align-items:center;justify-content:space-between;gap:8px}h2{margin:0;font-size:15px;font-weight:600}.filters{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:12px}.filters .el-input{width:55%;max-width:260px}.filters .el-select{width:112px}.filters :deep(.el-input__wrapper),.filters :deep(.el-select__wrapper){background:#fafcff;font-size:11px}.order-list{flex:1;min-height:0;display:flex;flex-direction:column;margin-top:8px}.order-row{flex:1;min-height:0;max-height:66px;display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr) 58px;gap:14px;align-items:center;text-align:left;padding:7px 4px;background:transparent;border:0;border-bottom:1px solid #f0f3f8;cursor:pointer;color:inherit}.order-row:last-child{border:0}.order-row:hover{background:#f7faff}.order-row:focus-visible{outline:2px solid #4f83ff;outline-offset:-2px}.identity,.progress{min-width:0;display:flex;flex-direction:column;gap:7px}.identity strong{font-size:12px;font-weight:500;font-variant-numeric:tabular-nums;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.identity small{font-size:10px;color:#909399;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.progress>span:first-child{display:flex;justify-content:space-between;gap:3px;font-size:10px;color:#909399}.progress b{font-weight:400;color:#606266}.track{height:3px;background:#eef2f8;border-radius:2px}.track i{display:block;height:100%;background:#7198f8}.status{display:flex;justify-content:space-between;align-items:center;font-size:10px;color:#909399}.status.active{color:#4f83ff}.status>span{font-size:17px;color:#b3bdca}.pagination{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #f0f3f8;padding-top:6px;margin-top:4px;gap:4px;font-size:10px;color:#909399;flex:none}.pagination>div{display:flex;align-items:center;gap:4px}.pagination .el-button{font-size:11px;height:24px;padding:3px 5px;margin:0;color:#6684b9}.pagination input{width:29px;border:0;border-bottom:1px solid #dce3ee;text-align:center;font:inherit;color:#606266;background:transparent;appearance:textfield}.pagination input::-webkit-inner-spin-button{appearance:none}.progress-error,.progress-empty{font-size:12px;flex:1;display:flex;align-items:center;justify-content:center;color:#909399}.progress-error{color:var(--el-color-danger)}.detail-heading{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.detail-heading span{font-size:12px;color:#909399}.product-heading{display:flex;flex-direction:column;gap:9px;background:#f8fbff;padding:16px;margin-top:16px;border-radius:6px}.product-heading span{font-size:12px;overflow-wrap:anywhere}.process-chain{list-style:none;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));padding:0;gap:16px;margin:24px 0}.process-chain li{min-width:0;border-top:3px solid #7198f8;padding-top:10px;display:flex;flex-direction:column;gap:8px;font-size:12px}.process-chain li span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.process-chain b{overflow-wrap:anywhere}.detail-paging{display:flex;justify-content:flex-end;align-items:center;gap:12px;font-size:12px}
@media(max-width:1100px){.production-progress{padding:12px}.order-row{gap:7px;grid-template-columns:minmax(0,1.5fr) minmax(0,1fr) 42px}.identity strong{font-size:10px}.identity small{font-size:9px}h2{font-size:13px}.filters .el-select{width:104px}.pagination>div{gap:1px}.pagination .el-button{padding:2px 4px}}
@media(max-width:650px){.production-progress{padding:8px}.filters{margin-top:4px}.filters :deep(.el-input__wrapper),.filters :deep(.el-select__wrapper){min-height:22px}.order-list{margin-top:3px}.pagination{padding-top:3px}.process-chain{gap:8px}.process-chain li{font-size:10px}}
</style>
