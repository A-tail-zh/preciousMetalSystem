<template>
 <section class="quotation-detail">
  <div class="document-heading"><div><strong>报价单号：{{quote.quoteNo}}</strong><p>所属工厂：{{quote.factoryName}}　保存时间：{{quote.createTime}}</p></div><el-button type="primary" @click="$emit('download',quote)">下载报价单</el-button></div>
  <article v-for="(item,index) in quote.items" :key="index" class="saved-item">
   <h4>商品 {{index+1}}：{{item.category}}　{{item.processingTypeName??''}}　数量：{{item.quantity}}</h4>
   <div class="item-body"><div><dl>
    <div v-for="field in dimensionFields(item)" :key="field.key"><dt>{{field.label}}（{{field.unit??'cm'}}）</dt><dd>{{decimalText(item.dimensions?.[field.key])}}</dd></div>
    <div v-for="field in weights.filter(f=>item[f.key]!=null)" :key="field.key"><dt>{{field.label}}</dt><dd>{{decimalText(item[field.key])}}</dd></div>
    <div v-for="field in prices.filter(f=>item.rule?.[f.key]!=null)" :key="field.key"><dt>{{field.label}}</dt><dd>{{decimalText(item.rule[field.key])}}</dd></div>
    <template v-if="item.volumeRule"><div><dt>π</dt><dd>{{item.volumeRule.piValue}}</dd></div><div><dt>弧底系数</dt><dd>{{decimalText(item.volumeRule.curvedBottomFactor)}}</dd></div></template>
    <div v-for="field in amounts.filter(f=>item[f.key]!=null)" :key="field.key"><dt>{{field.label}}</dt><dd>{{decimalText(item[field.key])}}</dd></div>
   </dl></div>
   <QuotationImage :key="quote.id+':'+index" :quote-id="quote.id" :index="index+1"/></div>
  </article>
  <p class="quote-total" role="status">本次合计金额：{{decimalText(quote.totalPrice)}} 元</p>
 </section>
</template>
<script setup lang="ts">
import QuotationImage from './QuotationImage.vue'
import {decimalText} from '@/utils/business-decimal'
import {productFields} from '../master-config'
defineProps<{quote:Record<string,any>}>();defineEmits<{download:[quote:Record<string,any>]}>()
const dimensionFields=(item:Record<string,any>)=>item.dimensionFields??productFields({...item,calculationVersion:1})
const weights=[{key:'powderWeight',label:'来料粉料重量（g）'},{key:'nonPowderWeight',label:'来料非粉料重量（g）'},{key:'lossWeight',label:'理论损失重量（g）'},{key:'volume',label:'单件体积（cm³）'},{key:'unitWeight',label:'单件理论重量（g）'},{key:'totalWeight',label:'总理论重量（g）'}]
const prices=[{key:'density',label:'密度（g/cm³）'},{key:'powderPrice',label:'粉料单价（元/g）'},{key:'nonPowderPrice',label:'非粉料单价（元/g）'},{key:'shortagePrice',label:'欠料单价（元/g）'},{key:'lossPrice',label:'理论损失单价（元/g）'},{key:'basePrice',label:'历史基础单价（元）'}]
const amounts=[{key:'powderAmount',label:'粉料加工费（元）'},{key:'nonPowderAmount',label:'非粉料加工费（元）'},{key:'productAmount',label:'欠料产品费用（元）'},{key:'lossAmount',label:'损失费用（元）'},{key:'unitPrice',label:'历史单件报价（元）'},{key:'totalPrice',label:'商品金额（元）'}]
</script>
<style scoped>.document-heading{display:flex;justify-content:space-between;gap:16px;align-items:start;flex-wrap:wrap}.document-heading p{color:#606266;font-size:13px;line-height:1.6}.saved-item{border:1px solid var(--business-border);border-radius:8px;background:white;padding:20px;margin-top:16px}.saved-item h4{margin:0 0 16px;line-height:1.6;overflow-wrap:anywhere}.item-body{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 20px;margin:0}dt{font-size:13px;color:#606266;line-height:1.5}dd{margin:4px 0 0;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.quote-total{font-size:18px;font-weight:600;margin:20px 0}.quotation-detail{min-width:0}@media(max-width:900px){.item-body{grid-template-columns:minmax(0,1fr)}}@media(max-width:480px){.saved-item{padding:14px}dl{gap:12px}.quote-total{font-size:16px}}</style>
