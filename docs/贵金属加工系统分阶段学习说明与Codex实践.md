# 贵金属加工系统分阶段学习说明与 Codex 实践

本文按“先搭骨架，再理解配置、数据、认证和业务”的顺序说明当前项目，适合边看代码边学习。核对日期：2026年10月15日。这里的阶段是建议的学习顺序，不代表项目真实开发时间线。

本文侧重项目原理说明。你随后粘贴的CSDN文章正文已用于配套《Codex贵金属加工系统开发与维护实战指南.md》，其中包含按步骤组织的Prompt与验收模板。模块行为以当前源码为准；旧设计文档、早期建表脚本中的字段和界面可能已被后续变更替代。

# 第一阶段 项目骨架

## 项目解决什么问题

系统管理贵金属加工中的工厂、产线、产品、物料、工序、报价、生产任务、库存、仓储单据、损耗和预警。核心是把“计划生产什么”和“实际领了多少、产出多少、损耗多少”关联起来，并按工厂隔离数据。

完整业务链：基础资料 → 产品工序及理论损耗 → 创建生产工单 → 审核 → 生产领料出库 → 加工产出入库及余料还库 → 批次结算 → 报工记录与损耗预警 → 后续工序或成品出库。

## 使用的技术

| 层次 | 当前项目技术 | 作用 |
| --- | --- | --- |
| 后端语言 | Java；pom 配置编译级别17 | 编写业务、校验和接口；本机安装的JDK版本可以更高，不能把安装版本当作编译级别 |
| 后端框架 | Spring Boot 4.1.0 | 启动应用、装配组件、提供HTTP接口 |
| 安全 | Spring Security、JWT、Redis | 登录认证、会话和权限检查 |
| 数据访问 | MyBatis、JdbcTemplate | 基础资料映射和复杂仓储事务 |
| 数据库 | MySQL | 持久保存业务记录及关联关系 |
| 数据迁移 | Flyway | 按版本创建和升级表结构 |
| 前端 | Vue 3.5、TypeScript 5.6 | 页面、交互和类型约束 |
| 前端工程 | Vite 6.4 | 开发服务器、热更新和构建 |
| 组件与状态 | Element Plus、Pinia、Vue Router | 表单表格、共享状态和路由 |
| 请求与图表 | Axios、ECharts | 调用后端与展示数据看板 |
| 测试 | 后端测试、Playwright、vue-tsc | 验证业务边界、浏览器行为及类型 |

项目基于若依体系扩展，当前属于前后端分离的模块化单体应用。后端多个 Maven 模块不等于多个独立微服务。

## 目录结构

```text
heavyMetalSystem/
├─ backend/
│  ├─ ruoyi-admin/       启动入口、控制器、配置、数据库迁移
│  ├─ ruoyi-metal/       贵金属业务模型、服务、数据访问
│  ├─ ruoyi-framework/   安全、会话、审计等框架能力
│  ├─ ruoyi-system/      用户、角色、菜单等系统能力
│  ├─ ruoyi-common/      通用模型、工具和异常
│  ├─ ruoyi-quartz/      定时任务
│  └─ ruoyi-generator/   上游代码生成能力
├─ frontend/
│  ├─ src/views/metal/   业务页面
│  ├─ src/views/audit/   日志页面
│  ├─ src/api/           接口封装
│  ├─ src/utils/         请求、UUID等公共工具
│  ├─ src/layout/        页面布局和菜单
│  ├─ tests/            浏览器回归测试
│  └─ vite.config.ts    开发及构建配置
├─ tools/               本地启动和验证工具
└─ docs/                设计、使用和验收文档
```

## 一次操作怎样执行

以保存工厂为例：Vue表单收集内容 → Axios发送请求 → 安全过滤器确认登录 → 控制器检查权限并接收参数 → Service验证业务约束 → Mapper或JdbcTemplate写MySQL → 返回结果 → 页面刷新。

表单上的红色星号帮助用户理解必填项，后端必须再次校验。隐藏按钮只改善交互，不能代替服务端鉴权。

学习练习：打开一个工厂编辑弹窗，在浏览器 Network 面板观察请求地址、请求方法、请求参数和返回结果，再定位对应控制器与服务。

# 第二阶段 核心配置与启动

## 后端配置

主要文件位于 `backend/ruoyi-admin/src/main/resources/`：

