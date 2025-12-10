package rmi;

public class BarrelStats implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public String barrelId;
    public int indexSize;
    public double avgSearchTime;

    public String getBarrelId() { return barrelId; }
    public int getIndexSize() { return indexSize; }
    public double getAvgeSearchTime() { return avgSearchTime; }
    
    @Override
    public String toString() {
        return "Barrel " + barrelId + ": " + indexSize + " pages indexed, avg search time: " + 
               String.format("%.1f", avgSearchTime) + " deciseconds";
    }
}