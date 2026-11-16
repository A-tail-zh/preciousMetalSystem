package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardCategoryCountsTest {
 @Test void countsDocumentsOncePerCategoryAcrossMixedQuotations(){
  var result=DashboardService.quotationCategoryCounts(List.of(
   Map.of("categories","铱杆实心、铱环","quoteCount",2L),
   Map.of("categories","铱杆实心","quoteCount",3L),
   Map.of("categories","铱环、铱环、 ","quoteCount",1L)));
  assertEquals(Map.of("铱杆实心",5L,"铱环",3L),result);
 }
 @Test void emptyMonthHasNoCategories(){assertTrue(DashboardService.quotationCategoryCounts(List.of()).isEmpty());}
}
