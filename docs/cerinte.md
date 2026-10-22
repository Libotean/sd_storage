# Cerinte - Sistem distribuit de stocare a fisierelor

## 1. Cerinte functionale: operatiile

| Operatie | Comanda | Pasii prin sistem | Rezultat | Erori tratate |
|---|---|---|---|---|
| Incarcare | put raport.pdf | 1. clientul cere coordonatorului doua noduri active, distincte; 2. trimite fisierul primului nod; 3. primul nod il copiaza pe al doilea; 4. dupa ambele confirmari, coordonatorul inregistreaza metadatele | ID-ul fisierului si cele doua noduri | mai putin de 2 noduri active: refuz, nimic stocat · fisier local inexistent · nume deja existent: refuz · fisier peste 1 GB: refuz · o copie esuata: se alege alt nod |
| Descarcare | get raport.pdf ./out/ | 1. clientul cere coordonatorului locatiile copiilor; 2. descarca de la primul nod; 3. verifica checksum-ul | fisierul salvat local, cu checksum verificat | fisier necunoscut · primul nod nu raspunde sau checksum gresit: incearca a doua copie · nicio copie accesibila: eroare explicita |
| Listare | ls | 1. clientul cere coordonatorului lista fisierelor; 2. coordonatorul raspunde din tabela de metadate, fara sa contacteze nodurile | tabel cu nume, dimensiune, ID si nodurile care detin fiecare copie | coordonator inaccesibil: eroare explicita · niciun fisier: mesajul "niciun fisier stocat" |
| Stergere | rm raport.pdf | 1. clientul cere coordonatorului stergerea dupa nume; 2. coordonatorul gaseste ID-ul si trimite stergerea fiecarui nod activ care detine o copie; 3. nodurile sterg fisierul si confirma; 4. coordonatorul elimina metadatele | confirmarea stergerii si lista copiilor sterse | fisier necunoscut: NOT_FOUND · un nod cu o copie e cazut: stergerea reuseste pe nodurile active, metadatele se elimina; la revenire nodul raporteaza ce detine, coordonatorul vede un ID necunoscut si ii cere sa-l stearga · coordonator inaccesibil: eroare explicita |
| Starea sistemului | nodes | 1. clientul cere coordonatorului lista nodurilor; 2. coordonatorul raspunde din tabela de noduri | pentru fiecare nod: ID, adresa:port, stare (ACTIV/SUSPECT/CAZUT), vechimea ultimului heartbeat, numarul de fisiere; coordonatorul este marcat | coordonator inaccesibil: eroare explicita |
| Adaugarea unui nod (la rulare) | java ... StorageNode --id n4 --port 7004 --dir data/n4 | 1. n4 porneste si isi creeaza directorul; 2. se anunta prin UDP multicast (ID, adresa, port); 3. coordonatorul si celelalte noduri il adauga in lista lor cu starea ACTIV; 4. n4 raporteaza coordonatorului fisierele din directorul lui; 5. n4 incepe sa trimita heartbeat | n4 apare in nodes; urmatoarele incarcari il pot alege | ID deja folosit de un nod activ: n4 este refuzat si se opreste cu mesaj · port ocupat: n4 se opreste la pornire cu mesaj · fisiere necunoscute in raport (nod care revine): coordonatorul cere stergerea lor |

### Decizii de proiectare

- **Identificarea fisierelor:** utilizatorul foloseste numele (get raport.pdf). Intern, fiecare fisier are un UUID generat de coordonator la incarcare. Nodurile salveaza fisierul pe disc sub UUID, deci numele cu spatii, diacritice sau ../ nu pot crea probleme pe disc. Numele sunt unice in sistem; coordonatorul tine corespondenta nume -> UUID.
- **Incarcarea unui nume existent:** este refuzata cu eroare. Utilizatorul sterge intai fisierul vechi cu rm. Astfel cele doua copii ale unui fisier nu pot ajunge versiuni diferite.
- **Dimensiunea maxima:** 1 GB per fisier. Testul de scalabilitate foloseste fisiere de 100 MB, deci limita lasa loc, iar o depasire primeste un mesaj de eroare clar inca de la ALLOCATE, inainte de orice transfer.

