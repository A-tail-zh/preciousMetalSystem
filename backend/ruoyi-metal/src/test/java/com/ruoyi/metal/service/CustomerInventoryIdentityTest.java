package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.lang.reflect.*;
class CustomerInventoryIdentityTest {
 @Test void externalProductsMustHaveSameCustomerBeforeMerging()throws Exception{
  Method check=WarehouseService.class.getDeclaredMethod("validateCustomerIdentity",String.class,String.class,String.class);check.setAccessible(true);
  for(String category:new String[]{"SEMI","FINISHED"}){
   assertDoesNotThrow(()->check.invoke(null,category," 客户A ","客户A"));
   assertDoesNotThrow(()->check.invoke(null,category,null," "));
   for(String[] names:new String[][]{{"客户A","客户B"},{null,"客户A"},{"客户A",null},{"Acme","acme"}})assertThrows(InvocationTargetException.class,()->check.invoke(null,category,names[0],names[1]));
  }
  assertDoesNotThrow(()->check.invoke(null,"RAW","来源A","来源B"));
 }
}
