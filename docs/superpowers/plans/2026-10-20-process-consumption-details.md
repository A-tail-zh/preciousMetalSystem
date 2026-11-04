# 工序消耗明细及批次单据展开实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 在生产工单详情按产品明细查看各工序重量平衡，并逐级展开批次、仓储单据及物料行。

**Architecture:** 新增只读消耗查询，不改变现有结算、报工、库存及冲销。工序取工单步骤快照；有效仓储行按批次预聚合，再关联报工，避免一对多连接重复累计。前端独立组件负责分页展开、状态和请求上下文隔离。

**Tech Stack:** Spring Boot、JdbcTemplate／参数化MySQL查询、Vue3、TypeScript、Element Plus、JUnit、Playwright Chrome。

**Spec:** 本文件“展示及业务口径”是本计划完整设计依据；依据用户已确认的工序消耗分析及当前仓储、报工代码制定。

## 全局约束

- 仅增加只读展示及必要查询；不开放还料修改，不重新结算，不调整库存或生产状态。
- 不修改数据库结构、历史记录、损耗公式、工厂隔离、8小时会话及原权限配置。
- 工序用工单步骤快照，关联键使用workOrderId、itemId、processId、batchId、orderId，不按名称或产品ID归并。
- 重量十进制字符串，沿用6位小数；前端不使用浮点数重新累计。
- 隔离测试数据并清理；保留测试审计；完成后后端打包重启并检查接口；不自动提交GitHub。

## 审查重点

- 同产品重复工单明细、同名工序不得串行；关联ID层层验证。
- 多单据、多批次、多物料连接不得放大重量；空批次与无单据步骤仍可展示。
- 已冲销单据可追溯但不计数；已作废报工不成为当前损耗。
- 旧请求不得回写新工单、产品、工序或批次，包括失败、finally及关闭重开。
- 查询需分页并受上下文权限约束；失败不得伪装成0、无记录或回退全厂数据。

## 展示及业务口径

### 入口与层级

生产工单详情当前“报工记录”区域改为“工序消耗明细”；保留原报工查看及批次纠错入口。沿用当前产品明细选择，左表明确显示当前产品编号、名称、规格。同产品重复明细按itemId区分。工序按保存的sequenceNo排序，未开始也显示。

层级：产品明细 → 工序表 → 批次表 → 单据表 → 单据物料行只读抽屉。

工序表：序号、工序、原料领用、半成品领用、领料合计、原料还库、半成品／成品产出、实际投入、损耗、完成件数、批次数、状态。单位g／件写入表头；宽表内部滚动，工序列与展开入口保持清晰。

批次表：批次号、状态、领料合计、原料还库、产出、实际投入、损耗、产出件数、结算完成件数、平均损耗、结算时间（已有字段可得时）。行内可查看原报工；结算时间如无可靠来源显示“—”，不替代为单据日期。

单据表：编号、业务日期、类型、状态、净重合计、数量合计、经办人；展开物料行包含编号、名称、分类、规格、毛重、杂质、净重、数量、单位名称、来源出库单号。原料数量按业务值展示，不把0件当漏填；没有关联来源显示“—”。不增加独立单据编辑／冲销动作。

### 公式和状态

- 原料领用＝有效PRODUCTION_OUT中的RAW净重；半成品领用＝其中SEMI净重。
- 领料合计＝两类之和；不把其他出库类型计入批次生产消耗。
- 原料还库＝有效RAW_RETURN净重。
- 产出＝有效SEMI_IN＋FINISHED_IN净重。
- 实际投入＝领料合计－原料还库；说明这是扣除退料后的投入，并非纯损耗。
- 损耗／暂算差额＝实际投入－产出。
- 仅status=POSTED单据参加当前重量、件数；冲销单据可在明细中查看，显著标注“不计入汇总”。
- OPEN：显示“未结算／暂算”，差额可能包含尚未登记的产出、退料，不标异常损耗，不触发预警，不累计为最终损耗。平均损耗显示“—”；产出件数单列，未结算完成件数显示“—”。
- SETTLED：有效报工提供最终损耗、完成件数、平均损耗及正常／异常状态，单据重量用于依据。若账据与报工不一致，保留两者并标“待核对”，不静默覆盖或重新计算历史。
- VOID：默认不计入工序汇总；“包含已冲销批次”开关只控制明细显示，汇总不变。批次内保留原单据及作废报工追踪。
- 工序状态：未开始／进行中／部分结算／已结算／待核对；全部批次VOID时显示“无有效批次”，不推断工序完成。已结算不等于整张生产工单完成。
- 有OPEN和SETTLED混合时：重量列展示全部有效批次当前累计；损耗分别注明“已结算X／暂算Y”，不把两者合为最终损耗；平均损耗只按已结算损耗÷已结算完成件数计算，分母0显示“—”。
- 不跨工序累加投入或件数作为整单原料消耗；半成品内部流转会重复计重。不新增整单投入总计。
- 多原料的现有物料损耗为领料比例分摊，注明“分摊损耗”；本次不推算各原料实际转化量。
- 历史缺失批次／快照／报工：明确缺失及待核对，缺失为null／“—”，真实无业务才显示0；不猜测归属、不补写历史。

