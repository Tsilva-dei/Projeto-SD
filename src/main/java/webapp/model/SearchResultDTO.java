package webapp.model;

public class SearchResultDTO {
    public String title;
    public String url;
    public String citation;

    public SearchResultDTO(){}

    public SearchResultDTO(String title, String url, String citation) {
        this.title = title;
        this.url = url;
        this.citation = citation;
    }
}