package hu.aproleptek.api;

import hu.aproleptek.storage.LocalStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final JdbcTemplate jdbc;
    private final LocalStorageService storage;

    public ApiController(JdbcTemplate jdbc, LocalStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of("username", authentication.getName(), "roles", authentication.getAuthorities().stream()
                .map(Object::toString).toList());
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "worksheets", count("worksheet"),
                "activities", count("activity"),
                "drafts", jdbc.queryForObject("select (select count(*) from worksheet where status='DRAFT') + (select count(*) from activity where status='DRAFT')", Long.class),
                "recentWorksheets", worksheetList("", null, null, 0, 5));
    }

    @GetMapping("/taxonomy")
    public List<Map<String, Object>> taxonomy(@RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "false") boolean includeInactive) {
        String activeClause = includeInactive ? "" : " where active=true";
        if (type == null || type.isBlank()) return jdbc.queryForList("select * from taxonomy_term" + activeClause + " order by term_type, sort_order, name");
        String typeClause = activeClause.isEmpty() ? " where term_type=?" : " and term_type=?";
        return jdbc.queryForList("select * from taxonomy_term" + activeClause + typeClause + " order by sort_order, name", type.toUpperCase(Locale.ROOT));
    }

    @PostMapping("/taxonomy")
    public Map<String, Object> createTerm(@RequestBody Map<String, Object> body) {
        String type = required(body, "termType").toUpperCase(Locale.ROOT);
        if (!Set.of("AGE", "TASK_TYPE", "TOPIC", "SKILL").contains(type)) throw new IllegalArgumentException("Érvénytelen kategóriatípus.");
        String name = required(body, "name");
        String slug = optional(body, "slug");
        if (slug == null) slug = slugify(name);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into taxonomy_term(id, term_type, name, slug, sort_order) values (?,?,?,?,?)", id, type, name, slug, intValue(body.get("sortOrder"), 0));
        return jdbc.queryForMap("select * from taxonomy_term where id=?", id);
    }

    @PutMapping("/taxonomy/{id}")
    public Map<String, Object> updateTerm(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String name = required(body, "name");
        if (name.length() > 120) throw new IllegalArgumentException("A kategória neve legfeljebb 120 karakter lehet.");
        String slug = Optional.ofNullable(optional(body, "slug")).orElseGet(() -> slugify(name)).toLowerCase(Locale.ROOT);
        if (slug.length() > 140 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("A slug csak kisbetűket, számokat és kötőjeleket tartalmazhat.");
        }
        int sortOrder = boundedInt(body.get("sortOrder"), 0, 0, 10000, "megjelenítési sorrend");
        boolean active = body.containsKey("active") ? Boolean.parseBoolean(body.get("active").toString())
                : jdbc.query("select active from taxonomy_term where id=?", rs -> rs.next() && rs.getBoolean(1), id);
        int updated = jdbc.update("update taxonomy_term set name=?, slug=?, sort_order=?, active=? where id=?", name, slug, sortOrder, active, id);
        if (updated == 0) throw new IllegalArgumentException("A kategória nem található.");
        return jdbc.queryForMap("select * from taxonomy_term where id=?", id);
    }

    @PutMapping("/taxonomy/{id}/defaults")
    public Map<String, Object> updateTaskTypeDefaults(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        int updated = jdbc.update("update taxonomy_term set default_description=?, default_instructions=? where id=? and term_type='TASK_TYPE'",
                optional(body, "defaultDescription"), optional(body, "defaultInstructions"), id);
        if (updated == 0) throw new IllegalArgumentException("A feladattípus nem található.");
        return jdbc.queryForMap("select * from taxonomy_term where id=?", id);
    }

    @GetMapping("/worksheets")
    public Map<String, Object> worksheets(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) UUID termId,
                                          @RequestParam(required = false) UUID ageId,
                                          @RequestParam(required = false) UUID taskTypeId,
                                          @RequestParam(required = false) UUID topicId,
                                          @RequestParam(required = false) UUID skillId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0), safeSize = Math.min(Math.max(size, 1), 100);
        List<Map<String, Object>> items = worksheetList(q, status, termId, ageId, taskTypeId, topicId, skillId, safePage * safeSize, safeSize);
        Long total = worksheetCount(q, status, termId, ageId, taskTypeId, topicId, skillId);
        return Map.of("items", items, "page", safePage, "size", safeSize, "total", total);
    }

    @GetMapping("/worksheets/{id}")
    public Map<String, Object> worksheet(@PathVariable UUID id) { return loadWorksheet(id); }

    @PostMapping("/worksheets")
    @Transactional
    public Map<String, Object> createWorksheet(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        saveWorksheet(id, body, true);
        return loadWorksheet(id);
    }

    @PutMapping("/worksheets/{id}")
    @Transactional
    public Map<String, Object> updateWorksheet(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        saveWorksheet(id, body, false);
        return loadWorksheet(id);
    }

    @DeleteMapping("/worksheets/{id}")
    public ResponseEntity<Void> archiveWorksheet(@PathVariable UUID id) {
        jdbc.update("update worksheet set status='ARCHIVED', updated_at=? where id=?", Timestamp.from(Instant.now()), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities")
    public Map<String, Object> activities(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0), safeSize = Math.min(Math.max(size, 1), 100);
        String search = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        List<Map<String, Object>> items = jdbc.queryForList("select * from activity where (lower(title) like ? or lower(coalesce(summary,'')) like ?) and (cast(? as varchar) is null or status=?) order by updated_at desc limit ? offset ?",
                search, search, status, status, safeSize, safePage * safeSize);
        Long total = jdbc.queryForObject("select count(*) from activity where (lower(title) like ? or lower(coalesce(summary,'')) like ?) and (cast(? as varchar) is null or status=?)", Long.class, search, search, status, status);
        items.forEach(item -> item.put("terms", termsFor("activity_id", item.get("id"))));
        return Map.of("items", items, "page", safePage, "size", safeSize, "total", total);
    }

    @GetMapping("/activities/{id}")
    public Map<String, Object> activity(@PathVariable UUID id) { return loadActivity(id); }

    @PostMapping("/activities")
    @Transactional
    public Map<String, Object> createActivity(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID(); saveActivity(id, body, true); return loadActivity(id);
    }

    @PutMapping("/activities/{id}")
    @Transactional
    public Map<String, Object> updateActivity(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        saveActivity(id, body, false); return loadActivity(id);
    }

    @DeleteMapping("/activities/{id}")
    public ResponseEntity<Void> archiveActivity(@PathVariable UUID id) {
        jdbc.update("update activity set status='ARCHIVED', updated_at=? where id=?", Timestamp.from(Instant.now()), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bundles")
    public List<Map<String,Object>> bundles() {
        List<Map<String,Object>> rows = jdbc.queryForList("select b.*, count(bw.worksheet_id) as worksheet_count from bundle_plan b left join bundle_worksheet bw on bw.bundle_id=b.id group by b.id order by b.updated_at desc");
        rows.forEach(row -> row.put("worksheetIds",jdbc.queryForList("select worksheet_id from bundle_worksheet where bundle_id=? order by sort_order",UUID.class,row.get("id"))));
        return rows;
    }

    @GetMapping("/bundles/{id}")
    public Map<String,Object> bundle(@PathVariable UUID id) {
        Map<String,Object> row = jdbc.queryForMap("select * from bundle_plan where id=?",id);
        row.put("worksheets",jdbc.queryForList("select w.id,w.title,bw.sort_order from worksheet w join bundle_worksheet bw on bw.worksheet_id=w.id where bw.bundle_id=? order by bw.sort_order",id));
        return row;
    }

    @PostMapping("/bundles")
    @Transactional
    public Map<String,Object> createBundle(@RequestBody Map<String,Object> body) {
        UUID id=UUID.randomUUID(); saveBundle(id,body,true); return bundle(id);
    }

    @PutMapping("/bundles/{id}")
    @Transactional
    public Map<String,Object> updateBundle(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        saveBundle(id,body,false); return bundle(id);
    }

    @DeleteMapping("/bundles/{id}")
    public ResponseEntity<Void> archiveBundle(@PathVariable UUID id) {
        jdbc.update("update bundle_plan set status='ARCHIVED', updated_at=? where id=?",Timestamp.from(Instant.now()),id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> saleProducts() {
        List<Map<String,Object>> products = jdbc.queryForList("select p.*, coalesce(w.title,b.title) as content_title, " +
                "coalesce(s.units_sold,0) as units_sold, coalesce(s.sale_count,0) as sale_count, " +
                "coalesce(s.revenue,0) as revenue, coalesce(s.material_cost,0) as sold_material_cost " +
                "from sale_product p left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id " +
                "left join (select product_id,sum(quantity) as units_sold,count(distinct sale_id) as sale_count, " +
                "sum(agreed_line_total) as revenue,sum(quantity*material_unit_cost+packaging_cost) as material_cost from sale_line group by product_id) s on s.product_id=p.id " +
                "order by p.active desc, content_title, p.variant_name");
        products.forEach(product -> {
            product.put("materials", productMaterials((UUID) product.get("id")));
            addPrintingCost(product);
        });
        return products;
    }

    @GetMapping("/settings/printing")
    public Map<String,Object> printingSettings() {
        return Map.of("side_cost", jdbc.queryForObject("select setting_value from app_setting where setting_key='print_side_cost'", String.class));
    }

    @PutMapping("/settings/printing")
    public Map<String,Object> updatePrintingSettings(@RequestBody Map<String,Object> body) {
        BigDecimal sideCost=money(body.get("sideCost"),"egy nyomtatott oldal díja",true);
        jdbc.update("update app_setting set setting_value=? where setting_key='print_side_cost'",sideCost.toPlainString());
        return printingSettings();
    }

    @PostMapping("/products")
    @Transactional
    public Map<String, Object> createSaleProduct(@RequestBody Map<String, Object> body) {
        return saveSaleProduct(null, body);
    }

    @PutMapping("/products/{id}")
    @Transactional
    public Map<String, Object> updateSaleProduct(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return saveSaleProduct(id, body);
    }

    private Map<String, Object> saveSaleProduct(UUID id, Map<String, Object> body) {
        String contentType = required(body, "contentType").toUpperCase(Locale.ROOT);
        if (!Set.of("WORKSHEET", "BUNDLE").contains(contentType)) throw new IllegalArgumentException("A termék feladatlaphoz vagy csomagtervhez kapcsolható.");
        UUID contentId;
        try { contentId = UUID.fromString(required(body, "contentId")); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Érvénytelen tartalomazonosító."); }
        String variantName = required(body, "variantName");
        if (variantName.length() > 160) throw new IllegalArgumentException("A termékváltozat neve legfeljebb 160 karakter lehet.");
        BigDecimal listPrice = money(body.get("listPrice"), "listaár", false);
        String deliveryType=Optional.ofNullable(optional(body,"deliveryType")).orElse("PHYSICAL").toUpperCase(Locale.ROOT);
        if(!Set.of("DIGITAL","PHYSICAL").contains(deliveryType)) throw new IllegalArgumentException("A termékváltozat digitális vagy fizikai lehet.");
        int printSides="DIGITAL".equals(deliveryType)?1:boundedInt(body.get("printSides"),1,1,2,"nyomtatási oldalszám");
        List<Map<String,Object>> recipe = listOfMaps(body.get("materials"));
        if("DIGITAL".equals(deliveryType)&&!recipe.isEmpty()) throw new IllegalArgumentException("Digitális termékváltozathoz nem rendelhető fogyóanyag.");
        BigDecimal materialCost = "DIGITAL".equals(deliveryType) ? BigDecimal.ZERO.setScale(2) : money(body.get("materialCost"), "anyagköltség", true);
        UUID worksheetId = "WORKSHEET".equals(contentType) ? contentId : null;
        UUID bundleId = "BUNDLE".equals(contentType) ? contentId : null;
        if ("WORKSHEET".equals(contentType)) jdbc.queryForObject("select id from worksheet where id=?", UUID.class, contentId);
        else jdbc.queryForObject("select id from bundle_plan where id=?", UUID.class, contentId);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("insert into sale_product(id,worksheet_id,bundle_id,variant_name,delivery_type,list_price,material_cost,print_sides) values (?,?,?,?,?,?,?,?)", id, worksheetId, bundleId, variantName, deliveryType, listPrice, materialCost, printSides);
        } else {
            jdbc.update("update sale_product set worksheet_id=?,bundle_id=?,variant_name=?,delivery_type=?,list_price=?,material_cost=?,print_sides=?,updated_at=? where id=?", worksheetId, bundleId, variantName, deliveryType, listPrice, materialCost, printSides, Timestamp.from(Instant.now()), id);
        }
        jdbc.update("delete from product_material where product_id=?",id);
        Set<UUID> linkedMaterials=new HashSet<>();
        for(Map<String,Object> item:recipe){
            UUID materialId;
            try { materialId=UUID.fromString(required(item,"materialId")); }
            catch(IllegalArgumentException ex){ throw new IllegalArgumentException("Érvénytelen anyagazonosító a termék anyaglistájában."); }
            if(!linkedMaterials.add(materialId)) throw new IllegalArgumentException("Egy anyag csak egyszer szerepeljen a termék anyaglistájában.");
            jdbc.queryForObject("select id from material_item where id=? and active=true",UUID.class,materialId);
            jdbc.update("insert into product_material(product_id,material_id,quantity) values (?,?,?)",id,materialId,decimalQuantity(item.get("quantity")));
        }
        if(!recipe.isEmpty()) recalculateProductMaterialCost(id);
        Map<String,Object> product=jdbc.queryForMap("select p.*, coalesce(w.title,b.title) as content_title from sale_product p left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id where p.id=?", id);
        product.put("materials",productMaterials(id));
        addPrintingCost(product);
        return product;
    }

    private void addPrintingCost(Map<String,Object> product) {
        int pages=0;
        if("PHYSICAL".equals(product.get("delivery_type"))) {
            if(product.get("worksheet_id")!=null) pages=Optional.ofNullable(jdbc.queryForObject("select coalesce(sum(page_count),0) from worksheet_page where worksheet_id=?",Long.class,product.get("worksheet_id"))).orElse(0L).intValue();
            else if(product.get("bundle_id")!=null) pages=Optional.ofNullable(jdbc.queryForObject("select coalesce(sum(p.page_count),0) from bundle_worksheet bw join worksheet_page p on p.worksheet_id=bw.worksheet_id where bw.bundle_id=?",Long.class,product.get("bundle_id"))).orElse(0L).intValue();
        }
        int sides=product.get("print_sides") instanceof Number n?n.intValue():1;
        BigDecimal sideCost=new BigDecimal(jdbc.queryForObject("select setting_value from app_setting where setting_key='print_side_cost'",String.class));
        int printedSheets=(pages+sides-1)/sides;
        int printedSurfaces=printedSheets*sides;
        BigDecimal printingCost=sideCost.multiply(BigDecimal.valueOf(printedSurfaces)).setScale(2,RoundingMode.HALF_UP);
        product.put("print_pages",pages);
        product.put("print_sheets",printedSheets);
        product.put("printed_surfaces",printedSurfaces);
        product.put("printing_cost",printingCost);
        Object rawMaterialCost=product.get("material_cost");
        BigDecimal material=rawMaterialCost instanceof BigDecimal b?b:new BigDecimal(rawMaterialCost.toString());
        product.put("manufacturing_cost",material.add(printingCost).setScale(2,RoundingMode.HALF_UP));
    }

    private BigDecimal printingCost(Map<String,Object> product) {
        addPrintingCost(product);
        return (BigDecimal)product.get("printing_cost");
    }

    private List<Map<String,Object>> productMaterials(UUID productId){
        return jdbc.queryForList("select pm.material_id,m.name,m.unit_name,pm.quantity,coalesce(p.unit_cost,0) as unit_cost, " +
                "pm.quantity*coalesce(p.unit_cost,0) as total_cost from product_material pm join material_item m on m.id=pm.material_id " +
                "left join (select material_id,sum(total_cost)/nullif(sum(quantity),0) as unit_cost from material_purchase group by material_id) p on p.material_id=m.id " +
                "where pm.product_id=? order by m.name",productId);
    }

    private void recalculateProductMaterialCost(UUID productId){
        BigDecimal cost=jdbc.queryForObject("select coalesce(sum(pm.quantity*coalesce(p.unit_cost,0)),0) from product_material pm " +
                "left join (select material_id,sum(total_cost)/nullif(sum(quantity),0) as unit_cost from material_purchase group by material_id) p on p.material_id=pm.material_id " +
                "where pm.product_id=?",BigDecimal.class,productId).setScale(2,RoundingMode.HALF_UP);
        jdbc.update("update sale_product set material_cost=?,updated_at=? where id=?",cost,Timestamp.from(Instant.now()),productId);
    }

    private void recalculateProductsUsingMaterial(UUID materialId){
        jdbc.queryForList("select product_id from product_material where material_id=?",UUID.class,materialId)
                .forEach(this::recalculateProductMaterialCost);
    }

    @GetMapping("/sales")
    public List<Map<String, Object>> sales() {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from sale_record order by sale_date desc,created_at desc limit 200");
        for (Map<String, Object> sale : rows) sale.put("items", jdbc.queryForList("select product_id,product_title,variant_name,quantity,list_unit_price,material_unit_cost,printing_cost,packaging_cost,agreed_line_total from sale_line where sale_id=? order by product_title", sale.get("id")));
        return rows;
    }

    @GetMapping("/sales/summary")
    public Map<String, Object> salesSummary(@RequestParam(required = false) java.time.LocalDate from,
                                            @RequestParam(required = false) java.time.LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("A kezdődátum nem lehet későbbi a záródátumnál.");
        java.sql.Date fromDate = java.sql.Date.valueOf(from == null ? java.time.LocalDate.of(1, 1, 1) : from);
        java.sql.Date toDate = java.sql.Date.valueOf(to == null ? java.time.LocalDate.of(9999, 12, 31) : to);
        return jdbc.queryForMap("select count(*) as sale_count, coalesce(sum(s.agreed_total),0) as revenue, " +
                "coalesce((select sum(l.quantity*l.material_unit_cost+l.packaging_cost) from sale_line l join sale_record r on r.id=l.sale_id where r.sale_date between ? and ?),0) as material_cost " +
                ", coalesce((select sum(l.quantity*l.printing_cost) from sale_line l join sale_record r on r.id=l.sale_id where r.sale_date between ? and ?),0) as printing_cost " +
                ", coalesce((select sum(l.packaging_cost) from sale_line l join sale_record r on r.id=l.sale_id where r.sale_date between ? and ?),0) as packaging_cost " +
                "from sale_record s where s.sale_date between ? and ?", fromDate, toDate, fromDate, toDate, fromDate, toDate, fromDate, toDate);
    }

    @PostMapping("/sales")
    @Transactional
    public Map<String, Object> createSale(@RequestBody Map<String, Object> body) {
        return saveSale(null,body);
    }

    @PutMapping("/sales/{id}")
    @Transactional
    public Map<String,Object> updateSale(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        jdbc.queryForObject("select id from sale_record where id=?",UUID.class,id);
        return saveSale(id,body);
    }

    private Map<String,Object> saveSale(UUID existingId,Map<String,Object> body) {
        List<Map<String, Object>> items = listOfMaps(body.get("items"));
        if (items.isEmpty()) throw new IllegalArgumentException("Adj hozzá legalább egy terméket az eladáshoz.");
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            UUID productId;
            try { productId = UUID.fromString(required(item, "productId")); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Érvénytelen termékazonosító."); }
            int quantity = boundedInt(item.get("quantity"), 1, 1, 1000, "darabszám");
            quantities.merge(productId, quantity, Integer::sum);
        }
        BigDecimal agreedTotal = money(body.get("agreedTotal"), "megbeszélt végösszeg", true);
        String channel = Optional.ofNullable(optional(body, "channel")).orElse("Vinted");
        if (channel.length() > 80) throw new IllegalArgumentException("A csatorna neve legfeljebb 80 karakter lehet.");
        java.sql.Date saleDate=dateValue(body.get("saleDate"),"eladási dátum");
        UUID packagingMaterialId=null;
        String packagingName=null,packagingUnit=null;
        BigDecimal packagingQuantity=BigDecimal.ZERO.setScale(3),packagingUnitCost=BigDecimal.ZERO.setScale(2),packagingTotalCost=BigDecimal.ZERO.setScale(2);
        Map<String,Object> packaging=body.get("packaging") instanceof Map<?,?> raw?mapOf(raw):Map.of();
        String packagingIdValue=optional(packaging,"materialId");
        if(packagingIdValue!=null&&!packagingIdValue.isBlank()) {
            try { packagingMaterialId=UUID.fromString(packagingIdValue); }
            catch(IllegalArgumentException ex) { throw new IllegalArgumentException("Érvénytelen csomagolóanyag-azonosító."); }
            Map<String,Object> material=jdbc.queryForMap("select id,name,unit_name from material_item where id=? and active=true and is_packaging=true",packagingMaterialId);
            packagingName=(String)material.get("name"); packagingUnit=(String)material.get("unit_name");
            packagingQuantity=decimalQuantity(packaging.get("quantity"));
            BigDecimal average=jdbc.queryForObject("select coalesce(sum(total_cost)/nullif(sum(quantity),0),0) from material_purchase where material_id=?",BigDecimal.class,packagingMaterialId);
            packagingUnitCost=average.setScale(2,RoundingMode.HALF_UP);
            packagingTotalCost=packagingUnitCost.multiply(packagingQuantity).setScale(2,RoundingMode.HALF_UP);
        }

        record Line(UUID productId, String title, String variant, int quantity, BigDecimal unitPrice, BigDecimal unitCost, BigDecimal printingCost) {
            BigDecimal listTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal listTotal = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Map<String, Object> product = jdbc.queryForMap("select p.*,coalesce(w.title,b.title) as content_title from sale_product p left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id where p.id=? and p.active=true", entry.getKey());
            BigDecimal printCost=printingCost(product);
            BigDecimal totalCost=((BigDecimal)product.get("material_cost")).add(printCost);
            Line line = new Line(entry.getKey(), (String) product.get("content_title"), (String) product.get("variant_name"), entry.getValue(), (BigDecimal) product.get("list_price"), totalCost, printCost);
            lines.add(line); listTotal = listTotal.add(line.listTotal());
        }
        UUID saleId=existingId==null?UUID.randomUUID():existingId;
        if(existingId==null) jdbc.update("insert into sale_record(id,sale_date,channel,list_total,agreed_total,note,packaging_material_id,packaging_name,packaging_unit,packaging_quantity,packaging_unit_cost,packaging_total_cost) values (?,?,?,?,?,?,?,?,?,?,?,?)",saleId,saleDate,channel,listTotal,agreedTotal,optional(body,"note"),packagingMaterialId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost);
        else {
            jdbc.update("update sale_record set sale_date=?,channel=?,list_total=?,agreed_total=?,note=?,packaging_material_id=?,packaging_name=?,packaging_unit=?,packaging_quantity=?,packaging_unit_cost=?,packaging_total_cost=? where id=?",saleDate,channel,listTotal,agreedTotal,optional(body,"note"),packagingMaterialId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost,saleId);
            jdbc.update("delete from sale_line where sale_id=?",saleId);
        }
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal packagingAllocated=BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            BigDecimal lineTotal = i == lines.size() - 1 ? agreedTotal.subtract(allocated) : agreedTotal.multiply(line.listTotal()).divide(listTotal, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(lineTotal);
            BigDecimal allocationBase=agreedTotal.signum()>0?agreedTotal:listTotal;
            BigDecimal lineBase=agreedTotal.signum()>0?lineTotal:line.listTotal();
            BigDecimal packagingLineCost=i==lines.size()-1?packagingTotalCost.subtract(packagingAllocated):packagingTotalCost.multiply(lineBase).divide(allocationBase,2,RoundingMode.HALF_UP);
            packagingAllocated=packagingAllocated.add(packagingLineCost);
            jdbc.update("insert into sale_line(sale_id,product_id,product_title,variant_name,quantity,list_unit_price,material_unit_cost,printing_cost,packaging_cost,agreed_line_total) values (?,?,?,?,?,?,?,?,?,?)",
                    saleId, line.productId(), line.title(), line.variant(), line.quantity(), line.unitPrice(), line.unitCost(),line.printingCost(),packagingLineCost,lineTotal);
        }
        return jdbc.queryForMap("select * from sale_record where id=?", saleId);
    }

    @GetMapping("/materials")
    public List<Map<String, Object>> materials() {
        return jdbc.queryForList("select m.*, coalesce(p.purchased_quantity,0) as purchased_quantity, " +
                "coalesce(p.total_spent,0) as total_spent, p.unit_cost " +
                "from material_item m left join (select material_id,sum(quantity) as purchased_quantity, " +
                "sum(total_cost) as total_spent, sum(total_cost)/nullif(sum(quantity),0) as unit_cost " +
                "from material_purchase group by material_id) p on p.material_id=m.id " +
                "order by m.active desc,m.name");
    }

    @PostMapping("/materials")
    public Map<String, Object> createMaterial(@RequestBody Map<String, Object> body) {
        String name = required(body,"name");
        String unitName = required(body,"unitName");
        if (name.length()>160 || unitName.length()>30) throw new IllegalArgumentException("Az anyagnév legfeljebb 160, a mértékegység legfeljebb 30 karakter lehet.");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into material_item(id,name,unit_name,is_packaging) values (?,?,?,?)",id,name,unitName,Boolean.parseBoolean(Objects.toString(body.get("isPackaging"),"false")));
        return jdbc.queryForMap("select * from material_item where id=?",id);
    }

    @PutMapping("/materials/{id}/packaging")
    public Map<String,Object> updateMaterialPackaging(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        boolean isPackaging=Boolean.parseBoolean(Objects.toString(body.get("isPackaging"),"false"));
        jdbc.update("update material_item set is_packaging=? where id=?",isPackaging,id);
        return jdbc.queryForMap("select * from material_item where id=?",id);
    }

    @GetMapping("/materials/purchases")
    public List<Map<String,Object>> materialPurchases() {
        return jdbc.queryForList("select p.*,m.name as material_name,m.unit_name from material_purchase p join material_item m on m.id=p.material_id order by p.purchase_date desc,p.created_at desc limit 200");
    }

    @PostMapping("/materials/{id}/purchases")
    @Transactional
    public Map<String,Object> createMaterialPurchase(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        jdbc.queryForObject("select id from material_item where id=? and active=true",UUID.class,id);
        BigDecimal quantity=decimalQuantity(body.get("quantity"));
        BigDecimal totalCost=money(body.get("totalCost"),"beszerzési ár",false);
        BigDecimal unitCost=totalCost.divide(quantity,4,RoundingMode.HALF_UP);
        UUID purchaseId=UUID.randomUUID();
        java.sql.Date purchaseDate=dateValue(body.get("purchaseDate"),"beszerzési dátum");
        jdbc.update("insert into material_purchase(id,material_id,purchase_date,quantity,total_cost,unit_cost,note) values (?,?,?,?,?,?,?)",purchaseId,id,purchaseDate,quantity,totalCost,unitCost,optional(body,"note"));
        recalculateProductsUsingMaterial(id);
        return jdbc.queryForMap("select p.*,m.name as material_name,m.unit_name from material_purchase p join material_item m on m.id=p.material_id where p.id=?",purchaseId);
    }

    @GetMapping("/expenses")
    public List<Map<String,Object>> expenses() {
        return jdbc.queryForList("select * from expense_record order by expense_date desc,created_at desc limit 200");
    }

    @PostMapping("/expenses")
    public Map<String,Object> createExpense(@RequestBody Map<String,Object> body) {
        String category=required(body,"category").toUpperCase(Locale.ROOT);
        if (!Set.of("EQUIPMENT","OTHER").contains(category)) throw new IllegalArgumentException("Az egyszeri kiadás típusa eszköz vagy egyéb lehet.");
        String title=required(body,"title");
        if(title.length()>200) throw new IllegalArgumentException("A kiadás megnevezése legfeljebb 200 karakter lehet.");
        BigDecimal amount=money(body.get("amount"),"kiadás összege",false);
        UUID id=UUID.randomUUID();
        jdbc.update("insert into expense_record(id,category,title,expense_date,amount,note) values (?,?,?,?,?,?)",id,category,title,dateValue(body.get("expenseDate"),"kiadás dátuma"),amount,optional(body,"note"));
        return jdbc.queryForMap("select * from expense_record where id=?",id);
    }

    @GetMapping("/expenses/summary")
    public Map<String,Object> expenseSummary() {
        return jdbc.queryForMap("select (select coalesce(sum(amount),0) from expense_record where category='EQUIPMENT') as equipment_total, " +
                "(select coalesce(sum(amount),0) from expense_record where category='OTHER') as other_total, " +
                "(select coalesce(sum(total_cost),0) from material_purchase) as material_total, " +
                "((select coalesce(sum(amount),0) from expense_record)+(select coalesce(sum(total_cost),0) from material_purchase)) as all_total");
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        List<String> storedKeys = new ArrayList<>();
        try {
            return storeMedia(file, storedKeys);
        } catch (IOException | RuntimeException ex) {
            cleanupStoredFiles(storedKeys, ex);
            throw ex;
        }
    }

    @PostMapping(value = "/media/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> uploadBatch(@RequestPart("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Legalább egy fájlt válassz ki.");
        List<String> storedKeys = new ArrayList<>();
        List<Map<String, Object>> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) uploaded.add(storeMedia(file, storedKeys));
            return uploaded;
        } catch (IOException | RuntimeException ex) {
            cleanupStoredFiles(storedKeys, ex);
            throw ex;
        }
    }

    private Map<String, Object> storeMedia(MultipartFile file, List<String> storedKeys) throws IOException {
        LocalStorageService.StoredFile stored = storage.save(file);
        storedKeys.add(stored.key());
        UUID id = UUID.randomUUID();
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("feltoltes");
        jdbc.update("insert into media_asset(id, original_name, storage_key, media_type, file_size) values (?,?,?,?,?)",
                id, originalName, stored.key(), stored.mediaType(), stored.size());
        return Map.of("id", id.toString(), "originalName", originalName, "mediaType", stored.mediaType(), "fileSize", stored.size(), "url", "/api/v1/media/" + id + "/content");
    }

    private void cleanupStoredFiles(List<String> storedKeys, Exception cause) {
        for (String key : storedKeys) {
            try { storage.delete(key); }
            catch (IOException cleanupError) { cause.addSuppressed(cleanupError); }
        }
    }

    @GetMapping("/media/{id}/content")
    public ResponseEntity<Resource> media(@PathVariable UUID id) throws IOException {
        Map<String, Object> row = jdbc.queryForMap("select storage_key, media_type, original_name from media_asset where id=?", id);
        Resource resource = storage.load((String) row.get("storage_key"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String) row.get("media_type")))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFilename((String) row.get("original_name")))
                .body(resource);
    }

    private void saveWorksheet(UUID id, Map<String, Object> b, boolean insert) {
        String title = required(b, "title");
        String slug = Optional.ofNullable(optional(b, "slug")).orElseGet(() -> slugify(title));
        String status = status(b.get("status"));
        String paperSize = choice(b.get("paperSize"), "A4", "papírméret", Set.of("A4", "A3", "LETTER"));
        String orientation = choice(b.get("orientation"), "PORTRAIT", "tájolás", Set.of("PORTRAIT", "LANDSCAPE"));
        String colorMode = choice(b.get("colorMode"), "COLOR", "színmód", Set.of("COLOR", "GRAYSCALE"));
        String usageType = choice(b.get("usageType"), "DIGITAL", "felhasználás", Set.of("DIGITAL", "PHYSICAL", "BOTH"));
        Object previewId = uuidOrNull(b.get("previewAssetId"));
        Object[] values = {title, slug, optional(b, "summary"), optional(b, "description"), optional(b, "instructions"), status,
                paperSize, orientation, colorMode, usageType, optional(b, "safetyNotes"), previewId, Timestamp.from(Instant.now()),
                "PUBLISHED".equals(status) ? Timestamp.from(Instant.now()) : null};
        if (insert) jdbc.update("insert into worksheet(id,title,slug,summary,description,instructions,status,paper_size,orientation,color_mode,usage_type,safety_notes,preview_asset_id,updated_at,published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", prepend(id, values));
        else jdbc.update("update worksheet set title=?,slug=?,summary=?,description=?,instructions=?,status=?,paper_size=?,orientation=?,color_mode=?,usage_type=?,safety_notes=?,preview_asset_id=?,updated_at=?,published_at=coalesce(?,published_at) where id=?",
                append(values, id));
        List<Map<String, Object>> pages = listOfMaps(b.get("pages"));
        if (!insert) jdbc.update("delete from worksheet_page where worksheet_id=?", id);
        boolean hasMain = pages.stream().anyMatch(p -> "MAIN".equalsIgnoreCase(valueOr(p.get("pageType"), "CUTOUT")));
        if (!hasMain) {
            Map<String, Object> main = new HashMap<>(); main.put("pageType", "MAIN"); main.put("title", "Alap feladatlap"); main.put("sortOrder", 0); main.put("pageCount", 1);
            pages.add(0, main);
        }
        if (pages.stream().filter(p -> "MAIN".equalsIgnoreCase(valueOr(p.get("pageType"), "CUTOUT"))).count() != 1) {
            throw new IllegalArgumentException("Egy feladatlaphoz pontosan egy alap feladatlap tartozhat.");
        }
        int order = 0;
        for (Map<String, Object> p : pages) {
            String pageType = valueOr(p.get("pageType"), "CUTOUT").toUpperCase(Locale.ROOT);
            if (!Set.of("MAIN", "CUTOUT").contains(pageType)) throw new IllegalArgumentException("Érvénytelen oldaltípus.");
            int sortOrder = boundedInt(p.get("sortOrder"), order, 0, 10_000, "oldalsorrend");
            int pageCount = boundedInt(p.get("pageCount"), 1, 1, 100, "oldalszám");
            String pagePaperSize = choice(p.get("paperSize"), paperSize, "papírméret", Set.of("A4", "A3", "LETTER"));
            String pageOrientation = choice(p.get("orientation"), orientation, "tájolás", Set.of("PORTRAIT", "LANDSCAPE"));
            String pageColorMode = choice(p.get("colorMode"), colorMode, "színmód", Set.of("COLOR", "GRAYSCALE"));
            jdbc.update("insert into worksheet_page(worksheet_id,page_type,title,sort_order,page_count,paper_size,orientation,color_mode,notes,preview_asset_id,print_asset_id) values (?,?,?,?,?,?,?,?,?,?,?)",
                    id, pageType, optional(p,"title"), sortOrder, pageCount, pagePaperSize, pageOrientation, pageColorMode, optional(p,"notes"), uuidOrNull(p.get("previewAssetId")), uuidOrNull(p.get("printAssetId")));
            order++;
        }
        jdbc.update("update worksheet set preview_asset_id=(select preview_asset_id from worksheet_page where worksheet_id=? and page_type='MAIN' order by sort_order limit 1) where id=?", id, id);
        jdbc.update("delete from worksheet_term where worksheet_id=? and term_id in (select id from taxonomy_term where active=true)", id);
        saveTerms("worksheet_id", id, b.get("termIds"));
    }

    private Map<String, Object> loadWorksheet(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("select * from worksheet where id=?", id);
        row.put("pages", jdbc.queryForList("select p.*, mp.original_name as preview_name, mp.media_type as preview_media_type, fp.original_name as print_name, fp.media_type as print_media_type from worksheet_page p left join media_asset mp on mp.id=p.preview_asset_id left join media_asset fp on fp.id=p.print_asset_id where p.worksheet_id=? order by p.sort_order", id));
        row.put("terms", termsFor("worksheet_id", id));
        List<Map<String,Object>> saleProducts=jdbc.queryForList("select p.id,p.worksheet_id,p.delivery_type,p.print_sides,p.variant_name,p.list_price,p.material_cost,coalesce(s.units_sold,0) as units_sold,coalesce(s.revenue,0) as revenue,coalesce(s.sold_material_cost,0) as sold_material_cost " +
                "from sale_product p left join (select product_id,sum(quantity) as units_sold,sum(agreed_line_total) as revenue,sum(quantity*material_unit_cost+packaging_cost) as sold_material_cost from sale_line group by product_id) s on s.product_id=p.id " +
                "where p.worksheet_id=? and p.active=true order by p.variant_name",id);
        saleProducts.forEach(this::addPrintingCost);
        row.put("saleProducts",saleProducts);
        int total = ((List<Map<String,Object>>) row.get("pages")).stream().mapToInt(p -> intValue(p.get("page_count"),1)).sum();
        row.put("totalPages", total);
        return row;
    }

    private List<Map<String, Object>> worksheetList(String q, String status, UUID termId, int offset, int limit) {
        return worksheetList(q, status, termId, null, null, null, null, offset, limit);
    }

    private List<Map<String, Object>> worksheetList(String q, String status, UUID termId, UUID ageId, UUID taskTypeId, UUID topicId, UUID skillId, int offset, int limit) {
        String search = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String sql = "select distinct w.*, (select coalesce(sum(p.page_count),0) from worksheet_page p where p.worksheet_id=w.id) as total_pages from worksheet w where (lower(w.title) like ? or lower(coalesce(w.summary,'')) like ?) and (cast(? as varchar) is null or w.status=?)"
                + worksheetTermFilterSql(termId, ageId, taskTypeId, topicId, skillId)
                + " order by w.updated_at desc limit ? offset ?";
        List<Object> params = worksheetFilterParams(search, status, termId, ageId, taskTypeId, topicId, skillId);
        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        rows.forEach(row -> row.put("terms", termsFor("worksheet_id", row.get("id"))));
        return rows;
    }

    private Long worksheetCount(String q, String status, UUID termId) {
        return worksheetCount(q, status, termId, null, null, null, null);
    }

    private Long worksheetCount(String q, String status, UUID termId, UUID ageId, UUID taskTypeId, UUID topicId, UUID skillId) {
        String search = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String sql = "select count(*) from worksheet w where (lower(w.title) like ? or lower(coalesce(w.summary,'')) like ?) and (cast(? as varchar) is null or w.status=?)"
                + worksheetTermFilterSql(termId, ageId, taskTypeId, topicId, skillId);
        return jdbc.queryForObject(sql, Long.class, worksheetFilterParams(search, status, termId, ageId, taskTypeId, topicId, skillId).toArray());
    }

    private String worksheetTermFilterSql(UUID termId, UUID ageId, UUID taskTypeId, UUID topicId, UUID skillId) {
        return " and (cast(? as uuid) is null or exists (select 1 from worksheet_term wt where wt.worksheet_id=w.id and wt.term_id=?))"
                + typedTermFilterSql("AGE") + typedTermFilterSql("TASK_TYPE") + typedTermFilterSql("TOPIC") + typedTermFilterSql("SKILL");
    }

    private String typedTermFilterSql(String type) {
        return " and (cast(? as uuid) is null or exists (select 1 from worksheet_term wt join taxonomy_term tt on tt.id=wt.term_id where wt.worksheet_id=w.id and wt.term_id=? and tt.term_type='" + type + "'))";
    }

    private List<Object> worksheetFilterParams(String search, String status, UUID termId, UUID ageId, UUID taskTypeId, UUID topicId, UUID skillId) {
        List<Object> params = new ArrayList<>();
        params.add(search); params.add(search); params.add(status); params.add(status);
        for (UUID id : Arrays.asList(termId, ageId, taskTypeId, topicId, skillId)) { params.add(id); params.add(id); }
        return params;
    }

    private void saveActivity(UUID id, Map<String,Object> b, boolean insert) {
        String title = required(b,"title"), slug = Optional.ofNullable(optional(b,"slug")).orElseGet(() -> slugify(title));
        Object[] values = {title, slug, optional(b,"summary"), optional(b,"description"), nullableInt(b.get("durationMinutes")), valueOr(b.get("locationType"),"ANY"), optional(b,"materials"), optional(b,"preparation"), optional(b,"instructions"), optional(b,"easyVariant"), optional(b,"challengeVariant"), optional(b,"safetyNotes"), status(b.get("status")), Timestamp.from(Instant.now())};
        if (insert) jdbc.update("insert into activity(id,title,slug,summary,description,duration_minutes,location_type,materials,preparation,instructions,easy_variant,challenge_variant,safety_notes,status,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", prepend(id,values));
        else jdbc.update("update activity set title=?,slug=?,summary=?,description=?,duration_minutes=?,location_type=?,materials=?,preparation=?,instructions=?,easy_variant=?,challenge_variant=?,safety_notes=?,status=?,updated_at=? where id=?", append(values,id));
        jdbc.update("delete from activity_term where activity_id=? and term_id in (select id from taxonomy_term where active=true)", id); saveTerms("activity_id",id,b.get("termIds"));
    }

    private void saveBundle(UUID id,Map<String,Object> b,boolean insert) {
        String title=required(b,"title");
        String slug=Optional.ofNullable(optional(b,"slug")).orElseGet(()->slugify(title));
        String type=valueOr(b.get("bundleType"),"DIGITAL");
        if(!Set.of("DIGITAL","PHYSICAL","BOTH").contains(type)) throw new IllegalArgumentException("Érvénytelen csomagtípus.");
        Object[] values={title,slug,optional(b,"description"),type,status(b.get("status")),Timestamp.from(Instant.now())};
        if(insert) jdbc.update("insert into bundle_plan(id,title,slug,description,bundle_type,status,updated_at) values (?,?,?,?,?,?,?)",prepend(id,values));
        else jdbc.update("update bundle_plan set title=?,slug=?,description=?,bundle_type=?,status=?,updated_at=? where id=?",append(values,id));
        jdbc.update("delete from bundle_worksheet where bundle_id=?",id);
        if(b.get("worksheetIds") instanceof List<?> ids) {
            int order=0;
            for(Object worksheetId:ids) jdbc.update("insert into bundle_worksheet(bundle_id,worksheet_id,sort_order) values (?,?,?)",id,UUID.fromString(worksheetId.toString()),order++);
        }
    }

    private Map<String,Object> loadActivity(UUID id) {
        Map<String,Object> row = jdbc.queryForMap("select * from activity where id=?",id);
        row.put("terms",termsFor("activity_id",id)); return row;
    }

    private List<Map<String,Object>> termsFor(String column, Object id) {
        if (!Set.of("worksheet_id","activity_id").contains(column)) throw new IllegalArgumentException("Invalid taxonomy relation");
        String join = column.equals("worksheet_id") ? "worksheet_term" : "activity_term";
        return jdbc.queryForList("select t.* from taxonomy_term t join " + join + " ct on ct.term_id=t.id where ct."+column+"=? order by t.term_type,t.sort_order,t.name",id);
    }

    private void saveTerms(String column, UUID id, Object raw) {
        if (!Set.of("worksheet_id","activity_id").contains(column)) throw new IllegalArgumentException("Invalid taxonomy relation");
        if (!(raw instanceof List<?> ids)) return;
        String relation = column.equals("worksheet_id") ? "worksheet_term" : "activity_term";
        for (Object term : ids) {
            UUID termId = UUID.fromString(term.toString());
            String type = jdbc.queryForObject("select term_type from taxonomy_term where id=?", String.class, termId);
            if ("activity_id".equals(column) && "TASK_TYPE".equals(type)) throw new IllegalArgumentException("A feladattípus csak feladatlaphoz rendelhető.");
            boolean alreadyLinked = jdbc.queryForObject("select exists(select 1 from " + relation + " where " + column + "=? and term_id=?)", Boolean.class, id, termId);
            boolean active = jdbc.queryForObject("select active from taxonomy_term where id=?", Boolean.class, termId);
            if (!active && !alreadyLinked) throw new IllegalArgumentException("Inaktív kategória új tartalomhoz nem rendelhető.");
            if (!alreadyLinked) jdbc.update("insert into " + relation + "("+column+",term_id) values (?,?)",id,termId);
        }
    }

    private long count(String table) {
        if (!Set.of("worksheet","activity").contains(table)) throw new IllegalArgumentException("Invalid table");
        return jdbc.queryForObject("select count(*) from "+table,Long.class);
    }
    private static String required(Map<String,Object> b,String key) { String v=optional(b,key); if(v==null) throw new IllegalArgumentException("A(z) "+key+" mező kötelező."); return v; }
    private static String optional(Map<String,Object> b,String key) { Object value=b.get(key); if(value==null) return null; String s=value.toString().trim(); return s.isEmpty()?null:s; }
    private static String valueOr(Object value,String fallback) { return value==null || value.toString().isBlank()?fallback:value.toString().trim().toUpperCase(Locale.ROOT); }
    private static String choice(Object value, String fallback, String label, Set<String> allowed) {
        String candidate = valueOr(value, fallback);
        if (!allowed.contains(candidate)) throw new IllegalArgumentException("Érvénytelen " + label + ".");
        return candidate;
    }
    private static int boundedInt(Object value, int fallback, int min, int max, String label) {
        if (value == null || value.toString().isBlank()) return fallback;
        int candidate;
        try { candidate = Integer.parseInt(value.toString().trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("A(z) " + label + " egész szám legyen."); }
        if (candidate < min || candidate > max) throw new IllegalArgumentException("A(z) " + label + " értéke " + min + " és " + max + " közé essen.");
        return candidate;
    }
    private static BigDecimal money(Object value, String label, boolean allowZero) {
        if (value == null || value.toString().isBlank()) {
            if (allowZero) return BigDecimal.ZERO.setScale(2);
            throw new IllegalArgumentException("A(z) " + label + " megadása kötelező.");
        }
        BigDecimal amount;
        try { amount = new BigDecimal(value.toString().trim()).setScale(2, RoundingMode.UNNECESSARY); }
        catch (NumberFormatException | ArithmeticException ex) { throw new IllegalArgumentException("A(z) " + label + " legfeljebb két tizedesjegyű összeg legyen."); }
        if (amount.signum() < 0 || (!allowZero && amount.signum() == 0)) throw new IllegalArgumentException("A(z) " + label + " csak pozitív összeg lehet.");
        return amount;
    }
    private static BigDecimal decimalQuantity(Object value) {
        BigDecimal quantity;
        try { quantity=new BigDecimal(Objects.toString(value,"" ).trim()).setScale(3,RoundingMode.UNNECESSARY); }
        catch (NumberFormatException | ArithmeticException ex) { throw new IllegalArgumentException("A mennyiség legfeljebb három tizedesjegyű pozitív szám legyen."); }
        if(quantity.signum()<=0) throw new IllegalArgumentException("A mennyiség pozitív szám legyen.");
        return quantity;
    }
    private static java.sql.Date dateValue(Object value,String label) {
        if(value==null || value.toString().isBlank()) return java.sql.Date.valueOf(java.time.LocalDate.now());
        try { return java.sql.Date.valueOf(value.toString()); }
        catch(IllegalArgumentException ex) { throw new IllegalArgumentException("Érvénytelen " + label + "."); }
    }
    private static String status(Object value) { String s=valueOr(value,"DRAFT"); if(!Set.of("DRAFT","REVIEW","PUBLISHED","ARCHIVED").contains(s)) throw new IllegalArgumentException("Érvénytelen státusz."); return s; }
    private static int intValue(Object value,int fallback) { if(value==null) return fallback; try{return Integer.parseInt(value.toString());}catch(NumberFormatException ex){return fallback;} }
    private static Object nullableInt(Object value) { if(value==null || value.toString().isBlank()) return null; return intValue(value,0); }
    private static Object uuidOrNull(Object value) { return value==null || value.toString().isBlank()?null:UUID.fromString(value.toString()); }
    private static String slugify(String text) { String s=java.text.Normalizer.normalize(text.toLowerCase(Locale.forLanguageTag("hu")), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}",""); return s.replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$",""); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> listOfMaps(Object value) { if(!(value instanceof List<?> l)) return new ArrayList<>(); return l.stream().filter(Map.class::isInstance).map(v->new HashMap<>((Map<String,Object>)v)).collect(Collectors.toCollection(ArrayList::new)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> mapOf(Object value) { return value instanceof Map<?,?> m?new HashMap<>((Map<String,Object>)m):Map.of(); }
    private static Object[] prepend(Object first,Object[] values) { Object[] result=new Object[values.length+1]; result[0]=first; System.arraycopy(values,0,result,1,values.length); return result; }
    private static Object[] append(Object[] values,Object last) { Object[] result=Arrays.copyOf(values,values.length+1); result[values.length]=last; return result; }
    private static String encodeFilename(String filename) { return java.net.URLEncoder.encode(filename,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"); }
}

