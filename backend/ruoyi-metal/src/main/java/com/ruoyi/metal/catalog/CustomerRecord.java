package com.ruoyi.metal.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

public class CustomerRecord extends MasterRecord {
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Size(max = 100) private String companyName;
    @NotBlank @Size(max = 500) private String address;
    @NotBlank @Size(max = 100) private String contactName;
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联络人电话须为11位大陆手机号") private String contactPhone;
    @Size(max = 100) private String department;
    @Positive @JsonFormat(shape = JsonFormat.Shape.STRING) private Long factoryId;
    private String factoryName;

    // Preserve strip() whitespace and include the browser trim() space separators and BOM.
    private static final java.util.regex.Pattern EDGE_WHITESPACE = java.util.regex.Pattern.compile("^[\\p{javaWhitespace}\\p{Zs}\\uFEFF]+|[\\p{javaWhitespace}\\p{Zs}\\uFEFF]+$");
    private static String trim(String value) { return value == null ? null : EDGE_WHITESPACE.matcher(value).replaceAll(""); }
    public String getName() { return name; }
    public void setName(String value) { name = trim(value); }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String value) { companyName = trim(value); }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = trim(value); }
    public String getContactName() { return contactName; }
    public void setContactName(String value) { contactName = trim(value); }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String value) { contactPhone = trim(value); }
    public String getDepartment() { return department; }
    public void setDepartment(String value) { String text = trim(value); department = text == null || text.isEmpty() ? null : text; }
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long value) { factoryId = value; }
    public String getFactoryName() { return factoryName; }
    public void setFactoryName(String value) { factoryName = value; }
}
