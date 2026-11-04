package com.ruoyi.metal.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RawMaterialKindsTest {
 @Test void untreatedAndPowderFollowMaterialsWhileMetalKeepsItsPostingState(){
  assertEquals("POWDER",RawMaterialKinds.initial("粉料"));
  assertEquals("UNPROCESSED",RawMaterialKinds.initial("块料"));
  assertEquals("UNPROCESSED",RawMaterialKinds.initial("旧料"));
  assertEquals("METAL",RawMaterialKinds.kind("METAL","旧料"));
  assertThrows(RuntimeException.class,()->RawMaterialKinds.unprocessed("METAL"));
  assertThrows(RuntimeException.class,()->RawMaterialKinds.unprocessed("POWDER"));
 }
 @Test void settlementNeverCreatesWeight(){
  RawMaterialKinds.mass(new BigDecimal("100"),new BigDecimal("90"),new BigDecimal("10"));
  RawMaterialKinds.mass(new BigDecimal("100"),new BigDecimal("90"),BigDecimal.ZERO);
  assertThrows(RuntimeException.class,()->RawMaterialKinds.mass(new BigDecimal("100"),new BigDecimal("90"),new BigDecimal("10.01")));
  assertThrows(RuntimeException.class,()->RawMaterialKinds.mass(new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO));
 }
}
