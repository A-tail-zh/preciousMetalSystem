package com.ruoyi.metal.service;
import java.util.*;import java.math.BigDecimal;import java.time.LocalDate;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.InboundSubmissionRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
class InboundSubmissionRulesTest {
 private Line line(){return new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,null,null,null);}
 private Request request(List<Group> groups){return new Request(UUID.randomUUID().toString(),1L,"RAW_IN",LocalDate.now(),List.of(1L),1L,null,null,null,null,null,null,null,false,null,groups);}
 @Test void acceptsTwoDifferentWarehouses(){assertDoesNotThrow(()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())),new Group(2L,null,List.of(line()))))));}
 @Test void rejectsRepeatedWarehouse(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of(line())),new Group(1L,null,List.of(line()))))));}
 @Test void rejectsEmptyGroup(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,List.of())))));}
 @Test void totalLimitIncludesAllGroups(){assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,null,Collections.nCopies(600,line())),new Group(2L,null,Collections.nCopies(401,line()))))));}
 @Test void childRequestIsStablePerWarehouse(){var root=UUID.randomUUID().toString();assertEquals(InboundSubmissionRules.childRequest(root,1L),InboundSubmissionRules.childRequest(root,1L));assertNotEquals(InboundSubmissionRules.childRequest(root,1L),InboundSubmissionRules.childRequest(root,2L));}
 @Test void outputActionExpressionSeparatesLegacyAndGroupedIds(){var sql=BatchOutputQueries.actionKey("o");assertTrue(sql.contains("inbound_submission_id"));assertTrue(sql.contains("D:"));assertTrue(sql.contains("G:"));}
 private Request withoutCommonPeople(List<Group> groups){return new Request(UUID.randomUUID().toString(),1L,"RAW_IN",LocalDate.now(),null,null,null,null,null,null,null,null,null,false,null,groups);}
 @Test void allIndependentGroupsDoNotRequireUnusedCommonPeople(){
  var groups=List.of(new Group(1L,new People(List.of(2L),3L,null,null),List.of(line())),new Group(2L,new People(List.of(4L),5L,null,null),List.of(line())));
  assertDoesNotThrow(()->InboundSubmissionRules.validate(withoutCommonPeople(groups)));
 }
 @Test void inheritedGroupStillRequiresCommonPeople(){
  var groups=List.of(new Group(1L,new People(List.of(2L),3L,null,null),List.of(line())),new Group(2L,null,List.of(line())));
  assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(withoutCommonPeople(groups)));
  assertDoesNotThrow(()->InboundSubmissionRules.validate(request(groups)));
 }
 @Test void independentGroupCannotFallBackToValidCommonPeople(){
  for(var p:List.of(new People(List.of(),3L,null,null),new People(List.of(2L),null,null,null),new People(List.of(2L,2L),3L,null,null),new People(List.of(-1L),3L,null,null),new People(Collections.nCopies(21,2L),3L,null,null)))
   assertThrows(ServiceException.class,()->InboundSubmissionRules.validate(request(List.of(new Group(1L,p,List.of(line()))))));
 }
 @Test void beanValidationAllowsUnusedCommonPeopleAndStillChecksOverrides(){
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();
   var good=withoutCommonPeople(List.of(new Group(1L,new People(List.of(2L),3L,null,null),List.of(line()))));
   assertTrue(validator.validate(good).isEmpty());
   var bad=withoutCommonPeople(List.of(new Group(1L,new People(List.of(),null,null,null),List.of(line()))));
   var paths=validator.validate(bad).stream().map(v->v.getPropertyPath().toString()).toList();
   assertTrue(paths.contains("groups[0].peopleOverride.operatorIds"));assertTrue(paths.contains("groups[0].peopleOverride.keeperId"));
  }
 }
 @Test void childOrderUsesItsOwnPeopleInsteadOfUnusedCommonPeople()throws Exception{
  var people=new People(List.of(2L),3L,4L,5L);var group=new Group(1L,people,List.of(line()));
  var method=InboundSubmissionService.class.getDeclaredMethod("order",Request.class,Group.class,Long.class,Integer.class);method.setAccessible(true);
  var order=(com.ruoyi.metal.catalog.WarehouseRequests.Order)method.invoke(new InboundSubmissionService(null,null,null),withoutCommonPeople(List.of(group)),group,null,null);
  assertEquals(people.operatorIds(),order.operatorIds());assertEquals(people.keeperId(),order.keeperId());assertEquals(people.inspectorId(),order.inspectorId());assertEquals(people.reviewerId(),order.reviewerId());
 }
}
