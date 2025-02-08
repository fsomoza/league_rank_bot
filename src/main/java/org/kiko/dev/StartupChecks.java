package org.kiko.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kiko.dev.adapters.MongoDbAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class StartupChecks {

    private final MongoDbAdapter mongoDbAdapter;

    public StartupChecks(){
        this.mongoDbAdapter = MongoDbAdapter.getInstance();
    }

    public void doChecks() {
        //go to riot version url and retrieve the las version

        String urlString = "https://ddragon.leagueoflegends.com/api/versions.json";

        try {
            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(new Request.Builder().url(urlString).build()).execute();
            if (!response.isSuccessful()){
                throw new IOException("error retrieving the vesion json");
            }

            String responseBody = response.body().string();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            if (!rootNode.isArray()){
                throw new Exception("response type of versions is not the expected one");
            }
            // get the first element of the array which is the last version
            String version = rootNode.get(0).toString();



        } catch (Exception e) {
            e.printStackTrace();
        }


//        String urlString = "https://ddragon.leagueoflegends.com/cdn/15.2.1/img/champion/" + championName + ".png";
//        URL url = new URL(urlString);
//
//        // Open connection with timeout settings
//        URLConnection connection = url.openConnection();
//        connection.setConnectTimeout(5000);
//        connection.setReadTimeout(5000);
    }

}
