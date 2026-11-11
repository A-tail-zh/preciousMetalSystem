<template>
 <el-table-column label="物料 / 产品" min-width="180"><template #default="{row}">{{categoryNames[row.category]||'类别不可用'}} · {{row.code||'编码不可用'}} / {{row.name||'物料资料不可用'}}</template></el-table-column>
 <el-table-column label="库存批次" min-width="135"><template #default="{row}">{{row.batchNo||'批次资料不可用'}}</template></el-table-column>
 <el-table-column label="料权归属" min-width="140"><template #default="{row}">{{String(row.ownerId)==='0'?'工厂自有':row.ownerName||'客户资料不可用'}}</template></el-table-column>
 <el-table-column label="净重增减（g）" min-width="135"><template #default="{row}">{{signedAdjustment(row.deltaWeight)}}</template></el-table-column>
 <el-table-column v-if="lines.some(r=>r.measurementMode==='PIECE')" label="件数增减" min-width="110"><template #default="{row}">{{row.measurementMode==='PIECE'?signedAdjustment(row.deltaQuantity,true):'—'}}</template></el-table-column>
 <el-table-column label="调整原因" min-width="170"><template #default="{row}">{{row.reason||row.remark||'—'}}</template></el-table-column>
</template>
<script setup lang="ts">
import {signedAdjustment} from './adjustment-display'
defineProps<{lines:Record<string,any>[]}>()
const categoryNames:Record<string,string>={RAW:'原料',SEMI:'半成品',PRODUCT:'成品'}
</script>
