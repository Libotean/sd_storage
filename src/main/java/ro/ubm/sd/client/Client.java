package ro.ubm.sd.client;

public class Client {
    public static void main(String[] args) {
        String coordinator = null;

        for (int i = 0; i< args.length; i++){
            if (args[i].equals("--coordinator") && i + 1 < args.length){
                coordinator = args[++i];
            } else {
                usage("argument necunoscut sau fara valoare: " + args[i]);
            }
        }

        if (coordinator == null || !coordinator.contains(":")){
            usage("lipseste --coordinator in forma adresa:port");
        }

        System.out.println("[client] coordonator configurat: " + coordinator + "; comenzi: put, get, ls, rm, nodes, exit");

    }

    private static void usage(String eroare) {
        System.err.println("Eroare: " + eroare);
        System.err.println("Utilizare: Client --coordinator localhost:7001");
        System.exit(1);
    }
}