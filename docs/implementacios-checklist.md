# ApróLéptek – implementációs checklist

Ez a terv az adminisztrációs és feladatlap-katalógus MVP-jét bontja ellenőrizhető munkacsomagokra. A jelölőnégyzetek Markdownban pipálhatók: `[ ]` → `[x]`.

## 0. Projektindítás és döntések

- [ ] Márkanév és alap arculati irány rögzítése.
- [ ] MVP hatókörének jóváhagyása: admin katalógus és tartalomkezelés, webshop-fizetés nélkül.
- [x] Backend Java/Spring Boot alapprojekt létrehozása.
- [x] Frontend React + Vite + TypeScript alapprojekt létrehozása.
- [x] PostgreSQL fejlesztői adatbázis Compose konfigurációjának elkészítése. *(A helyi Docker daemon indítási engedélye még szükséges.)*
- [x] Liquibase konfiguráció és első changelog létrehozása.
- [x] Repo könyvtárstruktúra, fejlesztői README és helyi konfigurációs minta elkészítése.
- [x] Titkok és jelszavak környezeti változóból vagy titokkezelőből jöjjenek; ne kerüljenek Gitbe.
- [ ] Fejlesztési, tesztelési és éles környezetek konfigurációs határainak rögzítése.

## 1. Adatmodell és migrációk

- [ ] Felhasználó és szerepkör adatmodell megtervezése.
- [x] MVP adminisztrátor szerepkör létrehozása. *(Kezdetben környezeti változóval konfigurált admin fiók, adatbázis-felhasználók későbbi bővítés.)*
- [x] Feladatlap tábla létrehozása.
- [x] Kivágható lap tábla létrehozása, kötelező feladatlap-kapcsolattal és sorrendmezővel.
- [x] Fájl-metaadat tábla létrehozása az előnézetekhez és nyomtatási fájlokhoz.
- [x] Játékötlet tábla létrehozása önálló tartalomtípusként.
- [x] Korosztály, témakör és készségterület taxonómiák létrehozása.
- [x] Termékdöntés rögzítése: külön, önálló taxonómia legyen a feladattípus; az Állatok és a Természet külön témakörök legyenek.
- [x] `TASK_TYPE` taxonómiacsoport hozzáadása Liquibase migrációval és referenciaadatokkal.
- [x] Feladattípusok induló listájának felvétele: árnyékpárosító, képpárosító, formapárosító, öltöztető, vonalvezető, válogató, képkirakó, sorozatalkotó, keresd és jelöld, vágás-ragasztás, színező, labirintus.
- [x] Az „Állatok és természet” összevont témakör kivezetése biztonságos migrációval; az érintett tartalmak az Állatok és Természet címkéket kapják, a régi kapcsolatok átvitele után.
- [x] Tartalmak és taxonómiák kapcsolótábláinak létrehozása.
- [x] Csomagterv és csomag–feladatlap kapcsolat létrehozása.
- [x] Státuszok és publikálási dátumok kialakítása.
- [ ] Létrehozó/módosító felhasználó és időbélyeg mezők hozzáadása.
- [ ] Idegen kulcsok, egyediségi feltételek és indexek migrációban rögzítése.
- [ ] Liquibase changesetek dokumentálása; alkalmazott changeset utólagos módosításának tiltása a csapatfolyamatban.
- [ ] ORM-séma automatikus frissítés kikapcsolása; migrált séma validálása.

## 2. Backend alapok és hozzáférés

