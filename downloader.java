import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.HashSet;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;





public class downloader{


static int parallel_threshold = 10;

public static Document download_page(String url) throws IOException{
    return Jsoup.connect(url).get();
}

public static String getText(Document doc){
    return doc.text();
}

public static void addUrls(url_queue urls,Document doc) throws RemoteException{
    Elements links = doc.select("a[href]");
    for(Element link: links){
        String url = link.absUrl("href");
        urls.addUrl(url);
    }     
}

public static void putHashMap(ConcurrentHashMap<String,HashSet<String>> index,String text,String url){
    String[] words = text.split("\\W+");
    for (String w :words){
        if(!w.isEmpty())
        if(!index.get(w).contains(url)){
            index.putIfAbsent(w, new HashSet<>());
            index.get(w).add(url);
        }
    }
}

public static void parallelIndexing(ConcurrentHashMap<String,HashSet<String>> index,url_queue urls){
    try (ForkJoinPool pool = new ForkJoinPool(parallel_threshold)) {
        pool.invoke(new Robots(urls,index));
    }
}

public static class Robots extends RecursiveAction{
    url_queue urls;
    ConcurrentHashMap<String,HashSet<String>> index;
    public Robots(url_queue urls,ConcurrentHashMap<String,HashSet<String>> index){
        this.urls = urls;
        this.index = index;
    }
    public void compute(){
        try {
            while(!urls.isEmpty()){
            String url = urls.getNextUrl();
            try{
            Document doc = download_page(url);
            String texto = getText(doc);
            addUrls(urls,doc);
            putHashMap(index,texto,url);
            }
            catch(IOException e){}
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

public static void main(String[] args) throws InterruptedException, RemoteException, MalformedURLException, NotBoundException{;
    ConcurrentHashMap<String,HashSet<String>> index = new ConcurrentHashMap<>();
    url_queue queue = (url_queue) Naming.lookup("queue");
    String first = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal";
    queue.addUrl(first);
    parallelIndexing(index, queue);
}
}