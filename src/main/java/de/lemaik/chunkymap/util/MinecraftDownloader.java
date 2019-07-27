package de.lemaik.chunkymap.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

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
        new OkHttpClient.Builder().build().newCall(new Request.Builder().url("https://launchermeta.mojang.com/mc/game/version_manifest.json").get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        result.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        JsonObject parsed = new JsonParser().parse(response.body().string()).getAsJsonObject();
                        for (JsonElement versionData : parsed.getAsJsonArray("versions")) {
                            if (versionData.getAsJsonObject().get("id").getAsString().equals(version)) {
                                result.complete(versionData.getAsJsonObject().get("url").getAsString());
                                return;
                            }
                        }
                        result.completeExceptionally(new Exception("Version " + version + " not found"));
                    }
                });

        return result;
    }

    private static CompletableFuture<String> getClientUrl(final String versionManifestUrl) {
        CompletableFuture<String> result = new CompletableFuture<>();
        new OkHttpClient.Builder().build().newCall(new Request.Builder().url(versionManifestUrl).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        result.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        JsonObject parsed = new JsonParser().parse(response.body().string()).getAsJsonObject();
                        result.complete(parsed.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString());
                    }
                });

        return result;
    }
}
