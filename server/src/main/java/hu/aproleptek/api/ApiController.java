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
    public List<Map<String, Object>> taxonomy(@RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) return jdbc.queryForList("select * from taxonomy_term where active=true order by term_type, sort_order, name");
        return jdbc.queryForList("select * from taxonomy_term where active=true and term_type=? order by sort_order, name", type.toUpperCase(Locale.ROOT));
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

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        LocalStorageService.StoredFile stored = storage.save(file);
        UUID id = UUID.randomUUID();
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("feltoltes");
        jdbc.update("insert into media_asset(id, original_name, storage_key, media_type, file_size) values (?,?,?,?,?)",
                id, originalName, stored.key(), stored.mediaType(), stored.size());
        return Map.of("id", id.toString(), "originalName", originalName, "mediaType", stored.mediaType(), "fileSize", stored.size(), "url", "/api/v1/media/" + id + "/content");
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
        Object previewId = uuidOrNull(b.get("previewAssetId"));
        Object[] values = {title, slug, optional(b, "summary"), optional(b, "description"), optional(b, "instructions"), status,
                valueOr(b.get("paperSize"), "A4"), valueOr(b.get("orientation"), "PORTRAIT"), valueOr(b.get("colorMode"), "COLOR"),
                valueOr(b.get("usageType"), "DIGITAL"), optional(b, "safetyNotes"), previewId, Timestamp.from(Instant.now()),
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
            jdbc.update("insert into worksheet_page(worksheet_id,page_type,title,sort_order,page_count,paper_size,orientation,color_mode,notes,preview_asset_id,print_asset_id) values (?,?,?,?,?,?,?,?,?,?,?)",
                    id, pageType, optional(p,"title"), intValue(p.get("sortOrder"), order++), Math.max(1,intValue(p.get("pageCount"),1)), valueOr(p.get("paperSize"), "A4"), valueOr(p.get("orientation"), "PORTRAIT"), valueOr(p.get("colorMode"), "COLOR"), optional(p,"notes"), uuidOrNull(p.get("previewAssetId")), uuidOrNull(p.get("printAssetId")));
        }
        jdbc.update("update worksheet set preview_asset_id=(select preview_asset_id from worksheet_page where worksheet_id=? and page_type='MAIN' order by sort_order limit 1) where id=?", id, id);
        jdbc.update("delete from worksheet_term where worksheet_id=?", id);
        saveTerms("worksheet_id", id, b.get("termIds"));
    }

    private Map<String, Object> loadWorksheet(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("select * from worksheet where id=?", id);
        row.put("pages", jdbc.queryForList("select p.*, mp.original_name as preview_name, mp.media_type as preview_media_type, fp.original_name as print_name, fp.media_type as print_media_type from worksheet_page p left join media_asset mp on mp.id=p.preview_asset_id left join media_asset fp on fp.id=p.print_asset_id where p.worksheet_id=? order by p.sort_order", id));
        row.put("terms", termsFor("worksheet_id", id));
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
        jdbc.update("delete from activity_term where activity_id=?", id); saveTerms("activity_id",id,b.get("termIds"));
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
            String type = jdbc.queryForObject("select term_type from taxonomy_term where id=? and active=true", String.class, termId);
            if ("activity_id".equals(column) && "TASK_TYPE".equals(type)) throw new IllegalArgumentException("A feladattípus csak feladatlaphoz rendelhető.");
            jdbc.update("insert into " + relation + "("+column+",term_id) values (?,?)",id,termId);
        }
    }

    private long count(String table) {
        if (!Set.of("worksheet","activity").contains(table)) throw new IllegalArgumentException("Invalid table");
        return jdbc.queryForObject("select count(*) from "+table,Long.class);
    }
    private static String required(Map<String,Object> b,String key) { String v=optional(b,key); if(v==null) throw new IllegalArgumentException("A(z) "+key+" mező kötelező."); return v; }
    private static String optional(Map<String,Object> b,String key) { Object value=b.get(key); if(value==null) return null; String s=value.toString().trim(); return s.isEmpty()?null:s; }
    private static String valueOr(Object value,String fallback) { return value==null || value.toString().isBlank()?fallback:value.toString().trim().toUpperCase(Locale.ROOT); }
    private static String status(Object value) { String s=valueOr(value,"DRAFT"); if(!Set.of("DRAFT","REVIEW","PUBLISHED","ARCHIVED").contains(s)) throw new IllegalArgumentException("Érvénytelen státusz."); return s; }
    private static int intValue(Object value,int fallback) { if(value==null) return fallback; try{return Integer.parseInt(value.toString());}catch(NumberFormatException ex){return fallback;} }
    private static Object nullableInt(Object value) { if(value==null || value.toString().isBlank()) return null; return intValue(value,0); }
    private static Object uuidOrNull(Object value) { return value==null || value.toString().isBlank()?null:UUID.fromString(value.toString()); }
    private static String slugify(String text) { String s=java.text.Normalizer.normalize(text.toLowerCase(Locale.forLanguageTag("hu")), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}",""); return s.replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$",""); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> listOfMaps(Object value) { if(!(value instanceof List<?> l)) return new ArrayList<>(); return l.stream().filter(Map.class::isInstance).map(v->new HashMap<>((Map<String,Object>)v)).collect(Collectors.toCollection(ArrayList::new)); }
    private static Object[] prepend(Object first,Object[] values) { Object[] result=new Object[values.length+1]; result[0]=first; System.arraycopy(values,0,result,1,values.length); return result; }
    private static Object[] append(Object[] values,Object last) { Object[] result=Arrays.copyOf(values,values.length+1); result[values.length]=last; return result; }
    private static String encodeFilename(String filename) { return java.net.URLEncoder.encode(filename,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"); }
}

