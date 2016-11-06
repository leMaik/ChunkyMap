package com.wertarbyte.renderservice.dynmapplugin.dynmap;

import org.dynmap.*;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.TileFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A map that uses the RenderService for rendering the tiles.
 */
public class RsMap extends HDMap {
    private final double azimuth;
    private final double inclination;
    private final IsoHDPerspective perspective;

    public RsMap(DynmapCore dynmap, ConfigurationNode config) {
        super(dynmap, config);
        this.azimuth = 135.0;
        this.inclination = 60.0;
        perspective = new IsoHDPerspective(dynmap, config);

        Matrix3D transform = new Matrix3D(0.0D, 0.0D, -1.0D, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D);
        transform.rotateXY(180.0D - this.azimuth);
        transform.rotateYZ(90.0D - this.inclination);
        transform.shearZ(0.0D, Math.tan(Math.toRadians(90.0D - this.inclination)));
        // transform.scale((double)this.basemodscale, (double)this.basemodscale, Math.sin(Math.toRadians(this.inclination)));
    }

    @Override
    public void addMapTiles(List<MapTile> list, DynmapWorld world, int tx, int ty) {
        list.add(new RsMapTile(world, perspective, this, tx, ty));
    }

    @Override
    public ImageFormat getImageFormat() {
        return ImageFormat.FORMAT_PNG; // TODO support jpg?
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
        return perspective.getTileCoords(world, x, y, z);
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        return perspective.getTileCoords(world, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        RsMapTile t = (RsMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tileOrdinalX();
        int y = t.tileOrdinalY();

        return new MapTile[]{
                new RsMapTile(w, perspective, this, x - 1, y - 1),
                new RsMapTile(w, perspective, this, x - 1, y + 1),
                new RsMapTile(w, perspective, this, x + 1, y - 1),
                new RsMapTile(w, perspective, this, x + 1, y + 1),
                new RsMapTile(w, perspective, this, x, y - 1),
                new RsMapTile(w, perspective, this, x + 1, y),
                new RsMapTile(w, perspective, this, x, y + 1),
                new RsMapTile(w, perspective, this, x - 1, y)};
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile mapTile) {
        return perspective.getRequiredChunks(mapTile);
    }

    @Override
    public List<MapType> getMapsSharingRender(DynmapWorld world) {
        ArrayList<MapType> maps = new ArrayList<>();
        for (MapType mt : world.maps) {
            if (mt instanceof RsMap) {
                maps.add(mt);
            }
        }
        return maps;
    }

    @Override
    public List<String> getMapNamesSharingRender(DynmapWorld dynmapWorld) {
        return getMapsSharingRender(dynmapWorld).stream().map(MapType::getName).collect(Collectors.toList());
    }
}
