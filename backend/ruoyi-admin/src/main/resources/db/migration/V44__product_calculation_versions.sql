-- Keep all historical geometry and numeric values; new creates explicitly select version 2.
ALTER TABLE metal_product
 ADD calculation_version TINYINT NOT NULL DEFAULT 1 COMMENT '1=旧版六类，2=新版八类',
 DROP CHECK chk_product_type,
 ADD CONSTRAINT chk_product_calculation_version CHECK(calculation_version IN (1,2)),
 ADD CONSTRAINT chk_product_type CHECK(
  (calculation_version=1 AND product_type IN ('坩埚弧底','坩埚平底','坩埚梯形','环','杆实心','杆空心')) OR
  (calculation_version=2 AND product_type IN ('平底坩埚','环','杆','梯形','弧底','靶','方形坩埚','方形盖'))
 );
