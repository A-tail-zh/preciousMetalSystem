<template>
 <section class="quote-page">
  <el-form ref="quoteForm" :model="quoteModel" scroll-to-error label-width="100px" label-position="left" :disabled="savingQuote" class="quotation-form">
   <div v-for="(item,index) in items" :key="item.key" class="goods-item">
    <el-form-item label="商品类型" :prop="`items.${index}.category`" :rules="[fieldRule(v=>v?'':'请选择商品类型','change')]"><el-select v-model="item.category" fit-input-width :teleported="false" @change="categoryChanged(item)"><el-option v-for="(_,name) in productDimensions" :key="name" :value="name"/></el-select></el-form-item>
    <el-form-item label="商品数量" :prop="`items.${index}.quantity`" :rules="integerRules('商品数量')"><el-input v-model="item.quantity" inputmode="numeric" placeholder="请输入…"/></el-form-item>
    <div class="spec-title">商品规格</div>
    <div class="spec-fields"><el-form-item v-for="dimension in productDimensions[item.category]??[]" :key="dimension.key" :prop="`items.${index}.dimensions.${dimension.key}`" :rules="[fieldRule(v=>decimalError(v,dimension.label,{positive:true}))]" :label="dimension.label+'：'" label-width="80px"><el-input v-model="item.dimensions[dimension.key]" inputmode="decimal" placeholder="请输入…"/><span class="dimension-unit">cm</span></el-form-item></div>
    <div v-if="item.category&&!supportedVolumeCategories.includes(item.category)" class="formula-note">该类别暂无计算公式</div>
    <el-button v-if="items.length>1" link type="danger" @click="items.splice(index,1)">移除商品</el-button>
   </div>
   <el-button class="blue-button add-goods" @click="addGoods">添加商品</el-button>
   <el-form-item v-if="canSave" label="所属工厂" prop="factoryId" :rules="[fieldRule(v=>v?'':'请选择所属工厂','change')]" class="quote-factory">
    <el-select v-model="quoteFactory" :disabled="!isAdmin||savingQuote||loadingFactories" :loading="loadingFactories" fit-input-width placeholder="请先选择所属工厂"><el-option v-for="o in quoteFactories" :key="o.id" :label="o.name" :value="o.id"/></el-select>
    <span v-if="loadingFactories" role="status">正在加载工厂…</span>
    <span v-else-if="factoryLoadFailed" role="alert">工厂加载失败，请重试</span>
    <span v-else-if="!quoteFactories.length" role="alert">暂无可用工厂，请联系管理员</span>
    <el-button v-if="factoryLoadFailed||(!loadingFactories&&!quoteFactories.length)" link type="primary" @click="loadFactories">重新加载工厂</el-button>
   </el-form-item>
   <p v-if="factoryChangedNotice" class="calculation-hints" role="status">所属工厂已变更，请重新计算</p>
   <div class="calculate-row"><el-button class="blue-button" :loading="calculating" :disabled="!canCalculate" @click="calculate">开始计算</el-button></div>
   <div v-if="calculationProblems.length" class="calculation-hints" role="status"><div v-for="problem in calculationProblems" :key="problem">{{problem}}</div><el-button v-if="ruleLoadFailed" link type="primary" @click="loadRule">重新加载配置</el-button></div>
  </el-form>
  <div v-if="results.length" class="quote-results"><el-table :data="results" border class="quotation-results-table"><el-table-column type="index" label="序号" width="65"/><el-table-column prop="category" label="商品类型" min-width="135"/><el-table-column prop="quantity" label="数量" align="right" min-width="85"/><el-table-column label="规格尺寸（cm）" min-width="260"><template #default="{row}">{{specification(row)}}</template></el-table-column><el-table-column label="产品图片" width="230"><template #default="{row}"><el-image v-if="row.imageUrl" class="quotation-product-image" :src="row.imageUrl" :preview-src-list="[row.imageUrl]" preview-teleported fit="contain" :alt="row.category+'尺寸图'"/><el-button v-else-if="row.imageState==='error'" link type="primary" @click="loadImage(row)">图片加载失败，点击重试</el-button><span v-else>{{row.imageState==='loading'?'图片加载中…':'暂无图片模板'}}</span></template></el-table-column><el-table-column prop="volume" label="产品体积（cm³）" align="right" min-width="160"/><el-table-column prop="unitPrice" label="单件报价（元）" align="right" min-width="160"/><el-table-column prop="totalPrice" label="金额（元）" align="right" min-width="175"/></el-table><strong>总报价：{{totalPrice}} 元</strong><div v-if="canSave" class="save-quote"><el-button class="blue-button" :loading="savingQuote" :disabled="!!savedQuoteNo||savingQuote||calculating" @click="saveQuote">保存报价单</el-button><span v-if="savedQuoteNo">已保存：{{savedQuoteNo}}</span><el-button v-if="savedQuoteId" class="blue-button" :loading="downloadingQuote" @click="downloadQuote">下载报价单</el-button></div></div>
 </section>
