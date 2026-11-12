<template>
 <el-dialog v-model="visible" :title="action==='SPLIT'?'拆分生产批次':'合并生产批次'" width="1060px" append-to-body :close-on-click-modal="false" :before-close="close">
  <OperationStatus :operation="operation"/>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <el-alert title="计划与实物分别分配；重量来自实际确认，不按件数推算。预览不占用库存，提交时再次核验。" type="info" :closable="false"/>
  <div v-loading="loading" class="transform-content">
   <el-form label-position="top" :disabled="locked||loading" @input="invalidate">
    <el-form-item label="分配方式"><el-select v-model="mode" aria-label="拆合方式" @change="modeChanged"><el-option v-for="m in modes" :key="m.value" :value="m.value" :label="m.label"/></el-select></el-form-item>
    <template v-if="action==='SPLIT'&&source">
     <p>{{source.number}} · 原计划 {{source.plannedQuantity??'未分配'}}件 · {{source.stageSummary}}</p>
     <el-card v-for="(result,index) in results" :key="result.clientKey" class="result-card" shadow="never">
      <template #header><b>结果{{index+1}}</b><el-button class="remove" link type="danger" :disabled="locked||results.length<=2" @click="results.splice(index,1);invalidate()">移除结果</el-button></template>
      <div class="result-controls"><el-form-item label="计划件数"><el-input-number v-model="result.plannedQuantity" :min="1" :max="2147483647" :precision="0" :aria-label="`结果${index+1}计划件数`" :disabled="source.plannedQuantity==null" @change="invalidate"/></el-form-item><el-form-item label="承接内容"><el-select v-model="result.kind" :aria-label="`结果${index+1}承接内容`" @change="invalidate"><el-option v-for="kind in resultKinds" :key="kind.value" :value="kind.value" :label="kind.label"/></el-select></el-form-item><el-form-item v-if="result.kind==='WIP'" label="当前工序接收人员"><el-select v-model="result.processorId" :aria-label="`结果${index+1}接收人员`" @change="invalidate"><el-option v-for="person in processors" :key="person.id" :value="String(person.id)" :label="person.name||person.nickName"/></el-select></el-form-item></div>
      <BatchAllocationEditor v-if="result.kind!=='PLAN'" :rows="result.kind==='WIP'?result.wip:result.stock" :kind="result.kind==='WIP'?'WIP':'STOCK'" :label="`结果${index+1}`" :disabled="locked"/>
     </el-card>
     <el-button :disabled="locked" @click="addResult();invalidate()">增加拆分结果</el-button>
    </template>
    <template v-else-if="action==='MERGE'">
     <el-form-item label="参与合并的生产批次"><el-select v-model="selected" multiple aria-label="参与合并批次" @change="loadMergeSources"><el-option v-for="batch in candidates" :key="batch.id" :value="String(batch.id)" :label="`${batch.number} / 计划${batch.plannedQuantity??'未分配'}件`"/></el-select></el-form-item>
     <el-card v-for="(entry,index) in mergeSources" :key="entry.context.id" class="result-card" shadow="never"><template #header>{{entry.context.number}} · 原计划 {{entry.context.plannedQuantity??'未分配'}}件 · {{entry.context.stageSummary}}</template><el-form-item v-if="mode==='PARTIAL_MERGE'" label="本次转入目标计划件数"><el-input-number v-model="entry.plannedQuantityForMerge" :min="0" :max="Number(entry.context.plannedQuantity||2147483647)" :precision="0" :aria-label="`合并来源${index+1}计划件数`" @change="invalidate"/></el-form-item><BatchAllocationEditor :rows="entry.stock" kind="STOCK" :label="`合并来源${index+1}`" :disabled="locked||mode==='FULL_MERGE'"/></el-card>
    </template>
    <el-form-item label="拆合原因" required><el-input v-model="reason" type="textarea" :maxlength="500" show-word-limit aria-label="拆合原因"/></el-form-item>
   </el-form>
   <el-card v-if="preview" shadow="never" class="preview" aria-label="拆合预览">
    <template #header>分配预览 · {{preview.allowed===false?'尚不可提交':'请核对后提交'}}</template>
    <el-alert v-for="(b,index) in preview.blockingReasons||[]" :key="index" :title="typeof b==='string'?b:b.message" type="error" :closable="false"/>
    <p v-if="action==='MERGE'&&preview.plannedQuantity!=null">合并目标计划：{{preview.plannedQuantity}}件</p>
    <el-table v-if="previewRows.length" :data="previewRows" border><el-table-column prop="displayLabel" label="结果 / 来源"/><el-table-column prop="plannedQuantity" label="承接计划件数"/><el-table-column prop="quantity" label="实际件数"/><el-table-column prop="netWeight" label="实际重量（g）"/></el-table>
    <p v-if="preview.remainingResults?.length">剩余部分将使用新的生产批次身份，原来源整体退出：</p>
    <el-table v-if="preview.remainingResults?.length" :data="preview.remainingResults" border><el-table-column prop="sourceBatchId" label="剩余来源"/><el-table-column prop="plannedQuantity" label="剩余计划件数"/><el-table-column prop="quantity" label="剩余实际件数"/><el-table-column prop="netWeight" label="剩余重量（g）"/></el-table>
    <p>料权和材料来源按上方逐项分配；提交后可在原操作结果核实，拆合本身不增加产量或欠料。</p>
   </el-card>
  </div>
  <template #footer><el-button :disabled="locked" @click="close()">取消</el-button><el-button :loading="previewing" :disabled="locked||loading||previewing||!!contextError" @click="doPreview">预览分配</el-button><el-button type="primary" :loading="operation.busy.value" :disabled="locked||loading||previewing||!preview||preview.allowed===false||!previewCurrent" @click="submit">确认{{action==='SPLIT'?'拆分':'合并'}}</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import request from '@/utils/request'
