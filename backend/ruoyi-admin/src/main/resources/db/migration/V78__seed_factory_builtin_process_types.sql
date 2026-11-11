-- Keep existing names and process references intact; only add missing built-in types.
INSERT INTO metal_process_type(factory_id,type_name,builtin_code,create_by,update_by)
SELECT f.factory_id,'酸洗','ACID_WASH','system','system'
FROM metal_factory f
WHERE f.del_flag='0'
 AND NOT EXISTS (SELECT 1 FROM metal_process_type t WHERE t.factory_id=f.factory_id
  AND (t.type_name='酸洗' OR t.builtin_code='ACID_WASH'));

INSERT INTO metal_process_type(factory_id,type_name,builtin_code,create_by,update_by)
SELECT f.factory_id,'融定','SMELTING','system','system'
FROM metal_factory f
WHERE f.del_flag='0'
 AND NOT EXISTS (SELECT 1 FROM metal_process_type t WHERE t.factory_id=f.factory_id
  AND (t.type_name='融定' OR t.builtin_code='SMELTING'));
