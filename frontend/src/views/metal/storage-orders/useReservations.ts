import {ref,type Ref} from 'vue'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import {uid,message} from '../storage-live/client'
import {clone} from './model'

const base='/metal/storage-orders/reservations'
const uncertain=(e:any)=>(e?.isAxiosError&&!e.response)||Number(e?.response?.status||e?.code)>=500
export function useReservations(form:Ref<any>){
 const busy=ref(false),error=ref(''),valid=ref(false),pending=ref<any>(null),notFound=ref(false)
 let token='',factoryId='',sessionId='',version=0,status='',generation=0
 function reset(){generation++;busy.value=false;token=getToken()||'';factoryId=form.value.factoryId;sessionId=uid();version=0;status='';valid.value=false;pending.value=null;notFound.value=false;error.value='';delete form.value.sessionId;delete form.value.reservationVersion}
 function accept(result:any,lines:any[]){
  const data={...result,...result.current}
  version=Number(data.version);status=data.status;valid.value=status==='TEMP';form.value.sessionId=sessionId;form.value.reservationVersion=version
  if(data.batchId)form.value.batchId=String(data.batchId)
  if(data.executionId){form.value.executionId=String(data.executionId);if(data.processorId)form.value.processorId=String(data.processorId);if(data.orderItemId)form.value.orderItemId=String(data.orderItemId);if(data.orderStepId)form.value.orderStepId=String(data.orderStepId)}
  if(valid.value)form.value.lines=lines.map(l=>({...l,...(data.lines||[]).find((r:any)=>r.reservationLineId===l.reservationLineId)}));error.value=valid.value||status==='RELEASED'?'':'预占已失效，请重新确认物料明细';pending.value=null;notFound.value=false
 }
 async function replace(lines:any[]){
  if(busy.value||pending.value)throw Error('预占结果尚未核实，请先核实原请求')
  const round=generation,next=clone(lines).map((l:any)=>({...l,reservationLineId:l.reservationLineId||l.localId||uid()}))
  const data={documentId:form.value.id||undefined,factoryId,requestId:uid(),sessionId,reservationVersion:version,type:form.value.type,executionMode:form.value.executionMode,orderId:form.value.orderId||undefined,batchId:form.value.batchId||undefined,executionId:form.value.executionId||undefined,orderItemId:form.value.orderItemId||undefined,orderStepId:form.value.orderStepId||undefined,processorId:form.value.processorId||form.value.reviewerId||undefined,sourceOutboundId:form.value.sourceOutboundId||undefined,customerId:form.value.customerId||undefined,lines:next}
  busy.value=true;error.value=''
  try{const r:any=await request({url:base,method:'post',data,headers:{repeatSubmit:false},quiet:true});if(round!==generation)throw Error('编辑会话已失效');accept(r.data,next);if(!valid.value)throw Error(error.value)}
  catch(e){if(round===generation){error.value=message(e);if(uncertain(e)){pending.value={data,lines:next};valid.value=false;error.value='预占结果未知，请核实原请求；输入及原明细已保留'}}throw Error(error.value||message(e))}
  finally{if(round===generation)busy.value=false}
 }
 async function verify(){if(!pending.value||busy.value)return false;const operation=pending.value,round=generation;busy.value=true;try{const r:any=await request({url:`${base}/operations/${operation.data.requestId}`,params:{factoryId},quiet:true});if(round!==generation)return false;accept(r.data,operation.lines);return valid.value}catch(e:any){if(round!==generation)return false;notFound.value=Number(e?.code||e?.response?.status)===404;error.value=notFound.value?'尚未查到预占结果，可重试原请求':'预占核实失败：'+message(e);return false}finally{if(round===generation)busy.value=false}}
 async function retry(){
  if(!pending.value||busy.value||!notFound.value)return
  const operation=pending.value,round=generation;busy.value=true
  try{const r:any=await request({url:operation.url||base,method:'post',data:operation.data,headers:{repeatSubmit:false},quiet:true});if(round===generation)accept(r.data,operation.lines)}catch(e){if(round===generation)error.value=message(e)}finally{if(round===generation)busy.value=false}
 }
 async function release(){
  if(pending.value||busy.value)throw Error('预占结果尚未核实，暂不能关闭或变更')
  if(!version||status!=='TEMP')return
  busy.value=true
  const round=generation,operation={url:`${base}/${sessionId}/release`,data:{factoryId,requestId:uid(),reservationVersion:version},lines:clone(form.value.lines)}
  try{const r:any=await request({url:operation.url,method:'post',data:operation.data,headers:{repeatSubmit:false,Authorization:`Bearer ${token}`},quiet:true});if(round!==generation)return;status=r.data.current?.status||r.data.status;version=Number(r.data.current?.version??r.data.version??version);valid.value=false;error.value=''}catch(e){if(round===generation){error.value='释放失败：'+message(e);if(uncertain(e)){pending.value=operation;valid.value=false;error.value='释放结果未知，请核实原请求后关闭'}}throw e}finally{if(round===generation)busy.value=false}
 }
 async function renew(){
  if(!version||status!=='TEMP'||busy.value||pending.value)return
  const round=generation;busy.value=true
  try{const r:any=await request({url:`${base}/${sessionId}/renew`,method:'post',data:{factoryId,requestId:uid(),reservationVersion:version},headers:{repeatSubmit:false},quiet:true});if(round!==generation)return;version=Number(r.data.version??version);form.value.reservationVersion=version;status=r.data.status||status;valid.value=status==='TEMP';error.value=valid.value?'':'预占已失效，请重新核对占用'}catch(e){if(round===generation){valid.value=false;error.value='预占续租失败：'+message(e)+'；请重新核对占用后提交'}}finally{if(round===generation)busy.value=false}
 }
 function unload(blocked=false){if(blocked||!version||status!=='TEMP')return;void fetch(`${import.meta.env.VITE_APP_BASE_API}${base}/${sessionId}/release`,{method:'POST',keepalive:true,headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify({factoryId,requestId:uid(),reservationVersion:version})}).catch(()=>{})}
 function bound(){status='BOUND';valid.value=false}
 return {busy,error,valid,pending,notFound,reset,replace,verify,retry,release,renew,unload,bound,sessionId:()=>version?sessionId:undefined}
}
