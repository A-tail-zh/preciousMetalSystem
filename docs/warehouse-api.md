# 仓储、损耗与预警接口

全部接口位于/metal，使用若依Bearer认证，后端按工厂检查范围。ID与十进制值用字符串，version整数；重量g、6位小数，数量整数，默认16条/页。参数错误、引用冲突与版本冲突返回业务错误，403为范围或权限拒绝。

| 根路径 | 查询 | 写入 |
|---|---|---|
| warehouse | GET /list、/{id} | POST、PUT、DELETE /{ids} |
| inventory | GET /list?filter=RAW/FINISHED/SEMI、/{id} | POST /opening、PUT资料、POST /{id}/adjust、DELETE /{ids} |
| warehouse-order | GET /list?filter=OUT/IN/RET、/{id} | POST立即过账、POST /{id}/reverse |
| process-batch | GET /list、/{id} | POST建立、POST /{id}/settle、POST /{id}/reverse |
| work-report | GET /list、/{id}、/summary?workOrderId=…、/step?itemId=…&processId=… | 自动生成，无直接写入 |
| warning-rule | GET /list、/{id} | POST、PUT、DELETE /{ids} |
| warning-record | GET /list、/{id} | POST /{id}/handle、DELETE /{ids}归档 |

选项：GET /warehouse/options/{kind}?factoryId=…&referenceId=…，kind为factories/warehouses/people/materials/products/processes/work-orders/stocks/batches/out-lines。processes的referenceId为产品ID。所有本厂选项执行范围检查，资料选项共享且只返回有效资料。

创建库房字段requestId(UUID)、factoryId、name、sortOrder、status(0启用/1停用)。规则字段requestId、factoryId、productId、processId、tolerance、status。编辑额外带id和version；工厂和规则归属不可编辑。

加工批次字段requestId、workOrderId、itemId、processId、plannedQuantity。结算字段version、completedQuantity；冲销version、reason。已结算不重复结算，未结算数量预占；结算要求产出数量等于实际完成数量。后一工序不能超过前工序有效完成数。冲销先验证剩余数量及库存下游引用，保留报工和预警作废历史。

仓储创建字段requestId、factoryId、warehouseId、type、orderDate、operatorId、keeperId、recipientId、inspectorId、reviewerId、workOrderId、batchId、batchVersion、settle、completedQuantity、lines。经手人及仓管员必填；生产出库/半成品出库领用人必填，其余人员选填。批次关联时带当前batchVersion，settle=true须具备批次结算权限。

类型：PRODUCTION_OUT/PRODUCT_OUT/EXCHANGE_OUT/SEMI_OUT、RAW_IN/FINISHED_IN/SEMI_IN、RAW_RETURN/PRODUCT_RETURN。OPENING仅期初接口可用，ADJUST仅调整接口可用。生产领料、原料回料及生产产出须关联加工批次；外部入库不选择生产工单。首次生产领料使工单进入进行中，全部工序完成后进入COMPLETED已完成。

每条lines字段inventoryId（出库）、sourceLineId（退还原出库行）、category、referenceId（入库资料ID）、batchNo、netWeight、quantity、grossWeight、impurity、purity、organization、purpose、remark。原料category=RAW/quantity=0，成品FINISHED和半成品SEMI按件计数；净重正数，毛重填写时净重必须等于毛重减杂质。生产产出batchNo留空，系统自动生成。退还由来源行生成分类和资料，累计退还不得超原出库。

库存调整字段requestId、version、operatorId、keeperId、deltaWeight、deltaQuantity、reason。差额可正负，件数单次±1000000以内；禁止负余额，成品半成品数量与净重须同时归零或同时保留。资料编辑仅specification/organization/purity；已有业务引用不能改快照。期初和调整不构造虚假报工。

预警处理字段version、reason（处理结果1～500字）。预警平均损耗严格超过理论损耗+允许超差才生成；未配置或停用超差默认为0g。相等正常，处理不改写实际损耗。待处理不能归档，已处理或来源作废可归档，批量最多100条且原子提交。

Excel：GET /warehouse-order/template下载xlsx；POST /warehouse-order/import为multipart file+factoryId，最多5MB/1000行，不支持公式；解析后只返回待填明细，不过账。模板包含库存/资料/来源行ID，用于稳定关联；下拉选项展示ID。确认仍执行完整事务及权限校验。

权限前缀metal:warehouse、inventory、warehouse-order、process-batch、work-report、warning-rule、warning-record；以菜单权限树实际操作为准。新增权限不自动分配现有演示角色。管理员在角色管理按岗位选择菜单和按钮，再重新登录生效。
