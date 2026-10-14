<template>
 <section class="quote-page">
  <el-form label-width="100px" label-position="left" :disabled="calculating||savingQuote" class="quotation-form">
   <div v-for="(item,index) in items" :key="item.key" class="goods-item">
    <el-form-item label="商品类型"><el-select v-model="item.category" fit-input-width :teleported="false" @change="categoryChanged(item)"><el-option v-for="(_,name) in productDimensions" :key="name" :value="name"/></el-select></el-form-item>
    <el-form-item label="商品数量"><el-input v-model="item.quantity" inputmode="numeric" placeholder="请输入…"/></el-form-item>
    <div class="spec-title">商品规格</div>
    <div class="spec-fields"><el-form-item v-for="dimension in productDimensions[item.category]??[]" :key="dimension.key" :label="dimension.label+'：'" label-width="80px"><el-input v-model="item.dimensions[dimension.key]" inputmode="decimal" placeholder="请输入…"/><span class="dimension-unit">cm</span></el-form-item></div>
    <div v-if="item.category&&!supportedVolumeCategories.includes(item.category)" class="formula-note">该类别暂无计算公式</div>
    <el-button v-if="items.length>1" link type="danger" @click="items.splice(index,1)">移除商品</el-button>
   </div>
   <el-button class="blue-button add-goods" @click="addGoods">添加商品</el-button>
   <div class="calculate-row"><el-button class="blue-button" :loading="calculating" :disabled="!canCalculate" @click="calculate">开始计算</el-button></div>
   <div v-if="calculationProblems.length" class="calculation-hints" role="status"><div v-for="problem in calculationProblems" :key="problem">{{problem}}</div><el-button v-if="ruleLoadFailed" link type="primary" @click="loadRule">重新加载配置</el-button></div>
  </el-form>
  <div v-if="results.length" class="quote-results"><div v-for="(result,index) in results" :key="index">商品{{index+1}}：体积 {{result.volume}} cm³，单件报价 {{result.unitPrice}} 元，合计 {{result.totalPrice}} 元</div><strong>总报价：{{totalPrice}} 元</strong><div v-if="canSave" class="save-quote"><span>所属工厂：</span><el-select v-model="quoteFactory" :disabled="!isAdmin||savingQuote" fit-input-width placeholder="请选择保存所属工厂"><el-option v-for="o in quoteFactories" :key="o.id" :label="o.name" :value="o.id"/></el-select><el-button class="blue-button" :loading="savingQuote" :disabled="!!savedQuoteNo" @click="saveQuote">保存报价单</el-button><span v-if="savedQuoteNo">已保存：{{savedQuoteNo}}</span><el-button v-if="savedQuoteId" class="blue-button" :loading="downloadingQuote" @click="downloadQuote">下载报价单</el-button></div></div>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,onMounted,watch} from 'vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import {ElMessage} from 'element-plus'
