package com.novastats.app.domain

/** Transformations pures des catégories désactivées, partageables entre l'UI et les tests JVM. */
object NotificationPreferenceRules {
    fun setEnabled(disabled: Set<String>, category: String, enabled: Boolean): Set<String> =
        if (enabled) disabled - category else disabled + category

    fun setAllEnabled(disabled: Set<String>, categories: Collection<String>, enabled: Boolean): Set<String> {
        val knownCategories = categories.toSet()
        return if (enabled) disabled - knownCategories else disabled + knownCategories
    }

    fun areAllEnabled(disabled: Set<String>, categories: Collection<String>): Boolean =
        categories.none { it in disabled }
}
