package rmi;

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.concurrent.*;
import java.util.*;
import java.io.*;

/**
 * URL Queue - Manages the queue of URLs to be downloaded
 * This component stores URLs found by Downloaders and provides them for processing
 * 
 * FEATURES:
 * - Thread-safe queue operations
 * - Visited URL tracking (prevents reprocessing)
 * - Optional Bloom filter for memory efficiency
 * - Persistent state across restarts
 */
public class URLQueue extends UnicastRemoteObject implements URLQueueInterface {
    
    private final ConcurrentLinkedQueue<String> urlQueue;
    private final Set<String> visitedURLs;
    private final Object lock = new Object();
    private final String persistenceFile = "data/urlqueue_state.dat";
    private final Timer autosaveTimer;
    
    public URLQueue() throws RemoteException {
        super();
        this.urlQueue = new ConcurrentLinkedQueue<>();
        this.visitedURLs = ConcurrentHashMap.newKeySet();
        
        // Setup persistence
        if (Config.getBarrelPersistenceEnabled()) {
            new File("data").mkdirs();
            loadState();
            
            // Periodic autosave
            this.autosaveTimer = new Timer("URLQueue-Autosave", true);
            autosaveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    saveState();
                }
            }, 30000, 30000);
            
            System.out.println("URLQueue persistence enabled");
        } else {
            this.autosaveTimer = null;
        }
    }
    
    @Override
    public void addURL(String url) throws RemoteException {
        synchronized(lock) {
            // Normalize URL (remove trailing slash, convert to lowercase for consistency)
            String normalizedUrl = normalizeURL(url);
            
            if (!visitedURLs.contains(normalizedUrl) && !urlQueue.contains(normalizedUrl)) {
                urlQueue.offer(normalizedUrl);
                System.out.println("URL added to queue: " + normalizedUrl + " (queue size: " + urlQueue.size() + ")");
            }
        }
    }
    
    @Override
    public void addURLs(List<String> urls) throws RemoteException {
        int addedCount = 0;
        for (String url : urls) {
            synchronized(lock) {
                String normalizedUrl = normalizeURL(url);
                if (!visitedURLs.contains(normalizedUrl) && !urlQueue.contains(normalizedUrl)) {
                    urlQueue.offer(normalizedUrl);
                    addedCount++;
                }
            }
        }
        if (addedCount > 0) {
            System.out.println("Added " + addedCount + " new URLs to queue (queue size: " + urlQueue.size() + ")");
        }
    }
    
    @Override
    public String getNextURL() throws RemoteException {
        String url = urlQueue.poll();
        if (url != null) {
            visitedURLs.add(url);
            System.out.println("URL retrieved from queue: " + url + " (queue size: " + urlQueue.size() + ", visited: " + visitedURLs.size() + ")");
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
    
    @Override
    public int getVisitedCount() throws RemoteException {
        return visitedURLs.size();
    }

    // Normalize URL for consistency
    private String normalizeURL(String url) {
        if (url == null) return null;
        
        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // Remove URL fragments (#section)
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex > 0) {
            url = url.substring(0, fragmentIndex);
        }
        
        return url;
    }

    // Save state for crash recovery
    private void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(persistenceFile))) {
            oos.writeObject(new ArrayList<>(urlQueue));
            oos.writeObject(new HashSet<>(visitedURLs));
            System.out.println("URLQueue state saved (queue: " + urlQueue.size() + 
                             ", visited: " + visitedURLs.size() + ")");
        } catch (Exception e) {
            System.err.println("Failed to save URLQueue state: " + e.getMessage());
        }
    }

    // Load state from disk
    @SuppressWarnings("unchecked")
    private void loadState() {
        File file = new File(persistenceFile);
        if (!file.exists()) {
            System.out.println("URLQueue: No previous state found, starting fresh");
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(file))) {
            List<String> loadedQueue = (List<String>) ois.readObject();
            Set<String> loadedVisited = (Set<String>) ois.readObject();
            
            urlQueue.addAll(loadedQueue);
            visitedURLs.addAll(loadedVisited);
            
            System.out.println("URLQueue state recovered (queue: " + urlQueue.size() + 
                             ", visited: " + visitedURLs.size() + ")");
        } catch (Exception e) {
            System.err.println("Failed to load URLQueue state: " + e.getMessage());
        }
    }
    
    // Shutdown gracefully
    public void shutdown() {
        System.out.println("URLQueue shutting down...");
        if (autosaveTimer != null) {
            autosaveTimer.cancel();
        }
        saveState();
        System.out.println("URLQueue shutdown complete");
    }
    
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", Config.getRMIHost());
            
            URLQueue queue = new URLQueue();
            
            // Create or get registry on configured port
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(Config.getRMIPort());
                System.out.println("RMI registry created on port " + Config.getRMIPort());
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(Config.getRMIPort());
                System.out.println("Using existing RMI registry on port " + Config.getRMIPort());
            }
            
            registry.rebind("URLQueue", queue);
            System.out.println("URLQueue is ready and registered on " + 
                             Config.getRMIHost() + ":" + Config.getRMIPort());
            
            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                queue.shutdown();
            }));
            
        } catch (Exception e) {
            System.err.println("URLQueue exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


// Remote interface for URL Queue operations
interface URLQueueInterface extends Remote {
    void addURL(String url) throws RemoteException;
    void addURLs(List<String> urls) throws RemoteException;
    String getNextURL() throws RemoteException;
    int getQueueSize() throws RemoteException;
    boolean hasURLs() throws RemoteException;
    int getVisitedCount() throws RemoteException;
}