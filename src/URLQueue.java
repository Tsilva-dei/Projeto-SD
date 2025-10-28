import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.concurrent.*;
import java.util.*;

/**
 * URL Queue - Manages the queue of URLs to be downloaded
 * This component stores URLs found by Downloaders and provides them for processing
 */
public class URLQueue extends UnicastRemoteObject implements URLQueueInterface {
    
    private final ConcurrentLinkedQueue<String> urlQueue;
    private final Set<String> visitedURLs;
    private final Object lock = new Object();
    
    public URLQueue() throws RemoteException {
        super();
        this.urlQueue = new ConcurrentLinkedQueue<>();
        this.visitedURLs = ConcurrentHashMap.newKeySet();
    }
    
    @Override
    public void addURL(String url) throws RemoteException {
        synchronized(lock) {
            if (!visitedURLs.contains(url)) {
                urlQueue.offer(url);
                System.out.println("URL added to queue: " + url);
            }
        }
    }
    
    @Override
    public void addURLs(List<String> urls) throws RemoteException {
        for (String url : urls) {
            addURL(url);
        }
    }
    
    @Override
    public String getNextURL() throws RemoteException {
        String url = urlQueue.poll();
        if (url != null) {
            visitedURLs.add(url);
            System.out.println("URL retrieved from queue: " + url);
        }
        return url;
    }
    
    @Override
    public int getQueueSize() throws RemoteException {
        return urlQueue.size();
    }
    
    @Override
    public boolean hasURLs() throws RemoteException {
        return !urlQueue.isEmpty();
    }
    
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", "localhost");
            
            URLQueue queue = new URLQueue();
            
            // Create or get registry on port 1099
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(1099);
                System.out.println("RMI registry created on port 1099");
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(1099);
                System.out.println("Using existing RMI registry on port 1099");
            }
            
            registry.rebind("URLQueue", queue);
            System.out.println("URLQueue is ready and registered");
            
        } catch (Exception e) {
            System.err.println("URLQueue exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * Remote interface for URL Queue operations
 */
interface URLQueueInterface extends Remote {
    void addURL(String url) throws RemoteException;
    void addURLs(List<String> urls) throws RemoteException;
    String getNextURL() throws RemoteException;
    int getQueueSize() throws RemoteException;
    boolean hasURLs() throws RemoteException;
}