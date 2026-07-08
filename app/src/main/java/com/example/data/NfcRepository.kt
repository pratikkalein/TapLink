package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class NfcRepository(
    private val nfcProfileDao: NfcProfileDao,
    private val context: Context
) {
    private val sharedPrefs = context.getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE)

    fun getAllProfiles(): Flow<List<NfcProfile>> = nfcProfileDao.getAllProfiles()

    suspend fun getProfileById(id: Int): NfcProfile? = nfcProfileDao.getProfileById(id)

    suspend fun insertProfile(profile: NfcProfile) = nfcProfileDao.insertProfile(profile)

    suspend fun deleteProfile(profile: NfcProfile) {
        nfcProfileDao.deleteProfile(profile)
        if (getActiveProfileId() == profile.id) {
            setActiveProfileId(-1)
        }
    }

    suspend fun clearAll() {
        nfcProfileDao.clearAll()
        setActiveProfileId(-1)
    }

    fun getActiveProfileId(): Int {
        return sharedPrefs.getInt("active_profile_id", -1)
    }

    fun setActiveProfileId(id: Int) {
        sharedPrefs.edit().putInt("active_profile_id", id).apply()
    }
}
