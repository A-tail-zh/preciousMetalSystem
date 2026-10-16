package com.ruoyi.metal.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;

/** Fills category-specific versioned templates without changing the source image or product. */
@Component
public class ProductDimensionImage {
    public static final String CATEGORY = "铱坩埚弧底";
    private record Field(String key, int x, int y, int width, int height) {}
    public static final String SOLID_ROD = "铱杆实心";
    public static final String HOLLOW_ROD = "铱杆空心";
    public static final String RING = "铱环";
    public static final String FLAT_BOTTOM = "铱坩埚平底";
    public static final String TAPERED = "铱坩埚梯形";
    private record Template(BufferedImage image, java.util.List<Field> fields) {}
    private final Map<String,Template> templates;
    public ProductDimensionImage() {
        templates=Map.of(CATEGORY,load(CATEGORY,"iridium-crucible-curved-bottom"),SOLID_ROD,load(SOLID_ROD,"iridium-rod-solid"),HOLLOW_ROD,load(HOLLOW_ROD,"iridium-rod-hollow"),RING,load(RING,"iridium-ring"),FLAT_BOTTOM,load(FLAT_BOTTOM,"iridium-crucible-flat-bottom"),TAPERED,load(TAPERED,"iridium-crucible-tapered"));
    }
    public static boolean supports(Object category) { return CATEGORY.equals(category)||SOLID_ROD.equals(category)||HOLLOW_ROD.equals(category)||RING.equals(category)||FLAT_BOTTOM.equals(category)||TAPERED.equals(category); }
    private Template load(String category,String name) {
        String base = "/templates/product-images/"+name;
        var fields = new ArrayList<Field>();
        try (var png = getClass().getResourceAsStream(base + ".png");
             var json = getClass().getResourceAsStream(base + ".fields.json")) {
            if (png == null || json == null) throw new IOException("Template resources missing");
            var template = ImageIO.read(png);
            var config = JSON.parseObject(new String(json.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            if (template == null || template.getWidth() != config.getIntValue("width") || template.getHeight() != config.getIntValue("height")) throw new IOException("Template dimensions mismatch");
            for (var item : config.getJSONArray("fields")) {
                var f = (com.alibaba.fastjson2.JSONObject)item;
                var field = new Field(f.getString("key"),f.getIntValue("x"),f.getIntValue("y"),f.getIntValue("width"),f.getIntValue("height"));
                if (field.x < 0 || field.y < 0 || field.width <= 0 || field.height <= 0 || field.x + field.width > template.getWidth() || field.y + field.height > template.getHeight()) throw new IOException("Template field out of bounds");
                fields.add(field);
            }
            if (!fields.stream().map(Field::key).toList().equals(ProductService.DIMENSIONS.get(category))) throw new IOException("Template fields mismatch");
            return new Template(template,java.util.List.copyOf(fields));
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("产品尺寸模板加载失败",e); }
    }
    public byte[] render(String category, Map<?,?> dimensions) {
        var selected=templates.get(category==null?"":category);
        if (selected==null) throw new ServiceException("该产品类别暂无尺寸图片模板");
        var template=selected.image();var fields=selected.fields();
        if (dimensions == null) throw new ServiceException("缺少产品尺寸");
        var values = new ArrayList<String>();
        for (var field : fields) {
            try {
                Object raw = dimensions.get(field.key);
                if (raw == null || raw.toString().length() > 32) throw new IllegalArgumentException();
                var value = new BigDecimal(raw.toString());
                if (value.signum() <= 0 || value.scale() > 6 || value.precision() - value.scale() > 14) throw new IllegalArgumentException();
                values.add(value.stripTrailingZeros().toPlainString());
            } catch (RuntimeException e) { throw new ServiceException("尺寸无效：" + field.key); }
        }
        if ((HOLLOW_ROD.equals(category)||RING.equals(category)) && new BigDecimal(values.get(0)).compareTo(new BigDecimal(values.get(1)))<=0) throw new ServiceException("外径必须大于内径");
        var image = new BufferedImage(template.getWidth(),template.getHeight(),BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.drawImage(template,0,0,null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (int i=0;i<fields.size();i++) {
                var f=fields.get(i);String text=values.get(i);int size=36;
                while (size>8 && g.getFontMetrics(new Font(Font.SANS_SERIF,Font.PLAIN,size)).stringWidth(text)>f.width-12) size--;
                g.setColor(Color.WHITE);g.fillRect(f.x,f.y,f.width,f.height);
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,size));g.setColor(Color.BLACK);
                var metrics=g.getFontMetrics();
                g.drawString(text,f.x+(f.width-metrics.stringWidth(text))/2,f.y+(f.height-metrics.getHeight())/2+metrics.getAscent());
            }
        } finally { g.dispose(); }
        try (var output=new ByteArrayOutputStream()) {
            if (!ImageIO.write(image,"png",output)) throw new IOException("PNG encoder missing");
            return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("产品尺寸图片生成失败",e); }
    }
}
