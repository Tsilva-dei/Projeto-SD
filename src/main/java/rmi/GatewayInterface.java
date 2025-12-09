package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

public interface GatewayInterface extends Remote {
    void indexURL(String url) throws RemoteException;
    List<SearchResult> search(String query) throws RemoteException;
    List<SearchResult> searchPaginated(String query, int page, int pageSize) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;
    SystemStats getStatistics() throws RemoteException;
}