- [x] Egységes API-hibaválasz és validációs szabályok kialakítása. *(A REST API hibái egységes `{error, status}` JSON formátumban jelennek meg; kezeli a hibás kérést, típuseltérést, fájlméretet, ütköző kapcsolatot és váratlan szerverhibát.)*
- [x] Verziózott REST API-előtag kialakítása (`/api/v1`).
- [x] Admin bejelentkezési folyamat megvalósítása.
- [x] Jelszó biztonságos hash-elése.
- [x] Szerveroldali szerepkör- és jogosultságellenőrzés az admin API-kon.
- [x] Adminisztrátori fiók biztonságos kezdeti létrehozásának módja dokumentálása. *(Első induláskor az `APP_ADMIN_USERNAME` és `APP_ADMIN_PASSWORD` környezeti változók adják a BCrypt-hash-elt admin belépést; a README leírja a helyi indítást és azt, hogy éles titkokat titokkezelőből kell megadni.)*
- [x] CORS és HTTP Basic alapú token nélküli admin API hozzáférés konfigurálása.
- [ ] Publikált nyilvános végpontok és védett admin végpontok elválasztása.
- [x] Kérésméret- és fájlfeltöltési korlátok beállítása.
- [x] Alap egészségellenőrző végpont kialakítása.

## 3. Fájltárolás

- [ ] Fájltárolási adapter interfész kialakítása.
- [x] Fejlesztői fájltár beállítása.
- [ ] Éles objektumtár céljának és konfigurációjának kiválasztása.
- [x] Előnézeti képek feltöltésének támogatása.
- [x] Nyomtatási fájlok feltöltésének támogatása.
- [x] Engedélyezett fájltípusok, MIME-típusok és maximális fájlméretek meghatározása.
- [x] Fájlnév és tárolási kulcs szerveroldali generálása.
- [x] Fájlfeltöltési metaadatok mentése PostgreSQL-be.
- [x] Előnézeti kép biztonságos megjelenítése.
- [x] Hiba esetén részleges feltöltés és árva metaadat rendezett kezelése. *(A többfájlos feltöltés tranzakciós kötegben fut; hiba esetén a DB-változások visszagördülnek, a már kiírt fájlok törlődnek.)*
- [ ] Éles digitális termékfájlok későbbi védett kiszolgálásának megtervezése.
- [ ] Fájlmentési és visszaállítási folyamat dokumentálása.

## 4. Feladatlap és kivágható lap API

- [x] Feladatlap lista API kereséssel, szűréssel, rendezéssel és lapozással.
- [x] Feladatlap létrehozása és szerkesztése.
- [x] Piszkozat, ellenőrzésre váró, publikált és archivált állapot kezelése.
- [x] Alap feladatlap fájljainak kezelése.
- [x] Kivágható lapok hozzáadása, módosítása és törlése.
- [x] Kivágható lapok sorrendjének mentése.
- [x] Összesített oldalszám számítása.
- [x] A4-es méret, tájolás, színesség és oldalszám adatainak validálása. *(Backend: engedélyezett papírméretek/tájolások/színmódok, oldalszám 1–100 közé korlátozva.)*
- [x] Korosztályok, témák és készségterületek hozzárendelése.
- [x] Több feladattípus hozzárendelése egy feladatlaphoz.
- [x] Állatok és Természet egymástól független témakörként hozzárendelhető egy vagy több feladatlaphoz.
- [ ] Archivált feladatlap ne jelenjen meg publikus katalógusban.
- [x] Hibaüzenetek és érvénytelen kapcsolatok kezelése. *(A hibás kérés, típuseltérés, hiányzó paraméter, méretkorlát, jogosultság, duplikáció és adatkapcsolati ütközés egységes `{error, status}` választ ad.)*

## 5. Játékötlet és csomagterv API

- [x] Önálló játékötlet létrehozása, módosítása és listázása.
- [ ] Játékötlet rögzíthető feladatlap-kapcsolat nélkül.
- [ ] Időtartam, eszközök, előkészület, játékmenet és változatok kezelése.
- [x] Biztonsági megjegyzés és készségterületek kezelése backend modellen.
- [ ] Opcionális kapcsolódó feladatlapok és más tartalmak kezelése.
- [x] Csomagterv létrehozása és szerkesztése.
- [x] Több feladatlap csomaghoz rendelése és sorrendezése.
- [x] Digitális/fizikai/kettős csomagtípus rögzítése.
- [ ] Csomagterv ne hozzon létre fizetést, rendelést vagy készletmozgást az MVP-ben.

## 5/A. Saját értékesítés, termékköltség és pénzügyi áttekintő

