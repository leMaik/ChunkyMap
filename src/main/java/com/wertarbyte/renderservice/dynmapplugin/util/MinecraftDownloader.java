package com.wertarbyte.renderservice.dynmapplugin.util;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A utility class to download Minecraft jars.
 */
public class MinecraftDownloader {
    public static CompletableFuture<Response> downloadMinecraft(String version) {
        CompletableFuture<Response> result = new CompletableFuture<Response>();
        new OkHttpClient.Builder().build()
                .newCall(new Request.Builder()
                        .url(String.format("https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar", version))
                        .get()
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        result.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        result.complete(response);
                    }
                });
        return result;
    }
}
