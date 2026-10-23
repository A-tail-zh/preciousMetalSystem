# 工单接口

根路径 /metal/work-order，使用若依Bearer认证。普通人员限定本厂，管理员可跨厂。主键和十进制值返回字符串，版本为整数，时间为yyyy-MM-dd HH:mm:ss（计划秒必须00）。

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | /list | 16条默认分页，创建顺序 |
| GET | /{id} | 快照、明细、计划、审核历史 |
| POST | 根路径 | 创建，UUID请求幂等 |
| PUT | 根路径 | 编辑待审核或驳回工单 |
| DELETE | /{ids} | 最多100张，原子逻辑删除 |
| POST | /{id}/approve | 通过并等待领料 |
| POST | /{id}/reject | 驳回，reason必填 |
| POST | /{id}/resubmit | 驳回重新提交 |
| GET | /options/factories | 可选工厂 |
| GET | /options/lines?factoryId=… | 工厂产线 |
| GET | /options/owners?factoryId=… | 本厂有效负责人 |
| GET | /options/products?lineId=… | 产线关联产品 |
| GET | /options/products/{id}/processes?lineId=… | 产品工艺 |

创建和编辑JSON字段：requestId（新建UUID）、id（编辑）、version、customer、factoryId、productionLineId、ownerId、orderDate、items。每项包含id（原明细）、productId、quantity、plans；每道计划包含processId、startTime、endTime。完整样例和字段约束见详细设计。审核操作提交version，驳回另提交reason。

状态PENDING、REJECTED、APPROVED、IN_PROGRESS；本期不提供进入IN_PROGRESS接口。普通编辑不能设置状态、编号、审计、快照或实际时间。编号WOyyyyMMddNNNNNN在成功创建时分配。审核通过后禁止修改删除；版本冲突保留前端输入。

权限前缀metal:work-order，操作list/query/add/edit/remove/approve/reject/resubmit。选项、详情和写入全部检查工厂范围。产品工序快照保持历史；工艺时间顺序校验与关联有效性在事务中执行。有效工单阻止关联解除、产品及工序删除；历史工单阻止产线物理删除。

2026-10-13更新：工序时间全部改为人工填写的实际时间。items.plans名称为兼容保留，startTime/endTime与actualStartTime/actualEndTime均返回实际时间，创建编辑写入实际字段。详情无计划/实际切换，旧计划仅归档，不转换成实际时间。

## 2026-10-13最终调整：仅计划时间

用户确认实际时间不需要。本次使用V8，保留原计划；只有原计划为空时才把上阶段人工填写时间带入计划。新增编辑写入计划字段，详情仅显示计划时间，无计划/实际切换；审批仅检查计划时间，不再要求实际时间。已通过133项后端测试、13组真实接口及页面审核流程回归，前端构建成功。此说明替代上阶段实际时间方案。
