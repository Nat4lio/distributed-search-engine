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
    String name;
    protected BarrelImpl(String name) throws RemoteException {
        super();
        invertedIndex = new ConcurrentHashMap<>();
        pages = new ConcurrentHashMap<>();
        inboundLinks = new ConcurrentHashMap<>();
        urls = new ConcurrentLinkedQueue<>();
        this.name = name;
    }

    public String getName(){
        return name;
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
       System.out.println("[Barrel " + name + "] recebeu atualização em " +new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) +" (PID=" + ProcessHandle.current().pid() + ")");
    }

    public synchronized void syncFullIndex(  Map<String, Set<String>> fullInvertedIndex, Map<String, PageInfo> fullPages,Map<String, Set<String>> fullInboundLinks) throws RemoteException {
    checkAlive();

    if (fullInvertedIndex != null)
        invertedIndex.putAll(fullInvertedIndex);

    if (fullPages != null)
        pages.putAll(fullPages);

    if (fullInboundLinks != null)
        inboundLinks.putAll(fullInboundLinks);

    System.out.println("[Barrel] Full index synchronized! Total entries: " + invertedIndex.size());
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

    @Override
    public synchronized Map<String, Object> getFullIndex() throws RemoteException {
        checkAlive();
        Map<String, Object> full = new HashMap<>();
        // devolve cópias para evitar partilha direta da estrutura interna
        Map<String, Set<String>> inv = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : invertedIndex.entrySet()) {
            inv.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        Map<String, PageInfo> pgs = new HashMap<>(pages);
        Map<String, Set<String>> inb = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : inboundLinks.entrySet()) {
            inb.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        full.put("invertedIndex", inv);
        full.put("pages", pgs);
        full.put("inboundLinks", inb);
        return full;
    }

    @Override
    public synchronized Map<String, PageInfo> getAllPages() throws RemoteException {
        checkAlive();
        return new HashMap<>(pages);
    }

    // main original (sem alterações funcionais)
    public static void main(String[] args) {
    try {
        String registryHost = "localhost";
        int registryPort = 1099;
        System.setProperty("java.rmi.server.hostname", "localhost");
        Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

        GatewayInterface gw = (GatewayInterface) registry.lookup("Gateway");

        // cria o barrel com nome temporário
        BarrelImpl localStub = new BarrelImpl("temp");

        String assignedName

        // registra primeiro o stub no Registry
        registry.rebind(assignedName, localStub);
        localStub.name = assignedName;

        System.out.println("[Barrel] registado no RMI como: " + assignedName);

        // agora sim, comunica ao Gateway para sincronizar
        gw.registerNewBarrel(localStub);

        System.out.println("Tamanho do invertedIndex: " + localStub.invertedIndex.size());
        System.out.println("Tamanho do pages: " + localStub.pages.size());
        System.out.println("Tamanho do inboundLinks: " + localStub.inboundLinks.size());

    } catch (Exception e) {
        e.printStackTrace();
    }
}
}
