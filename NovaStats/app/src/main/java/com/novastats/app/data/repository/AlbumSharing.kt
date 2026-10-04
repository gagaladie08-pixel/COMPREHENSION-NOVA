package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.domain.TitleNormalizer

/**
 * 💿 Albums multi-artistes (BO, albums d'événements, « Various Artists ») — règle 12.
 *
 * Un album partagé existe UNE fois (`artist_id` NULL, étiquette « Artistes variés » — jamais un artiste) ; tous ses
 * titres s'y rattachent quel que soit leur artiste principal ; son total = toutes les écoutes de ses titres.
 * [consolidate] met les données existantes en conformité avec la décision (mots-clés / « Various Artists » / marquage
 * manuel) : fusionne les albums du même nom coupés par artiste, ou redécoupe un partagé dont la marque a été retirée.
 * Les stats sont recalculées par l'appelant.
 */
object AlbumSharing {

    data class Report(val merged: Int, val split: Int)

    suspend fun consolidate(db: NovaDatabase, library: LibraryRepository): Report {
        var merged = 0; var split = 0
        db.albumDao().recomputeAggregates() // play_count fiable pour choisir l'album conservé
        val albums = db.albumDao().all()
        // ---- 1. Albums « normaux » dont le titre doit être partagé → un seul album sans propriétaire
        val toShare = albums.filter { it.artistId != null && library.isSharedAlbum(it.title, null) }.groupBy { TitleNormalizer.normalizeKey(it.title) }
        for ((key, group) in toShare) {
            val first = group.maxByOrNull { it.playCount }!!
            db.withTransaction {
                val target = db.albumDao().allShared().firstOrNull { TitleNormalizer.normalizeKey(it.title) == key }?.albumId
                    ?: db.albumDao().insert(AlbumEntity(title = first.title, titleRaw = first.titleRaw, artistId = null))
                for (al in group) {
                    mergeInto(db, al.albumId, target)
                    merged++
                }
            }
        }
        // ---- 2. Doublons d'albums partagés (même titre) → fusionnés
        db.albumDao().allShared().groupBy { TitleNormalizer.normalizeKey(it.title) }.values.filter { it.size > 1 }.forEach { dups ->
            val keep = dups.maxByOrNull { it.playCount }!!
            db.withTransaction { dups.filter { it.albumId != keep.albumId }.forEach { mergeInto(db, it.albumId, keep.albumId); merged++ } }
        }
        // ---- 3. Les titres dont les écoutes pointent vers un album partagé y sont rattachés (re-liaison : écoutes déplacées, pas le titre)
        for (al in db.albumDao().allShared()) db.albumDao().adoptTracksOf(al.albumId)
        // ---- 4. Albums partagés qui ne le sont plus (marque retirée « 0 », ou règle affinée) → redécoupés par artiste principal du titre
        for (al in db.albumDao().allShared()) {
            if (library.isSharedAlbum(al.title, null)) continue
            db.withTransaction {
                for (t in db.trackDao().inAlbum(al.albumId)) {
                    val newAlbum = library.resolveAlbum(al.titleRaw, t.artistId, forceShared = false)
                    db.trackDao().setAlbum(t.trackId, newAlbum)
                    db.scrobbleDao().setAlbumForTrack(t.trackId, newAlbum)
                    db.trackLinkDao().unlinkTrackAlbum(t.trackId, al.albumId)
                    db.trackLinkDao().insertTrackAlbum(com.novastats.app.data.db.entity.TrackAlbumEntity(trackId = t.trackId, albumId = newAlbum))
                    db.albumDao().fillCover(newAlbum, al.coverUrl, al.coverSource)
                }
                db.trackLinkDao().clearAlbumLinks(al.albumId)
                db.albumDao().delete(al.albumId)
                split++
            }
        }
        return Report(merged, split)
    }

    /** Déplace titres, écoutes, liens et pochette de `from` vers `into`, puis supprime `from`. */
    private suspend fun mergeInto(db: NovaDatabase, from: Long, into: Long) {
        if (from == into) return
        val src = db.albumDao().getById(from) ?: return
        db.trackDao().moveAlbum(from, into)
        db.scrobbleDao().moveAlbum(from, into)
        db.trackLinkDao().retargetAlbumLinks(from, into)
        db.trackLinkDao().clearAlbumLinks(from)
        db.albumDao().fillCover(into, src.coverUrl, src.coverSource)
        db.albumDao().delete(from)
        db.albumDao().adoptTracksOf(into)
    }
}
