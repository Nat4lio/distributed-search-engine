import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface que o cliente usa para falar com a Gateway.
 * A Gateway encapsula seleção de Barrel e failover.
 */
public interface GatewayInterface extends Remote {
    // Pesquisa por termos, devolve SearchResult agrupado (Gateway format)
    public java.util.List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException;

    // Inserir / pedir indexação de uma página (gateway encaminha para Barrels)
    public boolean indexPage(PageInfo page, java.util.Map<String, java.util.Set<String>> pageWords) throws RemoteException;

    // Obter inbound links
    public java.util.Set<String> inboundLinks(String url) throws RemoteException;

    // Estatísticas de sistema
    public java.util.Map<String, Object> getStats() throws RemoteException;
}
