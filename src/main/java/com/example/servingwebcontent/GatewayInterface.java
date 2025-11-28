package com.example.servingwebcontent;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface RMI da Gateway.
 * Inclui registerNewBarrel para atribuir nomes únicos aos barrels.
 */
public interface GatewayInterface extends Remote {
    List<SearchResult> search(List<String> terms, int pageNumber, int pageSize) throws RemoteException;
    boolean indexPage(PageInfo page, Map<String, Set<String>> pageWords) throws RemoteException;
    Set<String> inboundLinks(String url) throws RemoteException;
    Map<String, Object> getStats() throws RemoteException;

    // Permite a um Barrel pedir registo/nome único ao Gateway
    String registerNewBarrel(BarrelInterface stub) throws RemoteException;
}
