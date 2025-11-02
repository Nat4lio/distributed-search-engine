import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class client2 {

    private static volatile GatewayInterface gateway = null;
    private static volatile boolean gatewayDown = false;
    private static String gatewayName = "Gateway";
    private static final String registryHost = "localhost";
    private static final int registryPort = 1099;

    public static void main(String[] args) {
        System.setProperty("java.rmi.server.hostname", "localhost");
        gatewayName = (args.length >= 1) ? args[0] : "Gateway";

        tryConnectGateway();

        Thread monitor = new Thread(() -> {
            boolean lastStatus = gatewayDown;
            while (true) {
                try {
                    Thread.sleep(3000);

                    if (gatewayDown) {
                        tryConnectGateway();

                        // Reconexão confirmada só após verificação de estabilidade
                        if (!gatewayDown && lastStatus != gatewayDown) {
                            try {
                                Thread.sleep(1500); // pequena espera para estabilizar RMI
                                gateway.getStats(); // teste extra
                                printBanner("GATEWAY ONLINE NOVAMENTE");
                                lastStatus = gatewayDown;
                            } catch (Exception e) {
                                gatewayDown = true;
                                gateway = null;
                            }
                        }
                    } else {
                        if (gateway != null) {
                            try {
                                gateway.getStats();
                            } catch (Exception e) {
                                gatewayDown = true;
                                gateway = null;
                                printBanner("GATEWAY OFFLINE");
                                lastStatus = true;
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();

        Scanner sc = new Scanner(System.in);
        printHeader();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Escolha uma opção: ");
            String option = sc.nextLine().trim();

            if (gatewayDown || gateway == null) {
                if (!option.equals("0")) {
                    System.out.println("\n[Aviso] Ação impossível agora - Gateway indisponível.\n");
                }
                if (option.equals("0")) running = false;
                continue;
            }
            String line;

            try {
                switch (option) {
                   case "1":
                        System.out.print("Introduza os termos de pesquisa: ");
                        String[] parts = sc.nextLine().trim().split("\\s+");
                        List<String> terms = Arrays.asList(parts);
                        System.out.println("Pagina?");
                        line = sc.nextLine();
                        int npag = Integer.parseInt(line);
                        List<SearchResult> results = gateway.search(terms, npag, 10);
                        System.out.println("\n🔎 Resultados (página "+npag+"):");
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
                        boolean ok = gateway.indexPage(p, map);
                        System.out.println(ok ? "Página indexada com sucesso."
                                : "Falha ao indexar página.");
                        break;

                    case "3":
                        System.out.print("Introduza a URL: ");
                        String target = sc.nextLine().trim();
                        Set<String> inbound = gateway.inboundLinks(target);
                        System.out.println("\nLinks inbound (" + inbound.size() + "):");
                        if (inbound.isEmpty()) System.out.println("(nenhum link encontrado)");
                        for (String s : inbound) System.out.println("  - " + s);
                        break;

                    case "4":
                        Map<String, Object> stats = gateway.getStats();
                        System.out.println("\nEstatísticas gerais:");
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
                        System.out.println("Opção inválida. Tente novamente.");
                        break;
                }

            } catch (RemoteException e) {
                System.out.println("[Erro] Falha de comunicação com o Gateway - ligação perdida.");
                gatewayDown = true;
                gateway = null;
                printBanner("GATEWAY OFFLINE");
            } catch (Exception e) {
                System.out.println("[Erro] " + e.getMessage());
            }
        }

        System.out.println("Encerrando cliente...");
        sc.close();
    }

    private static void tryConnectGateway() {
        try {
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            GatewayInterface gw = (GatewayInterface) registry.lookup(gatewayName);
            gw.getStats(); // teste de ligação
            gateway = gw;
            gatewayDown = false;
        } catch (Exception e) {
            gatewayDown = true;
            gateway = null;
        }
    }

    private static void printHeader() {
        System.out.println(
                "╔════════════════════════════════════════╗\n" +
                "║          SISTEMA DE PESQUISA SD         ║\n" +
                "╚════════════════════════════════════════╝");
    }

    private static void printMenu() {
        System.out.println("\n===== MENU PRINCIPAL =====");
        System.out.println("1 -> Pesquisar termos");
        System.out.println("2 -> Indexar manualmente uma página");
        System.out.println("3 -> Ver links inbound de uma URL");
        System.out.println("4 -> Mostrar estatísticas");
        System.out.println("0 -> Sair");
    }

    private static void printBanner(String message) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.printf("%30s%n", message);
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();
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
