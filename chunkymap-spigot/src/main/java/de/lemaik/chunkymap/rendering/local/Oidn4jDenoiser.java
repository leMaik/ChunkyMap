package de.lemaik.chunkymap.rendering.local;

import de.lemaik.chunky.denoiser.Denoiser;
import de.lemaik.chunkymap.ChunkyMapPlugin;
import net.time4tea.oidn.Oidn;
import net.time4tea.oidn.OidnDevice;
import net.time4tea.oidn.OidnFilter;

import java.nio.FloatBuffer;
import java.util.logging.Level;

public class Oidn4jDenoiser implements Denoiser {
    @Override
    public float[] denoise(int width, int height, float[] beauty, float[] albedo, float[] normal) {
        FloatBuffer albedoBuffer = Oidn.Companion.allocateBuffer(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                albedoBuffer.put((y * width + x) * 3, albedo[(y * width + x) * 3 + 0]);
                albedoBuffer.put((y * width + x) * 3 + 1, albedo[(y * width + x) * 3 + 1]);
                albedoBuffer.put((y * width + x) * 3 + 2, albedo[(y * width + x) * 3 + 2]);
            }
        }

        FloatBuffer normalBuffer = Oidn.Companion.allocateBuffer(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                normalBuffer.put((y * width + x) * 3, normal[(y * width + x) * 3 + 0]);
                normalBuffer.put((y * width + x) * 3 + 1, normal[(y * width + x) * 3 + 1]);
                normalBuffer.put((y * width + x) * 3 + 2, normal[(y * width + x) * 3 + 2]);
            }
        }

        FloatBuffer buffer = Oidn.Companion.allocateBuffer(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.put((y * width + x) * 3, beauty[(y * width + x) * 3 + 0]);
                buffer.put((y * width + x) * 3 + 1, beauty[(y * width + x) * 3 + 1]);
                buffer.put((y * width + x) * 3 + 2, beauty[(y * width + x) * 3 + 2]);
            }
        }

        Oidn oidn = new Oidn();
        try (OidnDevice device = oidn.newDevice(Oidn.DeviceType.DEVICE_TYPE_DEFAULT)) {
            try (OidnFilter filter = device.raytraceFilter()) {
                filter.setFilterImage(buffer, buffer, width, height);
                if (albedo != null) {
                    // albedo is required if normal is set
                    filter.setAdditionalImages(albedoBuffer, normalBuffer, width, height);
                }
                filter.commit();
                filter.execute();

                if (!device.error().ok()) {
                    ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger().log(Level.WARNING, "Denoiser failed: " + device.error().getMessage() + " " + device.error().getError().getExplanation());
                }
            }
        } catch (Exception e) {
            ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger().log(Level.WARNING, "Denoiser failed", e);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                beauty[(y * width + x) * 3] = buffer.get((y * width + x) * 3);
                beauty[(y * width + x) * 3 + 1] = buffer.get((y * width + x) * 3 + 1);
                beauty[(y * width + x) * 3 + 2] = buffer.get((y * width + x) * 3 + 2);
            }
        }
        return beauty;
    }
}
