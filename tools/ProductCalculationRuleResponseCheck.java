import java.util.*;
import com.ruoyi.metal.service.QuoteRuleService;
import com.ruoyi.web.controller.metal.QuoteController;

/** No database or server: compile with the current controller and service into a private directory. */
public class ProductCalculationRuleResponseCheck {
    private static final Set<String> PRODUCT_FIELDS = Set.of("metalTypeId", "metalTypeName", "density", "version");
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Map<String,Object> product(Object density) {
        var value = new LinkedHashMap<String,Object>();
        value.put("metalTypeId", "1");value.put("metalTypeName", "铱");value.put("density", density);value.put("version", 9);
        return value;
    }
    public static void main(String[] args) {
        var parameters = Map.<String,Object>of("piValue", "3.14", "curvedBottomFactor", "1.03", "version", 7);
        var full = new LinkedHashMap<>(product("22.65"));
        full.put("powderPrice", "1.11");full.put("nonPowderPrice", "2.22");full.put("shortagePrice", "3.33");full.put("lossPrice", "4.44");
        var service = new QuoteRuleService(null) {
            @Override public List<Map<String,Object>> rules(Long id) { return List.of(full); }
            @Override public Map<String,Object> rule(Long id) { return full; }
            @Override public List<Map<String,Object>> productRules(Long id) { return List.of(product("22.65"), product(null)); }
            @Override public Map<String,Object> productRule(Long id) { return product("22.65"); }
            @Override public Map<String,Object> volumeRule() { return parameters; }
        };
        var controller = new QuoteController(service, null);
        var listResponse = controller.productRule(null);
        check(listResponse.get("data") instanceof List<?>, "Product list response must remain a list");
        var list = (List<?>) listResponse.get("data");
        for (Object value : list) check(((Map<?,?>) value).keySet().equals(PRODUCT_FIELDS), "Product endpoint exposes pricing fields: " + ((Map<?,?>) value).keySet());
        check(((Map<?,?>) list.get(1)).get("density") == null, "Missing density must remain null");
        check(listResponse.get("volumeRule").equals(parameters), "Public parameters must remain at the top level");
        System.out.println("PASS product list: narrow fields, null density, top-level public parameters");
        var singleResponse = controller.productRule(1L);
        check(singleResponse.get("data") instanceof Map<?,?>, "Selected metal response must remain an object");
        check(((Map<?,?>) singleResponse.get("data")).keySet().equals(PRODUCT_FIELDS), "Selected metal endpoint exposes pricing fields");
        check(singleResponse.get("volumeRule").equals(parameters), "Selected metal must retain public parameters");
        System.out.println("PASS selected metal: narrow object and public parameters");
        check(((Map<?,?>) controller.get(1L).get("data")).get("powderPrice").equals("1.11"), "Quote-authorized endpoint must retain pricing fields");
        System.out.println("PASS quote rule endpoint retains full prices");
    }
}
