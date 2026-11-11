<template>
 <section class="master-page" :class="{'material-page':kind==='material','line-page':kind==='line'}">
  <div class="master-toolbar">
<BusinessFilters @refresh-process-types="processTypeControl?.refresh()" ref="filtersRef" :kind="kind" :process-type-options="typeOptions" :allow-export="kind==='staff'&&can('system:user:export')" :exporting="exporting" @factory="listFactory=$event" @apply="applyFilters" @export="exportRows"><template #actions>
<el-button v-if="actionPermission.hasAny([`${config.permission}:add`])"  class="add-button list-action list-action--create" :disabled="['product','line','material','process'].includes(kind)&&!listFactory" @click="openAdd">添加</el-button>
   <el-button v-if="actionPermission.hasAny([`${config.permission}:edit`])"  class="edit-button list-action list-action--edit" :disabled="selected.length!==1" @click="openRecord(selected[0])">编辑</el-button>
   <el-button v-if="actionPermission.hasAny([`${config.permission}:remove`])"  class="delete-button list-action list-action--delete" :disabled="!selected.length||deleting" @click="remove(selected)">删除</el-button>
</template><template v-if="kind==='process'" #trailing-actions><ProcessTypeControl ref="processTypeControl" :factory-id="listFactory" @loaded="typeOptions=$event" @removed="processTypeRemoved"/></template></BusinessFilters>
    </div>
  <div v-if="editLoading" role="status">正在加载编辑资料… <el-button link @click="cancelEdit">取消加载</el-button></div>
  <div v-if="editError" role="alert">编辑资料加载失败 <el-button link @click="retryEdit()">重试</el-button></div>
  <el-table ref="tableRef" v-loading="loading" :data="rows" border row-key="id" :style="{width:fullWidthTable?'100%':`min(100%, ${config.width}px)`}" class="master-table" @selection-change="selected=$event">
   <el-table-column v-if="showActions" type="selection" :width="fullWidthTable?54:36" align="center"/>
   <el-table-column v-for="column in config.columns" :key="column.key" :label="column.label" :width="fullWidthTable?(column.key==='createTime'?168:undefined):column.width" :min-width="fullWidthTable?Math.max(column.width,column.key==='purity'?80:column.key==='volume'?96:0):undefined" align="center">
    <template #default="{row,$index}">
     <template v-if="kind==='process'&&column.key==='id'">{{(query.pageNum-1)*query.pageSize+$index+1}}</template>
     <span v-else-if="column.key==='status'&&!can(`${config.permission}:edit`)">{{row.status==='0'?'正常':'停用'}}</span><el-switch v-else-if="column.key==='status'" :model-value="row.status" active-value="0" inactive-value="1" :disabled="!can(`${config.permission}:edit`)||row.id==='1'||statusBusy===row.id" @change="changeStatus(row,$event)"/>
     <template v-else-if="kind==='product'&&column.key==='productType'">{{row.productType}}{{(row.calculationVersion??1)===1?'（旧版）':''}}</template>
     <template v-else-if="column.key==='productDisplay'">{{row.name}}<br>{{row.category}}</template>
     <template v-else-if="kind==='line' && column.key==='factoryName' && !row.factoryId">未分配工厂</template>
     <template v-else-if="kind==='material'&&column.key==='materialForm'">{{({POWDER:'粉状',UNTREATED:'未处理料',METAL:'金属料'} as Record<string,string>)[row.materialForm||'']||'—'}}</template>
     <template v-else-if="column.key.endsWith('Time')">{{displayDateTime(row[column.key])}}</template>
     <template v-else-if="Array.isArray(row[column.key])"><div v-for="(value,index) in row[column.key]" :key="index">{{value}}</div></template>
     <template v-else>{{businessValue(row[column.key],column.key)}}</template>
    </template>
   </el-table-column>
   <el-table-column v-if="showActions" label="操作" :width="fullWidthTable?(kind==='staff'?200:163):undefined" :min-width="kind==='staff'?200:kind==='product'?118:100" align="center">
    <template #default="{row}">
     <el-button v-if="(['product','material'].includes(kind)) && actionPermission.hasAny([`${config.permission}:query`])"  class="add-button row-button list-action list-action--primary list-action--row" @click="openRecord(row,true)">查看</el-button>
     <el-button v-if="(kind==='staff') && actionPermission.hasAny(['system:user:resetPwd'])"  class="add-button row-button list-action list-action--primary list-action--row" @click="openPassword(row)">重置密码</el-button>
     <el-button v-if="actionPermission.hasAny([`${config.permission}:edit`])"  class="edit-button row-button list-action list-action--edit list-action--row" :disabled="kind==='role'&&row.id==='1'" @click="openRecord(row)">编辑</el-button>
     <el-button v-if="actionPermission.hasAny([`${config.permission}:remove`])"  class="delete-button row-button list-action list-action--delete list-action--row" :disabled="deleting||(['staff','role'].includes(kind)&&row.id==='1')" @click="remove([row])">删除</el-button>
    </template>
   </el-table-column>
  </el-table>
  <div v-if="(!['product','line','material','process'].includes(kind)||listFactory)&&(loadFailed||refreshFailed)" class="load-error">列表更新失败，当前显示可能过期 <el-button link type="primary" @click="load">重试</el-button></div>
  <div class="master-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[16,32,64]" layout="total, sizes, prev, pager, next, jumper" @size-change="sizeChanged" @current-change="load"/></div>
  <el-dialog :class="{'base-info-form-dialog':['product','line','material','process'].includes(kind)}" v-model="dialogOpen" :title="`${readonly?'查看':form.id?'编辑':'添加'}${config.title.replace('管理','')}`" :width="['product','line'].includes(kind)?'min(900px,94vw)':'700px'" :top="kind==='line'?'5vh':undefined" :close-on-click-modal="false" :close-on-press-escape="!saving&&!uncertain" :show-close="!saving&&!uncertain" >
   <el-form ref="formRef" :model="form" :rules="rules" scroll-to-error label-width="160px" :disabled="readonly||saving||rolesLoading||uncertain||needsReload">
    <template v-if="['line','product','material'].includes(kind)">
     <el-form-item :label="kind==='line'?'产线编号':kind==='product'?'产品编号':'物料编码'" prop="code"><el-input v-model="form.code" maxlength="64"/></el-form-item>
    </template>
    <el-form-item v-if="['line','product','process'].includes(kind)" :label="kind==='line'?'产线名称':kind==='product'?'产品名称':'工序名称'" prop="name"><el-input v-model="form.name" maxlength="100"/></el-form-item>
    <el-form-item v-if="['line','staff','role','product','material','process'].includes(kind)" label="所属工厂" prop="factoryId"><el-select :class="{'line-factory-select':kind==='line'}" @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.factoryId" :clearable="isAdmin" placeholder="请选择所属工厂" :disabled="saving||uncertain||!isAdmin||(['product','material','process'].includes(kind)&&!!form.id)||(['staff','role'].includes(kind)&&form.id==='1')" @change="factoryChanged" style="width:100%"><el-option v-for="o in factories" :key="o.id" :value="o.id" :label="o.name"/></el-select></el-form-item>
    <el-form-item v-if="kind==='line'" label="关联产品"><el-select class="line-product-select" @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.productIds" multiple filterable style="width:100%"><el-option v-for="o in products" :key="o.id" :value="o.id" :label="o.name"/></el-select></el-form-item>
    <el-form-item v-if="kind==='material'" label="物料名称" prop="name"><el-input v-model="form.name" maxlength="100" placeholder="请输入物料名称"/></el-form-item>
    <el-form-item v-if="kind==='material'" label="金属类型" prop="metalTypeId" :rules="[requiredRule('金属类型',true,true)]"><MetalTypeSelect v-if="dialogOpen" :key="editEpoch" v-model="form.metalTypeId" :factory-id="form.factoryId" :disabled="readonly||saving||form.classificationLocked" @changed="metalChanged" @mutating="metalMutation=$event"/></el-form-item>
    <CatalogConfiguration v-if="kind==='material'" kind="material" :form="form" :process-options="[]" :materials="[]" :readonly="readonly||saving||uncertain||needsReload"/>
    <template v-if="kind==='process'">
     <el-form-item label="工序类型" prop="processType"><div class="material-type-control"><el-select :loading="processTypesLoading" @visible-change="v=>{resetSelectScroll(v);if(v)void refreshProcessTypes()}" fit-input-width :teleported="false" v-model="form.processType"><el-option v-for="option in editTypeOptions" :key="option.id" :label="option.name" :value="Number(option.id)"/></el-select><ProcessTypeControl v-if="dialogOpen&&!readonly" inline :key="editEpoch" :factory-id="form.factoryId" @loaded="editTypeOptions=$event" @changed="editorProcessTypesChanged" @open="typeMaintenanceOpen=$event"/></div></el-form-item>
     <el-form-item label="工序描述"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000"/></el-form-item>
    </template>
    <el-form-item v-if="kind==='material'" label="规格型号"><el-input v-model="form.specification" maxlength="200"/></el-form-item>
    <p v-if="kind==='material'&&form.classificationLocked&&!readonly" class="process-hint">物料已有业务引用，名称可修改；金属类型、材料形态和计量方式受引用保护。</p>
    <template v-if="kind==='product'">
     <el-form-item v-if="readonly" label="规格型号"><el-input class="product-specification-input" :model-value="form.specification??automaticSpecification" type="textarea" :autosize="{minRows:1}" readonly/></el-form-item>
     <el-form-item label="金属类型" prop="metalTypeId" :rules="[requiredRule('金属类型',true,true)]"><MetalTypeSelect v-if="dialogOpen" :key="editEpoch" v-model="form.metalTypeId" :factory-id="form.factoryId" :disabled="readonly||saving||legacyProduct" @loaded="metalOptions=$event" @changed="metalChanged" @mutating="metalMutation=$event"/></el-form-item>
     <el-form-item label="产品类型" prop="productType" :rules="[requiredRule('产品类型',true,true)]" :error="catalogTypeError||(!catalogTypesBusy&&catalogTypesLoaded&&form.productType&&!catalogTypeAllowed?'所选类型已失效，请重新选择':'')">
      <el-select v-model="productTypeChoice" style="width:100%" :disabled="!form.factoryId" :loading="catalogTypesBusy" :placeholder="form.factoryId?'请选择产品类型':'请先选择所属工厂'" no-data-text="本厂暂无产品类型，请先在类型管理中添加" @visible-change="v=>{resetSelectScroll(v);if(v)void loadCatalogTypes()}" @change="categoryChanged">
       <el-option v-if="legacyProduct" :value="'legacy:'+form.productType" :label="form.productType+'（旧版）'" disabled/>
       <el-option v-for="option in catalogTypeOptions" :key="option.id" :value="option.name" :label="option.name"/>
       <el-option v-if="!legacyProduct&&form.productType&&!catalogTypeOptions.some(o=>o.name===form.productType)" :value="form.productType" :label="form.productType+(readonly?'':form.id&&form.productType===originalCatalogType?'（历史类型）':'（已失效，请重新选择）')" disabled/>
      </el-select>
      <el-button v-if="catalogTypeError" link type="primary" :disabled="catalogTypesBusy" @click="loadCatalogTypes">重试加载产品类型</el-button>
     </el-form-item>
     <p v-if="legacyProduct" class="process-hint">旧版产品保留原字段及计算值。修改几何信息请明确选择新版产品类型并补齐字段；有业务引用时仍受保护。</p>
     <el-form-item :rules="[requiredRule(`${dimension.label}（${dimension.unit??'cm'}）`,true,false),{validator:positiveDecimal,trigger:'blur'}]" v-for="dimension in dimensionFields" :key="dimension.key" :label="`${dimension.label}（${dimension.unit??'cm'}）`" :prop="`dimensions.${dimension.key}`"><BusinessDecimalInput v-model="form.dimensions![dimension.key]" :disabled="legacyProduct" inputmode="decimal"/></el-form-item>
     <el-form-item label="产品体积（cm³）" prop="volume"><el-input :model-value="readonly?decimalText(form.volume):automaticVolume==null?null:decimalText(automaticVolume)" readonly placeholder="输入完整尺寸后自动计算"/></el-form-item>
     <el-form-item label="纯度（%）" prop="purity"><BusinessDecimalInput v-model="form.purity" :max="100" inputmode="decimal"/></el-form-item>
     <el-form-item label="理论成品净重（g）" prop="weight"><el-input :model-value="readonly?decimalText(form.weight):automaticWeight==null?null:decimalText(automaticWeight)" readonly placeholder="体积 × 所选金属密度"/></el-form-item>
     <div class="calculation-feedback" role="status" aria-live="polite"><span v-if="previewError" role="alert">{{previewError}}</span><span v-else-if="previewBusy">正在计算体积与净重…</span></div>
     <CatalogConfiguration kind="product" :form="form" :process-options="processes" :materials="routeMaterials" :readonly="readonly||saving||uncertain||needsReload"/>
    </template>
    <template v-if="kind==='staff'">
     <el-form-item label="手机号" prop="phonenumber"><el-input v-model="form.phonenumber" maxlength="11"/></el-form-item>
     <el-form-item label="姓名" prop="nickName"><el-input v-model="form.nickName" maxlength="100"/></el-form-item>
     <el-form-item label="角色" prop="roleIds"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.roleIds" multiple :disabled="form.id==='1'" style="width:100%"><el-option v-for="o in roles" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
     <el-form-item v-if="!form.id" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password"/></el-form-item>
    </template>
    <template v-if="kind==='role'">
     <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" maxlength="100"/></el-form-item>
     <el-form-item label="显示顺序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" :precision="0"/></el-form-item>
    </template>
    <el-form-item v-if="['staff','role'].includes(kind)" label="状态"><el-switch v-model="form.status" active-value="0" inactive-value="1" :disabled="form.id==='1'"/></el-form-item>
    <el-form-item v-if="kind==='role'" label="菜单和按钮权限"><div class="role-permission-tree" role="region" aria-label="菜单和按钮权限列表" tabindex="0"><el-tree ref="treeRef" :data="menuTree" node-key="id" :props="{label:'name',children:'children'}" show-checkbox check-strictly default-expand-all @check="checkMenu"/></div></el-form-item>
   </el-form>
   <el-alert v-if="needsReload" title="资料已变化，请关闭后重新加载并核对，当前输入已保留" type="warning" :closable="false"/><el-alert v-if="uncertain" title="提交结果待确认，当前输入已保留；请先核实结果，避免重复新增" type="warning" :closable="false"/><el-button v-if="uncertain" :disabled="saving" @click="acknowledgeUnknown">已核实结果，关闭草稿</el-button><template #footer><el-button :disabled="saving||uncertain" @click="dialogOpen=false">{{readonly?'关闭':'取消'}}</el-button><el-button v-if="!readonly" type="primary" :loading="saving" :disabled="needsReload||uncertain||(kind==='product'&&!catalogTypeReady)" @click="submit">确定</el-button></template>
  </el-dialog>
  <el-dialog v-model="passwordOpen" title="重置密码" width="500px" :close-on-click-modal="false" :show-close="!saving" :close-on-press-escape="!saving"><el-alert v-if="needsReload" title="资料已变化，请关闭后重新加载并核对" type="warning" :closable="false"/><el-input v-model="newPassword" type="password" show-password placeholder="请输入至少8位的新密码" autocomplete="new-password"/><template #footer><el-button :disabled="saving" @click="passwordOpen=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="needsReload||uncertain" @click="resetPassword">确定</el-button></template></el-dialog>
 </section>
