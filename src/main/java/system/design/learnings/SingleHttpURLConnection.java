package system.design.learnings;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpURLConnection;
import java.net.URL;


public class SingleHttpURLConnection {
    private static final Logger logger      = LogManager.getLogger(SingleHttpURLConnection.class);
    private static int connectionCounter    = 0;
    public static void main(String[] args){
        go("https://timesofindia.indiatimes.com/");
    }

    public static int getConnectionCounter(){
        return connectionCounter;
    }

    public static boolean go(String urlString){
        boolean isSuccessfull = false;
        try{
            URL url=new URL(urlString);
            logger.info("Time check before openning the connection="+urlString);
            HttpURLConnection huc=(HttpURLConnection)url.openConnection();
            logger.info("Time check after openning the connection"+urlString);
            synchronized (SingleHttpURLConnection.class){
                connectionCounter++;
            }
            huc.disconnect();
            isSuccessfull =  true;
        }
        catch(Exception e){
            e.printStackTrace();
            isSuccessfull = false;
        }
        logger.info("Total connection opened="+connectionCounter);
        return isSuccessfull;
    }
}
