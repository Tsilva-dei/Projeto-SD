import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * RPC/RMI Gateway - Entry point for clients
 * Distributes search requests among available Storage Barrels
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {
    
    private URLQueueInterface urlQueue;
    private List<StorageBarrelInterface> barrels;
    private final Map<Set<String>, List<SearchResult>> searchCache;
    private final Map<String, Integer> searchFrequency;
    private int currentBarrelIndex = 0;
    
    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchCache = new ConcurrentHashMap<>();
        this.searchFrequency = new ConcurrentHashMap<>();
    }
    
    /**
     * Connect to URL Queue and Storage Barrels
     */
    public void connectToServices() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            
            // Get URL Queue
            urlQueue = (URLQueueInterface) registry.lookup("URLQueue");
            System.out.println("Gateway connected to URLQueue");
            
            // Get all available Storage Barrels
            updateBarrelList(registry);
            
        } catch (Exception e) {
            System.err.println("Gateway connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update list of available Storage Barrels
     */
    private void updateBarrelList(Registry registry) {
        try {
            barrels.clear();
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith("StorageBarrel_")) {
                    StorageBarrelInterface barrel = (StorageBarrelInterface) registry.lookup(name);
                    barrels.add(barrel);
                    System.out.println("Gateway connected to " + name);
                }
            }
            
            if (barrels.isEmpty()) {
                System.err.println("WARNING: No Storage Barrels available!");
            }
        } catch (Exception e) {
            System.err.println("Error updating barrel list: " + e.getMessage());
        }
    }
    
    @Override
    public void indexURL(String url) throws RemoteException {
        try {
            urlQueue.addURL(url);
            System.out.println("URL queued for indexing: " + url);
        } catch (RemoteException e) {
            System.err.println("Failed to add URL to queue: " + e.getMessage());
            throw e;
        }
    }
    
    @Override
    public List<SearchResult> search(String query) throws RemoteException {
        // Parse query into terms
        Set<String> terms = new HashSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        
        // Update search frequency
        searchFrequency.put(query.toLowerCase(), searchFrequency.getOrDefault(query.toLowerCase(), 0) + 1);
        
        // Check cache
        if (searchCache.containsKey(terms)) {
            System.out.println("Cache hit for query: " + query);
            return searchCache.get(terms);
        }
        
        // Select a barrel (round-robin with failover)
        List<SearchResult> results = null;
        int attempts = 0;
        
        while (results == null && attempts < barrels.size()) {
            try {
                StorageBarrelInterface barrel = selectBarrel();
                results = barrel.search(terms);
                System.out.println("Search completed by barrel, found " + results.size() + " results");
            } catch (RemoteException e) {
                System.err.println("Barrel failed, trying another: " + e.getMessage());
                attempts++;
                
                // Try to update barrel list
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    updateBarrelList(registry);
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }
        
        if (results == null) {
            throw new RemoteException("No barrels available to process search");
        }
        
        // Cache results
        searchCache.put(terms, results);
        
        return results;
    }
    
    @Override
    public List<SearchResult> searchPaginated(String query, int page, int pageSize) throws RemoteException {
        List<SearchResult> allResults = search(query);
        
        int start = page * pageSize;
        int end = Math.min(start + pageSize, allResults.size());
        
        if (start >= allResults.size()) {
            return new ArrayList<>();
        }
        
        return allResults.subList(start, end);
    }
    
    @Override
    public Set<String> getIncomingLinks(String url) throws RemoteException {
        // Try all barrels until one succeeds
        for (StorageBarrelInterface barrel : barrels) {
            try {
                return barrel.getIncomingLinks(url);
            } catch (RemoteException e) {
                System.err.println("Barrel failed for incoming links: " + e.getMessage());
            }
        }
        throw new RemoteException("No barrels available to get incoming links");
    }
    
    @Override
    public SystemStats getStatistics() throws RemoteException {
        SystemStats stats = new SystemStats();
        
        // Top 10 searches
        stats.topSearches = searchFrequency.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        
        // Barrel statistics
        stats.barrelStats = new ArrayList<>();
        for (StorageBarrelInterface barrel : barrels) {
            try {
                BarrelStats bs = new BarrelStats();
                bs.barrelId = barrel.getBarrelId();
                bs.indexSize = barrel.getIndexSize();
                bs.avgSearchTime = barrel.getAverageSearchTime();
                stats.barrelStats.add(bs);
            } catch (RemoteException e) {
                System.err.println("Failed to get stats from barrel: " + e.getMessage());
            }
        }
        
        return stats;
    }
    
    /**
     * Select a barrel using round-robin
     */
    private StorageBarrelInterface selectBarrel() {
        if (barrels.isEmpty()) {
            return null;
        }
        StorageBarrelInterface barrel = barrels.get(currentBarrelIndex);
        currentBarrelIndex = (currentBarrelIndex + 1) % barrels.size();
        return barrel;
    }
    
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", "localhost");
            
            Gateway gateway = new Gateway();
            gateway.connectToServices();
            
            Registry registry = LocateRegistry.getRegistry(1099);
            registry.rebind("Gateway", gateway);
            
            System.out.println("Gateway is ready");
            
        } catch (Exception e) {
            System.err.println("Gateway exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * Remote interface for Gateway operations
 */
interface GatewayInterface extends Remote {
    void indexURL(String url) throws RemoteException;
    List<SearchResult> search(String query) throws RemoteException;
    List<SearchResult> searchPaginated(String query, int page, int pageSize) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;
    SystemStats getStatistics() throws RemoteException;
}

/**
 * System statistics
 */
class SystemStats implements java.io.Serializable {
    Map<String, Integer> topSearches;
    List<BarrelStats> barrelStats;
}

/**
 * Statistics for a single barrel
 */
class BarrelStats implements java.io.Serializable {
    String barrelId;
    int indexSize;
    double avgSearchTime;
    
    @Override
    public String toString() {
        return "Barrel " + barrelId + ": " + indexSize + " pages indexed, avg search time: " + avgSearchTime + " deciseconds";
    }
}