# ApróLéptek – termék- és technikai terv

## 1. Dokumentum célja

Ez a dokumentum az ApróLéptek első digitális termékének célját, fő felhasználói folyamatait, tartalmi modelljét és technikai alapjait rögzíti. Az első fejlesztési kör középpontjában egy adminisztrációs felület és egy jól kereshető feladatlap-katalógus áll. A webshop, a szülői fiók, a játéknaptár, az ajánlórendszer és az előfizetés későbbi ütemekben valósulhat meg.

## 2. Termékcél és alapelvek

Az ApróLéptek magyar nyelvű, szakmai szemléletű játék- és feladatlap-katalógus kisgyermekes családoknak, valamint gyermekekkel foglalkozó szakembereknek.

Az induló rendszer tegye lehetővé, hogy az adminisztrátor:

- egy helyen tárolja és kezelje a feladatlapokat, képeket és nyomtatási fájlokat;
- a feladatlapokhoz nulla vagy több kivágható lapot rendeljen;
- egységes szempontok szerint kategorizáljon és keressen;
- önálló, feladatlaphoz nem kötött játékötleteket rögzítsen;
- később feladatlapokból digitális vagy fizikai csomagokat állítson össze.

Alapelvek:

- A feladatlap tartalmi objektum; az eladható termék vagy csomag külön objektum.
- A játékötlet önállóan is teljes értékű, kapcsolódó anyag nélkül is publikálható.
- A címkék és korosztályok egységes, kezelhető listákból származzanak.
- A későbbi szülői felület ugyanazt a publikált katalógust használja, de semmilyen adminisztrációs művelethez ne férjen hozzá.
- A tartalom ne ígérjen garantált fejlődési eredményt, és ne jelenjen meg diagnosztikai eszközként.

## 3. Felhasználók és jogosultságok

### Adminisztrátor – MVP

Teljes hozzáférése van az adminfelülethez. Kezelheti a feladatlapokat, kivágható lapokat, fájlokat, játékötleteket, címkéket és csomagterveket; publikálhat és archiválhat tartalmakat; később felhasználókat és üzleti beállításokat is kezelhet.

### Szerkesztő – későbbi szerepkör

Tartalmat hozhat létre és módosíthat, de nem kezelhet szerepköröket, rendszerbeállításokat vagy más adminisztratív jogosultságokat.

### Szülő / nyilvános látogató – későbbi felület

Bejelentkezés nélkül böngészheti és szűrheti a publikált tartalmakat. Fiók később szükséges lehet kedvencekhez, vásárlásokhoz vagy személyes gyűjteményekhez.

Minden védett backend műveletnél a szerver ellenőrizze a jogosultságot. A frontend útvonalak elrejtése önmagában nem jogosultságvédelem.

## 4. Információarchitektúra

### MVP adminfelület

```text
Adminisztráció
├── Áttekintő
├── Feladatlapok
│   ├── Lista és keresés
│   ├── Új feladatlap
│   └── Feladatlap részletei és szerkesztése
│       ├── Alapfeladatlap
│       └── Kivágható lapok (0..N)
├── Játékötletek
│   ├── Lista és keresés
│   └── Új / szerkesztett játékötlet
├── Csomagtervek
├── Kategóriák és címkék
│   ├── Korosztályok
│   ├── Feladattípusok
│   ├── Témakörök (külön: Állatok, Természet, stb.)
│   └── Készségterületek
└── Fiók / kijelentkezés
```

### Későbbi nyilvános oldal

```text
Főoldal
├── Mai játék
├── Játékötletek
├── Feladatlapok / katalógus
├── Webshop
├── Szülőknek segítünk
├── Pedagógusoknak
├── Jeles napok
├── Rólunk
└── Kapcsolat
```

## 5. Fő felhasználói folyamatok

### Új feladatlap rögzítése

