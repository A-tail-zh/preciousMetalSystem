<template>
 <div class="allocation-editor">
  <p v-if="!rows.length">当前没有{{kind==='WIP'?'在制来源':'可分配库存'}}。</p>
  <el-table v-else :data="rows" border>
   <el-table-column type="expand"><template #default="{row}">
    <div class="composition" v-for="key in ['ownerAllocations','sourceAllocations']" :key="key">
     <p>{{key==='ownerAllocations'?'当前料权':'材料来源'}}：{{(row[key]||[]).length===1?'唯一明细，随总量承接':'请分别确认每项实际分配'}}</p>
     <el-table v-if="(row[key]||[]).length>1" :data="row[key]" border>
      <el-table-column label="归属 / 来源"><template #default="{row:part}">{{part.ownerName||part.currentOwnerName||part.ownerId||part.currentOwnerId}} · {{part.sourceKind||part.sourceAllocationId||''}}</template></el-table-column>
      <el-table-column label="分配重量（g）"><template #default="{row:part,$index}"><el-input v-model="part.netWeight" :aria-label="`${label} ${key==='ownerAllocations'?'料权':'来源'}${$index+1}重量`" :disabled="disabled"/></template></el-table-column>
      <el-table-column label="分配件数"><template #default="{row:part,$index}"><el-input v-if="row.measurementMode!=='WEIGHT'" v-model="part.quantity" :aria-label="`${label} ${key==='ownerAllocations'?'料权':'来源'}${$index+1}件数`" :disabled="disabled"/><span v-else>计重</span></template></el-table-column>
     </el-table>
    </div>
   </template></el-table-column>
   <el-table-column label="实际来源" min-width="180"><template #default="{row}">{{kind==='STOCK'?`库存 ${row.stockId} / 份额 ${row.stockSliceId}`:`在制来源 ${row.sourceAllocationId||row.id}`}}<div>可分配 {{row.balanceWeight}} g / {{row.measurementMode==='WEIGHT'?'计重':`${row.balanceQuantity}件`}}</div></template></el-table-column>
   <el-table-column label="分配净重（g）" min-width="130"><template #default="{row,$index}"><el-input v-model="row.netWeight" :aria-label="`${label}来源${$index+1}净重`" :disabled="disabled" placeholder="实测或已确认重量"/></template></el-table-column>
   <el-table-column label="实际件数" min-width="120"><template #default="{row,$index}"><el-input v-if="row.measurementMode!=='WEIGHT'" v-model="row.quantity" :aria-label="`${label}来源${$index+1}件数`" :disabled="disabled"/><span v-else>计重，不推算件数</span></template></el-table-column>
   <el-table-column v-if="kind==='WIP'" label="已确认未加工可退（g）" min-width="140"><template #default="{row,$index}"><el-input v-model="row.returnableWeight" :aria-label="`${label}来源${$index+1}可退净重`" :disabled="disabled"/><small v-if="row.availableReturnableWeight!=null">原来源上限 {{row.availableReturnableWeight}} g</small></template></el-table-column>
   <el-table-column v-if="kind==='WIP'" label="已确认未加工可退件数" min-width="140"><template #default="{row,$index}"><el-input v-if="row.measurementMode!=='WEIGHT'" v-model="row.returnableQuantity" :aria-label="`${label}来源${$index+1}可退件数`" :disabled="disabled"/><span v-else>计重</span></template></el-table-column>
  </el-table>
 </div>
</template>
<script setup lang="ts">
import type {Allocation} from './batch-transform'
defineProps<{rows:Allocation[];kind:'STOCK'|'WIP';label:string;disabled?:boolean}>()
</script>
<style scoped>.composition{padding:4px 24px 14px}.composition p{color:var(--el-text-color-secondary)}.allocation-editor{margin:10px 0}</style>
