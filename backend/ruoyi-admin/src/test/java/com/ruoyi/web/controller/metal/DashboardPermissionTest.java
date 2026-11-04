package com.ruoyi.web.controller.metal;

import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.metal.service.DashboardService;
import com.ruoyi.metal.service.ProductDimensionImage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardPermissionTest {
 @Configuration @EnableMethodSecurity
 static class Config {
  @Bean(name="ss") PermissionService permissions(){return mock(PermissionService.class);}
  @Bean DashboardService service(){return mock(DashboardService.class);}
  @Bean DashboardController controller(DashboardService service){return new DashboardController(service,mock(ProductDimensionImage.class));}
 }
 @Test void bothEndpointsRequireTheDashboardGrant(){
  try(var context=new AnnotationConfigApplicationContext(Config.class)){
   var controller=context.getBean(DashboardController.class);var permissions=context.getBean(PermissionService.class);var service=context.getBean(DashboardService.class);
   when(permissions.hasPermi("metal:dashboard:list")).thenReturn(false);
   assertThrows(AccessDeniedException.class,()->controller.dashboard(null));
   assertThrows(AccessDeniedException.class,controller::dashboardFactories);
   verifyNoInteractions(service);
   when(permissions.hasPermi("metal:dashboard:list")).thenReturn(true);
   controller.dashboard(7L);controller.dashboardFactories();
   verify(service).dashboard(7L);verify(service).dashboardFactories();
  }
 }
}
