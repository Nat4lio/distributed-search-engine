import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentLinkedDeque;

public class url_queue extends UnicastRemoteObject{
    ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
    public url_queue() throws RemoteException{
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
}