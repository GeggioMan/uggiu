package com.example.uggiu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_aliases")
data class DeviceAlias(
    @PrimaryKey val address: String,
    val alias: String
)
