# sd-storage - Sistem distribuit de stocare a fisierelor

Proiect la disciplina Sisteme Distribuite. Fisierele sunt stocate pe mai multe noduri, fiecare fisier are doua copii pe noduri distincte, iar un nod este coordonator si tine metadatele. Cerintele, modelul de date, scenariul demonstratiei si schita protocolului sunt in docs/cerinte.md.

## Echipa

- Libotean Bogdan

## Necesar

- JDK 17 sau mai nou
- Maven 3.9 sau mai nou

## Compilare

Din directorul proiectului:

    mvn -q package

Clasele compilate ajung in target/classes.

## Pornire

Fiecare proces ruleaza in terminalul lui. Nodurile (cate un terminal pentru fiecare):

    java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
    java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2
    java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3

Clientul:

    java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001

### Argumente

| Proces | Argument | Semnificatie |
|---|---|---|
| StorageNode | --id | identificatorul nodului, de exemplu n1 |
| StorageNode | --port | portul pe care asculta nodul (1-65535) |
| StorageNode | --dir | directorul in care nodul pastreaza fisierele; se creeaza daca lipseste |
| StorageNode | --coordinator | optional; nodul porneste ca si coordonator (temporar, pana la alegerea prin Bully din Etapa 5) |
| Client | --coordinator | adresa:port a coordonatorului |

La Etapa 1 fiecare proces afiseaza configuratia primita si se opreste:

    [n1] pornit: port 7001, director data/n1, rol: coordonator
    [client] coordonator configurat: localhost:7001; comenzi: put, get, ls, rm, nodes, exit