| 配置 | 含义 |
| --- | --- |
| application.yml | 通用启动、接口、令牌等配置 |
| application-druid.yml | 数据源与连接池相关配置 |
| application-metal.yml | 本项目数据源、Redis、上传目录、迁移与令牌配置 |
| db/migration/ | Flyway迁移脚本，按版本执行 |

关键环境变量：

| 变量 | 作用 | 学习要点 |
| --- | --- | --- |
| MYSQL_URL | JDBC连接地址 | 包含主机、端口、数据库、编码及时区 |
| MYSQL_USER | 数据库用户 | 应使用应用专用账号 |
| MYSQL_PASSWORD | 数据库密码 | 通过本地环境注入，不提交源码 |
| REDIS_HOST / REDIS_PORT | Redis地址 | 会话与验证码等功能依赖它 |
| REDIS_PASSWORD | Redis认证信息 | 按实际环境配置 |
| TOKEN_SECRET | JWT签名密钥 | 服务端使用，不发送到前端配置 |
| UPLOAD_PATH | 文件保存目录 | 需要运行进程具有写权限 |
| ADMIN_PASSWORD_HASH | 初始化管理员密码哈希 | 用于初始化迁移，不是明文密码 |

地址示例，不能直接当作你的真实凭据：

```properties
MYSQL_URL=jdbc:mysql://localhost:3308/heavy_metal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USER=your_app_user
MYSQL_PASSWORD=<在本机配置>
TOKEN_SECRET=<在本机配置足够强的随机密钥>
REDIS_HOST=localhost
REDIS_PORT=6380
```

本地环境使用MySQL 3308、Redis 6380、后端8080、前端5173；这些端口属于当前开发环境，换电脑后需要重新核对。`application-metal.yml` 的数据源缺省端口是3306，Flyway缺省地址使用3308，因此应显式配置同一个 `MYSQL_URL`，确保迁移和业务访问同一个数据库。

仓库根目录的私有 `.env` 由本地启动脚本加载；Spring Boot本身不会因为目录里有一个 `.env` 就自动读取它。不要把本地私密配置当作可公开的教学材料上传。

## Vite配置

`frontend/vite.config.ts` 当前关键配置：

```ts
server: {
  port: 5173,
  host: '0.0.0.0',
  open: false,
  proxy: {
    '/dev-api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewrite: p => p.replace(/^\/dev-api/, '')
    }
  }
}
```

- `host` 决定监听网卡。`0.0.0.0`表示接受所有IPv4网卡上的连接，浏览器实际访问应使用localhost或本机局域网IP。
- `port` 是前端服务端口。端口冲突时要检查终端实际启动地址。
- `open: false` 表示启动后不自动打开浏览器，不影响手动访问。
- `proxy` 把浏览器发到5173的 `/dev-api/...` 转给8080后端，并去掉前缀。
- `@` 别名指向 `src`，例如 `@/utils/request`。
- `base` 决定部署路径；当前为根路径 `/`。
- `build.outDir` 指向构建产物目录 `dist`；生产构建关闭源码映射。

前端开发环境的API前缀为 `/dev-api`，生产环境为 `/prod-api`。生产部署需要另外配置反向代理；Vite开发代理不会打进dist目录。

Vite可以类比为开发阶段提供前端资源的服务器，但它不运行Java业务。Java接口由Spring Boot应用及其Web容器承载；Vite主要处理前端模块、热更新和打包。

## 本地运行和检查

```powershell
# 前端：先进入 frontend 目录
npm.cmd ci
npm.cmd run dev

# 类型检查与生产构建
npm.cmd run typecheck
npm.cmd run build:prod

# 后端：在 backend 目录，且已安装并配置 Maven
mvn test
mvn package -DskipTests
```

运行后端还需要正确注入环境变量并启用本项目配置。优先阅读已有 `tools/start-backend.ps1`，不要通过把密码写进仓库解决配置问题。`-DskipTests`只适合已经完成测试后的打包，不能用它证明测试通过。

局域网访问使用 `http://<运行项目电脑的IPv4>:5173`。另一台电脑的127.0.0.1指向它自己。换WiFi后应重新查看ipconfig，并核对监听、防火墙和路由器客户端隔离。

# 第三阶段 数据库设计与基础管理

## 先区分四种编号

| 名称 | 示例 | 用途 |
| --- | --- | --- |
| 数据库主键 | factory_id、work_order_id | 内部唯一标识和关联 |
| 页面连续编号 | 1、2、3 | 展示行顺序，不能作为编辑删除依据 |
| 业务单据号 | WO开头的工单号 | 业务查询、打印和沟通 |
| 请求UUID | request_id | 识别同一次创建请求，防止重复提交 |

