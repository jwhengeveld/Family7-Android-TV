# Family7 op de televisie — een voorstel

Dit document hoort bij de Android TV-app in deze repository. Het is bedoeld om
aan Family7 voor te leggen: wat de app is, hoe hij werkt, en wat er nodig zou
zijn om hem officieel te maken.

---

## Waar het om gaat

Family7 is via de website goed te bekijken, maar op een televisie is een
browser onhandig: er is geen muis, en de afstandsbediening werkt niet mee. Deze
app brengt Family7 Plus naar het startscherm van Android TV en Google TV, met
bediening die op de bank hoort — pijltjes, OK, terug.

Hij is gebouwd door één ontwikkelaar, uit interesse en omdat de behoefte er
thuis was. Hij draait dagelijks op een Meta Portal Go en op Android TV.

**De app is nadrukkelijk niet gepubliceerd in de Play Store.** Dat kan ook niet
zonder Family7: naam en logo zijn van Family7, en de winkel eist daarvoor
toestemming van de merkhouder. Vandaar dit document.

---

## Wat de app doet

- **Live TV** — de Family7 Plus-uitzending, met de gidsgegevens van "nu op tv".
- **On Demand** — de volledige videotheek: uitgelicht, nieuw, categorieën,
  programmapagina's met seizoenen en afleveringen.
- **Kids** — de kinderrubriek, opgezocht in het menu van Family7 Plus zodat een
  verhuizing van die pagina vanzelf meegaat.
- **Mijn lijst** — dezelfde lijst als op de website. Wat de kijker in de app
  bewaart, staat ook op family7.nl en op zijn andere apparaten.
- **Zoeken en A-Z** — op titel en thema, inclusief vervolgpagina's.
- **Standaard tv-bediening** — Media3 met een MediaSession, zodat "Now playing"
  en de mediatoetsen van de afstandsbediening werken.

Er staat geen catalogus in de app. Alles komt live van family7.nl; wat Family7
publiceert, verschijnt vanzelf.

---

## Wat de app níét doet

- Er is **geen server van de maker**. De app praat rechtstreeks met family7.nl,
  net als een browser. Er zit niets tussen.
- Er wordt **niets verzameld**: geen statistieken, geen advertenties, geen
  crashrapportage, geen profielen. Zie [PRIVACY.md](PRIVACY.md).
- De app **omzeilt geen abonnement**. Kijken vereist inloggen met een eigen
  Family7-account; de app toont uitsluitend wat die gebruiker via zijn eigen
  account al mag zien.
- De app **herdistribueert niets**. Er wordt niets gedownload, opgeslagen of
  doorgegeven; de stream wordt afgespeeld en verder niets.

---

## Hoe de app technisch werkt

De app doet precies wat de website in een browser ook doet, alleen zonder
browser. Concreet:

| Wat | Adres |
|---|---|
| Aanmelden | `POST https://www.family7.nl/user/login` (het gewone Drupal-formulier) |
| Sessiecontrole | `GET https://www.family7.nl/user` |
| Startscherm Plus | `GET https://www.family7.nl/plus` |
| Nieuw | `GET https://www.family7.nl/plus/nieuw` |
| A-Z | `GET https://www.family7.nl/plus/a-z?title=All` |
| Programmapagina | `GET https://www.family7.nl/plus/programmas/{slug}` |
| Aflevering | `GET https://www.family7.nl/video/{slug}` |
| Mijn lijst | `GET /plus/mijnlijst`, `GET /plus/mijnlijst/{node-id}/add\|remove` |
| Live | `GET https://www.family7.nl/plus/live` |
| Stream | de spelerpagina van Streampartner en de HLS-stream daarachter |

De pagina's worden gelezen met Jsoup. Het adres van de videostream staat in de
speler van Streampartner ingepakt in een `eval(function(w,i,s,e){…})`-vorm; de
app pakt die op dezelfde manier uit als de browser dat doet.

**Dat laatste is precies het punt waarop dit beter kan.** Zolang de app HTML
leest, breekt hij bij elke verbouwing van de site — voor alle gebruikers
tegelijk. Een kleine, afgebakende API (catalogus, programma, aflevering,
streamadres, mijn lijst) zou de app stabiel maken en Family7 de vrijheid geven
de site te veranderen zonder de tv-app te slopen.

---

## Waarschijnlijk hoeft er weinig gebouwd te worden

