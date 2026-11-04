package com.ruoyi.web.controller.metal;
import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class FactoryFilterScopeTest {
 @Test void productAutocompleteIsFactoryScoped(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.currentFactory()).thenReturn(7L);var login=new LoginUser();login.setPermissions(Set.of("metal:product:list"));
  try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getLoginUser).thenReturn(login);new FilterOptionsController(jdbc,scope).suggestions("product","name","产品",null);verify(jdbc).queryForList(contains("factory_id=?"),any(Object[].class));}
 }
 @Test void productCandidatesNeverUseGlobalRows(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.currentFactory()).thenReturn(7L);var login=new LoginUser();login.setPermissions(Set.of("metal:production-line:list"));
  try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getLoginUser).thenReturn(login);new FilterOptionsController(jdbc,scope).options("products","line","",1,null);verify(jdbc).queryForList(contains("factory_id=?"),any(Object[].class));}
 }
}
