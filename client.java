import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * Cliente de exemplo para testar a Gateway.
 * Usage: java Client <gateway_name> <registry_host> <registry_port>
 */
public class client {

    public static void main(String[] args) {
        try {
            String gatewayName = (args.length >= 1) ? args[0] : "Gateway";
            String host = (args.length >= 2) ? args[1] : "localhost";
            int port = (args.length >= 3) ? Integer.parseInt(args[2]) : 1099;

            Registry registry = LocateRegistry.getRegistry(host, port);
            GatewayInterface gw = (GatewayInterface) registry.lookup(gatewayName);

            Scanner sc = new Scanner(System.in);
            System.out.println("Connected to Gateway. Type commands: search <terms...> | index <url> <title> <snippet> | inbound <url> | stats | exit");
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.equalsIgnoreCase("exit")) break;
                if (line.startsWith("search ")) {
                    String[] parts = line.substring(7).split("\\s+");
                    List<String> terms = Arrays.asList(parts);
                    List<SearchResult> results = gw.search(terms, 1, 10);
                    System.out.println("Results (page 1):");
                    for (SearchResult r : results) {
                        System.out.println(r);
                    }
                } else if (line.startsWith("index ")) {
                    // minimal example: index <url> <title> <snippet>
                    String[] parts = splitArgs(line.substring(6), 3);
                    String url = parts[0];
                    String title = parts[1];
                    String snippet = parts[2];
                    PageInfo p = new PageInfo(url, title, snippet);
                    // no exemplo não temos outlinks nem tokenização complexa: indexa palavra do título e snippet
                    Map<String, Set<String>> map = new HashMap<>();
                    StreamTokenizerHelper.tokenizeToMap(title + " " + snippet, url, map);
                    boolean ok = gw.indexPage(p, map);
                    System.out.println("Index result: " + ok);
                } else if (line.startsWith("inbound ")) {
                    String url = line.substring(8).trim();
                    Set<String> in = gw.inboundLinks(url);
                    System.out.println("Inbound (" + in.size() + "):");
                    for (String s : in) System.out.println("  " + s);
                } else if (line.equals("stats")) {
                    Map<String,Object> st = gw.getStats();
                    System.out.println("Stats: " + st);
                } else {
                    System.out.println("Unknown command");
                }
            }

            System.out.println("Client exiting.");
            sc.close();
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // helper simple to split into n parts (naive)
    private static String[] splitArgs(String s, int n) {
        String[] out = new String[n];
        for (int i = 0; i < n-1; i++) {
            int idx = s.indexOf(' ');
            if (idx == -1) {
                out[i] = s;
                s = "";
            } else {
                out[i] = s.substring(0, idx);
                s = s.substring(idx+1).trim();
            }
        }
        out[n-1] = s;
        return out;
    }
}

/**
 * Pequeno utilitário interno para tokenizar strings em palavras e preencher um map palavra->set(url).
 * (evita dependências externas)
 */
class StreamTokenizerHelper {
    public static void tokenizeToMap(String text, String url, Map<String, Set<String>> map) {
        if (text == null) return;
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.trim().isEmpty()) continue;
            map.computeIfAbsent(w, k -> new HashSet<>()).add(url);
        }
    }
}
