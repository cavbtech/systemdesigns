package system.design.learnings;

import org.apache.http.HttpConnection;

import java.io.IOException;
import java.net.HttpURLConnection;

public interface PoolConnection {
    HttpURLConnection getPooledConnection(String url,String threadName) throws Exception;
}
