# 半成品还库仓库锁定与释放领用预览一致性修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox tracking. Independent final review is required; no automatic Git commit or push.

**Goal:** 修复先选出库来源导致空仓库锁死，以及释放领用Excel预览接受保存端必拒绝的件重组合。

**Architecture:** 保留既有表单上下文隔离和保存端权威校验。来源仓库从已有候选取得；释放校验统一按releaseId汇总，预览、前端提交与事务内保存保持同一件重规则。

**Tech Stack:** Vue3/TypeScript/Element Plus、Java/Spring/JdbcTemplate、JUnit、Playwright。

**Spec:** docs/半成品流转复测与审查-2026-10-21.md。

## 全局边界

- 仅修复本轮两项缺陷；不调整来源路线、客户、权限、损耗、释放用途及结算规则。
- 不新增数据库字段、不修改历史库存或单据、不自动授权。
- 保留旧请求、UUID幂等、版本、工厂隔离和事务校验。
- 实测净重不按件数比例推算。4件40g可分为1件5g与3件35g；不强制每件10g。
- 释放余额全部件数和全部重量必须同时用完：4件40g领4件39g或3件40g均拒绝；4件40g或2件19g允许。
- 原料还库及无释放记录的正常出库维持原行为。
- 测试使用隔离数据并清理，审计保留；不得清理用户业务数据；不提交或推送GitHub。

## 审查重点

1. 来源先选/仓库先选、同工单多物料、切换来源时仓库回填与锁定一致。
2. 来源A慢B快、关闭重开、切换工厂或类型，旧响应不得恢复A仓库或批次。
3. 多行同释放池与多释放池同库存，分别约束池额度并共享真实库存上限。
4. 本单已有明细、导入追加、编辑排除自身，累计不漏算或重复计算。
5. 6位小数和并发额度变化：预览不预占，保存锁内重算，失败整体回滚。

## 任务一：修复半成品还库来源仓库回填（P2）

**Files**
- Modify: frontend/src/views/metal/warehouse-common/WarehouseOrderForm.vue（sourceOrders、sourceOrderChanged、batchChanged、仓库禁用条件及加载失败处理）。
- Test: frontend/tests/warehouse-work-order-context.spec.ts。
- Verify only: backend已有out-lines候选包含warehouseId，优先不修改接口。

**接口与状态约定**
- sourceOrders保留{id,name,batchId,warehouseId}，warehouseId必须来自当前有效出库来源候选，不能取用户输入或当前默认仓库。
- SEMI_RETURN有效来源选中后，form.warehouseId固定为原出库仓库；RAW_RETURN保持原逻辑。
- 同一来源单据若候选出现缺失/冲突warehouseId，视为无效上下文，不能随意采用第一条。

- [x] 将已复现问题转换为功能断言：新建半成品还库，直接选OUT-A，仓库自动显示原库房、保持只读、依赖加载结束后添加可用；修改前必须失败。
- [x] 为sourceOrders补充warehouseId，校验候选属于当前工厂且仓库存在于已加载可用仓库集合。
- [x] sourceOrderChanged先使旧明细/子弹窗及请求失效，清空旧行、批次版本、工单、产品和工序；同步确定来源仓库，再加载批次工单上下文。
- [x] 依赖请求上下文加入warehouseId，并继续校验openEpoch、detailEpoch、sourceOrderId、factoryId、type及目标批次。旧success/catch/finally均不得修改当前状态。
- [x] 加载时禁用物料添加、导入和提交；来源父选择允许重新选择。最新加载失败清空来源绑定的有效批次状态，展示重试，不能留下不可解除的空仓库禁用状态。
- [x] 清空来源时清理失效明细及来源绑定状态并恢复仓库可选；保留仓库值的前提是它仍属于当前工厂。关闭/重开和切换类型均沿用请求失效机制。
- [x] 覆盖：先选仓库后选来源、先选来源、清空重选、两仓库来源、最新请求失败、A慢B快、关闭重开及仅仓储权限账号。

Run: frontend目录执行 npx.cmd playwright test warehouse-work-order-context.spec.ts warehouse-order-open-race.spec.ts。
Expected: 新用例从失败变为通过，原料还库及入库上下文旧用例不退化。

## 任务二：统一释放领用预览及保存件重校验（P3）

**Files**
- Modify: backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseImportService.java。
- Reuse: backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/SemiFlowRules.java 的release(availableQuantity,availableWeight,requestedQuantity,requestedWeight)。
- Verify: SemiFlowService.reserveUse及WarehouseService保存事务继续调用同一规则。
- Test: backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/WarehouseImportServiceTest.java、SemiFlowRulesTest.java。

**汇总约定**
- 以releaseId为键，分别汇总该池在本次导入中的件数、净重和原始Excel行号。
- 每一行先校验来源和精度；读取可信释放余额，最后按每个池的完整合计调用共用规则。
- 不跨releaseId合并额度；同一inventoryId另外共享实物库存上限；不按单行等于整池余额来判断完整导入。
- 后端预览校验上传文件范围；前端负责把本单已有待提交明细与导入结果合并检查。保存端始终以最终完整请求在事务内重新校验。

