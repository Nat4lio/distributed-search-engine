import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.*;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Downloader com descoberta dinâmica de novos barrels.
 * A cada nova página indexada, tenta atualizar a lista de barrels no registry.
 */
public class downloader {
    static int parallel_threshold = 8;

    public static Document download_page(String url) throws IOException {
        return Jsoup.connect(url).userAgent("GoogolBot/1.0").timeout(10_000).get();
    }

    public static String getText(Document doc) { return doc.text(); }

    public static void addUrls(url_queue urls, Document doc) throws RemoteException {
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String u = link.absUrl("href");
            if (u != null && !u.isBlank()) urls.addUrl(u);
        }
    }

    public static HashMap<String,Set<String>> buildHashMap(String text,String url){
    HashMap<String,Set<String>> hm = new HashMap<>();
    String[] words = text.split("\\W+");
    for (String w :words){
        hm.computeIfAbsent(url, k -> new HashSet<>()).add(w);
    }
    return hm;
}

    // --- Método NOVO: atualiza dinamicamente a lista de barrels no registry ---
    public static void refreshBarrels(Registry registry, List<BarrelInterface> barrels) {
        try {
            String[] bound = registry.list();
            Set<String> known = new HashSet<>();
            for (BarrelInterface b : barrels) {
                try {
                    // comparar referências para evitar duplicados
                    b.ping();
                    known.add(b.toString());
                } catch (Exception ignored) {}
            }
            for (String name : bound) {
                if (name.startsWith("Barrel")) {
                    try {
                        BarrelInterface b = (BarrelInterface) registry.lookup(name);
                        if (!barrels.contains(b)) {
                            barrels.add(b);
                            System.out.println("[Downloader] Novo barrel detectado: " + name);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[Downloader] Erro ao atualizar barrels: " + e.getMessage());
        }
    }


    public static void parallelIndexing(url_queue urls,List<BarrelInterface> barrels,ConcurrentLinkedDeque<String> processados,Registry registry){
    try (ForkJoinPool pool = new ForkJoinPool(parallel_threshold)) {
        for(int i = 0;i<parallel_threshold;i++){
        pool.execute(() -> { 
            try {
                while (true) {
                    refreshBarrels(registry, barrels);
                    String url = urls.getNextUrl();
                    if (url!=null && !processados.contains(url)) {

                        System.out.println("Thread " + Thread.currentThread().getName() + " processando URL");

                        Document doc = download_page(url);
                        String texto = getText(doc);
                        addUrls(urls, doc);
                        HashMap<String,Set<String>> hm = buildHashMap(texto, url);

                        String titulo = doc.title();
                        String snippet = texto.length() > 100 ? texto.substring(0, 100) : texto;
                        PageInfo page = new PageInfo(url, titulo, snippet);

                        Map<String, PageInfo> pageInfoBatch = new HashMap<>();
                        pageInfoBatch.put(url, page);

                        for (BarrelInterface b : barrels) {
                            try {
                                b.putIndex(hm, pageInfoBatch);
                                System.out.println("O " + b.getName() + " atualizou às " + new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()));
                            } catch (RemoteException e) {}
                        }
                        processados.add(url);
                    }
                    Thread.sleep(500);
                }
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }
        });
        }
        pool.shutdown();
    }
}

    public static void main(String[] args) {
        System.setProperty("java.rmi.server.hostname", "localhost");
        ConcurrentLinkedDeque<String> urls_processados = new ConcurrentLinkedDeque<>();
        String registryHost = (args.length >= 1) ? args[0] : "localhost";
        int registryPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 1099;
        try {
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            url_queue queue = (url_queue) registry.lookup("queue");

            List<BarrelInterface> barrels = new ArrayList<>();
            refreshBarrels(registry, barrels);

            if (barrels.isEmpty()) {
                System.out.println("[Downloader] Nenhum barrel encontrado; aguardando...");
                return;
            }

            parallelIndexing(queue, barrels,urls_processados, registry);
            System.out.println("[Downloader] finished.");
        } catch (Exception e) {
            System.err.println("[Downloader] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