因此，本项目不应统一写成“id(uuid, primary)”。多数业务表采用 `BIGINT AUTO_INCREMENT PRIMARY KEY`，UUID用于请求幂等等场景。Java使用Long接收主键，传到JavaScript时需要避免超出安全整数范围带来的精度损失。

## 工厂与产线

`metal_factory`主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| factory_id | BIGINT，主键，自增 | 真实工厂ID |
| factory_name | VARCHAR(100) | 工厂名称 |
| area | DECIMAL(14,4) | 占地面积，单位万平方米 |
| address | VARCHAR(500) | 地址 |
| introduction | TEXT | 简介 |
| version | INT | 乐观锁版本 |
| del_flag | CHAR(1) | 逻辑删除标志 |
| create_time / update_time | DATETIME | 创建和更新时间 |

`metal_production_line`包含 `production_line_id`、`production_line_code`、`production_line_name`、`factory_id` 等。一个工厂对应多条产线；产线与产品通过 `metal_line_product` 关联，表示该产线可生产哪些产品。

## 产品、物料与工序

| 表 | 主键 | 关键字段及用途 |
| --- | --- | --- |
| metal_product | product_id | product_code、product_name、product_category、specification、dimensions、volume、purity、theoretical_weight |
| metal_material | material_id | material_code、material_name、material_type、specification；前端品名与类别呈现应结合当前服务实现理解 |
| metal_process | process_id | process_name、process_type、description |
| metal_product_process | product_id + sequence_no | process_id、theoretical_loss，保存产品工序顺序及每件理论损耗 |
| metal_line_product | production_line_id + product_id | 产线可生产产品的多对多关系 |

`dimensions`是JSON，保存不同产品类别所需的尺寸。不能给所有类别套同一套尺寸字段。重量、损耗等精确数值主要使用 `DECIMAL(20,6)`，Java对应BigDecimal。

基础物料是物料档案，原材料库存是该物料在特定工厂和仓库中的余额。创建物料档案不会自动增加库存。当前系统也不能仅凭“产品需要物料”就推断存在完整的产品BOM配方管理；实际领料明细由仓储生产出库登记。

产品、物料和工序属于共享基础目录；工单、仓储、库存等按工厂归属管理，不要想当然地给每张基础表都解释成有factory_id。

## 并发修改怎么处理

两个管理员同时打开同一记录，读取到的version都是3。第一个保存成功，数据库version变为4；第二个仍带3提交时，应收到刷新后重试的提示，而不是覆盖第一个人的修改。

示意SQL：

```sql
UPDATE metal_factory
SET factory_name = ?, version = version + 1
WHERE factory_id = ? AND version = ?;
```

更新行数为0表示记录已变化或不满足条件。主键回答“改哪条”，version回答“是否还是我打开时的版本”。

# 第四阶段 认证、角色与工厂隔离

## 账号和权限的数据关系

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| sys_user | user_id、user_name、nick_name、phonenumber、password、factory_id、status、auth_version | 用户身份与会话版本 |
| sys_role | role_id、role_name、role_key、factory_id | 角色定义及归属 |
| sys_menu | menu_id、parent_id、menu_type、perms | 菜单、页面与按钮权限 |
| sys_user_role | user_id、role_id | 用户分配角色 |
| sys_role_menu | role_id、menu_id | 角色分配权限 |

菜单可见性、按钮可操作性和后端接口授权是三个需要配合的层次。没有可操作按钮的列表会隐藏操作列及选择框；后端仍然独立检查接口权限。

## 登录流程

1. 用户输入账号、密码和验证码。
2. 服务端校验Redis中的验证码，并检查账号、密码、账号状态等。
3. 密码使用BCrypt校验，数据库不保存明文密码。
4. 登录成功，服务端创建会话并保存到Redis，签发JWT。
5. 前端在后续请求中发送 `Authorization: Bearer <token>`。
6. 安全过滤器验证令牌、加载会话，并核对用户状态、工厂和auth_version。
7. 权限和工厂范围满足后，才执行业务接口。

本项目JWT主要关联服务端缓存会话，并非只验证签名就永久放行。不能把JWT称作“加密了密码”；签名验证与数据加密是不同概念。

## 修改密码与手机号

密码策略至少8个字符，且受BCrypt输入字节上限约束。修改密码成功后提高会话版本并注销该账号全部会话，当前页面也需要重新登录。管理员重置密码走统一服务，旧入口也不能绕过会话失效逻辑。