- [x] 新增失败用例：4件40g释放池导入4件39g，预览必须拒绝；修改前应失败。
- [x] 补充3件40g拒绝、4件40g通过、2件19g通过、重复来源分行累计、多释放池同库存及六位小数测试。
- [x] 保留现有逐行来源、客户、路线、件数及重量上限校验；在完整文件汇总完成后按releaseId调用共用件重规则，避免另写一套差异公式。
- [x] 错误信息包含Excel相关行号、释放记录、可用件数和净重，说明“领完全部件数时须同时领完该释放余额净重；领完全部净重时须同时领完件数”。
- [x] 校验失败不得返回可追加的部分预览结果，不扣库存、不写领用关联、不预占额度。
- [x] 回归保存端相同输入，确认合法输入通过，非法输入一致拒绝；并发时仍按事务内最新额度拒绝且完整回滚。

Run: backend目录执行 mvn.cmd -o -pl ruoyi-metal -am test '-Dmaven.test.skip=false' '-Dtest=WarehouseImportServiceTest,SemiFlowRulesTest,SemiFlowServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'。
Expected: 新增预览用例先红后绿，现有保存/来源/还库用例通过。

## 任务三：前端合并待提交明细检查

**Files**
- Modify: frontend/src/utils/stock-availability.ts（复用已有BigInt微克精度工具，增加释放件重合计校验）。
- Modify: frontend/src/views/metal/warehouse-common/WarehouseOrderForm.vue（validateOutgoingLines、物料确认、导入追加及save）。
- Test: frontend/tests/stock-availability.spec.ts、warehouse-work-order-context.spec.ts；必要时新增semi-release-allowance.spec.ts。

**新增接口**
- releasedTransferError(balance:{quantity:string|number;netWeight:string|number}, lines:{quantity?:string|number;netWeight?:string|number}[]):string。
- 函数只接收同一releaseId的待提交行，返回空字符串或明确件重一致错误，不改变输入；重量使用十进制字符串/BigInt，不用浮点近似判断等于。

- [x] 补失败用例：清单已有2件20g，再导入2件19g，即使单独导入未超额度，合并后4件39g必须在追加前拒绝。
- [x] 每次确认添加/编辑、导入追加及保存前，组合最终待提交集合，编辑排除原行，再按releaseId校验；共享库存上限校验继续执行。
- [x] 导入遇到任何非法组合整批不追加；提示释放池及清单已占用、当前余额和件重规则，不静默改重量或数量。
- [x] 保存前刷新相关释放/库存候选，使用当前上下文的最新余额；加载期间禁止提交，旧响应不得写回。前端通过不代替后端事务校验。
- [x] 覆盖不同池同库存、删除恢复、编辑排除自身、重复导入、六位小数、额度并发变化、A慢B快和非生产旧行为。

Run: frontend目录执行 npx.cmd playwright test stock-availability.spec.ts warehouse-work-order-context.spec.ts semi-release-allowance.spec.ts（最后一个仅在新增后加入）。
Expected: 组合非法请求不进入清单或保存；正常分行领用不误拒绝。

## 任务四：完整验收、复审与交付

- [x] 修改tools/verify-semi-flow-live.mjs加入正常断言：4件39g导入预览应拒绝；4件40g正常，非法保存不改变库存。将已有诊断脚本转为长期回归，不能以“缺陷仍存在”作为通过条件。
- [x] 使用隔离账号和真实页面按自然顺序办理半成品还库，验证来源仓库只读回填、能够添加/导入并实际保存正确原库存；还库不自动释放。
- [x] 真实Excel验证：本单已有2件20g+导入2件20g通过，+2件19g拒绝；多来源池、并发、版本冲突、权限与跨厂拒绝。
- [x] 运行完整后端 mvn.cmd -o test '-Dmaven.test.skip=false'；运行上一轮13个相关浏览器文件及新增回归，明确报告通过、失败、跳过和原因。
- [x] frontend目录运行 npm.cmd run build:prod（包含类型检查）；检查1920、1440、768、390px的还库仓库、错误提示和来源下拉。
- [x] 独立代码审查重点核对来源上下文、导入合计、精度、保存边界及旧请求兼容；修复确定高优先级问题后复跑对应回归。
- [x] 本轮无需数据库迁移。后端代码变更验证通过后重新打包、重启本地后端并检查健康及关键接口；不使用仅前端通过代替部署验证。
- [x] 清理隔离业务数据、保留审计与验证证据；交付报告逐项列完成情况、测试数量、未完成与人工待办。不提交或推送GitHub。

## 实施次序和验收结论

先修P2录入阻塞，再修P3后端预览、前端合并校验，最后完整回归和部署复审。两个问题可分别验收，但最终需验证组合流程。

成功标准：按页面顺序先选来源即可办理还库；非法件重组合在预览或追加时有明确错误；合法分行输入可保存；并发情况下保存仍无超扣或部分写入。

实施和验收已完成，详见 docs/半成品还库与导入校验修复验收-2026-10-21.md。历史缺失来源库存及角色授权不属于本轮修复，未自动处理。
