import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class client {
    public static void main(String[] args) {
        try {
            String gatewayName = "Gateway";
            String host =  "localhost";
            int port = 1099;

            Registry registry = LocateRegistry.getRegistry(host, port);
            GatewayInterface gw = (GatewayInterface) registry.lookup(gatewayName);

            Scanner sc = new Scanner(System.in);
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║        🔍 SISTEMA DE PESQUISA SD        ║");
            System.out.println("╚════════════════════════════════════════╝");

            boolean running = true;
            while (running) {
                System.out.println("\n===== MENU PRINCIPAL =====");
                System.out.println("1️->  Pesquisar termos");
                System.out.println("2️->  Indexar manualmente uma página");
                System.out.println("3️->  Ver links inbound de uma URL");
                System.out.println("4️->  Mostrar estatísticas");
                System.out.println("0️->  Sair");
                System.out.print("Escolha uma opção: ");

                String option = sc.nextLine().trim();
                String line;
                switch (option) {
                    case "1":
                        System.out.print("Introduza os termos de pesquisa: ");
                        String[] parts = sc.nextLine().trim().split("\\s+");
                        List<String> terms = Arrays.asList(parts);
                        System.out.println("Pagina?");
                        line = sc.nextLine();
                        int npag = Integer.parseInt(line);
                        List<SearchResult> results = gw.search(terms, npag, 10);
                        System.out.println("\n🔎 Resultados (página "+npag+"+):");
                        if (results.isEmpty()) System.out.println("(nenhum resultado encontrado)");
                        for (SearchResult r : results) System.out.println(r);
                        break;

                    case "2":
                        System.out.print("URL: ");
                        String url = sc.nextLine().trim();
                        System.out.print("Título: ");
                        String title = sc.nextLine().trim();
                        System.out.print("Snippet: ");
                        String snippet = sc.nextLine().trim();
                        PageInfo p = new PageInfo(url, title, snippet);
                        Map<String, Set<String>> map = new HashMap<>();
                        StreamTokenizerHelper.tokenizeToMap(title + " " + snippet, url, map);
                        boolean ok = gw.indexPage(p, map);
                        System.out.println(ok ? "✅ Página indexada com sucesso." : "❌ Falha ao indexar.");
                        break;

                    case "3":
                        System.out.print("Introduza a URL: ");
                        String target = sc.nextLine().trim();
                        Set<String> inbound = gw.inboundLinks(target);
                        System.out.println("\n🔗 Links inbound (" + inbound.size() + "):");
                        if (inbound.isEmpty()) System.out.println("(nenhum link encontrado)");
                        for (String s : inbound) System.out.println("  - " + s);
                        break;

                    case "4":
                        Map<String, Object> stats = gw.getStats();
                        System.out.println("\n📊 Estatísticas gerais:");
                        System.out.println("Cache do Gateway: " + stats.get("gatewayCachedKeys"));
                        List<?> barrels = (List<?>) stats.get("barrels");
                        if (barrels != null)
                            System.out.println("Barrels registados: " + barrels.size());
                        System.out.println(stats);
                        break;

                    case "0":
                        running = false;
                        break;

                    default:
                        System.out.println("❗ Opção inválida. Tente novamente.");
                        break;
                }
            }

            System.out.println("👋 Encerrando cliente...");
            sc.close();
        } catch (Exception e) {
        }
    }
}

class StreamTokenizerHelper {
    public static void tokenizeToMap(String text, String url, Map<String, Set<String>> map) {
        if (text == null) return;
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (!w.trim().isEmpty()) {
                map.putIfAbsent(w, new HashSet<>());
                map.get(w).add(url);
            }
        }
    }
}
