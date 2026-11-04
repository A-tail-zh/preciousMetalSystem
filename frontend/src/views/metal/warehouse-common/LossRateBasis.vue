<template><div class="loss-basis"><template v-if="groups&&groups.length>1"><p v-for="group in groups" :key="group.lossRateBasis">{{label(group.lossRateBasis)}}：损耗 {{decimalText(group.lossWeight)}} g ÷ {{decimalText(group.lossRateDenominatorWeight)}} g ＝ {{lossRateText(group.lossRatePercent)}}</p></template><template v-else>{{label(basis)}}<span v-if="weight!=null">：{{decimalText(weight)}} g</span></template></div></template>
<script setup lang="ts">
import {decimalText} from '@/utils/business-decimal'
import {lossRateText} from '@/utils/report-metrics'
defineProps<{basis?:string|null;weight?:unknown;groups?:Record<string,any>[]}>()
function label(basis?:string|null){return basis==='OUTBOUND_TOTAL'?'出库总净重口径':basis==='NET_INPUT'?'历史净投入口径':'口径待核对'}
</script>
<style scoped>.loss-basis{font-size:12px;color:var(--el-text-color-secondary);line-height:1.5;overflow-wrap:anywhere}.loss-basis p{margin:4px 0}</style>