普通账号修改手机号时同步更新登录账号并要求重新登录；超级管理员的登录账号仍保留admin。角色权限或账号状态变化也需要结合会话失效规则处理，不能只改前端状态。

密码类请求不会放入用于重复请求检测的sessionStorage正文缓存，日志也需要脱敏。

## 工厂隔离

前端传factory_id不能直接成为可信依据。服务端通过当前登录人及数据库权限确定可访问的工厂范围。

日志查看规则是：超级管理员查看全部日志；工厂管理员仅查看本厂操作日志。普通人员是否可查看页面，还取决于授予的角色权限。运行日志和登录日志不直接开放给工厂管理员。

练习：用A厂账号请求B厂单据详情，应被拒绝或不可见；只验证“菜单隐藏了”不足以证明隔离正确。

# 第五阶段 报价模块

## 配置与保存表

`metal_quote_rule`：`rule_id`为主键，当前读取规则记录1；`base_price`为报价基础系数，`pi_value`为计算使用的圆周率，`version`用于并发更新。

`metal_saved_quotation`主要字段：

| 字段 | 类型或性质 | 作用 |
| --- | --- | --- |
| quote_id | BIGINT自增主键 | 保存记录标识 |
| quote_no | VARCHAR(32)，唯一 | 报价单号 |
| request_id | VARCHAR(36)，唯一 | 保存请求幂等键 |
| request_hash / request_actor | 请求摘要及用户ID | 校验重复请求内容和提交人 |
| factory_id | BIGINT | 报价归属工厂 |
| category_labels | VARCHAR(1000) | 类别摘要 |
| item_snapshot | JSON | 保存时的报价明细快照 |
| total_price | DECIMAL(32,6) | 报价合计 |
| rule_version | INT | 使用的报价规则版本 |
| create_time | DATETIME | 保存时间 |

## 计算逻辑

当前 `QuoteRuleService.calculate` 的核心是：按类别及尺寸计算体积，单价为体积乘base_price，总价为单价乘数量，展示和存储结果按相应精度处理。

实心铱杆的代码公式可理解为 `π × (直径÷2)² × 长度`。尺寸的单位必须保持一致；不能在没有单位换算依据时把结果擅自解释成克或立方厘米。

`VolumeCalculator`目前支持铱坩埚平底、铱环、铱杆实心、铱杆空心；其他类别不应假定已有可用公式。特别是空心杆现有实现采用 `π × (外径−内径)² × 长度`，这与常见圆环柱体积公式不同，业务口径应单独确认，不能把现有代码直接当作通用几何教材。本文记录实现，不在文档任务中修改计算。

保存报价后进入 `metal_saved_quotation`，保留当时快照并可下载。后续修改报价配置不应拿当前计算结果冒充历史保存值。报价审计归属从实际保存记录取得，而不是盲信客户端填写的工厂。

# 第六阶段 工单管理

## 数据结构

| 表 | 关键字段 | 含义 |
| --- | --- | --- |
| metal_work_order | work_order_id、order_no、customer、factory_id、production_line_id、owner_id、order_date、status、version | 任务主表 |
| metal_work_order_item | item_id、work_order_id、sequence_no、product_id、quantity、product_snapshot | 要生产的产品明细 |
| metal_work_order_step | step_id、item_id、process_id、sequence_no、process_name、theoretical_loss、start_time、end_time | 产品明细的工序计划及理论损耗快照 |
| metal_work_order_review | review_id、work_order_id、action、from_status、to_status、reason、actor_id、create_time | 审核历史 |

同一工单可以有多个产品明细。当前主表对应一条production_line_id，不应把它描述成主表本身支持任意多产线字段。工序开始和结束时间是计划时间；历史表里的实际时间列不代表当前界面维护真实加工开始结束。

## 单据号与快照

打开添加页面时预留并填充唯一工单号，用户不能修改。内部主键与页面工单号是两个概念；取消页面可能造成编号间断，唯一性优先于连续无空号。

创建工单时保存产品和工序理论损耗快照。之后产品理论损耗改了，旧工单仍按自己的快照结算，避免同一批历史生产数据随配置变化而变动。

## 状态流转

```text
待审核 PENDING → 审核通过 APPROVED → 首次真实领料 → 进行中 IN_PROGRESS
       ↓ 驳回                                       ↓ 全部任务达成
   REJECTED → 修改并重新提交                       COMPLETED
```

