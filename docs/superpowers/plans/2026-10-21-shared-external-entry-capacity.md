# 多释放池共享接入额度修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task; run test-first checks and one independent final review. Do not commit or push GitHub.

**Goal:** 在手工添加、编辑、分次导入及保存前，阻止同一清单多个释放池合计超过目标工序共享接入额度。

**Architecture:** 既有生产库存候选数组保持兼容，在响应顶层增加绑定目标上下文的共享额度。前端只对最终待提交清单中经候选确认的外部释放来源汇总件数，保留单池、实物库存和件重检查。后端继续复用entryCapacity，并保留原事务内逐行权威校验。

**Tech Stack:** Vue3/TypeScript/Element Plus、Java/Spring/JdbcTemplate、JUnit、Playwright。

**Spec:** docs/半成品流转代码审查-2026-10-21.md。

## 全局约束

- 本轮只修跨释放池共享接入额度漏检，不改变加工路线、客户限制、损耗、还库、释放及结算规则。
- 不修改数据库结构、历史业务数据、库存余额或现有权限；不新增权限或依赖产品/工序管理权限。
- 候选范围、选择值（库存ID和释放ID）、单池件重规则保持不变。
- 前端校验不预占后端额度，不能保证刷新后别人不会领用；保存必须继续锁内重算并整体回滚。
- 原材料和本单半成品不计入外部接入合计；同释放池多行按各行数量求和，不按释放ID去重。
- 既有已生效领用已经计入服务端entryCapacity，不在前端重复减一次；只汇总本张未提交清单。
- 测试使用隔离数据并清理，保留审计。不自动提交或推送GitHub。

## 审查重点

1. 不同库存多池及同库存多池，共享容量只能使用一次，物理库存另行共享。
2. 多次导入须与已有清单合并；错误文件整批不追加，不能部分填入。
3. 编辑排除原行、删除恢复可用量；不改变其他行内容或自动缩减件数。
4. 容量为0、候选为空、缺失/损坏上下文及历史异常，不能从某个候选上限猜测全局额度。
5. A慢B快、切换仓库/工厂/工单/产品明细/工序、关闭重开及并发变化，过期额度不得参与新目标校验。

## 任务一：补充候选共享额度快照

**Files**
- Modify: backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/WarehouseService.java。
- Modify: backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WarehouseController.java。
- Reuse: backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/SemiFlowService.java 的entryCapacity及candidates。
- Test: backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/ProductionStockSnapshotTest.java（新增）及现有候选权限测试。

**接口决定**
- GET /metal/warehouse/options/stocks在绑定生产工单时保持data为原候选数组，并增加顶层entryContext。
- entryContext包含字符串ID factoryId、warehouseId、workOrderId、itemId、processId，以及十进制整数字符串externalEntryCapacity。
- externalEntryCapacity取已验证目标工序的entryCapacity(itemId,sequenceNo)，为所有外部释放池共用，不是每池独立额度。
- 容量为0或data=[]时仍返回完整上下文；第一工序外部容量为0。历史负容量沿用现有报错，不静默转0。
- 非生产候选响应保持原样，旧客户端继续读取data即可。
- WarehouseService新增productionStockSnapshot(factoryId,warehouseId,workOrderId,itemId,processId)，返回候选和上下文；Controller组装success(stocks).put(entryContext)。可使用嵌套record ProductionStockSnapshot(List<Map<String,Object>> stocks,Map<String,Object> entryContext)。
- 在readOnly、REPEATABLE_READ事务中取得同一上下文的候选和容量；不分别发两个请求。先执行原候选权限及工厂/仓库/目标归属校验，不返回完整工单数据。

- [x] 先补失败用例：目标容量4，两池各4，响应顶层明确只有共享4；空候选仍有容量上下文。
- [x] 新增快照返回并保留原data数组、单池releaseRemainingQuantity/Weight及availableQuantity/Weight含义。
- [x] 覆盖容量0、首工序、错误产品明细/工序、跨厂、不可用仓库，以及仅仓储录入权限的账号。
- [x] 确认容量、候选及上下文来自同一读取快照，旧客户端和非生产候选不退化。

Run: backend目录 mvn.cmd -o -pl ruoyi-metal -am test '-Dmaven.test.skip=false' '-Dtest=ProductionStockSnapshotTest,SemiFlowServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'。
Expected: 新用例先失败，实施后全部通过；不增加数据库写入。

## 任务二：前端最终清单共享校验

**Files**
- Modify: frontend/src/api/metal/warehouse.ts，定义ProductionStockResponse和EntryContext，更新productionStockOptions返回类型。
- Modify: frontend/src/utils/stock-availability.ts，增加externalEntryError。
- Modify: frontend/src/views/metal/warehouse-common/WarehouseOrderForm.vue，接入共享状态与验证。
- Test: frontend/tests/stock-availability.spec.ts及warehouse-work-order-context.spec.ts。

**函数和状态**
- externalEntryError(capacity:string|number,lines:{quantity?:string|number;releaseId?:string|null}[]):string，按非空releaseId行累计数量，使用整数精确比较，返回空串或包含共享可用量与清单合计的错误。
- sharedEntryContext使用undefined表示尚未加载/已失效；与明确容量0区分。校验ID必须匹配当前工厂、仓库、工单、产品明细和工序。
- 外部来源由当前有效候选校验库存ID及releaseId关联后确认；不根据名称、类别文案或客户手填值判断。
- 缺失或损坏entryContext时禁止外部释放领用确认及提交，提示刷新/重试；不回退逐池额度推算。合法纯原料或本单半成品清单不因缺少外部额度而被误拒绝。

