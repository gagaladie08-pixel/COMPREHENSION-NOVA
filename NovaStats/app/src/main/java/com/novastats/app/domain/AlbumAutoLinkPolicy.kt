package com.novastats.app.domain

/**
 * Règle d'acceptation d'un rattachement d'album automatique (passe « albums manquants »).
 *
 * Distincte de la règle stricte de la recherche à la demande (`automaticAlbumSuggestion`, qui exige une
 * proposition **unique**, ≥ 95 %, vérifiée par liste de pistes et corroborée par 2 catalogues) : ici on
 * répète la recherche en lot, des jours après la sortie, pour les titres restés sans album. Le rattachement
 * n'est accepté que si la liste de pistes de l'album candidat contient réellement le titre.
 *
 * Compilation et type inconnu restent exclus : ils passent par la confirmation manuelle de l'éditeur.
 */
object AlbumAutoLinkPolicy {

    /** Score minimum quand la source a pu vérifier la liste de pistes (le titre y figure). */
    const val MIN_CONFIDENCE_VERIFIED = 90

    /** Score minimum quand aucune liste de pistes n'a pu être vérifiée : on exige alors la quasi-certitude. */
    const val MIN_CONFIDENCE_UNVERIFIED = 95

    /**
     * @param confidence score de confiance de la proposition (0..100)
     * @param tracklistVerified le titre a été retrouvé dans la liste de pistes de l'album candidat
     * @param kind type de sortie renvoyé par les catalogues
     */
    fun shouldAutoLink(
        confidence: Int,
        tracklistVerified: Boolean,
        kind: AlbumReleaseKind
    ): Boolean {
        if (!AlbumReleasePolicy.isAutoLinkable(kind)) return false
        val threshold = if (tracklistVerified) MIN_CONFIDENCE_VERIFIED else MIN_CONFIDENCE_UNVERIFIED
        return confidence >= threshold
    }
}
