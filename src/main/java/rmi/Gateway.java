package rmi;

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;

import java.io.*;

/**
 * RPC/RMI Gateway - Entry point for clients
 * Distributes search requests among available Storage Barrels
 * 
 * FEATURES:
 * - Load balancing (round-robin, random, or performance-based)
 * - Search result caching
 * - Automatic failover on barrel failures
 * - Real-time statistics tracking
 * - Persistent state for crash recovery
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {
    
    private URLQueueInterface urlQueue;
    private List<StorageBarrelInterface> barrels;
    private final Map<Set<String>, List<SearchResult>> searchCache;
    private final Map<String, Integer> searchFrequency;
    private int currentBarrelIndex = 0;
    private final Timer healthCheckTimer;
    private final Timer persistenceTimer;
    private final String persistenceFile = "data/gateway_state.dat";
    
    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchCache = Config.getGatewayCacheEnabled() ? 
                          new ConcurrentHashMap<>() : null;
        this.searchFrequency = new ConcurrentHashMap<>();
        
        // Setup persistence
        if (Config.getBarrelPersistenceEnabled()) {
            new File("data").mkdirs();
            loadState();
            
            // Periodic state saving
            this.persistenceTimer = new Timer("Gateway-Persistence", true);
            persistenceTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    saveState();
                }
            }, 30000, 30000); // Every 30 seconds
        } else {
            this.persistenceTimer = null;
        }
        
        // Health check timer - updates barrel list periodically
        this.healthCheckTimer = new Timer("Gateway-HealthCheck", true);
        healthCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Registry registry = LocateRegistry.getRegistry(Config.getRMIHost(), Config.getRMIPort());
                    updateBarrelList(registry);
                } catch (Exception e) {
                    System.err.println("Health check failed: " + e.getMessage());
                }
            }
        }, 5000, 10000); // Check every 10 seconds
    }
    
    /**
     * Connect to URL Queue and Storage Barrels
     */
    public void connectToServices() {
        try {
            Registry registry = LocateRegistry.getRegistry(Config.getRMIHost(), Config.getRMIPort());
            
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
     * Update list of available Storage Barrels with health check
     */
    private synchronized void updateBarrelList(Registry registry) {
        try {
            List<StorageBarrelInterface> activeBarrels = new ArrayList<>();
            String[] boundNames = registry.list();
            
            for (String name : boundNames) {
                if (name.startsWith("StorageBarrel_")) {
                    try {
                        StorageBarrelInterface barrel = (StorageBarrelInterface) registry.lookup(name);
                        // Ping to verify it's alive
                        if (barrel.ping()) {
                            activeBarrels.add(barrel);
                        }
                    } catch (Exception e) {
                        System.err.println("Barrel " + name + " is not responding");
                    }
                }
            }
            
            if (!activeBarrels.equals(barrels)) {
                barrels = activeBarrels;
                System.out.println("Gateway updated barrel list: " + barrels.size() + " active barrels");
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
            // Retry logic
            try {
                Thread.sleep(1000);
                urlQueue.addURL(url);
                System.out.println("URL queued after retry: " + url);
            } catch (Exception retryEx) {
                throw new RemoteException("Failed to queue URL after retry", e);
            }
        }
    }
    
    @Override
    public List<SearchResult> search(String query) throws RemoteException {
        // Parse query into terms
        Set<String> terms = new HashSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        
        // Update search frequency (for statistics)
        searchFrequency.merge(query.toLowerCase(), 1, Integer::sum);
        
        // Check cache
        if (searchCache != null && searchCache.containsKey(terms)) {
            System.out.println("Cache hit for query: " + query);
            return new ArrayList<>(searchCache.get(terms));
        }
        
        // Select a barrel and search (with failover)
        List<SearchResult> results = searchWithFailover(terms);
        
        if (results == null) {
            throw new RemoteException("No barrels available to process search");
        }
        
        // Cache results
        if (searchCache != null) {
            searchCache.put(new HashSet<>(terms), new ArrayList<>(results));
        }
        
        return results;
    }
    
    /**
     * Search with automatic failover if barrel fails
     */
    private List<SearchResult> searchWithFailover(Set<String> terms) {
        if (barrels.isEmpty()) {
            return null;
        }
        
        int attempts = 0;
        int maxAttempts = Math.min(barrels.size(), 3); // Try up to 3 barrels
        
        while (attempts < maxAttempts) {
            try {
                StorageBarrelInterface barrel = selectBarrel();
                if (barrel == null) {
                    return null;
                }
                
                List<SearchResult> results = barrel.search(terms);
                System.out.println("Search completed by barrel " + barrel.getBarrelId() + 
                                 ", found " + results.size() + " results");
                return results;
                
            } catch (RemoteException e) {
                System.err.println("Barrel failed (attempt " + (attempts + 1) + "): " + e.getMessage());
                attempts++;
                
                // Update barrel list to remove failed barrel
                try {
                    Registry registry = LocateRegistry.getRegistry(Config.getRMIHost(), Config.getRMIPort());
                    updateBarrelList(registry);
                } catch (Exception ex) {
                    // Continue with next attempt
                }
            }
        }
        
        return null;
    }
    
    @Override
    public List<SearchResult> searchPaginated(String query, int page, int pageSize) throws RemoteException {
        List<SearchResult> allResults = search(query);
        
        int start = page * pageSize;
        int end = Math.min(start + pageSize, allResults.size());
        
        if (start >= allResults.size()) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(allResults.subList(start, end));
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
        
        // Top 10 searches (sorted by frequency)
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
                // Barrel not available, skip
            }
        }
        
        return stats;
    }
    
    /**
     * Select a barrel using configured strategy
     */
    private synchronized StorageBarrelInterface selectBarrel() {
        if (barrels.isEmpty()) {
            return null;
        }
        
        String strategy = Config.getGatewayBarrelSelection();
        
        switch (strategy) {
            case "random":
                return barrels.get(new Random().nextInt(barrels.size()));
            
            case "round-robin":
            default:
                StorageBarrelInterface barrel = barrels.get(currentBarrelIndex);
                currentBarrelIndex = (currentBarrelIndex + 1) % barrels.size();
                return barrel;
        }
    }
    
    /**
     * Save gateway state for crash recovery
     */
    private void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(persistenceFile))) {
            oos.writeObject(new HashMap<>(searchFrequency));
            System.out.println("Gateway state saved");
        } catch (Exception e) {
            System.err.println("Failed to save gateway state: " + e.getMessage());
        }
    }
    
    /**
     * Load gateway state from disk
     */
    @SuppressWarnings("unchecked")
    private void loadState() {
        File file = new File(persistenceFile);
        if (!file.exists()) {
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(file))) {
            Map<String, Integer> loadedFreq = (Map<String, Integer>) ois.readObject();
            searchFrequency.putAll(loadedFreq);
            System.out.println("Gateway state recovered (" + searchFrequency.size() + " search queries)");
        } catch (Exception e) {
            System.err.println("Failed to load gateway state: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown gracefully
     */
    public void shutdown() {
        System.out.println("Gateway shutting down...");
        if (healthCheckTimer != null) {
            healthCheckTimer.cancel();
        }
        if (persistenceTimer != null) {
            persistenceTimer.cancel();
        }
        saveState();
        System.out.println("Gateway shutdown complete");
    }
    
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", Config.getRMIHost());
            
            Gateway gateway = new Gateway();
            gateway.connectToServices();
            
            Registry registry = LocateRegistry.getRegistry(Config.getRMIPort());
            registry.rebind("Gateway", gateway);
            
            System.out.println("Gateway is ready on " + Config.getRMIHost() + ":" + Config.getRMIPort());
            
            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                gateway.shutdown();
            }));
            
        } catch (Exception e) {
            System.err.println("Gateway exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}