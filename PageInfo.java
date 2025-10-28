import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Estrutura simples para transportar informação sobre uma página indexada.
 */
public class PageInfo implements Serializable{
    private static final long serialVersionUID = 1L;
    public final String url;
    public final String title;
    public final String snippet; // citação curta
    public final Set<String> outLinks; // links que a página tem

    public PageInfo(String url, String title, String snippet) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.outLinks = new HashSet<>();
    }

    public void addOutLink(Document doc) {
        Elements links = doc.select("a[href]");
    for(Element link: links){
        String url = link.absUrl("href");
        outLinks.add(url);
    }
    }

    @Override
    public String toString() {
        return String.format("PageInfo[url=%s,title=%s,snippet=%s,outLinks=%d]", url, title, snippet, outLinks.size());
    }
}