- [x] Döntés rögzítése: az eladás több, meglévő katalógustartalomhoz kapcsolt termékből állhat, egyetlen megállapodott végösszeggel; nincs automatikus mennyiségi kedvezményszabály.
- [x] Eladható termékváltozat kapcsolása a jelenlegi feladatlaphoz vagy csomagtervhez, formátummal, listaárral és becsült anyagköltséggel. *(A közös katalógustartalom-modellre átállítás külön feladat az 5/B szakaszban.)*
- [x] Több terméksor és mennyiség rögzítése egy eladásban. *(Admin eladási űrlap és tranzakciós `/sales` API.)*
- [x] Listaár-összeg, elfogadott végösszeg és egyeztetési különbözet mentése; terméksoronkénti összeg automatikus arányosítása.
- [x] Eladás dátuma, értékesítési csatornája (például Vinted) és megjegyzése rögzíthető.
- [x] Múltbeli eladás termék- és áradatainak pillanatképe megmarad későbbi árváltozás után is.
- [x] Termékenkénti darabszám, eladási gyakoriság és bevétel kimutatása. *(Adminban összesített bevétel és legnépszerűbb termékek.)*
- [x] Pénzügyi felület külön, felül váltható Áttekintő, Egyszeri kiadások, Fogyóanyagok és Eladások nézetekkel.
- [x] Termékenkénti fedezet és időszakválasztós statisztika. *(A termékrangsor a történeti bevételt és anyagköltség levonása utáni fedezetet mutatja; az áttekintő eladási bevétele és becsült anyagköltsége dátumtartomány szerint szűrhető.)*
- [x] Egyszeri eszközkiadások és fogyóanyag-beszerzések naplózása; beszerzési mennyiségből és árból súlyozott átlagár számítása.
- [x] Piactéri rendelésimport és automatikus fizetésintegráció nincs; a saját eladások és készletmozgások kézzel rögzítettek.
- [x] Fogyóanyag-beszerzések, egységár-számítás és fogyóanyag-készlet nyilvántartása.
- [x] Fogyóanyagok mennyiségi összekapcsolása a termékváltozatokkal, automatikus anyagköltség-számítással. *(A termék anyaglistája a súlyozott átlagos egységárakból számol; új beszerzés frissíti az érintett termékváltozatok aktuális költségét. Az eladási sorok költségpillanatképe nem változik.)*
- [x] Nyomtatási díj automatikus hozzáadása a fizikai termékváltozatok gyártási költségéhez, egy- és kétoldalas beállítással. *(A Pénzügy → Áttekintő nézetében állítható a nyomtatott oldalankénti díj; a rendszer a papírlapok és a nyomtatott felületek száma alapján számol, az eladás pedig a teljes gyártási költséget rögzíti pillanatképként.)*
- [x] Termékadatlap megnyitása külön szerkesztőnézetben: felhasznált fogyóanyagok, darabonkénti mennyiség, számolt anyagköltség és tervezett eladási ár.
- [x] Eladás szerkesztése, dátum ISO-formátumú kezelése és csomagolóanyag-választás eladásonként. *(A csomagolás súlyozott átlagos egységárból számolódik, mentéskor pillanatképként rögzül és arányosan kerül az eladási terméksorokra.)*
- [x] Saját, kézzel rögzített vevői megrendelések kezelése külön Megrendelések menüpontban, nyitott, teljesített és lemondott fülekkel; az Eladások menüpontban a végleges eladások maradnak.
- [x] Megrendelés soronként mutassa a készletből és a korábbi nyitott megrendelésekből számított gyártandó darabszámot.
- [x] Megrendelés terméksorának készre jelölése checkboxszal; teljesítés csak minden sor készre jelölése után lehetséges.
- [x] Teljesítéskor normál eladási rekord, termékkészlet-csökkenés és a megrendeléshez legyártott hiány készletmozgása készüljön; a csomagolóanyag kerüljön át az eladásra.
- [ ] Megrendelés létrehozása, szerkesztése, készre jelölése, teljesítése, lemondása és az eladási/készletadatok összhangja böngészőből végigellenőrizve.

