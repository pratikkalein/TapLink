package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcProfileDao {
    @Query("SELECT * FROM nfc_profiles")
    fun getAllProfiles(): Flow<List<NfcProfile>>

    @Query("SELECT * FROM nfc_profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): NfcProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: NfcProfile)

    @Delete
    suspend fun deleteProfile(profile: NfcProfile)

    @Query("DELETE FROM nfc_profiles")
    suspend fun clearAll()
}
