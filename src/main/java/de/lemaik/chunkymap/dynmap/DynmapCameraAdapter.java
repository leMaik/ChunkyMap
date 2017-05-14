package de.lemaik.chunkymap.dynmap;

import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.Vector3D;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.math.Vector3;

public class DynmapCameraAdapter {
    private final IsoHDPerspective perspective;
    private final Matrix3D transform;

    public DynmapCameraAdapter(IsoHDPerspective perspective) {
        this.perspective = perspective;

        transform = new Matrix3D();
        transform.scale(1.0D / (double) perspective.getModelScale(), 1.0D / (double) perspective.getModelScale(), 1.0D / Math.sin(Math.toRadians(perspective.inclination)));
        transform.shearZ(0.0D, -Math.tan(Math.toRadians(90.0D - perspective.inclination)));
        transform.rotateYZ(-(90.0D - perspective.inclination));
        transform.rotateXY(-180.0D + perspective.azimuth);
        Matrix3D coordswap = new Matrix3D(0.0D, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 0.0D, 0.0D);
        transform.multiply(coordswap);
    }

    public void apply(Camera camera, int tx, int ty, int mapzoomout) {
        double x = tx + 0.5;
        double y = ty + 0.5;
        Vector3D v = new Vector3D(x * (1 << mapzoomout) * 16 / perspective.getScale(), y * (1 << mapzoomout) * 16 / perspective.getScale(), 65);
        transform.transform(v);

        camera.setProjectionMode(ProjectionMode.PARALLEL);
        camera.setPosition(new Vector3(v.x, v.y, v.z));
        camera.setView((90 - perspective.azimuth + 90) / 180 * Math.PI, (-90 + perspective.inclination) / 180 * Math.PI, 0);
        camera.setFoV(128 / perspective.getScale());
        camera.setDof(Double.POSITIVE_INFINITY);
    }
}
