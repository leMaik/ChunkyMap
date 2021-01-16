/*
 * Copyright (c) 2016 Wertarbyte <http://wertarbyte.com>
 *
 * This file is part of the RenderService.
 *
 * Wertarbyte RenderService is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wertarbyte RenderService is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wertarbyte RenderService.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.lemaik.chunkymap;

import de.lemaik.chunkymap.rendering.local.ChunkyLogAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import se.llbit.log.Log;

/**
 * The main class.
 */
public class ChunkyMapPlugin extends JavaPlugin {

  @Override
  public void onLoad() {
    Log.setReceiver(new ChunkyLogAdapter(getLogger()), se.llbit.log.Level.ERROR,
        se.llbit.log.Level.WARNING, se.llbit.log.Level.INFO);
  }

  @Override
  public void onEnable() {
    Plugin dynmap = getServer().getPluginManager().getPlugin("dynmap");
    try (InputStream mapIcon = getResource("rs-map-icon.png")) {
      Path iconPath = Paths
          .get(dynmap.getDataFolder().getAbsolutePath(), "web", "images", "block_chunky.png");
      if (!iconPath.toFile().exists()) {
        Files.copy(mapIcon, iconPath);
      }
    } catch (IOException e) {
      getLogger().log(Level.WARNING, "Could not write chunky map icon", e);
    }
  }
}
