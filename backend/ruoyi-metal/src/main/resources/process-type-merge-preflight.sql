-- Read only. Every factory-owned process is listed, including deleted rows.
SELECT p.factory_id factoryId,p.process_id processId,p.process_name processName,
       p.process_type oldTypeId,t.type_name oldTypeName,p.semantic_code semanticCode,
       CASE p.semantic_code WHEN 'ACID_WASH' THEN '酸洗' WHEN 'SMELTING' THEN '融定' ELSE t.type_name END targetTypeName,
       CASE
         WHEN t.type_id IS NULL THEN '工序类型不存在'
         WHEN t.factory_id IS NULL OR t.factory_id<>p.factory_id THEN '工序类型不属于本厂'
         WHEN p.semantic_code IS NULL OR p.semantic_code NOT IN ('NORMAL','ACID_WASH','SMELTING') THEN '处理规则缺失或非法'
         WHEN t.type_name='酸洗' AND p.semantic_code<>'ACID_WASH' THEN '酸洗固定名称与原规则冲突'
         WHEN t.type_name='融定' AND p.semantic_code<>'SMELTING' THEN '融定固定名称与原规则冲突'
         ELSE NULL
       END issue,
       EXISTS(SELECT 1 FROM metal_product_process pp WHERE pp.process_id=p.process_id) productReferenced,
       EXISTS(SELECT 1 FROM metal_production_order_step s WHERE s.process_id=p.process_id) orderReferenced
FROM metal_process p
JOIN metal_factory f ON f.factory_id=p.factory_id AND f.del_flag='0'
LEFT JOIN metal_process_type t ON t.type_id=p.process_type
UNION ALL
SELECT p.factory_id,p.process_id,p.process_name,p.process_type,t.type_name,p.semantic_code,
       t.type_name,'活动工厂固定类型被公共或已删除工厂工序引用',
       EXISTS(SELECT 1 FROM metal_product_process pp WHERE pp.process_id=p.process_id),
       EXISTS(SELECT 1 FROM metal_production_order_step s WHERE s.process_id=p.process_id)
FROM metal_process_type t
JOIN metal_factory f ON f.factory_id=t.factory_id AND f.del_flag='0'
JOIN metal_process p ON p.process_type=t.type_id
LEFT JOIN metal_factory owner ON owner.factory_id=p.factory_id
WHERE t.type_name IN ('酸洗','融定') AND NOT(p.factory_id <=> t.factory_id)
  AND (owner.factory_id IS NULL OR owner.del_flag<>'0')
ORDER BY factoryId,processId;
