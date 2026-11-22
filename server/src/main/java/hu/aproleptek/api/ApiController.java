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

    @GetMapping("/catalog/types")
    public List<Map<String,Object>> catalogTypes() {
        return jdbc.queryForList("select code,name,sort_order from catalog_type where active=true order by sort_order,name");
    }

    @GetMapping("/catalog/items")
    public List<Map<String,Object>> catalogItems(@RequestParam(required=false) String type,
                                                  @RequestParam(defaultValue="") String q,
                                                  @RequestParam(required=false) String status) {
        String normalizedType=type==null||type.isBlank()?null:type.toUpperCase(Locale.ROOT);
        String normalizedStatus=status==null||status.isBlank()?null:status.toUpperCase(Locale.ROOT);
        String search="%"+q.trim().toLowerCase(Locale.ROOT)+"%";
        return jdbc.queryForList("select ci.*,ct.name as type_name from catalog_item ci join catalog_type ct on ct.code=ci.type_code " +
                        "where (cast(? as varchar) is null or ci.type_code=?) and (cast(? as varchar) is null or ci.status=?) " +
                        "and (lower(ci.title) like ? or lower(coalesce(ci.summary,'')) like ?) order by ct.sort_order,ci.title",
                normalizedType,normalizedType,normalizedStatus,normalizedStatus,search,search);
    }

    @GetMapping("/card-decks")
    public List<Map<String,Object>> cardDecks() {
        return jdbc.queryForList("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,cd.back_asset_id,cd.rules,cd.cards_per_sheet,count(c.card_id) as card_count " +
                "from catalog_item ci join card_deck cd on cd.catalog_item_id=ci.id left join card_deck_card c on c.deck_id=ci.id " +
                "group by ci.id,cd.catalog_item_id order by ci.updated_at desc");
    }

    @GetMapping("/card-decks/{id}")
    public Map<String,Object> cardDeck(@PathVariable UUID id) { return loadCardDeck(id); }

    @GetMapping("/card-faces")
    public List<Map<String,Object>> cardFaces() {
        return jdbc.queryForList("select ci.id,ci.title,cf.front_asset_id,m.original_name as front_name,m.media_type as front_media_type " +
                "from card_face cf join catalog_item ci on ci.id=cf.catalog_item_id join media_asset m on m.id=cf.front_asset_id " +
                "where ci.status<>'ARCHIVED' order by ci.title");
    }

    @PostMapping("/card-decks")
    @Transactional
    public Map<String,Object> createCardDeck(@RequestBody Map<String,Object> body) {
        UUID id=UUID.randomUUID(); saveCardDeck(id,body,true); return loadCardDeck(id);
    }

    @PutMapping("/card-decks/{id}")
    @Transactional
    public Map<String,Object> updateCardDeck(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        saveCardDeck(id,body,false); return loadCardDeck(id);
    }

    @DeleteMapping("/card-decks/{id}")
    public ResponseEntity<Void> archiveCardDeck(@PathVariable UUID id) {
        jdbc.update("update catalog_item set status='ARCHIVED',updated_at=? where id=? and type_code='CARD_DECK'",Timestamp.from(Instant.now()),id);
        return ResponseEntity.noContent().build();
    }

    private Map<String,Object> loadCardDeck(UUID id) {
        Map<String,Object> row=jdbc.queryForMap("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,cd.back_asset_id,cd.rules,cd.cards_per_sheet " +
                "from catalog_item ci join card_deck cd on cd.catalog_item_id=ci.id where ci.id=? and ci.type_code='CARD_DECK'",id);
        row.put("cards",jdbc.queryForList("select c.card_id as id,ci.title,cf.front_asset_id,c.sort_order,m.original_name as front_name,m.media_type as front_media_type " +
                "from card_deck_card c join card_face cf on cf.catalog_item_id=c.card_id join catalog_item ci on ci.id=c.card_id " +
                "join media_asset m on m.id=cf.front_asset_id where c.deck_id=? order by c.sort_order",id));
        return row;
    }

    private void saveCardDeck(UUID id,Map<String,Object> body,boolean insert) {
        String title=required(body,"title");
        if(title.length()>360) throw new IllegalArgumentException("A kártyacsomag neve legfeljebb 360 karakter lehet.");
        String slug=Optional.ofNullable(optional(body,"slug")).orElseGet(()->slugify(title));
        String itemStatus=status(body.get("status"));
        String summary=optional(body,"summary"),description=optional(body,"description"),rules=optional(body,"rules");
        UUID previewAssetId=uuidOrNull(body.get("previewAssetId")),backAssetId=uuidOrNull(body.get("backAssetId"));
        requireImageAsset(previewAssetId,"borítókép"); requireImageAsset(backAssetId,"hátoldal");
        int cardsPerSheet=boundedInt(body.get("cardsPerSheet"),1,1,100,"egy oldalra rendezett kártyák száma");
        UUID catalogItemId=id;
        if(!insert) jdbc.queryForObject("select id from card_deck where catalog_item_id=?",UUID.class,id);
        upsertCatalogItem(catalogItemId,"CARD_DECK",slug,title,summary,description,itemStatus,previewAssetId);
        if(insert) jdbc.update("insert into card_deck(catalog_item_id,back_asset_id,rules,cards_per_sheet) values (?,?,?,?)",catalogItemId,backAssetId,rules,cardsPerSheet);
        else jdbc.update("update card_deck set back_asset_id=?,rules=?,cards_per_sheet=? where catalog_item_id=?",backAssetId,rules,cardsPerSheet,catalogItemId);
        jdbc.update("delete from card_deck_card where deck_id=?",catalogItemId);
        List<Map<String,Object>> cards=listOfMaps(body.get("cards"));
        Set<UUID> linkedCardIds=new HashSet<>();
        for(int index=0;index<cards.size();index++) {
            Map<String,Object> card=cards.get(index);
            UUID frontAssetId=uuidOrNull(card.get("frontAssetId"));
            UUID cardId=uuidOrNull(card.get("id"));
            if(cardId==null&&frontAssetId==null) throw new IllegalArgumentException("Minden új kártyalaphoz tölts fel előoldali képet.");
            if(cardId!=null&&!linkedCardIds.add(cardId)) throw new IllegalArgumentException("Egy kártyalap ugyanabban a csomagban csak egyszer szerepelhet.");
            String cardTitle=optional(card,"title");
            if(cardTitle!=null&&cardTitle.length()>200) throw new IllegalArgumentException("A kártyalap neve legfeljebb 200 karakter lehet.");
            if(cardId!=null) {
                jdbc.queryForObject("select catalog_item_id from card_face where catalog_item_id=?",UUID.class,cardId);
                if(frontAssetId==null) frontAssetId=jdbc.queryForObject("select front_asset_id from card_face where catalog_item_id=?",UUID.class,cardId);
                requireImageAsset(frontAssetId,"kártyalap");
                String savedTitle=cardTitle!=null?cardTitle:jdbc.queryForObject("select title from catalog_item where id=?",String.class,cardId);
                jdbc.update("update catalog_item set title=?,preview_asset_id=?,updated_at=? where id=? and type_code='CARD_FACE'",savedTitle,frontAssetId,Timestamp.from(Instant.now()),cardId);
                jdbc.update("update card_face set front_asset_id=? where catalog_item_id=?",frontAssetId,cardId);
            } else {
                cardId=UUID.randomUUID();
                linkedCardIds.add(cardId);
                requireImageAsset(frontAssetId,"kártyalap");
                String savedTitle=cardTitle!=null?cardTitle:jdbc.queryForObject("select original_name from media_asset where id=?",String.class,frontAssetId);
                upsertCatalogItem(cardId,"CARD_FACE","card-"+cardId,savedTitle,null,null,"DRAFT",frontAssetId);
                jdbc.update("insert into card_face(catalog_item_id,front_asset_id) values (?,?)",cardId,frontAssetId);
            }
            jdbc.update("insert into card_deck_card(deck_id,card_id,sort_order) values (?,?,?)",catalogItemId,cardId,index);
        }
    }

    private void requireImageAsset(UUID assetId,String label) {
        if(assetId==null) return;
        String mediaType=jdbc.queryForObject("select media_type from media_asset where id=?",String.class,assetId);
        if(mediaType==null||!mediaType.toLowerCase(Locale.ROOT).startsWith("image/")) throw new IllegalArgumentException("A(z) "+label+" csak képfájl lehet.");
    }

    @GetMapping("/game-families")
    public List<Map<String,Object>> gameFamilies(){return jdbc.queryForList("select code,name,sort_order from game_family where active=true order by sort_order,name");}

    @PostMapping("/game-families")
    public Map<String,Object> createGameFamily(@RequestBody Map<String,Object> body){
        String name=required(body,"name");if(name.length()>120)throw new IllegalArgumentException("A játéktípus neve legfeljebb 120 karakter lehet.");
        String code=Optional.ofNullable(optional(body,"code")).orElseGet(()->slugify(name).replace('-','_').toUpperCase(Locale.ROOT));
        if(!code.matches("[A-Z0-9_]{2,40}"))throw new IllegalArgumentException("A játéktípus kódja csak nagybetűt, számot és aláhúzást tartalmazhat.");
        jdbc.update("insert into game_family(code,name,sort_order) values (?,?,?)",code,name,boundedInt(body.get("sortOrder"),100,0,10000,"megjelenítési sorrend"));
        return jdbc.queryForMap("select code,name,sort_order from game_family where code=?",code);
    }

    @GetMapping("/game-element-categories")
    public List<Map<String,Object>> gameElementCategories(){return jdbc.queryForList("select code,name,sort_order from game_element_category where active=true order by sort_order,name");}

    @PostMapping("/game-element-categories")
    public Map<String,Object> createGameElementCategory(@RequestBody Map<String,Object> body){
        String name=required(body,"name");if(name.length()>120)throw new IllegalArgumentException("A játékelem-kategória neve legfeljebb 120 karakter lehet.");
        String code=Optional.ofNullable(optional(body,"code")).orElseGet(()->slugify(name).replace('-','_').toUpperCase(Locale.ROOT));
        if(!code.matches("[A-Z0-9_]{2,40}"))throw new IllegalArgumentException("A kategóriakód csak nagybetűt, számot és aláhúzást tartalmazhat.");
        jdbc.update("insert into game_element_category(code,name,sort_order) values (?,?,?)",code,name,boundedInt(body.get("sortOrder"),100,0,10000,"megjelenítési sorrend"));
        return jdbc.queryForMap("select code,name,sort_order from game_element_category where code=?",code);
    }

    @GetMapping("/game-elements")
    public List<Map<String,Object>> gameElements(){
        return jdbc.queryForList("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,ge.category_code,gc.name as category_name,ge.print_pages,ge.units_per_sheet " +
                "from game_element ge join catalog_item ci on ci.id=ge.catalog_item_id join game_element_category gc on gc.code=ge.category_code order by ci.updated_at desc");
    }

    @GetMapping("/game-elements/{id}")
    public Map<String,Object> gameElement(@PathVariable UUID id){return loadGameElement(id);}

    @PostMapping("/game-elements")
    @Transactional
    public Map<String,Object> createGameElement(@RequestBody Map<String,Object> body){UUID id=UUID.randomUUID();saveGameElement(id,body,true);return loadGameElement(id);}

    @PutMapping("/game-elements/{id}")
    @Transactional
    public Map<String,Object> updateGameElement(@PathVariable UUID id,@RequestBody Map<String,Object> body){saveGameElement(id,body,false);return loadGameElement(id);}

    @DeleteMapping("/game-elements/{id}")
    public ResponseEntity<Void> archiveGameElement(@PathVariable UUID id){jdbc.update("update catalog_item set status='ARCHIVED',updated_at=? where id=? and type_code='GAME_ELEMENT'",Timestamp.from(Instant.now()),id);return ResponseEntity.noContent().build();}

    private Map<String,Object> loadGameElement(UUID id){
        Map<String,Object> row=jdbc.queryForMap("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,ge.category_code,gc.name as category_name,ge.print_pages,ge.units_per_sheet " +
                "from game_element ge join catalog_item ci on ci.id=ge.catalog_item_id join game_element_category gc on gc.code=ge.category_code where ci.id=? and ci.type_code='GAME_ELEMENT'",id);
        row.put("compatible_element_ids",jdbc.queryForList("select compatible_element_id from game_element_compatibility where element_id=? order by compatible_element_id",UUID.class,id));
        return row;
    }

    private void saveGameElement(UUID id,Map<String,Object> body,boolean insert){
        String title=required(body,"title");if(title.length()>360)throw new IllegalArgumentException("A játékelem neve legfeljebb 360 karakter lehet.");
        String slug=Optional.ofNullable(optional(body,"slug")).orElseGet(()->slugify(title));
        String summary=optional(body,"summary"),description=optional(body,"description"),itemStatus=status(body.get("status"));
        String categoryCode=required(body,"categoryCode").toUpperCase(Locale.ROOT);
        jdbc.queryForObject("select code from game_element_category where code=? and active=true",String.class,categoryCode);
        UUID previewAssetId=uuidOrNull(body.get("previewAssetId"));requireImageAsset(previewAssetId,"játékelem képe");
        int printPages=boundedInt(body.get("printPages"),0,0,100,"nyomtatandó oldalak száma");
        int unitsPerSheet=boundedInt(body.get("unitsPerSheet"),1,1,100,"egy oldalra férő játékelemek száma");
        upsertCatalogItem(id,"GAME_ELEMENT",slug,title,summary,description,itemStatus,previewAssetId);
        if(insert)jdbc.update("insert into game_element(catalog_item_id,category_code,print_pages,units_per_sheet) values (?,?,?,?)",id,categoryCode,printPages,unitsPerSheet);
        else jdbc.update("update game_element set category_code=?,print_pages=?,units_per_sheet=? where catalog_item_id=?",categoryCode,printPages,unitsPerSheet,id);
        jdbc.update("delete from game_element_compatibility where element_id=?",id);
        if(body.get("compatibleElementIds") instanceof List<?> compatibleIds)for(Object raw:compatibleIds){
            UUID compatibleId;try{compatibleId=UUID.fromString(raw.toString());}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Érvénytelen kompatibilis játékelem.");}
            if(id.equals(compatibleId))throw new IllegalArgumentException("Egy játékelem nem lehet önmagával kompatibilis.");
            jdbc.queryForObject("select catalog_item_id from game_element where catalog_item_id=?",UUID.class,compatibleId);
            jdbc.update("insert into game_element_compatibility(element_id,compatible_element_id) values (?,?)",id,compatibleId);
        }
    }

    @GetMapping("/games")
    public List<Map<String,Object>> games(){
        return jdbc.queryForList("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,g.family_code,gf.name as family_name,g.rules, " +
                "(g.print_pages+coalesce(sum(ceil(gce.quantity::numeric/ge.units_per_sheet)*ge.print_pages),0)) as print_pages,count(gce.element_id) as element_count " +
                "from game_content g join catalog_item ci on ci.id=g.catalog_item_id join game_family gf on gf.code=g.family_code " +
                "left join game_content_element gce on gce.game_id=g.catalog_item_id left join game_element ge on ge.catalog_item_id=gce.element_id " +
                "group by ci.id,g.catalog_item_id,gf.name,g.print_pages order by ci.updated_at desc");
    }

    @GetMapping("/games/{id}")
    public Map<String,Object> game(@PathVariable UUID id){return loadGame(id);}

    @PostMapping("/games")
    @Transactional
    public Map<String,Object> createGame(@RequestBody Map<String,Object> body){UUID id=UUID.randomUUID();saveGame(id,body,true);return loadGame(id);}

    @PutMapping("/games/{id}")
    @Transactional
    public Map<String,Object> updateGame(@PathVariable UUID id,@RequestBody Map<String,Object> body){saveGame(id,body,false);return loadGame(id);}

    @DeleteMapping("/games/{id}")
    public ResponseEntity<Void> archiveGame(@PathVariable UUID id){jdbc.update("update catalog_item set status='ARCHIVED',updated_at=? where id=? and type_code='GAME'",Timestamp.from(Instant.now()),id);return ResponseEntity.noContent().build();}

    private Map<String,Object> loadGame(UUID id){
        Map<String,Object> row=jdbc.queryForMap("select ci.id,ci.slug,ci.title,ci.summary,ci.description,ci.status,ci.preview_asset_id,g.family_code,gf.name as family_name,g.rules,g.print_pages " +
                "from game_content g join catalog_item ci on ci.id=g.catalog_item_id join game_family gf on gf.code=g.family_code where ci.id=? and ci.type_code='GAME'",id);
        row.put("elements",jdbc.queryForList("select gce.element_id,gce.quantity,gce.sort_order,ci.title,ci.preview_asset_id,ge.category_code,gc.name as category_name " +
                "from game_content_element gce join catalog_item ci on ci.id=gce.element_id join game_element ge on ge.catalog_item_id=ci.id join game_element_category gc on gc.code=ge.category_code " +
                "where gce.game_id=? order by gce.sort_order,ci.title",id));
        return row;
    }

    private void saveGame(UUID id,Map<String,Object> body,boolean insert){
        String title=required(body,"title");if(title.length()>360)throw new IllegalArgumentException("A játék neve legfeljebb 360 karakter lehet.");
        String slug=Optional.ofNullable(optional(body,"slug")).orElseGet(()->slugify(title));
        String summary=optional(body,"summary"),description=optional(body,"description"),itemStatus=status(body.get("status"));
        String familyCode=required(body,"familyCode").toUpperCase(Locale.ROOT);
        jdbc.queryForObject("select code from game_family where code=? and active=true",String.class,familyCode);
        UUID previewAssetId=uuidOrNull(body.get("previewAssetId"));requireImageAsset(previewAssetId,"játék borítóképe");
        String rules=optional(body,"rules");int printPages=boundedInt(body.get("printPages"),0,0,1000,"játékszabály nyomtatási oldalszáma");
        upsertCatalogItem(id,"GAME",slug,title,summary,description,itemStatus,previewAssetId);
        if(insert)jdbc.update("insert into game_content(catalog_item_id,family_code,rules,print_pages) values (?,?,?,?)",id,familyCode,rules,printPages);
        else jdbc.update("update game_content set family_code=?,rules=?,print_pages=? where catalog_item_id=?",familyCode,rules,printPages,id);
        jdbc.update("delete from game_content_element where game_id=?",id);
        List<Map<String,Object>> elements=listOfMaps(body.get("elements"));Set<UUID> uniqueElements=new HashSet<>();
        for(int index=0;index<elements.size();index++){
            Map<String,Object> element=elements.get(index);UUID elementId;
            try{elementId=UUID.fromString(required(element,"elementId"));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Érvénytelen játékelem.");}
            if(!uniqueElements.add(elementId))throw new IllegalArgumentException("Egy játékelem csak egyszer szerepeljen az összeállításban.");
            jdbc.queryForObject("select catalog_item_id from game_element where catalog_item_id=?",UUID.class,elementId);
            int quantity=boundedInt(element.get("quantity"),1,1,10000,"játékelem darabszáma");
            jdbc.update("insert into game_content_element(game_id,element_id,quantity,sort_order) values (?,?,?,?)",id,elementId,quantity,index);
        }
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
        jdbc.update("update catalog_item set status='ARCHIVED',updated_at=? where id=(select catalog_item_id from worksheet where id=?)",Timestamp.from(Instant.now()),id);
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
        jdbc.update("update catalog_item set status='ARCHIVED',updated_at=? where id=(select catalog_item_id from bundle_plan where id=?)",Timestamp.from(Instant.now()),id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> saleProducts() {
        List<Map<String,Object>> products = jdbc.queryForList("select p.*, ci.title as content_title,ci.type_code as content_type, " +
                "coalesce(ci.preview_asset_id,w.preview_asset_id,(select wp.preview_asset_id from bundle_worksheet bw join worksheet wp on wp.id=bw.worksheet_id where bw.bundle_id=p.bundle_id order by bw.sort_order limit 1)) as preview_asset_id, " +
                "coalesce(s.units_sold,0) as units_sold, coalesce(s.sale_count,0) as sale_count, " +
                "coalesce(s.revenue,0) as revenue, coalesce(s.material_cost,0) as sold_material_cost " +
                "from sale_product p join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id " +
                "left join (select product_id,sum(quantity) as units_sold,count(distinct sale_id) as sale_count, " +
                "sum(agreed_line_total) as revenue,sum(quantity*material_unit_cost+packaging_cost) as material_cost from sale_line group by product_id) s on s.product_id=p.id " +
                "order by p.active desc, content_title, p.variant_name");
        products.forEach(product -> {
            product.put("materials", productMaterials((UUID) product.get("id")));
            addPrintingCost(product);
        });
        return products;
    }

    @PutMapping("/products/{id}/stock")
    @Transactional
    public Map<String,Object> setProductStock(@PathVariable UUID id, @RequestBody Map<String,Object> body) {
        int quantity=boundedInt(body.get("quantity"),0,0,100000,"készlet");
        Integer previous=jdbc.queryForObject("select stock_quantity from sale_product where id=? and delivery_type='PHYSICAL' for update",Integer.class,id);
        jdbc.update("update sale_product set stock_quantity=?,updated_at=? where id=? and delivery_type='PHYSICAL'",quantity,Timestamp.from(Instant.now()),id);
        if(quantity!=previous) recordStockMovement(id,quantity-previous,"MANUAL_SET",null,"Készlet közvetlen beállítása");
        return jdbc.queryForMap("select id,stock_quantity from sale_product where id=? and delivery_type='PHYSICAL'",id);
    }

    @PostMapping("/products/{id}/stock-adjustments")
    @Transactional
    public Map<String,Object> adjustProductStock(@PathVariable UUID id, @RequestBody Map<String,Object> body) {
        int delta=boundedInt(body.get("delta"),0,-100000,100000,"készletmódosítás");
        if(delta==0) throw new IllegalArgumentException("A készletmódosítás nem lehet nulla.");
        jdbc.update("update sale_product set stock_quantity=stock_quantity+?,updated_at=? where id=? and delivery_type='PHYSICAL'",delta,Timestamp.from(Instant.now()),id);
        recordStockMovement(id,delta,"MANUAL_ADJUSTMENT",null,delta>0?"Kézi készletfeltöltés":"Kézi készletcsökkentés");
        return jdbc.queryForMap("select id,stock_quantity from sale_product where id=? and delivery_type='PHYSICAL'",id);
    }

    @PutMapping("/products/{id}/stock-minimum")
    @Transactional
    public Map<String,Object> setProductStockMinimum(@PathVariable UUID id, @RequestBody Map<String,Object> body) {
        int minimum=boundedInt(body.get("minimum"),0,0,100000,"készletminimum");
        jdbc.update("update sale_product set stock_minimum=?,updated_at=? where id=? and delivery_type='PHYSICAL'",minimum,Timestamp.from(Instant.now()),id);
        return jdbc.queryForMap("select id,stock_minimum from sale_product where id=? and delivery_type='PHYSICAL'",id);
    }

    @PutMapping("/products/{id}/preparation")
    @Transactional
    public Map<String,Object> updateProductPreparation(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        String note=optional(body,"note");
        if(note!=null&&note.length()>2000) throw new IllegalArgumentException("A megjegyzés legfeljebb 2000 karakter lehet.");
        jdbc.update("update sale_product set preparation_printed=?,preparation_laminated=?,preparation_velcro=?,preparation_note=?,updated_at=? where id=? and delivery_type='PHYSICAL'",
                Boolean.parseBoolean(Objects.toString(body.get("printed"),"false")),Boolean.parseBoolean(Objects.toString(body.get("laminated"),"false")),
                Boolean.parseBoolean(Objects.toString(body.get("velcro"),"false")),note,Timestamp.from(Instant.now()),id);
        return jdbc.queryForMap("select id,preparation_printed,preparation_laminated,preparation_velcro,preparation_note from sale_product where id=? and delivery_type='PHYSICAL'",id);
    }

    @GetMapping("/inventory/movements")
    public List<Map<String,Object>> stockMovements(@RequestParam(defaultValue="100") int limit) {
        int safeLimit=Math.max(1,Math.min(limit,500));
        return jdbc.queryForList("select sm.id,sm.product_id,sm.sale_id,sm.source_type,sm.quantity_delta,sm.note,sm.created_at, " +
                "p.variant_name,ci.title as content_title " +
                "from stock_movement sm join sale_product p on p.id=sm.product_id " +
                "join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id " +
                "order by sm.created_at desc,sm.id desc limit ?",safeLimit);
    }

    @GetMapping("/inventory/preparations")
    public List<Map<String,Object>> productPreparations() {
        return jdbc.queryForList("select pp.*,p.variant_name,ci.title as content_title,coalesce(ci.preview_asset_id,w.preview_asset_id,(select wp.preview_asset_id from bundle_worksheet bw join worksheet wp on wp.id=bw.worksheet_id where bw.bundle_id=p.bundle_id order by bw.sort_order limit 1)) as preview_asset_id " +
                "from product_preparation pp join sale_product p on p.id=pp.product_id " +
                "join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id " +
                "order by case when pp.status='IN_PROGRESS' then 0 else 1 end,pp.created_at desc limit 200");
    }

    @PostMapping("/products/{id}/preparations")
    @Transactional
    public Map<String,Object> createProductPreparation(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        jdbc.queryForObject("select id from sale_product where id=? and delivery_type='PHYSICAL' and active=true",UUID.class,id);
        int quantity=boundedInt(body.get("quantity"),1,1,100000,"előkészítendő darabszám");
        String note=optional(body,"note");
        if(note!=null&&note.length()>2000) throw new IllegalArgumentException("A megjegyzés legfeljebb 2000 karakter lehet.");
        UUID preparationId=UUID.randomUUID();
        jdbc.update("insert into product_preparation(id,product_id,quantity,printed,laminated,velcro,note) values (?,?,?,?,?,?,?)",preparationId,id,quantity,
                Boolean.parseBoolean(Objects.toString(body.get("printed"),"false")),Boolean.parseBoolean(Objects.toString(body.get("laminated"),"false")),
                Boolean.parseBoolean(Objects.toString(body.get("velcro"),"false")),note);
        return jdbc.queryForMap("select pp.*,p.variant_name,ci.title as content_title,coalesce(ci.preview_asset_id,w.preview_asset_id,(select wp.preview_asset_id from bundle_worksheet bw join worksheet wp on wp.id=bw.worksheet_id where bw.bundle_id=p.bundle_id order by bw.sort_order limit 1)) as preview_asset_id from product_preparation pp join sale_product p on p.id=pp.product_id join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id where pp.id=?",preparationId);
    }

    @PutMapping("/inventory/preparations/{id}")
    @Transactional
    public Map<String,Object> updateProductPreparation(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        int quantity=boundedInt(body.get("quantity"),1,1,100000,"előkészítendő darabszám");
        String note=optional(body,"note");
        if(note!=null&&note.length()>2000) throw new IllegalArgumentException("A megjegyzés legfeljebb 2000 karakter lehet.");
        int updated=jdbc.update("update product_preparation set quantity=?,printed=?,laminated=?,velcro=?,note=? where id=? and status='IN_PROGRESS'",quantity,
                Boolean.parseBoolean(Objects.toString(body.get("printed"),"false")),Boolean.parseBoolean(Objects.toString(body.get("laminated"),"false")),
                Boolean.parseBoolean(Objects.toString(body.get("velcro"),"false")),note,id);
        if(updated!=1) throw new IllegalArgumentException("Ez az előkészítés már lezárult, vagy nem található.");
        return jdbc.queryForMap("select pp.*,p.variant_name,ci.title as content_title,coalesce(ci.preview_asset_id,w.preview_asset_id,(select wp.preview_asset_id from bundle_worksheet bw join worksheet wp on wp.id=bw.worksheet_id where bw.bundle_id=p.bundle_id order by bw.sort_order limit 1)) as preview_asset_id from product_preparation pp join sale_product p on p.id=pp.product_id join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id where pp.id=?",id);
    }

    @PostMapping("/inventory/preparations/{id}/complete")
    @Transactional
    public Map<String,Object> completeProductPreparation(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        Map<String,Object> preparation=jdbc.queryForMap("select * from product_preparation where id=? for update",id);
        if(!"IN_PROGRESS".equals(preparation.get("status"))) throw new IllegalArgumentException("Ez az előkészítés már lezárult.");
        UUID productId=(UUID)preparation.get("product_id");
        int remaining=((Number)preparation.get("quantity")).intValue();
        int quantity=boundedInt(body.get("quantity"),remaining,1,remaining,"késztermékké tett darabszám");
        List<Map<String,Object>> materialRequirements=jdbc.queryForList("select m.id,m.name,m.unit_name,m.stock_quantity,pm.quantity*? as required_quantity " +
                "from product_material pm join material_item m on m.id=pm.material_id where pm.product_id=? order by m.id for update of m",quantity,productId);
        for(Map<String,Object> requirement:materialRequirements) {
            BigDecimal available=databaseDecimal(requirement.get("stock_quantity"));
            BigDecimal required=databaseDecimal(requirement.get("required_quantity"));
            if(available.compareTo(required)<0) throw new IllegalArgumentException("Nincs elég készleten ebből az alapanyagból: " + requirement.get("name") + ". Szükséges: " + required.stripTrailingZeros().toPlainString() + " " + requirement.get("unit_name") + ", készleten: " + available.stripTrailingZeros().toPlainString() + ".");
        }
        for(Map<String,Object> requirement:materialRequirements) jdbc.update("update material_item set stock_quantity=stock_quantity-? where id=?",requirement.get("required_quantity"),requirement.get("id"));
        int moved=jdbc.update("update sale_product set stock_quantity=stock_quantity+?,updated_at=? where id=? and delivery_type='PHYSICAL'",quantity,Timestamp.from(Instant.now()),productId);
        if(moved!=1) throw new IllegalArgumentException("Csak fizikai termék kerülhet késztermékkészletre.");
        Timestamp completedAt=Timestamp.from(Instant.now());
        if(quantity==remaining) jdbc.update("update product_preparation set status='COMPLETE',completed_at=? where id=?",completedAt,id);
        else {
            jdbc.update("update product_preparation set quantity=? where id=?",remaining-quantity,id);
            jdbc.update("insert into product_preparation(id,product_id,quantity,printed,laminated,velcro,note,status,completed_at) values (?,?,?,?,?,?,?,'COMPLETE',?)",
                    UUID.randomUUID(),productId,quantity,preparation.get("printed"),preparation.get("laminated"),preparation.get("velcro"),preparation.get("note"),completedAt);
        }
        recordStockMovement(productId,quantity,"PREPARATION_COMPLETED",null,"Előkészítésből " + quantity + " db késztermékként készletre véve");
        return jdbc.queryForMap("select pp.*,p.variant_name,ci.title as content_title,coalesce(ci.preview_asset_id,w.preview_asset_id,(select wp.preview_asset_id from bundle_worksheet bw join worksheet wp on wp.id=bw.worksheet_id where bw.bundle_id=p.bundle_id order by bw.sort_order limit 1)) as preview_asset_id from product_preparation pp join sale_product p on p.id=pp.product_id join catalog_item ci on ci.id=p.catalog_item_id left join worksheet w on w.id=p.worksheet_id left join bundle_plan b on b.id=p.bundle_id where pp.id=?",id);
    }

    @DeleteMapping("/inventory/preparations/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProductPreparation(@PathVariable UUID id) {
        int deleted=jdbc.update("delete from product_preparation where id=? and status='IN_PROGRESS'",id);
        if(deleted!=1) throw new IllegalArgumentException("Csak folyamatban lévő, létező előkészítés törölhető.");
        return ResponseEntity.noContent().build();
    }

    private void recordStockMovement(UUID productId,int delta,String sourceType,UUID saleId,String note) {
        if(delta==0) return;
        jdbc.update("insert into stock_movement(product_id,sale_id,source_type,quantity_delta,note) values (?,?,?,?,?)",productId,saleId,sourceType,delta,note);
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
        if (!Set.of("WORKSHEET", "BUNDLE", "CARD_DECK", "GAME", "GAME_ELEMENT").contains(contentType)) throw new IllegalArgumentException("Ehhez a tartalomtípushoz még nem hozható létre termékváltozat.");
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
        UUID catalogItemId;
        if ("WORKSHEET".equals(contentType)) catalogItemId=jdbc.queryForObject("select catalog_item_id from worksheet where id=?",UUID.class,contentId);
        else if("BUNDLE".equals(contentType)) catalogItemId=jdbc.queryForObject("select catalog_item_id from bundle_plan where id=?",UUID.class,contentId);
        else catalogItemId=jdbc.queryForObject("select id from catalog_item where id=? and type_code=?",UUID.class,contentId,contentType);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("insert into sale_product(id,worksheet_id,bundle_id,catalog_item_id,variant_name,delivery_type,list_price,material_cost,print_sides) values (?,?,?,?,?,?,?,?,?)", id, worksheetId, bundleId,catalogItemId, variantName, deliveryType, listPrice, materialCost, printSides);
        } else {
            jdbc.update("update sale_product set worksheet_id=?,bundle_id=?,catalog_item_id=?,variant_name=?,delivery_type=?,list_price=?,material_cost=?,print_sides=?,updated_at=? where id=?", worksheetId, bundleId,catalogItemId, variantName, deliveryType, listPrice, materialCost, printSides, Timestamp.from(Instant.now()), id);
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
        Map<String,Object> product=jdbc.queryForMap("select p.*, ci.title as content_title,ci.type_code as content_type from sale_product p join catalog_item ci on ci.id=p.catalog_item_id where p.id=?", id);
        product.put("materials",productMaterials(id));
        addPrintingCost(product);
        return product;
    }

    private void addPrintingCost(Map<String,Object> product) {
        int pages=0;
        if("PHYSICAL".equals(product.get("delivery_type"))) {
            if(product.get("worksheet_id")!=null) pages=Optional.ofNullable(jdbc.queryForObject("select coalesce(sum(page_count),0) from worksheet_page where worksheet_id=?",Long.class,product.get("worksheet_id"))).orElse(0L).intValue();
            else if(product.get("bundle_id")!=null) pages=Optional.ofNullable(jdbc.queryForObject("select coalesce(sum(p.page_count),0) from bundle_worksheet bw join worksheet_page p on p.worksheet_id=bw.worksheet_id where bw.bundle_id=?",Long.class,product.get("bundle_id"))).orElse(0L).intValue();
            else if("CARD_DECK".equals(product.get("content_type"))) {
                Map<String,Object> deck=jdbc.queryForMap("select cd.cards_per_sheet,cd.back_asset_id,count(c.card_id) as card_count from card_deck cd left join card_deck_card c on c.deck_id=cd.catalog_item_id where cd.catalog_item_id=? group by cd.catalog_item_id",product.get("catalog_item_id"));
                int cardCount=((Number)deck.get("card_count")).intValue();
                int perSheet=((Number)deck.get("cards_per_sheet")).intValue();
                int sidesPerCard=(deck.get("back_asset_id")==null?1:2);
                pages=((cardCount+perSheet-1)/perSheet)*sidesPerCard;
            } else if("GAME".equals(product.get("content_type"))) {
                int instructions=Optional.ofNullable(jdbc.queryForObject("select print_pages from game_content where catalog_item_id=?",Integer.class,product.get("catalog_item_id"))).orElse(0);
                int components=Optional.ofNullable(jdbc.queryForObject("select coalesce(sum(ceil(gce.quantity::numeric/ge.units_per_sheet)*ge.print_pages),0) from game_content_element gce join game_element ge on ge.catalog_item_id=gce.element_id where gce.game_id=?",Long.class,product.get("catalog_item_id"))).orElse(0L).intValue();
                pages=instructions+components;
            } else if("GAME_ELEMENT".equals(product.get("content_type"))) {
                pages=Optional.ofNullable(jdbc.queryForObject("select print_pages from game_element where catalog_item_id=?",Integer.class,product.get("catalog_item_id"))).orElse(0);
            }
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

    @GetMapping("/orders")
    public List<Map<String,Object>> customerOrders() {
        List<Map<String,Object>> orders=jdbc.queryForList("select * from customer_order order by case status when 'OPEN' then 0 when 'FULFILLED' then 1 else 2 end,created_at desc");
        Map<UUID,Integer> available=new HashMap<>();
        jdbc.queryForList("select id,stock_quantity from sale_product where delivery_type='PHYSICAL'").forEach(row->available.put((UUID)row.get("id"),((Number)row.get("stock_quantity")).intValue()));
        Map<UUID,Map<UUID,Integer>> demandByOrder=new HashMap<>();
        List<Map<String,Object>> openRows=jdbc.queryForList("select id from customer_order where status='OPEN' order by order_date,created_at,id");
        for(Map<String,Object> open:openRows){
            UUID orderId=(UUID)open.get("id");List<Map<String,Object>> lines=jdbc.queryForList("select product_id,quantity from customer_order_line where order_id=? order by product_id",orderId);
            Map<UUID,Integer> production=new HashMap<>();
            for(Map<String,Object> line:lines){UUID productId=(UUID)line.get("product_id");int quantity=((Number)line.get("quantity")).intValue();int stock=Math.max(0,available.getOrDefault(productId,0));int use=Math.min(stock,quantity);production.put(productId,quantity-use);available.put(productId,stock-use);}
            demandByOrder.put(orderId,production);
        }
        for(Map<String,Object> order:orders){
            UUID orderId=(UUID)order.get("id");List<Map<String,Object>> lines=jdbc.queryForList("select id,product_id,product_title,variant_name,quantity,list_unit_price,material_unit_cost,printing_cost " +
                    "from customer_order_line where order_id=? order by product_title,variant_name",orderId);
            for(Map<String,Object> line:lines){UUID productId=(UUID)line.get("product_id");line.put("available_stock_quantity",((Number)jdbc.queryForObject("select stock_quantity from sale_product where id=?",Integer.class,productId)).intValue());line.put("to_manufacture",((Number)demandByOrder.getOrDefault(orderId,Map.of()).getOrDefault(productId,0)).intValue());}
            order.put("items",lines);
        }
        return orders;
    }

    @PostMapping("/orders")
    @Transactional
    public Map<String,Object> createCustomerOrder(@RequestBody Map<String,Object> body){return saveCustomerOrder(null,body);}

    @PutMapping("/orders/{id}")
    @Transactional
    public Map<String,Object> updateCustomerOrder(@PathVariable UUID id,@RequestBody Map<String,Object> body){return saveCustomerOrder(id,body);}

    private Map<String,Object> saveCustomerOrder(UUID existingId,Map<String,Object> body){
        List<Map<String,Object>> rawItems=listOfMaps(body.get("items"));if(rawItems.isEmpty())throw new IllegalArgumentException("Adj hozzá legalább egy terméket a megrendeléshez.");
        Map<UUID,Integer> quantities=new LinkedHashMap<>();for(Map<String,Object> item:rawItems){UUID productId;try{productId=UUID.fromString(required(item,"productId"));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Érvénytelen termékazonosító.");}quantities.merge(productId,boundedInt(item.get("quantity"),1,1,1000,"darabszám"),Integer::sum);}
        java.sql.Date orderDate=dateValue(body.get("orderDate"),"megrendelés dátuma");String customerName=optional(body,"customerName");if(customerName!=null&&customerName.length()>160)throw new IllegalArgumentException("A vevő neve legfeljebb 160 karakter lehet.");
        String channel=Optional.ofNullable(optional(body,"channel")).orElse("Vinted");if(channel.length()>80)throw new IllegalArgumentException("A csatorna neve legfeljebb 80 karakter lehet.");String note=optional(body,"note");
        UUID packagingId=null;String packagingName=null,packagingUnit=null;BigDecimal packagingQuantity=BigDecimal.ZERO.setScale(3),packagingUnitCost=BigDecimal.ZERO.setScale(2),packagingTotalCost=BigDecimal.ZERO.setScale(2);
        String packagingIdValue=optional(body,"packagingMaterialId");if(packagingIdValue!=null&&!packagingIdValue.isBlank()){try{packagingId=UUID.fromString(packagingIdValue);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Érvénytelen csomagolóanyag-azonosító.");}Map<String,Object> packaging=jdbc.queryForMap("select name,unit_name from material_item where id=? and active=true and is_packaging=true",packagingId);packagingName=(String)packaging.get("name");packagingUnit=(String)packaging.get("unit_name");packagingQuantity=decimalQuantity(body.get("packagingQuantity"));packagingUnitCost=jdbc.queryForObject("select coalesce(sum(total_cost)/nullif(sum(quantity),0),0) from material_purchase where material_id=?",BigDecimal.class,packagingId).setScale(2,RoundingMode.HALF_UP);packagingTotalCost=packagingUnitCost.multiply(packagingQuantity).setScale(2,RoundingMode.HALF_UP);}
        List<Map<String,Object>> lines=new ArrayList<>();BigDecimal listTotal=BigDecimal.ZERO;
        for(Map.Entry<UUID,Integer> entry:quantities.entrySet()){
            Map<String,Object> product=jdbc.queryForMap("select p.*,ci.title as content_title,ci.type_code as content_type " +
                    "from sale_product p join catalog_item ci on ci.id=p.catalog_item_id where p.id=? and p.active=true",entry.getKey());
            if(!"PHYSICAL".equals(product.get("delivery_type")))throw new IllegalArgumentException("Megrendeléshez csak fizikai termék választható.");
            BigDecimal printing=printingCost(product);BigDecimal listPrice=(BigDecimal)product.get("list_price");BigDecimal material=(BigDecimal)product.get("material_cost");
            Map<String,Object> line=new HashMap<>();line.put("product_id",entry.getKey());line.put("product_title",product.get("content_title"));line.put("variant_name",product.get("variant_name"));line.put("quantity",entry.getValue());line.put("list_unit_price",listPrice);line.put("material_unit_cost",material);line.put("printing_cost",printing);lines.add(line);
            listTotal=listTotal.add(listPrice.multiply(BigDecimal.valueOf(entry.getValue())));
        }
        BigDecimal agreedTotal=body.get("agreedTotal")==null?listTotal:money(body.get("agreedTotal"),"megbeszélt végösszeg",true);UUID orderId=existingId==null?UUID.randomUUID():existingId;
        if(existingId==null)jdbc.update("insert into customer_order(id,order_date,customer_name,channel,list_total,agreed_total,note,packaging_material_id,packaging_name,packaging_unit,packaging_quantity,packaging_unit_cost,packaging_total_cost) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",orderId,orderDate,customerName,channel,listTotal,agreedTotal,note,packagingId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost);
        else{
            Map<String,Object> old=jdbc.queryForMap("select status from customer_order where id=? for update",existingId);if(!"OPEN".equals(old.get("status")))throw new IllegalArgumentException("Csak nyitott megrendelés módosítható.");
            jdbc.update("update customer_order set order_date=?,customer_name=?,channel=?,list_total=?,agreed_total=?,note=?,packaging_material_id=?,packaging_name=?,packaging_unit=?,packaging_quantity=?,packaging_unit_cost=?,packaging_total_cost=?,updated_at=? where id=?",orderDate,customerName,channel,listTotal,agreedTotal,note,packagingId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost,Timestamp.from(Instant.now()),existingId);
            jdbc.update("delete from customer_order_line where order_id=?",existingId);
        }
        for(Map<String,Object> line:lines)jdbc.update("insert into customer_order_line(order_id,product_id,product_title,variant_name,quantity,list_unit_price,material_unit_cost,printing_cost) values (?,?,?,?,?,?,?,?)",orderId,line.get("product_id"),line.get("product_title"),line.get("variant_name"),line.get("quantity"),line.get("list_unit_price"),line.get("material_unit_cost"),line.get("printing_cost"));
        return loadCustomerOrder(orderId);
    }

    @PostMapping("/orders/{id}/complete")
    @Transactional
    public Map<String,Object> completeCustomerOrder(@PathVariable UUID id){
        Map<String,Object> order=jdbc.queryForMap("select * from customer_order where id=? for update",id);if(!"OPEN".equals(order.get("status")))throw new IllegalArgumentException("Csak nyitott megrendelés teljesíthető.");
        List<Map<String,Object>> lines=jdbc.queryForList("select * from customer_order_line where order_id=? order by product_id",id);List<Map<String,Object>> saleItems=new ArrayList<>();BigDecimal orderListTotal=(BigDecimal)order.get("list_total"),agreedTotal=(BigDecimal)order.get("agreed_total"),allocated=BigDecimal.ZERO;
        for(Map<String,Object> line:lines){UUID productId=(UUID)line.get("product_id");int quantity=((Number)line.get("quantity")).intValue();Map<String,Object> stock=jdbc.queryForMap("select delivery_type,stock_quantity from sale_product where id=? for update",productId);int current=((Number)stock.get("stock_quantity")).intValue();
            if(!"PHYSICAL".equals(stock.get("delivery_type"))||current<quantity)throw new IllegalArgumentException("A megrendelés még nem teljesíthető. Készítsd el és vedd készletre a hiányzó darabokat.");
            saleItems.add(Map.of("productId",productId.toString(),"quantity",quantity));
        }
        Map<String,Object> saleBody=new HashMap<>();saleBody.put("items",saleItems);saleBody.put("agreedTotal",agreedTotal);saleBody.put("saleDate",java.time.LocalDate.now().toString());saleBody.put("channel",order.get("channel"));saleBody.put("note",("Megrendelés teljesítve"+(order.get("customer_name")==null?"":" · "+order.get("customer_name"))+(order.get("note")==null?"":" · "+order.get("note"))).substring(0,Math.min(2000,("Megrendelés teljesítve"+(order.get("customer_name")==null?"":" · "+order.get("customer_name"))+(order.get("note")==null?"":" · "+order.get("note"))).length())));if(order.get("packaging_material_id")!=null)saleBody.put("packaging",Map.of("materialId",order.get("packaging_material_id").toString(),"quantity",order.get("packaging_quantity")));
        Map<String,Object> sale=saveSale(null,saleBody);UUID saleId=(UUID)sale.get("id");
        for(int index=0;index<lines.size();index++){
            Map<String,Object> line=lines.get(index);BigDecimal lineList=((BigDecimal)line.get("list_unit_price")).multiply(BigDecimal.valueOf(((Number)line.get("quantity")).intValue()));
            BigDecimal lineTotal=index==lines.size()-1?agreedTotal.subtract(allocated):orderListTotal.signum()==0?BigDecimal.ZERO:agreedTotal.multiply(lineList).divide(orderListTotal,2,RoundingMode.HALF_UP);allocated=allocated.add(lineTotal);
            jdbc.update("update sale_line set product_title=?,variant_name=?,list_unit_price=?,material_unit_cost=?,printing_cost=?,agreed_line_total=? where sale_id=? and product_id=?",line.get("product_title"),line.get("variant_name"),line.get("list_unit_price"),((BigDecimal)line.get("material_unit_cost")).add((BigDecimal)line.get("printing_cost")),line.get("printing_cost"),lineTotal,saleId,line.get("product_id"));
        }
        jdbc.update("update sale_record set list_total=? where id=?",orderListTotal,saleId);jdbc.update("update customer_order set status='FULFILLED',sale_id=?,fulfilled_at=?,updated_at=? where id=?",saleId,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()),id);
        return loadCustomerOrder(id);
    }

    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public ResponseEntity<Void> cancelCustomerOrder(@PathVariable UUID id){int updated=jdbc.update("update customer_order set status='CANCELLED',updated_at=? where id=? and status='OPEN'",Timestamp.from(Instant.now()),id);if(updated!=1)throw new IllegalArgumentException("Csak nyitott megrendelés mondható le.");return ResponseEntity.noContent().build();}

    private Map<String,Object> loadCustomerOrder(UUID id){Map<String,Object> order=jdbc.queryForMap("select * from customer_order where id=?",id);order.put("items",jdbc.queryForList("select id,product_id,product_title,variant_name,quantity,list_unit_price,material_unit_cost,printing_cost from customer_order_line where order_id=? order by product_title,variant_name",id));return order;}

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

        record Line(UUID productId, String title, String variant, int quantity, BigDecimal unitPrice, BigDecimal unitCost, BigDecimal printingCost, boolean physical) {
            BigDecimal listTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal listTotal = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Map<String, Object> product = jdbc.queryForMap("select p.*,ci.title as content_title from sale_product p join catalog_item ci on ci.id=p.catalog_item_id where p.id=? and p.active=true", entry.getKey());
            BigDecimal printCost=printingCost(product);
            BigDecimal totalCost=((BigDecimal)product.get("material_cost")).add(printCost);
            Line line = new Line(entry.getKey(), (String) product.get("content_title"), (String) product.get("variant_name"), entry.getValue(), (BigDecimal) product.get("list_price"), totalCost, printCost, "PHYSICAL".equals(product.get("delivery_type")));
            lines.add(line); listTotal = listTotal.add(line.listTotal());
        }
        UUID saleId=existingId==null?UUID.randomUUID():existingId;
        List<Map<String,Object>> restoredStock=List.of();
        if(existingId!=null) {
            restoredStock=jdbc.queryForList("select sl.product_id,sum(sl.quantity) as quantity from sale_line sl join sale_product p on p.id=sl.product_id where sl.sale_id=? and p.delivery_type='PHYSICAL' group by sl.product_id",saleId);
            restoredStock
                    .forEach(row->jdbc.update("update sale_product set stock_quantity=stock_quantity+?,updated_at=? where id=?",row.get("quantity"),Timestamp.from(Instant.now()),row.get("product_id")));
        }
        for(Line line:lines) if(line.physical()) jdbc.update("update sale_product set stock_quantity=stock_quantity-?,updated_at=? where id=? and delivery_type='PHYSICAL'",line.quantity(),Timestamp.from(Instant.now()),line.productId());
        if(existingId==null) jdbc.update("insert into sale_record(id,sale_date,channel,list_total,agreed_total,note,packaging_material_id,packaging_name,packaging_unit,packaging_quantity,packaging_unit_cost,packaging_total_cost) values (?,?,?,?,?,?,?,?,?,?,?,?)",saleId,saleDate,channel,listTotal,agreedTotal,optional(body,"note"),packagingMaterialId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost);
        else {
            jdbc.update("update sale_record set sale_date=?,channel=?,list_total=?,agreed_total=?,note=?,packaging_material_id=?,packaging_name=?,packaging_unit=?,packaging_quantity=?,packaging_unit_cost=?,packaging_total_cost=? where id=?",saleDate,channel,listTotal,agreedTotal,optional(body,"note"),packagingMaterialId,packagingName,packagingUnit,packagingQuantity,packagingUnitCost,packagingTotalCost,saleId);
            jdbc.update("delete from sale_line where sale_id=?",saleId);
        }
        for(Map<String,Object> oldLine:restoredStock) recordStockMovement((UUID)oldLine.get("product_id"),((Number)oldLine.get("quantity")).intValue(),"SALE_EDIT_RESTORE",saleId,"Eladás szerkesztése: korábbi mennyiség visszaadása");
        for(Line line:lines) if(line.physical()) recordStockMovement(line.productId(),-line.quantity(),existingId==null?"SALE":"SALE_EDIT",saleId,existingId==null?"Eladás miatti készletcsökkenés":"Eladás módosítása: új mennyiség levonása");
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

    @PutMapping("/materials/{id}/stock")
    @Transactional
    public Map<String,Object> setMaterialStock(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        BigDecimal quantity=materialStockQuantity(body.get("quantity"));
        jdbc.queryForObject("select id from material_item where id=? for update",UUID.class,id);
        jdbc.update("update material_item set stock_quantity=? where id=?",quantity,id);
        return jdbc.queryForMap("select id,stock_quantity from material_item where id=?",id);
    }

    @PostMapping("/materials/{id}/stock-adjustments")
    @Transactional
    public Map<String,Object> adjustMaterialStock(@PathVariable UUID id,@RequestBody Map<String,Object> body) {
        BigDecimal delta;
        try { delta=new BigDecimal(Objects.toString(body.get("delta"))).setScale(3,RoundingMode.HALF_UP); }
        catch(Exception ex) { throw new IllegalArgumentException("Érvénytelen alapanyag-mennyiség."); }
        if(delta.signum()==0 || delta.abs().compareTo(new BigDecimal("1000000"))>0) throw new IllegalArgumentException("A módosítás 0 és 1 000 000 közötti mennyiség legyen.");
        jdbc.queryForObject("select id from material_item where id=? for update",UUID.class,id);
        jdbc.update("update material_item set stock_quantity=greatest(stock_quantity+?,0) where id=?",delta,id);
        return jdbc.queryForMap("select id,stock_quantity from material_item where id=?",id);
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
        jdbc.update("update material_item set stock_quantity=stock_quantity+? where id=?",quantity,id);
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

    private void upsertCatalogItem(UUID id,String typeCode,String slug,String title,String summary,String description,String status,UUID previewAssetId) {
        Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("insert into catalog_item(id,type_code,slug,title,summary,description,status,preview_asset_id,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?) " +
                        "on conflict(id) do update set type_code=excluded.type_code,slug=excluded.slug,title=excluded.title,summary=excluded.summary,description=excluded.description,status=excluded.status,preview_asset_id=excluded.preview_asset_id,updated_at=excluded.updated_at",
                id,typeCode,slug,title,summary,description,status,previewAssetId,now,now);
    }

    private void saveWorksheet(UUID id, Map<String, Object> b, boolean insert) {
        String title = required(b, "title");
        UUID catalogItemId=insert?UUID.randomUUID():jdbc.queryForObject("select catalog_item_id from worksheet where id=?",UUID.class,id);
        String slug = availableCatalogSlug("WORKSHEET",Optional.ofNullable(optional(b, "slug")).orElseGet(() -> slugify(title)),catalogItemId);
        String status = status(b.get("status"));
        String paperSize = choice(b.get("paperSize"), "A4", "papírméret", Set.of("A4", "A3", "LETTER"));
        String orientation = choice(b.get("orientation"), "PORTRAIT", "tájolás", Set.of("PORTRAIT", "LANDSCAPE"));
        String colorMode = choice(b.get("colorMode"), "COLOR", "színmód", Set.of("COLOR", "GRAYSCALE"));
        String usageType = choice(b.get("usageType"), "DIGITAL", "felhasználás", Set.of("DIGITAL", "PHYSICAL", "BOTH"));
        String originType = choice(b.get("originType"), "ORIGINAL", "eredet", Set.of("ORIGINAL", "ONLINE"));
        Object previewId = uuidOrNull(b.get("previewAssetId"));
        Object[] values = {title, slug, optional(b, "summary"), optional(b, "description"), optional(b, "instructions"), status,
                paperSize, orientation, colorMode, usageType, originType, optional(b, "safetyNotes"), previewId, Timestamp.from(Instant.now()),
                "PUBLISHED".equals(status) ? Timestamp.from(Instant.now()) : null};
        upsertCatalogItem(catalogItemId,"WORKSHEET",slug,title,optional(b,"summary"),optional(b,"description"),status,(UUID)previewId);
        if (insert) jdbc.update("insert into worksheet(id,catalog_item_id,title,slug,summary,description,instructions,status,paper_size,orientation,color_mode,usage_type,origin_type,safety_notes,preview_asset_id,updated_at,published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", prepend(catalogItemId, prepend(id, values)));
        else jdbc.update("update worksheet set title=?,slug=?,summary=?,description=?,instructions=?,status=?,paper_size=?,orientation=?,color_mode=?,usage_type=?,origin_type=?,safety_notes=?,preview_asset_id=?,updated_at=?,published_at=coalesce(?,published_at) where id=?",
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
        jdbc.update("update catalog_item ci set slug=w.slug,title=w.title,summary=w.summary,description=w.description,status=w.status,preview_asset_id=w.preview_asset_id,updated_at=w.updated_at from worksheet w where w.catalog_item_id=ci.id and w.id=?",id);
        jdbc.update("delete from worksheet_term where worksheet_id=? and term_id in (select id from taxonomy_term where active=true)", id);
        saveTerms("worksheet_id", id, b.get("termIds"));
    }

    private Map<String, Object> loadWorksheet(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("select * from worksheet where id=?", id);
        row.put("pages", jdbc.queryForList("select p.*, mp.original_name as preview_name, mp.media_type as preview_media_type, fp.original_name as print_name, fp.media_type as print_media_type from worksheet_page p left join media_asset mp on mp.id=p.preview_asset_id left join media_asset fp on fp.id=p.print_asset_id where p.worksheet_id=? order by p.sort_order", id));
        row.put("terms", termsFor("worksheet_id", id));
        row.put("saleProducts",worksheetSaleProducts(id,true));
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

    private List<Map<String,Object>> worksheetSaleProducts(UUID worksheetId,boolean includeMaterials){
        List<Map<String,Object>> products=jdbc.queryForList("select p.id,p.worksheet_id,p.delivery_type,p.print_sides,p.variant_name,p.list_price,p.material_cost,p.stock_quantity,p.stock_minimum,w.preview_asset_id,coalesce(s.units_sold,0) as units_sold,coalesce(s.revenue,0) as revenue,coalesce(s.sold_material_cost,0) as sold_material_cost " +
                "from sale_product p join worksheet w on w.id=p.worksheet_id left join (select product_id,sum(quantity) as units_sold,sum(agreed_line_total) as revenue,sum(quantity*material_unit_cost+packaging_cost) as sold_material_cost from sale_line group by product_id) s on s.product_id=p.id " +
                "where p.worksheet_id=? and p.active=true order by p.variant_name",worksheetId);
        products.forEach(product->{addPrintingCost(product);if(includeMaterials)product.put("materials",productMaterials((UUID)product.get("id")));});
        return products;
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
        UUID catalogItemId=insert?UUID.randomUUID():jdbc.queryForObject("select catalog_item_id from bundle_plan where id=?",UUID.class,id);
        upsertCatalogItem(catalogItemId,"BUNDLE",slug,title,null,optional(b,"description"),status(b.get("status")),null);
        Object[] values={title,slug,optional(b,"description"),type,status(b.get("status")),Timestamp.from(Instant.now())};
        if(insert) jdbc.update("insert into bundle_plan(id,catalog_item_id,title,slug,description,bundle_type,status,updated_at) values (?,?,?,?,?,?,?,?)",prepend(catalogItemId,prepend(id,values)));
        else jdbc.update("update bundle_plan set title=?,slug=?,description=?,bundle_type=?,status=?,updated_at=? where id=?",append(values,id));
        jdbc.update("update catalog_item ci set slug=b.slug,title=b.title,description=b.description,status=b.status,updated_at=b.updated_at from bundle_plan b where b.catalog_item_id=ci.id and b.id=?",id);
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
    private static BigDecimal databaseDecimal(Object value) {
        if(value instanceof BigDecimal decimal) return decimal;
        if(value==null) return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }
    private static BigDecimal decimalQuantity(Object value) {
        BigDecimal quantity;
        try { quantity=new BigDecimal(Objects.toString(value,"" ).trim()).setScale(3,RoundingMode.UNNECESSARY); }
        catch (NumberFormatException | ArithmeticException ex) { throw new IllegalArgumentException("A mennyiség legfeljebb három tizedesjegyű pozitív szám legyen."); }
        if(quantity.signum()<=0) throw new IllegalArgumentException("A mennyiség pozitív szám legyen.");
        return quantity;
    }

    private static BigDecimal materialStockQuantity(Object value) {
        try {
            BigDecimal quantity=new BigDecimal(Objects.toString(value)).setScale(3,RoundingMode.HALF_UP);
            if(quantity.signum()<0 || quantity.compareTo(new BigDecimal("1000000"))>0) throw new IllegalArgumentException("Az alapanyag készlete 0 és 1 000 000 közötti mennyiség legyen.");
            return quantity;
        } catch(NumberFormatException ex) { throw new IllegalArgumentException("Érvénytelen alapanyag-mennyiség."); }
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

    private String availableCatalogSlug(String typeCode,String requestedSlug,UUID currentCatalogItemId) {
        String base=slugify(requestedSlug==null?"":requestedSlug);
        if(base.isBlank())base="tartalom";
        base=base.substring(0,Math.min(base.length(),200));
        String candidate=base;int suffix=2;
        while(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from catalog_item where type_code=? and slug=? and id<>?)",Boolean.class,typeCode,candidate,currentCatalogItemId))) {
            String ending="-"+suffix++;candidate=base.substring(0,Math.min(base.length(),220-ending.length()))+ending;
        }
        return candidate;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> listOfMaps(Object value) { if(!(value instanceof List<?> l)) return new ArrayList<>(); return l.stream().filter(Map.class::isInstance).map(v->new HashMap<>((Map<String,Object>)v)).collect(Collectors.toCollection(ArrayList::new)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> mapOf(Object value) { return value instanceof Map<?,?> m?new HashMap<>((Map<String,Object>)m):Map.of(); }
    private static Object[] prepend(Object first,Object[] values) { Object[] result=new Object[values.length+1]; result[0]=first; System.arraycopy(values,0,result,1,values.length); return result; }
    private static Object[] append(Object[] values,Object last) { Object[] result=Arrays.copyOf(values,values.length+1); result[values.length]=last; return result; }
    private static String encodeFilename(String filename) { return java.net.URLEncoder.encode(filename,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"); }
}

