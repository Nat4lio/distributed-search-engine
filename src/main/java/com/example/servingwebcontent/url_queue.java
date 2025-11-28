package com.example.servingwebcontent;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentLinkedDeque;

public interface url_queue extends Remote{
    void addUrl(String url) throws RemoteException;
    String getNextUrl() throws RemoteException;
    boolean isEmpty() throws RemoteException;
    ConcurrentLinkedDeque<String> getQueue() throws RemoteException;
}