示例：工序一领300、退100、产190，实际投入200、损耗10。工序二领上游半成品190并补原料20，产200、退0，实际投入210、损耗10。不得把两工序实际投入200＋210称为工单消耗410g。

## 查询设计

新增以下只读接口，最终名称以仓储控制器既有路由规范为准：

1. GET /metal/work-report/consumption/steps?workOrderId=&itemId=：返回全部快照工序及聚合。字段区分rawIssuedWeight、semiIssuedWeight、issuedWeight、rawReturnedWeight、outputWeight、netInputWeight、settledLossWeight、provisionalDifferenceWeight、settledCompletedQuantity、outputQuantity、batchCount、openBatchCount、state、warnings。
2. GET /metal/work-report/consumption/batches?workOrderId=&itemId=&processId=&includeVoided=&pageNum=&pageSize=：分页批次，返回batchVersion、reportId及状态、当前依据重量与可靠报工快照。
3. GET /metal/work-report/consumption/orders?workOrderId=&itemId=&processId=&batchId=&pageNum=&pageSize=：分页对应单据及净重／数量合计，注明计入状态。
4. GET /metal/work-report/consumption/order-lines?workOrderId=&itemId=&processId=&batchId=&orderId=&pageNum=&pageSize=：分页白名单物料字段。

响应包含上下文ID和读取时间；一次响应在一致读取事务中完成。多次展开不承诺跨请求同一数据库快照；提供“刷新全部”清空展开缓存重新读取，同步刷新上级，避免混用过时缓存。并发登记／冲销后的刷新结果准确。

权限：工序汇总沿用现有summary的work-order:query或work-report:list；批次及单据展开沿用step的work-order:query或work-report:query。每级服务均校验工厂及全部父子关系。仅返回本上下文必要字段，不要求产品／工序管理查看权限，也不扩大通用仓储单据查询权限。按钮冲销仍用原process-batch:reverse并保留原校验。

参数化SQL：有效单据行先按batch_id聚合，报工单独按有效batch_id归并，步骤LEFT JOIN批次聚合。不用JOIN后DISTINCT掩盖重复计数，不逐工序执行N+1查询。分页默认16，最大100；从现有索引执行EXPLAIN，若出现性能问题先调整查询，不在本范围自动迁移新增索引。

## 文件及实施任务

### 任务1：后端消耗汇总与口径

Files：新建 backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/ProcessConsumptionService.java；修改 backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WarehouseController.java；新增 backend/ruoyi-metal/src/test/java/com/ruoyi/metal/service/ProcessConsumptionServiceTest.java。

- [x] 先添加失败测试：两工序例子、重复产品明细、多批次多行不重复汇总、未开始、OPEN与SETTLED混合、0完成件数、VOID、历史缺失及快照工序更名。
- [x] 实现只读步骤汇总及状态，按工单步骤顺序返回；与原LossCalculator及报工比对，异常提示不写数据。
- [x] 运行测试确认通过，复审JOIN基数、精度、缺失与0区别以及分类公式。

### 任务2：分页展开、隔离及权限

Files：上述service/controller；新建 ProcessConsumptionAccessTest.java，目录与任务1测试相同。

- [x] 先写失败测试：跨厂、跨工单item、同产品不同item、错process、错batch、错order、无权限、只有仓储权限、工单查看权限、报工列表／详情权限组合。
- [x] 实现批次、单据及物料行分页与白名单；保留已有接口行为。
- [x] 覆盖冲销单据可见但不计数、历史缺字段、空分页、过大pageSize、中文长字段；执行EXPLAIN确认不出现逐条查询。
- [x] 查询并发登记／冲销的读取一致性；通过后复审父子ID及权限边界。

