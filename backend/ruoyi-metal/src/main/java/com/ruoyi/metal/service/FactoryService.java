package com.ruoyi.metal.service;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.domain.*;
import com.ruoyi.metal.dto.FactoryRequest;
import com.ruoyi.metal.mapper.FactoryMapper;

/**
 * 工厂业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class FactoryService {
    private final FactoryMapper mapper;
    private final com.ruoyi.metal.security.FactoryScope scope;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public FactoryService(FactoryMapper mapper, com.ruoyi.metal.security.FactoryScope scope, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.mapper = mapper; this.scope=scope; this.jdbc=jdbc; }

    /**
     * 查询列表并补充关联资料。
     */
    public List<Factory> list() {
        List<Factory> factories = mapper.selectList(scope.currentFactory());
        if (!factories.isEmpty()) {
            Map<String, List<ProductionLine>> lines = mapper.selectLines(factories.stream()
                .map(f -> Long.valueOf(f.getFactoryId())).toList()).stream()
                .collect(Collectors.groupingBy(ProductionLine::getFactoryId));
            factories.forEach(f -> f.setProductionLines(lines.getOrDefault(f.getFactoryId(), List.of())));
        }
        return factories;
    }

    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    public Factory detail(Long id) {
        scope.check(id);
        Factory factory = required(mapper.selectById(id));
        factory.setProductionLines(mapper.selectLines(List.of(id)));
        return factory;
    }

    /**
     * 校验工厂访问权限后获取可关联的产线选项。
     */
    public List<ProductionLine> options(Long factoryId) {
        if(!scope.isAdmin()){scope.check(factoryId);return mapper.selectLines(List.of(factoryId));}
        if (factoryId != null) required(mapper.selectById(factoryId));
        return mapper.selectOptions(factoryId);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增记录并保存关联资料。
     */
    public String create(FactoryRequest form, String actor) {
        scope.adminOnly();
        if (form.getFactoryId() != null) throw new ServiceException("新增时不能指定工厂ID");
        List<Long> ids = checkedIds(form.getProductionLineIds());
        mapper.insert(form, actor);
        validateLines(ids, mapper.lockLines(ids, null), form.getFactoryId());
        assign(ids, form.getFactoryId());
        return form.getFactoryId().toString();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 修改记录并更新关联资料。
     */
    public void edit(FactoryRequest form, String actor) {
        scope.check(form.getFactoryId());
        if (form.getFactoryId() == null || form.getFactoryId() <= 0 || form.getVersion() == null)
            throw new ServiceException("编辑时必须提供工厂ID及版本号");
        List<Long> ids = checkedIds(form.getProductionLineIds());
        Factory factory = required(mapper.lockFactory(form.getFactoryId()));
        if (!factory.getVersion().equals(form.getVersion())) throw new ServiceException("数据已被修改，请刷新后重试");
        List<ProductionLine> lockedLines = mapper.lockLines(ids, form.getFactoryId());
        if (!scope.isAdmin() && lockedLines.stream().anyMatch(line -> ids.contains(Long.valueOf(line.getProductionLineId())) && !Objects.equals(line.getFactoryId(), form.getFactoryId().toString())))
            throw new ServiceException("产线归属调整仅限超级管理员");
        for (ProductionLine line : lockedLines) if (Objects.equals(line.getFactoryId(),form.getFactoryId().toString()) && !ids.contains(Long.valueOf(line.getProductionLineId()))) WorkOrderReferences.lineMove(jdbc,Long.valueOf(line.getProductionLineId()));
        validateLines(ids, lockedLines, form.getFactoryId());
        if (mapper.update(form, actor) != 1) throw new ServiceException("数据已被修改，请刷新后重试");
        mapper.clearLines(form.getFactoryId());
        assign(ids, form.getFactoryId());
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 检查工厂及其业务引用后批量逻辑删除，异常时回滚事务。
     */
    public void delete(Long[] ids, String actor) {
        scope.adminOnly();
        if (ids == null || ids.length == 0 || ids.length > 100)
            throw new ServiceException("请选择1至100个工厂");
        if (Arrays.stream(ids).anyMatch(id -> id == null || id <= 0)) throw new ServiceException("工厂ID无效");
        List<Long> sorted = Arrays.stream(ids).distinct().sorted().toList();
        List<Factory> factories = sorted.stream().map(id -> required(mapper.lockFactory(id))).toList();
        List<String> blocked = new ArrayList<>();
        for (Factory f : factories) {
            Long id=Long.valueOf(f.getFactoryId());
            WorkOrderReferences.factory(jdbc,id);WarehouseReferences.factory(jdbc,id);
            if (mapper.countLines(id) > 0 || jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM sys_user WHERE factory_id=? AND del_flag='0') + (SELECT COUNT(*) FROM sys_role WHERE factory_id=? AND del_flag='0')",Integer.class,id,id)>0) blocked.add(f.getFactoryName());
        }
        if (!blocked.isEmpty()) throw new ServiceException("工厂下存在关联产线、人员或角色，请先解除关联：" + String.join("、", blocked));
        sorted.forEach(id -> mapper.delete(id, actor));
    }

    private Factory required(Factory f) {
        if (f == null) throw new ServiceException("工厂不存在或已删除");
        return f;
    }
    private List<Long> checkedIds(List<Long> ids) {
        if (ids == null || ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("关联产线参数无效");
        if (new HashSet<>(ids).size() != ids.size()) throw new ServiceException("关联产线不能重复");
        return ids.stream().sorted().toList();
    }
    private void validateLines(List<Long> ids, List<ProductionLine> lines, Long factoryId) {
        Map<Long, ProductionLine> found = lines.stream().collect(Collectors.toMap(l -> Long.valueOf(l.getProductionLineId()), l -> l));
        for (Long id : ids) {
            ProductionLine line = found.get(id);
            if (line == null) throw new ServiceException("关联产线不存在：" + id);
            if (line.getFactoryId() != null && !line.getFactoryId().equals(factoryId.toString()))
                throw new ServiceException("产线已属于其他工厂：" + line.getProductionLineName());
        }
    }
    private void assign(List<Long> ids, Long factoryId) {
        if (!ids.isEmpty() && mapper.assignLines(ids, factoryId) != ids.size())
            throw new ServiceException("产线归属发生变化，请刷新后重试");
    }
}