审核通过本身不增加库存，也不代表已经生产。下一步应创建生产出库，选择该工单及实际库存物料，填写实际领料重量等信息。只有全部产品明细、全部工序满足数量与结算条件，工单才算完成。

已经进入生产的工单不能通过普通编辑任意改掉数量与工序，否则会破坏已发生的领料和产出关系。

# 第七阶段 库存与仓储单据

## 三类库存

| 类别 | 业务含义 | 计量重点 |
| --- | --- | --- |
| 原材料 RAW | 基础物料在仓库中的实际存量 | 毛重、杂质、净重 |
| 半成品 SEMI | 已加工但尚未完成全部工序 | 净重、数量、产品和来源批次 |
| 成品 FINISHED | 最终加工完成的产品 | 净重、数量、批次、纯度等 |

物料编码、品名类别、规格等是识别信息；单位名称是业务单位信息，不等于数量的计量单位“件”。

## 库存、单据和流水的区别

| 表 | 主要字段 | 回答的问题 |
| --- | --- | --- |
| metal_inventory | inventory_id、category、factory_id、warehouse_id、reference_id、batch_no、net_weight、quantity、version | 现在还剩多少 |
| metal_warehouse_order | order_id、order_no、kind、type、factory_id、warehouse_id、work_order_id、batch_id、status | 发生了什么出入库业务 |
| metal_warehouse_order_line | line_id、order_id、inventory_id、category、reference_id、net_weight、quantity及物料快照 | 每张单具体涉及什么 |
| metal_stock_ledger | ledger_id、inventory_id、order_id、line_id、delta_weight、delta_quantity、balance_weight、balance_quantity、reversal_of | 余额怎样一步步变化 |

库存余额不是基础档案字段。当前“库存调整”通过调整单和流水改变余额，需要填写原因并校验权限，不能直接把数据库中的net_weight改成想要的数字。

## 单据类型

| 方向 | 类型 | 库存影响与用途 |
| --- | --- | --- |
| 入库 | 原材料入库 RAW_IN | 增加原材料库存 |
| 入库 | 半成品入库 SEMI_IN | 登记阶段产出或相应外部入库 |
| 入库 | 成品入库 FINISHED_IN | 登记最终产出或相应外部入库 |
| 出库 | 生产出库 PRODUCTION_OUT | 领料进入生产，减少库存 |
| 出库 | 产品出库 PRODUCT_OUT | 成品交付，减少成品库存 |
| 出库 | 还料出库 EXCHANGE_OUT | 原料退给客户，减少库存；枚举保留历史名称，不计入生产损耗 |
| 出库 | 半成品出库 SEMI_OUT | 半成品对外出库 |
| 还库 | 原料还库 RAW_RETURN | 生产余料退回仓库，与原生产领料关联 |
| 还库 | 成品退还 PRODUCT_RETURN | 已出库成品退回，与原产品出库关联 |

“还料出库”与“原料还库”方向相反：前者退给客户，后者从生产现场退回仓库。

生产产出需要绑定生产工单及对应明细工序；不能把所有来源的半成品/成品入库一概解释成生产产出。是否绑定取决于所选业务来源和接口校验。

## 保存、事务和冲销

仓储单保存生效时，单据、明细、库存余额和流水需要在一个事务中一致提交。提交失败应一起回滚，不能出现单据存在但库存未扣的半成品状态。

已生效单据的纠错采用冲销：保留原记录，新增反向流水并标记原单状态。存在下游消耗时不能随意冲销上游；已结算生产批次也需遵守整批冲销约束。

幂等键防止同一请求重复生成单据；库存锁防止并发超扣；version防止旧页面覆盖新数据。这三者分别解决不同问题。

# 第八阶段 自动加工批次、报工与预警

## 工单、工序、加工批次的关系

工单是一项生产任务；工序是产品要经历的加工步骤；加工批次是同一任务产品明细在某一道工序上的一次实际加工结算范围。

本项目已取消独立加工批次页面及手动批次选择。后台仍保留 `metal_process_batch`，用于关联多次领料、产出、还料和结算。隐藏界面字段并不意味着删除内部关联。

批次号按工单号、时间和产品编号组合生成，并配合数据库唯一约束及冲突处理。同一工单产品明细工序优先绑定同一个未结算批次；结算后再次领料，才生成新批次。

核心字段包括 `batch_id`、`batch_no`、`work_order_id`、`item_id`、`process_id`、`planned_quantity`、`completed_quantity`、`status`、`version`。状态主要为OPEN、SETTLED、VOID。

