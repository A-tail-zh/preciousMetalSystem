# 全系统输入校验字段与容量清单

长度沿用现有 Java/JavaScript 字符串口径；密码单独按 UTF-8 字节限制。以下容量来自本轮数据库只读元数据核对，未修改数据库。

## 模块与写入入口

| 模块 | 主要接口 | 请求模型 |
|---|---|---|
| 工厂 | `/metal/factory` | FactoryRequest |
| 产品、产线、物料、工序 | `/metal/product`、`/metal/production-line`、`/metal/material`、`/metal/process` | 对应 Record |
| 人员、角色 | `/system/user`、`/system/role` | StaffRecord、RoleRecord |
| 工单 | `/metal/work-order` | WorkOrderRequest（含产品及工序计划） |
| 仓库、预警、库存 | `/metal/warehouse`、`/metal/warning-rule`、`/metal/inventory` | WarehouseRequests |
| 仓储单据与导入 | `/metal/warehouse-order`、`/metal/warehouse-order/import` | Order、Line、Import.Input |
| 批次、结算、还料确认 | `/metal/process-batch` 及子操作 | Batch、Action、ReturnConfirmation |
| 关闭、释放、跳步 | 工单与库存对应子操作、`/metal/route-skip-permits` | Close、Release、Cancel、Permit |
| 报价 | `/metal/quote-rule`、`/metal/quotation/*` | QuoteRule、CalculationRequest、QuotationSaveRequest |
| 个人资料 | `/system/user/profile` 及子操作 | Profile、Password |
| 系统管理 | `/system/config`、`/system/menu`、`/system/dict/*`、`/system/dept`、`/system/post`、`/system/notice` | Sys 对应模型 |

## 数据库字段容量、精度与可空性

