# 工厂接口

接口遵循若依响应格式，通过 `Authorization: Bearer <token>` 验证登录及操作权限。普通人员只能查看/编辑本人所属有效工厂；创建、删除、产线归属调整仅限超级管理员。详情、列表与选项均检查工厂范围。

| 方法 | 地址 | 权限 |
|---|---|---|
| GET | /metal/factory/list?pageNum=1&pageSize=16 | metal:factory:list |
| GET | /metal/factory/{id} | metal:factory:query |
| GET | /metal/factory/line-options?factoryId={id} | metal:factory:add 或 metal:factory:edit |
| POST | /metal/factory | metal:factory:add |
| PUT | /metal/factory | metal:factory:edit |
| DELETE | /metal/factory/{id1},{id2} | metal:factory:remove |

列表响应为 `{code:200,msg:"查询成功",rows:[...],total:数字}`，详情和选项为 `{code:200,msg:"操作成功",data:...}`。业务失败返回若依非200业务码和中文msg，客户端同时兼容HTTP异常。pageNum至少1，pageSize为1～100。

新增请求：

```json
{
  "factoryName": "工厂A",
  "area": "12.5000",
  "address": "详细地址",
  "introduction": "工厂简介",
  "productionLineIds": []
}
```

编辑额外传入 `factoryId`（字符串）和 `version`（非负整数）。产线ID数组必须传，空数组解除全部关联。最多1000条且不能重复。新增不允许指定ID。工厂名称最多100字符，地址最多500字符，简介最多5000字符；名称和地址去首尾空格后必填。面积大于0，最多10位整数4位小数，不允许超精度舍入。

详情及列表对象包含：`factoryId、factoryName、area、address、introduction、version、updateTime、productionLines、productionLineIds`。ID及面积为字符串。时间采用Asia/Shanghai，格式`yyyy-MM-dd HH:mm:ss`。每个产线对象含`productionLineId、productionLineName、factoryId`。

新增返回 `{code:200,msg:"操作成功",factoryId:"生成的ID"}`，编辑和删除返回若依成功结果。服务端从认证信息取得操作人，不接受客户端指定审计字段。

厂名允许重复。删除采用逻辑删除且批量原子提交（最多100个ID）；有关联产线、有效人员或角色时整体拒绝。不存在或已删除记录返回业务错误。编辑版本冲突返回“数据已被修改，请刷新后重试”，前端保留输入。表格工厂ID列为连续显示序号，接口factoryId始终为稳定主键。

关联修改会锁定工厂及相关产线，在事务中重新校验归属，禁止抢占其他工厂的产线。后续产线模块调整归属时，也必须锁定相关工厂并检查未删除状态，不能直接绕过服务规则更新factory_id。
