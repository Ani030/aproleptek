# Fájlelnevezési és verziózási konvenció

## Felhasználó által feltöltött fájlok

- A feltöltés előtt adj a fájlnak rövid, kisbetűs, ékezet nélküli nevet; a szavakat kötőjel válassza el.
- A név azonosítsa a feladatlapot, az oldal szerepét és – ha kell – a tartalmat: `<feladatlap-slug>-<szerep>-<tartalom>-v<verzió>.<kiterjesztés>`.
- Az oldal szerepe `elonezet`, `alaplap` vagy sorszámozott `kivaghato-01`, `kivaghato-02` legyen.
- Példa: `erdei-allatok-alaplap-v1.pdf`, `erdei-allatok-kivaghato-01-makkok-v2.png`.
- Csak a rendszer által elfogadott PNG, JPEG, WebP és PDF formátum használható. A kiterjesztés egyezzen a fájl tényleges formátumával.
- Ne kerüljön a fájlnévbe gyermek neve, személyes adat vagy változó állapotjelzés (például `vegleges-uj`).

## Tárolás és verziócsere

- A szerver minden feltöltéskor új, véletlenszerű UUID-alapú tárolási kulcsot ad a fájlnak. A fájl nem az eredeti nevével kerül a tárba.
- Az eredeti fájlnév metaadatként megmarad, ezért a fenti elnevezés jelenik meg az adminisztrációban és a letöltésnél.
- Fájl frissítésekor új fájlt kell feltölteni, a verziószámot növelni, majd az új fájlt hozzárendelni. A meglévő tárolási kulcsot tilos felülírni.
- A `v1`, `v2` számozás kézi szerkesztési konvenció. A rendszer minden feltöltést külön fájlként tárol, de jelenleg nem tart fenn automatikus verzióláncot vagy visszaállítási funkciót.
- A fájlok és adatbázis-metaadataik együtt tartandók meg; a visszaállításukra vonatkozó folyamatot a külön mentési dokumentáció határozza meg.
