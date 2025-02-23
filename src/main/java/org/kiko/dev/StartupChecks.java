package org.kiko.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.bson.Document;
import org.kiko.dev.adapters.MongoDbAdapter;
import java.io.IOException;

public class StartupChecks {

  private final MongoDbAdapter mongoDbAdapter;

  public StartupChecks() {
    this.mongoDbAdapter = MongoDbAdapter.getInstance();
  }

  public void doChecks() {
    // go to riot version url and retrieve the las version

    MongoDatabase database = mongoDbAdapter.getDatabase();

    String urlString = "https://ddragon.leagueoflegends.com/api/versions.json";

    try {
      OkHttpClient client = new OkHttpClient();
      Response response = client.newCall(new Request.Builder().url(urlString).build()).execute();
      if (!response.isSuccessful()) {
        throw new IOException("error retrieving the vesion json");
      }

      String responseBody = response.body().string();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode rootNode = objectMapper.readTree(responseBody);

      if (!rootNode.isArray()) {
        throw new Exception("response type of versions is not the expected one");
      }

      String collectionName = "dDragonVersion";
      Document filter = new Document("name", collectionName);

      Boolean doesVersionCollectionExists = database.listCollections()
          .filter(filter)
          .first() != null;

      if (doesVersionCollectionExists) {

      }

      MongoCollection<Document> versionCollection = database.getCollection("dDragonVersion");

      Document versionDocument = versionCollection.find().first();

      String versionFromDb = versionDocument.getString("version");
      // get the first element of the array which is the last version
      String version = rootNode.get(0).toString();

      if (versionFromDb != version) {

        // TODO Check if the version matches the one on the DB, if not, proceed to look
        // for a new champ addition and if theres a
        // new one, add it to the champions collection

      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // String urlString =
    // "https://ddragon.leagueoflegends.com/cdn/15.2.1/img/champion/" + championName
    // + ".png";
    // URL url = new URL(urlString);
    //
    // // Open connection with timeout settings
    // URLConnection connection = url.openConnection();
    // connection.setConnectTimeout(5000);
    // connection.setReadTimeout(5000);
  }

}
