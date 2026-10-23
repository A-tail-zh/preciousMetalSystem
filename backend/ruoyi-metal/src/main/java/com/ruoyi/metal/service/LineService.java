package com.ruoyi.metal.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.LineMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;

/**
 * 产线业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class LineService extends MasterService<LineRecord> {
    private final JdbcTemplate jdbc;

    public LineService(LineMapper mapper, FactoryScope scope, JdbcTemplate jdbc) {
        super(mapper, scope);
        this.jdbc = jdbc;
    }

    /**
     * 声明列表是否按照当前用户所属工厂过滤。
     */
    @Override
    protected boolean scoped() {
        return true;
    }

    /**
     * 校验当前用户对目标记录所属工厂的访问权限。
     */
    @Override
    protected void access(LineRecord row) {
        if (!scope.isAdmin()) scope.check(row.getFactoryId());
    }

    /**
     * 在锁定主记录前检查访问权限，并按固定顺序锁定涉及的工厂。
     */
    @Override
    protected void beforeLock(LineRecord form) {
        LineRecord old = mapper.detail(form.getId());
        if (old != null) {
            access(old);
            scope.lockFactories(Arrays.asList(old.getFactoryId(), form.getFactoryId()));
        }
    }

    /**
     * 批量删除前检查目标记录的访问权限，并锁定涉及的工厂。
     */
    @Override
    protected void beforeDelete(List<Long> ids) {
        List<Long> factories = new ArrayList<>();
        for (Long id : ids) {
            LineRecord row = mapper.detail(id);
            if (row != null) {
                access(row);
                if (row.getFactoryId() != null) factories.add(row.getFactoryId());
            }
        }
        scope.lockFactories(factories);
    }

    /**
     * 保存前校验业务数据，并准备需要持久化的字段。
     */
    @Override
    protected void prepare(LineRecord row, LineRecord old) {
        if (old != null && !Objects.equals(old.getFactoryId(), row.getFactoryId())) {
            scope.adminOnly();
            WorkOrderReferences.lineMove(jdbc, row.getId());
        }
        if (row.getFactoryId() == null) throw new ServiceException("请选择所属工厂");
        scope.lockFactories(Collections.singletonList(row.getFactoryId()));
        if (row.getProductIds() == null || row.getProductIds().size() > 1000 || row.getProductIds().stream().anyMatch(i -> i == null || i <= 0) || new HashSet<>(row.getProductIds()).size() != row.getProductIds().size())
            throw new ServiceException("产品关联无效或重复");
        if (old != null) WorkOrderReferences.lineProducts(jdbc, row.getId(), row.getProductIds());
        for (Long id : row.getProductIds().stream().sorted().toList())
            if (jdbc.queryForList("SELECT product_id FROM metal_product WHERE product_id=? AND del_flag='0' FOR UPDATE", id).isEmpty())
                throw new ServiceException("关联产品不存在或已删除");
    }

    /**
     * 保存主记录的关联关系。
     */
    @Override
    protected void relations(LineRecord row) {
        jdbc.update("DELETE FROM metal_line_product WHERE production_line_id=?", row.getId());
        for (Long id : row.getProductIds())
            jdbc.update("INSERT INTO metal_line_product(production_line_id,product_id) VALUES(?,?)", row.getId(), id);
    }

    /**
     * 为查询结果批量补充关联资料。
     */
    @Override
    protected void hydrate(List<LineRecord> rows) {
        if (rows.isEmpty()) return;
        Map<Long, LineRecord> indexed = new HashMap<>();
        rows.forEach(r -> {
            r.setProductIds(new ArrayList<>());
            r.setProductNames(new ArrayList<>());
            indexed.put(r.getId(), r);
        });
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("SELECT lp.production_line_id,lp.product_id,p.product_name FROM metal_line_product lp JOIN metal_product p ON p.product_id=lp.product_id AND p.del_flag='0' WHERE lp.production_line_id IN (" + placeholders + ") ORDER BY lp.product_id", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            LineRecord line = indexed.get(r.getLong("production_line_id"));
            line.getProductIds().add(r.getLong("product_id"));
            line.getProductNames().add(r.getString("product_name"));
        }, rows.stream().map(LineRecord::getId).toArray());
    }

    /**
     * 检查业务引用和保护规则，不满足删除条件时抛出异常。
     */
    @Override
    protected void deletable(LineRecord row) {
        WorkOrderReferences.lineDelete(jdbc, row.getId());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM metal_line_product WHERE production_line_id=?", Integer.class, row.getId()) > 0)
            throw new ServiceException("请先解除产线的产品关联：" + row.getName());
    }
}
