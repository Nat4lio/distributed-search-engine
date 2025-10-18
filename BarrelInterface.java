import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BarrelInterface extends Remote {
    public void putIndex(Map<String, Set<String>> partialIndex, Map<String, PageInfo> pages) throws RemoteException;
    public Set<String> searchUrls(List<String> terms) throws RemoteException;
    public PageInfo getPageInfo(String url) throws RemoteException;
    public Set<String> inboundLinks(String url) throws RemoteException;
    public Map<String, Object> getStats() throws RemoteException;
    public boolean ping() throws RemoteException;

    // **adicionado** para permitir simular a falha do barrel nos testes
    public void shutdown() throws RemoteException;
}