| 表 | 字段 | 类型 | 最大字符数 | 数值精度 | 小数位 | 可空 |
|---|---|---|---:|---:|---:|---|
| metal_batch_return_confirmation | confirmation_id | bigint | 0 | 19 | 0 | NO |
| metal_batch_return_confirmation | batch_id | bigint | 0 | 19 | 0 | NO |
| metal_batch_return_confirmation | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_batch_return_confirmation | request_id | varchar | 64 | 0 | 0 | NO |
| metal_batch_return_confirmation | request_hash | varchar | 64 | 0 | 0 | NO |
| metal_batch_return_confirmation | mode | varchar | 16 | 0 | 0 | NO |
| metal_batch_return_confirmation | physical_checked | tinyint | 0 | 3 | 0 | NO |
| metal_batch_return_confirmation | status | varchar | 16 | 0 | 0 | NO |
| metal_batch_return_confirmation | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_batch_return_confirmation | reason | varchar | 500 | 0 | 0 | YES |
| metal_batch_return_confirmation | version | int | 0 | 10 | 0 | NO |
| metal_batch_return_confirmation | create_time | datetime | 0 | 0 | 0 | NO |
| metal_batch_return_document | confirmation_id | bigint | 0 | 19 | 0 | NO |
| metal_batch_return_document | order_id | bigint | 0 | 19 | 0 | NO |
| metal_factory | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_factory | factory_name | varchar | 100 | 0 | 0 | NO |
| metal_factory | area | decimal | 0 | 14 | 4 | NO |
| metal_factory | address | varchar | 500 | 0 | 0 | NO |
| metal_factory | introduction | text | 65535 | 0 | 0 | YES |
| metal_factory | create_by | varchar | 64 | 0 | 0 | NO |
| metal_factory | create_time | datetime | 0 | 0 | 0 | NO |
| metal_factory | update_by | varchar | 64 | 0 | 0 | NO |
| metal_factory | update_time | datetime | 0 | 0 | 0 | NO |
| metal_factory | del_flag | char | 1 | 0 | 0 | NO |
| metal_factory | version | int | 0 | 10 | 0 | NO |
| metal_inventory | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_inventory | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_inventory | warehouse_id | bigint | 0 | 19 | 0 | NO |
| metal_inventory | category | varchar | 16 | 0 | 0 | NO |
| metal_inventory | reference_id | bigint | 0 | 19 | 0 | NO |
| metal_inventory | batch_no | varchar | 160 | 0 | 0 | NO |
| metal_inventory | code | varchar | 64 | 0 | 0 | NO |
| metal_inventory | name | varchar | 100 | 0 | 0 | NO |
| metal_inventory | specification | varchar | 200 | 0 | 0 | YES |
| metal_inventory | purity | decimal | 0 | 12 | 6 | YES |
| metal_inventory | organization | varchar | 100 | 0 | 0 | YES |
| metal_inventory | quantity | bigint | 0 | 19 | 0 | NO |
| metal_inventory | net_weight | decimal | 0 | 20 | 6 | NO |
| metal_inventory | origin_batch_id | bigint | 0 | 19 | 0 | YES |
| metal_inventory | version | int | 0 | 10 | 0 | NO |
| metal_inventory | del_flag | char | 1 | 0 | 0 | NO |
| metal_inventory | update_time | datetime | 0 | 0 | 0 | YES |
| metal_inventory | ownership_status | varchar | 16 | 0 | 0 | NO |
| metal_inventory | raw_organization | varchar | 100 | 0 | 0 | YES |
| metal_line_product | production_line_id | bigint | 0 | 19 | 0 | NO |
| metal_line_product | product_id | bigint | 0 | 19 | 0 | NO |
| metal_material | material_id | bigint | 0 | 19 | 0 | NO |
| metal_material | material_code | varchar | 64 | 0 | 0 | NO |
| metal_material | material_name | varchar | 100 | 0 | 0 | NO |
| metal_material | material_type | varchar | 30 | 0 | 0 | NO |
| metal_material | specification | varchar | 200 | 0 | 0 | YES |
| metal_material | create_by | varchar | 64 | 0 | 0 | NO |
| metal_material | create_time | datetime | 0 | 0 | 0 | NO |
| metal_material | update_by | varchar | 64 | 0 | 0 | NO |
| metal_material | update_time | datetime | 0 | 0 | 0 | NO |
| metal_material | del_flag | char | 1 | 0 | 0 | NO |
| metal_material | version | int | 0 | 10 | 0 | NO |
| metal_process | process_id | bigint | 0 | 19 | 0 | NO |
| metal_process | process_name | varchar | 100 | 0 | 0 | NO |
| metal_process | description | varchar | 2000 | 0 | 0 | YES |
| metal_process | process_type | tinyint | 0 | 3 | 0 | NO |
| metal_process | create_by | varchar | 64 | 0 | 0 | NO |
| metal_process | create_time | datetime | 0 | 0 | 0 | NO |
| metal_process | update_by | varchar | 64 | 0 | 0 | NO |
| metal_process | update_time | datetime | 0 | 0 | 0 | NO |
| metal_process | del_flag | char | 1 | 0 | 0 | NO |
| metal_process | version | int | 0 | 10 | 0 | NO |
| metal_process_batch | batch_id | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | request_id | varchar | 36 | 0 | 0 | NO |
| metal_process_batch | request_hash | char | 64 | 0 | 0 | NO |
| metal_process_batch | request_actor | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | item_id | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | process_id | bigint | 0 | 19 | 0 | NO |
| metal_process_batch | sequence_no | int | 0 | 10 | 0 | NO |
| metal_process_batch | planned_quantity | int | 0 | 10 | 0 | NO |
| metal_process_batch | completed_quantity | int | 0 | 10 | 0 | NO |
| metal_process_batch | status | varchar | 16 | 0 | 0 | NO |
| metal_process_batch | version | int | 0 | 10 | 0 | NO |
| metal_process_batch | create_time | datetime | 0 | 0 | 0 | YES |
| metal_process_batch | batch_no | varchar | 256 | 0 | 0 | YES |
| metal_process_batch | execution_snapshot | json | 0 | 0 | 0 | YES |
| metal_process_batch | completion_policy | varchar | 32 | 0 | 0 | NO |
| metal_process_raw_return_line | line_id | bigint | 0 | 19 | 0 | NO |
| metal_process_raw_return_line | batch_id | bigint | 0 | 19 | 0 | NO |
| metal_process_raw_return_line | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_process_raw_return_line | source_snapshot | json | 0 | 0 | 0 | NO |
| metal_product | product_id | bigint | 0 | 19 | 0 | NO |
| metal_product | product_code | varchar | 64 | 0 | 0 | NO |
| metal_product | product_name | varchar | 100 | 0 | 0 | NO |
| metal_product | product_category | varchar | 30 | 0 | 0 | NO |
| metal_product | specification | varchar | 200 | 0 | 0 | YES |
| metal_product | dimensions | json | 0 | 0 | 0 | NO |
| metal_product | volume | decimal | 0 | 20 | 6 | YES |
| metal_product | purity | decimal | 0 | 20 | 6 | YES |
| metal_product | unit | varchar | 20 | 0 | 0 | NO |
| metal_product | theoretical_weight | decimal | 0 | 20 | 6 | YES |
| metal_product | create_by | varchar | 64 | 0 | 0 | NO |
| metal_product | create_time | datetime | 0 | 0 | 0 | NO |
| metal_product | update_by | varchar | 64 | 0 | 0 | NO |
| metal_product | update_time | datetime | 0 | 0 | 0 | NO |
| metal_product | del_flag | char | 1 | 0 | 0 | NO |
| metal_product | version | int | 0 | 10 | 0 | NO |
| metal_product_process | product_id | bigint | 0 | 19 | 0 | NO |
| metal_product_process | process_id | bigint | 0 | 19 | 0 | NO |
| metal_product_process | sequence_no | int | 0 | 10 | 0 | NO |
| metal_product_process | theoretical_loss | decimal | 0 | 20 | 6 | NO |
| metal_production_line | production_line_id | bigint | 0 | 19 | 0 | NO |
| metal_production_line | production_line_code | varchar | 64 | 0 | 0 | NO |
| metal_production_line | production_line_name | varchar | 100 | 0 | 0 | NO |
| metal_production_line | factory_id | bigint | 0 | 19 | 0 | YES |
| metal_production_line | create_by | varchar | 64 | 0 | 0 | NO |
| metal_production_line | create_time | datetime | 0 | 0 | 0 | NO |
| metal_production_line | update_by | varchar | 64 | 0 | 0 | NO |
| metal_production_line | update_time | datetime | 0 | 0 | 0 | NO |
| metal_production_line | version | int | 0 | 10 | 0 | NO |
| metal_quote_rule | rule_id | bigint | 0 | 19 | 0 | NO |
| metal_quote_rule | base_price | decimal | 0 | 20 | 6 | NO |
| metal_quote_rule | pi_value | decimal | 0 | 20 | 15 | NO |
| metal_quote_rule | version | int | 0 | 10 | 0 | NO |
| metal_quote_rule | update_by | varchar | 64 | 0 | 0 | NO |
| metal_quote_rule | update_time | datetime | 0 | 0 | 0 | NO |
| metal_report_material | report_material_id | bigint | 0 | 19 | 0 | NO |
| metal_report_material | report_id | bigint | 0 | 19 | 0 | NO |
| metal_report_material | code | varchar | 64 | 0 | 0 | NO |
| metal_report_material | name | varchar | 100 | 0 | 0 | NO |
| metal_report_material | issued_weight | decimal | 0 | 20 | 6 | NO |
| metal_report_material | loss_weight | decimal | 0 | 20 | 6 | NO |
| metal_report_material | average_loss | decimal | 0 | 20 | 6 | NO |
| metal_route_skip_permit | permit_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit | product_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit | specification | varchar | 500 | 0 | 0 | NO |
| metal_route_skip_permit | route_hash | char | 64 | 0 | 0 | NO |
| metal_route_skip_permit | route_snapshot | json | 0 | 0 | 0 | NO |
| metal_route_skip_permit | source_sequence | int | 0 | 10 | 0 | NO |
| metal_route_skip_permit | target_sequence | int | 0 | 10 | 0 | NO |
| metal_route_skip_permit | enabled | tinyint | 0 | 3 | 0 | NO |
| metal_route_skip_permit | version | int | 0 | 10 | 0 | NO |
| metal_route_skip_permit | request_id | char | 36 | 0 | 0 | NO |
| metal_route_skip_permit | request_hash | char | 64 | 0 | 0 | NO |
| metal_route_skip_permit | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit | reason | varchar | 500 | 0 | 0 | NO |
| metal_route_skip_permit | create_time | datetime | 0 | 0 | 0 | NO |
| metal_route_skip_permit_version | permit_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit_version | version | int | 0 | 10 | 0 | NO |
| metal_route_skip_permit_version | snapshot | json | 0 | 0 | 0 | NO |
| metal_route_skip_permit_version | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_permit_version | request_id | char | 36 | 0 | 0 | NO |
| metal_route_skip_permit_version | request_hash | char | 64 | 0 | 0 | NO |
| metal_route_skip_permit_version | create_time | datetime | 0 | 0 | 0 | NO |
| metal_route_skip_use | line_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_use | permit_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_use | permit_version | int | 0 | 10 | 0 | NO |
| metal_route_skip_use | item_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_use | batch_id | bigint | 0 | 19 | 0 | NO |
| metal_route_skip_use | new_skipped | json | 0 | 0 | 0 | NO |
| metal_route_skip_use | execution_snapshot | json | 0 | 0 | 0 | NO |
| metal_saved_quotation | quote_id | bigint | 0 | 19 | 0 | NO |
| metal_saved_quotation | quote_no | varchar | 32 | 0 | 0 | NO |
| metal_saved_quotation | request_id | varchar | 36 | 0 | 0 | NO |
| metal_saved_quotation | request_hash | char | 64 | 0 | 0 | NO |
| metal_saved_quotation | request_actor | bigint | 0 | 19 | 0 | NO |
| metal_saved_quotation | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_saved_quotation | category_labels | varchar | 1000 | 0 | 0 | NO |
| metal_saved_quotation | item_snapshot | json | 0 | 0 | 0 | NO |
| metal_saved_quotation | total_price | decimal | 0 | 32 | 6 | NO |
| metal_saved_quotation | rule_version | int | 0 | 10 | 0 | NO |
| metal_saved_quotation | create_time | datetime | 0 | 0 | 0 | NO |
| metal_semi_auto_release_event | event_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_event | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_event | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_event | generation | int | 0 | 10 | 0 | NO |
| metal_semi_auto_release_event | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_event | status | varchar | 24 | 0 | 0 | NO |
| metal_semi_auto_release_event | failures | int | 0 | 10 | 0 | NO |
| metal_semi_auto_release_event | next_attempt | datetime | 0 | 0 | 0 | NO |
| metal_semi_auto_release_event | message | varchar | 500 | 0 | 0 | YES |
| metal_semi_auto_release_event | create_time | datetime | 0 | 0 | 0 | NO |
| metal_semi_auto_release_event | update_time | datetime | 0 | 0 | 0 | NO |
| metal_semi_auto_release_result | event_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_result | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_auto_release_result | status | varchar | 24 | 0 | 0 | NO |
| metal_semi_auto_release_result | message | varchar | 500 | 0 | 0 | YES |
| metal_semi_auto_release_result | quantity | bigint | 0 | 19 | 0 | YES |
| metal_semi_auto_release_result | net_weight | decimal | 0 | 20 | 6 | YES |
| metal_semi_flow_event | event_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_flow_event | request_id | varchar | 36 | 0 | 0 | YES |
| metal_semi_flow_event | request_hash | char | 64 | 0 | 0 | YES |
| metal_semi_flow_event | release_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_flow_event | action | varchar | 24 | 0 | 0 | NO |
| metal_semi_flow_event | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_flow_event | reason | varchar | 500 | 0 | 0 | NO |
| metal_semi_flow_event | create_time | datetime | 0 | 0 | 0 | NO |
| metal_semi_release | release_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | request_id | varchar | 36 | 0 | 0 | NO |
| metal_semi_release | request_hash | char | 64 | 0 | 0 | NO |
| metal_semi_release | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | quantity | bigint | 0 | 19 | 0 | NO |
| metal_semi_release | net_weight | decimal | 0 | 20 | 6 | NO |
| metal_semi_release | reason | varchar | 500 | 0 | 0 | NO |
| metal_semi_release | status | varchar | 12 | 0 | 0 | NO |
| metal_semi_release | version | int | 0 | 10 | 0 | NO |
| metal_semi_release | create_time | datetime | 0 | 0 | 0 | NO |
| metal_semi_release | cancel_reason | varchar | 500 | 0 | 0 | YES |
| metal_semi_release | cancel_actor | bigint | 0 | 19 | 0 | YES |
| metal_semi_release | cancel_time | datetime | 0 | 0 | 0 | YES |
| metal_semi_release | release_mode | varchar | 8 | 0 | 0 | NO |
| metal_semi_release | auto_event_id | bigint | 0 | 19 | 0 | YES |
| metal_semi_release_use | use_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release_use | release_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release_use | line_id | bigint | 0 | 19 | 0 | NO |
| metal_semi_release_use | route_snapshot | json | 0 | 0 | 0 | NO |
| metal_stock_ledger | ledger_id | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | order_id | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | line_id | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | delta_weight | decimal | 0 | 20 | 6 | NO |
| metal_stock_ledger | delta_quantity | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | balance_weight | decimal | 0 | 20 | 6 | NO |
| metal_stock_ledger | balance_quantity | bigint | 0 | 19 | 0 | NO |
| metal_stock_ledger | reversal_of | bigint | 0 | 19 | 0 | YES |
| metal_stock_ledger | create_time | datetime | 0 | 0 | 0 | YES |
| metal_stock_sequence | sequence_date | date | 0 | 0 | 0 | NO |
| metal_stock_sequence | prefix | varchar | 8 | 0 | 0 | NO |
| metal_stock_sequence | sequence_value | int | 0 | 10 | 0 | NO |
| metal_warehouse | warehouse_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse | name | varchar | 100 | 0 | 0 | NO |
| metal_warehouse | sort_order | int | 0 | 10 | 0 | NO |
| metal_warehouse | status | char | 1 | 0 | 0 | NO |
| metal_warehouse | version | int | 0 | 10 | 0 | NO |
| metal_warehouse | del_flag | char | 1 | 0 | 0 | NO |
| metal_warehouse | create_by | varchar | 64 | 0 | 0 | NO |
| metal_warehouse | create_time | datetime | 0 | 0 | 0 | YES |
| metal_warehouse | update_time | datetime | 0 | 0 | 0 | YES |
| metal_warehouse_number | request_id | varchar | 36 | 0 | 0 | NO |
| metal_warehouse_number | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_number | kind | varchar | 8 | 0 | 0 | NO |
| metal_warehouse_number | order_no | varchar | 32 | 0 | 0 | YES |
| metal_warehouse_number | create_time | datetime | 0 | 0 | 0 | NO |
| metal_warehouse_order | order_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order | order_no | varchar | 32 | 0 | 0 | NO |
| metal_warehouse_order | request_id | varchar | 36 | 0 | 0 | NO |
| metal_warehouse_order | request_hash | char | 64 | 0 | 0 | NO |
| metal_warehouse_order | request_actor | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order | warehouse_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order | kind | varchar | 16 | 0 | 0 | NO |
| metal_warehouse_order | type | varchar | 32 | 0 | 0 | NO |
| metal_warehouse_order | order_date | date | 0 | 0 | 0 | NO |
| metal_warehouse_order | work_order_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | batch_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | operator_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | keeper_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order | recipient_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | inspector_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | reviewer_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order | operator_name | text | 65535 | 0 | 0 | NO |
| metal_warehouse_order | keeper_name | varchar | 100 | 0 | 0 | NO |
| metal_warehouse_order | recipient_name | varchar | 100 | 0 | 0 | YES |
| metal_warehouse_order | inspector_name | varchar | 100 | 0 | 0 | YES |
| metal_warehouse_order | reviewer_name | varchar | 100 | 0 | 0 | YES |
| metal_warehouse_order | warehouse_name | varchar | 100 | 0 | 0 | NO |
| metal_warehouse_order | status | varchar | 16 | 0 | 0 | NO |
| metal_warehouse_order | reason | varchar | 500 | 0 | 0 | YES |
| metal_warehouse_order | version | int | 0 | 10 | 0 | NO |
| metal_warehouse_order | create_time | datetime | 0 | 0 | 0 | YES |
| metal_warehouse_order | operator_ids | json | 0 | 0 | 0 | YES |
| metal_warehouse_order_line | line_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order_line | order_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order_line | inventory_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order_line | source_line_id | bigint | 0 | 19 | 0 | YES |
| metal_warehouse_order_line | sequence_no | int | 0 | 10 | 0 | NO |
| metal_warehouse_order_line | category | varchar | 16 | 0 | 0 | NO |
| metal_warehouse_order_line | reference_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order_line | code | varchar | 64 | 0 | 0 | NO |
| metal_warehouse_order_line | name | varchar | 100 | 0 | 0 | NO |
| metal_warehouse_order_line | specification | varchar | 200 | 0 | 0 | YES |
| metal_warehouse_order_line | batch_no | varchar | 160 | 0 | 0 | NO |
| metal_warehouse_order_line | net_weight | decimal | 0 | 20 | 6 | NO |
| metal_warehouse_order_line | quantity | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_order_line | gross_weight | decimal | 0 | 20 | 6 | YES |
| metal_warehouse_order_line | impurity | decimal | 0 | 20 | 6 | YES |
| metal_warehouse_order_line | purity | decimal | 0 | 12 | 6 | YES |
| metal_warehouse_order_line | organization | varchar | 100 | 0 | 0 | YES |
| metal_warehouse_order_line | receiving_organization | varchar | 100 | 0 | 0 | YES |
| metal_warehouse_order_line | purpose | varchar | 200 | 0 | 0 | YES |
| metal_warehouse_order_line | remark | varchar | 500 | 0 | 0 | YES |
| metal_warehouse_request | request_id | varchar | 36 | 0 | 0 | NO |
| metal_warehouse_request | kind | varchar | 32 | 0 | 0 | NO |
| metal_warehouse_request | request_hash | char | 64 | 0 | 0 | NO |
| metal_warehouse_request | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_request | result_id | bigint | 0 | 19 | 0 | NO |
| metal_warehouse_request | create_time | datetime | 0 | 0 | 0 | YES |
| metal_warning_history | history_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_history | warning_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_history | action | varchar | 32 | 0 | 0 | NO |
| metal_warning_history | result | varchar | 500 | 0 | 0 | YES |
| metal_warning_history | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_history | actor_name | varchar | 100 | 0 | 0 | NO |
| metal_warning_history | create_time | datetime | 0 | 0 | 0 | YES |
| metal_warning_record | warning_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_record | report_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_record | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_record | process_name | varchar | 100 | 0 | 0 | NO |
| metal_warning_record | product_category | varchar | 100 | 0 | 0 | NO |
| metal_warning_record | specification | varchar | 200 | 0 | 0 | YES |
| metal_warning_record | order_no | varchar | 32 | 0 | 0 | NO |
| metal_warning_record | threshold_value | decimal | 0 | 20 | 6 | NO |
| metal_warning_record | theoretical_loss | decimal | 0 | 20 | 6 | NO |
| metal_warning_record | average_loss | decimal | 0 | 20 | 6 | NO |
| metal_warning_record | status | varchar | 16 | 0 | 0 | NO |
| metal_warning_record | source_void | tinyint | 0 | 3 | 0 | NO |
| metal_warning_record | result | varchar | 500 | 0 | 0 | YES |
| metal_warning_record | handler_id | bigint | 0 | 19 | 0 | YES |
| metal_warning_record | handler_name | varchar | 100 | 0 | 0 | YES |
| metal_warning_record | handled_time | datetime | 0 | 0 | 0 | YES |
| metal_warning_record | version | int | 0 | 10 | 0 | NO |
| metal_warning_record | del_flag | char | 1 | 0 | 0 | NO |
| metal_warning_record | trigger_time | datetime | 0 | 0 | 0 | YES |
| metal_warning_rule | rule_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_rule | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_rule | product_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_rule | process_id | bigint | 0 | 19 | 0 | NO |
| metal_warning_rule | tolerance | decimal | 0 | 20 | 6 | NO |
| metal_warning_rule | status | char | 1 | 0 | 0 | NO |
| metal_warning_rule | version | int | 0 | 10 | 0 | NO |
| metal_warning_rule | del_flag | char | 1 | 0 | 0 | NO |
| metal_warning_rule | create_time | datetime | 0 | 0 | 0 | YES |
| metal_work_order | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order | order_no | varchar | 32 | 0 | 0 | NO |
| metal_work_order | request_id | varchar | 36 | 0 | 0 | NO |
| metal_work_order | request_hash | char | 64 | 0 | 0 | NO |
| metal_work_order | request_actor | bigint | 0 | 19 | 0 | NO |
| metal_work_order | customer | varchar | 100 | 0 | 0 | NO |
| metal_work_order | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order | production_line_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order | owner_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order | factory_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order | line_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order | owner_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order | order_date | date | 0 | 0 | 0 | NO |
| metal_work_order | status | varchar | 20 | 0 | 0 | NO |
| metal_work_order | reject_reason | varchar | 500 | 0 | 0 | YES |
| metal_work_order | create_by | varchar | 64 | 0 | 0 | NO |
| metal_work_order | creator_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order | create_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order | update_by | varchar | 64 | 0 | 0 | NO |
| metal_work_order | update_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order | del_flag | char | 1 | 0 | 0 | NO |
| metal_work_order | version | int | 0 | 10 | 0 | NO |
| metal_work_order | auto_semi_release_eligible | tinyint | 0 | 3 | 0 | NO |
| metal_work_order | completion_generation | int | 0 | 10 | 0 | NO |
| metal_work_order_close | close_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_close | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_close | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_close | request_id | varchar | 36 | 0 | 0 | NO |
| metal_work_order_close | request_hash | char | 64 | 0 | 0 | NO |
| metal_work_order_close | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_close | action | varchar | 12 | 0 | 0 | NO |
| metal_work_order_close | reason | varchar | 500 | 0 | 0 | NO |
| metal_work_order_close | snapshot | json | 0 | 0 | 0 | NO |
| metal_work_order_close | create_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order_item | item_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_item | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_item | sequence_no | int | 0 | 10 | 0 | NO |
| metal_work_order_item | product_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_item | quantity | int | 0 | 10 | 0 | NO |
| metal_work_order_item | product_snapshot | json | 0 | 0 | 0 | NO |
| metal_work_order_legacy_plan | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_legacy_plan | step_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_legacy_plan | item_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_legacy_plan | process_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_legacy_plan | sequence_no | int | 0 | 10 | 0 | NO |
| metal_work_order_legacy_plan | process_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order_legacy_plan | start_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order_legacy_plan | end_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order_number | request_id | varchar | 36 | 0 | 0 | NO |
| metal_work_order_number | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_number | order_no | varchar | 32 | 0 | 0 | YES |
| metal_work_order_number | create_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order_review | review_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_review | work_order_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_review | action | varchar | 20 | 0 | 0 | NO |
| metal_work_order_review | from_status | varchar | 20 | 0 | 0 | YES |
| metal_work_order_review | to_status | varchar | 20 | 0 | 0 | NO |
| metal_work_order_review | reason | varchar | 500 | 0 | 0 | YES |
| metal_work_order_review | actor_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_review | actor_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order_review | create_time | datetime | 0 | 0 | 0 | NO |
| metal_work_order_sequence | business_date | date | 0 | 0 | 0 | NO |
| metal_work_order_sequence | sequence_value | int | 0 | 10 | 0 | NO |
| metal_work_order_step | step_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_step | item_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_step | process_id | bigint | 0 | 19 | 0 | NO |
| metal_work_order_step | sequence_no | int | 0 | 10 | 0 | NO |
| metal_work_order_step | process_name | varchar | 100 | 0 | 0 | NO |
| metal_work_order_step | theoretical_loss | decimal | 0 | 20 | 6 | NO |
| metal_work_order_step | start_time | datetime | 0 | 0 | 0 | YES |
| metal_work_order_step | end_time | datetime | 0 | 0 | 0 | YES |
| metal_work_order_step | actual_start_time | datetime | 0 | 0 | 0 | YES |
| metal_work_order_step | actual_end_time | datetime | 0 | 0 | 0 | YES |
| metal_work_report | report_id | bigint | 0 | 19 | 0 | NO |
| metal_work_report | batch_id | bigint | 0 | 19 | 0 | NO |
| metal_work_report | factory_id | bigint | 0 | 19 | 0 | NO |
| metal_work_report | item_id | bigint | 0 | 19 | 0 | NO |
| metal_work_report | process_id | bigint | 0 | 19 | 0 | NO |
| metal_work_report | completed_quantity | int | 0 | 10 | 0 | NO |
| metal_work_report | issued_weight | decimal | 0 | 20 | 6 | NO |
| metal_work_report | returned_weight | decimal | 0 | 20 | 6 | NO |
| metal_work_report | loss_weight | decimal | 0 | 20 | 6 | NO |
| metal_work_report | average_loss | decimal | 0 | 20 | 6 | NO |
| metal_work_report | theoretical_loss | decimal | 0 | 20 | 6 | NO |
| metal_work_report | tolerance | decimal | 0 | 20 | 6 | NO |
| metal_work_report | threshold_value | decimal | 0 | 20 | 6 | NO |
| metal_work_report | rule_id | bigint | 0 | 19 | 0 | YES |
| metal_work_report | rule_version | int | 0 | 10 | 0 | YES |
| metal_work_report | status | varchar | 16 | 0 | 0 | NO |
| metal_work_report | voided | tinyint | 0 | 3 | 0 | NO |
| metal_work_report | create_time | datetime | 0 | 0 | 0 | YES |
| sys_config | config_id | int | 0 | 10 | 0 | NO |
| sys_config | config_name | varchar | 100 | 0 | 0 | YES |
| sys_config | config_key | varchar | 100 | 0 | 0 | YES |
| sys_config | config_value | varchar | 500 | 0 | 0 | YES |
| sys_config | config_type | char | 1 | 0 | 0 | YES |
| sys_config | create_by | varchar | 64 | 0 | 0 | YES |
| sys_config | create_time | datetime | 0 | 0 | 0 | YES |
| sys_config | update_by | varchar | 64 | 0 | 0 | YES |
| sys_config | update_time | datetime | 0 | 0 | 0 | YES |
| sys_config | remark | varchar | 500 | 0 | 0 | YES |
| sys_dept | dept_id | bigint | 0 | 19 | 0 | NO |
| sys_dept | parent_id | bigint | 0 | 19 | 0 | YES |
| sys_dept | ancestors | varchar | 50 | 0 | 0 | YES |
| sys_dept | dept_name | varchar | 30 | 0 | 0 | YES |
| sys_dept | order_num | int | 0 | 10 | 0 | YES |
| sys_dept | leader | varchar | 20 | 0 | 0 | YES |
| sys_dept | phone | varchar | 11 | 0 | 0 | YES |
| sys_dept | email | varchar | 50 | 0 | 0 | YES |
| sys_dept | status | char | 1 | 0 | 0 | YES |
| sys_dept | del_flag | char | 1 | 0 | 0 | YES |
| sys_dept | create_by | varchar | 64 | 0 | 0 | YES |
| sys_dept | create_time | datetime | 0 | 0 | 0 | YES |
| sys_dept | update_by | varchar | 64 | 0 | 0 | YES |
| sys_dept | update_time | datetime | 0 | 0 | 0 | YES |
| sys_dict_data | dict_code | bigint | 0 | 19 | 0 | NO |
| sys_dict_data | dict_sort | int | 0 | 10 | 0 | YES |
| sys_dict_data | dict_label | varchar | 100 | 0 | 0 | YES |
| sys_dict_data | dict_value | varchar | 100 | 0 | 0 | YES |
| sys_dict_data | dict_type | varchar | 100 | 0 | 0 | YES |
| sys_dict_data | css_class | varchar | 100 | 0 | 0 | YES |
| sys_dict_data | list_class | varchar | 100 | 0 | 0 | YES |
| sys_dict_data | is_default | char | 1 | 0 | 0 | YES |
| sys_dict_data | status | char | 1 | 0 | 0 | YES |
| sys_dict_data | create_by | varchar | 64 | 0 | 0 | YES |
| sys_dict_data | create_time | datetime | 0 | 0 | 0 | YES |
| sys_dict_data | update_by | varchar | 64 | 0 | 0 | YES |
| sys_dict_data | update_time | datetime | 0 | 0 | 0 | YES |
| sys_dict_data | remark | varchar | 500 | 0 | 0 | YES |
| sys_dict_type | dict_id | bigint | 0 | 19 | 0 | NO |
| sys_dict_type | dict_name | varchar | 100 | 0 | 0 | YES |
| sys_dict_type | dict_type | varchar | 100 | 0 | 0 | YES |
| sys_dict_type | status | char | 1 | 0 | 0 | YES |
| sys_dict_type | create_by | varchar | 64 | 0 | 0 | YES |
| sys_dict_type | create_time | datetime | 0 | 0 | 0 | YES |
| sys_dict_type | update_by | varchar | 64 | 0 | 0 | YES |
| sys_dict_type | update_time | datetime | 0 | 0 | 0 | YES |
| sys_dict_type | remark | varchar | 500 | 0 | 0 | YES |
| sys_job | job_id | bigint | 0 | 19 | 0 | NO |
| sys_job | job_name | varchar | 64 | 0 | 0 | NO |
| sys_job | job_group | varchar | 64 | 0 | 0 | NO |
| sys_job | invoke_target | varchar | 500 | 0 | 0 | NO |
| sys_job | cron_expression | varchar | 255 | 0 | 0 | YES |
| sys_job | misfire_policy | varchar | 20 | 0 | 0 | YES |
| sys_job | concurrent | char | 1 | 0 | 0 | YES |
| sys_job | status | char | 1 | 0 | 0 | YES |
| sys_job | create_by | varchar | 64 | 0 | 0 | YES |
| sys_job | create_time | datetime | 0 | 0 | 0 | YES |
| sys_job | update_by | varchar | 64 | 0 | 0 | YES |
| sys_job | update_time | datetime | 0 | 0 | 0 | YES |
| sys_job | remark | varchar | 500 | 0 | 0 | YES |
| sys_job_log | job_log_id | bigint | 0 | 19 | 0 | NO |
| sys_job_log | job_name | varchar | 64 | 0 | 0 | NO |
| sys_job_log | job_group | varchar | 64 | 0 | 0 | NO |
| sys_job_log | invoke_target | varchar | 500 | 0 | 0 | NO |
| sys_job_log | job_message | varchar | 500 | 0 | 0 | YES |
| sys_job_log | status | char | 1 | 0 | 0 | YES |
| sys_job_log | exception_info | varchar | 2000 | 0 | 0 | YES |
| sys_job_log | start_time | datetime | 0 | 0 | 0 | YES |
| sys_job_log | end_time | datetime | 0 | 0 | 0 | YES |
| sys_job_log | create_time | datetime | 0 | 0 | 0 | YES |
| sys_logininfor | info_id | bigint | 0 | 19 | 0 | NO |
| sys_logininfor | user_name | varchar | 50 | 0 | 0 | YES |
| sys_logininfor | ipaddr | varchar | 128 | 0 | 0 | YES |
| sys_logininfor | login_location | varchar | 255 | 0 | 0 | YES |
| sys_logininfor | browser | varchar | 50 | 0 | 0 | YES |
| sys_logininfor | os | varchar | 50 | 0 | 0 | YES |
| sys_logininfor | status | char | 1 | 0 | 0 | YES |
| sys_logininfor | msg | varchar | 255 | 0 | 0 | YES |
| sys_logininfor | login_time | datetime | 0 | 0 | 0 | YES |
| sys_logininfor | trace_id | varchar | 32 | 0 | 0 | YES |
| sys_logininfor | event_id | varchar | 36 | 0 | 0 | YES |
| sys_logininfor | reason_category | varchar | 80 | 0 | 0 | YES |
| sys_logininfor | account_user_id | bigint | 0 | 19 | 0 | YES |
| sys_menu | menu_id | bigint | 0 | 19 | 0 | NO |
| sys_menu | menu_name | varchar | 50 | 0 | 0 | NO |
| sys_menu | parent_id | bigint | 0 | 19 | 0 | YES |
| sys_menu | order_num | int | 0 | 10 | 0 | YES |
| sys_menu | path | varchar | 200 | 0 | 0 | YES |
| sys_menu | component | varchar | 255 | 0 | 0 | YES |
| sys_menu | query | varchar | 255 | 0 | 0 | YES |
| sys_menu | route_name | varchar | 50 | 0 | 0 | YES |
| sys_menu | is_frame | int | 0 | 10 | 0 | YES |
| sys_menu | is_cache | int | 0 | 10 | 0 | YES |
| sys_menu | menu_type | char | 1 | 0 | 0 | YES |
| sys_menu | visible | char | 1 | 0 | 0 | YES |
| sys_menu | status | char | 1 | 0 | 0 | YES |
| sys_menu | perms | varchar | 100 | 0 | 0 | YES |
| sys_menu | icon | varchar | 100 | 0 | 0 | YES |
| sys_menu | create_by | varchar | 64 | 0 | 0 | YES |
| sys_menu | create_time | datetime | 0 | 0 | 0 | YES |
| sys_menu | update_by | varchar | 64 | 0 | 0 | YES |
| sys_menu | update_time | datetime | 0 | 0 | 0 | YES |
| sys_menu | remark | varchar | 500 | 0 | 0 | YES |
| sys_notice | notice_id | int | 0 | 10 | 0 | NO |
| sys_notice | notice_title | varchar | 50 | 0 | 0 | NO |
| sys_notice | notice_type | char | 1 | 0 | 0 | NO |
| sys_notice | notice_content | longblob | 4294967295 | 0 | 0 | YES |
| sys_notice | status | char | 1 | 0 | 0 | YES |
| sys_notice | create_by | varchar | 64 | 0 | 0 | YES |
| sys_notice | create_time | datetime | 0 | 0 | 0 | YES |
| sys_notice | update_by | varchar | 64 | 0 | 0 | YES |
| sys_notice | update_time | datetime | 0 | 0 | 0 | YES |
| sys_notice | remark | varchar | 255 | 0 | 0 | YES |
| sys_notice_read | read_id | bigint | 0 | 19 | 0 | NO |
| sys_notice_read | notice_id | int | 0 | 10 | 0 | NO |
| sys_notice_read | user_id | bigint | 0 | 19 | 0 | NO |
| sys_notice_read | read_time | datetime | 0 | 0 | 0 | NO |
| sys_oper_log | oper_id | bigint | 0 | 19 | 0 | NO |
| sys_oper_log | title | varchar | 50 | 0 | 0 | YES |
| sys_oper_log | business_type | int | 0 | 10 | 0 | YES |
| sys_oper_log | method | varchar | 200 | 0 | 0 | YES |
| sys_oper_log | request_method | varchar | 10 | 0 | 0 | YES |
| sys_oper_log | operator_type | int | 0 | 10 | 0 | YES |
| sys_oper_log | oper_name | varchar | 50 | 0 | 0 | YES |
| sys_oper_log | dept_name | varchar | 50 | 0 | 0 | YES |
| sys_oper_log | oper_url | varchar | 255 | 0 | 0 | YES |
| sys_oper_log | oper_ip | varchar | 128 | 0 | 0 | YES |
| sys_oper_log | oper_location | varchar | 255 | 0 | 0 | YES |
| sys_oper_log | oper_param | text | 65535 | 0 | 0 | YES |
| sys_oper_log | json_result | text | 65535 | 0 | 0 | YES |
| sys_oper_log | status | int | 0 | 10 | 0 | YES |
| sys_oper_log | error_msg | varchar | 2000 | 0 | 0 | YES |
| sys_oper_log | oper_time | datetime | 0 | 0 | 0 | YES |
| sys_oper_log | cost_time | bigint | 0 | 19 | 0 | YES |
| sys_oper_log | event_id | varchar | 36 | 0 | 0 | YES |
| sys_oper_log | trace_id | varchar | 32 | 0 | 0 | YES |
| sys_oper_log | actor_user_id | bigint | 0 | 19 | 0 | YES |
| sys_oper_log | actor_factory_id | bigint | 0 | 19 | 0 | YES |
| sys_oper_log | business_factory_id | bigint | 0 | 19 | 0 | YES |
| sys_oper_log | factory_name | varchar | 100 | 0 | 0 | YES |
| sys_oper_log | actor_name | varchar | 100 | 0 | 0 | YES |
| sys_oper_log | module_code | varchar | 80 | 0 | 0 | YES |
| sys_oper_log | action_code | varchar | 80 | 0 | 0 | YES |
| sys_oper_log | event_key | varchar | 160 | 0 | 0 | YES |
| sys_oper_log | result_code | varchar | 32 | 0 | 0 | YES |
| sys_oper_log | business_code | varchar | 160 | 0 | 0 | YES |
| sys_oper_log | object_type | varchar | 80 | 0 | 0 | YES |
| sys_oper_log | objects_json | json | 0 | 0 | 0 | YES |
| sys_oper_log | changes_json | json | 0 | 0 | 0 | YES |
| sys_oper_log | error_category | varchar | 100 | 0 | 0 | YES |
| sys_oper_log | replayed | tinyint | 0 | 3 | 0 | NO |
| sys_oper_log | truncated | tinyint | 0 | 3 | 0 | NO |
| sys_oper_log | schema_version | int | 0 | 10 | 0 | NO |
| sys_post | post_id | bigint | 0 | 19 | 0 | NO |
| sys_post | post_code | varchar | 64 | 0 | 0 | NO |
| sys_post | post_name | varchar | 50 | 0 | 0 | NO |
| sys_post | post_sort | int | 0 | 10 | 0 | NO |
| sys_post | status | char | 1 | 0 | 0 | NO |
| sys_post | create_by | varchar | 64 | 0 | 0 | YES |
| sys_post | create_time | datetime | 0 | 0 | 0 | YES |
| sys_post | update_by | varchar | 64 | 0 | 0 | YES |
| sys_post | update_time | datetime | 0 | 0 | 0 | YES |
| sys_post | remark | varchar | 500 | 0 | 0 | YES |
| sys_role | role_id | bigint | 0 | 19 | 0 | NO |
| sys_role | role_name | varchar | 100 | 0 | 0 | NO |
| sys_role | role_key | varchar | 100 | 0 | 0 | NO |
| sys_role | role_sort | int | 0 | 10 | 0 | NO |
| sys_role | data_scope | char | 1 | 0 | 0 | YES |
| sys_role | menu_check_strictly | tinyint | 0 | 3 | 0 | YES |
| sys_role | dept_check_strictly | tinyint | 0 | 3 | 0 | YES |
| sys_role | status | char | 1 | 0 | 0 | NO |
| sys_role | del_flag | char | 1 | 0 | 0 | YES |
| sys_role | create_by | varchar | 64 | 0 | 0 | YES |
| sys_role | create_time | datetime | 0 | 0 | 0 | YES |
| sys_role | update_by | varchar | 64 | 0 | 0 | YES |
| sys_role | update_time | datetime | 0 | 0 | 0 | YES |
| sys_role | remark | varchar | 500 | 0 | 0 | YES |
| sys_role | factory_id | bigint | 0 | 19 | 0 | YES |
| sys_role | version | int | 0 | 10 | 0 | NO |
| sys_role | active_role_name | varchar | 100 | 0 | 0 | YES |
| sys_role_dept | role_id | bigint | 0 | 19 | 0 | NO |
| sys_role_dept | dept_id | bigint | 0 | 19 | 0 | NO |
| sys_role_menu | role_id | bigint | 0 | 19 | 0 | NO |
| sys_role_menu | menu_id | bigint | 0 | 19 | 0 | NO |
| sys_runtime_log | id | bigint | 0 | 19 | 0 | NO |
| sys_runtime_log | event_id | varchar | 36 | 0 | 0 | NO |
| sys_runtime_log | event_time | datetime | 0 | 0 | 0 | NO |
| sys_runtime_log | level | varchar | 8 | 0 | 0 | NO |
| sys_runtime_log | service | varchar | 80 | 0 | 0 | YES |
| sys_runtime_log | component | varchar | 160 | 0 | 0 | YES |
| sys_runtime_log | event_type | varchar | 80 | 0 | 0 | YES |
| sys_runtime_log | summary | text | 65535 | 0 | 0 | YES |
| sys_runtime_log | trace_id | varchar | 32 | 0 | 0 | YES |
| sys_runtime_log | exception_class | varchar | 255 | 0 | 0 | YES |
| sys_runtime_log | detail_json | json | 0 | 0 | 0 | YES |
| sys_runtime_log | cost_ms | bigint | 0 | 19 | 0 | YES |
| sys_runtime_log | create_time | datetime | 0 | 0 | 0 | YES |
| sys_user | user_id | bigint | 0 | 19 | 0 | NO |
| sys_user | dept_id | bigint | 0 | 19 | 0 | YES |
| sys_user | user_name | varchar | 30 | 0 | 0 | NO |
| sys_user | nick_name | varchar | 100 | 0 | 0 | NO |
| sys_user | user_type | varchar | 2 | 0 | 0 | YES |
| sys_user | email | varchar | 50 | 0 | 0 | YES |
| sys_user | phonenumber | varchar | 11 | 0 | 0 | YES |
| sys_user | sex | char | 1 | 0 | 0 | YES |
| sys_user | avatar | varchar | 100 | 0 | 0 | YES |
| sys_user | password | varchar | 100 | 0 | 0 | YES |
| sys_user | status | char | 1 | 0 | 0 | YES |
| sys_user | del_flag | char | 1 | 0 | 0 | YES |
| sys_user | login_ip | varchar | 128 | 0 | 0 | YES |
| sys_user | login_date | datetime | 0 | 0 | 0 | YES |
| sys_user | pwd_update_date | datetime | 0 | 0 | 0 | YES |
| sys_user | create_by | varchar | 64 | 0 | 0 | YES |
| sys_user | create_time | datetime | 0 | 0 | 0 | YES |
| sys_user | update_by | varchar | 64 | 0 | 0 | YES |
| sys_user | update_time | datetime | 0 | 0 | 0 | YES |
| sys_user | remark | varchar | 500 | 0 | 0 | YES |
| sys_user | factory_id | bigint | 0 | 19 | 0 | YES |
| sys_user | version | int | 0 | 10 | 0 | NO |
| sys_user | unique_phone | varchar | 11 | 0 | 0 | YES |
| sys_user | auth_version | bigint | 0 | 19 | 0 | NO |
| sys_user_post | user_id | bigint | 0 | 19 | 0 | NO |
| sys_user_post | post_id | bigint | 0 | 19 | 0 | NO |
| sys_user_role | user_id | bigint | 0 | 19 | 0 | NO |
| sys_user_role | role_id | bigint | 0 | 19 | 0 | NO |

