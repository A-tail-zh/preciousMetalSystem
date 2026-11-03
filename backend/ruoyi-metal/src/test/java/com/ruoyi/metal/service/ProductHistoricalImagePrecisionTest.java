package com.ruoyi.metal.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

class ProductHistoricalImagePrecisionTest {
    private final ProductDimensionImage images = new ProductDimensionImage();

    @Test void legacyRingCanRenderWhenValidDiametersRoundToTheSameText() throws Exception {
        var dimensions = Map.of("outerDiameter", "1.004", "innerDiameter", "1.003", "height", "1");
        assertNotNull(ImageIO.read(new ByteArrayInputStream(images.render("铱环", dimensions))));
        assertEquals("1.004", dimensions.get("outerDiameter"));
        assertEquals("1.003", dimensions.get("innerDiameter"));
    }

    @Test void legacyHollowRodCanRenderWhenValidDiametersRoundToTheSameText() throws Exception {
        var dimensions = Map.of("outerDiameter", "1.004", "innerDiameter", "1.003", "length", "1");
        assertNotNull(ImageIO.read(new ByteArrayInputStream(images.render("铱杆空心", dimensions))));
    }

    @Test void trueEqualAndInvertedDiametersRemainInvalid() {
        for (String category : new String[]{"铱环", "铱杆空心"})
            for (String outer : new String[]{"1.003", "1.002"})
                assertThrows(ServiceException.class, () -> images.render(category,
                    Map.of("outerDiameter", outer, "innerDiameter", "1.003",
                        "铱环".equals(category) ? "height" : "length", "1")));
    }

    @Test void pictureTextStillUsesTwoDecimalPlaces() {
        assertArrayEquals(
            images.render("铱环", Map.of("outerDiameter", "1.006", "innerDiameter", "1.003", "height", "1")),
            images.render("铱环", Map.of("outerDiameter", "1.007", "innerDiameter", "1.004", "height", "1")));
    }

    @Test void legacyWorkbookKeepsBothCloseDiameterPictures() throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("报价单");
            sheet.createRow(5);sheet.createRow(6);
            QuotationImageWriter.embed(book, sheet, Map.of("category", "铱环", "dimensions",
                Map.of("outerDiameter", "1.004", "innerDiameter", "1.003", "height", "1")), 5, images);
            QuotationImageWriter.embed(book, sheet, Map.of("category", "铱杆空心", "dimensions",
                Map.of("outerDiameter", "1.004", "innerDiameter", "1.003", "length", "1")), 6, images);
            book.write(output);
            try (var saved = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
                assertEquals(2, saved.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            }
        }
    }
}
