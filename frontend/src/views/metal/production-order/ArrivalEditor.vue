<template>
  <el-form-item label="客户来料明细" class="arrival-details">
    <div class="arrival-content" v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button v-if="error" @click="load">重试</el-button>
      <div v-for="(row, index) in modelValue" :key="index" class="arrival-group">
        <div class="arrival-heading">
          <span>来料 {{ index + 1 }}</span>
          <el-button v-if="!disabled" link type="danger" @click="remove(index)">移除</el-button>
        </div>
        <div class="arrival-grid">
          <el-form-item label="物料" required>
            <el-select v-model="row.materialId" :aria-label="`来料物料 ${index + 1}`" :disabled="disabled" filterable placeholder="请选择物料" @change="changed(row)">
              <el-option v-for="m in materials" :key="m.id" :label="`${m.code||''} / ${m.name||m.materialType||''}`" :value="m.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="仓库" required>
            <el-select v-model="row.warehouseId" :aria-label="`来料仓库 ${index + 1}`" :disabled="disabled" placeholder="请选择仓库">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="指定仓管复验" required>
            <el-select v-model="row.reviewerId" :aria-label="`来料复验人员 ${index + 1}`" :disabled="disabled" placeholder="请选择复验人员">
              <el-option v-for="p in people" :key="p.id" :label="p.name||p.nickName" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="净重（g）" required>
            <el-input v-model="row.netWeight" :aria-label="`来料净重 ${index + 1}`" :disabled="disabled" placeholder="请输入净重" inputmode="decimal" @blur="normalize(row)" />
          </el-form-item>
          <el-form-item label="件数" :required="mode(row)==='PIECE'">
            <el-input v-if="mode(row)==='PIECE'" v-model="row.quantity" :aria-label="`来料件数 ${index + 1}`" :disabled="disabled" placeholder="请输入件数" inputmode="numeric" />
            <el-input v-else :aria-label="`来料件数 ${index + 1}`" :model-value="!row.materialId?'请先选择物料':mode(row)==='WEIGHT'?'按重量':'未配置计量'" readonly />
          </el-form-item>
          <el-form-item label="纯度（%）">
            <el-input v-model="row.purity" :aria-label="`来料纯度 ${index + 1}`" :disabled="disabled" placeholder="请输入纯度" inputmode="decimal" />
          </el-form-item>
        </div>
      </div>
      <el-button v-if="!disabled" type="primary" plain :disabled="loading||!!error" @click="add">＋ 添加来料</el-button>
    </div>
  </el-form-item>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue';import request from '@/utils/request';import useUserStore from '@/store/modules/user';import {roundDecimal,optionalPurityValid,purityError} from '@/utils/business-decimal';import {message,type Row} from '../storage-live/client'
const props=defineProps<{modelValue:any[];factoryId:string;disabled?:boolean}>(),emit=defineEmits<{'update:modelValue':[any[]];total:[string]}>(),user=useUserStore(),materials=ref<Row[]>([]),warehouses=ref<Row[]>([]),people=ref<Row[]>([]),loading=ref(false),error=ref('');let epoch=0
const mode=(row:any)=>materials.value.find(m=>m.id===row.materialId)?.measurementMode
const total=computed(()=>{let cents=0n;for(const row of props.modelValue){if(!/^\d{1,14}(\.\d{1,2})?$/.test(String(row.netWeight||'')))continue;const [whole,fraction='']=String(row.netWeight).split('.');cents+=BigInt(whole)*100n+BigInt((fraction+'00').slice(0,2))}return `${cents/100n}.${String(cents%100n).padStart(2,'0')}`})
watch(total,value=>emit('total',value))
async function load(){if(!props.factoryId)return;const id=++epoch,factory=props.factoryId,account=user.token;loading.value=true;error.value='';try{const r:any=await request({url:'/metal/storage-orders/options',params:{factoryId:factory,type:'CUSTOMER_IN'},quiet:true});if(id===epoch&&factory===props.factoryId&&account===user.token){materials.value=r.data.materials||[];warehouses.value=r.data.warehouses||[];people.value=(r.data.people||[]).filter((p:any)=>p.canAccept!==false)}}catch(e){if(id===epoch)error.value=message(e)}finally{if(id===epoch)loading.value=false}}
function changed(row:any){const material=materials.value.find(m=>m.id===row.materialId);row.form=material?.materialForm||material?.form||'';row.quantity=null}
function normalize(row:any){if(/^\d{1,14}(\.\d{1,2})?$/.test(row.netWeight))row.netWeight=roundDecimal(row.netWeight)}
function add(){emit('update:modelValue',[...props.modelValue,{materialId:'',warehouseId:'',reviewerId:'',netWeight:'',quantity:null,purity:'',form:''}])}function remove(index:number){emit('update:modelValue',props.modelValue.filter((_,i)=>i!==index))}
function validate(){if(error.value||loading.value)return '来料资料尚未加载成功';if(!props.modelValue.length)return '请添加客户来料明细';for(const r of props.modelValue){if(!materials.value.some(m=>m.id===r.materialId)||!warehouses.value.some(w=>w.id===r.warehouseId)||!people.value.some(p=>p.id===r.reviewerId))return '来料物料、仓库和复验人员必须为本厂有效资料';if(!mode(r))return '来料物料尚未配置计量方式';if(!/^\d{1,14}(\.\d{1,2})?$/.test(r.netWeight)||Number(r.netWeight)<=0)return '来料净重须大于0且最多两位小数';if(mode(r)==='PIECE'&&!/^[1-9]\d*$/.test(String(r.quantity)))return '按件来料须填写正整数件数';if(!optionalPurityValid(r.purity))return purityError}return ''}
watch(()=>props.factoryId,()=>{epoch++;void load()},{immediate:true});defineExpose({validate,load})
</script>
<style scoped>
.arrival-content { width: 100%; min-width: 0; }
.arrival-group { border: 1px solid #e2e8f0; padding: 14px; border-radius: 6px; margin-bottom: 14px; }
.arrival-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; color: #606266; }
.arrival-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 28px; }
.arrival-grid :deep(.el-form-item) { margin-bottom: 18px; }
.el-select { width: 100%; }
:deep(.el-input__inner) { text-align: left; }
@media (max-width: 650px) { .arrival-grid { grid-template-columns: minmax(0, 1fr); } .arrival-group { padding: 8px; } }
</style>
