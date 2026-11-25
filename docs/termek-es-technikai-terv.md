# ApróLéptek – termék- és technikai terv

## 1. Dokumentum célja

Ez a dokumentum az ApróLéptek adminisztrációs és szülőknek/nevelőknek szóló ingyenes tartalmi felületének célját, folyamatait és technikai alapjait rögzíti. Az admin felület a tartalmak szerkesztésére szolgál; a szülői felület a publikált fejlesztő játékokat mutatja be. Az első változatban nincs előfizetés, fizetés, vásárlási folyamat vagy szülői fiók. A kapcsolódó feladatlap a játék részeként jelenik meg, nem önálló ajánlatként.

## 2. Termékcél és alapelvek

Az ApróLéptek magyar nyelvű, szakmai szemléletű játék- és feladatlap-katalógus kisgyermekes családoknak, valamint gyermekekkel foglalkozó szakembereknek.

Az induló rendszer tegye lehetővé, hogy az adminisztrátor:

- egy helyen tárolja és kezelje a feladatlapokat, képeket és nyomtatási fájlokat;
- a feladatlapokhoz nulla vagy több kivágható lapot rendeljen;
- egységes szempontok szerint kategorizáljon és keressen;
- önálló, feladatlaphoz nem kötött játékötleteket rögzítsen;
- később feladatlapokból digitális vagy fizikai csomagokat állítson össze.

### Az első változat tartalmi alapelvei

- A publikált fejlesztő játékok bejelentkezés nélkül olvashatók.
- A játékhoz kapcsolódó feladatlap nem önálló nyilvános termék; a játék részleteinél, a játék használatát segítő anyagként jelenhet meg.
- A kapcsolódó feladatlap kötelező vagy kiegészítő lehet.
- Az admin belső pénzügyi nyilvántartása és korábbi eladásai ettől függetlenül működnek, azok a szülői oldalon nem jelennek meg.
- Előfizetés, szülői fiók, vásárlás, játékcsomag-ajánlat és fizetési folyamat nincs az első fejlesztési körben. Csak külön későbbi döntés után kerülhet vissza a tervbe.

Alapelvek:

- A feladatlap tartalmi objektum; az eladható termék vagy csomag külön objektum.
- A szülői oldalon megjelenő fejlesztő játék önálló tartalmi oldal, amelyhez nulla vagy több feladatlap kapcsolódhat. Ha a feladatlap szükséges, az a játék hozzáférésének része.
- A címkék és korosztályok egységes, kezelhető listákból származzanak.
- Az admin és a szülői felület ugyanazokra a publikált tartalmakra épül; a szülői felület csak a publikált játékokat listázza.
- A tartalom ne ígérjen garantált fejlődési eredményt, és ne jelenjen meg diagnosztikai eszközként.

## 3. Felhasználók és jogosultságok

### Adminisztrátor

Teljes hozzáférése van az adminfelülethez. Kezelheti a fejlesztő játékokat, feladatlapokat, képeket és fájlokat, kategóriákat, belső termékeket, készletet és pénzügyi nyilvántartást. A tartalmakat piszkozatból ellenőrzésre, majd publikálásra állíthatja. Szülői fiókokat és előfizetéseket az első változatban nem kezel.

### Szerkesztő – későbbi szerepkör

Tartalmat hozhat létre és módosíthat, de nem kezelhet szerepköröket, rendszerbeállításokat vagy más adminisztratív jogosultságokat.

### Szülő / nevelő – ügyfélfelület

Bejelentkezés nélkül böngészheti a publikált fejlesztő játékokat és a hozzájuk kapcsolt, szabadon elérhető segédanyagokat. Az első változat nem tartalmaz szülői fiókot.

### Szülői előnézet – admin funkció

