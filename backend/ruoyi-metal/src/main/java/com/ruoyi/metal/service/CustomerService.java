package com.ruoyi.metal.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.CustomerMapper;
import com.ruoyi.metal.security.FactoryScope;

@Service
public class CustomerService extends MasterService<CustomerRecord> {
    public CustomerService(CustomerMapper mapper, FactoryScope scope) { super(mapper, scope); }

    @Override protected boolean scoped() { return true; }
    @Override protected void access(CustomerRecord row) { scope.check(row.getFactoryId()); }

    @Override protected void prepare(CustomerRecord row, CustomerRecord previous) {
        if (!scope.isAdmin()) {
            Long own = scope.currentFactory();
            if (own == null) throw new ServiceException("请先配置有效的所属工厂", 403);
            if (row.getFactoryId() != null && !own.equals(row.getFactoryId()))
                throw new ServiceException("不能操作其他工厂的客户", 403);
            row.setFactoryId(own);
        }
        if (previous != null && row.getFactoryId() == null) row.setFactoryId(previous.getFactoryId());
        if (row.getFactoryId() == null) throw new ServiceException("请选择所属工厂");
        if (previous != null && !Objects.equals(previous.getFactoryId(), row.getFactoryId()))
            throw new ServiceException("客户创建后不能更换所属工厂");
        scope.lockFactories(List.of(row.getFactoryId()));
    }

    @Override protected void beforeLock(CustomerRecord row) {
        CustomerRecord previous = mapper.detail(row.getId());
        if (previous != null) {
            access(previous);
            scope.lockFactories(List.of(previous.getFactoryId()));
        }
    }

    @Override protected void beforeDelete(List<Long> ids) {
        List<Long> factories = new ArrayList<>();
        for (Long id : ids) {
            CustomerRecord row = mapper.detail(id);
            if (row != null) { access(row); factories.add(row.getFactoryId()); }
        }
        scope.lockFactories(factories);
    }
}
