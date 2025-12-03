package com.example.servingwebcontent;
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

    public static HashMap<String, Set<String>> buildHashMap(String text, String url) {
        HashMap<String, Set<String>> hm = new HashMap<>();
        String[] words = text.split("\\W+");
        for (String w : words) {
            if (w.isBlank()) continue;
            hm.computeIfAbsent(w.toLowerCase(), k -> new HashSet<>()).add(url);
        }
        return hm;
    }

    public static void refreshBarrels(Registry registry, List<BarrelInterface> barrels) {
        try {
            String[] bound = registry.list();
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

    public static void parallelIndexing(
            url_queue urls,
            List<BarrelInterface> barrels,
            ConcurrentLinkedDeque<String> processados,
            Registry registry
    ) {
        try (ForkJoinPool pool = new ForkJoinPool(parallel_threshold)) {

            for (int i = 0; i < parallel_threshold; i++) {

                pool.execute(() -> {
                    try {
                        while (true) {

                            refreshBarrels(registry, barrels);
                            String url = urls.getNextUrl();

                            if (url != null && !processados.contains(url)) {

                                System.out.println("\n==============================");
                                System.out.println("[Downloader] A indexar: " + url);
                                System.out.println("Thread: " + Thread.currentThread().getName());
                                System.out.println("==============================\n");

                                Document doc = download_page(url);
                                String texto = getText(doc);
                                addUrls(urls, doc);

                                HashMap<String, Set<String>> hm = buildHashMap(texto, url);

                                String titulo = doc.title();
                                String snippet = texto.length() > 100 ? texto.substring(0, 100) : texto;

                                PageInfo page = new PageInfo(url, titulo, snippet);
                                page.addOutLink(doc);

                                Map<String, PageInfo> pageInfoBatch = new HashMap<>();
                                pageInfoBatch.put(url, page);

                                // iterar com iterator para permitir remover com segurança
                                Iterator<BarrelInterface> it = barrels.iterator();

                                while (it.hasNext()) {
                                    BarrelInterface b = it.next();

                                    // tentamos obter nome (pode falhar) e também o stubId via toString()
                                    String barrelName = null;
                                    String stubId = null;
                                    try { barrelName = b.getName(); } catch (Exception ignored) {}
                                    try { stubId = b.toString(); } catch (Exception ignored) {}

                                    boolean success = false;

                                    for (int tentativa = 1; tentativa <= 3; tentativa++) {
                                        // usa barrelName para a print se disponível, caso contrário usa "Barrel"
                                        String nameForPrint = (barrelName != null) ? barrelName : "Barrel";
                                        System.out.println("[Downloader] Enviando página para " + nameForPrint + " (tentativa " + tentativa + ")");

                                        try {
                                            b.putIndex(hm, pageInfoBatch);
                                            System.out.println("[Downloader] ? Página indexada com sucesso no " + nameForPrint);
                                            success = true;
                                            break;
                                        } catch (Exception e) {
                                            System.out.println("[Downloader] Falha ao enviar para " + nameForPrint + " na tentativa " + tentativa);
                                        }
                                    }

                                    if (!success) {
                                        String nameForPrint = (barrelName != null) ? barrelName : "Barrel";
                                        System.err.println("[Downloader] Erro ao indexar " + nameForPrint + " — REMOVIDO");

                                        // tenta pedir shutdown graciosamente
                                        try { b.shutdown(); } catch (Exception ignored) {}

                                        // remove da lista local com o iterator (já estamos a usar it)
                                        it.remove();

                                        // tenta remover do registry: primeiro via barrelName se conhecido,
                                        // senão procura por stubId comparando toString() dos stubs no registry
                                        boolean unbound = false;
                                        if (barrelName != null) {
                                            try {
                                                registry.unbind(barrelName);
                                                System.out.println("[Downloader] Barrel removido do registry: " + barrelName);
                                                unbound = true;
                                            } catch (Exception e) {
                                                System.err.println("[Downloader] Falha ao remover do registry: " + barrelName);
                                            }
                                        }

                                        if (!unbound && stubId != null) {
                                            try {
                                                String[] names = registry.list();
                                                for (String candidateName : names) {
                                                    if (!candidateName.startsWith("Barrel")) continue;
                                                    try {
                                                        Object stubObj = registry.lookup(candidateName);
                                                        if (stubObj != null && stubObj.toString().equals(stubId)) {
                                                            try {
                                                                registry.unbind(candidateName);
                                                                System.out.println("[Downloader] Barrel removido do registry: " + candidateName);
                                                                unbound = true;
                                                                break;
                                                            } catch (Exception e) {
                                                                System.err.println("[Downloader] Falha ao remover do registry: " + candidateName);
                                                            }
                                                        }
                                                    } catch (Exception ignored) {}
                                                }
                                            } catch (Exception ignored) {}
                                        }

                                        if (!unbound) {
                                            System.err.println("[Downloader] Não foi possível remover binding do registry para " + nameForPrint);
                                        }
                                    }
                                }

                                processados.add(url);
                            }

                            Thread.sleep(500);
                        }
                    } catch (Exception e) {
                        System.err.println("[Downloader] ERRO inesperado no worker: " + e.getMessage());
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
                System.out.println("[Downloader] Nenhum barrel encontrado, aguardando...");
                return;
            }

            parallelIndexing(queue, barrels, urls_processados, registry);

            System.out.println("[Downloader] finished.");

        } catch (Exception e) {
            System.err.println("[Downloader] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