</template>
<script setup lang="ts">
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'

import {requiredRule} from '@/utils/input-validation'
import {textRules,decimalError,fieldRule,applyFieldErrors,passwordError} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {getToken} from '@/utils/auth'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
const actionPermission = useActionPermission()
import {displayDateTime} from '@/utils/display-date-time'
import { resetSelectScroll } from '@/utils/reset-select-scroll'
import {computed,reactive,ref,nextTick,onMounted,watch,onBeforeUnmount} from 'vue'
import request,{download} from '@/utils/request'
import BusinessFilters from '@/components/BusinessFilters/index.vue'
import {productWeight} from '@/utils/product-volume'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {FormInstance,FormRules,TreeInstance,TableInstance} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {masterList,masterDetail,masterSave,masterRemove,masterAction,masterOptions} from '@/api/metal/master'
import type {MasterRow,Option} from '@/api/metal/master'
import {masterConfigs,productFields,processTypes} from './master-config'
import type {MasterKind} from './master-config'
import MetalTypeSelect from './MetalTypeSelect.vue'
import {businessValue,decimalText} from '@/utils/business-decimal'
import CatalogConfiguration from './CatalogConfiguration.vue'
import ProcessTypeControl from './ProcessTypeControl.vue'
const processTypeControl=ref<InstanceType<typeof ProcessTypeControl>>()
const typeOptions=ref<Option[]>([]),editTypeOptions=ref<Option[]>([]),listFactory=ref('')
const props=defineProps<{kind:MasterKind}>(),kind=props.kind,config=masterConfigs[kind]
const fullWidthTable=['line','product','material','process','staff','role'].includes(kind)
const user=useUserStore(),isAdmin=computed(()=>String(user.id)==='1')
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()
const can=(permission:string)=>sessionCurrent()&&actionPermission.has(permission)
const canRead=()=>can(config.permission+':list')
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const showActions=computed(()=>can(config.permission+':edit')||can(config.permission+':remove')||(['product','material'].includes(kind)&&can(config.permission+':query'))||(kind==='staff'&&can('system:user:resetPwd')))
const appliedFilters=ref<Record<string,string>>({}),exporting=ref(false)
const filtersRef=ref<InstanceType<typeof BusinessFilters>>()
function processTypeRemoved(id:string){
 filtersRef.value?.removeProcessType(id)
 editTypeOptions.value=editTypeOptions.value.filter(o=>o.id!==id)
 if(String(form.processType)===id)form.processType=undefined
}
function applyFilters(filters:Record<string,string>){if(['product','line','material','process'].includes(kind)){rows.value=[];total.value=0}appliedFilters.value=filters;query.pageNum=1;selected.value=[];tableRef.value?.clearSelection();load()}
async function exportRows(filters:Record<string,string>){if(exporting.value)return;const account=identity(),round=operationEpoch,current=()=>!disposed&&account===identity()&&round===operationEpoch&&can('system:user:export');if(!current())return;exporting.value=true;try{await download('/system/user/export',filters,'人员.xlsx',{timeout:0,isContextCurrent:current})}finally{if(current())exporting.value=false}}
const rows=ref<MasterRow[]>([]),selected=ref<MasterRow[]>([]),total=ref(0),loading=ref(false),loadFailed=ref(false),saving=ref(false),deleting=ref(false),statusBusy=ref('')
const query=reactive({pageNum:1,pageSize:16}),dialogOpen=ref(false),readonly=ref(false),passwordOpen=ref(false),newPassword=ref(''),passwordRow=ref<MasterRow>()
const formRef=ref<FormInstance>(),treeRef=ref<TreeInstance>(),tableRef=ref<TableInstance>()
const factories=ref<Option[]>([]),products=ref<Option[]>([]),processes=ref<Option[]>([]),roles=ref<Option[]>([]),menus=ref<Option[]>([]),routeMaterials=ref<Record<string,any>[]>([])
const blank=():Partial<MasterRow>=>({name:'',code:'',specification:'',description:'',dimensions:{},processes:[],storageConfiguration:kind==='product'?{steps:[]}:undefined,productIds:[],roleIds:[],menuIds:[],status:'0',roleSort:0,volume:null,purity:null,weight:null,...(kind==='product'?{calculationVersion:2}:{})})
const form=reactive<Partial<MasterRow>>(blank())
const catalogTypeOptions=ref<Option[]>([]),catalogTypesBusy=ref(false),catalogTypesLoaded=ref(false),catalogTypeError=ref(''),originalCatalogType=ref<string>()
const catalogTypeKey=kind==='product'?'productType':'materialType'
const catalogLabel=kind==='product'?'产品类型':'物料类型'
let catalogTypeEpoch=0
const catalogTypeAllowed=computed(()=>!!form[catalogTypeKey]&&(catalogTypeOptions.value.some(o=>o.name===form[catalogTypeKey])||!!form.id&&form[catalogTypeKey]===originalCatalogType.value&&(kind!=='product'||form.calculationVersion===originalProduct.value?.calculationVersion)))
const catalogTypeReady=computed(()=>!!form.factoryId&&catalogTypesLoaded.value&&!catalogTypesBusy.value&&!catalogTypeError.value&&catalogTypeAllowed.value)
async function loadCatalogTypes(){
 if(kind!=='product'||!dialogOpen.value||readonly.value||!form.factoryId||saving.value||uncertain.value||!sessionCurrent())return
 const round=++catalogTypeEpoch,edit=editEpoch,factory=form.factoryId,account=identity()
 const current=()=>!disposed&&round===catalogTypeEpoch&&edit===editEpoch&&dialogOpen.value&&factory===form.factoryId&&account===identity()&&sessionCurrent()
 catalogTypesBusy.value=true;catalogTypeError.value='';catalogTypesLoaded.value=false
 try{const result=await masterOptions(kind==='product'?'product-types':'material-types',{factoryId:factory},current,true);if(current()){catalogTypeOptions.value=result.data;catalogTypesLoaded.value=true}}
 catch(error:any){if(current()){catalogTypeOptions.value=[];catalogTypeError.value=error.message||catalogLabel+'加载失败，请重试'}}finally{if(current())catalogTypesBusy.value=false}
}