1. Adminisztrátor belép az adminfelületre.
2. A Feladatlapok részen új rekordot hoz létre.
3. Megadja a címet, rövid leírást, korosztályt, feladattípusokat, témaköröket és készségterületeket.
4. Feltölti az alaplap előnézeti képét és nyomtatási fájlját.
5. Szükség esetén hozzáad kivágható lapokat; minden laphoz fájl, előnézet, sorrend és nyomtatási adatok adhatók meg.
6. Ellenőrzi az előnézetet és az összes oldalszámot.
7. Piszkozatként menti vagy publikálja.

### Játékötlet rögzítése

1. Adminisztrátor új játékötletet hoz létre.
2. Megadja a menetet, korosztályt, időtartamot, eszközöket, előkészületet, változatokat és biztonsági tudnivalókat.
3. Opcionálisan feladatlapot, csomagot vagy más tartalmat kapcsol hozzá.
4. Ment vagy publikál.

### Csomag összeállítása – kezdetben tervezési funkció

1. Adminisztrátor csomagot hoz létre, címmel, leírással, borítóval és csomagtípussal.
2. Kiválasztja a tartalmazott feladatlapokat.
3. Ellenőrzi, hogy minden feladatlap alaplapja és kivágható lapjai a csomag részét képezik-e.
4. Később digitális vagy fizikai termékváltozatot, árat, készletet és teljesítési adatokat adhat meg.

## 6. Tartalmi adatmodell

### Közös tartalommezők

- `id`: belső azonosító
- `slug`: későbbi nyilvános URL azonosító
- `title`, `summary`, `description`
- `status`: `DRAFT`, `REVIEW`, `PUBLISHED`, `ARCHIVED`
- közös tartalmi mezők: `age_range_ids`, `topic_ids`, `skill_area_ids`;
- feladatlap-specifikus mező: `task_type_ids`;
- `created_at`, `updated_at`, `published_at`
- `created_by`, `updated_by`

### Feladatlap

A feladatlap a pedagógiai tartalmi egység. Alaplapja mindig egy kötelező fő elem, amelyhez opcionálisan több kivágható lap kapcsolható.

Javasolt mezők:

- cím, rövid leírás, használati útmutató;
- ajánlott korosztályok, feladattípusok, témakörök és készségterületek;
- nyomtatási jellemzők: papírméret (induláskor jellemzően A4), tájolás, színes/fekete-fehér, oldalszám;
- előnézeti kép és nyomtatási fájl(ok);
- felhasználási forma: digitális, fizikai vagy mindkettőre tervezett;
- biztonsági és felügyeleti megjegyzések;
- státusz és szerzői/audit mezők.

### Kivágható lap

Külön rekordként tartozik egy feladatlaphoz. Egy feladatlapnak nulla vagy több kivágható lapja lehet. Kivágható lap nem önálló katalógustartalom az MVP-ben.

Mezők:

- `worksheet_id` – kötelező szülőkapcsolat;
- cím vagy sorszám;
- sorrend (`sort_order`);
- rövid használati megjegyzés;
- előnézeti kép és nyomtatási fájl;
- papírméret, tájolás, színesség és oldalszám.

Az adminfelület jelezze az összesített oldalszámot, például: „1 alaplap + 3 kivágható lap, összesen 4 A4-es oldal”.

### Játékötlet

Önálló tartalom, amelyhez nem szükséges feladatlap vagy termék.

Mezők: cím, leírás, ajánlott korosztályok, időtartam, benti/kinti jelleg, eszközök, előkészület, lépések, könnyített és nehezített változat, készségterületek, biztonsági tudnivalók, opcionális kapcsolódó tartalmak.

### Csomag

A csomag feladatlapokra hivatkozó összeállítás. Ne másolja le a feladatlapok tartalmát.

Mezők: cím, slug, leírás, borítókép, csomagtípus (`DIGITAL`, `PHYSICAL`, `BOTH`), státusz, elemek és darabszám, későbbi ár- és teljesítési adatok. A digitális és fizikai értékesíthetőség külön termékváltozatként legyen kezelhető.

