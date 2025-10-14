
public class downloader{

public String url;
public int paralel_threshold = 10;

public Document download_page(){
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
        urls.add(Url);
    }
    return urls;      
}

public static void main(String[] args){
    
}

}