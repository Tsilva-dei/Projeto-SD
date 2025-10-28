import java.rmi.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

/**
 * Downloader - Downloads web pages, extracts content and sends to Storage Barrels
 * Multiple downloaders work in parallel for better performance
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
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            
            // Get URL Queue
            urlQueue = (URLQueueInterface) registry.lookup("URLQueue");
            System.out.println("Downloader " + downloaderId + " connected to URLQueue");
            
            // Get all available Storage Barrels
            String[] boundNames = registry.list();
            for (String name : boundNames) {
                if (name.startsWith("StorageBarrel_")) {
                    StorageBarrelInterface barrel = (StorageBarrelInterface) registry.lookup(name);
                    barrels.add(barrel);
                    System.out.println("Downloader " + downloaderId + " connected to " + name);
                }
            }
            
            if (barrels.isEmpty()) {
                System.err.println("WARNING: No Storage Barrels found!");
            }
            
        } catch (Exception e) {
            System.err.println("Downloader " + downloaderId + " connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        System.out.println("Downloader " + downloaderId + " started");
        
        while (running) {
            try {
                // Get next URL from queue
                String url = urlQueue.getNextURL();
                
                if (url == null) {
                    // No URLs available, wait a bit
                    Thread.sleep(2000);
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
            
            // Download HTML using jsoup
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Googol Bot)")
                    .timeout(5000)
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
            
            // Send extracted links back to URL queue
            if (!links.isEmpty()) {
                urlQueue.addURLs(new ArrayList<>(links));
            }
            
            // Send indexed data to all Storage Barrels (reliable multicast)
            multicastToBarrels(url, title, citation, words, links);
            
            System.out.println("Downloader " + downloaderId + " finished: " + url + " (" + words.size() + " words, " + links.size() + " links)");
            
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
        for (String token : tokens) {
            if (token.length() > 2) { // Ignore very short words
                words.add(token);
            }
        }
        return words;
    }
    
    /**
     * Send indexed data to all Storage Barrels (reliable multicast)
     * Simple implementation: send to all barrels, retry on failure
     */
    private void multicastToBarrels(String url, String title, String citation, Set<String> words, Set<String> links) {
        for (StorageBarrelInterface barrel : barrels) {
            boolean success = false;
            int retries = 3;
            
            while (!success && retries > 0) {
                try {
                    barrel.indexPage(url, title, citation, words, links);
                    success = true;
                } catch (RemoteException e) {
                    retries--;
                    System.err.println("Failed to send to barrel, retries left: " + retries);
                    if (retries > 0) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
            }
        }
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
            downloader.stop();
            try {
                downloaderThread.join(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }
}