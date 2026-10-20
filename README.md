# 贵金属加工系统

基于官方 RuoYi-Vue / RuoYi-Vue3 TypeScript，实现工厂、产线、产品、物料、工序、人员与角色权限管理。

## 本机已配置的运行环境

当前MySQL8.4.11由MySQL84服务运行，自动启动，地址127.0.0.1:3308，库heavy_metal；原MySQL57继续使用3306。已创建专用应用账号，不使用root运行应用。

已有Windows Redis3.0.504用于本地联调，地址127.0.0.1:6380；它与下面Docker Compose的Redis7.4方案不同。本机现有配置已写入忽略文件.env，不要覆盖。应用管理员账号admin，随机初始密码见.env的ADMIN_LOGIN_PASSWORD；数据库账号和密码见MYSQL_USER、MYSQL_PASSWORD。MySQL root密码按用户要求设置，未保存在项目文件中。

后续本机启动顺序：检查MySQL84服务 → `powershell -File tools/start-local-redis.ps1` → `powershell -File tools/start-backend.ps1` → 在frontend目录`npm run dev`。打开`http://127.0.0.1:5173/metal/factory`。

## 目录

```text
backend/                  官方若依后端（Spring Boot 4、MyBatis、Spring Security）
  ruoyi-admin/            应用入口、工厂接口、配置和数据库迁移
  ruoyi-metal/            贵金属业务：工厂实体、请求、Mapper、事务服务、测试
  ruoyi-common/           若依公共模块
  ruoyi-framework/        若依认证、权限、框架配置
  ruoyi-system/           若依用户、角色、菜单等
  ruoyi-quartz/           若依定时任务
  ruoyi-generator/        若依代码生成器
frontend/                 官方 Vue 3 + TypeScript + Element Plus 前端
  src/api/metal/          工厂接口和数据类型
  src/views/metal/factory/ 工厂列表和新增、编辑弹窗
  src/layout/FactoryLayout.vue  按需求原型制作的工厂页面外框
  tests/                  浏览器交互测试（受控接口响应）
tools/                    管理员密码哈希生成、后端启动脚本
docs/                     接口说明、验证记录和页面截图
compose.yml               独立 MySQL 8.4、Redis 服务
```

## 初始化与启动

需要 Java 21、Maven、Node 22、MySQL 8.4 和 Redis。可以只安装 Docker，通过 Compose 启动数据库和 Redis。

1. 在项目根目录复制 `.env.example` 为 `.env`，修改数据库密码、root 密码和随机 `TOKEN_SECRET`（至少32字符）。`.env` 不提交。
2. 安装前端依赖：`npm --prefix frontend ci --cache .npm-cache`。
3. 运行 `node tools/hash-password.mjs`，输入自己设置的管理员密码，把输出的 BCrypt 哈希填写到 `.env` 的 `ADMIN_PASSWORD_HASH`，用单引号包裹值以避免 Docker Compose 展开 `$`。启动脚本会去除外层单引号，不展开内容。系统没有预置固定管理员密码。
4. 在根目录运行 `docker compose up -d`。MySQL 默认宿主端口3308，避开已有 MySQL 5.7 的3306；Redis默认6379。数据保存在独立 Docker 卷中。
5. 编译后端：在 `backend` 下运行 `mvn -gs ../build-settings.xml -s ../build-settings.xml -B package`。
6. 在根目录运行 `powershell -File tools/start-backend.ps1`。后端监听8080。
7. 在 `frontend` 下运行 `npm run dev`，打开 `http://127.0.0.1:5173/metal/factory`，使用账号 `admin` 和自己设置的密码登录。

使用外部服务时，在 `.env` 中设置 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。先由管理员创建一个空的 `heavy_metal` 数据库，并给项目账号授权。不要连接已有若依数据库或业务数据库。

首次启动由 Flyway 按 V1系统表 → V2定时任务 → V3工厂 → V4其余基础管理 → V5报价参数 → V6工单管理初始化；已执行迁移只能追加后续版本，禁止改写历史、自动清库及自动基线。原始 `backend/sql` 是上游参考文件，包含 DROP TABLE，请勿用于现有数据库。启动脚本将构建JAR复制至.runtime后运行，方便后续重新构建。

管理员管理全部工厂；普通人员、角色、产线和工厂按所属工厂隔离，产品、物料和工序为共享资料。菜单及操作权限通过角色表单配置，普通角色只能授予自己已有的权限。旧原生人员/角色接口迁移至/legacy/system/user和/legacy/system/role，并限制超级管理员使用，避免旧接口绕过工厂范围。未配置有效工厂的普通账号不能登录。

## 功能

- 工厂列表严格使用需求图片的列顺序、添加/编辑/删除按钮；默认16条/页，无额外搜索栏、状态列或查看按钮。
- 工厂新增、编辑、详情回显、单条/批量逻辑删除。
- 面积单位为万平方米，最多10位整数、4位小数；名称、面积、地址必填。
- 工厂一对多关联产线，支持解除关联；不能直接占用其他工厂的产线；关联保存与工厂资料整体事务提交。
- 存在关联产线时禁止删除，批量删除任一失败则全部不删除；乐观锁防止编辑覆盖。
- 其余六项提供真实CRUD、16条分页、版本冲突检测和关联删除限制；产线可关联多个产品，产品包含六类尺寸与排序工序，每道工序损耗以g保存。尺寸cm、体积cm³、纯度%、重量g；支持类别按已确认公式自动计算体积，净重手动填写。
- 新增、编辑弹窗采用此前设计方案，Markdown没有弹窗原型，不能认定弹窗已达到在线原型逐像素一致。
- 人员/角色复用若依用户、角色、菜单关系；手机号登录，密码重置、启停及授权调整后失效受影响登录令牌。
- 页面按1442px基准等比缩放，菜单可点击、分组可折叠，侧栏单独滚动。报价规则仅配置基础单价和π；自动报价页提供类型、商品、数量及尺寸选择。已接入体积计算和报价计算，报价包含配置的基础单价。
- 工单管理已实现增删查改、多产品工序计划、审核、驳回重提及权限隔离；仓储、损耗及预警已接入。详细规格见docs/基础管理详细设计.md。

