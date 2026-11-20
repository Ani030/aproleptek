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
- [x] Eladható termékváltozat kapcsolása feladatlaphoz vagy csomagtervhez, formátummal, listaárral és becsült anyagköltséggel. *(Liquibase `sale_product` tábla, termék API és admin űrlap.)*
- [x] Több terméksor és mennyiség rögzítése egy eladásban. *(Admin eladási űrlap és tranzakciós `/sales` API.)*
- [x] Listaár-összeg, elfogadott végösszeg és egyeztetési különbözet mentése; terméksoronkénti összeg automatikus arányosítása.
- [x] Eladás dátuma, értékesítési csatornája (például Vinted) és megjegyzése rögzíthető.
- [x] Múltbeli eladás termék- és áradatainak pillanatképe megmarad későbbi árváltozás után is.
- [x] Termékenkénti darabszám, eladási gyakoriság és bevétel kimutatása. *(Adminban összesített bevétel és legnépszerűbb termékek.)*
- [x] Pénzügyi felület külön, felül váltható Áttekintő, Egyszeri kiadások, Fogyóanyagok és Eladások nézetekkel.
- [x] Termékenkénti fedezet és időszakválasztós statisztika. *(A termékrangsor a történeti bevételt és anyagköltség levonása utáni fedezetet mutatja; az áttekintő eladási bevétele és becsült anyagköltsége dátumtartomány szerint szűrhető.)*
- [x] Egyszeri eszközkiadások és fogyóanyag-beszerzések naplózása készletnyilvántartás nélkül. *(A beszerzési mennyiségből és teljes árból egységár készül; az anyagok átlagos egységára súlyozottan jelenik meg.)*
- [x] Nincs készletvezetés, automatikus készletcsökkentés vagy automatikus rendelés-/fizetésintegráció ebben az ütemben.
- [x] Fogyóanyag-beszerzések és egységár-számítás megvalósítása készletvezetés nélkül.
- [x] Fogyóanyagok mennyiségi összekapcsolása a termékváltozatokkal, automatikus anyagköltség-számítással. *(A termék anyaglistája a súlyozott átlagos egységárakból számol; új beszerzés frissíti az érintett termékváltozatok aktuális költségét. Az eladási sorok költségpillanatképe nem változik.)*
- [x] Nyomtatási díj automatikus hozzáadása a fizikai termékváltozatok gyártási költségéhez, egy- és kétoldalas beállítással. *(A Pénzügy → Áttekintő nézetében állítható a nyomtatott oldalankénti díj; a rendszer a papírlapok és a nyomtatott felületek száma alapján számol, az eladás pedig a teljes gyártási költséget rögzíti pillanatképként.)*
- [x] Termékadatlap megnyitása külön szerkesztőnézetben: felhasznált fogyóanyagok, darabonkénti mennyiség, számolt anyagköltség és tervezett eladási ár.
- [x] Eladás szerkesztése, dátum ISO-formátumú kezelése és csomagolóanyag-választás eladásonként. *(A csomagolás súlyozott átlagos egységárból számolódik, mentéskor pillanatképként rögzül és arányosan kerül az eladási terméksorokra.)*

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

## 10. Későbbi ütemek – most nem blokkolják az MVP-t

### Publikus katalógus

- [ ] Publikált feladatlapok nyilvános listája és részletező oldala.
- [ ] Publikus játékötlet-kereső korosztály és téma szerint.
- [ ] SEO renderelési megoldás kiválasztása (előrenderelés vagy SSR).

### Webshop

- [ ] Digitális és fizikai termékváltozatok.
- [ ] Kosár és rendelési folyamat.
- [ ] Fizetési szolgáltató integráció.
- [ ] Digitális letöltés biztonságos hozzáféréssel.
- [ ] Fizikai készlet, szállítási módok és rendelésállapotok.

### Fiókok, ajánló és klub

- [ ] Szülői fiók és kedvencek.
- [ ] Szűrőalapú „Mit játsszunk ma?” ajánló.
- [ ] Heti játéknaptár és nyomtatható terv.
- [ ] Előfizetéses klub és tagsági hozzáférések.

## 11. Megvalósítási sorrend

1. Projektváz és helyi fejlesztői környezet.
2. Adatmodell és Liquibase migrációk.
3. Admin belépés és szerepkör-védelem.
4. Fájltár és feltöltési API.
5. Feladatlap + kivágható lap backend.
6. Taxonómia és admin keresés: `TASK_TYPE` migráció, külön Állatok és Természet címkék, régi összevont címke biztonságos kivezetése.
7. API-szűrők és feladatlapkapcsolatok bővítése korosztály, feladattípus, témakör és készségterület szerint.
8. React admin kategóriakezelő, feladatlap-szerkesztő és külön feladattípus/témakör szűrők.
9. Játékötletek és csomagtervek.
10. Tartalomfeltöltés és elfogadási ellenőrzés, külön állat és természet témákkal és feladattípus szerinti kereséssel.
11. Későbbi publikus katalógus és webshop tervezése.
