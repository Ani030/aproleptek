# ApróLéptek – termék- és technikai terv

## 1. Dokumentum célja

Ez a dokumentum az ApróLéptek adminisztrációs és szülői felületének, tartalmainak, hozzáférési és értékesítési modelljének célját, fő folyamatait és technikai alapjait rögzíti. Az admin felület a tartalmak kezelésére szolgál; a szülői felület egységes arculatú, publikált játékokat és összeállított fejlesztő játékcsomagokat mutat be. A későbbi üzleti modell havi előfizetést és külön megvásárolható, teljes játékcsomagokat is támogat. A feladatlap önmagában nem lesz nyilvános vásárlási termék: ha egy játékhoz szükséges vagy ahhoz tervezett, a játékcsomag részeként kap hozzáférést a vásárló.

## 2. Termékcél és alapelvek

Az ApróLéptek magyar nyelvű, szakmai szemléletű játék- és feladatlap-katalógus kisgyermekes családoknak, valamint gyermekekkel foglalkozó szakembereknek.

Az induló rendszer tegye lehetővé, hogy az adminisztrátor:

- egy helyen tárolja és kezelje a feladatlapokat, képeket és nyomtatási fájlokat;
- a feladatlapokhoz nulla vagy több kivágható lapot rendeljen;
- egységes szempontok szerint kategorizáljon és keressen;
- önálló, feladatlaphoz nem kötött játékötleteket rögzítsen;
- később feladatlapokból digitális vagy fizikai csomagokat állítson össze.

### Értékesítési és hozzáférési alapelv

- A szülő nem különálló feladatlapot vásárol: a feladatlap a hozzá tervezett játék vagy játékcsomag része, és annak hozzáférési szabályát követi.
- Két ajánlati forma készülhet: aktív időszak alatt elérhető havi előfizetés, illetve külön megvásárolható, teljes játékcsomag.
- A játékcsomag a szülő számára kerek, önállóan használható élményt ad. Tartalmazhat leírt játékot, a végrehajtásához szükséges feladatlapokat, nyomtatható segédleteket és opcionális fizikai terméket.
- A kapcsolódó feladatlap lehet kötelező vagy kiegészítő. A kötelező elem a csomag hozzáférésével együtt érhető el; nem jelenhet meg külön megvásárolható ajánlatként.
- Az admin belső pénzügyi nyilvántartásában meglévő önálló feladatlap-termékek és múltbeli eladások megmaradhatnak. Ez nem teszi őket a jövőbeli nyilvános webshopban önállóan megvásárolhatóvá.
- A havi előfizetés az előfizetési időszak alatt ad hozzáférést az előfizetésbe sorolt tartalmakhoz. Az egyszer megvásárolt játékcsomagnál tervezési javaslat a tartós hozzáférés; ezt az értékesítés indítása előtt véglegesíteni kell. A végleges árak, próbaidő, lemondás és visszatérítés külön üzleti döntési kör.

Alapelvek:

- A feladatlap tartalmi objektum; az eladható termék vagy csomag külön objektum.
- A szülői oldalon megjelenő fejlesztő játék önálló tartalmi oldal, amelyhez nulla vagy több feladatlap kapcsolódhat. Ha a feladatlap szükséges, az a játék hozzáférésének része.
- A címkék és korosztályok egységes, kezelhető listákból származzanak.
- Az admin és a szülői felület ugyanazokra a publikált tartalmakra épül, de külön nézetet és szerveroldalon ellenőrzött jogosultságokat használ.
- A tartalom ne ígérjen garantált fejlődési eredményt, és ne jelenjen meg diagnosztikai eszközként.

## 3. Felhasználók és jogosultságok

### Adminisztrátor

Teljes hozzáférése van az adminfelülethez. Kezelheti a fejlesztő játékokat, feladatlapokat, képeket és fájlokat, kategóriákat, játékcsomagokat, előfizetéseket, felhasználókat és üzleti beállításokat. A tartalmakat piszkozatból ellenőrzésre, majd publikálásra állíthatja.

### Szerkesztő – későbbi szerepkör

Tartalmat hozhat létre és módosíthat, de nem kezelhet szerepköröket, rendszerbeállításokat vagy más adminisztratív jogosultságokat.