### Kategóriák és címkék

Az admin és a későbbi nyilvános katalógus egymástól különálló, többértékű taxonómiacsoportokat használ. Egy feladatlaphoz több érték is rendelhető ugyanabból a csoportból. Így például egy állatos, kivágható elemekkel készült árnyékpárosító egyszerre kaphat korosztályt, feladattípust, témakört és készségterületet.

Kezdeti taxonómiák:

- korosztály: például 18–24 hónap, 2–3 év, 3–4 év, 4–6 év;
- feladattípus: árnyékpárosító, képpárosító, formapárosító, öltöztető, vonalvezető, válogató, képkirakó, sorozatalkotó, keresd és jelöld, vágás és ragasztás, színező, labirintus;
- témakör: **állatok** és **természet** külön választható címkék, továbbá járművek, évszakok, érzelmek, mindennapi rutinok;
- készségterület: finommotorika, nagymozgás, kommunikáció, érzékelés, gondolkodás, kreativitás, társas-érzelmi kapcsolódás;
- formátum és használat: A4, digitális, fizikai, beltéri/kültéri.

A feladattípus azt írja le, mit csinál a gyermek a lapon; a témakör azt, miről szól; a készségterület pedig azt, milyen képesség gyakorlását támogathatja. Ezek ne helyettesítsék egymást. Az „Állatok és természet” összevont címkét ne használjuk új tartalmakhoz: a két nagy témakör külön-külön legyen kereshető és szűrhető, és egy lap mindkettőhöz hozzárendelhető legyen. A későbbi alkategóriák (például háziállatok vagy növények) opcionálisan hierarchikusan bővíthetők, de az MVP-ben a címkék önálló értékek.

A kategóriákat adminból lehessen kezelni, de ne legyen szabadon gépelhető címkékből sok, egymással átfedő változat. A korábbi összevont címke kivezetésekor a meglévő kapcsolatok ne vesszenek el: az érintett tartalmakat külön állatok és/vagy természet címkékre kell átállítani, majd az összevont érték inaktiválható.

Technikai reprezentációként a közös `taxonomy_term` táblában külön `term_type` érték jelölje az `AGE`, `TASK_TYPE`, `TOPIC` és `SKILL` csoportokat. A feladatlapok és a játékötletek a megfelelő kapcsolótáblán keresztül több korosztályt, témát és készségterületet kaphatnak; a `TASK_TYPE` kizárólag feladatlaphoz rendelhető. Az API ezt szerveroldalon is validálja. Új `TASK_TYPE` csoportot Liquibase migrációval kell bevezetni; a felület ne mossa össze a külön csoportok értékeit.

## 7. Keresés és szűrés

Az MVP admin katalógusban legyen:

- szöveges keresés címre és leírásra;
- szűrés tartalomtípusra és státuszra;
- külön szűrő korosztályra, feladattípusra, témakörre és készségterületre;
- több érték kiválasztása egy szűrőcsoportban, és a szűrőcsoportok kombinálása;
- rendezés cím, létrehozás vagy módosítás dátuma szerint;
- lapozás nagyobb adatmennyiségnél.

A későbbi nyilvános kereső csak publikált tartalmat adjon vissza, és a szülők számára érthető szűrőket használjon. A backend API validálja a szűrési értékeket és a lapméretet.

## 8. Technológiai architektúra

### Kötelező stack

- Backend: Java és Spring Boot.
- Adatbázis: PostgreSQL.
- Sémamigráció: Liquibase, verziókezelt changelogokkal.
- Frontend: React, Vite és TypeScript.
- Komponenskészlet: shadcn/ui + Tailwind CSS, saját ApróLéptek téma-tokenekkel.

### Logikai felépítés