## 验证

- 后端：`mvn -gs ../build-settings.xml -s ../build-settings.xml -B test`。
- 前端生产构建：`npm --prefix frontend run build:prod`。
- 浏览器测试：在 `frontend` 下运行 `npx playwright test`，使用已安装的 Chrome。测试接口由测试代码拦截，不证明数据库事务或真实登录已验证。
- 真实后端浏览器验收：后端和Redis运行后，在frontend下设置`$env:LIVE_FACTORY='1'`并运行`npx playwright test factory-live.spec.ts`。测试通过本应用本地Redis取得自己的验证码测试夹具，没有关闭验证码配置或访问第三方服务。
- 六项真实接口及数据库验收：`node tools/verify-master-live.mjs`，结果保存到docs/master-live-verification.json。六项页面验收：frontend下设置`$env:LIVE_MASTER='1'`，运行`npx playwright test master-live.spec.ts`。测试只清理自己创建的夹具。
- 全量 TypeScript检查：`npx vue-tsc --noEmit`；若依上游还有既存类型错误，验证记录区分新增工厂代码与上游问题。

源码版本固定在本地checkout，后端来源 https://gitee.com/y_project/RuoYi-Vue ，提交 `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3`；前端来源 https://gitcode.com/yangzongzhuan/RuoYi-Vue3 ，typescript分支提交 `0ea09ef814213875b61cffb3485d76de680256f3`。保留官方MIT许可证。

## 工单管理

设计见[工单管理详细设计](docs/工单管理详细设计.md)，接口见[工单接口](docs/work-order-api.md)。菜单路径 `/metal/work-order`，详情使用同一路径的 `id` 查询参数。新单待审核，通过后锁定并等待领料；实际执行与报工由后续仓储模块接入。

真实接口验收：`node tools/verify-work-order-live.mjs`。浏览器在frontend目录设置 `LIVE_WORK_ORDER=1` 后运行 `node node_modules/@playwright/test/cli.js test work-order-live.spec.ts`。重启验收依次运行 `node tools/verify-work-order-persistence.mjs prepare`、重启后端、`node tools/verify-work-order-persistence.mjs verify`。脚本只清理自己生成的可丢弃本地验收数据。

首页/index已改为数据看板，按工厂范围展示真实统计；后台管理按钮进入业务页面。演示角色和人员见[演示角色与人员](docs/演示角色与人员.md)。创建/复用本地演示账号：`node tools/seed-demo-identities.mjs`；初始密码仅保存至忽略提交的.runtime/demo-identities.json。

工单时间按2026-10-13确认改为实际开始、结束时间，新增编辑填写实际时间，详情取消计划/实际切换。V7保留旧计划归档，不自动转换历史时间。

## 2026-10-13最终调整：仅计划时间

用户确认实际时间不需要。本次使用V8，保留原计划；只有原计划为空时才把上阶段人工填写时间带入计划。新增编辑写入计划字段，详情仅显示计划时间，无计划/实际切换；审批仅检查计划时间，不再要求实际时间。已通过133项后端测试、13组真实接口及页面审核流程回归，前端构建成功。此说明替代上阶段实际时间方案。

## 仓储、损耗与预警

已实现全部原料/成品/半成品出入还库、库房与库存流水、分批加工、损耗报工及预警处理。数据库追加V9～V10；当前工单仍只使用计划时间。详见[详细设计](docs/损耗统计与预警详细设计.md)、[接口](docs/warehouse-api.md)、[使用说明](docs/仓储损耗使用说明.md)。首次使用先配置库房和本厂人员；新增权限由管理员在角色管理明确分配，不自动增加演示角色权限。

真实验收：node tools/verify-warehouse-live.mjs；Excel导入：node tools/verify-warehouse-import.mjs（测试需Python标准库）；重启：verify-warehouse-persistence.mjs prepare → 重启后端 → verify。浏览器在frontend设置LIVE_WAREHOUSE=1运行warehouse-live.spec.ts。测试只清理独立工厂夹具，不修改业务数据。单据模板配置菜单已移除，物料模板下载/导入保留。

## 数据看板与保存报价单

首页按提供的原型布局展示工单、预警、已保存报价单、真实铱损耗趋势、工序TOP5、工厂对比及预警中心。无数据时显示0或空状态。自动报价计算后可“保存报价单”，保存需metal:quotation:add权限；重复计算不增加单数，UUID重试只保存一次。数据库追加V11，历史计算不补录。详见[数据看板详细设计](docs/数据看板详细设计.md)。

接口验收：`node tools/verify-dashboard-live.mjs`；真实浏览器验收：frontend下设置LIVE_DASHBOARD=1，运行dashboard-live.spec.ts。

## 最新仓储流程

加工批次由生产出库自动生成，不再单独建批次；同一工单产品明细、工序复用未结算批次号，结算后新领料再生成。编号为工单号-毫秒时间-产品编号，数据库唯一。预警规则页面取消，新结算按工单保存的单件理论损耗判断，不使用允许超差；相等正常。仓储物料清单详情可结算和整批冲销。详见[自动加工批次与预警调整](docs/自动加工批次与预警调整.md)。
