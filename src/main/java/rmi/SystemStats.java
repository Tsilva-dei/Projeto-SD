package rmi;
import java.util.List;
import java.util.Map;

public class SystemStats implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public Map<String, Integer> topSearches;
    public List<BarrelStats> barrelStats;

    public Map<String, Integer> getTopSearches() { return topSearches; }
    public List<BarrelStats> getBarrelStats() { return barrelStats; }
}