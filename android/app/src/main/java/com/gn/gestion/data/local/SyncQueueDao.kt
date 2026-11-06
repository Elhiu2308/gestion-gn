package com.gn.gestion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gn.gestion.services.SyncQueue

@Dao
interface SyncQueueDao {

    @Insert
    suspend fun insert(item: SyncQueue): Long

    @Query("SELECT * FROM sync_queue WHERE pendiente = 1 ORDER BY creadoEn ASC")
    suspend fun pendientes(): List<SyncQueue>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun borrar(id: Long)

    @Query("UPDATE sync_queue SET pendiente = 0 WHERE documentoId = :docId AND accion = :accion")
    suspend fun marcarResuelto(docId: Long, accion: String)
}