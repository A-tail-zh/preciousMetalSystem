package com.ruoyi.framework.audit;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.utils.PageUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaginationCleanupTest {
 @Test void validationFailureBeforeSqlMustNotContaminateNextRequest(){
  assertThrows(IllegalArgumentException.class,()->PageUtils.withPage(1,16,()->{assertNotNull(PageHelper.getLocalPage());throw new IllegalArgumentException("invalid date range");}));
  assertNull(PageHelper.getLocalPage());
 }
 @Test void successWithoutSqlAlsoClearsAndNextRequestGetsItsOwnPage(){
  assertEquals("empty",PageUtils.withPage(2,16,()->"empty"));assertNull(PageHelper.getLocalPage());
  PageUtils.withPage(3,5,()->{assertEquals(3,PageHelper.getLocalPage().getPageNum());assertEquals(5,PageHelper.getLocalPage().getPageSize());return null;});assertNull(PageHelper.getLocalPage());
 }
}
