package app.lawnchair.data.wallpaper.service

import app.lawnchair.data.wallpaper.Wallpaper

/**
 * Holds the ranked wallpaper list for readers that cannot suspend, such as the long-press popup
 * picking its layout. Writes go through [update] on a background thread; reads never touch Room.
 */
class WallpaperCache {

    @Volatile
    private var topWallpapers: List<Wallpaper> = emptyList()

    fun get(): List<Wallpaper> = topWallpapers

    fun update(wallpapers: List<Wallpaper>) {
        topWallpapers = wallpapers
    }
}
