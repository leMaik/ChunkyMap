package de.lemaik.chunkymap.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A utility class to download Minecraft jars.
 */
public class MinecraftDownloader {

  public static CompletableFuture<Response> downloadMinecraft(String version) {
    return getVersionManifestUrl(version)
        .thenCompose(MinecraftDownloader::getClientUrl)
        .thenCompose(clientUrl -> {
          CompletableFuture<Response> result = new CompletableFuture<>();

          new OkHttpClient.Builder().build()
              .newCall(new Request.Builder()
                  .url(clientUrl)
                  .get()
                  .build())
              .enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                  result.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                  result.complete(response);
                }
              });
          return result;
        });
  }

  private static CompletableFuture<String> getVersionManifestUrl(final String version) {
    CompletableFuture<String> result = new CompletableFuture<>();
    new OkHttpClient.Builder().build().newCall(
        new Request.Builder().url("https://launchermeta.mojang.com/mc/game/version_manifest.json")
            .get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            try (
                ResponseBody body = response.body();
                Reader reader = body.charStream()
            ) {
              JsonObject parsed = new JsonParser().parse(reader)
                  .getAsJsonObject();
              for (JsonElement versionData : parsed.getAsJsonArray("versions")) {
                if (versionData.getAsJsonObject().get("id").getAsString()
                    .equals(version)) {
                  result
                      .complete(versionData.getAsJsonObject().get("url").getAsString());
                  return;
                }
              }
              result.completeExceptionally(
                  new Exception("Version " + version + " not found"));
            }
          }
        });

    return result;
  }

  private static CompletableFuture<String> getClientUrl(final String versionManifestUrl) {
    CompletableFuture<String> result = new CompletableFuture<>();
    new OkHttpClient.Builder().build()
        .newCall(new Request.Builder().url(versionManifestUrl).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            try (
                ResponseBody body = response.body();
                Reader reader = body.charStream()
            ) {
              JsonObject parsed = new JsonParser().parse(reader).getAsJsonObject();
              result.complete(
                  parsed.getAsJsonObject("downloads").getAsJsonObject("client").get("url")
                      .getAsString());
            }
          }
        });

    return result;
  }
}
