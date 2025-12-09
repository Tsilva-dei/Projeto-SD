package rmi;

import java.rmi.*;
import java.rmi.registry.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

/**
 * Downloader - Downloads web pages, extracts content and sends to Storage Barrels
 * Multiple downloaders work in parallel for better performance
 * 
 * FEATURES:
 * - Reliable multicast with acknowledgment from all barrels
 * - Configurable retry logic
 * - Ensures only one downloader processes each URL
 */
public class Downloader implements Runnable {
    
    private final String downloaderId;
    private URLQueueInterface urlQueue;
    private List<StorageBarrelInterface> barrels;
    private volatile boolean running = true;
    
    public Downloader(String downloaderId) {
        this.downloaderId = downloaderId;
        this.barrels = new ArrayList<>();
    }
    
    /**
     * Connect to RMI registry and get remote references
     */
    public void connectToServices() {
        try {
            Registry registry = LocateRegistry.getRegistry(Config.getRMIHost(), Config.getRMIPort());
            
            // Get URL Queue
            urlQueue = (URLQueueInterface) registry.lookup("URLQueue");
            System.out.println("Downloader " + downloaderId + " connected to URLQueue");
            
            // Get all available Storage Barrels
            updateBarrelList(registry);
            
        } catch (Exception e) {
            System.err.println("Downloader " + downloaderId + " connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update list of available barrels
     */
    private void updateBarrelList(Registry registry) {
        try {
            barrels.clear();
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith("StorageBarrel_")) {
                    StorageBarrelInterface barrel = (StorageBarrelInterface) registry.lookup(name);
                    barrels.add(barrel);
                    System.out.println("Downloader " + downloaderId + " connected to " + name);
                }
            }
            
            if (barrels.isEmpty()) {
                System.err.println("WARNING: Downloader " + downloaderId + " - No Storage Barrels found!");
            }
        } catch (Exception e) {
            System.err.println("Error updating barrel list: " + e.getMessage());
        }
    }
    
    @Override
    public void run() {
        System.out.println("Downloader " + downloaderId + " started");
        
        while (running) {
            try {
                // Get next URL from queue (only one downloader gets each URL)
                String url = urlQueue.getNextURL();
                
                if (url == null) {
                    // No URLs available, wait a bit
                    Thread.sleep(Config.getDownloaderSleepNoURLs());
                    continue;
                }
                
                // Download and process the page
                processPage(url);
                
            } catch (RemoteException e) {
                System.err.println("Downloader " + downloaderId + " RMI error: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                    connectToServices(); // Try to reconnect
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Downloader " + downloaderId + " error: " + e.getMessage());
            }
        }
        
        System.out.println("Downloader " + downloaderId + " stopped");
    }
    
    /**
     * Download and process a web page
     */
    private void processPage(String url) {
        try {
            System.out.println("Downloader " + downloaderId + " processing: " + url);
            
            // Download HTML using jsoup with configured settings
            Document doc = Jsoup.connect(url)
                    .userAgent(Config.getDownloaderUserAgent())
                    .timeout(Config.getDownloaderJsoupTimeout())
                    .get();
            
            // Extract title
            String title = doc.title();
            if (title.isEmpty()) {
                title = url;
            }
            
            // Extract text and create citation (first 150 chars)
            String text = doc.body().text();
            String citation = text.length() > 150 ? text.substring(0, 150) + "..." : text;
            
            // Extract words (tokenize)
            Set<String> words = extractWords(text);
            
            // Extract links
            Set<String> links = new HashSet<>();
            Elements linkElements = doc.select("a[href]");
            for (Element link : linkElements) {
                String absUrl = link.attr("abs:href");
                if (absUrl != null && !absUrl.isEmpty() && absUrl.startsWith("http")) {
                    links.add(absUrl);
                }
            }
            
            // Send extracted links back to URL queue (for recursive indexing)
            if (!links.isEmpty()) {
                urlQueue.addURLs(new ArrayList<>(links));
            }
            
            // Send indexed data to all Storage Barrels (reliable multicast with ACK)
            boolean success = reliableMulticastToBarrels(url, title, citation, words, links);
            
            if (success) {
                System.out.println("Downloader " + downloaderId + " finished: " + url + 
                                 " (" + words.size() + " words, " + links.size() + " links)");
            } else {
                System.err.println("Downloader " + downloaderId + " FAILED to index: " + url);
            }
            
        } catch (IOException e) {
            System.err.println("Downloader " + downloaderId + " failed to download " + url + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Downloader " + downloaderId + " error processing " + url + ": " + e.getMessage());
        }
    }
    
    /**
     * Extract words from text (simple tokenization)
     */
    private Set<String> extractWords(String text) {
        Set<String> words = new HashSet<>();
        String[] tokens = text.toLowerCase().split("\\W+");
        int minWordLength = Config.getSearchMinWordLength();
        
        for (String token : tokens) {
            if (token.length() >= minWordLength) {
                words.add(token);
            }
        }
        return words;
    }
    
    /**
     * Reliable Multicast: Send to ALL barrels with acknowledgment
     * Ensures all barrels receive the same information (consistency)
     * Uses retry logic for fault tolerance
     */
    private boolean reliableMulticastToBarrels(String url, String title, String citation, 
                                               Set<String> words, Set<String> links) {
        if (barrels.isEmpty()) {
            System.err.println("No barrels available for multicast");
            return false;
        }
        
        int retryCount = Config.getDownloaderRetryCount();
        int retryDelay = Config.getDownloaderRetryDelay();
        
        // Track which barrels have acknowledged
        Set<StorageBarrelInterface> acknowledgedBarrels = new HashSet<>();
        Set<StorageBarrelInterface> failedBarrels = new HashSet<>();
        
        // Try to send to all barrels
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            if (acknowledgedBarrels.size() == barrels.size()) {
                // All barrels acknowledged - success!
                System.out.println("All " + barrels.size() + " barrels acknowledged indexing of " + url);
                return true;
            }
            
            if (attempt > 0) {
                System.out.println("Retry attempt " + attempt + " for remaining barrels");
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            
            // Try to send to barrels that haven't acknowledged yet
            for (StorageBarrelInterface barrel : barrels) {
                if (acknowledgedBarrels.contains(barrel) || failedBarrels.contains(barrel)) {
                    continue; // Already done or permanently failed
                }
                
                try {
                    boolean ack = barrel.indexPage(url, title, citation, words, links);
                    if (ack) {
                        acknowledgedBarrels.add(barrel);
                        System.out.println("Barrel acknowledged: " + barrel.getBarrelId());
                    } else {
                        System.err.println("Barrel returned NACK: " + barrel.getBarrelId());
                    }
                } catch (RemoteException e) {
                    System.err.println("Failed to reach barrel: " + e.getMessage());
                    if (attempt == retryCount) {
                        failedBarrels.add(barrel);
                    }
                }
            }
        }
        
        // Check if at least one barrel acknowledged
        if (acknowledgedBarrels.isEmpty()) {
            System.err.println("CRITICAL: No barrels acknowledged indexing of " + url);
            return false;
        }
        
        if (acknowledgedBarrels.size() < barrels.size()) {
            System.err.println("WARNING: Only " + acknowledgedBarrels.size() + "/" + 
                             barrels.size() + " barrels acknowledged");
        }
        
        return !acknowledgedBarrels.isEmpty();
    }
    
    public void stop() {
        running = false;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Downloader <downloader-id>");
            return;
        }
        
        String downloaderId = args[0];
        Downloader downloader = new Downloader(downloaderId);
        
        downloader.connectToServices();
        
        Thread downloaderThread = new Thread(downloader);
        downloaderThread.start();
        
        // Graceful shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Downloader " + downloaderId + " shutting down...");
            downloader.stop();
            try {
                downloaderThread.join(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }
}