const dimensionFields=computed(()=>productFields(form))
const automaticSpecification=computed(()=>dimensionFields.value.filter(d=>form.dimensions?.[d.key]).map(d=>d.label+form.dimensions![d.key]+(d.unit??'cm')).join('；'))
const metalOptions=ref<Option[]>([]),metalRules=ref<Record<string,any>[]>([])
const originalProduct=ref<Partial<MasterRow>>()
const legacyProduct=computed(()=>kind==='product'&&Boolean(form.id)&&(form.calculationVersion??1)===1)
const productTypeChoice=computed({get:()=>legacyProduct.value?'legacy:'+form.productType:form.productType,set:(value:string)=>{form.calculationVersion=2;form.productType=value}})
const sameGeometry=computed(()=>{
 const old=originalProduct.value
 return Boolean(form.id&&old&&form.factoryId===old.factoryId&&form.calculationVersion===old.calculationVersion&&form.metalTypeId===old.metalTypeId&&form.productType===old.productType&&dimensionFields.value.every(d=>form.dimensions?.[d.key]===old.dimensions?.[d.key]))
})
const calculationPreview=ref<{volume:string|null;weight:string|null}>({volume:null,weight:null}),previewBusy=ref(false),previewError=ref('')
const automaticVolume=computed(()=>legacyProduct.value||sameGeometry.value&&originalProduct.value?.volume!=null?originalProduct.value?.volume??null:calculationPreview.value.volume)
const automaticWeight=computed(()=>legacyProduct.value?originalProduct.value?.weight??null:sameGeometry.value&&originalProduct.value?.volume!=null?productWeight(automaticVolume.value,String(metalRules.value.find(r=>String(r.metalTypeId)===String(form.metalTypeId))?.density??'')):calculationPreview.value.weight)
let previewEpoch=0,previewTimer:ReturnType<typeof setTimeout>|undefined
function cancelPreview(){++previewEpoch;clearTimeout(previewTimer);previewBusy.value=false}
watch(()=>[dialogOpen.value,readonly.value,form.factoryId,form.metalTypeId,form.productType,form.calculationVersion,JSON.stringify(form.dimensions)],()=>{
 cancelPreview();calculationPreview.value={volume:null,weight:null};previewError.value=''
 if(kind!=='product'||!dialogOpen.value||readonly.value||legacyProduct.value||sameGeometry.value&&originalProduct.value?.volume!=null||!form.factoryId||!form.metalTypeId||!form.productType||!dimensionFields.value.length)return
 if(dimensionFields.value.some(d=>decimalError(form.dimensions?.[d.key],d.label,{positive:!d.allowZero})))return
 const round=previewEpoch,edit=editEpoch;previewBusy.value=true
 previewTimer=setTimeout(async()=>{
  const current=()=>!disposed&&round===previewEpoch&&edit===editEpoch&&dialogOpen.value&&!readonly.value&&sessionCurrent()&&can(`${config.permission}:${form.id?'edit':'add'}`)
  try{const r:any=await request({url:'/metal/product/calculation-preview',method:'post',params:{factoryId:form.factoryId},headers:{repeatSubmit:false},isContextCurrent:current,data:{metalTypeId:form.metalTypeId,productType:form.productType,dimensions:{...form.dimensions}}});if(current())calculationPreview.value=r.data}
  catch(error:any){if(current())previewError.value=error.message||'计算失败，请核对尺寸及公共参数'}
  finally{if(current())previewBusy.value=false}
 },250)
})
onBeforeUnmount(cancelPreview)
const permissionActionNames:Record<string,string>={query:'查看',add:'新增',edit:'修改',remove:'删除',adjust:'调整',reverse:'冲销',import:'导入',settle:'结算',handle:'处理',list:'列表',export:'导出',accept:'验收',approve:'审核通过',batchLogout:'批量强退',cancel:'取消',changeStatus:'修改状态',code:'生成代码',configure:'配置','exception-close':'关闭异常','exception-review':'复核异常',execute:'执行',forceLogout:'强制退出',handover:'交接',manage:'管理','partial-close':'部分结清',preview:'预览',receive:'接收',reject:'驳回',reopen:'重新打开',resetPwd:'重置密码',resubmit:'重新提交',return:'退回',unlock:'解锁','verify-origin':'核对来源','weight-export':'重量导出'}
const menuTree=computed(()=>{const indexed=new Map(menus.value.map(m=>[m.id,{...m,name:m.name.replace(/([A-Za-z]+(?:-[A-Za-z]+)*)$/,(_,action:string)=>permissionActionNames[action]??action),children:[] as Option[]}])) ;const roots:Option[]=[];for(const m of indexed.values()){const parent=indexed.get(m.parentId??'');if(parent)parent.children.push(m);else roots.push(m)}return roots})
function checkMenu(node:Option,state:{checkedKeys:(string|number)[]}){
 const checked=state.checkedKeys.some(key=>String(key)===String(node.id))
 const visit=(children:Option[])=>{for(const child of children){treeRef.value?.setChecked(child.id,checked,false);visit(child.children??[])}}
 visit(node.children??[])
}
const decimalOk=(v:unknown,positive=false)=>!decimalError(v,'数值',{positive})
const originalDimensions=ref<Record<string,string>>({})
const positiveDecimal=(rule:any,v:unknown,cb:(e?:Error)=>void)=>{const key=String(rule.field??'').split('.').pop()!;const unchanged=legacyProduct.value&&originalDimensions.value[key]===v&&Number.isFinite(Number(v))&&Number(v)>0;const zero=dimensionFields.value.find(d=>d.key===key)?.allowZero;cb(decimalOk(v,!zero)||unchanged?undefined:new Error('尺寸须大于0，最多14位整数、6位小数，不支持科学计数法'))}
const optionalDecimal=(_r:unknown,v:unknown,cb:(e?:Error)=>void)=>cb(v==null||v===''||decimalOk(v)?undefined:new Error('请输入非负数，最多6位小数'))
const lossDecimal=(step:{lossUnit?:string},v:unknown,cb:(e?:Error)=>void)=>cb(decimalOk(v)&&(step.lossUnit==='G_PER_PIECE'||Number(v)<=100)?undefined:new Error('请输入0至100的理论损耗百分比，最多6位小数'))
function measurementModeError(value:unknown,index:number){
 if(value!=='PIECE'&&value!=='WEIGHT')return '请选择按件或按重量管理'
 if(index===(form.processes?.length??0)-1&&value==='WEIGHT')return '最后一道工序产出成品，须按件管理'
 if(value==='WEIGHT'&&form.processes?.[index]?.lossUnit==='G_PER_PIECE')return '按重量管理须先重新配置损耗百分比'
 return ''
}
const required=(message:string)=>[{required:true,whitespace:true,message,trigger:'blur'}]
const rules:FormRules={code:textRules('编号或编码',64,true),name:textRules('名称',100,true),factoryId:[{validator:(_r,v,cb)=>cb(form.id==='1'&&kind==='staff'||v?undefined:new Error('请选择所属工厂')),trigger:'change'}],category:required('请选择类别'),processType:[{required:true,message:'请选择工序类型',trigger:'change'},{validator:(_r,v,cb)=>cb(processTypesLoading.value||processTypesFailed.value||!editTypeOptions.value.some(o=>String(o.id)===String(v))?new Error('请重新加载并选择有效的工序类型'):undefined),trigger:'change'}],phonenumber:[{required:true,message:'请输入手机号',trigger:'blur'},{pattern:/^1[3-9]\d{9}$/,message:'请输入大陆11位手机号',trigger:'blur'}],nickName:textRules('姓名',100,true),roleName:textRules('角色名称',100,true),roleIds:[{type:'array',required:true,min:1,message:'请选择至少一个角色',trigger:'change'}],password:[fieldRule(v=>passwordError(v))],specification:textRules('规格型号',200),description:textRules('工序介绍',2000),volume:[{validator:optionalDecimal,trigger:'blur'}],weight:[{validator:optionalDecimal,trigger:'blur'}],purity:[{validator:(_r,v,cb)=>optionalDecimal(_r,v,e=>cb(e??(v!==null&&v!==''&&Number(v)>100?new Error('纯度不能超过100%'):undefined))),trigger:'blur'}]}
let sequence=0,operationEpoch=0,manualPending=false
async function load(){if(loading.value){manualPending=true;return false}return readList(false)}
async function readList(background:boolean,refresh?:InventoryRefreshContext){
 if(!canRead()){resetAccess();return false}if(['product','line','material','process'].includes(kind)&&!appliedFilters.value.factoryId){sequence++;loading.value=false;manualPending=false;rows.value=[];total.value=0;selected.value=[];loadFailed.value=false;return true}if(loading.value)return false
 const round=++sequence,account=identity(),params={...query,...appliedFilters.value},selectedIds=selected.value.map(row=>row.id)
 const valid=()=>!disposed&&round===sequence&&account===identity()&&canRead(),current=()=>valid()&&!manualPending&&JSON.stringify(params)===JSON.stringify({...query,...appliedFilters.value})&&(!refresh||refresh.isCurrent()&&!saving.value&&!deleting.value&&!statusBusy.value)
 loading.value=true
 try{const data=await masterList(config.endpoint,params,current,background);if(!current())return false;rows.value=data.rows;total.value=data.total;selected.value=[];tableRef.value?.clearSelection();if(background){await nextTick();if(!current())return false;for(const row of data.rows)if(selectedIds.includes(row.id))tableRef.value?.toggleRowSelection(row,true)}loadFailed.value=false;return true}
 catch{if(current())loadFailed.value=true;return false}
 finally{if(valid()){loading.value=false;if(manualPending){manualPending=false;void load()}}}
}
function reset(){processTypesEpoch++;processTypesLoading.value=false;processTypesFailed.value=false;catalogTypeEpoch++;catalogTypeOptions.value=[];catalogTypesBusy.value=false;catalogTypesLoaded.value=false;catalogTypeError.value='';originalCatalogType.value=undefined;metalMutation.value=false;typeMaintenanceOpen.value=false;treeRef.value?.setCheckedKeys([]);for(const key of Object.keys(form))delete(form as Record<string,unknown>)[key];Object.assign(form,blank());formRef.value?.clearValidate()}
function categoryChanged(){form.calculationVersion=2;form.dimensions=Object.fromEntries(dimensionFields.value.map(d=>[d.key,'']));form.volume=null;form.weight=null}
const editLoading=ref(false),editError=ref(false),rolesLoading=ref(false)
let editEpoch=0,roleEpoch=0,disposed=false,retryEdit:()=>void=()=>{},openedId='',openedVersion:number|undefined,openedOptions:Record<string,any>={}
const needsReload=ref(false),uncertain=ref(false)
function resetAccess(){++sequence;++operationEpoch;++editEpoch;++roleEpoch;manualPending=false;loading.value=false;loadFailed.value=false;rows.value=[];selected.value=[];total.value=0;tableRef.value?.clearSelection();editLoading.value=false;rolesLoading.value=false;editError.value=false;dialogOpen.value=false;passwordOpen.value=false;passwordRow.value=undefined;newPassword.value='';saving.value=false;deleting.value=false;statusBusy.value='';exporting.value=false;needsReload.value=false;uncertain.value=false;reset()}
const processTypesLoading=ref(false),processTypesFailed=ref(false)
let processTypesEpoch=0
function editorProcessTypesChanged(options:Option[],addedId?:string){editTypeOptions.value=options;openedOptions={...openedOptions,types:JSON.parse(JSON.stringify(options))};if(addedId)form.processType=Number(addedId);else if(form.processType&&!options.some(o=>String(o.id)===String(form.processType)))form.processType=undefined;void processTypeControl.value?.refresh()}
async function refreshProcessTypes(){
 if(kind!=='process'||!dialogOpen.value||!form.factoryId)return
 const round=++processTypesEpoch,editor=editEpoch,factory=form.factoryId,account=identity()
 const current=()=>!disposed&&round===processTypesEpoch&&editor===editEpoch&&factory===form.factoryId&&account===identity()&&dialogOpen.value
 processTypesLoading.value=true;processTypesFailed.value=false
 try{const result:any=await request({url:'/metal/process/types',params:{factoryId:factory},isContextCurrent:current});if(current()){editTypeOptions.value=result.data??[];if(form.processType&&!editTypeOptions.value.some(o=>String(o.id)===String(form.processType)))ElMessage.warning('原工序类型已失效，请重新选择')}}
 catch{if(current())processTypesFailed.value=true}finally{if(round===processTypesEpoch)processTypesLoading.value=false}
}
async function editorOptions(draft:Partial<MasterRow>,view:boolean,current:()=>boolean,background=false){
 const opts={factories:[] as Option[],products:[] as Option[],processes:[] as Option[],roles:[] as Option[],menus:[] as Option[],rules:[] as Record<string,any>[],metals:[] as Option[],types:[] as Option[]}
 if(!view){
  if(['line','staff','role','product','material','process'].includes(kind))opts.factories=(await masterOptions('factories',undefined,current,background)).data
  if(kind==='role')opts.menus=(await masterOptions('menus',undefined,current,background)).data
  const factory=draft.factoryId||(!isAdmin.value?opts.factories[0]?.id:undefined)
  if(!draft.id&&factory)draft.factoryId=factory
  const params=factory?{factoryId:factory}:undefined
  if(factory&&kind==='line')opts.products=(await masterOptions('products',params,current,background)).data
  if(factory&&kind==='product'){opts.processes=(await masterOptions('processes',params,current,background)).data;opts.rules=(await request({url:'/metal/product/calculation-rule',params,isContextCurrent:current,quiet:background})).data;(opts as any).materials=(await request({url:'/metal/storage-config/materials',params:{factoryId:factory},isContextCurrent:current,quiet:background})).data??[]}
  if(factory&&kind==='process')opts.types=(await request({url:'/metal/process/types',params,isContextCurrent:current,quiet:background})).data
  if(kind==='staff'&&factory)opts.roles=(await masterOptions('roles',{factoryId:factory},current,background)).data
  if(factory&&['product','material'].includes(kind))opts.metals=(await request({url:'/metal/metal-types',params,isContextCurrent:current,quiet:background})).data
 }
 if(view&&draft.factoryId)opts.factories=[{id:draft.factoryId,name:draft.factoryName??'历史工厂'}]
 if(view&&kind==='product')opts.processes=(draft.processes??[]).map(s=>({id:s.processId,name:s.processName??s.processId}))
 if(kind==='staff'&&draft.id==='1')opts.roles=[{id:'1',name:'超级管理员'}]
 return opts
}
const metalMutation=ref(false),typeMaintenanceOpen=ref(false)
function metalChanged(options:Option[]){
 if(!dialogOpen.value)return
 const previous=new Map((openedOptions.rules??[]).map((rule:Record<string,any>)=>[String(rule.metalTypeId),rule]))
 openedOptions={...openedOptions,metals:JSON.parse(JSON.stringify(options)),...(kind==='product'?{rules:options.map(metal=>previous.get(String(metal.id))??{metalTypeId:String(metal.id),metalTypeName:metal.name,factoryId:form.factoryId,density:null,version:0})}:{})}
}
async function probeEditor(refresh:InventoryRefreshContext){
 const optionsBaseline=openedOptions,round=editEpoch,account=identity(),password=passwordOpen.value,id=password?passwordRow.value?.id:openedId,version=password?passwordRow.value?.version:openedVersion
 const current=()=>!disposed&&refresh.isCurrent()&&round===editEpoch&&account===identity()&&sessionCurrent()&&!saving.value&&!uncertain.value&&!metalMutation.value&&!typeMaintenanceOpen.value&&optionsBaseline===openedOptions&&(password?passwordOpen.value:dialogOpen.value)
 if(id){const response=await masterDetail(config.endpoint,id,!can(config.permission+':query'),current,true);if(current()&&response.data.version!==version)needsReload.value=true}
 if(!password&&current()&&kind==='product')await loadCatalogTypes()
 if(!password&&current()){const options=await editorOptions(form,readonly.value,current,true);if(current()&&JSON.stringify(options)!==JSON.stringify(openedOptions))needsReload.value=true}
}
const {failed:refreshFailed}=useInventoryRefresh(refresh=>readList(true,refresh),computed(()=>dialogOpen.value||passwordOpen.value),{allowed:canRead,reset:resetAccess},{probe:probeEditor,paused:computed(()=>loading.value||saving.value||deleting.value||!!statusBusy.value||exporting.value||editLoading.value||rolesLoading.value||uncertain.value||metalMutation.value||typeMaintenanceOpen.value)})
function cancelEdit(){if(saving.value||uncertain.value)return;++editEpoch;++roleEpoch;editLoading.value=false;rolesLoading.value=false;editError.value=false;dialogOpen.value=false}
watch(dialogOpen,open=>{if(!open&&!saving.value){++editEpoch;++roleEpoch;editLoading.value=false;rolesLoading.value=false}}, {flush:'sync'})
onBeforeUnmount(()=>{disposed=true;++editEpoch;++roleEpoch;++sequence;++operationEpoch})
async function factoryChanged(){if(saving.value||uncertain.value)return;if(kind!=='staff'){
 const epoch=editEpoch,ticket=++roleEpoch,factory=form.factoryId,account=identity(),current=()=>!disposed&&epoch===editEpoch&&ticket===roleEpoch&&account===identity()&&factory===form.factoryId
 if(kind==='product'){catalogTypeEpoch++;form[catalogTypeKey]=undefined;if(kind==='product')categoryChanged();catalogTypeOptions.value=[];catalogTypesLoaded.value=false;catalogTypesBusy.value=false;catalogTypeError.value='';void loadCatalogTypes()}
 form.productIds=[];form.metalTypeId=undefined;form.processType=undefined;form.processes=[];form.storageConfiguration=kind==='product'?{steps:[]}:undefined;routeMaterials.value=[];products.value=[];processes.value=[];metalRules.value=[];metalOptions.value=[];editTypeOptions.value=[];cancelPreview();rolesLoading.value=true
 try{const opts=await editorOptions(form,false,current);if(current()){products.value=opts.products;processes.value=opts.processes;routeMaterials.value=(opts as any).materials??[];metalRules.value=opts.rules;editTypeOptions.value=opts.types;openedOptions=JSON.parse(JSON.stringify(opts))}}finally{if(current())rolesLoading.value=false}return}
const epoch=editEpoch,ticket=++roleEpoch,factory=form.factoryId,account=identity(),current=()=>!disposed&&epoch===editEpoch&&ticket===roleEpoch&&account===identity()&&sessionCurrent()&&factory===form.factoryId;form.roleIds=[];roles.value=[];rolesLoading.value=true;try{const result=factory?(await masterOptions('roles',{factoryId:factory},current)).data:[];if(current()){roles.value=result;openedOptions.roles=JSON.parse(JSON.stringify(result))}}finally{if(current())rolesLoading.value=false}}
async function openEditor(row?:MasterRow,view=false){
 if(saving.value||uncertain.value||disposed||!can(`${config.permission}:${row?(view?'query':'edit'):'add'}`))return;dialogOpen.value=false;const epoch=++editEpoch,account=identity();++roleEpoch;rolesLoading.value=false;editLoading.value=true;editError.value=false;needsReload.value=false;retryEdit=()=>{void openEditor(row,view)}
 const valid=()=>!disposed&&epoch===editEpoch&&account===identity()&&can(`${config.permission}:${row?(view?'query':'edit'):'add'}`)
 try{
  const draft:Partial<MasterRow>=row?(await masterDetail(config.endpoint,row.id,!view&&!can(`${config.permission}:query`),valid)).data:blank();if(kind==='product'&&row)draft.calculationVersion??=1
  if(!valid())return
  if(!row&&kind==='staff')draft.password='12345678'
  if(!row&&['product','line','material','process'].includes(kind))draft.factoryId=listFactory.value
  for(const field of ['productIds','roleIds','menuIds'] as const)if(draft[field])draft[field]=draft[field]!.map(String)
  const opts=await editorOptions(draft,view,valid)
  if(!view&&['line','staff','role','product','material','process'].includes(kind)&&!isAdmin.value)draft.factoryId=opts.factories[0]?.id
  if(!valid())return
  readonly.value=view;reset();Object.assign(form,draft);originalCatalogType.value=draft[catalogTypeKey];originalDimensions.value={...(draft.dimensions??{})};originalProduct.value={...draft,dimensions:{...(draft.dimensions??{})}};factories.value=opts.factories;products.value=opts.products;processes.value=opts.processes;routeMaterials.value=(opts as any).materials??[];roles.value=opts.roles;menus.value=opts.menus;metalRules.value=opts.rules??[];editTypeOptions.value=opts.types
  openedId=draft.id??'';openedVersion=draft.version;openedOptions=JSON.parse(JSON.stringify(opts));dialogOpen.value=true;if(kind==='product'&&!view)void loadCatalogTypes();await nextTick();if(valid()){treeRef.value?.setCheckedKeys(form.menuIds??[]);formRef.value?.clearValidate()}
 }catch{if(valid()){reset();editError.value=true}}finally{if(valid())editLoading.value=false}
}
async function openAdd(){await openEditor()}
async function openRecord(row:MasterRow,view=false){await openEditor(row,view)}
function moveStep(index:number,direction:number){if(readonly.value||saving.value)return;const list=form.processes!;if(index+direction<0||index+direction>=list.length)return;[list[index],list[index+direction]]=[list[index+direction],list[index]]}
async function submit(){if(typeMaintenanceOpen.value)return;if(kind==='product'&&!catalogTypeReady.value)return;if(needsReload.value||uncertain.value||saving.value||editLoading.value||rolesLoading.value||!dialogOpen.value||readonly.value||!can(`${config.permission}:${form.id?'edit':'add'}`))return;const epoch=editEpoch,account=identity(),current=()=>!disposed&&epoch===editEpoch&&account===identity()&&can(`${config.permission}:${openedId?'edit':'add'}`)&&dialogOpen.value;saving.value=true;try{if(!await formRef.value?.validate().catch(()=>false)||!current()||needsReload.value||String(form.id??'')!==openedId)return;if(kind==='product'){if(form.dimensions?.outerDiameter&&Number(form.dimensions.outerDiameter)<=Number(form.dimensions.innerDiameter)){ElMessage.error('外径必须大于内径');return}const steps=form.processes??[];if(steps.some(s=>!s.processId||!decimalOk(s.loss)||(s.lossUnit!=='G_PER_PIECE'&&Number(s.loss)>100))||new Set(steps.map(s=>s.processId)).size!==steps.length){ElMessage.error('请选择不重复工序并填写0至100的损耗百分比');return}}
 const payload=JSON.parse(JSON.stringify(form)) as Partial<MasterRow>;delete payload.createBy;delete payload.createTime;delete payload.updateTime;delete payload.factoryName;delete payload.roleNames;delete payload.productNames;if(kind==='product'){delete payload.specification;delete payload.category;delete payload.volume;delete payload.weight}if(kind==='material'){payload.name=payload.name?.trim();delete payload.materialType;delete payload.materialCategory;}if(kind==='line'&&!payload.factoryId)payload.factoryId=null;if(kind==='role')payload.menuIds=treeRef.value?.getCheckedKeys().map(String)??[];for(const key of ['volume','purity','weight'] as const)if(payload[key]==='')payload[key]=null;const added=!openedId;if(openedId)payload.version=openedVersion;await masterSave(config.endpoint,payload,current);if(!current())return;ElMessage.success(added?'添加成功':'修改成功');dialogOpen.value=false;if(added)query.pageNum=['process','material'].includes(kind)?Math.max(1,Math.ceil((total.value+1)/query.pageSize)):1;await load()}catch(error:any){if(current()){uncertain.value=!!error?.isAxiosError&&(!error.response||error.response.status>=500);if(/版本|重新加载|刷新/.test(error?.message??''))needsReload.value=true;applyFieldErrors(formRef.value,error)}}finally{if(!disposed&&epoch===editEpoch&&account===identity())saving.value=false}}
