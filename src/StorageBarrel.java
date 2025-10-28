import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/**
 * Storage Barrel - Stores the inverted index and handles search queries
 * Multiple barrels replicate the same data for fault tolerance
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
    
    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.invertedIndex = new ConcurrentHashMap<>();
        this.pageInfoMap = new ConcurrentHashMap<>();
        this.incomingLinks = new ConcurrentHashMap<>();
        System.out.println("StorageBarrel " + barrelId + " initialized");
    }
    
    @Override
    public void indexPage(String url, String title, String citation, Set<String> words, Set<String> links) throws RemoteException {
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
        
        System.out.println("Indexed page: " + url + " with " + words.size() + " words");
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
            int incomingLinkCount = incomingLinks.getOrDefault(url, Collections.emptySet()).size();
            results.add(new SearchResult(url, info.title, info.citation, incomingLinkCount));
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
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java StorageBarrel <barrel-id>");
            return;
        }
        
        try {
            String barrelId = args[0];
            System.setProperty("java.rmi.server.hostname", "localhost");
            
            StorageBarrel barrel = new StorageBarrel(barrelId);
            
            Registry registry = LocateRegistry.getRegistry(1099);
            registry.rebind("StorageBarrel_" + barrelId, barrel);
            
            System.out.println("StorageBarrel " + barrelId + " is ready");
            
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
    void indexPage(String url, String title, String citation, Set<String> words, Set<String> links) throws RemoteException;
    List<SearchResult> search(Set<String> terms) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;
    int getIndexSize() throws RemoteException;
    double getAverageSearchTime() throws RemoteException;
    String getBarrelId() throws RemoteException;
}

/**
 * Page information stored for each URL
 */
class PageInfo implements Serializable {
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