## 最终请求声明与页面字段

声明原文同时记录已有约束，业务组合规则仍由各自服务在事务内核对。

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/BatchCompletionRequests.java

```
public record ReturnDocument(@NotBlank @Pattern(regexp="RAW_RETURN|SEMI_RETURN|PROCESS_RAW_RETURN") String type,
@NotNull Long warehouseId,@NotNull LocalDate orderDate,@NotEmpty @Size(max=20) List<@NotNull Long> operatorIds,
@NotNull Long keeperId,Long inspectorId,Long reviewerId,@NotBlank @Size(max=500) String reason,
@NotEmpty @Size(max=1000) List<@NotNull @Valid Line> lines){}
public record ReturnConfirmation(@NotBlank @Size(max=64) String requestId,@NotNull @Min(0) Integer version,
@NotBlank @Pattern(regexp="NONE|RETURNED") String mode,@NotNull Boolean physicalChecked,
@Size(max=500) String reason,@NotNull @Size(max=100) List<@NotNull @Valid ReturnDocument> documents){}
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/CalculationRequest.java

```
public record CalculationRequest(@NotBlank String category, @NotNull Map<String, String> dimensions,
@NotNull @Min(1) @Max(1000000) Integer quantity) {
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/LineRecord.java

```
@NotBlank
@Size(max = 64)
@NotBlank
@Size(max = 100)
@NotNull
private List<@NotNull @Positive Long> productIds = new ArrayList<>();
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/MasterRecord.java

```
@Min(0)
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/MaterialRecord.java

```
@NotBlank @Size(max=64)  private String code;
@NotBlank @Size(max=100) private String materialType;
@Size(max=200)  private String specification;
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/ProcessRecord.java

```
@NotBlank
@Size(max = 100)
@Size(max = 2000)
@NotNull
@Min(1)
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/ProductRecord.java

```
@NotBlank
@Size(max = 64)
@NotBlank
@Size(max = 100)
@NotBlank
@Size(max = 200)
@NotNull
@DecimalMin("0")
@Digits(integer = 14, fraction = 6)
@DecimalMin("0")
@DecimalMax("100")
@Digits(integer = 14, fraction = 6)
@DecimalMin("0")
@Digits(integer = 14, fraction = 6)
@NotNull
private List<@NotNull @Valid ProductStep> processes = new ArrayList<>();
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/ProductStep.java

```
@NotNull
@NotNull
@DecimalMin("0")
@Digits(integer = 14, fraction = 6)
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/QuotationSaveRequest.java

```
public record QuotationSaveRequest(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,@NotNull @Positive Long factoryId,@NotNull @Size(min=1,max=100) List<@NotNull @Valid CalculationRequest> items){}
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/QuoteRule.java

```
@NotNull
@DecimalMin(value = "0", inclusive = false)
@Digits(integer = 14, fraction = 6)
@NotNull
@DecimalMin(value = "0", inclusive = false)
@Digits(integer = 5, fraction = 15)
@NotNull
@Min(0)
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/RoleRecord.java

```
@NotBlank @Size(max=100)  private String roleName;
@NotNull @Min(0)  private Integer roleSort;
@NotBlank @Pattern(regexp="[01]")  private String status;
@tools.jackson.databind.annotation.JsonSerialize(contentUsing=tools.jackson.databind.ser.std.ToStringSerializer.class) @NotNull private List<@NotNull @Positive Long> menuIds=new ArrayList<>();
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/StaffRecord.java

```
@NotBlank @Pattern(regexp="1[3-9][0-9]{9}")  private String phonenumber;
@NotBlank @Size(max=100)  private String nickName;
@NotBlank @Pattern(regexp="[01]")  private String status;
@Size(min=8,max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)  private String password;
@tools.jackson.databind.annotation.JsonSerialize(contentUsing=tools.jackson.databind.ser.std.ToStringSerializer.class) @NotNull private List<@NotNull @Positive Long> roleIds=new ArrayList<>();
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/WarehouseRequests.java

```
public record Warehouse(Long id,Integer version,String requestId,@NotNull Long factoryId,@NotBlank @Size(max=100) String name,@Min(0) int sortOrder,@Pattern(regexp="0|1") String status){}
public record Rule(Long id,Integer version,String requestId,@NotNull Long factoryId,@NotNull Long productId,@NotNull Long processId,@NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal tolerance,@Pattern(regexp="0|1") String status){}
public record Batch(@NotBlank String requestId,@NotNull Long workOrderId,@NotNull Long itemId,@NotNull Long processId,@Min(1) @Max(1000000) int plannedQuantity){}
public record Action(@NotNull @Min(0) Integer version,@Size(max=500) String reason,Integer completedQuantity){}
public record Stock(Long id,Integer version,@Size(max=200) String specification,@Size(max=100) String organization,@DecimalMin("0") @DecimalMax("100") @Digits(integer=6,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal purity){}
public record Adjustment(@NotBlank String requestId,@NotNull Integer version,@NotNull Long operatorId,@NotNull Long keeperId,@NotNull @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal deltaWeight,@NotNull @Min(-1000000) @Max(1000000) Long deltaQuantity,@NotBlank @Size(max=500) String reason,@Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal deltaGrossWeight,@Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal deltaImpurity){
public record Order(@NotBlank String requestId,@NotNull Long factoryId,@NotNull Long warehouseId,@NotBlank String type,@NotNull LocalDate orderDate,Long operatorId,@Size(max=20) List<@NotNull Long> operatorIds,@NotNull Long keeperId,Long recipientId,Long inspectorId,Long reviewerId,Long workOrderId,Long batchId,Integer batchVersion,Boolean settle,Integer completedQuantity,@Size(max=500) String reason,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Line> lines,Long itemId,Long processId){}
public record Line(@JsonFormat(shape=JsonFormat.Shape.STRING) Long inventoryId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long sourceLineId,String category,@JsonFormat(shape=JsonFormat.Shape.STRING) Long referenceId,@Size(max=160) String batchNo,@NotNull @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal netWeight,Long quantity,@DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal grossWeight,@DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal impurity,@DecimalMin("0") @DecimalMax("100") @Digits(integer=6,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal purity,@Size(max=100) String organization,@Size(max=200) String purpose,@Size(max=500) String remark,@Size(max=100) String receivingOrganization,@JsonFormat(shape=JsonFormat.Shape.STRING) Long releaseId,Long skipPermitId,Integer skipPermitVersion){
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/catalog/WorkOrderRequest.java

```
@Positive Long id, @Min(0) Integer version,
@Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
@NotBlank @Size(max = 100) String customer, @NotNull @Positive Long factoryId,
@NotNull @Positive Long productionLineId, @NotNull @Positive Long ownerId,
@NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate orderDate,
@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items
public record Item(@Positive Long id, @NotNull @Positive Long productId,
@NotNull @Min(1) @Max(1000000) Integer quantity,
@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Plan> plans) {
public record Plan(@NotNull @Positive Long processId,
@NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
@NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
public record Action(@NotNull @Min(0) Integer version, @Size(max = 500) String reason) {
```

### backend/ruoyi-metal/src/main/java/com/ruoyi/metal/dto/FactoryRequest.java

```
@NotBlank(message = "工厂名称不能为空")
@Size(max = 100, message = "工厂名称不能超过100个字符")
@NotNull(message = "占地面积不能为空")
@DecimalMin(value = "0", inclusive = false, message = "占地面积必须大于0")
@Digits(integer = 10, fraction = 4, message = "占地面积最多10位整数、4位小数")
@NotBlank(message = "详细地址不能为空")
@Size(max = 500, message = "详细地址不能超过500个字符")
@Size(max = 5000, message = "工厂简介不能超过5000个字符")
@Size(max = 1000, message = "关联产线数量不能超过1000条")
private List<@NotNull @Positive Long> productionLineIds;
@Min(0)
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/BatchCompletionController.java

```
@RestController @Validated @RequestMapping("/metal/process-batch")
@PostMapping("/{id}/confirm-return")
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/BusinessExportController.java

```
@PostMapping("/system/user/export") @PreAuthorize("@ss.hasPermi('system:user:export')") @Log(title="人员条件导出",businessType=BusinessType.EXPORT,isSaveResponseData=false)
@PostMapping("/metal/warehouse-order/export") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:export')") @Log(title="仓储工单条件导出",businessType=BusinessType.EXPORT,isSaveResponseData=false)
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/DashboardController.java

```
@RestController @RequestMapping("/metal")
@PostMapping("/quotation/save") @PreAuthorize("@ss.hasPermi('metal:quotation:add')") @Log(title="保存报价单",businessType=BusinessType.INSERT) public AjaxResult save(@Valid @RequestBody QuotationSaveRequest input){return success(service.saveQuotation(input));}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/FactoryController.java

```
@RequestMapping("/metal/factory")
public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
@RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query) {
@PostMapping
@PutMapping
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/FilterOptionsController.java

```
@RestController @RequestMapping("/metal/filter-options") public class FilterOptionsController {
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/LineController.java

```
@RequestMapping("/metal/production-line")
public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query) {
@PostMapping
@PutMapping
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/MasterOptionsController.java

```
@RestController @RequestMapping("/metal/options") public class MasterOptionsController extends BaseController {
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/MaterialController.java

```
@RestController @Validated @RequestMapping("/metal/material")
@GetMapping("/list") @Log(title="物料管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:material:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
@PostMapping @Log(title="物料管理",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('metal:material:add')") public AjaxResult create(@Valid @RequestBody MaterialRecord form){return success().put("id",service.create(form));}
@PutMapping @Log(title="物料管理",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('metal:material:edit')") public AjaxResult edit(@Valid @RequestBody MaterialRecord form){service.edit(form);return success();}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/ProcessConsumptionController.java

```
@RequestMapping("/metal/work-report/consumption")
public AjaxResult batches(@RequestParam(defaultValue="ALL") String batchScope,@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam(defaultValue="false") boolean includeVoided,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.batches(workOrderId,itemId,processId,includeVoided,pageNum,pageSize,batchScope));}
public AjaxResult orders(@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam @Positive Long batchId,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.orders(workOrderId,itemId,processId,batchId,pageNum,pageSize));}
public AjaxResult lines(@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam @Positive Long batchId,@RequestParam @Positive Long orderId,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.lines(workOrderId,itemId,processId,batchId,orderId,pageNum,pageSize));}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/ProcessController.java

```
@RestController @Validated @RequestMapping("/metal/process")
@GetMapping("/list") @Log(title="工序管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:process:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
@PostMapping @Log(title="工序管理",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('metal:process:add')") public AjaxResult create(@Valid @RequestBody ProcessRecord form){return success().put("id",service.create(form));}
@PutMapping @Log(title="工序管理",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('metal:process:edit')") public AjaxResult edit(@Valid @RequestBody ProcessRecord form){service.edit(form);return success();}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/ProductController.java

```
@RestController @Validated @RequestMapping("/metal/product")
@GetMapping("/list") @Log(title="产品管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:product:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
@PostMapping @Log(title="产品管理",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('metal:product:add')") public AjaxResult create(@Valid @RequestBody ProductRecord form){return success().put("id",service.create(form));}
@PutMapping @Log(title="产品管理",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('metal:product:edit')") public AjaxResult edit(@Valid @RequestBody ProductRecord form){service.edit(form);return success();}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/QuoteController.java

```
@PostMapping("/metal/quotation/image")
@PutMapping("/metal/quote-rule")
@PostMapping("/metal/quotation/calculate")
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/RoleManagementController.java

```
@RestController @Validated @RequestMapping("/system/role")
@GetMapping("/list") @PreAuthorize("@ss.hasPermi('system:role:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
@PostMapping @Log(title="Role",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('system:role:add')") public AjaxResult create(@Valid @RequestBody RoleRecord form){return success().put("id",service.create(form));}
@PutMapping @Log(title="Role",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:role:edit')") public AjaxResult edit(@Valid @RequestBody RoleRecord form){service.edit(form);return success();}
@PutMapping("/changeStatus") @Log(title="Role状态",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:role:edit')") public AjaxResult status(@RequestBody RoleRecord form){service.changeStatus(form);return success();}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/SemiFlowController.java

```
@RestController @RequestMapping("/metal")
public record Close(@NotBlank String requestId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason,@NotNull Boolean confirmed){}
public record Release(@NotBlank String requestId,@NotNull @Min(0) Integer version,@Min(1) @Max(1000000) long quantity,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal netWeight,@NotBlank @Size(max=500) String reason){}
public record Cancel(@NotBlank String requestId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason){}
@PostMapping("/work-order/{id}/partial-close") @PreAuthorize("@ss.hasPermi('metal:work-order:partial-close')") @Log(title="工单部分完工关闭",businessType=BusinessType.UPDATE)
@PostMapping("/work-order/{id}/reopen") @PreAuthorize("@ss.hasPermi('metal:work-order:reopen')") @Log(title="撤销部分关闭",businessType=BusinessType.UPDATE)
@PostMapping("/warehouse/inventory/{id}/semi-release") @PreAuthorize("@ss.hasPermi('metal:semi-release:add')") @Log(title="剩余半成品释放",businessType=BusinessType.INSERT)
@PostMapping("/warehouse/semi-release/{id}/cancel") @PreAuthorize("@ss.hasPermi('metal:semi-release:cancel')") @Log(title="撤销半成品释放",businessType=BusinessType.UPDATE)
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/StaffManagementController.java

```
@RestController @Validated @RequestMapping("/system/user")
@GetMapping("/list") @PreAuthorize("@ss.hasPermi('system:user:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
@PostMapping @Log(title="Staff",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('system:user:add')") public AjaxResult create(@Valid @RequestBody StaffRecord form){return success().put("id",service.create(form));}
@PutMapping @Log(title="Staff",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:edit')") public AjaxResult edit(@Valid @RequestBody StaffRecord form){service.edit(form);return success();}
@PutMapping("/changeStatus") @Log(title="Staff状态",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:edit')") public AjaxResult status(@RequestBody StaffRecord form){service.changeStatus(form);return success();}
@PutMapping("/resetPwd") @Log(title="重置密码",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:resetPwd')") public AjaxResult password(@RequestBody StaffRecord form){service.resetPassword(form);return success();}
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WarehouseController.java

```
@RequestMapping("/metal")
public Map<String, Object> warehouseList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
public Map<String, Object> inventoryList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
public Map<String, Object> warehouseOrderList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.OrderListQuery query) {
public Map<String, Object> processBatchList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
public Map<String, Object> workReportList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
public Map<String, Object> warningRuleList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
public Map<String, Object> warningRecordList(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) String filter, @RequestParam(required = false) Long factoryId) {
@PostMapping("/warehouse")
@PutMapping("/warehouse")
@PostMapping("/warning-rule")
@PutMapping("/warning-rule")
@PostMapping("/inventory/opening")
@PutMapping("/inventory")
@PostMapping("/inventory/{id}/adjust")
public record NumberRequest(@NotBlank String requestId, @NotBlank String kind) {
@PostMapping("/warehouse-order/number/reserve")
@PostMapping("/warehouse-order")
@PostMapping("/process-batch")
@PostMapping("/process-batch/{id}/settle")
@PostMapping("/process-batch/{id}/reverse")
@PostMapping("/warehouse-order/{id}/reverse")
@PostMapping("/warning-record/{id}/handle")
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WarehouseImportController.java

```
@RestController @RequestMapping("/metal/warehouse-order")
@PostMapping("/import") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')")
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WorkOrderController.java

```
@RequestMapping("/metal/work-order")
public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize) {
@NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId) {
@PostMapping("/number/reserve")
@PostMapping
@PutMapping
@PostMapping("/{id}/approve")
@PostMapping("/{id}/reject")
@PostMapping("/{id}/resubmit")
```

### backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/metal/WorkOrderFlowExtensionController.java

```
@RestController @RequestMapping("/metal")
public record Permit(@NotNull @Positive Long itemId,@Min(1) int sourceSequence,@Min(3) int targetSequence,@NotNull Boolean enabled,@Min(0) Integer version,@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,@NotBlank @Size(max=500) String reason){}
@PostMapping("/route-skip-permits") @PreAuthorize("@ss.hasPermi('metal:route-skip:configure')") @Log(title="新增工艺跳步许可",businessType=BusinessType.INSERT)
@PutMapping("/route-skip-permits/{id}") @PreAuthorize("@ss.hasPermi('metal:route-skip:configure')") @Log(title="修改工艺跳步许可",businessType=BusinessType.UPDATE)
```

### frontend/src/views/metal/factory/index.vue

```
<el-table-column label="工厂名称" prop="factoryName" min-width="98" align="center" />
<el-table-column label="详细地址" prop="address" min-width="113" align="center" />
<el-table-column label="工厂简介" prop="introduction" min-width="230" align="center" />
<el-table-column label="更新时间" prop="updateTime" :formatter="dateTimeCell" width="168" align="center" />
<el-form-item label="工厂名称" prop="factoryName"><el-input v-model="form.factoryName" maxlength="100" /></el-form-item>
<el-form-item label="占地面积（万㎡）" prop="area"><el-input v-model="form.area" inputmode="decimal" placeholder="请输入大于0的面积，最多4位小数" /></el-form-item>
<el-form-item label="详细地址" prop="address"><el-input v-model="form.address" type="textarea" :rows="2" maxlength="500" /></el-form-item>
<el-form-item label="工厂简介" prop="introduction"><el-input v-model="form.introduction" type="textarea" :rows="4" maxlength="5000" show-word-limit /></el-form-item>
```

### frontend/src/views/metal/MasterDataPage.vue

```
<el-form-item :label="kind==='line'?'产线编号':kind==='product'?'产品编号':'物料编码'" prop="code"><el-input v-model="form.code" maxlength="64"/></el-form-item>
<el-form-item v-if="['line','product','process'].includes(kind)" :label="kind==='line'?'产线名称':kind==='product'?'产品名称':'工序名称'" prop="name"><el-input v-model="form.name" maxlength="100"/></el-form-item>
<el-form-item v-if="['line','staff','role'].includes(kind)" label="所属工厂" prop="factoryId"><el-select :class="{'line-factory-select':kind==='line'}" @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.factoryId" :clearable="isAdmin" placeholder="请选择所属工厂" :disabled="!isAdmin||(['staff','role'].includes(kind)&&form.id==='1')" @change="factoryChanged" style="width:100%"><el-option v-for="o in factories" :key="o.id" :value="o.id" :label="o.name"/></el-select></el-form-item>
<el-form-item v-if="kind==='material'" label="物料类型" prop="materialType"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.materialType" style="width:100%"><el-option v-for="value in materialTypes" :key="value" :value="value"/></el-select></el-form-item>
<el-form-item label="工序类型" prop="processType"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.processType" style="width:100%"><el-option v-for="(name,index) in processTypes" :key="index" :label="name" :value="index+1"/></el-select></el-form-item>
<el-form-item label="产品类别" prop="category"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.category" style="width:100%" @change="categoryChanged"><el-option v-for="(_,category) in productDimensions" :key="category" :value="category"/></el-select></el-form-item>
<el-form-item required v-for="dimension in dimensionFields" :key="dimension.key" :label="`${dimension.label}（cm）`" :prop="`dimensions.${dimension.key}`" :rules="[{validator:positiveDecimal,trigger:'blur'}]"><el-input v-model="form.dimensions![dimension.key]" inputmode="decimal"/></el-form-item>
<el-form-item label="产品体积（cm³）" prop="volume"><el-input :model-value="readonly?form.volume:automaticVolume" readonly :placeholder="supportedVolumeCategories.includes(form.category??'')?'输入尺寸后自动计算':'该类别暂无计算公式'"/></el-form-item>
<el-form-item label="纯度（%）" prop="purity"><el-input v-model="form.purity" inputmode="decimal"/></el-form-item>
<el-form-item label="理论成品净重（g）" prop="weight"><el-input v-model="form.weight" inputmode="decimal"/></el-form-item>
<span>{{index+1}}</span><div class="process-choice"><label><span class="process-required" aria-hidden="true">* </span>加工工序</label><el-form-item class="process-selection" label-width="0" :prop="`processes.${index}.processId`" :rules="[{required:true,message:'请选择加工工序',trigger:'change'}]"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="step.processId" filterable placeholder="请选择加工工序"><el-option v-for="o in processes" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item></div>
<el-form-item class="loss-field" required label="理论损耗（g/件）" label-position="top" label-width="auto" :prop="`processes.${index}.loss`" :rules="[{validator:lossDecimal,trigger:['blur','change']}]"><el-input v-model="step.loss" placeholder="请输入每件产品的理论损耗" inputmode="decimal" :aria-label="`第${index+1}道工序理论损耗（g/件）`"/></el-form-item>
<el-form-item label="手机号" prop="phonenumber"><el-input v-model="form.phonenumber" maxlength="11"/></el-form-item>
<el-form-item label="姓名" prop="nickName"><el-input v-model="form.nickName" maxlength="100"/></el-form-item>
<el-form-item label="角色" prop="roleIds"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.roleIds" multiple :disabled="form.id==='1'" style="width:100%"><el-option v-for="o in roles" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
<el-form-item v-if="!form.id" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password"/></el-form-item>
<el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" maxlength="100"/></el-form-item>
<el-form-item label="显示顺序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" :precision="0"/></el-form-item>
```

### frontend/src/views/metal/quotation/index.vue

```
<el-form-item label="商品类型" :prop="`items.${index}.category`" :rules="[fieldRule(v=>v?'':'请选择商品类型','change')]"><el-select v-model="item.category" fit-input-width :teleported="false" @change="categoryChanged(item)"><el-option v-for="(_,name) in productDimensions" :key="name" :value="name"/></el-select></el-form-item>
<el-form-item label="商品数量" :prop="`items.${index}.quantity`" :rules="integerRules('商品数量')"><el-input v-model="item.quantity" inputmode="numeric" placeholder="请输入…"/></el-form-item>
<div class="spec-fields"><el-form-item v-for="dimension in productDimensions[item.category]??[]" :key="dimension.key" :prop="`items.${index}.dimensions.${dimension.key}`" :rules="[fieldRule(v=>decimalError(v,dimension.label,{positive:true}))]" :label="dimension.label+'：'" label-width="80px"><el-input v-model="item.dimensions[dimension.key]" inputmode="decimal" placeholder="请输入…"/><span class="dimension-unit">cm</span></el-form-item></div>
<el-form-item v-if="canSave" label="所属工厂" prop="factoryId" :rules="[fieldRule(v=>v?'':'请选择所属工厂','change')]" class="quote-factory">
<div v-if="results.length" class="quote-results"><el-table :data="results" border class="quotation-results-table"><el-table-column type="index" label="序号" width="65"/><el-table-column prop="category" label="商品类型" min-width="135"/><el-table-column prop="quantity" label="数量" align="right" min-width="85"/><el-table-column label="规格尺寸（cm）" min-width="260"><template #default="{row}">{{specification(row)}}</template></el-table-column><el-table-column label="产品图片" width="230"><template #default="{row}"><el-image v-if="row.imageUrl" class="quotation-product-image" :src="row.imageUrl" :preview-src-list="[row.imageUrl]" preview-teleported fit="contain" :alt="row.category+'尺寸图'"/><el-button v-else-if="row.imageState==='error'" link type="primary" @click="loadImage(row)">图片加载失败，点击重试</el-button><span v-else>{{row.imageState==='loading'?'图片加载中…':'暂无图片模板'}}</span></template></el-table-column><el-table-column prop="volume" label="产品体积（cm³）" align="right" min-width="160"/><el-table-column prop="unitPrice" label="单件报价（元）" align="right" min-width="160"/><el-table-column prop="totalPrice" label="金额（元）" align="right" min-width="175"/></el-table><strong>总报价：{{totalPrice}} 元</strong><div v-if="canSave" class="save-quote"><el-button class="blue-button" :loading="savingQuote" :disabled="!!savedQuoteNo||savingQuote||calculating" @click="saveQuote">保存报价单</el-button><span v-if="savedQuoteNo">已保存：{{savedQuoteNo}}</span><el-button v-if="savedQuoteId" class="blue-button" :loading="downloadingQuote" @click="downloadQuote">下载报价单</el-button></div></div>
```

### frontend/src/views/metal/quote-rule/index.vue

```
<el-form-item label="基础单价" prop="basePrice" required><el-input v-model="form.basePrice" inputmode="decimal" class="price-input"/><span class="price-unit">元</span></el-form-item>
<el-form-item label="π值" prop="piValue" required><el-input v-model="form.piValue" inputmode="decimal"/></el-form-item>
```

### frontend/src/views/metal/warehouse-common/BatchForm.vue

```
<template><el-dialog v-model="visible" title="建立加工批次" width="min(650px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"><el-form ref="batchForm" :model="form" :rules="rules" scroll-to-error label-width="120px" :disabled="busy"><el-form-item label="工厂"><el-select v-model="factoryId" :disabled="!admin" fit-input-width @change="factoryChanged"><el-option v-for="o in factories" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item><el-form-item label="生产工单" prop="workOrderId" required><el-select v-model="form.workOrderId" fit-input-width @change="orderChanged"><el-option v-for="o in orders" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item><el-form-item label="产品明细" prop="itemId" required><el-select v-model="form.itemId" fit-input-width @change="form.processId=''"> <el-option v-for="(o,i) in items" :key="o.id" :label="o.snapshot.name+' '+(i+1)+'（'+o.quantity+'件）'" :value="o.id"/></el-select></el-form-item><el-form-item label="工序" prop="processId" required><el-select v-model="form.processId" fit-input-width><el-option v-for="o in processes" :key="o.processId" :label="o.processName" :value="o.processId"/></el-select></el-form-item><el-form-item label="加工数量" prop="plannedQuantity" required><el-input v-model="form.plannedQuantity" @input="v=>quantityInput=String(v)" inputmode="numeric"/></el-form-item></el-form><template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button v-if="actionPermission.has('metal:process-batch:add')" type="primary" :loading="busy" @click="save">确认</el-button></template></el-dialog></template>
```

### frontend/src/views/metal/warehouse-common/BatchReturnConfirmationDialog.vue

```
<el-form-item label="还库日期" prop="date"><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD"/></el-form-item>
<el-form-item label="经办人" prop="operators"><el-select fit-input-width popper-class="batch-return-source-options" v-model="operators" multiple filterable><el-option v-for="p in people" :key="p.id" :label="personLabel(p)" :value="p.id"/></el-select></el-form-item>
<el-form-item label="仓管员" prop="keeper"><el-select fit-input-width popper-class="batch-return-source-options" v-model="keeper" filterable><el-option v-for="p in people" :key="p.id" :label="personLabel(p)" :value="p.id"/></el-select></el-form-item>
<el-table-column label="实测净重(g)" min-width="150"><template #default="{row,$index}"><el-form-item :prop="`lines.${$index}.netWeight`" :rules="[fieldRule(v=>decimalError(v,'还库净重',{positive:row.type!=='RAW_RETURN'}))]" label-width="0"><el-input v-model="row.netWeight" inputmode="decimal" aria-label="还库净重"/></el-form-item></template></el-table-column>
<el-table-column label="半成品件数" min-width="120"><template #default="{row,$index}"><el-form-item v-if="row.type==='SEMI_RETURN'" :prop="`lines.${$index}.quantity`" :rules="[fieldRule(v=>integerError(quantityInputs.get(row)??v,'还库件数'))]" label-width="0"><el-input v-model="row.quantity" @input="v=>quantityInputs.set(row,String(v))" inputmode="numeric" aria-label="还库件数"/></el-form-item><span v-else>—</span></template></el-table-column>
<el-table-column label="毛重(g)／杂质(g)（选填）" min-width="190"><template #default="{row,$index}"><el-form-item :prop="`lines.${$index}.grossWeight`" :rules="[fieldRule(v=>decimalError(v,'毛重',{optional:true}))]" label-width="0"><el-input v-model="row.grossWeight" placeholder="毛重"/></el-form-item><el-form-item :prop="`lines.${$index}.impurity`" :rules="[fieldRule(v=>decimalError(v,'杂质',{optional:true}))]" label-width="0"><el-input v-model="row.impurity" placeholder="杂质"/></el-form-item></template></el-table-column>
<el-form-item label="核对说明" prop="reason" :required="mode==='RETURNED'"><el-input v-model="reason" type="textarea" maxlength="500" :placeholder="mode==='NONE'?'无余料核对说明（选填）':'填写本次全部还料的原因'"/></el-form-item>
<el-form-item prop="checked" label-width="0"><el-checkbox v-model="checked">已核对全部实物；{{mode==='NONE'?'确认无余料':'全部需还物料均已填入本次清单'}}</el-checkbox></el-form-item>
```

### frontend/src/views/metal/warehouse-common/WarehouseOrderForm.vue

```
<el-form-item label="工厂" prop="factoryId" required><el-select :model-value="form.factoryId" :disabled="!admin" fit-input-width @change="changeFactory"><el-option v-for="o in factories" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
<el-form-item prop="type" required :label="kind==='OUT'?'出库类型':kind==='RET'?'还库类型':'入库类型'"><el-select v-model="form.type" fit-input-width @change="typeChanged"><el-option v-for="type in types" :key="type" :label="orderTypeNames[type]" :value="type"/></el-select></el-form-item>
<el-form-item v-if="returning && batchReturning" label="对应出库工单" prop="sourceOrderId" required><el-select v-model="form.sourceOrderId" filterable clearable placeholder="请选择生产出库工单" fit-input-width @change="sourceOrderChanged"><el-option v-for="o in sourceOrders" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
<template v-if="productionAllowed && !batchReturning"><el-form-item label="生产工单" prop="workOrderId" :required="form.type==='PRODUCTION_OUT'"><el-select v-model="form.workOrderId" clearable fit-input-width @change="orderChanged"><el-option v-for="o in orders" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item><el-form-item v-if="form.workOrderId" label="产品明细" prop="itemId" required><el-select :disabled="detailLoading" v-model="itemId" fit-input-width @change="itemChanged"><el-option v-for="(o,i) in items" :key="o.id" :label="o.snapshot.name+' '+(i+1)" :value="o.id"/></el-select></el-form-item><el-form-item v-if="itemId" label="工序" prop="processId" required><el-select :disabled="detailLoading" v-model="processId" fit-input-width @change="processChanged"><el-option v-for="o in steps" :key="o.processId" :label="o.processName" :value="o.processId"/></el-select></el-form-item></template>
<el-alert v-if="recovery&&itemId&&!detailLoading&&!steps.length" type="warning" :closable="false" title="当前产品没有可登记加工余料的工序：须有未结算批次、有效半成品领料及产出，且领料扣除产出和已还料后仍有额度。若有实物余料，请核对产出重量。"/><el-form-item v-if="form.type==='SEMI_RETURN'||recovery" label="还库原因" prop="reason" required><el-input v-model="form.reason" type="textarea" maxlength="500" :placeholder="recovery?'说明加工粉末、边角料的回收情况':'说明未加工半成品还库原因；还库不会自动释放'"/></el-form-item>
<el-form-item prop="orderDate" required :label="kind==='OUT'?'出库日期':kind==='RET'?'还库日期':'入库日期'"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" :teleported="true"/></el-form-item>
<el-form-item label="仓库/库房" prop="warehouseId" required><el-select v-model="form.warehouseId" :disabled="form.type==='SEMI_RETURN'&&!!form.sourceOrderId" fit-input-width @change="warehouseChanged"><el-option v-for="o in warehouses" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
<el-form-item v-for="field in peopleFields" :key="field.key" :prop="field.key" :label="field.label" :rules="[fieldRule(v=>field.required&&(!v||(Array.isArray(v)&&!v.length))?'请选择'+field.label:Array.isArray(v)&&v.length>20?'经办人最多20位':'','change')]" :required="field.required"><el-select v-model="form[field.key]" :multiple="field.key==='operatorIds'" filterable fit-input-width clearable><el-option v-for="o in people" :key="o.id" :label="personLabel(o)" :value="o.id"/></el-select></el-form-item>
<el-table :data="form.lines" border @selection-change="selected=$event"><el-table-column type="selection" width="42"/><el-table-column v-for="c in lineColumns" :key="c.key" :label="c.label" :prop="c.key" :min-width="c.width||90" :label-class-name="kind==='IN'&&!opening&&c.key==='netWeight'?'inbound-net-weight-heading':''"/><el-table-column label="操作" width="140" align="center"><template #default="{row}"><div class="material-line-actions"><el-button class="detail-edit-button" size="small" @click="editLine(row)">编辑</el-button><el-button class="detail-delete-button" size="small" @click="form.lines.splice(form.lines.indexOf(row),1)">删除</el-button></div></template></el-table-column></el-table>
<div v-if="form.settle" ref="settlementQuantity" class="settlement-quantity"><el-form-item label="实际完成数量" prop="completedQuantity" required><el-input v-model="form.completedQuantity" @input="v=>completedQuantityInput=String(v)" inputmode="numeric" placeholder="填写总件数"><template #append>件</template></el-input></el-form-item></div>
<el-form-item prop="choiceId" required :label="recovery?'回收原材料':out?'库存物料':returning?'原出库明细':line.category==='RAW'?'原材料':line.category==='SEMI'?'半成品':'成品'"><el-select v-model="line.choiceId" :popper-class="out?'warehouse-stock-options':undefined" :disabled="boundOutput&&(dependenciesLoading||!selectedOutputItem)||form.type==='PRODUCTION_OUT'&&(!form.workOrderId||!itemId||!processId||productionStockLoading)" :placeholder="recovery?'请选择回收原材料':out?'请选择库存物料':returning?'请选择原出库明细':line.category==='RAW'?'请选择原材料':line.category==='SEMI'?'请选择半成品对应产品':'请选择成品对应产品'" filterable fit-input-width @change="choiceChanged"><el-option v-for="o in choices" :key="o.choiceKey||o.id" :label="materialChoiceLabel(o)" :value="o.choiceKey||o.id" :disabled="batchReturning&&!returnSourceUsable(o)" :aria-label="materialChoiceLabel(o)"><template v-if="out"><div class="stock-option-main">{{stockOptionMain(o)}}</div><div v-if="stockOptionDetail(o)" class="stock-option-detail">{{stockOptionDetail(o)}}</div></template><template v-else>{{materialChoiceLabel(o)}}</template></el-option></el-select><span v-if="boundOutput&&!selectedOutputItem" role="status">请先选择生产工单的产品明细</span></el-form-item>
</el-form-item></template><p v-if="returnLoadError" role="alert">可退额度加载失败，请重试 <el-button link @click="refreshReturnSources(true)">重试</el-button></p><template v-if="returning&&form.type==='SEMI_RETURN'"><el-form-item label="原产品规格"><el-input :model-value="currentReturnSource?.specification||'未记录'" readonly/></el-form-item><el-form-item label="已完成来源工序"><el-input :model-value="currentReturnSource?.originProcessName||'来源工序未记录'" readonly/></el-form-item><el-form-item label="来源批次"><el-input :model-value="currentReturnSource?.batchNo||'未记录'" readonly/></el-form-item><el-form-item label="来源可还件数"><el-input :model-value="currentReturnSource?.remainingQuantity" readonly/></el-form-item><el-form-item label="批次可还件数"><el-input :model-value="currentReturnSource?.batchReturnableQuantity" readonly/></el-form-item><el-alert type="info" :closable="false" title="还库恢复原来源半成品库存，不生成新产出，也不自动释放。"/></template><el-form-item label="用途" prop="purpose"><el-input v-model="line.purpose" maxlength="200"/></el-form-item><el-form-item label="备注" prop="remark"><el-input v-model="line.remark" maxlength="500"/></el-form-item></el-form><template #footer><el-button :disabled="checkingStock" @click="lineVisible=false">取消</el-button><el-button v-if="can('metal:warehouse-order:add')" type="primary" :loading="checkingStock||refreshingReturn||recoveryLoading" :disabled="recoveryLoading||refreshingReturn||returnLoadError||(boundOutput&&(dependenciesLoading||!selectedOutputItem))" @click="confirmLine">确认</el-button></template></el-dialog>
```

### frontend/src/views/metal/warehouse-common/WorkReportDialog.vue

```
<template v-if="report"><el-table :data="report.materials" border class="material-report"><el-table-column label="物料编码" prop="code" width="98"/><el-table-column label="物料名称" prop="name" width="98"/><el-table-column label="分摊损耗(g)" prop="lossWeight" width="116"/><el-table-column label="平均损耗" prop="averageLoss"/></el-table>
<el-button v-show="false" link type="primary" @click="emit('consumption',String(report.processId));visible=false">查看工序消耗明细</el-button><el-collapse v-show="false" class="report-evidence"><el-collapse-item title="查看批次与领还料依据" name="evidence"><p>领料净重：{{report.issuedWeight}}g；原料还库净重：{{report.rawReturnedWeight??'—'}}g</p><p>半成品／成品产出净重：{{report.outputWeight??'—'}}g；完成：{{report.completedQuantity}}件</p><p>损耗：{{report.lossWeight}}g；平均：{{report.averageLoss}}g；阈值：{{report.thresholdValue}}g</p><p>损耗＝领料净重－原料还库净重－半成品还库净重－半成品／成品产出净重；按领用净重比例分摊</p><p v-if="report.voided">报工已作废，拆分重量不展示</p><el-table :data="report.orders" border><el-table-column label="来源单据" prop="orderNo"/><el-table-column label="类型"><template #default="{row}">{{orderTypeNames[row.type]}}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{stateNames[row.status]}}</template></el-table-column></el-table><el-button v-if="canReverse&&!report.voided" type="danger" :disabled="loading" :loading="reversing" @click="reverse">批次纠错（冲销）</el-button></el-collapse-item></el-collapse>
```

### frontend/src/views/metal/work-order/AutoSemiReleaseStatus.vue

```
<template><section v-if="failed||hasActivity" class="auto-status" v-loading="loading"><div class="heading"><strong>正常完成后自动释放</strong><el-button :disabled="loading" @click="load">刷新状态</el-button></div><p v-if="failed" role="alert">自动释放状态加载失败，请重试。</p><template v-if="data"><p>{{data.autoSemiReleaseEligible?'本工单适用正常完成自动释放；部分关闭仍需单独确认。':'历史或未启用自动释放的工单，余量须单独确认释放。'}}</p><p v-if="data.event">处理状态：{{data.event?names[data.event.status]??data.event.status:'尚未触发'}} {{data.event?.message}}</p><div class="scroll"><el-table v-if="data.results?.length" :data="data.results" border><el-table-column prop="inventoryId" label="来源库存" min-width="120"/><el-table-column prop="quantity" label="释放件数" min-width="100"/><el-table-column prop="netWeight" label="释放净重(g)" min-width="150"/><el-table-column label="结果" min-width="140"><template #default="{row}">{{names[row.status]??row.status}}</template></el-table-column><el-table-column prop="message" label="原因" min-width="280"/></el-table></div></template></section></template>
```

### frontend/src/views/metal/work-order/index.vue

```
<el-table-column v-if="can('metal:work-order:edit')||can('metal:work-order:remove')" type="selection" width="50" align="center"/><el-table-column label="工单号" prop="orderNo" min-width="150" align="center"/><el-table-column label="客户" prop="customer" min-width="75" align="center"/><el-table-column label="工厂名称" prop="factoryName" min-width="90" align="center"/><el-table-column label="产线名称" prop="lineName" min-width="90" align="center"/><el-table-column label="负责人" prop="ownerName" min-width="75" align="center"/>
<el-table-column label="创建人" prop="creatorName" min-width="65" align="center"/><el-table-column label="创建时间" prop="createTime" :formatter="dateTimeCell" width="140" align="center"/><el-table-column v-if="can('metal:work-order:query')||can('metal:work-order:edit')||can('metal:work-order:remove')" class-name="order-actions" label="操作" min-width="264" align="center"><template #default="{row}"><el-button v-if="actionPermission.hasAny(['metal:work-order:query'])"  class="blue row-button" @click="view(row)">查看</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:edit'])"  class="orange row-button" :disabled="!orderEditable(row)" :title="row.operationDisabledReason" @click="formRef?.open(row)">编辑</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:remove'])"  class="red row-button" :disabled="row.canDelete!==true||deleting" :title="row.operationDisabledReason" @click="remove([row])">删除</el-button></template></el-table-column>
```

### frontend/src/views/metal/work-order/ProcessConsumption.vue

```
<el-table-column prop="sequenceNo" label="序号" width="62"/>
<el-table-column prop="processName" label="工序" min-width="130"/>
<el-table-column v-for="c in columns" :key="c.key" :prop="c.key" :label="c.label" :min-width="135" align="right"><template #default="{row}">{{row[c.key]??'—'}}</template></el-table-column>
<el-table-column prop="settledCompletedQuantity" label="结算完成(件)" min-width="115" align="right"><template #default="{row}">{{row.settledCompletedQuantity??'—'}}</template></el-table-column>
<el-table-column prop="batchCount" label="有效批次" width="90" align="right"/>
```

### frontend/src/views/metal/work-order/SemiFlowPanel.vue

```
<template v-if="data.progress?.length"><h4>工序数量覆盖</h4><el-table :data="data.progress" border><el-table-column prop="processName" label="工序"/><el-table-column prop="localQuantity" label="本单加工(件)"/><el-table-column prop="externalQuantity" label="外部半成品接入(件)"/><el-table-column prop="skippedQuantity" label="许可跳过(件)"/><el-table-column prop="coveredQuantity" label="数量覆盖合计(件)"/></el-table></template>
<template v-if="data.items?.length"><h4>产品完成情况</h4><el-table :data="data.items" border><el-table-column label="产品"><template #default="{row}">{{productName(row)}}</template></el-table-column><el-table-column prop="plannedQuantity" label="原计划(件)"/><el-table-column prop="completedQuantity" label="最终已结算完成(件)"/><el-table-column prop="cancelledQuantity" :label="data.workOrder.status==='CLOSED_PARTIAL'?'取消继续加工(件)':'尚未完成(件)'"/></el-table></template>
<template v-if="data.stocks?.length"><h4>剩余半成品库存</h4><el-table :data="data.stocks" border><el-table-column prop="name" label="产品"/><el-table-column prop="processName" label="已完成工序"/><el-table-column prop="batchNo" label="来源批次" min-width="180"/><el-table-column prop="quantity" label="实物库存(件)"/><el-table-column prop="netWeight" label="实物净重(g)"/><el-table-column prop="releasedQuantity" label="已释放未领(件)"/><el-table-column prop="unreleasedQuantity" label="尚未释放(件)"/><el-table-column v-if="has('metal:semi-release:add')" label="操作" width="90"><template #default="{row}"><el-button v-if="closed&&has('metal:semi-release:add')&&Number(row.unreleasedQuantity)>0" size="small" type="primary" :disabled="busy" @click="open('release',row)">释放</el-button></template></el-table-column></el-table></template>
<template v-if="data.releases?.length"><h4>释放记录</h4><el-table :data="data.releases" border><el-table-column prop="releaseId" label="释放记录"/><el-table-column prop="name" label="产品"/><el-table-column prop="quantity" label="释放(件)"/><el-table-column prop="netWeight" label="释放净重(g)"/><el-table-column prop="availableQuantity" label="可领(件)"/><el-table-column prop="availableWeight" label="可领净重(g)"/><el-table-column label="状态"><template #default="{row}">{{row.status==='ACTIVE'?'有效':'已撤销'}}</template></el-table-column><el-table-column v-if="has('metal:semi-release:cancel')" label="操作"><template #default="{row}"><el-button v-if="row.status==='ACTIVE'&&has('metal:semi-release:cancel')" size="small" :disabled="busy" @click="open('cancel',row)">撤销释放</el-button></template></el-table-column></el-table></template>
<template v-if="data.uses?.length"><h4>跨工单来源与去向</h4><el-table :data="data.uses" border><el-table-column prop="releaseId" label="来源释放记录"/><el-table-column prop="orderNo" label="生产出库单"/><el-table-column prop="targetWorkOrderNo" label="领用工单"/><el-table-column prop="quantity" label="原领用(件)"/><el-table-column prop="netWeight" label="原领用净重(g)"/><el-table-column prop="status" label="单据状态"/></el-table></template>
<template v-if="data.closures?.length"><h4>关闭操作记录</h4><el-table :data="data.closures" border><el-table-column prop="createTime" label="时间"/><el-table-column label="操作"><template #default="{row}">{{row.action==='CLOSE'?'部分完工关闭':'撤销部分关闭'}}</template></el-table-column><el-table-column prop="reason" label="原因"/></el-table></template>
<template v-if="mode==='close'"><el-table :data="data.items" border><el-table-column label="产品"><template #default="{row}">{{productName(row)}}</template></el-table-column><el-table-column prop="plannedQuantity" label="计划(件)"/><el-table-column prop="completedQuantity" label="实际完成(件)"/><el-table-column prop="cancelledQuantity" label="取消继续加工(件)"/></el-table><p>关闭保留各工序真实加工记录，不补造完成数量。请先处理所有未结算批次。</p><el-checkbox v-model="draft.confirmed" :disabled="busy">已核对实物余料，已领未加工半成品已还库</el-checkbox></template>
<template v-if="mode==='release'"><el-alert :closable="false" type="info" title="还库不会自动释放；本次释放不增加实物库存，原来源与历史损耗保持不变。"/><p>释放后可供同厂、同客户且工序匹配的其他工单领用。</p><p>可释放：{{target.unreleasedQuantity}}件 / {{target.unreleasedWeight}}g</p><div><el-form-item label="释放件数" prop="quantity" required><el-input v-model="draft.quantity" @input="v=>quantityInput=String(v)" inputmode="numeric"/></el-form-item><el-form-item label="释放净重(g)" prop="netWeight" required><el-input v-model="draft.netWeight" inputmode="decimal"/></el-form-item></div></template>
<el-form-item label="操作原因" prop="reason" required><el-input v-model="draft.reason" type="textarea" placeholder="填写操作原因" maxlength="500" show-word-limit :disabled="busy"/></el-form-item>
```

### frontend/src/views/metal/work-order/WorkOrderDetail.vue

```
<div class="detail-columns"><div class="detail-card reports"><div class="section-title">&gt;&gt;已结算报工记录</div><p v-if="reportsLoading" class="report-status" role="status">正在加载报工记录…</p><div v-if="reportState==='error'" class="report-error" role="alert">报工记录加载失败 <el-button link type="primary" @click="loadReports">重试</el-button></div><el-table v-loading="reportsLoading" :data="currentReports" border :empty-text="reportState==='success'?'暂无报工记录':' '"><el-table-column label="工序名称" prop="processName"/><el-table-column label="损耗重量（g）" prop="lossWeight"/><el-table-column label="平均损耗（每件产品的损耗重量）" prop="averageLoss"/><el-table-column label="状态"><template #default="{row}">{{row.status==='ABNORMAL'?'异常':'正常'}}</template></el-table-column><el-table-column v-if="showReportActions" label="操作" width="132" align="center"><template #default="{row}"><div class="report-actions"><el-button v-if="actionPermission.hasAny(['metal:work-report:query'])"  type="primary" size="small" @click="openConsumption(row)">查看</el-button><el-button v-if="canEditReport" type="primary" size="small" @click="reportDialog?.open(row.itemId,row.processId,row.processName)">编辑</el-button></div></template></el-table-column></el-table><p v-if="reportState==='success'&&!currentReports.length" class="empty-note">加工批次结算后自动生成报工记录</p></div>
<div class="detail-card progress"><div class="progress-heading"><div class="section-title">&gt;&gt;工序进程</div><el-tabs class="progress-products" v-model="activeItem" type="card"><el-tab-pane v-for="(item,index) in order.items" :key="item.id||index" :name="String(index)" :label="(item.snapshot?.name??'产品')+' '+(index+1)+'（'+item.quantity+'件）'"/></el-tabs></div><el-table :data="plans" border><el-table-column label="工序名称" prop="processName"/><el-table-column label="开始时间"><template #default="{row}">{{displayDateTime(row.startTime)}}</template></el-table-column><el-table-column label="结束时间"><template #default="{row}">{{displayDateTime(row.endTime)}}</template></el-table-column></el-table></div>
<div v-if="order.reviews?.length" class="detail-card"><div class="section-title">操作记录</div><el-table :data="order.reviews" border><el-table-column prop="createTime" label="时间" :formatter="dateTimeCell"/><el-table-column prop="actorName" label="操作人"/><el-table-column label="操作"><template #default="{row}">{{reviewNames[row.action]||row.action}}</template></el-table-column><el-table-column prop="reason" label="说明"/></el-table></div>
```

### frontend/src/views/metal/work-order/WorkOrderForm.vue

```
<el-form-item label="客户" prop="customer"><el-input v-model="form.customer" maxlength="100" placeholder="请输入…"/></el-form-item>
<el-form-item label="工厂" prop="factoryId"><el-select class="work-order-factory-select" popper-class="work-order-factory-options" :model-value="form.factoryId" :disabled="!isAdmin" fit-input-width :teleported="true" @visible-change="resetSelectScroll" @change="changeFactory"><el-option v-for="option in factories" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item>
<el-form-item label="产线" prop="productionLineId"><el-select :model-value="form.productionLineId" fit-input-width :teleported="false" :disabled="!form.factoryId" @visible-change="resetSelectScroll" @change="changeLine"><el-option v-for="option in lines" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item><p v-if="form.factoryId&&!optionsLoading&&!opening&&!lines.length" class="order-error">当前工厂暂无可用产线，请先在产线管理中创建并绑定</p>
<el-form-item label="开单日期" prop="orderDate"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :teleported="true" placeholder="请选择日期"/></el-form-item>
<el-form-item label="负责人" prop="ownerId"><el-select v-model="form.ownerId" filterable fit-input-width :teleported="false" :disabled="!form.factoryId" @visible-change="resetSelectScroll"><el-option v-for="option in owners" :key="option.id" :value="option.id" :label="ownerLabel(option)"/></el-select></el-form-item>
<el-form-item :label="'选择产品'+(form.items.length>1?' '+(index+1):'')" :prop="`items.${index}.productId`" :rules="[{required:true,message:'请选择产品',trigger:'change'}]"><el-select :model-value="item.productId" fit-input-width :teleported="false" :disabled="!form.productionLineId" @visible-change="resetSelectScroll" @change="value=>changeProduct(item,value)"><el-option v-for="option in availableProducts" :key="option.id" :value="option.id" :label="option.name+(option.hasProcesses?'':'（未配置工序）')" :disabled="!option.hasProcesses"/></el-select><el-button v-if="form.items.length>1" link type="danger" @click="form.items.splice(index,1)">移除</el-button></el-form-item>
<el-form-item label="产品数量" required :prop="'items.'+index+'.quantity'" :rules="[fieldRule(v=>integerError(quantityInputs.get(item)??v,'产品数量'),['blur','change'])]"><el-input v-model="item.quantity" @input="value=>quantityInputs.set(item,String(value))" inputmode="numeric" placeholder="请输入产品数量" style="width:170px"/></el-form-item>
<div class="time-description">计划开始、结束时间</div><el-table :data="item.plans" border class="plan-table"><el-table-column label="工序名称" prop="processName" min-width="130"/><el-table-column label="开始时间" min-width="235"><template #default="{row,$index}"><el-form-item :prop="`items.${index}.plans.${$index}.startTime`" :rules="planRules(item,$index,'startTime')" label-width="0"><el-date-picker v-model="row.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择开始时间"/></el-form-item></template></el-table-column><el-table-column label="结束时间" min-width="235"><template #default="{row,$index}"><el-form-item :prop="`items.${index}.plans.${$index}.endTime`" :rules="planRules(item,$index,'endTime')" label-width="0"><el-date-picker v-model="row.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择结束时间"/></el-form-item></template></el-table-column></el-table>
```

### frontend/src/views/metal/work-order/WorkOrderWeightBalance.vue

```
<template><section class="ledger" v-loading="loading"><div class="heading"><strong>整单重量总账</strong><el-select v-model="item" placeholder="全部产品" clearable @change="load"><el-option v-for="row in data?.items??[]" :key="row.itemId" :value="row.itemId" :label="product(row)"/></el-select><el-button :disabled="loading" @click="load">刷新总账</el-button><el-button v-if="canExport" :disabled="loading||!data" :loading="exporting" @click="download">导出</el-button></div><el-alert v-if="failed" title="总账加载失败，请刷新重试" type="error" :closable="false"/><template v-if="data"><p>{{data.workOrderNo}} · {{statusNames[data.status]}} · {{displayDateTime(data.readTime)}}</p><p>新增投入＝原料领入＋外部半成品领入＋来源半成品净调整。内部流转不重复累计；释放不改变重量。未结算差额含待确认损耗，不代表已称量在制实物。</p><el-alert v-for="warning in data.anomalies" :key="warning" :title="warning" type="warning" :closable="false"/><div class="scroll"><el-table :data="rows" border row-key="key" @expand-change="expanded"><el-table-column type="expand"><template #default="{row}"><div v-loading="detailLoading[row.key]" class="lines"><p v-if="detailErrors[row.key]" role="alert">明细加载失败或总账已变化，请刷新总账。</p><el-table :data="details[row.key]?.rows??[]" border><el-table-column prop="orderNo" label="单据／批次" min-width="210"/><el-table-column prop="code" label="物料编号" min-width="140"/><el-table-column prop="name" label="名称" min-width="140"/><el-table-column prop="weight" label="净重(g)" min-width="140"/><el-table-column prop="quantity" label="件数" width="90"/></el-table><el-pagination v-if="details[row.key]?.total>20" :total="details[row.key].total" :page-size="20" :current-page="pages[row.key]||1" layout="prev,pager,next" @current-change="page=>loadLines(row.key,page)"/></div></template></el-table-column><el-table-column prop="label" label="重量项目" min-width="230"/><el-table-column prop="value" label="净重(g)" min-width="160"/></el-table></div></template></section></template>
```

### frontend/src/views/system/config/index.vue

```
<el-form-item label="参数名称" prop="configName">
<el-form-item label="参数键名" prop="configKey">
<el-form-item label="系统内置" prop="configType">
<el-table-column label="参数主键" align="center" prop="configId" />
<el-table-column label="参数名称" align="center" prop="configName" :show-overflow-tooltip="true" />
<el-table-column label="参数键名" align="center" prop="configKey" :show-overflow-tooltip="true" />
<el-table-column label="参数键值" align="center" prop="configValue" :show-overflow-tooltip="true" />
<el-table-column label="系统内置" align="center" prop="configType">
<el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
<el-form-item label="参数名称" prop="configName">
<el-form-item label="参数键名" prop="configKey">
<el-form-item label="参数键值" prop="configValue">
<el-form-item label="系统内置" prop="configType">
<el-form-item label="备注" prop="remark">
```

### frontend/src/views/system/dept/index.vue

```
<el-form-item label="部门名称" prop="deptName">
<el-form-item label="状态" prop="status">
<el-table-column prop="deptName" label="部门名称" width="260"></el-table-column>
<el-table-column prop="orderNum" label="排序" width="200">
<el-table-column prop="status" label="状态" width="100">
<el-table-column label="创建时间" align="center" prop="createTime" width="200">
<el-form-item label="上级部门" prop="parentId">
<el-form-item label="部门名称" prop="deptName">
<el-form-item label="显示排序" prop="orderNum">
<el-form-item label="负责人" prop="leader">
<el-form-item label="联系电话" prop="phone">
<el-form-item label="邮箱" prop="email">
```

### frontend/src/views/system/dict/data.vue

```
<el-form-item label="字典名称" prop="dictType">
<el-form-item label="字典标签" prop="dictLabel">
<el-form-item label="状态" prop="status">
<el-table-column label="字典编码" align="center" prop="dictCode" />
<el-table-column label="字典标签" align="center" prop="dictLabel">
<el-table-column label="字典键值" align="center" prop="dictValue" />
<el-table-column label="字典排序" align="center" prop="dictSort" />
<el-table-column label="状态" align="center" prop="status">
<el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
<el-form-item label="数据标签" prop="dictLabel">
<el-form-item label="数据键值" prop="dictValue">
<el-form-item label="样式属性" prop="cssClass">
<el-form-item label="显示排序" prop="dictSort">
<el-form-item label="回显样式" prop="listClass">
<el-form-item label="状态" prop="status">
<el-form-item label="备注" prop="remark">
```

### frontend/src/views/system/dict/index.vue

```
<el-form-item label="字典名称" prop="dictName">
<el-form-item label="字典类型" prop="dictType">
<el-form-item label="状态" prop="status">
<el-table-column label="字典编号" align="center" prop="dictId" />
<el-table-column label="字典名称" align="center" prop="dictName" :show-overflow-tooltip="true"/>
<el-table-column label="状态" align="center" prop="status">
<el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
<el-form-item label="字典名称" prop="dictName">
<el-form-item prop="dictName">
<el-form-item label="状态" prop="status">
<el-form-item label="备注" prop="remark">
```

### frontend/src/views/system/menu/index.vue

```
<el-form-item label="菜单名称" prop="menuName">
<el-form-item label="状态" prop="status">
<el-table-column prop="menuName" label="菜单名称" :show-overflow-tooltip="true" width="220">
<el-table-column prop="menuName" label="类型" :show-overflow-tooltip="true" width="100">
<el-table-column prop="orderNum" label="排序" width="200">
<el-table-column prop="perms" label="权限标识" :show-overflow-tooltip="true" />
<el-table-column prop="component" label="组件路径" :show-overflow-tooltip="true" />
<el-table-column prop="status" label="状态" width="80">
<el-form-item label="菜单类型" prop="menuType">
<el-form-item label="菜单图标" prop="icon">
<el-form-item label="显示排序" prop="orderNum">
<el-form-item label="菜单名称" prop="menuName">
<el-form-item prop="routeName">
<el-form-item prop="path">
<el-form-item prop="component">
```

### frontend/src/views/system/notice/index.vue

```
<el-form-item label="公告标题" prop="noticeTitle">
<el-form-item label="操作人员" prop="createBy">
<el-form-item label="类型" prop="noticeType">
<el-table-column label="序号" align="center" prop="noticeId" width="100" />
<el-table-column label="公告类型" align="center" prop="noticeType" width="100">
<el-table-column label="状态" align="center" prop="status" width="100">
<el-table-column label="创建者" align="center" prop="createBy" width="100" />
<el-table-column label="创建时间" align="center" prop="createTime" width="100">
<el-form-item label="公告标题" prop="noticeTitle">
<el-form-item label="公告类型" prop="noticeType">
```

### frontend/src/views/system/notice/ReadUsers.vue

```
<el-form-item prop="searchValue">
<el-table-column label="登录名称" prop="userName" align="center" :show-overflow-tooltip="true" />
<el-table-column label="用户名称" prop="nickName" align="center" :show-overflow-tooltip="true" />
<el-table-column label="所属部门" prop="deptName" align="center" :show-overflow-tooltip="true" />
<el-table-column label="手机号码" prop="phonenumber" align="center" width="120" />
<el-table-column label="阅读时间" prop="readTime" align="center" width="160">
```

### frontend/src/views/system/post/index.vue

```
<el-form-item label="岗位编码" prop="postCode">
<el-form-item label="岗位名称" prop="postName">
<el-form-item label="状态" prop="status">
<el-table-column label="岗位编号" align="center" prop="postId" />
<el-table-column label="岗位编码" align="center" prop="postCode" />
<el-table-column label="岗位名称" align="center" prop="postName" />
<el-table-column label="岗位排序" align="center" prop="postSort" />
<el-table-column label="状态" align="center" prop="status">
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
<el-form-item label="岗位名称" prop="postName">
<el-form-item label="岗位编码" prop="postCode">
<el-form-item label="岗位顺序" prop="postSort">
<el-form-item label="岗位状态" prop="status">
<el-form-item label="备注" prop="remark">
```

### frontend/src/views/system/role/authUser.vue

```
<el-form-item label="用户名称" prop="userName">
<el-form-item label="手机号码" prop="phonenumber">
<el-table-column label="用户名称" prop="userName" :show-overflow-tooltip="true" />
<el-table-column label="用户昵称" prop="nickName" :show-overflow-tooltip="true" />
<el-table-column label="邮箱" prop="email" :show-overflow-tooltip="true" />
<el-table-column label="手机" prop="phonenumber" :show-overflow-tooltip="true" />
<el-table-column label="状态" align="center" prop="status">
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
```

### frontend/src/views/system/role/index.vue

```
<el-form-item label="角色名称" prop="roleName">
<el-form-item label="权限字符" prop="roleKey">
<el-form-item label="状态" prop="status">
<el-table-column label="角色编号" prop="roleId" width="120" />
<el-table-column label="角色名称" prop="roleName" :show-overflow-tooltip="true" width="150" />
<el-table-column label="权限字符" prop="roleKey" :show-overflow-tooltip="true" width="150" />
<el-table-column label="显示顺序" prop="roleSort" width="100" />
<el-table-column label="创建时间" align="center" prop="createTime">
<el-form-item label="角色名称" prop="roleName">
<el-form-item prop="roleKey">
<el-form-item label="角色顺序" prop="roleSort">
```

### frontend/src/views/system/role/selectUser.vue

```
<el-form-item label="用户名称" prop="userName">
<el-form-item label="手机号码" prop="phonenumber">
<el-table-column label="用户名称" prop="userName" :show-overflow-tooltip="true" />
<el-table-column label="用户昵称" prop="nickName" :show-overflow-tooltip="true" />
<el-table-column label="邮箱" prop="email" :show-overflow-tooltip="true" />
<el-table-column label="手机" prop="phonenumber" :show-overflow-tooltip="true" />
<el-table-column label="状态" align="center" prop="status">
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
```

### frontend/src/views/system/user/authRole.vue

```
<el-form-item label="用户昵称" prop="nickName">
<el-form-item label="登录账号" prop="userName">
<el-table-column label="角色编号" align="center" prop="roleId" />
<el-table-column label="角色名称" align="center" prop="roleName" />
<el-table-column label="权限字符" align="center" prop="roleKey" />
<el-table-column label="创建时间" align="center" prop="createTime" width="180">
```

### frontend/src/views/system/user/index.vue

```
<el-form-item label="用户名称" prop="userName">
<el-form-item label="手机号码" prop="phonenumber">
<el-form-item label="状态" prop="status">
<el-table-column label="用户编号" align="center" key="userId" prop="userId" v-if="columns.userId.visible" />
<el-table-column label="用户昵称" align="center" key="nickName" prop="nickName" v-if="columns.nickName.visible" :show-overflow-tooltip="true" />
<el-table-column label="部门" align="center" key="deptName" prop="dept.deptName" v-if="columns.deptName.visible" :show-overflow-tooltip="true" />
<el-table-column label="手机号码" align="center" key="phonenumber" prop="phonenumber" v-if="columns.phonenumber.visible" width="120" />
<el-table-column label="创建时间" align="center" prop="createTime" v-if="columns.createTime.visible" width="160">
<el-form-item label="用户昵称" prop="nickName">
<el-form-item label="归属部门" prop="deptId">
<el-form-item label="手机号码" prop="phonenumber">
<el-form-item label="邮箱" prop="email">
<el-form-item v-if="form.userId == undefined" label="用户名称" prop="userName">
<el-form-item v-if="form.userId == undefined" label="用户密码" prop="password" :rules="pwdValidator">
```

### frontend/src/views/system/user/profile/resetPwd.vue

```
<el-form-item label="旧密码" prop="oldPassword">
<el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
<el-form-item label="确认密码" prop="confirmPassword">
```

### frontend/src/views/system/user/profile/userInfo.vue

```
<el-form-item label="用户昵称" prop="nickName">
<el-form-item label="手机号码" prop="phonenumber">
<el-form-item label="邮箱" prop="email">
```
