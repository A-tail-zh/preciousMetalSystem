<template>
<el-dialog v-model="visible" :title="`${form.id?'编辑':'添加'}${kindNames[kind]}`" width="1120px" class="storage-dialog" :close-on-click-modal="false" :before-close="close" destroy-on-close>
 <OperationStatus :operation="operation"/>
 <el-alert v-if="reservations.error.value" type="error" :closable="false"><template #title>{{reservations.error.value}} <el-button v-if="reservations.pending.value" link :loading="reservations.busy.value" @click="reservations.verify">核实预占结果</el-button><el-button v-if="reservations.notFound.value" link @click="reservations.retry">重试原请求</el-button><el-button v-if="!reservations.pending.value" link :disabled="locked" @click="reconfirm">重新核对占用</el-button></template></el-alert><el-alert v-if="state.optionsError" type="error" :closable="false"><template #title>{{state.optionsError}} <el-button link @click="refreshOptions">重试</el-button></template></el-alert><el-alert v-if="error" :title="error" type="error" :closable="false"/>
 <el-alert v-if="receiptTypeProblem" :title="receiptTypeProblem" type="warning" :closable="false"/>
 <el-form label-position="top" :disabled="locked" v-loading="state.optionsLoading"><h3>基础信息</h3><div class="storage-grid">
 <el-form-item label="单据编号"><el-input :model-value="form.number||'保存后自动生成'" readonly/></el-form-item><el-form-item label="所属工厂"><el-input :model-value="state.factories.find(f=>f.id===state.factoryId)?.name" readonly/></el-form-item>
 <el-form-item label="单据类型" required><el-select :model-value="form.type" aria-label="单据类型" @change="changeType"><el-option v-for="t in availableTypes" :key="t" :label="typeNames[t]+(expectedReceiptType&&t!==expectedReceiptType?'（与批次不符，请更正）':'')" :value="t" :disabled="!!expectedReceiptType&&t!==expectedReceiptType"/></el-select></el-form-item>
 <el-form-item label="单据日期" required><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD"/></el-form-item>
 <el-form-item v-if="productionRelated" label="生产工单" required><el-select :model-value="form.orderId" aria-label="生产工单" filterable @change="changeOrder"><el-option v-for="o in state.productionOrders" :key="o.id" :label="o.number" :value="o.id"/></el-select></el-form-item>
 <el-form-item v-if="form.type==='RAW_OUT'" label="关联原工单（可选）"><el-select :model-value="form.orderId" clearable filterable @change="changeRawOrder"><el-option v-for="o in state.productionOrders" :key="o.id" :label="o.number" :value="o.id"/></el-select></el-form-item><el-form-item v-if="productionRelated" label="加工批次" required><el-select :model-value="form.executionId" aria-label="加工批次" :disabled="!!form.id&&!!savedExecutionId" filterable @change="changeExecution"><el-option v-for="e in executions" :key="e.id" :label="`${e.number||e.batchNo||e.id} / ${e.productName||''} / ${e.processName||''}`" :value="e.id"/></el-select></el-form-item>
 <el-form-item v-if="['RAW_OUT','PRODUCT_OUT','SEMI_OUT'].includes(form.type)" label="提料客户" required><el-select aria-label="提料客户" :model-value="form.customerId" @change="changeCustomer" :disabled="form.type==='RAW_OUT'&&!!form.orderId"><el-option v-for="c in state.customers" :key="c.id" :value="c.id" :label="c.companyName||c.name"/></el-select></el-form-item>
 <el-form-item v-if="productionRelated&&selectedExecution&&!(form.type==='PRODUCTION_OUT'&&form.executionMode==='NEW')" label="批次加工人员"><el-input :model-value="selectedExecution.processorName||'人员资料不可用'" aria-label="批次加工人员" readonly/></el-form-item>
 <el-form-item label="指定复验人员" required :error="reviewerProblem"><el-select v-model="form.reviewerId" aria-label="指定复验人员" filterable @visible-change="v=>v&&refreshOptions()"><el-option v-if="form.reviewerId&&!reviewers.some(p=>String(p.id)===String(form.reviewerId))" :value="String(form.reviewerId)" :label="selectedReviewerName" disabled/><el-option v-for="p in reviewers" :key="p.id" :label="p.name||p.nickName" :value="p.id"/></el-select></el-form-item>
 <el-form-item label="仓库/库房" v-if="kind!=='REVERSE'" required><el-select :model-value="singleWarehouse?form.warehouseIds[0]||'':form.warehouseIds" :multiple="!singleWarehouse" aria-label="仓库/库房" @change="changeWarehouses"><el-option v-for="w in state.warehouses" :key="w.id" :label="w.name" :value="w.id"/></el-select></el-form-item>
 <el-form-item v-if="kind==='REVERSE'" label="原单据"><el-input :model-value="form.reverseDocumentNumber||form.reverseDocumentId" readonly/></el-form-item>
 <el-form-item label="原因 / 备注" :required="['ADJUST','RETURN_RIGHTS','REVERSE'].includes(kind)" class="wide"><el-input v-model="form.reason" type="textarea" maxlength="500"/></el-form-item>
 </div><section v-if="kind!=='REVERSE'" class="material-list"><h3>物料清单</h3><el-tabs v-model="activeWarehouse"><el-tab-pane v-for="id in form.warehouseIds" :key="id" :name="id" :label="warehouseName(id)"/></el-tabs>
 <div class="line-actions"><el-button type="primary" :disabled="!activeWarehouse||!!state.optionsError" @click="lineEditor?.open()">添加物料</el-button><el-button v-if="['OUT','IN','RET'].includes(kind)" :disabled="!activeWarehouse" @click="templateCsv">下载模板</el-button><el-button v-if="['OUT','IN','RET'].includes(kind)" :disabled="!activeWarehouse||!!state.optionsError" @click="fileInput?.click()">导入</el-button><input ref="fileInput" hidden type="file" accept=".csv,text/csv" @change="importCsv"/></div>
 <el-alert v-if="!['ADJUSTMENT','OWNERSHIP_RETURN','REVERSAL'].includes(form.type)&&activeLines.some((row:any)=>!['WEIGHT','PIECE'].includes(row.measurementMode))" title="明细计量方式缺失，请编辑明细重新加载并核对配置" type="warning" :closable="false"/><el-table :data="activeLines" border><AdjustmentColumns v-if="form.type==='ADJUSTMENT'" :lines="activeLines"/><template v-else><el-table-column label="物料 / 产品"><template #default="{row}">{{row.code||row.identity?.code||state.products.find(p=>String(p.id)===String(row.productId))?.code||row.materialId||row.productId||row.stockId}} {{row.name||row.identity?.name||state.products.find(p=>String(p.id)===String(row.productId))?.name||''}}<span v-if="form.type==='PRODUCTION_OUT'&&!state.optionsLoading&&!state.optionsError&&!state.stocks.some(s=>s.id===String(row.stockId)&&['RAW','SEMI'].includes(s.category))" style="color:var(--el-color-danger)">（不可用于生产领料，请重新选择）</span></template></el-table-column><el-table-column v-if="kind==='OUT'" label="客户 / 料权方"><template #default="{row}">{{row.selectionOwnerName||row.allocations?.map((a:any)=>a.ownerId==='0'?'工厂自有':state.customers.find(c=>String(c.id)===String(a.ownerId))?.name||a.ownerId).join('、')||'—'}}<span v-if="row.selectionCompanyName">｜{{row.selectionCompanyName}}</span></template></el-table-column><el-table-column prop="netWeight" label="净重（g）"/><el-table-column prop="quantity" label="件数"/><el-table-column v-if="form.type==='PRODUCT_IN'&&(activeLines.length?activeLines.some((row:any)=>row.measurementMode==='WEIGHT'):state.executions.find(e=>e.id===form.executionId)?.measurementMode==='WEIGHT')" prop="referenceQuantity" label="参考完工件数"/><el-table-column v-if="kind==='ADJUST'" prop="deltaWeight" label="净重增减（g）"/><el-table-column v-if="kind==='ADJUST'" prop="deltaQuantity" label="件数增减"/><el-table-column prop="remark" label="备注"/></template><el-table-column label="操作" width="140"><template #default="{row}"><el-button link @click="lineEditor?.open(row)">编辑</el-button><el-button link type="danger" @click="removeLine(row)">移除</el-button></template></el-table-column></el-table></section></el-form>
 <template #footer><el-button :disabled="locked" @click="close()">取消</el-button><el-button :disabled="locked" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="operation.busy.value" :disabled="locked||!!state.optionsError" @click="save(true)">提交复验</el-button></template>
 <LineEditor ref="lineEditor" :type="form.type" :customer-id="form.customerId" :warehouse-id="activeWarehouse" :execution-id="form.executionId" :debt="form.debt" :confirmed-lines="reservations.valid.value?form.lines:[]" :confirm-line="saveLine" :confirm-group="saveGroup" :refresh-options="refreshOptions" :reservation-pending="!!reservations.pending.value" :reservation-error="reservations.error.value" @verify-reservation="reservations.verify" @retry-reservation="reservations.retry" :reservation-retry="reservations.notFound.value"/>
