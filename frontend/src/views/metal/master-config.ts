export type MasterKind = 'line' | 'product' | 'material' | 'process' | 'staff' | 'role'
export const legacyProductDimensions: Record<string, { key: string; label: string }[]> = {
    铱坩埚弧底: [['innerDiameter', '内径'], ['innerHeight', '内高'], ['wallThickness', '壁厚'], ['bottomThickness', '底厚'], ['rHeight', 'R高度']],
    铱坩埚平底: [['innerDiameter', '内径'], ['innerHeight', '内高'], ['wallThickness', '壁厚'], ['bottomThickness', '底厚']],
    铱坩埚梯形: [['innerDiameter', '内径'], ['innerHeight', '内高'], ['wallThickness', '壁厚'], ['bottomThickness', '底厚']],
    铱环: [['outerDiameter', '外径'], ['innerDiameter', '内径'], ['height', '高度']],
    铱杆实心: [['diameter', '直径'], ['length', '长度']],
    铱杆空心: [['outerDiameter', '外径'], ['innerDiameter', '内径'], ['length', '长度']]
} as unknown as Record<string, { key: string; label: string }[]>
for (const category of Object.keys(legacyProductDimensions)) legacyProductDimensions[category] = (legacyProductDimensions[category] as unknown as string[][]).map(([key, label]) => ({
    key,
    label
}))
export interface ProductDimension {key:string;label:string;unit?:string;allowZero?:boolean}
const flat:ProductDimension[]=[{key:'innerDiameter',label:'内径'},{key:'outerDiameter',label:'外径'},{key:'wallThickness',label:'壁厚'},{key:'innerHeight',label:'内高'},{key:'bottomThickness',label:'底厚'}]
export const productDimensions:Record<string,ProductDimension[]>={
 '平底坩埚':flat,
 '环':[{key:'outerDiameter',label:'外径'},{key:'innerDiameter',label:'内径'},{key:'thickness',label:'厚'}],
 '杆':[{key:'innerDiameter',label:'内径',allowZero:true},{key:'outerDiameter',label:'外径'},{key:'length',label:'长度'}],
 '梯形':[{key:'upperInnerDiameter',label:'上内径'},{key:'upperOuterDiameter',label:'上外径'},{key:'wallThickness',label:'壁厚'},{key:'longInnerHeight',label:'长内高'},{key:'lowerInnerDiameter',label:'下内径'},{key:'angle',label:'夹角',unit:'°',allowZero:true},{key:'bottomThickness',label:'底厚'}],
 '弧底':flat,
 '靶':[{key:'innerDiameter',label:'内径'},{key:'wallThickness',label:'壁厚'},{key:'bottomThickness',label:'底厚'}],
 '方形坩埚':[{key:'length',label:'长'},{key:'width',label:'宽'},{key:'height',label:'高'},{key:'innerLength',label:'内长'},{key:'innerWidth',label:'内宽'},{key:'innerHeight',label:'内高'}],
 '方形盖':[{key:'length',label:'长'},{key:'width',label:'宽'},{key:'height',label:'高'}]
}
export const productTypes=Object.keys(productDimensions)
export function productFields(product:{id?:string;productType?:string;category?:string;calculationVersion?:number}):ProductDimension[]{
 return (product.calculationVersion??(product.id?1:2))===1?legacyProductDimensions[product.productType?'铱'+product.productType:product.category??'']??[]:productDimensions[product.productType??'']??[]
}

export const processTypes = ['粉料', '旧锅清理', '碎料', '融定', '闸板', '线切割', '卷板成型', '焊接', '喷砂成品']
export const materialTypes = ['粉料', '块料', '旧料']

export interface Column {
    key: string;
    label: string;
    width: number
}

const cols = (items: [string, string, number][]): Column[] => items.map(([key, label, width]) => ({key, label, width}))
export const masterConfigs = {
    line: {
        title: '产线管理',
        endpoint: '/metal/production-line',
        permission: 'metal:production-line',
        width: 708,
        columns: cols([['code', '产线编号', 72], ['name', '产线名称', 72], ['productNames', '产品名称', 128], ['createBy', '创建人', 72], ['createTime', '创建时间', 128]])
    },
    product: {
        title: '产品管理',
        endpoint: '/metal/product',
        permission: 'metal:product',
        width: 990,
        columns: cols([['code', '产品编号', 70], ['name', '产品名称', 110], ['metalTypeName','金属类型',100], ['productType','产品类型',120], ['specification', '规格型号', 220], ['volume', '产品体积', 76], ['purity', '纯度', 56], ['weight', '理论成品净重（g）', 135], ['createBy', '创建人', 64], ['createTime', '创建时间', 130]])
    },
    material: {
        title: '物料管理',
        endpoint: '/metal/material',
        permission: 'metal:material',
        width: 936,
        columns: cols([['code', '物料编码', 132], ['materialType', '物料类型', 220], ['specification', '规格型号', 136], ['metalTypeName', '金属类型', 120], ['createBy', '创建人', 100], ['createTime', '创建时间', 140]])
    },
    process: {
        title: '工序管理',
        endpoint: '/metal/process',
        permission: 'metal:process',
        width: 990,
        columns: cols([['id', '工序ID', 98], ['name', '工序名称', 130], ['description', '工序描述', 250], ['createBy', '创建人', 130], ['updateTime', '更新时间', 146]])
    },
    staff: {
        title: '人员管理',
        endpoint: '/system/user',
        permission: 'system:user',
        width: 1032,
        columns: cols([['phonenumber', '手机号', 146], ['nickName', '姓名', 100], ['factoryName', '工厂名称', 160], ['roleNames', '角色', 180], ['status', '状态', 66], ['createTime', '创建时间', 144]])
    },
    role: {
        title: '角色管理',
        endpoint: '/system/role',
        permission: 'system:role',
        width: 1004,
        columns: cols([['roleName', '角色名称', 210], ['factoryName', '所属工厂名称', 210], ['roleSort', '显示顺序', 130], ['status', '状态', 80], ['createTime', '创建时间', 190]])
    }
} satisfies Record<MasterKind, {
    title: string;
    endpoint: string;
    permission: string;
    width: number;
    columns: Column[]
}>
