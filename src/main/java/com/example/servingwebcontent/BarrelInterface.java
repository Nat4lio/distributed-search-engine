package com.example.servingwebcontent;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface RMI para os Barrels.
 */
public interface BarrelInterface extends Remote {
    void putIndex(Map<String, Set<String>> partialIndex, Map<String, PageInfo> pages) throws RemoteException;
    Set<String> searchUrls(List<String> terms) throws RemoteException;
    PageInfo getPageInfo(String url) throws RemoteException;
    Set<String> inboundLinks(String url) throws RemoteException;
    Map<String, Object> getStats() throws RemoteException;
    boolean ping() throws RemoteException;
    void shutdown() throws RemoteException;
    String getName() throws RemoteException;

    // Métodos úteis para sincronização/backup
    Map<String, Object> getFullIndex() throws RemoteException;
    Map<String, PageInfo> getAllPages() throws RemoteException;
}

