# ChunkyMap
ChunkyMap is a map renderer for [Dynmap][dynmap] that uses [Chunky][chunky] to render the map tiles. This combines photorealistic rendering with the convenience and automatic updates of Dynmap. ChunkyMap is implemented as a drop-in replacement for the `HDMap` that comes with Dynmap.

![banner](banner.png)

## Installation
1. Download the latest jar from [the releases page][latest-release] and put it in your plugins directory.
2. Edit `plugins/dynmap/worlds.txt` and change the `map` from `org.dynmap.hdmap.HDMap` to `de.lemaik.chunkymap.dynmap.ChunkyMap` for all maps that you want to render with Chunky.
3. Restart your server (_restart_ not reload).
4. Re-render the worlds you enabled the new renderer for by running the following command from the server console: `dynmap fullrender world` (replace `world` with the actual world name).
5. Wait for it… :hourglass:
6. Wait for it… :hourglass: (Sorry, rendering with Chunky takes a while, but it's worth it!)
7. Take a look at your new, shiny maps! :sparkles:

## Configuration
The maps can be configured by adding options to the map's section in the `world.txt` file.

| Option | Description | Default |
| --- | --- | --- |
| `samplesPerPixel` | Samples per pixel that Chunky should render. More SPP improves the render quality but also increases render time. | 100 |
| `chunkyThreads` | Number of threads per Chunky instance. More threads will decrease render time but increase the CPU load of your server. | 2 |
| `texturepack` | Texturepack path, relative to `plugins/dynmap`. Use this option to specify a texturepack for a map. The texturepack in Dynmap's `configuration.txt` is ignored by ChunkyMap. | *None*

## Ceveats
* Rendering maps with a `..._lowres` perspective doesn't work at the moment. As a workaround, change the perspective to `..._hires`.
* Rendering is pretty slow, but I'll improve this by rendering multiple tiles as one image in the future.
* ChunkyMap, at the moment, only works with Bukkit/Spigot. Supporting more servers would be awesome, though!

## Copyright & License
ChunkyMap is Copyright 2016 Maik Marschner (leMaik)

Permission to modify and redistribute is granted under the terms of the GNU General Public License, Version 3. See the `LICENSE` file for the full license.

ChunkyMap uses the following third-party libraries:
* **Chunky by Jesper Öqvist**  
  Chunky is covered by the GNU General Public License, Version 3. See the license file in `licenses/gpl-3.txt`.
* **Commons Math by the Apache Software Foundation**  
  Commons Math is covered by The Apache Software License, Version 2.0. See the license file in `licenses/commons-math.txt`.
* **DynmapCore by mikeprimm**  
  DynmapCore is covered by The Apache Software License, Version 2.0. See the license file in `licenses/apache-2.txt`.
* **Gson by Google**  
  Gson is covered by The Apache Software License, Version 2.0. See the license file in `licenses/apache-2.txt`.
* **OkHttp by Square**  
  OkHttp is covered by The Apache Software License, Version 2.0. See the license file in `licenses/apache-2.txt`.

## Trivia
Originally, ChunkyMap was created as a demo for the [RenderService][rs3]. This service allows rendering the tiles on a distributed network of renderers, which is often faster than rendering the tiles locally.

Ironically, I then implemented support for local tile rendering and removed support for using the RenderService. It will come back in the future.

[dynmap]: http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/1286593-dynmap
[chunky]: http://chunky.llbit.se/
[latest-release]: https://github.com/leMaik/ChunkyMap/releases/latest
[rs3]: https://bitbucket.org/account/user/wertarbyte/projects/RS