- [x] 将.runtime/semi-code-review/multi-pool.repro.spec.ts的缺陷诊断改为正常功能回归：容量4，两池各领4时，第二行不能加入，不能发出保存请求；修改前必须失败。
- [x] 写纯函数用例：A2+B2等于4通过；A4+B1拒绝；同池重复行合计；原料/本单半成品不计外部；容量0及合法整数边界。
- [x] 候选加载成功时同时发布data和匹配的entryContext。父级变化、关闭及卸载立即使旧共享状态失效；旧成功/失败/finally不更新新状态。
- [x] validateOutgoingLines先组合最终清单：新增=原清单+本次行；编辑=原清单排除自身+编辑后行；保存=当前清单本身，不能重复拼接。
- [x] 保留每池件重及真实库存校验，再对最终清单所有外部来源合计调用externalEntryError。
- [x] 添加/编辑拒绝时保留输入；导入拒绝时整批不追加；不得自动改件数、净重或换释放池。
- [x] 保存前刷新候选及共享快照，加载期间锁定提交和目标；刷新后不足则保留清单并提示核对，不发保存请求。

Run: frontend目录 npx.cmd playwright test stock-availability.spec.ts warehouse-work-order-context.spec.ts。
Expected: 单池旧流程通过，多池超额在添加或导入时拒绝，保存刷新仍可阻止并发变化后的错误组合。

## 任务三：明确展示共享口径并补齐交互矩阵

**Files**
- Modify: WarehouseOrderForm.vue。
- Test: warehouse-work-order-context.spec.ts，必要时新增shared-entry-capacity.spec.ts。

- [x] 生产领料区域增加简短提示：本工序可外部接入X件，本单已填Y件，剩余Z件；注明本单未提交不预占，保存重新核对。
- [x] 候选仍展示各池自身可领量，避免把每池上限改成互不一致或隐藏当前已选来源。
- [x] 编辑计算本条可用量时排除自身；删除后实时恢复本单可用量；如果并发后总量已超额，明确展示超额，不仅将负数显示0而掩盖错误。
- [x] 覆盖下表，并保留来源切换、仓库回填和旧隐藏入口状态。

| 场景 | 预期 |
|---|---|
| 共享4，A2+B2，单池件重均合法 | 可提交 |
| 共享4，A4+B4 | 第二条拒绝，无保存请求 |
| 同库存多个池累计超过库存 | 即使共享容量够，也因实物上限拒绝 |
| 清单A3，再导入B2 | 文件单独预览可合法，合并后整批不追加 |
| 清单A2，导入B2 | 可追加 |
| A2+B2，编辑B为3 | 排除旧B后计算为5，拒绝且保留输入 |
| 删除A2，再加C2 | 恢复容量，合法时通过 |
| 容量变为3，清单已有4 | 保存前刷新拒绝，不提交 |
| 两池同源或重复导入行 | 数量逐行累计，不按池去重漏算 |
| 候选为空或容量0 | 明确展示状态，不允许新增外部领用 |
| A慢B快/关闭重开/切换仓库 | 只使用最新目标共享快照 |
| 原料补领/本单半成品领用 | 不错误消耗外部接入额度 |

Run: frontend目录 npx.cmd playwright test warehouse-work-order-context.spec.ts stock-availability.spec.ts warehouse-order-open-race.spec.ts。
Expected: 组合和生命周期用例通过，原有来源与仓库回归不退化。

## 任务四：真实验收、复审和交付

- [x] 在tools/verify-semi-flow-live.mjs补充不同库存两池及同库存多池隔离场景：原工单关闭并释放，目标前置容量4，页面阻止合计8；合计4能够真实保存。
- [x] 真实Excel分次导入验证本单既有行参与共享校验；不改变后台“文件预览不预占”的语义。
- [x] 保留后台文件预览importedExternal汇总及reserveUse逐行entryCapacity权威校验，不为修前端削弱保存保护；直接绕过前端的超额请求应整体回滚，库存、释放额度、接入数量不变。
- [x] 并发账号消耗容量后，验证刷新拒绝或服务端事务拒绝；测试不宣称浏览器刷新可以消除所有并发窗口。
- [x] 运行完整后端回归及package，执行13个既有相关浏览器文件和新增用例，前端npm.cmd run build:prod。
- [x] 检查1920、1440、768、390px共享提示、长错误信息及下拉；截图并复核，不撑宽页面。
- [x] 独立代码审查共享/单池/实物三层额度、请求快照与保存边界；发现确定问题后补回归修复。
- [x] 无需数据库迁移。后端变更测试通过后重新打包并重启本地服务，检查健康和候选接口。
- [x] 清理隔离业务数据，审计日志保留；报告各项完成状态、实际测试数量、跳过原因、未完成与验证限制。不提交或推送GitHub。

## 完成标准

用户按任意合法来源组合填写时，前端能够在加入或提交前准确识别共享额度超限；合法组合不误拒绝。实际保存仍受后端事务保护。原料、内部半成品、单池件重和旧候选客户端保持兼容。

本轮实施和验收已完成；详见 docs/共享外部接入额度修复验收-2026-10-21.md。未提交或推送GitHub。
