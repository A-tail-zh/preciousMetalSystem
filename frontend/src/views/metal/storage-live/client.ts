import {computed,reactive,ref,watch} from 'vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import {createUUID} from '@/utils/uuid'
export type Row={id:string;factoryId:string;[key:string]:any}
export const storage=reactive({factoryId:'',factories:[] as Row[],factoriesLoading:false,factoryError:'',optionsLoading:false,optionsError:'',warehouses:[] as Row[],materials:[] as Row[],products:[] as Row[],customers:[] as Row[],people:[] as Row[],productionOrders:[] as Row[],executions:[] as Row[],stocks:[] as Row[]})
let identity='',factoryRequest=0,optionRequest=0
export const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
export const uid=createUUID
export const message=(e:any)=>e?.response?.data?.msg||e?.message||String(e||'请求失败')
export function useStorage(){
 const user=useUserStore(),isAdmin=computed(()=>String(user.id)==='1')
 const can=(p:string)=>user.sessionReady&&(user.permissions.includes('*:*:*')||user.permissions.includes(p))
 const context=()=>JSON.stringify([user.id,user.token,user.sessionEpoch,storage.factoryId])
 async function loadFactories(){const epoch=++factoryRequest,account=user.token;storage.factoriesLoading=true;storage.factoryError='';try{const r:any=await request({url:'/metal/options/factories',quiet:true});if(epoch!==factoryRequest||account!==user.token)return;storage.factories=(r.data||[]).map((f:any)=>({...f,id:String(f.id)}));if(!isAdmin.value)storage.factoryId=String(user.factoryId||storage.factories[0]?.id||'');else if(!storage.factories.some(f=>f.id===storage.factoryId))storage.factoryId=''}catch(e){if(epoch===factoryRequest)storage.factoryError=message(e)}finally{if(epoch===factoryRequest)storage.factoriesLoading=false}}
 watch(()=>JSON.stringify([user.sessionReady,user.id,user.token,user.sessionEpoch]),key=>{if(identity===key)return;identity=key;factoryRequest++;optionRequest++;storage.factoryId='';storage.factories=[];for(const k of ['warehouses','materials','products','customers','people','productionOrders','executions','stocks'] as const)storage[k]=[];if(user.sessionReady)void loadFactories()},{immediate:true})
 async function loadOptions(params:Record<string,any>={}){if(!storage.factoryId)return;const epoch=++optionRequest,current=context();storage.optionsLoading=true;storage.optionsError='';try{const r:any=await request({url:'/metal/storage-orders/options',params:{factoryId:storage.factoryId,...params},quiet:true});if(epoch!==optionRequest||current!==context())return;for(const k of ['warehouses','materials','products','customers','people','productionOrders','executions','stocks'] as const)storage[k]=(r.data?.[k]||[]).map((v:any)=>({...v,id:String(v.id)}))}catch(e){if(current===context()&&epoch===optionRequest)storage.optionsError=message(e)}finally{if(epoch===optionRequest)storage.optionsLoading=false}}
 function changeFactory(id:string){if(storage.factories.some(f=>f.id===id)&&(isAdmin.value||id===String(user.factoryId)))storage.factoryId=id}
 return {state:storage,user,can,isAdmin,context,loadFactories,loadOptions,changeFactory}
}
export function useOperation(base:string,context:()=>string,onSuccess:(data:any)=>void|Promise<void>){
 const busy=ref(false),error=ref(''),pending=ref<{url:string;method:'post'|'put';data:any;context:string}|null>(null),notFound=ref(false)
 async function complete(data:any){pending.value=null;notFound.value=false;error.value='';await onSuccess(data)}
 // New actions get a fresh key; only retry() reuses the frozen request.
 async function send(url:string,data:any,method:'post'|'put'='post') {if(busy.value||pending.value)return;pending.value={url,method,data:JSON.parse(JSON.stringify({...data,requestId:uid()})),context:context()};await retry()}
 async function retry(){const operation=pending.value;if(!operation||busy.value||operation.context!==context())return;busy.value=true;error.value='';try{const r:any=await request({url:operation.url,method:operation.method,data:operation.data,headers:{repeatSubmit:false},quiet:true});if(operation.context===context())await complete(r.data??r)}catch(e:any){if(operation.context!==context())return;const uncertain=(e?.isAxiosError&&!e.response)||Number(e?.response?.status||e?.code)>=500;if(!uncertain){pending.value=null;error.value=message(e)}else{error.value='提交结果暂未确认，请先核实保存结果';notFound.value=false}}finally{busy.value=false}}
 async function verify(){const operation=pending.value;if(!operation||busy.value||operation.context!==context())return;busy.value=true;try{const r:any=await request({url:`${base}/operations/${operation.data.requestId}`,params:{factoryId:operation.data.factoryId},quiet:true});if(operation.context===context())await complete(r.data??r)}catch(e:any){if(operation.context===context()){notFound.value=Number(e?.code||e?.response?.status)===404;error.value=notFound.value?'尚未查到结果，可使用原请求重试':'结果核实失败，请稍后重试'}}finally{busy.value=false}}
 watch(context,(next,previous)=>{if(next!==previous){pending.value=null;notFound.value=false;error.value=''}})
 function clear(){if(busy.value||pending.value)return false;error.value='';return true}
 return {busy,error,pending,notFound,send,retry,verify,clear}
}
