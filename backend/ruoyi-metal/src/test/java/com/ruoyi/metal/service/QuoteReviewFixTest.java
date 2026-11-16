package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import com.ruoyi.metal.catalog.CalculationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;

class QuoteReviewFixTest {
    @Test void tinyPositiveDimensionsCannotProduceAZeroVolume() {
        var dimensions=ProductGeometry.normalize("方形盖",Map.of("length","0.01","width","0.01","height","0.01"));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProductGeometry.volume("方形盖",dimensions,null,null));
    }
    @Test void configuredDensityCannotProduceAZeroWeight() {
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProductGeometry.weight(new BigDecimal("0.01"),"0.01"));
    }
    @Test void missingDensityKeepsTheWeightEmpty() {
        assertNull(ProductGeometry.weight(new BigDecimal("0.01"),null));
        assertNull(ProductGeometry.weight(null,"22.65"));
    }
    @Test void validSmallResultsStillUseHalfUpTwoDecimals() {
        assertEquals(new BigDecimal("0.01"),ProductGeometry.volume("方形盖",ProductGeometry.normalize("方形盖",Map.of("length","0.10","width","0.10","height","0.50")),null,null));
        assertEquals(new BigDecimal("0.01"),ProductGeometry.weight(new BigDecimal("0.01"),"0.50"));
    }
    @Test void independentTrialKeepsOneSnapshotAcrossAConcurrentParameterChange() {
        var transactions=new SnapshotTransactions();
        QuoteRuleService target=new QuoteRuleService(null,FactoryCatalogTestFixtures.scope()){
            @Override public String metalName(Long id){return "铱";}
            @Override public Map<String,Object> rule(Long id){
                var old=transactions.parameters();transactions.currentPi="3.20";
                return Map.of("density","22.65","piValue",old.get("piValue"),"piVersion",old.get("version"));
            }
            @Override public Map<String,Object> volumeRuleForMetal(Long id){return transactions.parameters();}
        };
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(transactions);interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());factory.addAdvice(interceptor);
        var service=(QuoteRuleService)factory.getProxy();
        var result=service.calculate(new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),1,1L,"杆"));
        var rule=(Map<?,?>)result.get("rule");var volumeRule=(Map<?,?>)result.get("volumeRule");
        assertEquals(rule.get("piValue"),volumeRule.get("piValue"));
        assertEquals(rule.get("piVersion"),volumeRule.get("version"));
        assertEquals("6.28",result.get("volume"));
        assertTrue(transactions.readOnly);assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,transactions.isolation);
        assertNull(transactions.snapshot.get());assertEquals("3.20",transactions.currentPi);
    }
    @Test void previewPreparationAndQuotationAllRejectZeroVolume() {
        var dimensions=Map.of("length","0.01","width","0.01","height","0.01");
        var rules=configuredRules("22.65");var products=new ProductService(null,FactoryCatalogTestFixtures.scope(),null,rules);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->products.preview(new com.ruoyi.metal.catalog.ProductCalculationRequest(1L,"方形盖",dimensions)));
        var product=new com.ruoyi.metal.catalog.ProductRecord();product.setFactoryId(7L);product.setMetalTypeId(1L);product.setProductType("方形盖");product.setDimensions(dimensions);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->products.prepare(product,null));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->rules.calculate(new CalculationRequest(null,dimensions,1000000,1L,"方形盖","SHORTAGE_CUSTOM",null,null,"0")));
    }
    @Test void previewAndPreparationStillAllowAnUnconfiguredDensity() {
        var dimensions=Map.of("length","1","width","1","height","1");var rules=configuredRules(null);var products=new ProductService(null,FactoryCatalogTestFixtures.scope(),null,rules);
        var preview=products.preview(new com.ruoyi.metal.catalog.ProductCalculationRequest(1L,"方形盖",dimensions));
        assertEquals("1.00",preview.get("volume"));assertNull(preview.get("weight"));
        var product=new com.ruoyi.metal.catalog.ProductRecord();product.setFactoryId(7L);product.setMetalTypeId(1L);product.setProductType("方形盖");product.setDimensions(dimensions);products.prepare(product,null);
        assertEquals(new BigDecimal("1.00"),product.getVolume());assertNull(product.getWeight());
    }
    private QuoteRuleService configuredRules(String density) {
        return new QuoteRuleService(null,FactoryCatalogTestFixtures.scope()){
            @Override public String metalName(Long id){return "铱";}
            @Override public Map<String,Object> volumeRuleForMetal(Long id){return Map.of("piValue","3.14","curvedBottomFactor","1.03","version",0);}
            @Override public Map<String,Object> rule(Long id){return density==null?Map.of():Map.of("density",density,"shortagePrice","100.00");}
        };
    }
    private static final class SnapshotTransactions extends AbstractPlatformTransactionManager {
        String currentPi="3.14";boolean readOnly;int isolation;
        final ThreadLocal<Map<String,Object>> snapshot=new ThreadLocal<>();
        Map<String,Object> parameters(){return snapshot.get()!=null?snapshot.get():Map.of("piValue",currentPi,"curvedBottomFactor","1.03","version",currentPi.equals("3.14")?0:1);}
        @Override protected Object doGetTransaction(){return new Object();}
        @Override protected void doBegin(Object transaction,TransactionDefinition definition){
            readOnly=definition.isReadOnly();isolation=definition.getIsolationLevel();
            if(readOnly&&isolation==TransactionDefinition.ISOLATION_REPEATABLE_READ)snapshot.set(parameters());
        }
        @Override protected void doCommit(DefaultTransactionStatus status){}
        @Override protected void doRollback(DefaultTransactionStatus status){}
        @Override protected void doCleanupAfterCompletion(Object transaction){snapshot.remove();}
    }
}