## 2. Modelul de date

Coordonatorul tine in memorie doua tabele: metadatele fisierelor (cheie: numele, unic in sistem) si lista nodurilor (cheie: ID-ul nodului).

### Metadatele unui fisier

| Camp | Tip Java | Exemplu | Stabilit de |
|---|---|---|---|
| identificator | UUID | 3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90 | coordonator, la incarcare |
| nume | String | raport.pdf | utilizator |
| dimensiune | long (octeti) | 482133 | client |
| checksum | String (SHA-256, hex) | 9f86d081... | client, verificat de fiecare nod |
| locatiile copiilor | List&lt;String&gt; (ID-uri de nod) | [n2, n3] | coordonator, dupa confirmari |

### Un nod din lista

| Camp | Tip Java | Exemplu |
|---|---|---|
| ID nod | String | n2 |
| adresa si port | String, int | 192.168.37.102, 7002 |
| stare | enum | ACTIV, SUSPECT, CAZUT (folosit din Etapa 3) |
| ultimul semn de viata | long (ms) | System.currentTimeMillis() la ultimul heartbeat |

Checksum-ul se calculeaza cu MessageDigest.getInstance("SHA-256") din JDK. El dovedeste ca fisierul descarcat este identic cu cel incarcat, indiferent de nodul care l-a servit.

### Stocarea pe disc a unui nod

Fiecare copie ocupa doua fisiere in directorul nodului:

- data/n2/UUID: continutul fisierului;
- data/n2/UUID.meta: ID, nume, dimensiune si checksum, in format text cheie=valoare.

Fisierul .meta permite reconstruirea metadatelor (Etapa 6): daca coordonatorul cade, noul coordonator cere fiecarui nod lista fisierelor detinute. Din fisierele .meta reface numele, dimensiunea si checksum-ul, iar locatiile copiilor sunt nodurile care au raportat fiecare UUID.

### Regula de alegere a nodurilor pentru o copie noua

Dintre nodurile cu starea ACTIV, coordonatorul alege cele doua noduri distincte care detin cele mai putine fisiere; la egalitate decide ID-ul nodului (ordine alfabetica). Regula este determinista (usor de testat), echilibreaza incarcarea si face ca un nod nou adaugat la rulare sa fie folosit imediat. Coordonatorul este si el nod de stocare si poate fi ales.

## 3. Cerinte nefunctionale si obiective masurabile

| Cerinta | Obiectiv masurabil | Cum se masoara | Etapa | De ce aceasta tinta |
|---|---|---|---|---|
| Integritate | 0 fisiere cu checksum diferit din 100 de descarcari | script care incarca si descarca 100 de fisiere | 2 | orice fisier corupt inseamna date pierdute, deci tinta e zero |
| Descoperire | un nod nou apare in lista celorlalti in cel mult 3 s | diferenta dintre momentele din loguri | 2 | anuntul multicast se trimite periodic (de exemplu la 1 s), deci 3 s acopera si pierderea unui anunt |
| Detectarea defectelor | un nod oprit cu kill -9 este marcat cazut in cel mult 5 s | loguri cu timestamp pe fiecare nod | 3 | cu heartbeat la 1 s, cateva heartbeat-uri lipsa inseamna sigur un nod cazut, nu o intarziere |
| Scalabilitate | debitul de incarcare cu 3 noduri creste fata de 1 nod | 10 fisiere de 100 MB, timp total | 4 | cu mai multe noduri, incarcarile se impart, deci se scrie in paralel |
| Coordonare | noul coordonator este ales in cel mult 10 s dupa caderea celui vechi | loguri Bully | 5 | 5 s pentru detectarea caderii plus cateva secunde pentru mesajele de alegere |
| Replicare | fiecare fisier are exact 2 copii pe noduri distincte | comanda ls si directoarele data/ | 6 | cerinta temei: o copie poate cadea, cealalta ramane |
| Disponibilitate | dupa oprirea oricarui nod, 100% din fisiere raman descarcabile | descarcarea tuturor fisierelor dupa oprire | 7 | consecinta directa a celor 2 copii pe noduri distincte |

### Ipoteze de lucru

