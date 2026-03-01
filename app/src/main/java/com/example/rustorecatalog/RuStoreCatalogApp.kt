package com.example.rustorecatalog

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.example.rustorecatalog.network.BackendModule

class RuStoreCatalogApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(BackendModule.sharedHttpClient)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