## 5/B. Bővíthető katalógus- és termékmodell

**Cél:** új terméktípus (például kártyacsomag vagy játék) saját tartalmi adatlapot kaphasson úgy, hogy az eladás, költségszámítás, előkészítés és készlet közös folyamatait ne kelljen típusonként újraépíteni.

### Modell és adatmegőrzés

- [x] Katalógustartalom, tartalomtípus, eladható termék, termékváltozat és eladási sor felelősségének dokumentált szétválasztása a `docs/termek-es-technikai-terv.md` szerint.
- [x] Bővíthető `catalog_type` típusregiszter létrehozása; az első változat WORKSHEET, BUNDLE, CARD_DECK, GAME és GAME_ELEMENT típusokat rögzít közös Java/SQL enum nélkül. *(Liquibase `020-extensible-catalog-foundation`.)*
- [x] Közös `catalog_item` alaprekord és típus/státusz/cím index létrehozása. *(Liquibase `020-extensible-catalog-foundation`.)*
- [x] Katalógustípusok és közös katalógustételek olvasó API-ja (`/catalog/types`, `/catalog/items`).
- [x] Feladatlap- és csomagterv API mentés/archiválás szinkronban tartja a közös katalógusrekordot.
- [x] Típusonként külön részlettáblák és API-k; a típus-specifikus mezők nem kerülnek a közös termék- vagy eladástáblába.
- [x] A `sale_product` közös katalógustartalomhoz kapcsolása, a meglévő termékváltozatok átvezetésével; a gyártási recept, nyomtatási költség, előkészítés és készlet továbbra is a közös termékváltozatot használja. *(A termék API új rekordnál is menti a közös katalógusazonosítót.)*
- [x] Meglévő feladatlapok, csomagtervek és eladható termékváltozatok közös katalógusazonosítóinak visszatöltése migrációban.
- [ ] Meglévő adatbázison ellenőrizni, hogy az eladások, anyagkapcsolatok, médiafájlok, készletek és előkészítési tételek azonosítói és adatai változatlanok maradnak.
- [x] Minden eddigi sémaátalakítás új Liquibase changesetben történik; a `020-extensible-catalog-foundation` hozzáadva a master changeloghoz.
- [x] A `020`–`022` migrációk lefutása a meglévő fejlesztői adatbázison ellenőrizve; Liquibase mind a 22 changesetet alkalmazottnak jelzi.
- [ ] Teljesen üres adatbázis felépítésének ellenőrzése a teljes changeloggal.
- [ ] Régi API-válaszok és admin folyamatok átállítása úgy, hogy a már rögzített eladások és költség-pillanatképek változatlanok maradjanak.
- [x] Közös katalóguslista és tartalomtípus szerinti szűrés kialakítása; a célzott munkanézetek megmaradnak szerkesztőként.

### Kártyacsomag

- [x] Kártyacsomag mint önálló katalógustartalom létrehozása. *(Liquibase részlettáblák, API CRUD és admin nézet.)*
- [x] Borítókép, opcionális hátoldal, leírás/játékszabály és rendezett, képes kártyalap-lista kezelése.
- [x] Kártyalapok külön katalógustartalomként kezelése; ugyanaz a kártyalap több csomagban újrafelhasználható.
- [x] Kártyák nyomtatási elrendezésének és egy oldalra férő kártyák számának rögzítése.
- [x] Oldalszám, egy-/kétoldalas nyomtatás és nyomtatási költség számítása a kártyaelrendezésből.
- [x] Kártyacsomag fizikai termékváltozatának anyaglistája, nyomtatási/laminálási/tépőzár előkészítése és költsége a feladatlapok közös folyamatát használja.
- [x] Kártyacsomag termékváltozatának eladása és készletkezelése a közös termékfolyamatban működik.
- [ ] Kártyacsomag létrehozása, mentése, termékváltozat-beállítása és készletre vétele felületen végigellenőrizve.

### Bővíthető játékok és elemek

