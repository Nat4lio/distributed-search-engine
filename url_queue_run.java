import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class url_queue_run {
    public static void main(String[] args) throws RemoteException{

        url_queue queue = new url_queue();
        
        try{
        LocateRegistry.createRegistry(1099);
        }catch(Exception e){}

        Registry registry = LocateRegistry.getRegistry("localhost",1099);
        registry.rebind("queue",queue);

        String first = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal";
        queue.addUrl(first);

        

    }
    
}
