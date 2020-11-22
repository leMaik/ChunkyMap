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

package de.lemaik.chunkymap.rendering.rs;

/**
 * A render job.
 */
public class RenderJob {

  private String _id;
  private int spp;
  private int targetSpp;

  public String getId() {
    return _id;
  }

  public int getSpp() {
    return spp;
  }

  public int getTargetSpp() {
    return targetSpp;
  }
}
