package com.kyant.backdrop.catalog

import androidx.compose.runtime.saveable.Saver

enum class CatalogDestination {
    Home,
    Upload,
    Settings,
    Reader;

    companion object {
        val Saver = Saver<CatalogDestination, String>(
            save = { it.name },
            restore = { enumValueOf<CatalogDestination>(it) }
        )
    }
}