- [x] Játék mint önálló katalógustartalom és bővíthető játékcsalád létrehozása (öltöztetős, boltos/pénztáros, egyéb).
- [x] Újrafelhasználható játékelemek kezelése kategóriával, képpel és kompatibilitással (például baba, ruha, kiegészítő, zöldség, gyümölcs).
- [x] Bővíthető játéktípus- és elemkategória-törzs kialakítása új adatbázis-oszlopok hozzáadása nélkül.
- [x] Játék és játékelemek külön kapcsolata; egy játékelem több játékcsomagban is felhasználható.
- [x] Játékelemek több játékban/csomagban történő újrafelhasználásának támogatása.
- [x] Játék vagy játékelem önálló értékesítése külön létrehozott termékváltozattal történik.
- [x] A fizikai játékkészletek a közös anyag-, nyomtatás-, előkészítés-, készlet- és eladási folyamatba kapcsolódnak.
- [ ] Játékcsomag és játékelem létrehozása, termékváltozata, eladása és készletre vétele felületen végigellenőrizve.

### Bővíthetőség ellenőrzése

- [ ] Egy további, eddig nem ismert tartalomtípus felvehető legyen új részlettáblával és szerkesztővel a közös pénzügyi/készletfolyamat módosítása nélkül.
- [ ] Új tartalomtípus fizikai és digitális termékváltozata létrehozható legyen.
- [ ] Új tartalomtípus költsége, eladása, előkészítése, késztermékkészlete és készletmozgása azonos szabályok szerint jelenjen meg.
- [ ] Migrációs és API-elfogadási forgatókönyvek rögzítése: meglévő feladatlap/eladás, új kártyacsomag, új játékcsomag és későbbi új típus.

## 5/C. Fizikai készlet és előkészítés

- [x] Fizikai eladható termékváltozat kész darabszámának megadása és manuális növelése/csökkentése.
- [x] Minimumkészlet beállítása és alacsony készlet jelzése.
- [x] Késztermék-készlet darabszámának és becsült előállítási értékének összesítése.
- [x] Késztermék és alapanyag külön kezelése a készletnézetben.
- [x] Előkészítési tételek létrehozása mennyiséggel, képpel, nyomtatás/laminálás/tépőzár jelölőkkel és megjegyzéssel.
- [x] Egy termék több előkészítési tételének egy termékcsoport alatt történő összesítése, a részletek megnyithatók.
- [x] Előkészítés alatt álló darabszám elkülönítése a késztermékkészlettől.
- [x] Előkészítésből késztermék bevételezésekor a bevételezett mennyiség átkerül a késztermékkészletbe, és ugyanennyivel csökken az előkészítési tétel (teljes és részleges API-folyamat izolált adatbázis-klónon ellenőrizve).
- [x] Megrendelés teljesítésekor a készre jelölt, még készleten nem lévő darabok készletbevételezése és eladási készletcsökkenése külön készletmozgásként naplózódik.
- [x] A korábban jelzett „Váratlan hiba” jelenlegi kóddal nem reprodukálódott a klónon; a részleges bevételezés sikerült. A szerveroldali naplózás most megőrzi a jövőbeli váratlan kivételek stack trace-ét.
- [ ] Eladás készletcsökkentésének, eladásszerkesztéskor történő korrekciónak és törlés/sztornó utáni visszaállításnak végigellenőrzése.
- [x] Késztermék- és alapanyagmozgások előzménye, kézi módosítások indoklásával; beszerzés és előkészítés miatti felhasználás is naplózódik (027-es migráció).

## 6. Kategóriák és keresés

