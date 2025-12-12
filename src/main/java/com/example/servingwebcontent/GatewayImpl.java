package com.example.servingwebcontent;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GatewayImpl - coordena queries e delega em Barrels.
 * Correções aplicadas: cache com expiração automática, armazenamento de timestamp, LRU.
 */
public class GatewayImpl extends UnicastRemoteObject implements GatewayInterface {

    private static final long serialVersionUID = 1L;

    private final List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());
    private final List<String> barrelNames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger rr = new AtomicInteger(0);

    // Cache corrigida
    private final Map<String, CacheEntry> cache;
    private final int CACHE_SIZE = 100;
    private final long CACHE_TTL_MS = 3000; // 3 segundos de validade
    private StatsService statsService;
    private final Map<String, Integer> barrelSizes = Collections.synchronizedMap(new HashMap<>());
    private final java.util.concurrent.Executor notifyExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private final AtomicInteger barrelCounter = new AtomicInteger(1);

    // Contador de pesquisas por query (para top 10)
    private final Map<String, Integer> searchFrequency = Collections.synchronizedMap(new HashMap<>());

    // Tempos das pesquisas (ms)
    private final List<Double> searchTimes = Collections.synchronizedList(new ArrayList<>());

    public void setStatsService(StatsService s) {
    this.statsService = s;
}

    protected GatewayImpl() throws RemoteException {
        super();

        // LRU Cache com tamanho máximo
        this.cache = Collections.synchronizedMap(
            new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
        );

        // Monitor de saúde dos barrels
        Thread monitor = new Thread(this::healthMonitor);
        monitor.setDaemon(true);
        monitor.start();
        System.out.println("[Gateway] Health monitor iniciado.");
    }

    private void healthMonitor() {
    while (true) {
        try {
            Thread.sleep(500);

            synchronized (barrels) {
                for (int i = barrels.size() - 1; i >= 0; i--) {
                    BarrelInterface b = barrels.get(i);
                    String name = barrelNames.get(i);
                    boolean removed = false;
                    try {
                        if (!b.ping()) {
                            System.err.println("[Gateway] Barrel inativo: " + name);
                            
                            barrels.remove(i);
                            barrelNames.remove(i);
                            barrelSizes.remove(name);
                            removed = true;
                        } else {
                            
                            Map<String, Object> bstats = null;
                            try {
                                bstats = b.getStats();
                            } catch (RemoteException re) {
                                System.err.println("[Gateway] falha a obter stats de " + name + " -> " + re.getMessage());
                            }

                            if (bstats != null) {
                                Object sizeObj = bstats.get("size");
                                int newSize = -1;
                                newSize = ((Number) sizeObj).intValue();
                                int oldSize = barrelSizes.getOrDefault(name, -1);
                                if (newSize != -1 && newSize != oldSize) {
                                    barrelSizes.put(name, newSize);
                                    if (statsService != null) {
                                        Map<String, Object> snapshot = null;
                                        try {
                                            snapshot = getStats();
                                        } catch (RemoteException re) {
                                            System.err.println("[Gateway] falha a obter gateway stats: " + re.getMessage());
                                        }
                                        final Map<String, Object> toSend = (snapshot == null) ? Map.of("error", "empty-stats") : snapshot;
                                        notifyExecutor.execute(() -> {
                                            try {
                                                statsService.pushStats(toSend);
                                            } catch (Exception e) {
                                                System.err.println("[Gateway] erro ao notificar statsService: " + e.getMessage());
                                            }
                                        });
                                    }
                                }
                            }
                        }

                        if (removed) {
                            if (statsService != null) {
                                Map<String, Object> snapshot = null;
                                try {
                                    snapshot = getStats();
                                } catch (RemoteException re) {
                                    System.err.println("[Gateway] falha a obter gateway stats: " + re.getMessage());
                                }
                                final Map<String, Object> toSend = (snapshot == null) ? Map.of("error", "empty-stats") : snapshot;
                                notifyExecutor.execute(() -> {
                                    try {
                                        statsService.pushStats(toSend);
                                    } catch (Exception e) {
                                        System.err.println("[Gateway] erro ao notificar statsService: " + e.getMessage());
                                    }
                                });
                            }
                        }

                    } catch (RemoteException e) {
                        System.err.println("[Gateway] Erro ao contactar " + name + " -> removido. " + e.getMessage());
                        try {
                            barrels.remove(i);
                            barrelNames.remove(i);
                            barrelSizes.remove(name);
                        } catch (Exception ex) {
                           
                        }
                        if (statsService != null) {
                            Map<String, Object> snapshot = null;
                            try {
                                snapshot = getStats();
                            } catch (RemoteException re) {
                                System.err.println("[Gateway] falha a obter gateway stats: " + re.getMessage());
                            }
                            final Map<String, Object> toSend = (snapshot == null) ? Map.of("error", "empty-stats") : snapshot;
                            notifyExecutor.execute(() -> {
                                try {
                                    statsService.pushStats(toSend);
                                } catch (Exception ex) {
                                    System.err.println("[Gateway] erro ao notificar statsService: " + ex.getMessage());
                                }
                            });
                        }
                    }
                } 
            } 

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            System.err.println("[Gateway] Erro no health monitor: " + e.getMessage());
        }
    }
}

    @Override
    public synchronized String registerNewBarrel(BarrelInterface stub) throws RemoteException {
        String name = "Barrel" + barrelCounter.getAndIncrement();

        // Se já existir pelo menos um barrel, tenta sincronizar a partir do último
        if (!barrels.isEmpty()) {
            BarrelInterface source = barrels.get(barrels.size() - 1);
            try {
                Map<String, Object> full = source.getFullIndex();
                if (full != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Set<String>> inv = (Map<String, Set<String>>) full.get("invertedIndex");
                    @SuppressWarnings("unchecked")
                    Map<String, PageInfo> pgs = (Map<String, PageInfo>) full.get("pages");
                    try {
                        stub.putIndex(inv, pgs);
                        int words = (inv == null) ? 0 : inv.size();
                        int pagesCount = (pgs == null) ? 0 : pgs.size();
                        System.out.println("[Gateway] Novo barrel sincronizado: " + words + " palavras, " + pagesCount + " páginas copiadas.");
                    } catch (RemoteException re) {
                        System.err.println("[Gateway] Falha ao sincronizar novo barrel : " + re.getMessage());
                    }
                } else {
                    System.out.println("[Gateway] getFullIndex devolveu null — nada a sincronizar.");
                }
            } catch (RemoteException e) {
                System.err.println("[Gateway] Falha ao obter índice do barrel existente: " + e.getMessage());
            }
        } else {
            System.out.println("[Gateway] Primeiro barrel - sem sincronização necessária.");
        }

        barrels.add(stub);
        barrelNames.add(name);
        System.out.println("[Gateway] Novo barrel registado: " + name);
        if (statsService != null) {
        statsService.pushStats(getStats());
        }
        return name;
    }

    private BarrelInterface chooseBarrel() throws RemoteException {
        if (barrels.isEmpty()) throw new RemoteException("No barrels registered");
        int idx = Math.abs(rr.getAndIncrement()) % barrels.size();
        return barrels.get(idx);
    }

    // Classe interna para cache com timestamp
    private static class CacheEntry {
        List<SearchResult> results;
        long timestamp;

        CacheEntry(List<SearchResult> results) {
            this.results = results;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException {
        if (terms == null || terms.isEmpty()) return Collections.emptyList();
        String key = String.join(" ", terms).toLowerCase();

        // conta a pesquisa
        searchFrequency.merge(key, 1, Integer::sum);

        if (statsService != null) {
        try {
            statsService.pushStats(getStats());
        } catch (RemoteException e) {
            System.err.println("[Gateway] falha ao notificar StatsService: " + e.getMessage());
        }
        }

        long start = System.nanoTime();

        CacheEntry cached = cache.get(key);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            double msCached = (System.nanoTime() - start) / 1_000_000.0;
            searchTimes.add(msCached);
            return paginate(cached.results, pageNumber, pageSize);
        }

        // Se expirou → remove
        cache.remove(key);

        Exception lastEx = null;
        Set<String> urls = null;
        for (int attempt = 0; attempt < Math.max(1, barrels.size()); attempt++) {
            try {
                BarrelInterface b = chooseBarrel();
                Set<String> resultSet = b.searchUrls(terms);
                urls = new HashSet<>(resultSet);
                break;
            } catch (RemoteException re) {
                lastEx = re;
                System.err.println("[Gateway] barrel failed, trying another: " + re.getMessage());
            }
        }
        if (urls == null) throw new RemoteException("All barrels failed", lastEx);

        List<SearchResult> results = new ArrayList<>();
        for (String u : urls) {
            PageInfo p = null;
            int inboundCount = 0;
            for (BarrelInterface b : barrels) {
                try {
                    p = b.getPageInfo(u);
                    Set<String> inbound = b.inboundLinks(u);
                    inboundCount = (inbound == null) ? 0 : inbound.size();
                    break;
                } catch (RemoteException e) {
                    // try next barrel
                }
            }
            if (p == null) p = new PageInfo(u, "(no-title)", "(no-snippet)");
            results.add(new SearchResult(u, p.title, p.snippet, inboundCount));
        }

        results.sort((a, b) -> Integer.compare(b.score, a.score));
        cache.put(key, new CacheEntry(results));

        long end = System.nanoTime();
        double ms = (end - start) / 1_000_000.0;
        searchTimes.add(ms);

        return paginate(results, pageNumber, pageSize);
    }

    private List<SearchResult> paginate(List<SearchResult> list, int pageNumber, int pageSize) {
        int page = Math.max(1, pageNumber);
        int size = Math.max(1, pageSize);
        int from = (page - 1) * size;
        if (from >= list.size()) return Collections.emptyList();
        int to = Math.min(list.size(), from + size);
        return new ArrayList<>(list.subList(from, to));
    }

    @Override
    public boolean indexPage(String url) throws RemoteException {
        if (url == null || url.isBlank())
            throw new RemoteException("URL inválido");

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            url_queue q = (url_queue) registry.lookup("queue");

            q.addUrl(url);

            // Limpando cache antigo que contém este URL
            cache.entrySet().removeIf(entry -> entry.getValue().results.stream().anyMatch(r -> r.url.equals(url)));

            System.out.println("[Gateway] URL enviada para indexação: " + url);
            return true;

        } catch (Exception e) {
            throw new RemoteException("Falha ao adicionar URL à queue: " + e.getMessage());
        }
    }

    @Override
    public Set<String> inboundLinks(String url) throws RemoteException {
        Set<String> aggregated = new HashSet<>();
        for (BarrelInterface b : barrels) {
            try {
                Set<String> s = b.inboundLinks(url);
                if (s != null) aggregated.addAll(s);
            } catch (RemoteException e) {
                // ignore per-barrel failures
            }
        }
        return aggregated;
    }

    @Override
    public Map<String, Object> getStats() throws RemoteException {
        Map<String, Object> m = new HashMap<>();

        // top 10 pesquisas
        List<String> top10 = searchFrequency.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
        m.put("topSearches", top10);

        // tempos médios (ms)
        double avgMs = searchTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgDecimas = avgMs / 100.0; // ms -> décimas de segundo
        avgDecimas = Math.round(avgDecimas * 10000.0) / 10000.0; 
        m.put("avgTimes", Map.of("global", avgDecimas));

        // stats dos barrels
        List<Map<String, Object>> bs = new ArrayList<>();
        for (int i = 0; i < barrels.size(); i++) {
            BarrelInterface b = barrels.get(i);
            String id = barrelNames.get(i);
            try {
                Map<String, Object> stats = b.getStats();
                if (stats == null) stats = new HashMap<>();
                stats.putIfAbsent("id", id);
                bs.add(stats);
            } catch (RemoteException e) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("id", id);
                fail.put("error", e.getMessage());
                bs.add(fail);
            }
        }
        m.put("barrels", bs);

        // opcional: cache size
        m.put("gatewayCachedKeys", cache.size());

        return m;
    }

    public static void main(String[] args) {
        try {
            String name = (args.length >= 1) ? args[0] : "Gateway";
            String registryHost = (args.length >= 2) ? args[1] : "localhost";
            int registryPort = (args.length >= 3) ? Integer.parseInt(args[2]) : 1099;

            System.setProperty("java.rmi.server.hostname", "localhost");
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

            GatewayImpl gw = new GatewayImpl();
            registry.rebind(name, gw);
            System.out.println("[Gateway] bound as '" + name + "' on " + registryHost + ":" + registryPort);
        } catch (Exception e) {
            System.err.println("[Gateway] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

}