function mutationCurrent(permission:string){const round=++operationEpoch,account=identity();return()=>!disposed&&round===operationEpoch&&account===identity()&&can(permission)}
async function remove(targets:MasterRow[]){if(deleting.value||saving.value||statusBusy.value||uncertain.value||!targets.length||!can(`${config.permission}:remove`))return;const ids=targets.map(r=>r.id),current=mutationCurrent(`${config.permission}:remove`);deleting.value=true;try{await ElMessageBox.confirm(`确认删除选中的${ids.length}条记录？`,'删除确认',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'});if(!current())return;await masterRemove(config.endpoint,ids,current);if(!current())return;ElMessage.success('删除成功');query.pageNum=Math.min(query.pageNum,Math.max(1,Math.ceil((total.value-ids.length)/query.pageSize)));await load()}catch{}finally{if(current())deleting.value=false}}
async function changeStatus(row:MasterRow,value:unknown){if(statusBusy.value||saving.value||deleting.value||uncertain.value||!can(`${config.permission}:edit`))return;const current=mutationCurrent(`${config.permission}:edit`),payload={id:row.id,version:row.version,status:String(value)};statusBusy.value=row.id;try{await masterAction(config.endpoint,'changeStatus',payload,current);if(current())await load()}catch{}finally{if(current())statusBusy.value=''}}
function openPassword(row:MasterRow){if(saving.value||uncertain.value||!can('system:user:resetPwd'))return;++editEpoch;passwordRow.value={...row};needsReload.value=false;newPassword.value='';passwordOpen.value=true}
async function resetPassword(){if(!can('system:user:resetPwd')||saving.value||needsReload.value||uncertain.value||!passwordOpen.value||!passwordRow.value)return;if(passwordError(newPassword.value)){ElMessage.error(passwordError(newPassword.value));return}const valid=mutationCurrent('system:user:resetPwd'),round=editEpoch,target=passwordRow.value.id,current=()=>valid()&&round===editEpoch&&passwordRow.value?.id===target;saving.value=true;try{await masterAction(config.endpoint,'resetPwd',{id:target,version:passwordRow.value.version,password:newPassword.value},current);if(!current())return;ElMessage.success('密码重置成功');passwordOpen.value=false;newPassword.value='';await load()}catch{}finally{if(current())saving.value=false}}
// shortcut: master creation has no request-ID recovery, enable retries when its endpoint supports them.
function acknowledgeUnknown(){if(saving.value)return;uncertain.value=false;dialogOpen.value=false;void load()}
function sizeChanged(){query.pageNum=1;load()}
onBeforeRouteLeave(()=>{if(saving.value||uncertain.value)return false});onBeforeRouteUpdate(()=>{if(saving.value||uncertain.value)return false})
onMounted(load)
</script>
<style scoped>
/* Preview updates must not move the save button while a dimension input blurs. */
.calculation-feedback{min-height:40px;line-height:20px;font-size:12px;color:var(--el-text-color-secondary);margin:0 0 12px}

.master-page{min-width:0;max-width:100%;box-sizing:border-box;min-height:calc(var(--design-height,100vh) - var(--business-header-height,56px));background:var(--business-canvas);padding:16px 16px 24px;display:flex;flex-direction:column}
.master-page :deep(.el-dialog .el-select-dropdown__wrap){max-height:180px}
.material-page :deep(.el-dialog){width:min(900px,94vw);max-height:90vh}
.material-page :deep(.el-dialog .el-select-dropdown__wrap){max-height:min(340px,50vh)}
.line-page :deep(.el-dialog){max-height:90vh;display:flex;flex-direction:column}
.line-page :deep(.el-dialog__body){flex:1}
.line-page :deep(.line-product-select .el-select-dropdown__wrap),
.line-page :deep(.line-factory-select .el-select-dropdown__wrap){max-height:340px}
.master-toolbar{min-width:0;max-width:100%;margin-bottom:16px;flex-shrink:0}.master-toolbar>*{flex-shrink:0}.master-toolbar>.el-button{width:89px;height:35px;margin:0;padding:0;font-size:14px}
.master-toolbar>.el-button:disabled{color:#fff;background:var(--el-button-bg-color);border-color:var(--el-button-border-color);opacity:.6}
.master-table{font-size:14px;--el-table-header-bg-color:#f4f4f4;--el-table-border-color:#e8ebf0;--el-table-header-text-color:#303133}.master-table :deep(th.el-table__cell){height:44px;padding:4px 0;font-weight:600}.master-table :deep(.cell){font-size:14px;white-space:pre-wrap;overflow-wrap:anywhere;line-height:22px;padding:8px 10px}.master-table :deep(td.el-table__cell){padding:0}.master-table :deep(.row-button){height:25px;font-size:13px;padding:0 9px;margin:2px;border-radius:2px}
.master-pagination{margin-top:16px;padding-top:0;display:flex;justify-content:center}.master-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:center}.process-hint{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7;margin:0 0 14px}.process-choice{display:flex;flex-direction:column;gap:8px}.process-choice label{font-size:13px;color:var(--el-text-color-regular)}.process-step{display:flex;flex-wrap:wrap;align-items:flex-start;gap:8px;margin-bottom:12px}.process-step .loss-field{width:160px;flex-shrink:0;margin:0 0 30px}.process-step .el-select{width:230px;flex-shrink:0}.process-step .el-input{width:160px}.load-error{text-align:center;margin:16px;color:#c45656}
.role-permission-tree{width:100%;max-height:min(360px,45vh);overflow:auto;border:1px solid #dcdfe6;border-radius:4px;padding:8px;overscroll-behavior:contain}
.role-permission-tree :deep(.el-tree){min-width:max-content}
.product-specification{white-space:normal;overflow-wrap:anywhere;line-height:24px;padding:4px 0}
.step-order-button.is-disabled{color:#a8abb2;background:#f0f2f5;border-color:#dcdfe6;cursor:not-allowed}
.process-step{display:grid;grid-template-columns:18px minmax(0,200px) minmax(0,145px) minmax(0,170px) auto;gap:10px;align-items:start;margin-bottom:24px}
.process-step>span{padding-top:30px;line-height:32px}
.process-choice label{height:22px;line-height:22px;font-size:14px;font-weight:600}
.process-step .loss-field{width:100%;margin:0}
.process-step .loss-field :deep(.el-form-item__label){height:22px;line-height:22px;margin:0 0 8px;padding:0;font-size:14px}
.process-step .loss-field :deep(.el-form-item__content){margin-left:0;min-height:32px}
.process-step .el-select,.process-step .el-input{width:100%;min-width:0}
.process-step-actions{display:flex;gap:10px;padding-top:30px}
.process-step-actions .el-button{height:32px;margin:0}
@media(max-width:640px){
 .process-step{grid-template-columns:18px minmax(0,1fr)}
 .process-choice,.process-step .loss-field,.process-step .mode-field,.process-step-actions{grid-column:2}
 .process-step-actions{padding-top:0;flex-wrap:wrap}
}
</style>

<style>
.base-info-form-dialog .el-form-item__label{position:relative;justify-content:flex-start;text-align:left;white-space:nowrap;padding-left:12px}
.base-info-form-dialog .el-form-item.is-required:not(.is-no-asterisk)>.el-form-item__label:before{position:absolute;left:0;margin-right:0}
</style>

<style scoped>
/* Preview updates must not move the save button while a dimension input blurs. */
.calculation-feedback{min-height:40px;line-height:20px;font-size:12px;color:var(--el-text-color-secondary);margin:0 0 12px}
.process-selection{margin-bottom:0!important}.process-required{color:var(--el-color-danger)}</style>

<style scoped>
/* Preview updates must not move the save button while a dimension input blurs. */
.calculation-feedback{min-height:40px;line-height:20px;font-size:12px;color:var(--el-text-color-secondary);margin:0 0 12px}
.mode-field{min-width:0;margin:0}.mode-field :deep(.el-form-item__label){height:22px;line-height:22px;margin:0 0 8px;padding:0}.mode-field :deep(.el-form-item__content){margin-left:0;min-height:32px}@media(min-width:641px) and (max-width:1100px){.process-step{grid-template-columns:18px minmax(0,1fr) minmax(0,145px) auto}.process-step .mode-field{grid-column:2 / span 2;grid-row:2}.process-step-actions{grid-column:4;grid-row:1}}</style>

<style scoped>
.material-type-control{display:flex;align-items:center;gap:12px;width:100%;min-width:0}
.material-type-control .el-select{flex:1;min-width:0}
.material-type-control :deep(.el-button){flex-shrink:0}
@media(max-width:600px){.material-type-control{flex-wrap:wrap}.material-type-control .el-select{flex-basis:100%;width:100%}}
</style>