- [x] Korosztályok adminisztrálható listája.
- [x] Témakörök adminisztrálható listája.
- [x] Készségterületek adminisztrálható listája.
- [x] Feladattípus önálló, adminisztrálható taxonómiacsoportként.
- [x] Kategóriakezelőben a korosztály, feladattípus, témakör és készségterület csoportok külön kezelése.
- [x] Induló témakörök között az Állatok és a Természet külön elemekként szerepelnek; az összevont címke inaktív.
- [x] Feladatlap listájának külön Feladattípus szűrője legyen.
- [x] Témakörszűrőben az Állatok és a Természet külön választható; több témakör egyidejű kiválasztása működik.
- [x] Keresési API fogad külön korosztály-, feladattípus-, témakör- és készségterület-szűrőket, és kombinálja azokat.
- [x] Kategória slugok és megjelenítési sorrend kezelése. *(Adminfelületről módosítható név, slug és sorrend; a backend validálja ezeket, és a taxonómia listái a sorrendet használják.)*
- [x] Inaktív kategória ne vesszen el olyan tartalomról, amely már használja. *(Az admin lekérheti az inaktív kategóriákat; új tartalomhoz nem választhatók, a meglévő kapcsolataikat pedig a feladatlap- és játékötlet-mentés megőrzi.)*
- [x] Szöveges keresés címben és leírásban.
- [x] Több szűrő együttes használata API-szinten és az admin katalógusban.
- [x] Lapozás és rendezés API-szinten.
- [x] Üres találati, betöltési és hibaállapotok megtervezése. *(A katalógus üres keresési állapota mellett a teljes admin kezdeti adatbetöltése visszajelzést és hiba esetén újrapróbálást mutat.)*

## 7. Admin frontend

- [x] Vite + React + TypeScript konfiguráció.
- [x] shadcn/ui-hoz előkészített komponensrendszer és Tailwind CSS beállítása.
- [x] ApróLéptek admin szín-, betű-, térköz- és saroktokenek definiálása.
- [x] Alap layout oldalsó navigációval és fejlécsávval.
- [x] Bejelentkezési oldal és védett adminfelület.
- [x] Áttekintő dashboard.
- [x] Feladatlap lista előnézeti képpel, címkékkel és státusszal.
- [x] Kereső és szűrők a katalóguslistákban.
- [x] Külön Feladattípus szűrő és megjelenített feladattípus-címkék a feladatlaplistában.
- [x] Feladatlap-szerkesztőben külön Feladattípus és Témakör mezőcsoport.
- [x] A Témakör mezőben Állatok és Természet különválasztva, mindkettő kiválasztható értékként.
- [x] Feladatlap szerkesztő űrlap.
- [x] Külön szerkeszthető alaplap- és kivághatólap-szekció.
- [x] Kivágható lapok hozzáadása, törlése és sorrendezése.
- [x] Fájl feltöltési folyamat előnézettel és hibajelzésekkel.
- [x] Összesített nyomtatási oldalszám kijelzése.
- [x] Piszkozat, ellenőrzés és publikálás státuszok.
- [x] Játékötlet lista és szerkesztő.
- [x] Csomagterv lista és szerkesztő.
- [x] Kategória- és címkekezelő felület.
- [x] Reszponzív admin elrendezés laptopra és kisebb képernyőre.
- [ ] Billentyűzetes működés, fókuszjelzés, címkék és megfelelő kontraszt.

## 8. Tartalmi előkészítés

- [ ] Első korosztályi taxonómia véglegesítése.
- [ ] Témakör- és készségterület-lista véglegesítése.
- [x] Feladattípusok induló elnevezéseinek egységesítése, például „Árnyékpárosító” és „Képpárosító”.
- [x] Állatok és Természet önálló témacímkékként felvéve; további alkategóriák később bővíthetők.
- [x] Fájlelnevezési és verziózási konvenció meghatározása. *(Rögzítve a `docs/fajlelnevezes-es-verziozas.md` dokumentumban; a kézi verziószámozás és az UUID-alapú tárkulcs szerepe is tisztázva.)*
- [x] Feladatlap-minőségi ellenőrző lista összeállítása. *(A tartalmat, fájlokat, nyomtatást, hozzáférhetőséget és publikálást a `docs/feladatlap-minosegi-ellenorzo-lista.md` foglalja össze.)*
- [ ] Első feladatlapok feltöltése és helyes A4-es nyomtatási ellenőrzése.
- [ ] Kivágható lapos feladatlapok összes oldalának ellenőrzése.
- [ ] Első önálló játékötletek rögzítése.
- [ ] Kapcsolódó tartalmak megadása ott, ahol valóban hasznos.
- [ ] Előnézeti képekhez alternatív szövegek megírása.

