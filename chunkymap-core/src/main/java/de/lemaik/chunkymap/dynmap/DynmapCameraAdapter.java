package de.lemaik.chunkymap.dynmap;

import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.Vector3D;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.math.Vector3;

public class DynmapCameraAdapter {
    private final double inclination;
    private final double azimuth;
    private final int scale;
    private final Matrix3D transformMapToWorld;

    public DynmapCameraAdapter(IsoHDPerspective perspective) {
        this(perspective.inclination, perspective.azimuth, perspective.getModelScale());
    }

    public DynmapCameraAdapter(double inclination, double azimuth, int scale) {
        this.inclination = inclination;
        this.azimuth = azimuth;
        this.scale = scale;
        transformMapToWorld = new Matrix3D();
        transformMapToWorld.scale(1.0D / (double) scale,
                1.0D / (double) scale,
                1.0D / Math.sin(Math.toRadians(inclination)));
        transformMapToWorld.shearZ(0.0D, -Math.tan(Math.toRadians(90.0D - inclination)));
        transformMapToWorld.rotateYZ(-(90.0D - inclination));
        transformMapToWorld.rotateXY(-180.0D + azimuth);
        Matrix3D coordswap = new Matrix3D(0.0D, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 0.0D, 0.0D);
        transformMapToWorld.multiply(coordswap);
    }

    public void apply(Camera camera, int tx, int ty, int mapzoomout, int extrazoomout) {
        double x = tx + 0.5;
        double y = ty + 0.5;
        Vector3D v = new Vector3D(x * (1 << mapzoomout) * 64 / (double) scale,
                y * (1 << mapzoomout) * 64 / (double) scale, 65);
        transformMapToWorld.transform(v);

        camera.setProjectionMode(ProjectionMode.PARALLEL);
        camera.setPosition(new Vector3(v.x, v.y, v.z));
        camera.setView((90 - azimuth + 90) / 180 * Math.PI,
                (-90 + inclination) / 180 * Math.PI, 0);
        camera.setFoV(128.0 / (double) scale);
        camera.setDof(Double.POSITIVE_INFINITY);
    }
}