## 产出约束

前一道工序结算后的完成数量，决定后一道工序可加工数量。不能只看到工单总数量100件，就认为任何工序都可以立即加工100件。

同一批次累计半成品和成品产出不得超过其计划数量。结算时产出数量合计必须等于实际完成数量；中间工序产出半成品，最后工序产出成品。后续工序领用半成品时还需检查来源工单明细和前序工序。

## 损耗计算

当前仓储结算会把原料还库、半成品产出和成品产出统一纳入回收重量汇总：

```text
损耗重量 = 生产领料净重 − 原料还库净重 − 半成品产出净重 − 成品产出净重
实际平均损耗 = 损耗重量 ÷ 实际完成数量
```

`LossCalculator`中的returned参数表示上面的回收重量合计，不仅是“剩余原材料退回”。遗漏产品产出重量会把已经变成产品的金属错误当成损耗。

教学示例：领料100g，余料还库10g，产出半成品80g，实际完成5件。损耗为10g，平均为2g/件。

## 状态与预警

理论值来自创建工单时的工序快照，不随产品后续修改。新业务不使用独立预警规则和额外容差：

- 理论损耗2g/件，实际2g/件：正常。
- 理论损耗2g/件，实际2.1g/件：异常并生成预警。
- 实际1.9g/件：正常。

代码采用“总损耗是否大于理论单件损耗×完成数量”进行比较，避免先把平均值四舍五入后误判。

`metal_work_report`保存批次、完成数量、领料、回收、损耗、平均值、理论值和NORMAL/ABNORMAL状态。`metal_report_material`保存物料损耗分摊。`metal_warning_record`保存异常预警，`metal_warning_history`保存处理历史。

处理预警表示登记原因与处理结果，不会把异常损耗改成正常。冲销源业务也需要同步处理对应报工和预警有效性。汇总平均损耗应使用总损耗÷总数量，不能直接平均各批次平均值。

# 第九阶段 日志与故障定位

## 三类日志

| 日志 | 表 | 关注内容 |
| --- | --- | --- |
| 操作日志 | sys_oper_log | 谁、何时、操作什么、业务对象、结果和工厂范围 |
| 登录日志 | sys_logininfor | 登录成功失败、访问来源等 |
| 系统运行日志 | sys_runtime_log | 异常、接口执行、运行故障及traceId |

操作日志用于追责和业务追踪，运行日志用于排错；两者通过请求关联信息一起帮助定位问题。日志详情中不能泄露密码、认证头凭据或Cookie等敏感原文。

## 为什么需要 outbox

业务事务中写入 `audit_outbox`，随后由投递任务写入日志表。这样可以减少业务成功却没有审计记录的风险。投递失败需要重试和退避，而不是无限高速循环。

`audit_scope`记录操作日志关联工厂；`audit_file_cursor`记录运行日志采集位置；`audit_log_config`与`audit_cleanup_history`管理留存设置和清理进度。

日志归档先完整脱敏并写压缩文件及清单，再重新读取验证哈希、条数和ID集合，全部一致后才删除对应数据库记录。普通页面展示截断不能用于归档，否则1000条可能只保存前100条。

清理默认不自动开启。已投递outbox的留存依据是delivered_time，不能按创建时间把刚补投完成的数据立即清掉。旧历史日志不应在没有授权时被重写或重新脱敏。

## 实际排错顺序

1. 在页面或Network面板记录失败接口、时间、返回码和traceId。
2. 在操作日志确认业务对象、操作者、工厂及操作结果。
3. 用traceId查运行日志，定位异常服务和SQL。
4. 对照单据、库存与流水判断是否事务回滚或业务校验失败。
5. 修复后重放同类隔离测试，检查不会重复扣库。

HTTP局域网环境下部分浏览器能力不同于localhost安全上下文；例如UUID和剪贴板功能需要兼容处理。只在127.0.0.1测试通过不足以覆盖局域网使用场景。

# 第十阶段 测试、部署与代码阅读方法

## 应重点验证哪些场景

