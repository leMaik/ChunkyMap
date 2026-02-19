package de.lemaik.chunkymap.rendering.local;

import de.lemaik.chunky.denoiser.Denoiser;
import net.time4tea.oidn.Oidn;
import net.time4tea.oidn.OidnDevice;
import net.time4tea.oidn.OidnFilter;
import se.llbit.log.Log;

import java.nio.FloatBuffer;

public class Oidn4jDenoiser implements Denoiser {
    @Override
    public float[] denoise(int width, int height, float[] beauty, float[] albedo, float[] normal) {
        FloatBuffer albedoBuffer = Oidn.Companion.allocateBuffer(width, height);
        copy(width, height, albedo, albedoBuffer);

        FloatBuffer normalBuffer = Oidn.Companion.allocateBuffer(width, height);
        copy(width, height, normal, normalBuffer);

        FloatBuffer buffer = Oidn.Companion.allocateBuffer(width, height);
        copy(width, height, beauty, buffer);

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
                    Log.warn("Denoiser failed: " + device.error().getMessage() + " " + device.error().getError().getExplanation());
                }
            }
        } catch (Exception e) {
            Log.warn("Denoiser failed", e);
        }

        copy(width, height, buffer, beauty);

        return beauty;
    }

    private void copy(int width, int height, float[] source, FloatBuffer dest) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dest.put((y * width + x) * 3 + 0, source[(y * width + x) * 3 + 0]);
                dest.put((y * width + x) * 3 + 1, source[(y * width + x) * 3 + 1]);
                dest.put((y * width + x) * 3 + 2, source[(y * width + x) * 3 + 2]);
            }
        }
    }

    private void copy(int width, int height, FloatBuffer source, float[] dest) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dest[(y * width + x) * 3 + 0] = source.get((y * width + x) * 3 + 0);
                dest[(y * width + x) * 3 + 1] = source.get((y * width + x) * 3 + 1);
                dest[(y * width + x) * 3 + 2] = source.get((y * width + x) * 3 + 2);
            }
        }
    }
}
