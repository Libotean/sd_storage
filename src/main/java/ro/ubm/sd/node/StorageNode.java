package ro.ubm.sd.node;

import java.nio.file.Files;
import java.nio.file.Path;


public class StorageNode {
    public static void main(String[] args) throws Exception {
        String id = null;
        int port = -1;
        String dir = null;
        boolean coordinator = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--id" -> id = args[++i];
                    case "--port" -> port = Integer.parseInt(args[++i]);
                    case "--dir" -> dir = args[++i];
                    case "--coordinator" -> coordinator = true;
                    default -> usage("argument necunoscut: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            usage("valoare lipsa sau port invalid");
        }
        if (id == null || dir == null || port < 1 || port > 65535) {
            usage("lipsesc argumente obligatorii sau portul nu e intre 1 si 65535");
        }
        Files.createDirectories(Path.of(dir));
        String rol = coordinator ? "coordonator" : "nod de stocare";
        System.out.println("[" + id + "] pornit: port " + port + ", director " + dir + ", rol: " + rol);

    }

    private static void usage(String eroare){
        System.err.println("Eroare: " + eroare);
        System.err.println("Utilizare: StorageNode --id n1 --port 7001 --dir data/n1 [--coordinator]");
        System.exit(1);
    }
}