```text
React/Vite admin SPA
        │ HTTPS / REST JSON
        ▼
Spring Boot moduláris alkalmazás
  ├── auth / authorization
  ├── worksheet / cutout sheet
  ├── activity
  ├── taxonomy
  ├── media
  └── bundle planning
        │
        ├── PostgreSQL – rekordok, kapcsolatok, auditadatok
        └── fájltár – képek és nyomtatási fájlok
```

Az első verzió egy Spring Boot alkalmazás és egy React alkalmazás legyen, nem mikroszolgáltatás-rendszer. A funkcionális modulok kódon belüli határai később lehetővé teszik a bővítést.

### Fájltárolás

Az adatbázis a fájlok metaadatait és elérési kulcsait tárolja, a bináris fájlokat külön fájltár. Fejlesztéskor használható helyi könyvtár; éles környezethez objektumtár javasolt. A tárhely legyen cserélhető adapter mögött, hogy az alkalmazás üzleti logikája ne függjön egy szolgáltató saját API-jától.

Külön kezelendő:

- eredeti nyomtatási fájl;
- katalógus-előnézeti kép;
- opcionális összeállított PDF;
- fájlnév, MIME-típus, méret, oldalszám, feltöltő és feltöltési idő.

A fájlfeltöltésnél korlátozni kell az engedélyezett formátumokat és méreteket, és ellenőrizni kell a tényleges MIME-típust. A digitális vásárlásra szánt fájlok később ne legyenek nyilvánosan elérhető URL-en.

### API-elvek

- verziózott API-előtag, például `/api/v1`;
- JSON alapú REST végpontok;
- validált kérések és egységes hibaformátum;
- lapozás és rendezés a listavégpontokon;
- admin végpontok szerepkör-ellenőrzése szerveroldalon;
- nyilvános, publikált katalógusvégpontok elkülönítése az admin műveletektől.

## 9. Adatbázis és migrációs szabályok

- Minden séma- és kezdeti referenciaadat-változás Liquibase changelogban legyen.
- Már alkalmazott changesetet ne módosítsunk; új módosítás új changeset legyen.
- Az alkalmazás induláskor ne végezzen automatikus ORM-sémamódosítást; a séma forrása a Liquibase.
- Fejlesztés alatt az ORM validálhatja, hogy az entitások megfelelnek a migrált sémának.
- Kulcsok, idegen kulcsok, egyediségi szabályok és indexek legyenek explicit módon megtervezve.
- A keresési mezőkön és kapcsolótáblák idegen kulcsain legyenek megfelelő indexek.
- Teszt- és fejlesztői adatbázis induló adatai legyenek megkülönböztetve az éles tartalomtól.

## 10. Frontend és vizuális rendszer

### Adminfelület

Munkára optimalizált, letisztult kezelőfelület: oldalsó navigáció, kereshető táblázatok, jól tagolt szerkesztőűrlapok, előnézeti panel és egyértelmű piszkozat/publikálás állapotok. A shadcn/ui biztosítsa az alapkomponenseket, a márka színei és tipográfiája saját design tokenként legyen beállítva.

### Nyilvános márkafelület később

Természetközeli, meleg tónusok, visszafogott illusztrációk, olvasható magyar tipográfia, mobilközpontú kártyák. A nyilvános kereső használja ugyanazt a tartalmi API-t, de külön információs architektúrával és látogatói élménnyel.

### SEO

Vite-os adminfelületnél a SPA megfelelő. A későbbi nyilvános, keresőforgalomra építő oldalakhoz külön kell választani a renderelési döntést: előrenderelés vagy szerveroldali megjelenítés. Ezt a nyilvános tartalmi oldal fejlesztése előtt kell rögzíteni.

## 11. MVP hatóköre

### Benne van

