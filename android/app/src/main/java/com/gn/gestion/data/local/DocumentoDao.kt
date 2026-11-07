package com.gn.gestion.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gn.gestion.models.Documento
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: Documento): Long

    @Update
    suspend fun update(doc: Documento)

    @Delete
    suspend fun delete(doc: Documento)

    @Query("SELECT * FROM documentos WHERE eliminadoEn IS NULL ORDER BY fecha DESC")
    fun getAll(): Flow<List<Documento>>

    @Query("SELECT * FROM documentos WHERE id = :id")
    fun getById(id: Long): Flow<Documento?>

    @Query("SELECT * FROM documentos WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): Documento?

    @Query("SELECT * FROM documentos WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Documento?

    @Query("SELECT * FROM documentos WHERE uuid = :uuid LIMIT 1")
    fun getByUuidFlow(uuid: String): Flow<Documento?>

    @Query("SELECT * FROM documentos ORDER BY fecha DESC")
    suspend fun getAllOnce(): List<Documento>

    @Query("SELECT * FROM documentos WHERE estado = :estado")
    fun getByEstado(estado: String): Flow<List<Documento>>

    @Query("SELECT * FROM documentos WHERE syncPendiente = 1")
    suspend fun pendientesSync(): List<Documento>

    @Query("UPDATE documentos SET syncStatus = :estado, actualizadoEn = :t WHERE id = :id")
    suspend fun marcarEstadosync(id: Long, estado: String, t: Long)

    @Query("UPDATE documentos SET syncPendiente = 0 WHERE id = :id")
    suspend fun marcarPendienteEnvio(id: Long)

    @Query("SELECT * FROM documentos WHERE uuid = :uuid AND eliminadoEn IS NULL LIMIT 1")
    suspend fun activoPorUuid(uuid: String): Documento?

    @Query("SELECT * FROM documentos WHERE servidor_id = :id LIMIT 1")
    suspend fun getByServidorId(id: Long): Documento?

    @Query(
        "SELECT COUNT(*) FROM documentos " +
            "WHERE servidor_id IS NULL AND syncPendiente = 0 AND eliminadoEn IS NULL"
    )
    suspend fun countSinServidorId(): Int
}