</template>
<script setup lang="ts">
import type {FormInstance} from 'element-plus'
import {fieldRule,integerRules,decimalError,applyFieldErrors,remapFieldErrors} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {createUUID} from '@/utils/uuid'
import {ref,computed,onMounted,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import {ElMessage} from 'element-plus'
import {productDimensions} from '../master-config'
import {productVolume,supportedVolumeCategories} from '@/utils/product-volume'
type Item={key:number;category:string;quantity:string;dimensions:Record<string,string>}
type QuoteInput={category:string;quantity:number;dimensions:Record<string,string>}
type CalculationSnapshot={signature:string;factoryId:string;items:QuoteInput[]}
type Result={imageUrl?:string;imageState?:'loading'|'error'|'unsupported'|'ready';volume:string;unitPrice:string;totalPrice:string;category:string;quantity:number;dimensions:Record<string,string>}
function specification(row:Result){return (productDimensions[row.category]??[]).map(d=>d.label+'：'+row.dimensions[d.key]).join('；')}
const imageCategories=['铱坩埚弧底','铱坩埚平底','铱坩埚梯形','铱杆实心','铱杆空心','铱环']
async function loadImage(row:Result){
 const epoch=calculationEpoch,current=()=>!disposed&&epoch===calculationEpoch&&results.value.includes(row)
 if(!current())return
 if(!imageCategories.includes(row.category)){row.imageState='unsupported';return}
 row.imageState='loading'
 try{const response=await request({url:'/metal/quotation/image',method:'post',headers:{repeatSubmit:false},data:{category:row.category,dimensions:row.dimensions,quantity:row.quantity},...{isContextCurrent:current}});if(current()){row.imageUrl=response.data;row.imageState='ready'}}catch{if(current())row.imageState='error'}
}
function loadResultImages(){for(const row of results.value)void loadImage(row)}
let key=0
const blank=():Item=>({key:++key,category:'',quantity:'',dimensions:{}})
const items=ref<Item[]>([blank()]),pi=ref(''),calculating=ref(false),results=ref<Result[]>([])
const user=useUserStore(),canSave=computed(()=>actionPermission.has('metal:quotation:add')),isAdmin=computed(()=>String(user.id)==='1'),quoteFactories=ref<{id:string,name:string}[]>([]),quoteFactory=ref(''),quoteRequestId=ref(createUUID()),savingQuote=ref(false),savedQuoteNo=ref(''),savedQuoteId=ref(''),downloadingQuote=ref(false)
const loadingRule=ref(true),ruleLoadFailed=ref(false),loadingFactories=ref(canSave.value),factoryLoadFailed=ref(false),factoryChangedNotice=ref(false)
const calculationProblems=computed(()=>{
 if(loadingRule.value)return ['正在加载报价配置']
 if(ruleLoadFailed.value||!pi.value)return ['报价配置加载失败，请重新加载']
 const problems:string[]=[]
 if(canSave.value){
  if(loadingFactories.value)problems.push('正在加载所属工厂')
  else if(factoryLoadFailed.value)problems.push('请重新加载所属工厂')
  else if(!quoteFactory.value||!quoteFactories.value.some(f=>f.id===quoteFactory.value))problems.push('请先选择所属工厂')
 }
 for(const [index,item]of items.value.entries()){
  const prefix=`商品${index+1}：`
  if(!item.category){problems.push(prefix+'请选择商品类型；多余的空白商品可移除');continue}
  if(!supportedVolumeCategories.includes(item.category)){problems.push(prefix+'该类别暂无计算公式（弧底、梯形暂不支持）');continue}
  if(!/^[1-9]\d{0,6}$/.test(item.quantity)||Number(item.quantity)>1000000){problems.push(prefix+'商品数量须为1～1000000的整数');continue}
  const invalid=(productDimensions[item.category]??[]).find(d=>!/^(0|[1-9]\d{0,13})(\.\d{1,6})?$/.test(item.dimensions[d.key]??'')||Number(item.dimensions[d.key])<=0)
  if(invalid){problems.push(prefix+invalid.label+'须为正数，最多14位整数、6位小数');continue}
  if(productVolume(item.category,item.dimensions,pi.value)===null)problems.push(prefix+'外径必须大于内径')
 }
 return problems
})
const quoteForm=ref<FormInstance>(),quoteModel=computed(()=>({items:items.value,factoryId:quoteFactory.value}))
const canCalculate=computed(()=>!savingQuote.value&&!!items.value.length&&!calculationProblems.value.length)
const totalPrice=computed(()=>{const sum=results.value.reduce((a,r)=>a+BigInt(r.totalPrice.replace('.','')),0n).toString().padStart(7,'0');return sum.slice(0,-6)+'.'+sum.slice(-6)})
let calculationEpoch=0,ruleEpoch=0,factoryEpoch=0,saveEpoch=0,disposed=false
let calculatedSnapshot:CalculationSnapshot|undefined
const inputItems=():QuoteInput[]=>items.value.map(item=>({category:item.category,quantity:Number(item.quantity),dimensions:{...item.dimensions}}))
const inputSignature=()=>JSON.stringify(inputItems())
function clearSavedQuote(){savedQuoteNo.value='';savedQuoteId.value='';quoteRequestId.value=createUUID()}
function invalidateCalculation(){calculationEpoch++;calculating.value=false;results.value=[];calculatedSnapshot=undefined;clearSavedQuote()}
watch(()=>JSON.stringify(items.value),()=>{factoryChangedNotice.value=false;invalidateCalculation()},{flush:'sync'})
watch(quoteFactory,()=>{factoryChangedNotice.value=results.value.length>0||calculating.value;invalidateCalculation()},{flush:'sync'})
onBeforeUnmount(()=>{disposed=true;ruleEpoch++;factoryEpoch++;saveEpoch++;invalidateCalculation()})
function categoryChanged(item:Item){item.dimensions=Object.fromEntries((productDimensions[item.category]??[]).map(d=>[d.key,'']))}
function addGoods(){items.value.push(blank())}
async function calculate(){
 if(disposed||calculating.value||savingQuote.value||!canCalculate.value)return
 invalidateCalculation();factoryChangedNotice.value=false
 const epoch=calculationEpoch,snapshot:CalculationSnapshot={signature:inputSignature(),factoryId:quoteFactory.value,items:inputItems()}
 const current=()=>!disposed&&epoch===calculationEpoch&&snapshot.factoryId===quoteFactory.value&&snapshot.signature===inputSignature()
 calculating.value=true
 try{
  const responses=await Promise.all(snapshot.items.map((item,index)=>request({url:'/metal/quotation/calculate',method:'post',headers:{repeatSubmit:false},data:item,...{isContextCurrent:current}}).catch((error:any)=>{throw remapFieldErrors(error,path=>path?`items.${index}.${path}`:path)})))
  if(!current())return
  calculatedSnapshot=snapshot;results.value=responses.map((r,index)=>({...r.data,...snapshot.items[index]}));loadResultImages()
 }catch(error){if(current()){applyFieldErrors(quoteForm.value,error);results.value=[];calculatedSnapshot=undefined}}finally{if(current())calculating.value=false}
}
async function loadRule(){
 const round=++ruleEpoch,current=()=>!disposed&&round===ruleEpoch
 loadingRule.value=true;ruleLoadFailed.value=false
 try{const response=await request({url:'/metal/quotation/context',method:'get',...{isContextCurrent:current}});if(current())pi.value=response.data.rule.piValue}
 catch{if(current())ruleLoadFailed.value=true}finally{if(current())loadingRule.value=false}
}
async function loadFactories(){
 if(disposed||savingQuote.value||!canSave.value)return
 const round=++factoryEpoch,current=()=>!disposed&&round===factoryEpoch
 loadingFactories.value=true;factoryLoadFailed.value=false;quoteFactories.value=[]
 try{
  const response=await request({url:'/metal/quotation/factories',...{isContextCurrent:current}})
  if(!current())return
  quoteFactories.value=response.data.map((f:{id:string|number;name:string})=>({...f,id:String(f.id)}))
  if(!isAdmin.value||quoteFactories.value.length===1)quoteFactory.value=quoteFactories.value[0]?.id||''
  else if(!quoteFactories.value.some(f=>f.id===quoteFactory.value))quoteFactory.value=''
 }catch{if(current()){factoryLoadFailed.value=true;quoteFactory.value=''}}finally{if(current())loadingFactories.value=false}
}
async function saveQuote(){
 if(disposed||!canSave.value||savingQuote.value||calculating.value||!results.value.length||savedQuoteId.value)return
 const snapshot=calculatedSnapshot
 if(!snapshot||snapshot.factoryId!==quoteFactory.value||snapshot.signature!==inputSignature()||!canCalculate.value){ElMessage.error('报价条件已变化，请重新计算');return}
 const round=++saveEpoch,epoch=calculationEpoch,body=JSON.parse(JSON.stringify({requestId:quoteRequestId.value,factoryId:snapshot.factoryId,items:snapshot.items}))
 const current=()=>!disposed&&round===saveEpoch&&epoch===calculationEpoch&&snapshot.factoryId===quoteFactory.value&&snapshot.signature===inputSignature()
 savingQuote.value=true
 try{
  const response=await request({url:'/metal/quotation/save',method:'post',headers:{repeatSubmit:false},data:body,...{isContextCurrent:current}})
  if(!current())return
  savedQuoteNo.value=response.data.quoteNo;savedQuoteId.value=String(response.data.id);results.value=response.data.items;loadResultImages();ElMessage.success('报价单已保存')
 }catch{/* The request layer displays current-context errors; retain the snapshot and UUID for retry. */}
 finally{if(!disposed&&round===saveEpoch)savingQuote.value=false}
}
async function downloadQuote(){
 if(disposed||!savedQuoteId.value||downloadingQuote.value)return
 const quoteId=savedQuoteId.value,epoch=calculationEpoch,filename=savedQuoteNo.value+'.xlsx',current=()=>!disposed&&epoch===calculationEpoch&&quoteId===savedQuoteId.value
 downloadingQuote.value=true
 try{
  const blob=await request({url:'/metal/quotation/orders/'+quoteId+'/download',method:'get',responseType:'blob',...{isContextCurrent:current}})
  if(!current())return
  const file=blob as unknown as Blob
  if(file.type.includes('json')){const error=JSON.parse(await file.text());if(current())ElMessage.error(error.msg||'报价单下载失败');return}
  const url=URL.createObjectURL(file),a=document.createElement('a');a.href=url;a.download=filename;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000)
 }catch{/* Current errors are displayed by the request layer. */}finally{if(!disposed)downloadingQuote.value=false}
}
onMounted(()=>{void loadRule();if(canSave.value)void loadFactories()})
</script>
<style scoped>
.quote-page{background:#f8fbff;min-height:calc(var(--design-height,100vh) - 82px);padding:25px 30px}.quotation-form{width:550px;max-width:100%}.goods-item{margin-bottom:20px}.quotation-form :deep(.el-form-item){margin-bottom:18px}.goods-item>.el-form-item :deep(.el-input),.goods-item>.el-form-item :deep(.el-select){width:280px}.spec-title{margin:28px 0 18px;font-size:14px}.spec-fields{margin-left:20px}.spec-fields :deep(.el-input){width:150px}.spec-fields :deep(.el-form-item){margin-bottom:8px}.dimension-unit{margin-left:10px}.blue-button{background:#577bef;border-color:#577bef;color:#fff}.add-goods{margin-top:6px}.calculate-row{display:flex;justify-content:flex-end;margin-top:40px}.calculate-row .el-button{width:200px}.formula-note{color:#8a9099;font-size:14px}.quote-results{margin-top:30px;line-height:2;max-width:100%}.quotation-product-image{width:210px;height:155px;cursor:zoom-in}.quotation-results-table{width:100%;margin-bottom:16px}.quotation-results-table :deep(.cell){white-space:normal;overflow-wrap:anywhere}
</style>
<style scoped>.quotation-form :deep(.el-form-item__label){white-space:nowrap;flex-shrink:0}.calculation-hints{margin-top:12px;color:#c45656;font-size:14px;line-height:1.8} .quote-factory :deep(.el-select){width:280px;max-width:100%}.quote-factory :deep(.el-form-item__content){gap:8px}.quote-factory [role="alert"],.quote-factory [role="status"]{font-size:13px} @media(max-width:600px){.quote-page{padding:16px}.quotation-form{width:100%}.goods-item>.el-form-item :deep(.el-input),.goods-item>.el-form-item :deep(.el-select){width:100%}.calculate-row .el-button{max-width:100%}}
</style>
<style scoped>.save-quote{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:18px}.save-quote .el-select{width:260px}.save-quote small{color:#909399}</style>