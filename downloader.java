import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


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

public static void putHashMap(ConcurrentHashMap<String,Set<String>> index,String text,String url){
    String[] words = text.split("\\W+");
    for (String w :words){
        index.computeIfAbsent(w, k -> new HashSet<>()).add(url);
    }
}

public static void parallelIndexing(ConcurrentHashMap<String,Set<String>> index,url_queue urls,BarrelInterface barrel){
    try (ForkJoinPool pool = new ForkJoinPool(parallel_threshold)) {
        for(int i = 0;i<parallel_threshold;i++){
        pool.execute(new Robots(urls,index,barrel));
        }
        pool.shutdown();
    }
}

public static class Robots extends RecursiveAction{
    url_queue urls;
    ConcurrentHashMap<String,Set<String>> index;
    BarrelInterface barrel;
    public Robots(url_queue urls,ConcurrentHashMap<String,Set<String>> index,BarrelInterface barrel){
        this.urls = urls;
        this.index = index;
        this.barrel = barrel;
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
            String titulo = doc.title();
            String snippet = texto.length() > 100 ? texto.substring(0,100) : texto;
            PageInfo page = new PageInfo(url, titulo, snippet);
            Map<String,PageInfo> pageInfoBatch = new HashMap<>();
            pageInfoBatch.put(url,page);
            barrel.putIndex(index,pageInfoBatch);
            }
            catch(IOException e){}
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

public static void main(String[] args) throws InterruptedException, RemoteException, MalformedURLException, NotBoundException{;
    ConcurrentHashMap<String,Set<String>> index = new ConcurrentHashMap<>();
    int queuePort = 1200;
    try {
        LocateRegistry.createRegistry(queuePort);
        System.out.println("RMI registry criado na porta " + queuePort);
    } catch (Exception e) {
        System.out.println("Registry já existe na porta " + queuePort);
    }

    Registry barrelRegistry = LocateRegistry.getRegistry("localhost", 1099);
    BarrelInterface barrel = (BarrelInterface) barrelRegistry.lookup("Barrel");

    Registry queueR = LocateRegistry.getRegistry("localhost",1099);
    url_queue queue = (url_queue) queueR.lookup("queue");
    

    parallelIndexing(index, queue, barrel);
}
}