Streampartner noemt op zijn eigen site een **REST API in JSON en XML** als
standaardonderdeel van het platform, naast whitelabel-accounts en embed-players
die klanten in hun eigen stream-panel configureren
([streampartner.nl](https://www.streampartner.nl/),
[FAQ](https://www.streampartner.nl/faq),
[Kerk-tv](https://www.streampartner.nl/kerktv/)). Family7 is klant van
Streampartner — de streams lopen over `highvolume08.streampartner.nl` en zelfs
het domein `family7.tv` wordt door hun nameservers bediend.

Die API bestaat ook echt en draait op **`https://api.streampartner.nl/`**. Een
verzoek zonder sleutel antwoordt met:

```json
{"errors":{"title":"No API key and ACCESS key sent in header",
 "detail":"Please add your API key and ACCESS key to your request and put it in
 the header called 'Api-Key' and 'Access-Key'",
 "code":"ERR_401_NO_API_KEY_NO_API_ACCESS_KEY_IN_HEADER","status":"401"}}
```

Het is dus een gewone sleutel-API met twee headers, `Api-Key` en `Access-Key`,
per klant. De documentatie is niet openbaar: er is geen developer-portal en
geen schema-endpoint, en volgens de FAQ van Streampartner staat de uitleg over
de API-scripts en de embed-player in het **klantpanel**
([ssl.streampartner.nl/panel](https://ssl.streampartner.nl/panel/login.php) /
[my.streampartner.eu](https://my.streampartner.eu/panel/index.php?language=nl)).
Family7 kan die documentatie en een sleutel daar opvragen; een buitenstaander
kan dat niet.

Aardig precedent: in de kerk-tv-pakketten noemt Streampartner een
"Scipio App koppeling". Een app van een derde partij die op hun platform
aansluit is voor hen dus niets nieuws.

Met andere woorden: het stuk waar deze app nu het meeste moeite voor doet — bij
een streamadres komen — is bij Streampartner vermoedelijk gewoon een
API-aanroep. Er hoeft dan niets nieuws gebouwd te worden; het gaat om
toegang en een afspraak.

Twee dingen om daarbij scherp te houden:

**1. Streampartner dekt de video, niet de rest.** De catalogus, de
programmapagina's, de seizoenen, "Mijn lijst" en het inloggen zitten in de
Drupal-site van Family7 zelf. Een Streampartner-API lost het streamdeel op; het
lezen van family7.nl blijft nodig zolang daar niets tegenover staat. Het zou
dus om twee gesprekken gaan, of om één gesprek waarin Family7 beide kanten
regelt.

**2. Een API-sleutel hoort niet in de app.** Wat in een geïnstalleerde app zit,
is eruit te halen — een Streampartner-sleutel in de APK zou de streams voor
iedereen openzetten, ook zonder abonnement. De veilige vorm is dat Family7 de
poortwachter blijft: de app logt in bij family7.nl, en Family7 geeft de app
daarna een kortlopend streamadres dat zij zelf bij Streampartner ophalen. De
sleutel blijft dan bij Family7, en het abonnement blijft bepalen wie wat mag
zien.

_Kanttekening: wat de API precies kan — alleen streams en statistieken, of ook
VOD-metadata — is van buitenaf niet te zien, omdat de documentatie achter het
klantpanel zit. Bovenstaande is gebaseerd op wat Streampartner zelf op zijn
site adverteert en op het foutbericht dat de API zonder sleutel teruggeeft._

---

## Staat van de code

- Kotlin, Jetpack Compose for TV, Media3 ExoPlayer. Android 7.0 en hoger,
  gericht op API 35.
- Release-builds worden verkleind met R8 (2,8 MB APK) en ondertekend met een
  sleutel die niet in de repository staat.
- GitHub Actions bouwt, test, controleert de handtekening en controleert dat de
  release niet debuggable is en geen onversleuteld verkeer toestaat.
- Het wachtwoord wordt nergens bewaard. De aanmeldsessie gaat versleuteld met
  AES-256-GCM naar schijf, met een sleutel uit de AndroidKeyStore, en blijft
  buiten back-ups.
- Cookies gaan alleen terug naar het domein, pad en beveiligingsniveau waarvoor
  Family7 ze heeft afgegeven.
- Unit tests op de gevoelige onderdelen: het uitpakken van het streamadres, de
  cookieregels en de cache.

---

## Wat wij van Family7 zouden vragen

1. **Toestemming voor naam en logo**, zodat de app in de Play Store mag staan.
   Google vraagt daar schriftelijk bewijs van, vooraf aan te melden.
2. **Akkoord op de manier waarop de app de dienst gebruikt** — of, liever nog,
   een gesprek over een eenvoudige API in plaats van het lezen van pagina's.
   Voor het streamdeel gaat het waarschijnlijk niet om bouwen maar om
   aanzetten: de REST API die Streampartner standaard levert, met Family7 als
   poortwachter ervoor.
3. **Een besluit over het eigenaarschap.** Wat ons betreft zijn alle opties
   bespreekbaar: Family7 neemt de app over en publiceert hem zelf, de app wordt
   onder toezicht van Family7 uitgegeven, of hij blijft waar hij is en verdwijnt
   op verzoek.

Zonder toestemming blijft de app waar hij nu is: als broncode en als los te
installeren bestand op GitHub, voor wie hem zelf op zijn tv wil zetten.

---

## Contact

Via de GitHub-repository van dit project. Een verzoek van Family7 om het
gebruik van hun merk of content te staken wordt gehonoreerd.
