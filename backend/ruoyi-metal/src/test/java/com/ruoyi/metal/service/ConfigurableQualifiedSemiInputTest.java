package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigurableQualifiedSemiInputTest {
 private final Map<String,Object> stock=new HashMap<>(Map.of("category","SEMI","measurement_mode","WEIGHT","metal_type_id_snapshot",1L,"smelting_status","PASSED","washing_status","PASSED"));
 private Map<String,Object> target(int marker,String mode){return new HashMap<>(Map.of("processing_policy_version",3,"metal_type_id",1L,"process_role","POST_PROCESS","measurement_mode",mode,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}"));}
 @Test void oldVersionTwoStillRejectsWeightSources(){assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProcessingInputService.require(mock(JdbcTemplate.class),stock,target(2,"PIECE")));}
 @Test void configurablePieceTargetCanTakeQualifiedWeightSources(){assertDoesNotThrow(()->ProcessingInputService.require(mock(JdbcTemplate.class),stock,target(3,"PIECE")));}
 @Test void configurableWeightTargetCanTakeQualifiedWeightSources(){assertDoesNotThrow(()->ProcessingInputService.require(mock(JdbcTemplate.class),stock,target(3,"WEIGHT")));}
 @Test void newModeDoesNotBypassSmeltingQualification(){var unqualified=new HashMap<>(stock);unqualified.put("smelting_status","UNVERIFIED");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProcessingInputService.require(mock(JdbcTemplate.class),unqualified,target(3,"PIECE")));}
}
