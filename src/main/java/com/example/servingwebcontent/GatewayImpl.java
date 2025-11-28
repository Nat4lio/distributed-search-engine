package com.example.servingwebcontent;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GatewayImpl - coordena queries e delega em Barrels.
 * Agora com monitor automático de falhas e recuperação dinâmica.
 */
public class GatewayImpl extends UnicastRemoteObject implements GatewayInterface {

    private static final long serialVersionUID = 1L;

    private final List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());
    private final List<String> barrelNames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger rr = new AtomicInteger(0);
    private final Map<String, List<SearchResult>> cache;
    private final int CACHE_SIZE = 100;

    // Contador global de nomes únicos
    private final AtomicInteger barrelCounter = new AtomicInteger(1);

    protected GatewayImpl() throws RemoteException {
        super();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, List<SearchResult>>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                return size() > CACHE_SIZE;
            }
        });

        // Inicia thread de monitorização
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
                            System.err.println("[Gateway] Falha ao contactar " + name + " -> removido temporariamente.");
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

        // Se já existir pelo menos um barrel, tentamos sincronizar do último
        if (!barrels.isEmpty()) {
            BarrelInterface source = barrels.get(barrels.size() - 1);
            try {
                Map<String, Object> full = source.getFullIndex();
                if (full != null) {
                    // Extrai estruturas esperadas (conforme implementado em BarrelImpl.getFullIndex)
                    @SuppressWarnings("unchecked")
                    Map<String, Set<String>> inv = (Map<String, Set<String>>) full.get("invertedIndex");
                    @SuppressWarnings("unchecked")
                    Map<String, PageInfo> pgs = (Map<String, PageInfo>) full.get("pages");
                    try {
                        // envia o índice completo para o novo barrel
                        stub.putIndex(inv, pgs);
                        int words = (inv == null) ? 0 : inv.size();
                        int pagesCount = (pgs == null) ? 0 : pgs.size();
                        System.out.println("[Gateway] Novo barrel sincronizado: " + words + " palavras, " + pagesCount + " páginas copiadas.");
                    } catch (RemoteException re) {
                        System.err.println("[Gateway] Falha ao sincronizar novo barrel (putIndex): " + re.getMessage());
                    }
                } else {
                    System.out.println("[Gateway] getFullIndex devolveu null — nada a sincronizar.");
                }
            } catch (RemoteException e) {
                System.err.println("[Gateway] Falha ao obter índice do barrel existente: " + e.getMessage());
            }
        } else {
            System.out.println("[Gateway] Primeiro barrel — sem sincronização necessária.");
        }

        // agora registamos o novo barrel
        barrels.add(stub);
        barrelNames.add(name);
        System.out.println("[Gateway] Novo barrel registado: " + name);
        return name;
    }


    // -------------------------------------------------------------
    // Escolha de Barrel (round-robin)
    // -------------------------------------------------------------
    private BarrelInterface chooseBarrel() throws RemoteException {
        if (barrels.isEmpty()) throw new RemoteException("No barrels registered");
        int idx = Math.abs(rr.getAndIncrement()) % barrels.size();
        return barrels.get(idx);
    }

    // -------------------------------------------------------------
    // Pesquisa
    // -------------------------------------------------------------
    @Override
    public List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException {
        if (terms == null || terms.isEmpty()) return Collections.emptyList();
        String key = String.join(" ", terms).toLowerCase();
        List<SearchResult> cached = cache.get(key);
        if (cached != null) return paginate(cached, pageNumber, pageSize);

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
                } catch (RemoteException e) {}
            }
            if (p == null) p = new PageInfo(u, "(no-title)", "(no-snippet)");
            results.add(new SearchResult(u, p.title, p.snippet, inboundCount));
        }

        results.sort((a, b) -> Integer.compare(b.score, a.score));
        cache.put(key, results);
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

    // -------------------------------------------------------------
    // Indexação
    // -------------------------------------------------------------
    @Override
    public boolean indexPage(PageInfo page, Map<String, Set<String>> pageWords) throws RemoteException {
        if (page == null || page.url == null) throw new RemoteException("Invalid page");
        int RETRIES = 3;
        boolean anySuccess = false;
        for (int i = 0; i < barrels.size(); i++) {
            BarrelInterface b = barrels.get(i);
            boolean success = false;
            for (int r = 0; r < RETRIES; r++) {
                try {
                    Map<String, Set<String>> partial = (pageWords == null) ? new HashMap<>() : new HashMap<>(pageWords);
                    Map<String, PageInfo> pagesBatch = new HashMap<>();
                    pagesBatch.put(page.url, page);
                    b.putIndex(partial, pagesBatch);
                    success = true;
                    anySuccess = true;
                    break;
                } catch (RemoteException e) {
                    System.err.println("[Gateway] putIndex failed on barrel " + i + " attempt " + r + ": " + e.getMessage());
                }
            }
            if (!success)
                System.err.println("[Gateway] WARNING - barrel " + i + " failed after retries; continuing.");
        }
        cache.clear();
        return anySuccess;
    }

    // -------------------------------------------------------------
    // Inbound Links + Estatísticas
    // -------------------------------------------------------------
    @Override
    public Set<String> inboundLinks(String url) throws RemoteException {
        Set<String> aggregated = new HashSet<>();
        for (BarrelInterface b : barrels) {
            try {
                Set<String> s = b.inboundLinks(url);
                if (s != null) aggregated.addAll(s);
            } catch (RemoteException e) {}
        }
        return aggregated;
    }

    @Override
    public Map<String, Object> getStats() throws RemoteException {
        Map<String, Object> m = new HashMap<>();
        m.put("gatewayCachedKeys", cache.size());
        List<Map<String, Object>> bs = new ArrayList<>();
        for (BarrelInterface b : barrels) {
            try {
                bs.add(b.getStats());
            } catch (RemoteException e) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("error", e.getMessage());
                bs.add(fail);
            }
        }
        m.put("barrels", bs);
        return m;
    }

    // -------------------------------------------------------------
    // Main
    // -------------------------------------------------------------
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
