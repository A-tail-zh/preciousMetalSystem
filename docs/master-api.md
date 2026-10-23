# 基础管理接口

登录采用若依Bearer令牌。列表GET /list默认pageNum=1、pageSize=16，最大100；详情GET /{id}；新增POST；修改PUT；删除DELETE /{id1},{id2}（最大100条，整批事务）。查询返回rows/total或data，新增返回id字符串；业务失败检查code/msg。修改必须携带id字符串和version整数。数字精度值使用字符串，时间为yyyy-MM-dd HH:mm:ss。审计字段由后端生成。

| 根路径 | 请求资料字段 | 权限前缀 |
|---|---|---|
| /metal/production-line | code,name,factoryId,productIds | metal:production-line |
| /metal/product | code,name,category,specification,dimensions,volume,purity,weight,processes | metal:product |
| /metal/material | code,materialType,specification | metal:material |
| /metal/process | name,description,processType（1～9） | metal:process |
| /system/user | phonenumber,nickName,factoryId,roleIds,status，新增password | system:user |
| /system/role | roleName,factoryId,roleSort,status,menuIds | system:role |

分别使用:list/:query/:add/:edit/:remove权限。人员、角色新接口采用id而非原生userId/roleId；沿用底层若依用户、角色及关系表。状态0启用、1停用。PUT /system/user/changeStatus和/system/role/changeStatus提交{id,version,status}；PUT /system/user/resetPwd提交{id,version,password}，需system:user:resetPwd。

产品dimensions按类别提交精确字段集合，processes为有序[{processId,loss}]，loss为非负克数。六类字段、单位、类型、最大长度见基础管理详细设计。产品、物料编码逻辑删除后不复用。产品被产线引用、工序被产品引用、角色被人员引用时禁止删除。产线物理删除前必须解除工厂和产品关系。

GET /metal/options/factories、products、processes、roles?factoryId={id}、menus返回id/name选项。范围与维护接口一致，不能借选项跨厂访问；菜单包含parentId/perms以构建权限树。

GET /metal/quote-rule返回{basePrice,piValue,version}；PUT同路径保存，分别需metal:quote-rule:list/edit。GET /metal/quotation/context需metal:quotation:list，返回报价规则、商品及calculationEnabled=true。

GET /metal/product/calculation-rule供产品query/add/edit权限读取计算参数。POST /metal/quotation/calculate需metal:quotation:list，提交{category,dimensions,quantity}，quantity为1～1000000整数；返回字符串volume、unitPrice、totalPrice及所采用rule。后端校验类别、尺寸和外径大于内径。产品保存和读取均由后端计算体积，支持平底、实心、空心和环；弧底、梯形尚无公式，体积为null，计算接口拒绝。体积不含基础单价，单价=未舍入体积×基础单价，总价再乘数量，输出6位小数。

普通账号必须所属有效工厂。工厂、产线、人员、普通角色的详情、批量写入和下拉均校验范围；共享资料按按钮权限。授权、角色/人员启停、密码重置和人员归属调整在事务提交后失效相关令牌。超级账号/角色不可删除或停用，不能停用/删除自己，也不能为普通人员分配超级角色。旧原生维护接口位于/legacy/system/user、/legacy/system/role，仅超级管理员可调用；系统菜单维护同样限制超级管理员。
