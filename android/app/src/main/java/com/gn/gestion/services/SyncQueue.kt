package com.gn.gestion.services;

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val documentoId: Long,
    val uuid: String,
    val accion: String,
    val pendiente: Boolean = true,
    val creadoEn: Long = System.currentTimeMillis()
)