<template>
 <el-input v-bind="$attrs" :model-value="draft" @update:model-value="edit" @focus="focus" @blur="blur">
  <template v-for="(_,name) in $slots" #[name]="slotProps"><slot :name="name" v-bind="slotProps||{}"/></template>
 </el-input>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import {roundDecimal} from '@/utils/business-decimal'
defineOptions({inheritAttrs:false})
const props=withDefaults(defineProps<{modelValue?:string|number|null;allowNegative?:boolean;max?:number;integer?:number;fraction?:number}>(),{allowNegative:false,integer:14,fraction:6})
const emit=defineEmits<{ 'update:modelValue':[string];input:[string];focus:[FocusEvent];blur:[FocusEvent] }>()
const text=(value:unknown)=>value==null?'':roundDecimal(value)
const draft=ref(text(props.modelValue));let focused=false,dirty=false
watch(()=>props.modelValue,value=>{if(!focused)draft.value=text(value)})
function focus(event:FocusEvent){focused=true;dirty=false;emit('focus',event)}
function edit(value:string){dirty=true;draft.value=value;emit('update:modelValue',value);emit('input',value)}
function normalize(value:string){
 const match=/^([+-]?)(\d+)(?:\.(\d+))?$/.exec(value)
 // Keep invalid original input for form/API validation; rounding must not hide a range error.
 if(!match||match[2].length>props.integer||(match[3]?.length??0)>props.fraction)return value
 if(!props.allowNegative&&match[1]==='+')return value
 if(!props.allowNegative&&match[1]==='-'&&/[1-9]/.test(match[2]+(match[3]??'')))return value
 if(props.max!=null&&Number(value)>props.max)return value
 return roundDecimal(value)
}
function blur(event:FocusEvent){focused=false;if(dirty){const rounded=normalize(draft.value);draft.value=rounded;emit('update:modelValue',rounded);emit('input',rounded)}else draft.value=text(props.modelValue);dirty=false;emit('blur',event)}
</script>