### 任务3：工序表与批次展开

Files：修改 frontend/src/views/metal/work-order/WorkOrderDetail.vue、frontend/src/api/metal/warehouse.ts；新建 frontend/src/views/metal/work-order/ProcessConsumption.vue、frontend/tests/process-consumption.spec.ts。

- [x] 先添加失败浏览器回归：产品明细切换、工序顺序、长表、OPEN／SETTLED区别、空态与失败重试。
- [x] 接入新组件，展示工序表和懒加载分页批次；保留原报工查看及批次纠错。
- [x] 每层独立请求序号及上下文；切工单／item、关闭、卸载令旧响应失效；旧成功、失败及finally均不能覆盖新数据。
- [x] 加载中禁用对应依赖操作；展开失败显示重试，绝不显示假0。重复展开复用当前上下文缓存，“刷新全部”使其全部失效。
- [x] 更新 frontend/tests/work-order-report-summary-race.spec.ts 等依赖旧报工区域测试，运行并通过。

### 任务4：单据与物料依据

Files：新建 frontend/src/views/metal/work-order/ConsumptionDocuments.vue；修改上述组件及 frontend/src/views/metal/warehouse-common/WorkReportDialog.vue（仅入口／说明衔接）；扩展 process-consumption.spec.ts。

- [x] 先写失败测试：展开多单据、明细分页、原出库来源、冲销标记、普通工单查看账号与只读权限。
- [x] 用只读抽屉展示单据行，保持父工单／产品／工序／批次标题可见。复用时间格式、重量字符串、原料／半成品／成品标签。
- [x] 弹窗中增加“查看工序消耗”衔接；不存在可靠报工时不伪造报工ID；未结算仅查看依据，不开放原批次纠错替代结算。
- [x] 覆盖A慢B快、A失败B成功、最新失败旧成功、关闭重开、翻页、刷新及组件卸载，防止历史异步回归。
- [x] 1920、1440、768、390px检查内部横滚、长规格换行、展开可操作；不扩宽整页。

### 任务5：隔离真实业务验收

Files：新增 tools/verify-process-consumption-live.mjs；复用 tools/warehouse-test-fixture.mjs、tools/local-test-client.mjs；新增 frontend/tests/process-consumption-live.spec.ts（确有必要时与工具分工，避免重复）。

- [x] 建立至少两产品、重复同产品明细、两道工序及分次领料的隔离工单，真实执行领料→部分退料／产出→结算→上游半成品领用→下一工序。
- [x] 验证300／100／190例子，OPEN暂算，部分批次结算，多批次加权平均，0g原料还库，原料和半成品混合领用。
- [x] 核对每层合计与实际单据、库存及原报工一致；读取操作前后库存、报工、预警和单据无额外变化。
- [x] 在无后续引用时实际冲销一批隔离数据，确认汇总排除、明细保留；有后续引用时保留原冲销限制。
- [x] 改当前工序名称后验证工单快照不变；隔离账号验证权限与跨厂拒绝。
- [x] 用Chrome受控延迟验证各层串行保护；保存宽屏、展开及窄屏截图。
- [x] finally清理隔离业务及账号资料，核对无残留，保留审计。不触碰用户业务数据。

### 任务6：全量相关验证、复审和交付

- [x] 后端：在backend运行 mvn -o -gs ../build-settings.xml -s ../build-settings.xml -pl ruoyi-admin -am package -DskipTests=false；记录总数、失败、跳过及原因。
- [x] 前端：在frontend运行 node node_modules/vue-tsc/bin/vue-tsc.js --noEmit；node node_modules/vite/bin/vite.js build；Playwright运行新增消耗、工单报工汇总竞态、批次纠错生命周期及仓储相关回归。
- [x] 审查精度、JOIN重复、历史与冲销、权限、请求竞态、分页性能及无写副作用；发现问题修复并复验。
- [x] 验证通过后按项目脚本重启本地后端，检查健康与关键只读接口，确认真实数据清理。
- [x] 报告已完成／未完成、实际测试数量、跳过原因、需要人工核对的历史异常及运行限制；不宣称未执行测试通过，不提交GitHub。

## 当前状态

用户已确认并完成实施、相关回归及独立代码复审。无数据库迁移。验证结果、可选测试跳过及限制见 docs/工序消耗明细与批次单据验收-2026-10-20.md。