</el-dialog>
</template>
<script setup lang="ts">
import {groupedLines} from './stock-groups'
import AdjustmentColumns from '../storage-live/AdjustmentColumns.vue'
import {optionalPurityValid,purityError} from '@/utils/business-decimal'
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {useReservations} from './useReservations'
import {registerSessionCleanup} from '@/utils/session-cleanup'
import {onBeforeRouteLeave} from 'vue-router';import {ElMessage,ElMessageBox} from 'element-plus';import request from '@/utils/request'
import {useStorage,useOperation,today,uid,message,type Row} from '../storage-live/client';import OperationStatus from '../storage-live/OperationStatus.vue';import {kindNames,typeNames,typesFor,productionReceiptTypes,receiptTypeFor,clone,type Kind} from './model';import LineEditor from './LineEditor.vue';import {parseCsv,downloadCsv} from '../storage-preview/csv'
const emit=defineEmits<{saved:[]}>(),{state,user,context,loadOptions}=useStorage(),base='/metal/storage-orders',visible=ref(false),kind=ref<Kind>('OUT'),form=ref<any>({lines:[],warehouseIds:[]}),activeWarehouse=ref(''),error=ref(''),lineEditor=ref<InstanceType<typeof LineEditor>>(),fileInput=ref<HTMLInputElement>()
const reservations=useReservations(form)
const locked=computed(()=>operation.busy.value||!!operation.pending.value||reservations.busy.value||!!reservations.pending.value)
let initial='',epoch=0,submitAfter=false,action='save'
const operation=useOperation(base,context,async data=>{if(action==='save'){Object.assign(form.value,data);savedExecutionId.value=String(form.value.executionId||'');initial=JSON.stringify(form.value);if(submitAfter){setTimeout(()=>{if(visible.value)void submit()},0);return}}if(action==='submit')reservations.bound();else{try{await reservations.release()}catch(e){error.value='草稿已保存，但库存占用释放尚未完成：'+message(e);return}}visible.value=false;window.dispatchEvent(new Event('storage-documents-changed'));emit('saved');ElMessage.success(action==='submit'?'已提交复验':'草稿已保存')})
const savedExecutionId=ref('')
const selectedExecution=computed(()=>state.executions.find(e=>String(e.id)===String(form.value.executionId)))
const expectedReceiptType=computed(()=>productionReceiptTypes.includes(form.value.type)?receiptTypeFor(selectedExecution.value?.outputCategory):undefined)
const availableTypes=computed(()=>{
 if(expectedReceiptType.value)return [...new Set([expectedReceiptType.value,...(form.value.id?[form.value.type]:[])])]
 return form.value.id&&productionReceiptTypes.includes(form.value.type)?productionReceiptTypes:typesFor(kind.value)
})
const receiptTypeProblem=computed(()=>{
 if(!productionReceiptTypes.includes(form.value.type)||!form.value.executionId||state.optionsLoading||state.optionsError)return ''
 if(!selectedExecution.value)return '加工批次已不可用，请核对工单、工厂和批次状态'
 if(!expectedReceiptType.value)return '加工批次的冻结产出类别未配置，请核对工单工序'
 return form.value.type===expectedReceiptType.value?'':`当前加工批次仅支持“${typeNames[expectedReceiptType.value]}”，请更正单据类型并重新核对物料明细`
})
function syncExecution(){
 if(form.value.type==='PRODUCTION_OUT'&&form.value.executionMode==='NEW')return false
 const e=selectedExecution.value
 if(!e||form.value.orderId&&String(e.orderId)!==String(form.value.orderId))return false
 form.value.orderId=String(e.orderId)
 form.value.orderItemId=String(e.orderItemId??'');form.value.orderStepId=String(e.orderStepId??'');form.value.processorId=String(e.processorId??'')
 const expected=productionReceiptTypes.includes(form.value.type)?receiptTypeFor(e.outputCategory):undefined
 if(!form.value.id&&expected&&form.value.type!==expected){form.value.type=expected;return true}
 return false
}
const productionRelated=computed(()=>['PRODUCTION_OUT','PROCESS_RAW_IN','SEMI_IN','PRODUCT_IN','RAW_RETURN','RECOVERY_RETURN'].includes(form.value.type))
const singleWarehouse=computed(()=>['RAW_IN','CUSTOMER_IN','RAW_RETURN','RECOVERY_RETURN'].includes(form.value.type))
const executions=computed(()=>state.executions.filter(e=>String(e.orderId)===String(form.value.orderId)&&e.status!=='CLOSED'))
const reviewers=computed(()=>state.people.filter(p=>(p.status==null||String(p.status)==='0')&&(kind.value==='OUT'?p.canReceive!==false:p.canAccept!==false)&&(form.value.type!=='PRODUCTION_OUT'||String(p.id)===String(state.executions.find(e=>e.id===form.value.executionId)?.processorId))))
const selectedReviewerName=computed(()=>state.people.find(p=>String(p.id)===String(form.value.reviewerId))?.name||form.value.reviewerName||`人员#${form.value.reviewerId}（资料不可用）`)
const reviewerProblem=computed(()=>{if(!form.value.reviewerId||state.optionsLoading||state.optionsError||reviewers.value.some(p=>String(p.id)===String(form.value.reviewerId)))return '';return form.value.type==='PRODUCTION_OUT'?'批次加工人员没有有效的领料复验权限，请先编辑批次调整人员':'指定复验人员已不可用，请重新选择'})
const activeLines=computed(()=>{const lines=form.value.lines.filter((l:any)=>l.warehouseId===activeWarehouse.value);return kind.value==='OUT'?groupedLines(lines):lines})
const warehouseName=(id:string)=>state.warehouses.find(w=>w.id===id)?.name||id
async function refreshOptions(){const current=context(),session=epoch,execution=form.value.executionId,type=form.value.type;await loadOptions({type,executionId:execution||undefined,sessionId:reservations.sessionId()});if(!visible.value||session!==epoch||current!==context()||execution!==form.value.executionId||type!==form.value.type)return;if(syncExecution())return refreshOptions();if(type==='PRODUCTION_OUT'){const e=state.executions.find(e=>e.id===execution);if(e){form.value.reviewerId=String(e.processorId);form.value.reviewerName=e.processorName||state.people.find(p=>String(p.id)===String(e.processorId))?.name||''}}}
async function open(next:Kind,row?:Row,preset:any={}){if(locked.value)return;if(visible.value&&!await requestClose())return;epoch++;kind.value=next;error.value='';operation.clear();form.value={id:'',factoryId:state.factoryId,kind:next,type:typesFor(next)[0],version:0,reviewRound:0,date:today(),orderId:'',executionId:'',reviewerId:'',warehouseIds:[],lines:[],reason:'',...clone(preset)};if(row){const current=context();try{const r:any=await request({url:base+'/'+row.id,params:{factoryId:state.factoryId}});if(current!==context())return;Object.assign(form.value,r.data)}catch(e){error.value=message(e);return}}if(!form.value.warehouseIds?.length)form.value.warehouseIds=[...new Set(form.value.lines.map((l:any)=>String(l.warehouseId)))];form.value.lines=form.value.lines.map((l:any)=>({...l,localId:l.localId||uid()}));activeWarehouse.value=form.value.warehouseIds[0]||'';reservations.reset();savedExecutionId.value=form.value.id?String(form.value.executionId||''):'';visible.value=true;const ticket=epoch;await refreshOptions();if(ticket===epoch)initial=JSON.stringify(form.value)}
async function allowClear(){if(locked.value)return false;if(!form.value.lines.length)return true;try{await ElMessageBox.confirm('变更选择将清空现有物料清单，是否继续？','确认变更');await reservations.release();reservations.reset();return true}catch{return false}}
async function changeType(value:string){if(value===form.value.type||!await allowClear())return;form.value.type=value;error.value='';form.value.lines=[];if(!productionReceiptTypes.includes(value)&&kind.value==='IN'){form.value.orderId='';form.value.executionId='';form.value.orderItemId='';form.value.orderStepId='';form.value.processorId=''}if(singleWarehouse.value&&form.value.warehouseIds.length>1)form.value.warehouseIds=[];activeWarehouse.value=form.value.warehouseIds[0]||'';await refreshOptions()}
async function changeOrder(value:string){if(!await allowClear())return;form.value.orderId=value;form.value.executionId='';form.value.lines=[];await refreshOptions()}
async function changeExecution(value:string){if(!await allowClear())return;form.value.executionId=value||'';error.value='';form.value.lines=[];syncExecution();await refreshOptions()}
async function changeWarehouses(value:string|string[]){
 const ids=(Array.isArray(value)?value:[value]).filter(Boolean),lines=form.value.lines.filter((l:any)=>ids.includes(l.warehouseId))
 if(lines.length!==form.value.lines.length){try{await ElMessageBox.confirm('移除仓库将删除其物料明细并释放占用，是否继续？','确认变更');await replaceLines(lines)}catch(e){if(e!=='cancel'&&e!=='close')error.value=message(e);return}}
 form.value.warehouseIds=ids;activeWarehouse.value=ids[0]||''
}
async function changeCustomer(value:string){if(value===form.value.customerId||!await allowClear())return;form.value.customerId=value;form.value.lines=[];await refreshOptions()}
async function changeRawOrder(value:string){if(value===form.value.orderId||!await allowClear())return;form.value.orderId=value;form.value.lines=[];form.value.customerId=value?state.productionOrders.find(o=>o.id===value)?.customerId:'';await refreshOptions()}
async function replaceLines(lines:any[]){if(kind.value==='OUT')await reservations.replace(lines);else form.value.lines=lines}
async function saveLine(line:any){
 if(kind.value==='OUT'&&((form.value.type==='PRODUCTION_OUT'&&(!form.value.orderId||!form.value.executionId))||(form.value.type!=='PRODUCTION_OUT'&&!form.value.customerId)))throw Error(form.value.type==='PRODUCTION_OUT'?'请先选择生产工单和加工批次':'请先选择提料客户')
 const next=clone(form.value.lines),at=next.findIndex((l:any)=>l.localId===line.localId)
 if(at<0)next.push(line);else next[at]=line
 await replaceLines(next)
}
async function saveGroup(lines:any[],groupId:string){
 if(kind.value!=='OUT')throw Error('只有出库单支持合并领料')
 if(form.value.type==='PRODUCTION_OUT'?(!form.value.orderId||!form.value.executionId):!form.value.customerId)throw Error('请先补齐工单、加工批次或提料客户')
 const next=clone(form.value.lines).filter((l:any)=>l.selectionGroupId!==groupId&&l.localId!==groupId)
 const existing=next.find((l:any)=>l.selectionKey===lines[0]?.selectionKey&&l.selectionGroupId)
 const targetId=existing?.selectionGroupId||groupId
 await replaceLines([...next,...lines.map(l=>({...l,selectionGroupId:targetId}))])
}
async function removeLine(line:any){try{await replaceLines(form.value.lines.filter((l:any)=>line.selectionGroupId?l.selectionGroupId!==line.selectionGroupId:l!==line));await refreshOptions()}catch(e){error.value=message(e)}}
async function reconfirm(){try{await reservations.replace(form.value.lines);await refreshOptions()}catch(e){error.value=message(e)}}
async function save(send:boolean){if(locked.value)return;error.value='';syncExecution();if(receiptTypeProblem.value){error.value=receiptTypeProblem.value;return}if(reviewerProblem.value){error.value=reviewerProblem.value;return;}if(form.value.type==='PRODUCTION_OUT'&&form.value.lines.some((l:any)=>!state.stocks.some(s=>s.id===String(l.stockId)&&['RAW','SEMI'].includes(s.category)))){error.value='生产领料仅支持本厂原料或半成品，请重新选择失效明细';return;}if(send&&['RAW_OUT','PRODUCT_OUT','SEMI_OUT'].includes(form.value.type)&&!form.value.customerId){error.value='请选择提料客户';return}if(send&&(!form.value.reviewerId||!form.value.date||productionRelated.value&&!form.value.executionId||kind.value!=='REVERSE'&&!form.value.lines.length)){error.value='请补齐复验人员、日期、加工批次和物料明细';return}if(kind.value==='OUT'&&form.value.lines.length&&!reservations.valid.value){try{await reservations.replace(form.value.lines)}catch(e){error.value=message(e);return}}submitAfter=send;action='save';const payload=clone(form.value);delete payload.debt;if(payload.type!=='EXTERNAL_SEMI_IN')for(const line of payload.lines)delete line.externalSource;await operation.send(base+(form.value.id?'/'+form.value.id:''),payload,form.value.id?'put':'post')}
async function submit(){action='submit';await operation.send(`${base}/${form.value.id}/submit`,{factoryId:state.factoryId,version:form.value.version,reviewRound:form.value.reviewRound,sessionId:form.value.sessionId,reservationVersion:form.value.reservationVersion})}
async function close(done?:()=>void){if(locked.value)return;const current=context();if(visible.value&&JSON.stringify(form.value)!==initial){try{await ElMessageBox.confirm('放弃未保存的单据内容？','确认关闭',{confirmButtonText:'放弃',cancelButtonText:'继续填写'})}catch{return}}if(current!==context())return;try{await reservations.release()}catch(e){error.value=message(e);return}visible.value=false;done?.()}
const csvFields=['stockId','materialId','productId','issueLineId','ownerId','netWeight','quantity','referenceQuantity','factoryWeight','purity','form','sourceReference','completedProcessIds','remark']
const csvLabels=['库存ID','物料ID','产品ID','原领料明细ID','料权客户ID(工厂填0)','净重(g)','数量(件)','参考完工件数','工厂料实测归还重量(g)','纯度(%)','形态(POWDER/UNTREATED/METAL)','外部来源凭证','已完成工序ID(分号分隔)','备注']
function templateCsv(){downloadCsv(typeNames[form.value.type]+'模板',csvLabels,[])}
async function importCsv(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;const current=context(),session=epoch;try{if(file.size>2*1024*1024)throw Error('CSV不能超过2MB');const rows=parseCsv(await file.text());if(rows.length>501)throw Error('一次最多500行');if(rows[0]?.join('|')!==csvLabels.join('|'))throw Error('请使用当前模板');const lines=rows.slice(1).map((r,index)=>{const fail=(m:string)=>{throw Error(`第${index+2}行：${m}`)};if(r.length!==csvFields.length)fail('字段数量错误');const l:any=Object.fromEntries(csvFields.map((f,i)=>[f,r[i].trim()]));const needsStock=kind.value==='OUT'||kind.value==='RET',stock=state.stocks.find(x=>x.id===l.stockId),material=state.materials.find(x=>x.id===l.materialId),product=state.products.find(x=>x.id===l.productId),execution=state.executions.find(x=>x.id===form.value.executionId);if(form.value.type==='PRODUCTION_OUT'&&stock?.category==='PRODUCT')fail('生产领料不能选择成品');if(needsStock&&!stock)fail(form.value.type==='PRODUCTION_OUT'?'生产领料仅支持本厂原料或半成品，不能选择成品':'库存不属于本厂');const category=({RAW_OUT:'RAW',PRODUCT_OUT:'PRODUCT',SEMI_OUT:'SEMI'} as Record<string,string>)[form.value.type];if(category&&stock?.category!==category)fail('库存类别与提料类型不符');if(kind.value==='OUT'&&stock?.warehouseId!==activeWarehouse.value)fail('库存不属于当前仓库');if(!needsStock&&!material&&!product)fail('请选择本厂物料或产品');if(l.ownerId&&l.ownerId!=='0'&&!state.customers.some(x=>x.id===l.ownerId))fail('料权客户不属于本厂');if(!/^\d{1,14}(\.\d{1,2})?$/.test(l.netWeight)||Number(l.netWeight)<=0)fail('净重须为正数且最多两位小数');const mode=stock?.measurementMode||execution?.measurementMode||material?.measurementMode;if(!mode)fail('未配置计量方式');if(mode==='PIECE'&&!/^[1-9]\d{0,8}$/.test(l.quantity))fail('请填写正整数件数');if(form.value.type==='PRODUCT_IN'&&mode==='WEIGHT'&&!/^[1-9]\d{0,8}$/.test(l.referenceQuantity))fail('请填写参考完工件数');if(!optionalPurityValid(l.purity))fail(purityError);if(kind.value==='RET'&&!l.issueLineId)fail('缺少原领料明细ID');if(form.value.type==='EXTERNAL_SEMI_IN'&&(!l.sourceReference||!l.completedProcessIds))fail('请填写外部来源与完整已完成工序');return {...l,...(form.value.type==='EXTERNAL_SEMI_IN'?{externalSource:{sourceReference:l.sourceReference,completedProcessIds:l.completedProcessIds.split(';').filter(Boolean)}}:{}),localId:uid(),warehouseId:activeWarehouse.value,measurementMode:mode,quantity:mode==='PIECE'?l.quantity:null,ownerId:l.ownerId||'0',form:l.form||material?.materialForm,allocations:[],debtAllocations:[],code:stock?.identity?.code||material?.code,name:stock?.identity?.name||material?.name||product?.name}});if(current===context()&&session===epoch)await replaceLines([...form.value.lines,...lines])}catch(e){error.value=message(e)}}

