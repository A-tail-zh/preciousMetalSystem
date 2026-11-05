<template>
 <div class="customer-snapshot"><template v-if="factoryOwned">工厂自有</template><dl v-else><div v-for="field in fields" :key="field.key"><dt>{{field.label}}</dt><dd><el-input v-if="inputs" :model-value="snapshot?.[field.key]??''" :aria-label="field.label" readonly placeholder="未填写"/><template v-else>{{snapshot?.[field.key]??(snapshot&&field.key==='department'?'—':'历史未记录')}}</template></dd></div></dl></div>
</template>
<script setup lang="ts">
defineProps<{snapshot?:Record<string,any>|null;factoryOwned?:boolean;inputs?:boolean}>()
const fields=[{key:'name',label:'客户名称'},{key:'companyName',label:'客户公司'},{key:'address',label:'地址'},{key:'contactName',label:'联络人'},{key:'contactPhone',label:'电话'},{key:'department',label:'部门'}]
</script>
<style scoped>.customer-snapshot{width:100%}.customer-snapshot dl{margin:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px 16px}.customer-snapshot dl>div{min-width:0}.customer-snapshot dt{color:var(--el-text-color-secondary)}.customer-snapshot dd{margin:0;overflow-wrap:anywhere}@media(max-width:600px){.customer-snapshot dl{grid-template-columns:minmax(0,1fr)}}</style>
