import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;

/**
 * Storage Barrel - Stores the inverted index and handles search queries
 * Multiple barrels replicate the same data for fault tolerance
 * 
 * FEATURES:
 * - Persistent storage (survives crashes)
 * - Automatic state recovery on restart
 * - Periodic autosave
 * - Synchronization acknowledgment for reliable multicast
 */
public class StorageBarrel extends UnicastRemoteObject implements StorageBarrelInterface {
    
    // Inverted index: word -> set of URLs
    private final Map<String, Set<String>> invertedIndex;
    
    // URL -> PageInfo (title, citation, incoming links)
    private final Map<String, PageInfo> pageInfoMap;
    
    // URL -> Set of URLs that link to it
    private final Map<String, Set<String>> incomingLinks;
    
    private final String barrelId;
    private long totalSearchTime = 0;
    private int searchCount = 0;
    
    private final String persistenceFile;
    private final Timer autosaveTimer;
    
    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.invertedIndex = new ConcurrentHashMap<>();
        this.pageInfoMap = new ConcurrentHashMap<>();
        this.incomingLinks = new ConcurrentHashMap<>();
        
        // Setup persistence
        if (Config.getBarrelPersistenceEnabled()) {
            String persistenceDir = Config.getBarrelPersistenceDirectory();
            new File(persistenceDir).mkdirs();
            this.persistenceFile = persistenceDir + "/barrel_" + barrelId + ".dat";
            
            // Try to load previous state
            loadState();
            
            // Setup autosave timer
            this.autosaveTimer = new Timer("Autosave-" + barrelId, true);
            int autosaveInterval = Config.getBarrelPersistenceAutosaveInterval();
            autosaveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    saveState();
                }
            }, autosaveInterval, autosaveInterval);
            
            System.out.println("StorageBarrel " + barrelId + " persistence enabled: " + persistenceFile);
        } else {
            this.persistenceFile = null;
            this.autosaveTimer = null;
            System.out.println("StorageBarrel " + barrelId + " persistence disabled");
        }
        
        System.out.println("StorageBarrel " + barrelId + " initialized with " + pageInfoMap.size() + " pages");
    }
    
    @Override
    public synchronized boolean indexPage(String url, String title, String citation, Set<String> words, Set<String> links) throws RemoteException {
        try {
            // Store page info
            pageInfoMap.put(url, new PageInfo(title, citation));
            
            // Update inverted index
            for (String word : words) {
                invertedIndex.computeIfAbsent(word.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(url);
            }
            
            // Update incoming links for linked pages
            for (String linkedUrl : links) {
                incomingLinks.computeIfAbsent(linkedUrl, k -> ConcurrentHashMap.newKeySet()).add(url);
            }
            
            System.out.println("Barrel " + barrelId + " indexed: " + url + " (" + words.size() + " words)");
            return true; // ACK
            
        } catch (Exception e) {
            System.err.println("Barrel " + barrelId + " indexing error: " + e.getMessage());
            return false; // NACK
        }
    }
    
    @Override
    public List<SearchResult> search(Set<String> terms) throws RemoteException {
        long startTime = System.currentTimeMillis();
        
        List<SearchResult> results = new ArrayList<>();
        
        if (terms.isEmpty()) {
            return results;
        }
        
        // Find URLs that contain ALL search terms
        Set<String> resultUrls = null;
        for (String term : terms) {
            Set<String> urlsForTerm = invertedIndex.get(term.toLowerCase());
            if (urlsForTerm == null) {
                return results; // No results if any term is not found
            }
            if (resultUrls == null) {
                resultUrls = new HashSet<>(urlsForTerm);
            } else {
                resultUrls.retainAll(urlsForTerm);
            }
        }
        
        // Build search results with page info and incoming link count
        for (String url : resultUrls) {
            PageInfo info = pageInfoMap.get(url);
            if (info != null) {
                int incomingLinkCount = incomingLinks.getOrDefault(url, Collections.emptySet()).size();
                results.add(new SearchResult(url, info.title, info.citation, incomingLinkCount));
            }
        }
        
        // Sort by number of incoming links (descending)
        results.sort((a, b) -> Integer.compare(b.incomingLinks, a.incomingLinks));
        
        long endTime = System.currentTimeMillis();
        totalSearchTime += (endTime - startTime);
        searchCount++;
        
        return results;
    }
    
    @Override
    public Set<String> getIncomingLinks(String url) throws RemoteException {
        return new HashSet<>(incomingLinks.getOrDefault(url, Collections.emptySet()));
    }
    
    @Override
    public int getIndexSize() throws RemoteException {
        return pageInfoMap.size();
    }
    
    @Override
    public double getAverageSearchTime() throws RemoteException {
        return searchCount > 0 ? (double) totalSearchTime / searchCount / 100.0 : 0.0; // in deciseconds
    }
    
    @Override
    public String getBarrelId() throws RemoteException {
        return barrelId;
    }
    
    @Override
    public boolean ping() throws RemoteException {
        return true;
    }
    
    /**
     * Save current state to disk for crash recovery
     */
    private synchronized void saveState() {
        if (persistenceFile == null) return;
        
        try {
            // Create temp file first (atomic write)
            File tempFile = new File(persistenceFile + ".tmp");
            
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tempFile)))) {
                
                // Write all data structures
                oos.writeObject(new HashMap<>(invertedIndex));
                oos.writeObject(new HashMap<>(pageInfoMap));
                oos.writeObject(new HashMap<>(incomingLinks));
                oos.writeLong(totalSearchTime);
                oos.writeInt(searchCount);
                oos.flush();
            }
            
            // Atomic rename
            Files.move(tempFile.toPath(), Paths.get(persistenceFile), 
                      StandardCopyOption.REPLACE_EXISTING, 
                      StandardCopyOption.ATOMIC_MOVE);
            
            System.out.println("Barrel " + barrelId + " state saved (" + pageInfoMap.size() + " pages)");
            
        } catch (Exception e) {
            System.err.println("Barrel " + barrelId + " failed to save state: " + e.getMessage());
        }
    }
    
    /**
     * Load state from disk (crash recovery)
     */
    @SuppressWarnings("unchecked")
    private void loadState() {
        File file = new File(persistenceFile);
        if (!file.exists()) {
            System.out.println("Barrel " + barrelId + " no previous state found, starting fresh");
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            
            Map<String, Set<String>> loadedIndex = (Map<String, Set<String>>) ois.readObject();
            Map<String, PageInfo> loadedPages = (Map<String, PageInfo>) ois.readObject();
            Map<String, Set<String>> loadedLinks = (Map<String, Set<String>>) ois.readObject();
            long loadedSearchTime = ois.readLong();
            int loadedSearchCount = ois.readInt();
            
            // Restore data
            invertedIndex.putAll(loadedIndex);
            pageInfoMap.putAll(loadedPages);
            incomingLinks.putAll(loadedLinks);
            totalSearchTime = loadedSearchTime;
            searchCount = loadedSearchCount;
            
            System.out.println("Barrel " + barrelId + " state recovered: " + pageInfoMap.size() + " pages");
            
        } catch (Exception e) {
            System.err.println("Barrel " + barrelId + " failed to load state: " + e.getMessage());
            System.err.println("Starting with empty index");
        }
    }
    
    /**
     * Shutdown hook - save state before exit
     */
    public void shutdown() {
        System.out.println("Barrel " + barrelId + " shutting down...");
        if (autosaveTimer != null) {
            autosaveTimer.cancel();
        }
        saveState();
        System.out.println("Barrel " + barrelId + " shutdown complete");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java StorageBarrel <barrel-id>");
            return;
        }
        
        try {
            String barrelId = args[0];
            System.setProperty("java.rmi.server.hostname", Config.getRMIHost());
            
            StorageBarrel barrel = new StorageBarrel(barrelId);
            
            Registry registry = LocateRegistry.getRegistry(Config.getRMIPort());
            registry.rebind("StorageBarrel_" + barrelId, barrel);
            
            System.out.println("StorageBarrel " + barrelId + " is ready on " + 
                             Config.getRMIHost() + ":" + Config.getRMIPort());
            
            // Shutdown hook for graceful exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                barrel.shutdown();
            }));
            
        } catch (Exception e) {
            System.err.println("StorageBarrel exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * Remote interface for Storage Barrel operations
 */
interface StorageBarrelInterface extends Remote {
    boolean indexPage(String url, String title, String citation, Set<String> words, Set<String> links) throws RemoteException;
    List<SearchResult> search(Set<String> terms) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;
    int getIndexSize() throws RemoteException;
    double getAverageSearchTime() throws RemoteException;
    String getBarrelId() throws RemoteException;
    boolean ping() throws RemoteException;
}

/**
 * Page information stored for each URL
 */
class PageInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    String title;
    String citation;
    
    public PageInfo(String title, String citation) {
        this.title = title;
        this.citation = citation;
    }
}

/**
 * Search result containing URL, title, citation and relevance score
 */
class SearchResult implements Serializable {
    private static final long serialVersionUID = 1L;
    String url;
    String title;
    String citation;
    int incomingLinks;
    
    public SearchResult(String url, String title, String citation, int incomingLinks) {
        this.url = url;
        this.title = title;
        this.citation = citation;
        this.incomingLinks = incomingLinks;
    }
    
    @Override
    public String toString() {
        return "Title: " + title + "\nURL: " + url + "\nCitation: " + citation + "\nIncoming links: " + incomingLinks;
    }
}