- adminisztrátori bejelentkezés és szerepkör;
- admin áttekintő és tartalomlisták;
- feladatlap létrehozása, szerkesztése, keresése és archiválása;
- egy feladatlaphoz nulla vagy több kivágható lap;
- képek és nyomtatási fájlok feltöltése és előnézete;
- A4-es nyomtatási adatok rögzítése;
- korosztályok, feladattípusok, témakörök és készségterületek kezelése;
- önálló játékötlet rögzítése és opcionális kapcsolatai;
- piszkozat és publikált státusz;
- csomagterv összeállítása fizetés és rendelés nélkül;
- React/Vite admin és Spring Boot/PostgreSQL/Liquibase backend.

### Nincs benne az első ütemben

- vásárlói regisztráció és szülői szerepkör;
- kosár, fizetés, számlázás és szállítás;
- védett digitális vásárlási letöltés;
- készletkezelés és rendelésadminisztráció;
- előfizetéses klub;
- gyermekprofil, kedvencek és személyre szabott ajánló;
- teljes nyilvános marketingoldal, hacsak külön induló célként nem kerül jóváhagyásra.

## 12. Későbbi bővítési fázisok

1. **Katalógus publikálása:** publikus feladatlap- és játékötlet-kereső.
2. **Webshop:** digitális és fizikai termékváltozatok, rendelések és teljesítés.
3. **Tartalmi bővítés:** játéknaptár, jelesnapi gyűjtemények, cikkek és heti terv.
4. **Személyes funkciók:** fiók, kedvencek, játékajánló, minimális profiladatokkal.
5. **Játékklub:** előfizetés, tagsági tartalmak és hozzáférés-kezelés.

## 13. Biztonság, adatvédelem és minőség

- Csak minimálisan szükséges személyes adatot gyűjtsünk.
- Admin belépésnél biztonságos jelszókezelés és szerveroldali jogosultságvizsgálat kell.
- Fájloknál méret-, típus- és hozzáférés-ellenőrzés szükséges.
- Legyen rendszeres adatbázis- és fájlmentés, valamint visszaállítási eljárás.
- Admin műveleteknél legalább létrehozó/módosító és időbélyeg auditadat rögzüljön.
- A gyermekek fejlődéséről szóló leírások legyenek ítélkezésmentesek, ne ígérjenek garantált eredményt.
- A fizikai termékek biztonsági és jogi megfelelősége a tényleges forgalmazás előtt külön ellenőrzendő.

## 14. Elfogadási kritériumok az első használható kiadáshoz

- Adminisztrátor be tud jelentkezni, és illetéktelen felhasználó nem érheti el az admin API-kat.
- Feladatlapot lehet létrehozni alaplappal és tetszőleges számú kivágható lappal.
- Minden oldalhoz lehet képet és nyomtatási fájlt rendelni, előnézetet látni és sorrendet állítani.
- A rendszer megmutatja az összesített oldalszámot.
- A feladatlapok külön kereshetők és szűrhetők korosztály, feladattípus, témakör és készségterület szerint.
- Az „Állatok” és „Természet” témakörök külön címkék; egy tartalom mindkettőhöz rendelhető.
- Játékötlet feladatlapkapcsolat nélkül is létrehozható és menthető.
- Csomagterv több feladatlapot tud összefogni, de még nem indít rendelést.
- A Liquibase migrációkból üres adatbázis felépíthető.
- Fájlok és rekordok mentése, valamint tesztkörnyezeti visszaállítása dokumentált.

## 15. Hivatalos technikai hivatkozások

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [PostgreSQL dokumentáció](https://www.postgresql.org/docs/current/)
- [Liquibase changelog útmutató](https://docs.liquibase.com/community/user-guide-5-0/what-is-a-changelog)
- [React – Quick Start](https://react.dev/learn)
- [Vite – Getting Started](https://vite.dev/guide/)
- [shadcn/ui – Vite telepítés](https://ui.shadcn.com/docs/installation/vite)
- [shadcn/ui – témázás](https://ui.shadcn.com/docs/theming)

Az implementáció megkezdésekor a támogatott, egymással kompatibilis stabil verziókat kell kiválasztani és a projektben rögzíteni.
