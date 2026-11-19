package com.pliki.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.pliki.app.data.AppDatabase
import com.pliki.app.data.FileOps
import com.pliki.app.data.StorageIndex

class PlikiApp : Application(), SingletonImageLoader.Factory {
    val db by lazy { AppDatabase.create(this) }
    val index by lazy { StorageIndex(this) }
    val ops by lazy { FileOps(this, db) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