### Szülő / nevelő – ügyfélfelület

Bejelentkezés nélkül böngészheti a publikus bemutató tartalmakat. Fiókkal megtekintheti az előfizetéseit, a megvásárolt játékcsomagjait és az ezekhez tartozó feladatlapokat. A szülő és a kisgyermeknevelő kezdetben ugyanazt az ügyfélszerepkört használja; a szakmai szűrés külön felhasználói mód lehet később, ha valós igény igazolja.

### Szülői előnézet – admin funkció

Az admin a publikálás előtt ugyanazt a tartalmat előnézheti, ahogyan az a szülői felületen megjelenik. Az előnézetben tesztállapot választható: látogató, fiókos jogosultság nélkül, aktív előfizető vagy az adott játékcsomag tulajdonosa. Ezek csak szimulált megjelenítési állapotok; nem hoznak létre valódi vásárlást/előfizetést, nem adnak tényleges fájlhozzáférést, és nem kerülik meg a backend jogosultság-ellenőrzését.

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
│   ├── Játékcsomag-ajánlatok és hozzáférési módjuk
│   ├── Eladások (több termék egy megállapodott végösszeggel)
│   └── Bevétel, kiadás és népszerűség áttekintő
├── Kategóriák és címkék
│   ├── Korosztályok
│   ├── Feladattípusok
│   ├── Témakörök (külön: Állatok, Természet, stb.)
│   └── Készségterületek
└── Fiók / kijelentkezés
```

### Szülői / ügyfélfelület

```text
Főoldal
├── Játékok böngészése
│   ├── Nagymozgás és testtudat
│   │   └── Egyensúlyfejlesztő játékok
│   └── további fejlesztési témák
├── Játék részletes oldala
│   ├── kor, játékidő, helyszín, eszközigény
│   ├── játékmenet, változatok és biztonság
│   └── a játékhoz tartozó feladatlapok
├── Előfizetés / teljes játékcsomagok
├── Fiókom
│   ├── előfizetésem
│   └── megvásárolt játékcsomagjaim
└── Segítség / kapcsolat
```

Feladatlapnak nincs külön nyilvános katalógus- vagy vásárlási oldala. A szülő egy játék részletei vagy a saját hozzáférése felől nyitja meg; a játékhoz tartozó hozzáférési feltétel vonatkozik rá.

A játék nyilvános előnézete megmutathatja a címet, borítóképet, rövid leírást, korosztályt, időtartamot, helyszínt, eszközigényt, fejlesztési területeket és hozzáférési lehetőségeket. A teljes játékmenet és a hozzá tartozó nyomtatható fájlok lehetnek szabadon elérhetők, előfizetéshez kötöttek vagy játékcsomag részei; ezt tartalmonként kell beállítani. A kötelező feladatlap hozzáférése mindig az őt tartalmazó játék/ajánlat hozzáférését követi.

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

1. Az admin kiválasztja a témakört és alcsoportot, például „Nagymozgás és testtudat → Egyensúlyfejlesztő játékok”.
2. Megadja a címet, egységes borítóképet, rövid bevezetőt, ajánlott kort, játékidőt, helyszínt és eszközigényt.
3. Kitölti a fejlesztési területeket, a szükséges eszközöket, a lépésenkénti játékmenetet, a könnyítéseket, nehezítéseket, játékos változatot, biztonsági részt és szülői/nevelői tippet.
4. Opcionálisan kapcsol hozzá feladatlapokat, mindegyiknél jelezve, hogy szükséges vagy kiegészítő.
5. A szülői előnézetben ellenőrzi a kártyát és a részletes oldalt látogatóként, előfizetőként és játékcsomag-tulajdonosként, majd publikálja.

### Csomag összeállítása – kezdetben tervezési funkció

1. Az admin teljes játékélményt tartalmazó játékcsomagot hoz létre, saját címmel, leírással és egységes borítóképpel.
2. A csomaghoz fejlesztő játékokat és az azokhoz szükséges vagy kiegészítő feladatlapokat/segédanyagokat kapcsol.
3. Ellenőrzi a szülői előnézetben, hogy a csomag önmagában érthető és használható-e.
4. Az ajánlat hozzáférési módja lehet előfizetés vagy külön megvásárolható játékcsomag; a feladatlap nem kap önálló vásárlási ajánlatot.

### Eladható termék és eladás rögzítése – saját értékesítés MVP

Ez a folyamat az admin belső, kézi értékesítési és önköltség-nyilvántartása. Nem jelenti azt, hogy a nyilvános boltban minden katalógustartalom külön megvásárolható. A nyilvános értékesítési szabályokat az előfizetéses/játékcsomagos modell határozza meg; feladatlap önmagában ott nem lehet termék.

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

Ez a belső admin pénzügyi és készletfogalma; a jövőbeli nyilvános bolt ajánlata ettől külön hozzáférési modellt használ. A nyilvános boltban fejlesztő játék vagy teljes játékcsomag értékesíthető. Feladatlap önálló nyilvános SKU-t és vásárlási gombot nem kap; a megvásárolt/előfizetett játék által igényelt lap az ajánlat része.

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

Az áttekintő kártya és a részletes oldal ugyanazt a borítóképet és arculati elemeket használja. Az áttekintőn a cím, rövid leírás, életkor, időtartam, fejlesztési területek és hozzáférés (előfizetés része / külön játékcsomag) legyen gyorsan áttekinthető.

### Csomag

A nyilvánosan megvásárolható **játékcsomag** kerek, teljes élményt adó ajánlat, nem önálló feladatlap. Tartalmazhat egy vagy több fejlesztő játékot, a hozzájuk szükséges feladatlapokat, nyomtatható segédleteket, kártyákat és opcionálisan fizikai terméket. A csomag feladatlap-kapcsolata lehet kötelező vagy kiegészítő. A feladatlap a csomag részeként kap hozzáférést, nem külön termékként.

Mezők: cím, slug, leírás, egységes borítókép, csomagba rendezett játékok és erőforrások, kötelező/kiegészítő kapcsolatok, hozzáférés típusa, ár, státusz és teljesítési adatok. A jelenlegi admin „Csomagterv” feladatait úgy kell továbbfejleszteni, hogy ne csak feladatlapokból álló ajánlatot tudjon létrehozni, hanem teljes játékélményt összeállítani. A már rögzített csomagokat és belső pénzügyi adatokat migráció nélkül meg kell őrizni.

### Előfizetés és hozzáférési jogosultság

- Az előfizetés a meghatározott játékkönyvtárhoz ad hozzáférést az aktív időszakban.
- Az egyszer megvásárolt játékcsomag hozzáférést ad az adott csomaghoz és annak kötelező feladatlapjaihoz; a tartós hozzáférés a javasolt, de még jóváhagyandó alapértelmezés.
- Tartalmonként beállítható, hogy csak előnézet publikus, teljes leírás ingyenes, előfizetéses vagy egy külön játékcsomag része.
- Az ügyfélfiókban külön jelenjen meg az előfizetés állapota és a külön megvásárolt játékcsomagok listája.
- Az adatmodell különítse el a katalógustartalmat, a nyilvános ajánlatot/árat, a vásárlást, az előfizetést és a hozzáférési jogosultságot (entitlement). A jogosultságot mindig a szerver ellenőrizze a fájlok és tartalmak kiszolgálásakor.
- A fizetési szolgáltató kiválasztása, számlázás, adózás, lemondás, visszatérítés, próbaidő és az előfizetésben szereplő tartalmak szabálya a fizetési integráció előtt külön döntési pont.

### Kategóriák és címkék

Az admin és a későbbi nyilvános katalógus egymástól különálló, többértékű taxonómiacsoportokat használ. Egy feladatlaphoz több érték is rendelhető ugyanabból a csoportból. Így például egy állatos, kivágható elemekkel készült árnyékpárosító egyszerre kaphat korosztályt, feladattípust, témakört és készségterületet.

Kezdeti taxonómiák:

- korosztály: például 18–24 hónap, 2–3 év, 3–4 év, 4–6 év;
- feladattípus: árnyékpárosító, képpárosító, formapárosító, öltöztető, vonalvezető, válogató, képkirakó, sorozatalkotó, keresd és jelöld, vágás és ragasztás, színező, labirintus;
- témakör: **állatok** és **természet** külön választható címkék, továbbá járművek, évszakok, érzelmek, mindennapi rutinok;
- készségterület: finommotorika, nagymozgás, kommunikáció, érzékelés, gondolkodás, kreativitás, társas-érzelmi kapcsolódás;
- formátum és használat: A4, digitális, fizikai, beltéri/kültéri.

A feladattípus azt írja le, mit csinál a gyermek a lapon; a témakör azt, miről szól; a készségterület pedig azt, milyen képesség gyakorlását támogathatja. Ezek ne helyettesítsék egymást. Az „Állatok és természet” összevont címkét ne használjuk új tartalmakhoz: a két nagy témakör külön-külön legyen kereshető és szűrhető, és egy lap mindkettőhöz hozzárendelhető legyen. A nyilvános játékböngészőben a témakörök legalább egy szülő–alcsoport szintig rendezhetők. Induló ág: **Nagymozgás és testtudat → Egyensúlyfejlesztő játékok**. Az admin később további alcsoportokat is felvehet.

A fejlesztő játékok ajánlott életkora lehet nyitott korhatárú, például „18 hónaptól”; ezért a kötött korosztály címkéken túl az oldalon pontos, közérthető ajánlásnak is meg kell jelennie. Az induló készségterületek között szerepeljen az egyensúly, mozgáskoordináció, testtudat, figyelem és mozgásos önbizalom. A készségcímkéhez rövid magyarázó szöveg kapcsolható, hogy a szülő ne csak szakkifejezést lásson.

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

Természetközeli, meleg tónusok, visszafogott illusztrációk, olvasható magyar tipográfia, mobilközpontú kártyák. A szülői oldal a játékokat és teljes játékcsomagokat teszi felfedezhetővé. A feladatlapok csak az őket tartalmazó játék hozzáférésén keresztül nyithatók meg. Az admin és az ügyfélfelület külön alkalmazásnézet, közös publikált tartalommal és szerveroldali, szerepkör szerint védett API-val. Az adminnak külön „Megtekintés szülőként” előnézete van.

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

Az admin MVP változatlanul külön, fizetés nélküli belső kezelőfelület. A nyilvános előfizetéses és játékcsomag-értékesítési cél viszont már rögzített termékirány, és a következő tervezési/fejlesztési fázis tárgya.

## 12. Későbbi bővítési fázisok

1. **Játékoldal tartalmi modellje:** borítókép, ajánlott életkor, játékidő, helyszín, eszközigény, fejlesztési területek, játékmenet, variációk, biztonság és szülői tippek.
2. **Játék–feladatlap kapcsolat:** kötelező és kiegészítő lapok kapcsolása; nyilvános hozzáférés és letöltés mindig a játék/játékcsomag jogosultságát követi.
3. **Kategóriák és induló tartalom:** Nagymozgás és testtudat → Egyensúlyfejlesztő játékok; „Sétálj a vonalon!” szülői oldali előnézetének elkészítése.
4. **Szülői felület és szerepkörök:** publikus böngészés, ügyfélfiók, admin szerkesztő és „Megtekintés szülőként” előnézet, külön backend jogosultságokkal.
5. **Játékcsomag-ajánlat:** egy vagy több játék teljes, megvásárolható összeállítása a hozzá szükséges feladatlapokkal és segédanyagokkal; feladatlap önmagában nem kerül a nyilvános boltba.
6. **Hozzáférési jogosultságok:** előfizetéses tartalom és egyszer megvásárolt csomag, külön vásárlás/előfizetés/jogosultság adatmodellel.
7. **Fizetési és előfizetési integráció:** szolgáltató, számlázás, adózás, lemondás, visszatérítés és hozzáférési időszakok döntése, majd megvalósítása.
8. **Védett tartalomkiszolgálás:** a feladatlapok és digitális segédanyagok letöltésének entitlement-ellenőrzése.
9. **SEO és kiadás:** publikus oldalak előrenderelése vagy SSR-je, mobilos, hozzáférhetőségi és keresőoptimalizálási ellenőrzéssel.
10. **További személyes funkciók:** kedvencek, játéknaptár, ajánló, heti terv és szakember/óvoda funkciók valós felhasználói igény alapján.

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
