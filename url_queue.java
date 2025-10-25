import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentLinkedDeque;

public interface url_queue extends Remote{
    public void addUrl(String url) throws RemoteException;
    public String getNextUrl() throws RemoteException;
    public boolean isEmpty() throws RemoteException;
    public ConcurrentLinkedDeque<String> getQueue() throws RemoteException;
    
}