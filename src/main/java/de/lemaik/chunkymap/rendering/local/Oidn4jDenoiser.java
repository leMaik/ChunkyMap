package de.lemaik.chunkymap.rendering.local;

import de.lemaik.chunky.denoiser.Denoiser;
import net.time4tea.oidn.Oidn;
import net.time4tea.oidn.OidnDevice;
import net.time4tea.oidn.OidnFilter;

import java.nio.FloatBuffer;

public class Oidn4jDenoiser implements Denoiser {
    @Override
    public float[] denoise(int width, int height, float[] beauty, float[] albedo, float[] normal) throws DenoisingFailedException {
        FloatBuffer albedoBuffer = Oidn.Companion.allocateBuffer(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                albedoBuffer.put((y * width + x) * 3, (float) Math.min(1.0, albedo[(y * width + x) * 3 + 0]));
                albedoBuffer.put((y * width + x) * 3 + 1, (float) Math.min(1.0, albedo[(y * width + x) * 3 + 1]));
                albedoBuffer.put((y * width + x) * 3 + 2, (float) Math.min(1.0, albedo[(y * width + x) * 3 + 2]));
            }
        }
        FloatBuffer normalBuffer = Oidn.Companion.allocateBuffer(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                normalBuffer.put((y * width + x) * 3, (float) normal[(y * width + x) * 3 + 0]);
                normalBuffer.put((y * width + x) * 3 + 1, (float) normal[(y * width + x) * 3 + 1]);
                normalBuffer.put((y * width + x) * 3 + 2, (float) normal[(y * width + x) * 3 + 2]);
            }
        }

        FloatBuffer buffer = Oidn.Companion.allocateBuffer(width, height);
        // TODO use multiple threads for post-processing
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.put((y * width + x) * 3, (float) Math.min(1.0, beauty[(y * width + x) * 3 + 0]));
                buffer.put((y * width + x) * 3 + 1, (float) Math.min(1.0, beauty[(y * width + x) * 3 + 1]));
                buffer.put((y * width + x) * 3 + 2, (float) Math.min(1.0, beauty[(y * width + x) * 3 + 2]));
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
            }
        }

        buffer.get(beauty);
        return beauty;
    }
}
