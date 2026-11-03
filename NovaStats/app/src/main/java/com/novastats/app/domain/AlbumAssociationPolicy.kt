package com.novastats.app.domain

/** Règles de conservation de la borne qui distingue écoutes historiques et rattachements futurs. */
object AlbumAssociationPolicy {
    /** Fusion de backups : choisir la date la plus récente évite de rattacher rétroactivement des écoutes importées. */
    fun latestAssociationSince(existing: Long?, imported: Long?): Long? = listOfNotNull(existing, imported).maxOrNull()

    /** L'association d'album ne s'applique qu'à partir de son horodatage, jamais avant. */
    fun albumIdAt(albumId: Long?, associationSince: Long?, startedAt: Long): Long? =
        if (albumId != null && associationSince != null && startedAt < associationSince) null else albumId
}
