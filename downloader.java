import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;



public class downloader{

public String url;
public int paralel_threshold = 10;

public Document download_page() throws IOException{
    return Jsoup.connect(url).get();
}

public String getText(Document doc){
    return doc.text();
}

public List<String> getUrls(Document doc){
    List<String> urls = new ArrayList<>();
    Elements links = doc.select("a[href]");
    for(Element link: links){
        String url = link.absUrl("href");
        urls.add(url);
    }
    return urls;      
}

public static void main(String[] args){
    
}

}