export type MasterKind='line'|'product'|'material'|'process'|'staff'|'role'
export const productDimensions:Record<string,{key:string;label:string}[]>={
  铱坩埚弧底:[['innerDiameter','内径'],['innerHeight','内高'],['wallThickness','壁厚'],['bottomThickness','底厚'],['rHeight','R高度']],
  铱坩埚平底:[['innerDiameter','内径'],['innerHeight','内高'],['wallThickness','壁厚'],['bottomThickness','底厚']],
  铱坩埚梯形:[['innerDiameter','内径'],['innerHeight','内高'],['wallThickness','壁厚'],['bottomThickness','底厚']],
  铱环:[['outerDiameter','外径'],['innerDiameter','内径'],['height','高度']],
  铱杆实心:[['diameter','直径'],['length','长度']],
  铱杆空心:[['outerDiameter','外径'],['innerDiameter','内径'],['length','长度']]
} as unknown as Record<string,{key:string;label:string}[]>
for(const category of Object.keys(productDimensions)) productDimensions[category]=(productDimensions[category] as unknown as string[][]).map(([key,label])=>({key,label}))
export const processTypes=['粉料','旧锅清理','碎料','融定','闸板','线切割','卷板成型','焊接','喷砂成品']
export const materialTypes=['旧铱坩埚','旧铱杆','铱块','铱粉']
export interface Column {key:string;label:string;width:number}
const cols=(items:[string,string,number][]):Column[]=>items.map(([key,label,width])=>({key,label,width}))
export const masterConfigs={
 line:{title:'产线管理',endpoint:'/metal/production-line',permission:'metal:production-line',width:708,columns:cols([['code','产线编号',72],['name','产线名称',72],['productNames','产品名称',128],['createBy','创建人',72],['createTime','创建时间',128]])},
 product:{title:'产品管理',endpoint:'/metal/product',permission:'metal:product',width:990,columns:cols([['code','产品编号',70],['productDisplay','名称/类别',94],['specification','规格型号',220],['volume','产品体积',76],['purity','纯度',56],['weight','理论成品净重（g）',135],['createBy','创建人',64],['createTime','创建时间',130]])},
 material:{title:'物料管理',endpoint:'/metal/material',permission:'metal:material',width:936,columns:cols([['code','物料编码',132],['materialType','物料类型',220],['specification','规格型号',136],['createBy','创建人',100],['createTime','创建时间',140]])},
 process:{title:'工序管理',endpoint:'/metal/process',permission:'metal:process',width:990,columns:cols([['id','工序ID',98],['name','工序名称',130],['description','工序描述',250],['createBy','创建人',130],['updateTime','更新时间',146]])},
 staff:{title:'人员管理',endpoint:'/system/user',permission:'system:user',width:1032,columns:cols([['phonenumber','手机号',146],['nickName','姓名',100],['factoryName','工厂名称',160],['roleNames','角色',180],['status','状态',66],['createTime','创建时间',144]])},
 role:{title:'角色管理',endpoint:'/system/role',permission:'system:role',width:1004,columns:cols([['roleName','角色名称',210],['factoryName','所属工厂名称',210],['roleSort','显示顺序',130],['status','状态',80],['createTime','创建时间',190]])}
} satisfies Record<MasterKind,{title:string;endpoint:string;permission:string;width:number;columns:Column[]}>
