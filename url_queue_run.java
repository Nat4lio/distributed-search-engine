import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentLinkedDeque;

public class url_queue_run extends UnicastRemoteObject implements url_queue {

    ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
    public url_queue_run() throws RemoteException{
        super();
    }
    public void addUrl(String url) throws RemoteException{
        if(url!=null && !url.isEmpty()){
        queue.add(url);
        }
    }
    public String getNextUrl() throws RemoteException{
        return queue.poll();
    }
    public boolean isEmpty() throws RemoteException{
        return queue.isEmpty();
    }
    public ConcurrentLinkedDeque<String> getQueue() throws RemoteException{
        return queue;
    }
    public static void main(String[] args) throws RemoteException{
        url_queue_run queue = new url_queue_run();
        Registry registry;
        try{
        registry = LocateRegistry.createRegistry(1099);
        }catch(Exception e){
            registry = LocateRegistry.getRegistry("localhost", 1099);
        }

        registry.rebind("queue",queue);
        System.out.println("queue registada");

        String first = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal";
        queue.addUrl(first);

        

    }
    
}
