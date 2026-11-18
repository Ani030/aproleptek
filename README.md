# ApróLéptek

Az első fejlesztési ütem adminisztrációs felülete a feladatlapok, a hozzájuk tartozó kivágható oldalak, önálló játékötletek és kategóriák kezelésére.

## Technológia

- `server/`: Java 21+, Spring Boot 4.1.1, REST API, Spring Security Basic adminhitelesítés
- PostgreSQL 18 fejlesztői adatbázis Docker Compose-szal
- Liquibase adatbázis-migrációk
- `admin/`: React, TypeScript, Vite, Tailwind CSS és shadcn/ui-hoz előkészített komponensrendszer
- Fejlesztéskor helyi fájltár (`server/uploads`); később objektumtár-adapterre cserélhető

## Helyi indítás

Szükséges: Java 21 vagy újabb, Maven, Node.js 20.19+ (vagy 22.12+), npm és Docker.

1. Indítsd el a PostgreSQL-t a projekt gyökeréből:

   ```sh
   docker compose up -d postgres
   ```

2. Indítsd el a backendet. Válassz saját admin jelszót:

   ```sh
   cd server
   APP_ADMIN_USERNAME=admin APP_ADMIN_PASSWORD='sajat-hosszu-jelszo' mvn spring-boot:run
   ```

   Az API a `http://localhost:8080` címen indul. Liquibase automatikusan alkalmazza a verziózott migrációkat.

3. Másik terminálban indítsd el az admin felületet:

   ```sh
   cd admin
   npm install
   npm run dev
   ```

4. Nyisd meg a `http://localhost:5173` címet, és lépj be a megadott admin névvel és jelszóval.

Az admin API címe alapértelmezésben `http://localhost:8080/api/v1`. Eltérő környezetben az `admin/.env.local` fájlban állítható:

```dotenv
VITE_API_URL=https://pelda.hu/api/v1
```

## Adatbázis és fájlok

- A helyi adatbázis neve `aproleptek`, felhasználója `aproleptek`; a fejlesztői jelszó csak a Compose konfigurációban szerepel.
- Éles környezetben a `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, `APP_STORAGE_ROOT` és `APP_CORS_ALLOWED_ORIGIN` változókat titokkezelőből kell megadni.
- A Liquibase changelogok a `server/src/main/resources/db/changelog/changes` mappában vannak. Már lefuttatott changesetet ne módosíts; új sémaváltozás új changeset legyen.
- A feltöltött fájlok nem kerülnek a PostgreSQL-be. A metaadatok az adatbázisban, a fájlok az `APP_STORAGE_ROOT` mappában vannak.
- Az éles Basic Auth végpontot kizárólag HTTPS mögött szabad elérhetővé tenni. A jelenlegi egyadminos belépés MVP-megoldás; több admin felhasználó és adatbázisban tárolt jogosultság a következő biztonsági bővítési feladat.

## Jelenlegi funkciók

- Admin belépés és védett admin API-k.
- Feladatlap CRUD, piszkozat/ellenőrzés/publikált/archivált állapot.
- Egy alap feladatlaphoz 0..N kivágható oldal, sorrenddel és oldalszámmal.
- PNG, JPEG, WebP és PDF fájlfeltöltés, legfeljebb 20 MB fájlonként.
- Korosztály, témakör és készségterület címkék, keresés és szűrés.
- Önálló játékötlet CRUD.
- Csomagterv adatmodell előkészítve; rendelés és fizetés még nincs.

## Dokumentáció

- [Termék- és technikai terv](docs/termek-es-technikai-terv.md)
- [Implementációs checklist](docs/implementacios-checklist.md)
