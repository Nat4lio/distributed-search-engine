import java.io.Serializable;

public class SearchResult implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String url;
    public final String title;
    public final String snippet;
    public final int score;
    public SearchResult(String url, String title, String snippet, int score){ this.url=url; this.title=title; this.snippet=snippet; this.score=score; }
    @Override public String toString(){ return String.format("SearchResult[score=%d,url=%s,title=%s]\n  %s", score,url,title,snippet); }
}