Az admin a publikálás előtt a szülői oldalon megjelenő játékoldalt előnézheti. A piszkozat előnézete nem publikálja a tartalmat.

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
├── Fejlesztő játékoldalak
│   ├── Lista és keresés
│   └── Új / szerkesztett játékoldal
├── Tartalmak
│   ├── Feladatlapok
│   ├── Kártyacsomagok
│   ├── Fizikai játékok és bővíthető játékelemek
│   └── Játékcsomagok (játék + kapcsolódó anyagok)
├── Pénzügy és önköltség
│   ├── Eladások (több termék egy megállapodott végösszeggel)
│   └── Bevétel, kiadás és népszerűség áttekintő
├── Kategóriák és címkék
│   ├── Korosztályok
│   ├── Feladattípusok
│   ├── Témakörök (külön: Állatok, Természet, stb.)
│   └── Készségterületek
└── Admin fiók / kijelentkezés
```

### Szülői / ügyfélfelület

```text
Főoldal
├── Játékok böngészése
│   ├── Nagymozgás és testtudat
│   │   └── Egyensúly és stabil járás
│   └── további fejlesztési témák
├── Játék részletes oldala
│   ├── kor, játékidő, helyszín, eszközigény
│   ├── játékmenet, változatok és biztonság
│   └── a játékhoz tartozó feladatlapok
└── Segítség / kapcsolat
```

Feladatlapnak nincs külön nyilvános katalógusoldala. A kapcsolódó játék részletei között, annak használatát segítő anyagként jelenik meg.

A nyilvános játékoldal a címet, borítóképet, ajánlót, korosztályt, időtartamot, helyszínt, eszközigényt, fejlesztési területeket és teljes játékmenetet mutatja. Minden publikált játék és kapcsolódó segédanyaga szabadon elérhető.

## 5. Fő felhasználói folyamatok

### Új feladatlap rögzítése

1. Adminisztrátor belép az adminfelületre.
2. A Feladatlapok részen új rekordot hoz létre.
3. Megadja a címet, rövid leírást, korosztályt, feladattípusokat, témaköröket és készségterületeket.
4. Feltölti az alaplap előnézeti képét és nyomtatási fájlját.
5. Szükség esetén hozzáad kivágható lapokat; minden laphoz fájl, előnézet, sorrend és nyomtatási adatok adhatók meg.
6. Ellenőrzi az előnézetet és az összes oldalszámot.
7. Piszkozatként menti vagy publikálja.

### Fejlesztő játékoldal létrehozása

1. Az admin kiválasztja a témakört és alcsoportot, például „Nagymozgás és testtudat → Egyensúly és stabil járás”.
2. Megadja a címet, egységes borítóképet, rövid bevezetőt, ajánlott kort, játékidőt, helyszínt és eszközigényt.
3. Kitölti a fejlesztési területeket, a szükséges eszközöket, a lépésenkénti játékmenetet, a könnyítéseket, nehezítéseket, játékos változatot, biztonsági részt és szülői/nevelői tippet.
4. Opcionálisan kapcsol hozzá feladatlapokat, mindegyiknél jelezve, hogy szükséges vagy kiegészítő.
5. A szülői előnézetben ellenőrzi a kártyát és a részletes oldalt, majd publikálja.

### Játékcsomag összeállítása – későbbre halasztva

1. Az admin teljes játékélményt tartalmazó játékcsomagot hoz létre, saját címmel, leírással és egységes borítóképpel.
2. A csomaghoz fejlesztő játékokat és az azokhoz szükséges vagy kiegészítő feladatlapokat/segédanyagokat kapcsol.
3. Ellenőrzi a szülői előnézetben, hogy a csomag önmagában érthető és használható-e.
4. A kereskedelmi ajánlat és annak hozzáférése csak későbbi üzleti döntés után kerül tervezésbe.

### Eladható termék és eladás rögzítése – saját értékesítés MVP

Ez a folyamat az admin belső, kézi értékesítési és önköltség-nyilvántartása. Elkülönül az ingyenes szülői katalógustól, és a korábbi admin eladásokat kezeli.

1. Bármely katalógustartalomhoz eladható termékváltozat hozható létre, például digitális vagy fizikai kivitelben. A tartalom típusa nem változtatja meg az eladási folyamatot.
2. A termékváltozathoz egyszer megadható a listaár és az egy példányra jutó anyagköltség. Fizikai változatnál a rendszer az adminban beállított nyomtatott oldalankénti tintaköltséget is hozzáadja; termékenként választható az egy- vagy kétoldalas nyomtatás, az oldalszámból pedig automatikusan számolódik a lapköltség.
3. Egy eladás rögzítésekor több termék kiválasztható, mennyiséggel együtt. A rendszer összesíti a listaárakat.
4. A vevővel egyeztetett végösszeget egyszer kell megadni az egész összeállításra. Nincs automatikus darabszám alapú kedvezményszabály.
5. A rendszer megőrzi a listaárak összegét, az elfogadott végösszeget és az eladáskori termékárakat. Az egyeztetési különbözetet arányosan szétosztja a tételek között a termékenkénti bevételi és fedezeti statisztikához.
6. Az értékesítési csatorna (induláskor például Vinted), dátum és opcionális megjegyzés rögzíthető.
7. Kimutatás készül az eladott darabszámról, az eladások gyakoriságáról, a bevételről és a becsült gyártási költség (anyag és nyomtatás) utáni fedezetről termékenként és időszakonként.

Az admin külön, kézzel rögzíthető vevői megrendeléseket is kezel. A megrendelés nem eladás, amíg nem teljesítették: a nyitott rendelések soronként mutatják, hogy a legrégebbi rendeléstől számított készletelosztás alapján mennyit fedez a készlet és mennyit kell még gyártani; a nyitott lista ugyanezt a legrégebbi rendeléstől kezdve mutatja. A nyitott, teljesített és lemondott rendelések külön füleken visszakereshetők. Minden nyitott terméksor külön készre jelölhető. A rendelés űrlapja és kártyája mutatja a termékek, a nyomtatás és a csomagolás becsült összköltségét, valamint a várható nyereséget. Szerkesztéskor a változatlan vagy csökkentett mennyiségű, már készre jelölt sor jelölése megmarad; a mennyiség növelésekor az érintett sor ismét készre jelölendő. A teljesítéskor a hiányzó, legyártott darabok készletmozgásként bekerülnek, majd a normál eladási folyamat rögzíti az értékesítést és levonja a készletet. A rendeléskor rögzített csomagolóanyag költsége átkerül az eladásba. Ez a belső rendeléskövetés nem végez piactéri importot, fizetést vagy webshopos rendeléskezelést.

Az anyagok beszerzési naplója mennyiséget és teljes árat tárol; az egységár a beszerzésekből súlyozott átlagként számolódik. Az egyszeri eszközkiadások külön kategóriában jelennek meg. A termékváltozathoz anyaglista rendelhető mennyiségekkel; az anyagköltség az anyagok súlyozott egységárából automatikusan számolódik. A fizikai termékváltozat gyártási költségébe a nyomtatott oldalanként beállított tintaköltség is beleszámít. Egyoldalas módban egy papírlap egy nyomtatott felület; kétoldalas módban egy lap két nyomtatott felület, és mindkettő után felszámítódik az oldalankénti díj. A fizikai termékváltozat készlete, minimumkészlete és előkészítési tételei követhetők; eladás készletcsökkenést rögzít. Új beszerzés az érintett termékváltozatok aktuális költségét frissíti. A múltbeli eladás pillanatkép: a későbbi listaár-, anyag- és nyomtatásidíj-változtatás nem módosítja.

### Bővíthető katalógus- és termékmodell

A katalógustartalom, az eladható termék, a termékváltozat és az eladási sor külön fogalom:

- **Katalógustartalom:** szerkeszthető, képekkel és fájlokkal rendelkező szakmai vagy játékos tartalom, például feladatlap, kártyacsomag vagy játék.
- **Tartalomtípus:** a tartalom saját adatlapját és szerkesztőjét határozza meg. Új típus hozzáadása ne igényelje az eladás, pénzügy vagy készlet működésének átírását.
- **Eladható termék:** egy katalógustartalom értékesítésre szánt egysége.
- **Termékváltozat:** az eladható kivitel, például digitális fájl, nyomtatott kártyacsomag vagy laminált feladatlap. Ehhez tartozik az ár, gyártási recept, nyomtatási beállítás és készlet.
- **Eladás:** egy vagy több termékváltozatból álló tranzakció, amely a korábban rögzített ár- és költségpillanatképeket használja.

Ez a belső admin pénzügyi és készletfogalma. Elkülönül a jelenlegi, ingyenesen böngészhető nyilvános tartalmi katalógustól. Feladatlap önálló nyilvános SKU-t és vásárlási gombot nem kap; a játékhoz kapcsolódó feladatlap a játék használatát segítő anyagként jelenik meg.

Az adatmodell javasolt alapja egy közös `catalog_item` rekord stabil azonosítóval, címmel, státusszal, borító/előnézeti médiával és típusazonosítóval. A `catalog_type` bővíthető törzsadatként sorolja fel a típusokat; új tartalomtípushoz nem kell enumot vagy eladási táblát módosítani. A típus saját adatai külön, a `catalog_item.id`-hez kapcsolódó részlettáblákban legyenek. A közös médiakapcsolatok és tartalomkapcsolatok ne kerüljenek típusonként duplikálásra.

A katalógusalapot a `020-extensible-catalog-foundation` vezette be. Erre épül a kártyacsomagok adatmodellje és szerkesztője, valamint a `023-games-and-elements` játék- és játékelem-modulja. A játékcsaládok és elemkategóriák törzsadatként bővíthetők; a játék és a külön képpel kezelhető, kompatibilis elemek sok-sok kapcsolattal állíthatók össze. A játékok, elemek és kártyacsomagok külön termékváltozattal kerülnek a közös eladási, költség- és készletfolyamatba. A felületi végigellenőrzés és a kártyalapok több csomag közötti önálló újrafelhasználása még nyitott feladat.

A feladatlapok és csomagtervek meglévő azonosítói, fájljai, termékei, eladásai és készletadatai migrációval kerüljenek az új alapmodellbe. Minden termékváltozat a `catalog_item`-re hivatkozzon; az eladás, gyártási recept, nyomtatási költség, késztermékkészlet és előkészítési tétel továbbra is ezt a közös termékváltozatot használja. Tartalomtípus-specifikus mező csak a saját tartalomtáblába kerülhet. A már alkalmazott Liquibase changeseteket nem módosítjuk; az átállás új, visszaellenőrizhető migrációval történik.

#### Kártyacsomag

Egy kártyacsomag önálló katalógustartalom. Saját borítóképe, opcionális közös hátoldala, leírása/játékszabálya és rendezett kártyalap-listája van. A kártyalapok külön médiatartalmak, nem külön eladási termékek. Egy vagy több fizikai/digitális termékváltozat kapcsolható a csomaghoz.

A fizikai kártyacsomag ugyanazt a gyártási rendszert használja, mint a fizikai feladatlap: anyaglista, nyomtatási díj, egy- vagy kétoldalas nyomtatás, laminálás és opcionális tépőzár, előkészítési tételek, késztermék-készlet és eladás. A gyártási mennyiség számításához rögzíteni kell a kártyák nyomtatási elrendezését (például hány kártya fér egy oldalra); a rendszer ne feltételezzen automatikusan egy kártyát oldalanként.

A kártyacsomag-fejlesztés külön `card_deck` és `card_deck_card` kapcsolótáblát, admin listát/szerkesztőt, borító- és hátoldal-képkezelést, rendezhető kártyalapokat, játékszabály mezőt és oldalankénti kártyaszámot ad. A kártyalap saját `CARD_FACE` katalógustartalom, így több csomag is hivatkozhat ugyanarra a lapra. A kártyacsomag digitális vagy fizikai termékváltozata a közös eladási és készletmodellhez kapcsolódik; a fizikai nyomtatási költség a lapok számából, oldalbeállításból és a hátoldal meglétéből számolódik. A `024-reusable-card-faces` változtatás a kártyalapokat külön katalógustartalommá emeli és csomagok között megoszthatóvá teszi. A korábbi lapazonosítók és fájlképek megmaradnak a migráció során; egy mentett csomagnál a meglévő kártyalap kiválasztható.

#### Bővíthető játékok és játékelemek

Ez a meglévő modul a fizikai, összeállítható játékokra és újrahasználható elemeikre szolgál (például baba, ruha, bolti kellék). A szülőknek szóló, szöveges fejlesztő játékoldal ettől külön tartalomtípus. Az adminban és a nyilvános felületen következetes elnevezést kell használni, hogy a „játékötlet”, „fizikai játék” és „eladható játékcsomag” ne keveredjen.

Egy játék (például öltöztetős vagy boltos-pénztáros játék) önálló katalógustartalom. A játék saját részlettáblája hivatkozik az újrafelhasználható játékelemekre, például babákra, ruhákra, kiegészítőkre, zöldségekre és gyümölcsökre. Az elem kategóriája és kompatibilitása adatokkal kezelendő, ne új adatbázis-oszlopokkal. Egy elem több játékban vagy csomagban is felhasználható. A játék elemei önmagukban csak akkor kapnak eladható termékváltozatot, ha külön is értékesítjük őket; a játékcsomag összetétele nem keveredik össze az eladási sorokkal.

A játékcsalád és az elemtípusok szerkeszthetők az adminisztrációban. Játékcsomaghoz kép, leírás/szabály és tetszőleges játékcsalád rendelhető; az összeállításhoz több játékelem is hozzáadható mennyiséggel. Játékelemnek kategória, kép, nyomtatási elrendezés és kompatibilis elemek állíthatók be. Játék vagy önálló elem eladása külön termékváltozat létrehozásával történik, így a gyártási recept és ár nem keveredik össze a tartalomjegyzékkel.

#### Közös gyártási és készletfolyamat

- Gyártási recept termékváltozatonként: fogyóanyag és mennyiség, manuális költségelem szükség esetén, nyomtatott oldalak és oldalankénti díj.
- Előkészítési követelmények termékváltozatonként: nyomtatás, laminálás, tépőzár; a nem releváns lépés kikapcsolható. Az előkészítési tételek száma és állapota követi a meglévő készletfolyamatot.
- Késztermék-készlet és minimumkészlet az eladható fizikai termékváltozaton van, nem a tartalom egyes képein vagy lapjain.
- Eladáskor a fizikai termékváltozat készlete csökken; korrekció és sztornó visszaállítja a készletet a meglévő szabályok szerint.
- Digitális termékváltozatra nyomtatási/laminálási előkészítés és fizikai készlet nem vonatkozik.
- A kártyacsomag és a játék termékváltozatai ugyanazokat a költség-, eladási-, készlet- és előkészítési szolgáltatásokat használják, mint a feladatlapok.

Az elrendezés, a csomagolás, a kártyák és játékelemek újrafelhasználása tartalmi kapcsolatokban jelenik meg. Új terméktípus hozzáadásakor csak a típusregisztrációt, saját részlettáblát/API-t és szerkesztőnézetet kell hozzáadni, majd a közös termékváltozatot a katalógustartalomhoz kapcsolni.

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

### Fejlesztő játék / játékoldal

Önálló, a szülő vagy nevelő számára olvasható játékoldal; nem feladatlap és nem azonos a fizikai játék/elemkészlet admin moduljával. A példaoldal címe: „Sétálj a vonalon! – Játékos egyensúlyfejlesztés”. A játék akkor is érthető és használható legyen, ha nem tartozik hozzá feladatlap. Ha tartozik, a kapcsolata legyen kötelező vagy kiegészítő; a kötelező feladatlap a játékcsomag hozzáférésébe tartozik.

Mezők és a szülői oldalon megjelenő sorrendjük:

1. Egységes borítókép, cím és rövid kedvcsináló.
2. Négy kiemelt adat: ajánlott életkor (például 18 hónaptól), játékidő-tartomány (5–10 perc), helyszín (beltér/kültér), eszközigény (minimális/közepes/sok).
3. „Mit fejleszt?” készségterületek, mindegyikhez rövid, közérthető magyarázattal.
4. Szükséges eszközök és előkészület.
5. Számozott játékmenet lépések.
6. Könnyítési és nehezítési lehetőségek.
7. Játékos történet vagy alternatív változat.
8. Biztonsági tudnivalók és szülőknek/nevelőknek szóló tipp.
9. Kapcsolódó feladatlapok, ha vannak; jelezni kell, hogy a játékhoz szükségesek-e vagy csak kiegészítők.

Az áttekintő kártya és a részletes oldal ugyanazt a borítóképet és arculati elemeket használja. Az áttekintőn a cím, rövid leírás, életkor, időtartam és fejlesztési területek legyenek gyorsan áttekinthetők.

**Megvalósítási állapot (2026-11):** Az admin meglévő játékötlet-adatlapja a szülőknek szóló fejlesztő játékok adatlapjává bővült. A 028-as Liquibase-migráció hozzáadta a borítóképet, az ajánlott életkort, a játékidő alsó/felső határát, az eszközigény szintjét, a játékos történetet és a szülői/nevelői tippet; bevezette a témakörök szülő–alcsoport kapcsolatát és az első nagymozgásos kategóriákat; valamint kötelező/kiegészítő típussal összeköthetővé tette a játékokat a feladatlapokkal. Az öt induló fejlesztési területhez közérthető rövid leírás került. A 029-es migráció az admin adatlapban szerkeszthető „Sétálj a vonalon!” mintajátékot piszkozatként betölti. Elkészült a `/szulok` ingyenesen böngészhető katalógus és a reszponzív játékoldal. A publikus API kizárólag publikált játékokat ad vissza, teljes leírással. A borítókép még feltöltendő. Előfizetés, vásárlás, szülői fiók és jogosultságkezelés egyelőre nincs a fejlesztési körben. A korábban hozzáadott 030-as hozzáférési adatbázismező és szimulált hozzáférés-választó nem része az aktuális felhasználói folyamatnak.

### Későbbre halasztva: játékcsomag-ajánlat

**Későbbi lehetőség, most nem része a fejlesztésnek:** a játékcsomag kerek, teljes élményt adó összeállítás lehet, nem önálló feladatlap. Tartalmazhat fejlesztő játékokat, a hozzájuk szükséges feladatlapokat, nyomtatható segédleteket, kártyákat és opcionálisan fizikai terméket. Jelenleg nincs nyilvános csomagajánlat, ár vagy vásárlási folyamat.

Egy későbbi tervezési körben vizsgálható mezők: cím, leírás, borítókép, csomagba rendezett játékok és segédanyagok. Hozzáférési típus, ár, vásárlás és teljesítés most nincs tervezve. A belső admin „Csomagterv” és a történeti pénzügyi adatok ettől függetlenek.

### Kereskedelmi funkciók: határozatlan időre elhalasztva

- A jelenlegi szülői oldal minden publikált játékot teljes tartalommal, ingyenesen mutat be.
- Szülői fiók, előfizetés, egyszeri digitális vásárlás, fizetési szolgáltató, számlázás és hozzáférési jogosultság nincs a mostani hatókörben.
- Ezeket csak külön termékdöntés után tervezzük újra; jelenleg sem admin-beállítás, sem szülői felületi ígéret nem épülhet rájuk.

### Kategóriák és címkék

Az admin és a későbbi nyilvános katalógus egymástól különálló, többértékű taxonómiacsoportokat használ. Egy feladatlaphoz több érték is rendelhető ugyanabból a csoportból. Így például egy állatos, kivágható elemekkel készült árnyékpárosító egyszerre kaphat korosztályt, feladattípust, témakört és készségterületet.

Kezdeti taxonómiák:

- korosztály: például 18–24 hónap, 2–3 év, 3–4 év, 4–6 év;
- feladattípus: árnyékpárosító, képpárosító, formapárosító, öltöztető, vonalvezető, válogató, képkirakó, sorozatalkotó, keresd és jelöld, vágás és ragasztás, színező, labirintus;
- témakör: **állatok** és **természet** külön választható címkék, továbbá járművek, évszakok, érzelmek, mindennapi rutinok;
- készségterület: finommotorika, nagymozgás, kommunikáció, érzékelés, gondolkodás, kreativitás, társas-érzelmi kapcsolódás;
- formátum és használat: A4, digitális, fizikai, beltéri/kültéri.

A feladattípus azt írja le, mit csinál a gyermek a lapon; a témakör azt, miről szól; a készségterület pedig azt, milyen képesség gyakorlását támogathatja. Ezek ne helyettesítsék egymást. Az „Állatok és természet” összevont címkét ne használjuk új tartalmakhoz: a két nagy témakör külön-külön legyen kereshető és szűrhető, és egy lap mindkettőhöz hozzárendelhető legyen. A nyilvános játékböngészőben a témakörök legalább egy szülő–alcsoport szintig rendezhetők. Induló ág: **Nagymozgás és testtudat → Egyensúly és stabil járás**. Az admin később további alcsoportokat is felvehet.

A fejlesztő játékok ajánlott életkora lehet nyitott korhatárú, például „18 hónaptól”; ezért a kötött korosztály címkéken túl az oldalon pontos, közérthető ajánlásnak is meg kell jelennie. Az induló készségterületek között szerepeljen az egyensúly, mozgáskoordináció, testtudat, figyelem és mozgásos önbizalom. A készségcímkéhez rövid magyarázó szöveg kapcsolható, hogy a szülő ne csak szakkifejezést lásson.

A kategóriákat adminból lehessen kezelni, de ne legyen szabadon gépelhető címkékből sok, egymással átfedő változat. A feladatlapok témakörei és készségcímkéi külön taxonómiát alkotnak a fejlesztő játékok fő területeitől, altípusaitól és készségterületeitől. A játéktartalom admin gyűjteménye fő terület → altípus szerkezetben, lenyitható csoportokkal és keresővel jelenik meg. A nyilvános katalógus az aktív kategóriafát a tartalom feltöltöttségétől függetlenül mutatja; a játékok cím, ajánló és kategória alapján kereshetők.

Technikai reprezentációként a közös `taxonomy_term` táblában a feladatlapok az `AGE`, `TASK_TYPE`, `TOPIC`, `SKILL`, a fejlesztő játékok pedig az `ACTIVITY_TOPIC` és `ACTIVITY_SKILL` típusokat használják. A játék témakörök ugyanúgy legfelső szintű fő területekből és közvetlen altípusokból állnak; a szülő és gyermek mindig azonos taxonómiatípusú. A 034-es migráció külön játék-kategóriákat készít a meglévő játékcímkékből, és az aktivitáskapcsolatokat átvezeti úgy, hogy a feladatlapokon használt régi címkék és kapcsolataik megmaradjanak. A 035-ös migráció a Finommotorika 10 alkategóriáját és 100 piszkozat játékötletét tölti fel, 18–36 hónapos célkorosztály-jelöléssel. A 036-os migráció az Érzékelés és észlelés 10 altípusát, további 100 piszkozat játékot és a 18 hónapos–6 éves korosztály-jelölést adja hozzá; a propriocepció és a vesztibuláris érzékelés kategórialeírása szerkeszthető és a szülői katalógusban megjeleníthető. A 038-as migráció a Gondolkodás és kognitív fejlődés 10 altípusát és 100 altípus-kapcsolatát, életkori ajánlással tölti fel; a 039-es a Figyelem, emlékezet és végrehajtó működések 10 altípusát és 100 játékötletét adja hozzá. A 040-es migráció a Szociális készségek 10 altípusát és 100 játékötletét veszi fel; ezek életkora nincs előre meghatározva. A 041-es migráció az Érzelmi fejlődés és önszabályozás 10 altípusát és 100 játékötletét hozza létre 18–36 hónapos korosztály-jelöléssel. A 043-as migráció az Önállóság és mindennapi életvezetés kezdeti 8 altípusát és 80 játékötletét, a 044-es pedig további 2 altípusát és 20 játékötletét rögzíti, életkori besorolás nélkül; a biztonsági ötletek leírása kizárja valódi veszélyes tárgyak használatát. A 045-ös migráció Matematikai és térbeli alapkészségek címmel 10 alkategóriát és 100 játékötletet, valamint szerkeszthető fő- és alkategória-leírásokat ad hozzá, életkori besorolás nélkül. A 046-os migráció a Kreativitás, képzelet és művészeti nevelés 8 alkategóriáját és 80 játékötletét rögzíti a megadott korosztályokkal. A 047-es migráció a Környezeti tapasztalatszerzés és felfedezés 10 alkategóriáját és 100 játékötletét tölti fel 18 hónapos–6 éves korosztály-jelöléssel, a veszélyfelismerési játékok biztonságos feldolgozására vonatkozó szerkeszthető leírással. A fő területek és – ahol rendelkezésre áll – az altípusok leírása a szülői nézetben külön lenyitható. A fejlesztőjáték-admin kezdetben csak azonosítókat, címeket, státuszokat és kategóriakapcsolatokat tölt le, a teljes játékadatokat pedig csak a lenyitott fő kategóriához kéri be. Az API szerveroldalon tiltja a két taxonómia összekeverését.

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
  ├── catalog item / content type
  ├── card deck / game extensions
  ├── activity
  ├── taxonomy
  ├── media
  ├── bundle planning
  └── self-sales / product costing
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

### Nyilvános márkafelület

Természetközeli, meleg tónusok, visszafogott illusztrációk, olvasható magyar tipográfia, mobilközpontú kártyák. A szülői oldal a szabadon elérhető fejlesztő játékokat teszi felfedezhetővé. A kapcsolódó feladatlap a játék használatát segítő anyag. Az admin és a nyilvános felület külön nézet, közös publikált tartalommal. Az adminnak külön „Megtekintés szülőként” előnézete van.

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
- eladható termékváltozat összekapcsolása katalógustartalomhoz (a bővíthető modell részletes terve az 5/B szakaszban van);
- több termékből álló saját eladás rögzítése egy egyeztetett végösszeggel, listaár-összesítéssel és termékenkénti statisztikával;
- termékenkénti listaár, becsült anyagköltség és fizikai készlet kezelése;
- fogyóanyag-beszerzések mennyiségi egységárral és egyszeri eszközkiadások naplózása;
- React/Vite admin és Spring Boot/PostgreSQL/Liquibase backend.

### Nincs benne az első ütemben

- vásárlói regisztráció és szülői szerepkör;
- kosár, fizetés, számlázás és szállítás;
- védett digitális vásárlási letöltés;
- piactéri rendelésimport és teljes webshopos rendelésteljesítési adminisztráció;
- automatikus darabszám alapú kedvezményszabályok;
- automatikus piactéri rendelésimport és fizetésintegráció;
- előfizetéses klub;
- gyermekprofil, kedvencek és személyre szabott ajánló;
- teljes nyilvános marketingoldal és vásárlói fiókrendszer.

Az admin külön belső kezelőfelület. A jelenlegi nyilvános oldal ingyenes tartalmi katalógus. Bármilyen kereskedelmi modell külön későbbi döntést és újratervezést igényel.

## 12. Későbbi bővítési fázisok

1. **Játékoldal tartalmi modellje:** borítókép, ajánlott életkor, játékidő, helyszín, eszközigény, fejlesztési területek, játékmenet, variációk, biztonság és szülői tippek.
2. **Játék–feladatlap kapcsolat:** kötelező és kiegészítő lapok kapcsolása; a publikus játékoldalon a kapcsolódó segédanyagok jelennek meg.
3. **Kategóriák és induló tartalom:** Nagymozgás és testtudat → Egyensúly és stabil járás; „Sétálj a vonalon!” szülői oldali előnézetének elkészítése.
4. **Szülői felület:** ingyenes, bejelentkezés nélküli böngészés és admin „Megtekintés szülőként” előnézet.
5. **Tartalmi minőség és kiadás:** mobilos, hozzáférhetőségi és keresőoptimalizálási ellenőrzés.

Az ügyfélfiók, játékcsomag-ajánlat, vásárlás, előfizetés és fizetési integráció nincs ütemezve; ezekhez külön új termékdöntés és terv szükséges.

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
- Csomagterv több feladatlapot tud összefogni, de önmagában nem indít rendelést.
- A jelenlegi termékváltozat feladatlaphoz vagy csomagtervhez köthető; a tervezett közös katalógusmodellre átállás után bármely tartalomtípushoz kapcsolható.
- Egy eladás több terméket rögzít a listaárak összegével és a kézzel megadott, egyeztetett végösszeggel.
- A vevővel kialkudott összeg eltérése arányosan oszlik meg a termékek között; termék- és áradatok pillanatképként megmaradnak.
- Termékenként megadható anyaglista mennyiségekkel, amelynek aktuális anyagköltsége a fogyóanyagok súlyozott átlagos egységárából számolódik.
- Eladási összesítő megmutatja a bevételt, az eladott mennyiséget és a termékenkénti népszerűséget.
- A fizikai termékváltozat készlete és előkészítése kezelhető; az új kártya- és játéktípusok ugyanazt a közös folyamatot használják.
- Új katalógustípus felvétele nem igényli a közös pénzügyi, eladási, készlet- vagy előkészítési logika típusonkénti átírását.
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
