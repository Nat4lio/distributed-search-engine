import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/** Metadados mínimos sobre uma página Web. */
public class PageInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String url;
    public final String title;
    public final String snippet;
    public final Set<String> outLinks = new HashSet<>();

    public PageInfo(String url, String title, String snippet) {
        this.url = url; this.title = title; this.snippet = snippet;
    }
    public void addOutLink(String dest) { if (dest!=null && !dest.isBlank()) outLinks.add(dest); }
    @Override public String toString(){ return String.format("PageInfo[url=%s,title=%s,snippet=%s,outLinks=%d]", url,title,snippet,outLinks.size()); }
}
