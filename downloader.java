import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

public static HashMap<String,Set<String>> buildHashMap(String text,String url){
    HashMap<String,Set<String>> hm = new HashMap<>();
    String[] words = text.split("\\W+");
    for (String w :words){
        hm.computeIfAbsent(url, k -> new HashSet<>()).add(w);
    }
    return hm;
}

public static void parallelIndexing(url_queue urls,List<BarrelInterface> barrels,ConcurrentLinkedDeque<String> processados){
    try (ForkJoinPool pool = new ForkJoinPool(parallel_threshold)) {
        for(int i = 0;i<parallel_threshold;i++){
        pool.execute(() -> { 
            try {
                while (true) {
                    String url = urls.getNextUrl();
                    if (url!=null && !processados.contains(url)) {
                        System.out.println("Thread " + Thread.currentThread().getName() + " processando URL");

                        Document doc = download_page(url);
                        String texto = getText(doc);
                        addUrls(urls, doc);
                        HashMap<String,Set<String>> hm = buildHashMap(texto, url);

                        String titulo = doc.title();
                        String snippet = texto.length() > 100 ? texto.substring(0, 100) : texto;
                        PageInfo page = new PageInfo(url, titulo, snippet);
                        page.addOutLink(doc);

                        Map<String, PageInfo> pageInfoBatch = new HashMap<>();
                        pageInfoBatch.put(url, page);

                        for (BarrelInterface b : barrels) {
                            try {
                                b.putIndex(hm, pageInfoBatch);
                            } catch (RemoteException e) {}
                        }

                        processados.add(url);
                    }
                    Thread.sleep(500);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        }
        pool.shutdown();
    }
}


public static void main(String[] args) throws InterruptedException, RemoteException, MalformedURLException, NotBoundException{;
    ConcurrentLinkedDeque<String> urls_processados = new ConcurrentLinkedDeque<>();
    int queuePort = 1200;
    try {
        LocateRegistry.createRegistry(queuePort);
        System.out.println("RMI registry criado na porta " + queuePort);
    } catch (Exception e) {
        System.out.println("Registry já existe na porta " + queuePort);
    }

    Registry barrelRegistry = LocateRegistry.getRegistry("localhost", 1099);
    BarrelInterface Barrel1 = (BarrelInterface) barrelRegistry.lookup("barrel1");
    System.out.println("barrel1 registado");
    BarrelInterface Barrel2 = (BarrelInterface) barrelRegistry.lookup("barrel2");
    System.out.println("barrel2 registado");
    BarrelInterface Barrel3 = (BarrelInterface) barrelRegistry.lookup("barrel3");
    System.out.println("barrel3 registado");
    List<BarrelInterface> Barrels = Arrays.asList(Barrel1,Barrel2,Barrel3);

    Registry queueR = LocateRegistry.getRegistry("localhost",1099);
    url_queue queue = (url_queue) queueR.lookup("queue");
    System.out.println("queue registada");
    

    parallelIndexing(queue,Barrels,urls_processados);

}
}