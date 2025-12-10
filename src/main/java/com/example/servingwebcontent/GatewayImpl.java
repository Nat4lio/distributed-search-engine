package com.example.servingwebcontent;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GatewayImpl - coordena queries e delega em Barrels.
 * Acrescenta: contagem top searches, tempos médios de pesquisa, inclusão de id em barrels stats.
 */
public class GatewayImpl extends UnicastRemoteObject implements GatewayInterface {

    private static final long serialVersionUID = 1L;

    private final List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());
    private final List<String> barrelNames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger rr = new AtomicInteger(0);

    private final Map<String, List<SearchResult>> cache;
    private final int CACHE_SIZE = 100;

    private final AtomicInteger barrelCounter = new AtomicInteger(1);

    // Contador de pesquisas por query (para top 10)
    private final Map<String, Integer> searchFrequency = Collections.synchronizedMap(new HashMap<>());

    // Tempos das pesquisas (ms)
    private final List<Double> searchTimes = Collections.synchronizedList(new ArrayList<>());

    protected GatewayImpl() throws RemoteException {
        super();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, List<SearchResult>>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                return size() > CACHE_SIZE;
            }
        });

        // Monitor de saúde dos barrels
        Thread monitor = new Thread(this::healthMonitor);
        monitor.setDaemon(true);
        monitor.start();
        System.out.println("[Gateway] Health monitor iniciado.");
    }

    private void healthMonitor() {
        while (true) {
            try {
                Thread.sleep(10_000);
                synchronized (barrels) {
                    Iterator<BarrelInterface> it = barrels.iterator();
                    int index = 0;
                    while (it.hasNext()) {
                        BarrelInterface b = it.next();
                        String name = barrelNames.get(index);
                        try {
                            if (!b.ping()) {
                                System.err.println("[Gateway] Barrel inativo: " + name);
                                it.remove();
                                barrelNames.remove(index);
                                continue;
                            }
                        } catch (RemoteException e) {
                            System.err.println("[Gateway] Falha ao contactar " + name + " -> removido.");
                            it.remove();
                            barrelNames.remove(index);
                            continue;
                        }
                        index++;
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
        return name;
    }

    private BarrelInterface chooseBarrel() throws RemoteException {
        if (barrels.isEmpty()) throw new RemoteException("No barrels registered");
        int idx = Math.abs(rr.getAndIncrement()) % barrels.size();
        return barrels.get(idx);
    }

    @Override
    public List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException {
        if (terms == null || terms.isEmpty()) return Collections.emptyList();
        String key = String.join(" ", terms).toLowerCase();

        // conta a pesquisa
        searchFrequency.merge(key, 1, Integer::sum);

        long start = System.nanoTime();

        List<SearchResult> cached = cache.get(key);
        if (cached != null) {
            double msCached = (System.nanoTime() - start) / 1_000_000.0;
            searchTimes.add(msCached);
            return paginate(cached, pageNumber, pageSize);
        }

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
        cache.put(key, results);

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
            cache.clear();
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

        // tempos médios (ms) -> converte para décimas se desejares (o frontend mostra "décimas")
        double avgMs = searchTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        // se quiseres em décimas de segundo:
        double avgDecimas = avgMs / 100.0; // ms -> décimas de segundo (1 décima = 100 ms)
        m.put("avgTimes", Map.of("global", avgDecimas));

        // stats dos barrels (cada barrel devolve um Map). Inserimos o id/nome em cada Map.
        List<Map<String, Object>> bs = new ArrayList<>();
        for (int i = 0; i < barrels.size(); i++) {
            BarrelInterface b = barrels.get(i);
            String id = barrelNames.get(i);
            try {
                Map<String, Object> stats = b.getStats();
                if (stats == null) stats = new HashMap<>();
                // inclui id (pode já existir em barrel, mas garantimos)
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