Obiectivele de mai sus sunt valabile doar in aceste conditii:

- toate nodurile ruleaza in aceeasi retea locala (sau pe acelasi calculator, pe porturi diferite);
- cel mult un nod este cazut la un moment dat;
- defectele sunt de tip crash: un nod fie functioneaza corect, fie se opreste complet; nu trimite date gresite intentionat (nu bizantin);
- un nod cazut poate reveni ulterior cu acelasi ID si acelasi director.

## 4. Scenariul demonstratiei

Fiecare comanda se ruleaza din directorul proiectului, dupa compilare (mvn -q package). Fiecare nod si clientul ruleaza in terminalul lui.

| Pas | Actiunea operatorului | Comanda exacta | Ce trebuie sa se vada | Functioneaza din etapa |
|---|---|---|---|---|
| 1 | porneste nodurile n1, n2, n3 in terminale separate, apoi clientul | terminal 1: java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator; terminal 2: java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2; terminal 3: java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3; terminal 4: java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001 | fiecare nod ii descopera pe ceilalti; n1 este coordonator | 2 |
| 2 | incarca un fisier din client | put raport.pdf | ID-ul fisierului si doua noduri distincte care il detin | 2 |
| 3 | descarca fisierul | get raport.pdf ./out/ | fisierul salvat in ./out/, cu checksum identic | 2 |
| 4 | porneste nodul n4, fara a opri ceva | terminal 5: java -cp target/classes ro.ubm.sd.node.StorageNode --id n4 --port 7004 --dir data/n4; apoi in client: nodes | n4 apare in nodes; urmatoarele incarcari il pot folosi | 4 |
| 5 | opreste brusc un nod care detine raport.pdf | in client: ls (se vede ce noduri au fisierul); jps -l (se afla PID-ul nodului); Windows: taskkill /F /PID numar, Linux: kill -9 numar; apoi in client: nodes si get raport.pdf ./out/ | nodul este marcat cazut; get raport.pdf reuseste din cealalta copie | 3 |
| 6 | opreste brusc coordonatorul | jps -l (PID-ul lui n1); Windows: taskkill /F /PID numar, Linux: kill -9 numar; apoi in client: nodes | nodurile ramase aleg un nou coordonator | 5 |
| 7 | verifica fisierele dupa realegere | in client: ls, apoi get raport.pdf ./out/ | metadatele sunt reconstruite; fisierul ramane accesibil | 6 |

## 5. Schita protocolului

Fiecare mesaj are un antet (o linie de text); cand mesajul transporta un fisier, continutul urmeaza imediat dupa antet, ca exact dimensiune octeti. Formatul exact si implementarea vin in Etapa 2. Mesajele de descoperire, heartbeat si alegere a liderului se adauga in etapele 2, 3 si 5.

| Mesaj | De la -> catre | Continut | Raspuns |
|---|---|---|---|
| ALLOCATE | client -> coordonator | nume, dimensiune | NODES cu ID-ul fisierului si 2 noduri, sau ERROR |
| PUT | client -> nod | ID, nume, dimensiune, checksum, nodul pentru copie + octetii fisierului | STORED sau ERROR |
| REPLICATE | nod -> nod | ca PUT, fara nodul urmator | STORED sau ERROR |
| COMMIT | nod -> coordonator | ID fisier, nodurile care il detin | OK |
| LOCATE | client -> coordonator | nume sau ID | LOCATIONS cu metadatele, sau NOT_FOUND |
| GET | client -> nod | ID fisier | antet cu dimensiunea + octetii, sau NOT_FOUND |
| LIST | client -> coordonator | nimic | FILES cu lista fisierelor (nume, dimensiune, ID, nodurile cu copii); lista poate fi goala |
| REMOVE | client -> coordonator | nume | REMOVED cu nodurile de pe care s-a sters, sau NOT_FOUND |
| DELETE | coordonator -> nod | ID fisier | DELETED sau NOT_FOUND (nodul sterge continutul si fisierul .meta) |
| STATUS | client -> coordonator | nimic | NODE_LIST cu fiecare nod (ID, adresa:port, stare, ultimul heartbeat, numarul de fisiere) si ID-ul coordonatorului |
