import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BarrelImpl - versão SEM persistência em disco.
 * - O primeiro barrel criado inicia VAZIO.
 * - Cada novo barrel é registado no Gateway que devolve um nome.
 * - O Gateway é responsável por sincronizar o novo barrel com o anterior.
 *
 * Nota: este ficheiro assume que o Gateway implementa registerNewBarrel(BarrelInterface)
 * e que, quando devolve o nome, já terá tentado sincronizar (ou irá fazê-lo).
 */
public class BarrelImpl extends UnicastRemoteObject implements BarrelInterface {

    private static final long serialVersionUID = 1L;

    // Estruturas em memória (não persistidas)
    private final ConcurrentHashMap<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PageInfo> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> inboundLinks = new ConcurrentHashMap<>();

    private volatile boolean alive = true;

    // Nome atribuído pelo Gateway (Barrel1, Barrel2, ...)
    public String name;

    protected BarrelImpl(String assignedName) throws RemoteException {
        super();
        this.name = assignedName;
        // NÃO carregamos nada do disco — versão "sem ficheiros"
    }

    public String getName() throws RemoteException{
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
                String word = e.getKey().toLowerCase();
                Set<String> urls = e.getValue();
                invertedIndex.compute(word, (k, old) -> {
                    k = k.toLowerCase();
                    if (old == null) old = Collections.newSetFromMap(new ConcurrentHashMap<>());
                    old.addAll(urls);
                    return old;
                });
            }
        }
        if (pagesBatch != null) {
            for (Map.Entry<String, PageInfo> e : pagesBatch.entrySet()) {
                String url = e.getKey();
                PageInfo info = e.getValue();
                pages.put(url, info);
                for (String out : info.outLinks) {
                    inboundLinks.compute(out, (k, old) -> {
                        k = k.toLowerCase();
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
            if (t == null) continue;
            Set<String> urls = invertedIndex.getOrDefault(t.toLowerCase(), Collections.emptySet());
            if (result == null) result = new HashSet<>(urls);
            else result.retainAll(urls);
            if (result.isEmpty()) break;
        }
        return (result == null) ? Collections.emptySet() : result;
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

    @Override
    public void shutdown() throws RemoteException {
        this.alive = false;
        System.out.println("[Barrel:" + name + "] shutdown called");
    }

    // Métodos extras usados para sincronização pelo Gateway
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

    // ---- main: regista-se no Gateway e obtém nome; NÃO usa ficheiros ----
    public static void main(String[] args) {
        try {
            // parâmetros: <registryHost> <registryPort>
            String registryHost = (args.length >= 1) ? args[0] : "localhost";
            int registryPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 1099;

            System.setProperty("java.rmi.server.hostname", "localhost");
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

            // obtem o stub do gateway (assume que já está arrancado)
            GatewayInterface gw = (GatewayInterface) registry.lookup("Gateway");

            // cria stub temporário com nome "temp" (não carrega nada)
            BarrelImpl localStub = new BarrelImpl("temp");

            // pede ao gateway um nome único — gateway deverá sincronizar o novo com o anterior
            String assignedName = gw.registerNewBarrel(localStub);

            // actualiza o nome localmente e faz o rebind com o nome definitivo
            localStub.name = assignedName;
            registry.rebind(assignedName, localStub);

            System.out.println("[Barrel] registado como: " + assignedName);

        } catch (Exception e) {
            System.err.println("[Barrel] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