## 9. Ellenőrzés és kiadásra készítés

- [ ] Adatbázis létrehozása üres állapotból Liquibase migrációkkal.
- [ ] Admin jogosultságok ellenőrzése engedélyezett és tiltott esetekkel.
- [ ] Feladatlap CRUD folyamat végigellenőrzése.
- [ ] Több kivágható lap hozzáadása, átrendezése és törlése ellenőrizve.
- [ ] Fájlformátum- és fájlméret-hibák ellenőrzése.
- [ ] Keresési, szűrési és lapozási viselkedés ellenőrzése.
- [ ] Azonos témán belül a feladattípus szerinti találatok ellenőrzése (például árnyékpárosítók).
- [ ] Állatok és Természet szűrésének külön-külön és kombinált működése ellenőrizve.
- [ ] Játékötlet feladatlapkapcsolat nélkül menthető.
- [ ] Csomag több feladatlapot tartalmazhat.
- [ ] Mobil- és billentyűzetes használhatóság áttekintése.
- [ ] Mentésből visszaállítási folyamat kipróbálása.
- [ ] Környezeti változók és éles titkok kezelése ellenőrizve.
- [ ] Fejlesztői indítás, migráció és admin első belépés dokumentálva.
- [ ] MVP elfogadási kritériumok teljesülése rögzítve.

## 10. Szülői termékoldal, előfizetés és játékcsomagok

### Rögzített irány és oldal terve

- [x] Külön admin szerkesztő és szülői/nevelői ügyfélfelület szerepkörének rögzítése; adminból szülői előnézet szükséges.
- [x] Üzleti modell rögzítése: havi előfizetés és/vagy külön megvásárolható teljes játékcsomag; a feladatlap önmagában nem nyilvános vásárlási termék.
- [x] A feladatlap hozzáférése az azt igénylő játék vagy játékcsomag hozzáférését követi; a szükséges lap a csomag része.
- [x] Szülői játékoldal és „Sétálj a vonalon!” példaoldal tartalmi felépítésének dokumentálása.
- [x] Kategóriaág rögzítése: Nagymozgás és testtudat → Egyensúlyfejlesztő játékok.
- [x] Pénzügyi admin belső értékesítési naplójának elkülönítése a jövőbeli nyilvános bolt ajánlataitól; korábbi rekordok és belső termékek megmaradnak.

### Tartalom és admin szerkesztő

- [ ] Fejlesztő játék önálló tartalomtípusának mezői: borítókép, cím, rövid leírás, ajánlott életkor, játékidő-tartomány, helyszín, eszközigény.
- [ ] Játékoldal tartalmi részei: fejlesztési területek magyarázattal, eszközök, számozott menet, könnyítés, nehezítés, játékos változat, biztonság és szülői/nevelői tipp.
- [ ] Témakör–alcsoport kezelésének admin terve és adatmodellje; elsőként Nagymozgás és testtudat → Egyensúlyfejlesztő játékok.
- [ ] Fejlesztési területek kezelése külön a témakörtől; induló értékek: egyensúly, mozgáskoordináció, testtudat, figyelem, mozgásos önbizalom.
- [ ] Játékhoz kapcsolódó feladatlapok kezelése kötelező/kiegészítő jelöléssel.
- [ ] Admin „Megtekintés szülőként” előnézet tesztállapotokkal: látogató, fiókos jogosultság nélkül, aktív előfizető, játékcsomag-tulajdonos; csak szimulálja a hozzáférési felületet, valódi jogosultságot nem ír.
- [ ] „Sétálj a vonalon! – Játékos egyensúlyfejlesztés” mintaoldal feltöltése a tervben rögzített 18 hónapos kortól, 5–10 perces játékidővel és öt fejlesztési területtel.

### Szülői oldal és hozzáférés

