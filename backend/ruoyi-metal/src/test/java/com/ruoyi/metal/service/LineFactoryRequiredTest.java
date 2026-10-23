package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.LineRecord;
import com.ruoyi.metal.mapper.LineMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LineFactoryRequiredTest {
    @Test void evenAdministratorMustChooseFactoryBeforeSavingLine() {
        var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        var service=new LineService(mock(LineMapper.class),scope,mock(JdbcTemplate.class));
        var line=new LineRecord();line.setProductIds(List.of());
        assertTrue(assertThrows(ServiceException.class,()->service.prepare(line,null)).getMessage().contains("请选择所属工厂"));
    }
    @Test void boundFactoryAloneDoesNotBlockDeletingUnreferencedLine() {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        var service=new LineService(mock(LineMapper.class),mock(FactoryScope.class),jdbc);
        var line=new LineRecord();line.setId(7L);line.setFactoryId(3L);
        assertDoesNotThrow(()->service.deletable(line));
    }
}
