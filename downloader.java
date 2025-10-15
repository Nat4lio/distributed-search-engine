import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.HashMap;
import java.io.IOException;



public class downloader{

static String url;
static ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<String>();
static HashMap<String,List<String>> index = new HashMap<>();
static int parallel_threshold = 10;

public Document download_page() throws IOException{
    return Jsoup.connect(url).get();
}

public String getText(Document doc){
    return doc.text();
}

public void addUrls(Document doc){
    Elements links = doc.select("a[href]");
    for(Element link: links){
        String url = link.absUrl("href");
        urls.add(url);
    }     
}

public void putHashMap(String text,String url){
    String[] words = text.split("\\W+");
    for (String w :words){
        if(!index.get(w).contains(url)){
            index.keySet().add(url);
        }
    }
}

public static void parallelIndexing(){
    try(ForkJoinPool pool = new ForkJoinPool(parallel_threshold)){
        pool.invoke(new Indexing(urls));
    }
}

public static class Indexing extends RecursiveAction{
    ConcurrentLinkedQueue<String> urls;
    public Indexing(ConcurrentLinkedQueue<String> urls){
        this.urls = urls;
    }
    public void compute(){
        while(!urls.isEmpty()){
        String url = urls.poll();
        downloader robot = new downloader();
        try{
        Document doc = robot.download_page();
        String texto = robot.getText(doc);
        robot.addUrls(doc);
        robot.putHashMap(texto,url);
        }
        catch(IOException e){}
        }
    }
}

public static void main(String[] args){
    
}

}