- [ ] Egységes főoldal és fejlesztési témák szerint böngészhető játékáttekintő.
- [ ] Játékkártya egységes borítóképpel, címmel, életkorral, idővel, fejlesztési területekkel és hozzáférési jelzéssel.
- [ ] Reszponzív részletes játékoldal a rögzített tartalmi sorrenddel.
- [ ] Tartalmonkénti hozzáférés beállítása: teljesen nyilvános, előfizetéses vagy egyedi játékcsomag része; publikus előnézeti kártya mindhárom esethez.
- [ ] Feladatlap ne kapjon önálló nyilvános termékoldalt, árat vagy vásárlási gombot; a hozzá tartozó játékból legyen elérhető.
- [ ] Szülői/nevelői fiók regisztrációval, bejelentkezéssel és szerepkör-alapú hozzáféréssel.
- [ ] Fiókban előfizetés állapota és külön megvett játékcsomagok listája.
- [ ] Jogosultsági modell: aktív előfizetéshez rendelt tartalmak és egyedi játékcsomag-vásárlások elkülönítése.
- [ ] Játékleírás, feladatlap és nyomtatási fájl letöltésének szerveroldali jogosultság-ellenőrzése.

### Játékcsomagok és kereskedelmi működés

- [ ] Játékcsomag-összeállító fejlesztő játékot, kötelező feladatlapot és kiegészítő anyagot is tudjon tartalmazni.
- [ ] Teljes, önállóan elvégezhető játékcsomag megjelenítése külön ajánlatként; benne a feladatlapok nem külön termékek.
- [ ] Havi előfizetési csomag és az abba bevont játékok adminisztrációja.
- [ ] Egyszeri játékcsomag-vásárlás hozzáférési időtartamának üzleti döntése. *(Javaslat: tartós hozzáférés, de árusítás előtt jóváhagyandó.)*
- [ ] Előfizetési ár, számlázás, próbaidő, lemondás, visszatérítés és jogosultság-megszűnés szabályainak rögzítése.
- [ ] Fizetési szolgáltató kiválasztása és kosár/fizetési folyamat kialakítása.
- [ ] Nyilvános oldal renderelési/SEO megoldásának kiválasztása (előrenderelés vagy SSR).
- [ ] Szülői és admin nézetek jogosultsági, mobilos és hozzáférhetőségi elfogadási ellenőrzése.

## 11. Megvalósítási sorrend

1. Projektváz és helyi fejlesztői környezet.
2. Adatmodell és Liquibase migrációk.
3. Admin belépés és szerepkör-védelem.
4. Fájltár és feltöltési API.
5. Feladatlap + kivágható lap backend.
6. Taxonómia és admin keresés: `TASK_TYPE` migráció, külön Állatok és Természet címkék, régi összevont címke biztonságos kivezetése.
7. API-szűrők és feladatlapkapcsolatok bővítése korosztály, feladattípus, témakör és készségterület szerint.
8. React admin kategóriakezelő, feladatlap-szerkesztő és külön feladattípus/témakör szűrők.
9. Fejlesztő játékok admin adatlapja, borítóképe, részletes tartalmi mezői és opcionális feladatlap-kapcsolatai.
10. Nagymozgás és testtudat → Egyensúlyfejlesztő játékok kategóriaág és a „Sétálj a vonalon!” mintaoldal.
11. Szülői ügyfélfelület, reszponzív játékáttekintő, részletes játékoldal és hozzáférési jelzések.
12. Admin „Megtekintés szülőként” szimulált látogatói, előfizetői és játékcsomag-tulajdonosi nézetekkel.
13. Teljes játékcsomag összeállítása fejlesztő játékokból és az azokhoz tartozó kötelező/kiegészítő feladatlapokból; feladatlap önálló nyilvános értékesítése nélkül.
14. Szülői fiókok, előfizetések, vásárlások és szerveroldali hozzáférési jogosultságok.
15. Fizetési szolgáltató, védett fájlkiszolgálás, számlázás, lemondás, visszatérítés és kiadásra kész ellenőrzés.
