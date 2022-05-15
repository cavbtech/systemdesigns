package system.design.learnings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpURLConnectionTest {
    private static final Logger logger      = LogManager.getLogger(HttpURLConnectionTest.class);
    String[] listOfUrls =   new String[]{"https://timesofindia.indiatimes.com/",
                                         "https://medium.com/",
                                         "https://medium.com/double-pointer/system-design-interview-course-31ddb8dfdafc",
                                         "https://engoo.com/app/daily-news"};

    @Test
    public void testWithoutConnectionPool() throws InterruptedException {
        // Opening 100 just to open all connections at once.  This can be avoided but only opened for testing
        ExecutorService service                 =   Executors.newFixedThreadPool(100);
        List<Callable<Boolean>> callableList    =   new ArrayList<>();
        for (int i=0; i<4; i++){
            Callable<Boolean> callable = () -> Arrays.stream(listOfUrls).map(x -> SingleHttpURLConnection.go(x)).reduce((x, y) -> x && y).get();
            callableList.add(callable);
        }
        List<Future<Boolean>> listFutures       = service.invokeAll(callableList);
        long startTime = System.currentTimeMillis();
        List<Boolean> results                   = listFutures.stream().map(x-> {
                                                                    boolean result = false;
                                                                    try {
                                                                        result = x.get();
                                                                    } catch (InterruptedException e) {
                                                                        e.printStackTrace();
                                                                    } catch (ExecutionException e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                    return result;
                                                                }).collect(Collectors.toList());
        service.shutdown();
        long endTime = System.currentTimeMillis();
        long diff    = endTime - startTime;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        logger.info(String.format("Total time taken total mins=%d and total seconds=%d and milliseconds=%d",minutes, seconds,diff));
        // Here it is opening 400 connections because each thread is opening 4 URLs and each URL is a separate
        // HTTP connection.  This can be optimized with SimpleHttpConnectionPool which would help us in
        // opening only 4 connections. Check the other method testWithConnectionPool
        assertEquals(SingleHttpURLConnection.getConnectionCounter(),400);
    }

    @Test
    public void testWithConnectionPool() throws InterruptedException {
        // Opening 100 just to open all connections at once.  This can be avoided but only opened for testing
        ExecutorService service              =   Executors.newFixedThreadPool(100);
        SimpleHttpConnectionPool shcp        =   SimpleHttpConnectionPool.getInstance();
        List<Callable<HttpURLConnection>> callableList    =   new ArrayList<>();
        for (int i=0; i<100; i++){
            for(String url:listOfUrls){
                Callable<HttpURLConnection> callable = new Callable<HttpURLConnection>() {
                    @Override
                    public HttpURLConnection call() throws Exception {
                        return shcp.getPooledConnection(url,Thread.currentThread().getName());
                    }
                };
                callableList.add(callable);
            }
        }
        List<Future<HttpURLConnection>> listFutures       = service.invokeAll(callableList);
        long startTime = System.currentTimeMillis();
        List<HttpURLConnection> results                   = listFutures.stream().map(x-> {
            HttpURLConnection result = null;
            try {
                result = x.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return result;
        }).collect(Collectors.toList());

        service.shutdown();
        int totalHttpConnectionsOpened  = shcp.getOpenCunnections();
        long endTime                    = System.currentTimeMillis();
        long diff                       = endTime - startTime;
        long seconds                    = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes                    = TimeUnit.MILLISECONDS.toMinutes(diff);
        logger.info(String.format("Total time taken total mins=%d and total seconds=%d and milliseconds=%d",minutes, seconds,diff));
        // Ideally, there should be only 4 connections
        Set<Integer> justHashCodes      = results.stream().map(x->x.hashCode()).collect(Collectors.toSet());

        shcp.closeAllConnections();
        // Ideally there should be not more than 4 connections.  Indeed, the evict policy makes sure to evict
        // LRU connections and if the number of connections max connections in SimpleHttpConnectionPool.maxConnections
        // is less than number of URLs then the cache also maintains the same number of connections and hence
        // the condition of asset is always <= list of URLs
        assertTrue(totalHttpConnectionsOpened<=listOfUrls.length);

    }
}