async function requestClose(){await close();return !visible.value}
const unload=()=>reservations.unload(operation.busy.value||!!operation.pending.value)
const unregister=registerSessionCleanup(async()=>{if(visible.value){if(locked.value)throw Error('请先核实当前单据或预占结果后退出');await reservations.release()}})
const heartbeat=window.setInterval(()=>{if(visible.value&&kind.value==='OUT'&&!operation.busy.value&&!operation.pending.value)void reservations.renew()},60000)
window.addEventListener('pagehide',unload)
onBeforeRouteLeave(async()=>!visible.value||await requestClose())
onBeforeUnmount(()=>{unload();window.clearInterval(heartbeat);window.removeEventListener('pagehide',unload);unregister()})
watch(()=>[state.factoryId,user.sessionEpoch],()=>{unload();epoch++;visible.value=false;reservations.reset()})
defineExpose({open,requestClose})
</script>
<style scoped>
.material-list{margin-top:8px;padding:18px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-fill-color-extra-light)}
.material-list h3{margin:0 0 12px;padding:0 0 12px;border-bottom:1px solid var(--el-border-color-light);color:var(--el-text-color-primary);font-size:15px;font-weight:600}
.material-list :deep(.el-tabs__header){margin-bottom:14px}
.material-list :deep(.el-table){border-radius:6px;overflow:hidden}
.material-list :deep(.el-table__empty-block){min-height:88px}
.line-actions{display:flex;gap:8px;margin-bottom:14px;flex-wrap:wrap}
.line-actions .el-button+.el-button{margin-left:0}
.storage-grid .el-select{width:100%}
</style>
