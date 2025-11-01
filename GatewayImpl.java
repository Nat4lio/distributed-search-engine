import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementação da Gateway:
 *  - mantém uma lista de Barrels (endereços RMI)
 *  - selecciona Barrel por round-robin para queries de leitura
 *  - para writes (indexPage) faz replicação simples: tenta enviar a todos os Barrels; se um falhar, faz retry n vezes e regista erro.
 *  - implementa cache LRU para resultados de pesquisa (simples LinkedHashMap)
 *  - expõe métodos para o cliente
 */
public class GatewayImpl extends UnicastRemoteObject implements GatewayInterface {

    private static final long serialVersionUID = 1L;

    // Lista de referências para Barrels (stubs)
    private final List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());
    private final List<String> barrelNames = Collections.synchronizedList(new ArrayList<>());

    // Round-robin counter
    private final AtomicInteger rr = new AtomicInteger(0);

    // Cache simples: key = join(terms), value = list<SearchResult>
    private final Map<String, List<SearchResult>> cache;

    // cache size
    private final int CACHE_SIZE = 100;

    private final AtomicInteger barrelCounter = new AtomicInteger(1);

    protected GatewayImpl() throws RemoteException {
        super();
        // LRU cache usando LinkedHashMap
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, List<SearchResult>>(16,0.75f,true){
            protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest){
                return size() > CACHE_SIZE;
            }
        });
    }


    // Helper: escolher barrel por rr. Se vazio, lança RemoteException
    private BarrelInterface chooseBarrel() throws RemoteException {
        if (barrels.isEmpty()) throw new RemoteException("No barrels registered");
        int idx = Math.abs(rr.getAndIncrement()) % barrels.size();
        return barrels.get(idx);
    }

    // Pesquisa: orchestrates call to a barrel and returns formatted SearchResult, com paginação
    @Override
    public List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException {
        if (terms == null || terms.isEmpty()) return Collections.emptyList();
        String key = String.join(" ", terms).toLowerCase();

        // check cache
        List<SearchResult> cached = cache.get(key);
        Set<String> rcached = new HashSet<String>();
        if (cached != null) {
            for(SearchResult c: cached){
                rcached.add(c.url);
            }
        }

        // tenta usar um Barrel; em caso de falha tenta os outros (failover)
        Exception lastEx = null;
        List<String> urls = null;
        for (int attempt = 0; attempt < Math.max(1, barrels.size()); attempt++) {
            BarrelInterface b = null;
            try {
                b = chooseBarrel();
                Set<String> resultSet = b.searchUrls(terms);
                Set<String> combinados = new HashSet<>(rcached);
                combinados.addAll(resultSet);
                urls = new ArrayList<>(resultSet);
                break;
            } catch (RemoteException re) {
                lastEx = re;
                // remove barrel que falha? melhor marcar e tentar os restantes
                System.err.println("Gateway: barrel failed with RemoteException, will try another. " + re.getMessage());
                // try next barrel by continuing
            }
        }
        if (urls == null) {
            throw new RemoteException("All barrels failed", lastEx);
        }

        // Para ordenar por inbound links (relevância), consultamos o barrel(s) para inboundLinks count
        List<SearchResult> results = new ArrayList<>();
        for (String u : urls) {
            PageInfo p = null;
            int inboundCount = 0;
            // try to fetch page info from any barrel
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
            if (p == null) {
                // fallback: create minimal PageInfo
                p = new PageInfo(u, "(sem título)", "(sem snippet)");
            }
            results.add(new SearchResult(u, p.title, p.snippet, inboundCount));
        }

        // ordenar por score desc
        results.sort((a,b) -> Integer.compare(b.score, a.score));

        // guardar no cache (versão completa, sem paginação)
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

    // indexPage: Gateway tentará replicar para todos os barrels.
    // pageWords: mapa palavra -> set(url) (normalmente com um único url), fornecido pelo Downloader
    @Override
    public boolean indexPage(PageInfo page, Map<String, Set<String>> pageWords) throws RemoteException {
        if (page == null || page.url == null) throw new RemoteException("Invalid page");
        // tentativa a todos os barrels; se algum falhar, tentamos retry limited
        int RETRIES = 3;
        boolean anySuccess = false;
        for (int i = 0; i < barrels.size(); i++) {
            BarrelInterface b = barrels.get(i);
            boolean success = false;
            for (int r = 0; r < RETRIES; r++) {
                try {
                    Map<String, Set<String>> partial = new HashMap<>();
                    if (pageWords != null) partial.putAll(pageWords);
                    Map<String, PageInfo> pagesBatch = new HashMap<>();
                    pagesBatch.put(page.url, page);
                    b.putIndex(partial, pagesBatch);
                    success = true;
                    anySuccess = true;
                    break;
                } catch (RemoteException e) {
                    System.err.println("Gateway: putIndex failed on barrel " + i + " attempt " + r + ": " + e.getMessage());
                    // continue retry
                }
            }
            if (!success) {
                System.err.println("Gateway: WARNING - barrel " + i + " failed after retries; continuing.");
            }
        }
        return anySuccess;
    }

    @Override
    public Set<String> inboundLinks(String url) throws RemoteException {
        // pergunta a cada barrel; agrega resultados
        Set<String> aggregated = new HashSet<>();
        for (BarrelInterface b : barrels) {
            try {
                Set<String> s = b.inboundLinks(url);
                if (s != null) aggregated.addAll(s);
            } catch (RemoteException e) {
                // ignore barrel failure; continuar
            }
        }
        return aggregated;
    }

    @Override
    public Map<String, Object> getStats() throws RemoteException {
        Map<String, Object> m = new HashMap<>();
        m.put("gatewayCachedKeys", cache.size());
        List<Map<String,Object>> barrelsStats = new ArrayList<>();
        for (BarrelInterface b : barrels) {
            try {
                barrelsStats.add(b.getStats());
            } catch (RemoteException e) {
                Map<String,Object> fail = new HashMap<>();
                fail.put("error", e.getMessage());
                barrelsStats.add(fail);
            }
        }
        m.put("barrels", barrelsStats);
        return m;
    }

    @Override
    public synchronized String registerNewBarrel(BarrelInterface stub) throws RemoteException {
    String name = "Barrel" + barrelCounter.getAndIncrement();
    System.out.println("[Gateway] Novo barrel registado: " + name);

    if (!barrels.isEmpty()) {
        BarrelInterface existing = barrels.get(0);
        try {
            Map<String, Object> fullIndex = existing.getFullIndex();

            @SuppressWarnings("unchecked")
            Map<String, Set<String>> inv = (Map<String, Set<String>>) fullIndex.get("invertedIndex");

            @SuppressWarnings("unchecked")
            Map<String, PageInfo> pgs = (Map<String, PageInfo>) fullIndex.get("pages");

            @SuppressWarnings("unchecked")
            Map<String, Set<String>> inb = (Map<String, Set<String>>) fullIndex.get("inboundLinks");

            stub.syncFullIndex(inv, pgs, inb);

            System.out.println("[Gateway] Synced " + name + " with "
                    + barrelNames.get(0) + " (" + inv.size() + " words)");
        } catch (Exception e) {;
        }
    }

    barrels.add(stub);
    barrelNames.add(name);
    return name;
}


    public static void main(String[] args) {
            String name = "Gateway";
            String registryHost = "localhost";
            int registryPort = 1099;

            try{
            System.setProperty("java.rmi.server.hostname", "localhost");
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            GatewayImpl gw = new GatewayImpl();
            registry.rebind(name, gw);
            System.out.println("[Gateway] bound as '" + name + "' on " + registryHost + ":" + registryPort);
        } catch (Exception e) {}
}
    }
