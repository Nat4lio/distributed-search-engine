import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementação simples do Barrel. Mantém:
 *  - invertedIndex: palavra -> set URLs
 *  - pages: url -> PageInfo
 *  - inboundLinks: url -> set URLs que apontam para ele
 *
 * A putIndex é usada para replicação (Gateway ou Downloader chamam isto).
 */
// (mantém os imports e a maior parte do código anterior)

public class BarrelImpl extends UnicastRemoteObject implements BarrelInterface {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String, Set<String>> invertedIndex;
    private final ConcurrentHashMap<String, PageInfo> pages;
    public final ConcurrentHashMap<String, Set<String>> inboundLinks;
    static ConcurrentLinkedQueue<String> urls;

    // flag para simular falha no barrel (usada nos testes)
    private volatile boolean alive = true;

    protected BarrelImpl() throws RemoteException {
        super();
        invertedIndex = new ConcurrentHashMap<>();
        pages = new ConcurrentHashMap<>();
        inboundLinks = new ConcurrentHashMap<>();
        urls = new ConcurrentLinkedQueue<>();
    }

    private void checkAlive() throws RemoteException {
        if (!alive) throw new RemoteException("Barrel is shutdown");
    }

    @Override
    public synchronized void putIndex(Map<String, Set<String>> partialIndex, Map<String, PageInfo> pagesBatch) throws RemoteException {
        checkAlive();
        if (partialIndex != null) {
            for (Map.Entry<String, Set<String>> e : partialIndex.entrySet()) {
                String url = e.getKey();
                Set<String> words = e.getValue();
                for(String word: words){
                invertedIndex.compute(word, (k, old) -> {
                    if (old == null) old = Collections.newSetFromMap(new ConcurrentHashMap<>());
                    old.add(url);
                    return old;
                });
            }
            }
        }
        if (pagesBatch != null) {
            for (Map.Entry<String, PageInfo> e : pagesBatch.entrySet()) {
                String url = e.getKey();
                PageInfo pinfo = e.getValue();
                pages.put(url, pinfo);
                for (String out : pinfo.outLinks) {
                    inboundLinks.compute(out, (k, old) -> {
                        if (old == null) old = Collections.newSetFromMap(new ConcurrentHashMap<>());
                        old.add(url);
                        return old;
                    });
                }
            }
        }
    }

    @Override
    public Set<String> searchUrls(List<String> terms) throws RemoteException {
        checkAlive();
        if (terms == null || terms.isEmpty()) return Collections.emptySet();
        Set<String> result = null;
        for (String t : terms) {
            Set<String> urls = invertedIndex.getOrDefault(t.toLowerCase(), Collections.emptySet());
            if (result == null) {
                result = new HashSet<>(urls);
            } else {
                result.retainAll(urls);
            }
            if (result.isEmpty()) break;
        }
        if (result == null) return Collections.emptySet();
        return result;
    }

    @Override
    public PageInfo getPageInfo(String url) throws RemoteException {
        checkAlive();
        return pages.get(url);
    }

    @Override
    public Set<String> inboundLinks(String url) throws RemoteException {
        checkAlive();
        return inboundLinks.getOrDefault(url, Collections.emptySet());
    }

    @Override
    public Map<String, Object> getStats() throws RemoteException {
        checkAlive();
        Map<String, Object> m = new HashMap<>();
        m.put("indexedPages", pages.size());
        m.put("uniqueWords", invertedIndex.size());
        return m;
    }

    @Override
    public boolean ping() throws RemoteException {
        return alive;
    }

    // **novo**: shutdown
    @Override
    public void shutdown() throws RemoteException {
        System.out.println("BarrelImpl: shutdown called, switching alive=false");
        this.alive = false;
    }

    // main original (sem alterações funcionais)
    public static void main(String[] args) {
        try {
            String host = "localhost";
            int port = 1099;
            LocateRegistry.createRegistry(port);
            Registry registry = LocateRegistry.getRegistry(host, port);
            BarrelImpl barrel1 = new BarrelImpl();
            BarrelImpl barrel2 = new BarrelImpl();
            BarrelImpl barrel3 = new BarrelImpl();
            registry.rebind("barrel1", barrel1);
            System.out.println("Barrel bound as barrel1 on " + host + ":" + port);
            registry.rebind("barrel2", barrel2);
            System.out.println("Barrel bound as barrel2 on " + host + ":" + port);
            registry.rebind("barrel3", barrel3);
            System.out.println("Barrel bound as barrel3 on " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("Barrel exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
