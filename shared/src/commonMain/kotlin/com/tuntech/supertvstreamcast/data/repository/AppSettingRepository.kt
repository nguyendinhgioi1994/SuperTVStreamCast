package com.tuntech.supertvstreamcast.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.tuntech.supertvstreamcast.domain.Feature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data object AppSettingPreferencesKey {
    val IS_ONBOARDING_SELECTED = booleanPreferencesKey("isOnboardingSelected")
}

class AppSettingRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private var startFeature: Feature? = null

    suspend fun saveOnboardingSelected(value: Boolean) {
        dataStore.edit {
            it[AppSettingPreferencesKey.IS_ONBOARDING_SELECTED] = value
        }
    }

    fun isOnboardingSelected(): Flow<Boolean> {
        return dataStore.data.map {
            it[AppSettingPreferencesKey.IS_ONBOARDING_SELECTED] == true
        }
    }

    /** The tab chosen in onboarding; the first Dashboard after it opens there, later launches open Home. */
    fun setStartFeature(feature: Feature) {
        startFeature = feature
    }

    fun consumeStartFeature(): Feature = (startFeature ?: Feature.HOME).also { startFeature = null }
}
