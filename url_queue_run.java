import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.rmi.RemoteException;

/**
 * url_queue_run - implementa e bind a fila "queue".
 */
public class url_queue_run extends UnicastRemoteObject implements url_queue {
    private static final long serialVersionUID = 1L;
    ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
    public url_queue_run() throws RemoteException { super(); }
    public void addUrl(String url) throws RemoteException { if (url!=null && !url.isEmpty()) queue.add(url); }
    public String getNextUrl() throws RemoteException { return queue.poll(); }
    public boolean isEmpty() throws RemoteException { return queue.isEmpty(); }
    public ConcurrentLinkedDeque<String> getQueue() throws RemoteException { return queue; }

    public static void main(String[] args) throws RemoteException {
        try {
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(1099);
                System.out.println("[Queue] RMI registry criado na porta 1099");
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry("localhost", 1099);
                System.out.println("[Queue] Registry já existe, a ligar...");
            }
            url_queue_run queue = new url_queue_run();
            registry.rebind("queue", queue);
            System.out.println("[Queue] Fila registada como 'queue'");
            String first = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal";
            queue.addUrl(first);
            System.out.println("[Queue] URL inicial adicionada com sucesso");
        } catch (Exception e) {
            System.err.println("[Queue] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