import {useOperation,message,type Row} from '../storage-live/client'
import OperationStatus from '../storage-live/OperationStatus.vue'
import BatchAllocationEditor from './BatchAllocationEditor.vue'
import {allocationRows,stockAllocations,wipAllocations} from './batch-transform'
import {weightText} from '../storage-orders/stock-groups'
const props=defineProps<{factoryId:string;orderId:string;identity:string;people:Row[]}>()
const emit=defineEmits<{changed:[result:any];locked:[value:boolean]}>()
const base='/metal/production-batches',visible=ref(false),action=ref<'SPLIT'|'MERGE'>('SPLIT'),mode=ref('PLAN_SPLIT'),loading=ref(false),previewing=ref(false),error=ref(''),contextError=ref(''),source=ref<any>(),results=ref<any[]>([]),reason=ref(''),preview=ref<any>(),previewBody=ref(''),selected=ref<string[]>([]),candidates=ref<Row[]>([]),mergeSources=ref<any[]>([])
const operation=useOperation(base,()=>props.identity,async result=>{visible.value=false;emit('changed',result)})
const locked=computed(()=>operation.busy.value||!!operation.pending.value)
const processors=computed(()=>props.people.filter(p=>p.canReceive===true&&(p.status==null||String(p.status)==='0')))
const modes=computed(()=>action.value==='SPLIT'?[{value:'PLAN_SPLIT',label:'未开工计划拆分'},{value:'STOCK_SPLIT',label:'已完成库存拆分'},{value:'PARTIAL_COMPLETION_WITH_WIP',label:'已完成部分与在制承接'}]:[{value:'FULL_MERGE',label:'全部合并'},{value:'PARTIAL_MERGE',label:'部分合并并保留剩余'}])
const resultKinds=computed(()=>mode.value==='PLAN_SPLIT'?[{value:'PLAN',label:'生产计划'}]:mode.value==='STOCK_SPLIT'?[{value:'COMPLETED_STOCK',label:'已完成库存'}]:[{value:'COMPLETED_STOCK',label:'已完成库存'},{value:'WIP',label:'当前工序在制'}])
const previewRows=computed(()=>(preview.value?.results||preview.value?.sourceContributions||preview.value?.sources||[]).map((row:any,index:number)=>{
 const parts=[...(row.stockAllocations||[]),...(row.wipAllocations||[])],cents=parts.reduce((sum:bigint,p:any)=>{const [w,d='']=String(p.netWeight||'0').split('.');return sum+BigInt(w)*100n+BigInt(d.padEnd(2,'0'))},0n)
 const context=mergeSources.value.find(s=>String(s.context.id)===String(row.batchId))?.context
 return {...row,displayLabel:row.number||context?.number||`结果${index+1} · ${row.kind==='WIP'?'当前工序在制':row.kind==='PLAN'?'生产计划':'已完成库存'}`,plannedQuantity:row.plannedQuantity??row.plannedQuantityForMerge??(mode.value==='FULL_MERGE'?context?.plannedQuantity:null),quantity:row.quantity??(parts.length?parts.reduce((sum:number,p:any)=>sum+Number(p.quantity||0),0):null),netWeight:row.netWeight??(parts.length?weightText(cents):null)}
}))
const previewCurrent=computed(()=>{try{return previewBody.value===JSON.stringify(body())}catch{return false}})
let ticket=0,key=0
function invalidate(){preview.value=undefined;previewBody.value=''}
function addResult(kind?:string){results.value.push({clientKey:`part${++key}`,plannedQuantity:undefined,kind:kind||resultKinds.value[0].value,processorId:'',stock:allocationRows(source.value||{},'STOCK'),wip:allocationRows(source.value||{},'WIP')})}
function modeChanged(){invalidate();if(action.value==='SPLIT'){results.value=[];addResult();addResult(mode.value==='PARTIAL_COMPLETION_WITH_WIP'?'WIP':undefined)}else{for(const e of mergeSources.value)e.stock=allocationRows(e.context,'STOCK',mode.value==='FULL_MERGE')}}
async function context(id:string){const r:any=await request({url:`${base}/${id}/context`,params:{factoryId:props.factoryId,intent:action.value},quiet:true});return r.data}
async function mergeCandidates(orderItemId:any){const all:Row[]=[];for(let pageNum=1;;pageNum++){const r:any=await request({url:base+'/list',params:{factoryId:props.factoryId,orderId:props.orderId,orderItemId,lifecycle:'ACTIVE',pageNum,pageSize:64},quiet:true});all.push(...(r.rows||[]));if((r.rows||[]).length<64||all.length>=Number(r.total||0))break}return all}
function checkContext(c:any){const allowed=c.actions?.find((a:any)=>a.action===action.value);if(allowed?.allowed===false)throw Error(allowed.message||'当前批次不允许此操作');if(c.pendingDocuments?.length||c.activeReservations?.length)throw Error('存在未处理单据或有效预占，请处理后刷新')}
async function open(kind:'SPLIT'|'MERGE',row:Row,available:Row[]){
 if(locked.value)return
 const current=props.identity,n=++ticket;operation.clear();action.value=kind;visible.value=true;loading.value=true;error.value='';contextError.value='';reason.value='';source.value=undefined;results.value=[];mergeSources.value=[];invalidate();selected.value=[String(row.id)];candidates.value=available.filter(b=>b.lifecycle==='ACTIVE'&&String(b.orderItemId)===String(row.orderItemId))
 try{const loaded=await context(String(row.id));if(n!==ticket||current!==props.identity)return;source.value=loaded;checkContext(source.value);mode.value=kind==='MERGE'?'FULL_MERGE':source.value.wipSources?.length?'PARTIAL_COMPLETION_WITH_WIP':source.value.stockSlices?.some((s:any)=>Number(s.netWeight??s.weight)>0)?'STOCK_SPLIT':'PLAN_SPLIT';if(kind==='SPLIT')modeChanged();else{mergeSources.value=[{context:source.value,stock:allocationRows(source.value,'STOCK',true),plannedQuantityForMerge:source.value.plannedQuantity}];const available=await mergeCandidates(row.orderItemId);if(n===ticket&&current===props.identity)candidates.value=available}}
 catch(e){if(n===ticket&&current===props.identity)contextError.value=error.value=message(e)}finally{if(n===ticket&&current===props.identity)loading.value=false}
}
async function loadMergeSources(){const n=++ticket,current=props.identity;loading.value=true;contextError.value='';error.value='';invalidate();try{const data=await Promise.all(selected.value.map(id=>context(id)));if(n!==ticket||current!==props.identity)return;for(const c of data)checkContext(c);mergeSources.value=data.map(c=>({context:c,stock:allocationRows(c,'STOCK',mode.value==='FULL_MERGE'),plannedQuantityForMerge:c.plannedQuantity}))}catch(e){if(n===ticket&&current===props.identity)contextError.value=error.value=message(e)}finally{if(n===ticket&&current===props.identity)loading.value=false}}
function body():any {
 if(!reason.value.trim()||reason.value.length>500)throw Error('请填写500字以内的拆合原因')
 const common={factoryId:props.factoryId,mode:mode.value,reason:reason.value.trim()}
 if(action.value==='SPLIT'){
  if(results.value.length<2)throw Error('拆分至少需要两个结果')
  const execution=(source.value.executions||[]).find((e:any)=>e.status==='OPEN')
  return {...common,sourceBatchId:String(source.value.id),sourceBatchVersion:source.value.version,...(execution?{sourceExecutionId:String(execution.executionId??execution.id),sourceExecutionVersion:execution.version}:{}),results:results.value.map(r=>{
   if(source.value.plannedQuantity!=null&&(!Number.isInteger(r.plannedQuantity)||r.plannedQuantity<1))throw Error('请填写各结果的正整数计划件数')
   if(r.kind==='WIP'&&!r.processorId)throw Error('请选择当前工序在制的接收人员')
   return {clientKey:r.clientKey,plannedQuantity:source.value.plannedQuantity==null?null:r.plannedQuantity,kind:r.kind,...(r.kind==='WIP'?{processorId:r.processorId,wipAllocations:wipAllocations(r.wip)}:r.kind==='COMPLETED_STOCK'?{stockAllocations:stockAllocations(r.stock)}:{})}
  })}
 }
 if(mergeSources.value.length<2)throw Error('请选择至少两个参与合并的批次')
 return {...common,sources:mergeSources.value.map(s=>({batchId:String(s.context.id),version:s.context.version,stockAllocations:stockAllocations(s.stock),...(mode.value==='PARTIAL_MERGE'?{plannedQuantityForMerge:s.plannedQuantityForMerge}:{})}))}
}
async function doPreview(){if(locked.value||loading.value)return;error.value='';invalidate();let payload:any;try{payload=body()}catch(e){error.value=message(e);return}const n=ticket,current=props.identity;previewing.value=true;try{const r:any=await request({url:`${base}/${action.value==='SPLIT'?'splits':'merges'}/preview`,method:'post',data:payload,headers:{repeatSubmit:false},quiet:true});if(n!==ticket||current!==props.identity)return;preview.value=r.data;previewBody.value=JSON.stringify(payload)}catch(e){if(n===ticket&&current===props.identity)error.value=message(e)}finally{if(n===ticket&&current===props.identity)previewing.value=false}}
async function submit(){if(!previewCurrent.value||!preview.value||locked.value||preview.value.allowed===false)return;await operation.send(`${base}/${action.value==='SPLIT'?'splits':'merges'}`,JSON.parse(previewBody.value))}
function close(done?:()=>void){if(locked.value)return;visible.value=false;ticket++;done?.()}
watch(locked,v=>emit('locked',v))
watch(()=>props.identity,()=>{ticket++;visible.value=false;loading.value=false;previewing.value=false})
defineExpose({open,requestClose:()=>!visible.value&&!locked.value,isLocked:()=>locked.value})
</script>
<style scoped>.transform-content{margin-top:16px}.result-card{margin:12px 0}.result-controls{display:flex;gap:22px;flex-wrap:wrap}.remove{float:right}.preview{margin-top:20px}.el-select{min-width:220px;width:100%}</style>
