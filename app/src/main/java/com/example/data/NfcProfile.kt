package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_profiles")
data class NfcProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String = "Personal"
)