import {productDimensions} from '../master-config'
import {productVolume,supportedVolumeCategories} from '@/utils/product-volume'
type Item={key:number;category:string;quantity:string;dimensions:Record<string,string>}
type Result={volume:string;unitPrice:string;totalPrice:string}
let key=0
const blank=():Item=>({key:++key,category:'',quantity:'',dimensions:{}})
const items=ref<Item[]>([blank()]),pi=ref(''),calculating=ref(false),results=ref<Result[]>([])
const user=useUserStore(),canSave=computed(()=>user.permissions.includes('*:*:*')||user.permissions.includes('metal:quotation:add')),isAdmin=computed(()=>String(user.id)==='1'),quoteFactories=ref<{id:string,name:string}[]>([]),quoteFactory=ref(''),quoteRequestId=ref(crypto.randomUUID()),savingQuote=ref(false),savedQuoteNo=ref(''),savedQuoteId=ref(''),downloadingQuote=ref(false)
const loadingRule=ref(true),ruleLoadFailed=ref(false)
const calculationProblems=computed(()=>{
 if(loadingRule.value)return ['正在加载报价配置']
 if(ruleLoadFailed.value||!pi.value)return ['报价配置加载失败，请重新加载']
 const problems:string[]=[]
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
const canCalculate=computed(()=>!!items.value.length&&!calculationProblems.value.length)
const totalPrice=computed(()=>{const sum=results.value.reduce((a,r)=>a+BigInt(r.totalPrice.replace('.','')),0n).toString().padStart(7,'0');return sum.slice(0,-6)+'.'+sum.slice(-6)})
watch(()=>JSON.stringify(items.value),()=>{results.value=[];savedQuoteNo.value='';savedQuoteId.value='';quoteRequestId.value=crypto.randomUUID()})
function categoryChanged(item:Item){item.dimensions=Object.fromEntries((productDimensions[item.category]??[]).map(d=>[d.key,'']))}
function addGoods(){items.value.push(blank())}
async function calculate(){if(calculating.value||!canCalculate.value)return;calculating.value=true;results.value=[];try{const responses=await Promise.all(items.value.map(item=>request({url:'/metal/quotation/calculate',method:'post',headers:{repeatSubmit:false},data:{category:item.category,dimensions:{...item.dimensions},quantity:Number(item.quantity)}})));results.value=responses.map(r=>r.data)}catch{results.value=[]}finally{calculating.value=false}}
async function loadRule(){loadingRule.value=true;ruleLoadFailed.value=false;try{pi.value=(await request({url:'/metal/quotation/context',method:'get'})).data.rule.piValue}catch{ruleLoadFailed.value=true}finally{loadingRule.value=false}}
async function saveQuote(){if(!quoteFactory.value){ElMessage.error('请选择所属工厂');return}savingQuote.value=true;try{const response=await request({url:'/metal/quotation/save',method:'post',data:{requestId:quoteRequestId.value,factoryId:quoteFactory.value,items:items.value.map(item=>({category:item.category,quantity:Number(item.quantity),dimensions:{...item.dimensions}}))}});savedQuoteNo.value=response.data.quoteNo;savedQuoteId.value=String(response.data.id);results.value=response.data.items;ElMessage.success('报价单已保存')}finally{savingQuote.value=false}}
async function downloadQuote(){if(!savedQuoteId.value||downloadingQuote.value)return;const quoteId=savedQuoteId.value,filename=savedQuoteNo.value+'.xlsx';downloadingQuote.value=true;try{const blob=await request({url:'/metal/quotation/orders/'+quoteId+'/download',method:'get',responseType:'blob'});const file=blob as unknown as Blob;if(file.type.includes('json')){const error=JSON.parse(await file.text());ElMessage.error(error.msg||'报价单下载失败');return}const url=URL.createObjectURL(file),a=document.createElement('a');a.href=url;a.download=filename;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000)}finally{downloadingQuote.value=false}}
watch(quoteFactory,()=>{quoteRequestId.value=crypto.randomUUID();savedQuoteNo.value='';savedQuoteId.value=''})
onMounted(async()=>{await loadRule();if(canSave.value){quoteFactories.value=(await request({url:'/metal/quotation/factories'})).data;if(!isAdmin.value||quoteFactories.value.length===1)quoteFactory.value=quoteFactories.value[0]?.id||''}})
</script>
<style scoped>
.quote-page{background:#f8fbff;min-height:calc(var(--design-height,100vh) - 82px);padding:25px 30px}.quotation-form{width:550px}.goods-item{margin-bottom:20px}.quotation-form :deep(.el-form-item){margin-bottom:18px}.goods-item>.el-form-item :deep(.el-input),.goods-item>.el-form-item :deep(.el-select){width:280px}.spec-title{margin:28px 0 18px;font-size:14px}.spec-fields{margin-left:20px}.spec-fields :deep(.el-input){width:150px}.spec-fields :deep(.el-form-item){margin-bottom:8px}.dimension-unit{margin-left:10px}.blue-button{background:#577bef;border-color:#577bef;color:#fff}.add-goods{margin-top:6px}.calculate-row{display:flex;justify-content:flex-end;margin-top:40px}.calculate-row .el-button{width:200px}.formula-note{color:#8a9099;font-size:14px}.quote-results{margin-top:30px;line-height:2}
</style>
<style scoped>.quotation-form :deep(.el-form-item__label){white-space:nowrap;flex-shrink:0}.calculation-hints{margin-top:12px;color:#c45656;font-size:14px;line-height:1.8}</style>
<style scoped>.save-quote{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:18px}.save-quote .el-select{width:260px}.save-quote small{color:#909399}</style>