| 场景 | 预期 |
| --- | --- |
| 相同请求UUID重复保存 | 不重复生成业务记录；内容不一致应拒绝 |
| 两个人同时扣同一库存 | 不能超扣，余额与流水一致 |
| 批次多次产出累计超过计划 | 在保存时拒绝，不等结算时才发现 |
| 修改密码后使用旧令牌 | 全部旧会话失效 |
| A厂访问B厂详情 | 服务端拒绝或不可见 |
| 清空日志日期筛选 | 页面不报空值异常 |
| 损耗等于理论值 | 正常，不预警 |
| 已有下游消耗后冲销上游 | 拒绝破坏关联关系的操作 |
| 归档写入或校验失败 | 原日志与范围记录保留 |
| 普通HTTP局域网访问 | 页面、UUID、错误详情复制正常或合理兼容 |

测试要同时包含成功、失败、边界、权限和并发场景。只有点通新增页面，无法证明库存链条正确。

## SQL学习示例

以下都是只读查询，用少量结果观察结构，不要直接UPDATE库存余额：

```sql
SELECT work_order_id, order_no, customer, status, factory_id
FROM metal_work_order WHERE del_flag = '0'
ORDER BY work_order_id DESC LIMIT 5;

SELECT item_id, product_id, quantity, product_snapshot
FROM metal_work_order_item WHERE work_order_id = 123;

SELECT s.process_name, s.sequence_no, s.theoretical_loss,
       s.start_time, s.end_time
FROM metal_work_order_step s
JOIN metal_work_order_item i ON i.item_id = s.item_id
WHERE i.work_order_id = 123
ORDER BY i.sequence_no, s.sequence_no;

SELECT ledger_id, delta_weight, delta_quantity,
       balance_weight, balance_quantity, create_time
FROM metal_stock_ledger WHERE inventory_id = 456
ORDER BY ledger_id;
```

123和456是示例ID，需要换成实际查询结果。数据库账号的查询权限也应符合管理规则，不能用SQL绕过应用的工厂隔离。

## 阅读代码的顺序

先看 `frontend/src/views/metal/` 中一个页面，再找对应 `src/api/` 请求，接着找后端Controller和Service，最后看表结构及测试。阅读仓储核心时重点看 `WarehouseService`、`WarehouseRequests`、`WarehouseReferences`、`LossCalculator`。

`VolumeCalculator`与`QuoteRuleService`适合理解类别公式、BigDecimal和报价；工单服务适合理解快照与状态机；安全框架适合理解JWT与Redis会话；审计包适合理解可靠投递及故障恢复。

Flyway应按全部迁移版本理解最终结构。只打开V1或V4，就断言当前字段完全如此，会忽略后续约束、字段和菜单调整。

# 第十一阶段 Codex 使用技巧

## 用任务合同代替一句笼统需求

下面是结合本项目整理的实践模板，不是Codex自动保证的功能。每次明确目标、范围和验收条件，会更容易检查结果。

```text
目标：修复仓储单物料清单操作按钮换行。
范围：仅修改该弹窗布局，不修改字段、校验、接口和库存逻辑。
现象：编辑与删除上下排列。
预期：两个按钮同一行、间距一致，小屏不遮挡内容。
验收：提供修改文件及浏览器验证结果。
```

截图最好配上页面路径、点击步骤、当前角色和预期效果。涉及权限时，说明“谁能看、谁能改、跨工厂是否允许”，不要只说“加权限”。

## 大改动先形成可核对的计划

```text
请先阅读仓储服务、工单服务和现有设计。
制定自动加工批次的修改计划，包含数据关联、状态流转、并发、幂等、
冲销、历史兼容和验收用例。先给设计，本轮不要修改实现。
```

审查方案后再说：

```text
按已确认方案实现并运行相关测试。
不要清理业务数据，不推送GitHub。
交付修改说明、测试结果和仍需人工确认的业务口径。
```

