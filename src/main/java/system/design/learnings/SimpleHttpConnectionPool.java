package system.design.learnings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SimpleHttpConnectionPool caches pool of HttpConnections for various sites based on the site name.
 * As of now, it can hold only 100 connections i.,e 100 sites.  If cache exceeds 100, then it applies LRU
 * (least recently used) algorithm to evict the connection
 * As of now, it maintains only one connection per site.  It can be enhanced to hold multiple connections per site
 * <p>
 * This class is a singleton class.  Although, Singleton is not a good practise, I am using it here as my assumption is
 * the same class gets used across the Application. May be dependency injection is another option but then it becomes
 * complex to implement without third party framework (ex:Spring).  As this is simple connection pool, I am just
 * following simple design
 *
 * This is not optimized ccode and certainly can be optimized with the locks
 */
public final class SimpleHttpConnectionPool implements PoolConnection {
    private static final Logger logger = LogManager.getLogger(SimpleHttpConnectionPool.class);
    //TODO: This can be taken from properties using static block
    // I am not implementing that as this is a simple connection pool
    private static final int maxConnections = 3;
    //Cache to hold connections based on the URL. This can any other data structure.  Map is easier to search with O(1)
    private final ConcurrentHashMap<String, HttpURLConnection> httpCache;
    //Deque to hold the list of connection strings in the order they created
    private final ConcurrentHashMap<String, Long> connectionLastUsed;

    ReentrantReadWriteLock lock              = new ReentrantReadWriteLock();
    Lock writeLock                           = lock.writeLock();
    Lock readLock                            = lock.readLock();

    //As the class is singleton, to avoid any multithreading issue, creating the class upfront
    private static SimpleHttpConnectionPool httpConnectionPool = new SimpleHttpConnectionPool(maxConnections);

    /**
     * SimpleHttpConnectionPool is private constructor as this class is treated as singleton
     *
     * @param maxConnections
     */
    private SimpleHttpConnectionPool(final int maxConnections) {
        httpCache           = new ConcurrentHashMap<>();
        connectionLastUsed  = new ConcurrentHashMap<>();
    }

    public int getTotalConnectionsOpened() {
        int totalConnections = 0;
        try{
            readLock.lock();
            totalConnections = httpCache.size();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            readLock.unlock();
        }
        return totalConnections;
    }

    public synchronized void closeAllConnections() {
        logger.info("connectionLastUsed=" + connectionLastUsed.size());
        connectionLastUsed.entrySet().forEach(x -> {
            logger.info("x=" + x);
            if (httpCache.containsKey(x.getKey())) {
                httpCache.remove(x.getKey()).disconnect();
            }
        });
        connectionLastUsed.clear();
    }

    /**
     * getInstance gives the SimpleHttpConnectionPool instance.
     * Made it as final so that others can't implement this
     *
     * @return
     */
    public static final SimpleHttpConnectionPool getInstance() {
        return httpConnectionPool;
    }

    /**
     * addToConnectionPool
     *
     * @param urlString
     * @throws IOException
     */
    private  void addToConnectionPool(String urlString,String threadName) throws Exception {
        try{
            writeLock.lock();
            // Possibility of multi threading errors and hence adding in synchronized block
            // This may be a little costly operation but then needed to avoid any multi threading issues
            if (httpCache.size() == maxConnections) {
                // This may be a costly operations to sort the data and figure out which one is latest
                // Thought of using Deque for this but that is creating a problem with number of records i,e
                // The number of URLs are only 4 but deque maintains the number of times it is called
                String lruURL = finalLRUConnection();
                logger.info("threadName="+threadName + "evicting " + lruURL + " based on LRU");
                if(httpCache.contains(lruURL)){
                    HttpURLConnection lruConnection = httpCache.remove(lruURL);
                    connectionLastUsed.remove(lruURL);
                    logger.info("threadName="+threadName +"lruURL=" + lruURL+ " lruConnection=" + lruConnection);
                    // Make sure to disconnect. It may get gced but it is good practice to disconnect first and then let
                    // gc collect the unsed objects
                    lruConnection.disconnect();
                }
            }
            URL url = new URL(urlString);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            httpCache.put(urlString, huc);
            connectionLastUsed.put(urlString, System.currentTimeMillis());
        }catch (Exception e){
            throw e;
        }finally{
            writeLock.unlock();
        }


    }

    /**
     * finalLRUConnection identifies the least recently used url.
     * <p>
     * This is synchronized method to avoid any concurrent updates.  Althought ConcurrentHashMap is used,
     * it is not guarenteed as some threads might be adding new entries but this method holds new entries until
     * sorty gets completed.  Thus it is a costly operations and there might be better ways to handle it
     *
     * @return
     */
    private  String finalLRUConnection() {
        try{
            readLock.lock();
            List<Map.Entry<String, Long>> list = new ArrayList<>(connectionLastUsed.entrySet());
            logger.info("before sorting list="+list);
            list.sort(Comparator.comparingLong(Map.Entry::getValue));
            logger.info("after sorting list="+list);
            return list.get(0).getKey();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            readLock.unlock();
        }
        return null;
    }


    /**
     * getPooledConnection, is the method that users can call after getting the SimpleHttpConnectionPool instance
     * from getInstance.
     * <p>
     * This method, checks if the connection already exist and if it is then it returns the connection and if it doesnt
     * exist then it opens up a connection, adds to the caches and returns the same connection
     *
     * @param url
     * @param threadName, really not required. Only used to debug
     * @return
     * @throws IOException
     */
    @Override
    public HttpURLConnection getPooledConnection(String url, String threadName) throws Exception {
        HttpURLConnection connection = null;
        boolean isURLExistInCache = httpCache.containsKey(url);
        logger.info("threadName=" + threadName + " url=" + url + " exist in httpCache=" + isURLExistInCache);
        if (!isURLExistInCache) {
            addToConnectionPool(url,threadName);
            logger.info("threadName=" + threadName + "url" + url + " is now added");
        }
        try{
            readLock.lock();
            connection = httpCache.get(url);
        }catch (Exception e){
            throw e;
        }finally {
            readLock.unlock();
        }

        try{
            writeLock.lock();
            // always update
            connectionLastUsed.put(url, System.currentTimeMillis());
        }catch (Exception e){
            throw e;
        }finally {
            writeLock.unlock();
        }

        return connection;
    }
}
