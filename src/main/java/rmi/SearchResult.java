package rmi;

import java.io.Serializable;

public class SearchResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String url;
    public String title;
    public String citation;
    public int incomingLinks;
    
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