计划应随最终实现更新；不要让已经放弃的方案长期留在文档中误导下一次任务。OpenAI官方也提供了用计划文档管理复杂任务的方法，可根据项目实际精简使用。[官方计划文档示例](https://developers.openai.com/cookbook/articles/codex_exec_plans)

## 让Codex解释一条真实调用链

```text
请从工单页面保存按钮开始，追踪到控制器、服务、SQL和日志。
给出实际文件与方法，解释request_id、order_no、version分别解决什么问题。
只读代码，不修改文件，也不要打印本地密钥。
```

学习时先自己判断，再让Codex根据代码核对。涉及数学公式、计量单位、实际业务规则时，需要确认口径，不能因为有一段代码就认为业务定义必然正确。

## 使用 AGENTS.md 保存稳定规则

官方文档说明，可以通过AGENTS.md为项目提供持久约定，项目目录中的规则可以与更具体目录的规则配合使用。适合记录长期有效、可执行的要求；不要把每次聊天全文复制进去。[官方AGENTS.md说明](https://learn.chatgpt.com/docs/agent-configuration/agents-md)

下面是适合本项目的示例，本文没有自动创建该规则文件：

```md
# 项目约定

- 重量统一按克处理，精确计算使用BigDecimal。
- 修改仓储逻辑时检查事务、并发、幂等和冲销。
- 修改接口时同时检查后端授权与工厂隔离。
- 不读取或输出真实密码、令牌和本地环境文件内容。
- 数据清理必须有明确范围与备份，不擅自删除业务记录。
- 不修改已执行的Flyway迁移来修复现有数据库。
- 修改前端后运行typecheck；业务变化运行相关测试。
- 没有运行的测试明确说明，不写成已通过。
- 未明确要求时不推送、不合并GitHub分支。
```

## 区分上下文、技能和权限

提供文件路径和相关设计，帮助Codex找到证据；技能适合重复工作流程，例如生成文档或读取表格。它们不能代替项目业务规则，也不会自动授予外部发布或数据删除权限。

一次专注一个明确目标。新任务开始时可以要求“先报告当前分支、未提交修改及计划涉及文件”，避免覆盖尚未完成的工作。官方最佳实践强调提供清晰上下文并配合可验证的反馈；具体提示词仍应适应当前项目。[官方最佳实践](https://learn.chatgpt.com/guides/best-practices)

## 代码审查提示词

```text
审查仓储、认证和日志模块，不做格式化重构。
优先找可复现的业务错误、越权、并发不一致、敏感信息泄露和数据丢失。
每项给出触发条件、影响、实际文件位置、修复建议与最小验证用例。
把待确认业务口径与已经证实的缺陷分开说明。
```

看到“测试通过”时，继续看运行了哪些测试、跳过哪些、是否覆盖真实接口。构建成功不等于所有业务正确；类型检查通过也不能证明库存计算正确。

## 提交GitHub前讲清楚动作

本地commit、推送分支、创建PR、合并main是不同操作。仓库默认页面显示main；功能分支有完整代码时，main仍可能只显示README。

可使用：

```text
检查当前diff，排除.env、数据库备份、真实账号和运行日志。
把本次修复提交到指定功能分支并创建PR，不合并main。
给出提交号、分支、PR链接和测试摘要。
```

如果本次只想学习或整理说明，应明确“只生成文档，不修改代码、不上传”。遇到认证失败，在本机凭据管理器处理，不在聊天里发送密码或令牌。

# 第十二阶段 建议的学习实践

1. 跑通前后端，能解释浏览器5173到后端8080的代理过程。
2. 阅读工厂管理的一条新增或编辑调用链，理解主键、校验和version。
3. 画出用户、角色、菜单的关系，用两个工厂账号验证隔离。
4. 创建一个产品并配置两道工序，记录每件理论损耗。
5. 新建小数量工单，审核后登记真实测试领料。
6. 记录半成品产出与余料还库，确认数量重量后结算。
7. 对比报工实际平均损耗与工单快照，分别验证正常、相等、超出三种情况。
8. 查看库存流水和日志，解释每一笔余额变化来自哪张单据。
9. 在隔离测试数据中验证重复提交、超量产出和下游依赖冲销限制。
10. 用Codex审查自己的操作记录，要求引用代码证据，再自己复核结论。

上述数据是学习用例，不代表真实客户生产记录。练习优先使用隔离环境；本说明的生成没有执行清库、写业务数据或GitHub发布。

# 参考与源码入口

- 用户提供的文章地址：https://blog.csdn.net/weixin_43571227/article/details/162223574 ，正文已由用户粘贴提供。
- OpenAI官方AGENTS.md：https://learn.chatgpt.com/docs/agent-configuration/agents-md
- OpenAI官方最佳实践：https://learn.chatgpt.com/guides/best-practices
- 项目技术版本：backend/pom.xml、frontend/package.json。
- 项目配置：frontend/vite.config.ts、backend/ruoyi-admin/src/main/resources/application-metal.yml。
- 核心报价与损耗：backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/ 下的QuoteRuleService、VolumeCalculator、WarehouseService、LossCalculator。
- 表结构演进：backend/ruoyi-admin/src/main/resources/db/migration/。
- 既有业务说明：docs/仓储损耗使用说明.md、docs/工单管理详细设计.md、docs/日志管理使用与验收说明.md。阅读旧文档时需要与当前代码核对。
