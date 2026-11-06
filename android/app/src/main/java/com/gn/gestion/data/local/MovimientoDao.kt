package com.gn.gestion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gn.gestion.models.Movimiento
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mov: Movimiento): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(movs: List<Movimiento>)

    @Query("SELECT * FROM movimientos WHERE documentoUuid = :uuid ORDER BY fechaHora DESC")
    fun porDocumento(uuid: String): Flow<List<Movimiento>>

    @Query("SELECT * FROM movimientos WHERE documentoUuid = :uuid ORDER BY fechaHora DESC")
    suspend fun porDocumentoOnce(uuid: String): List<Movimiento>

    @Query("SELECT * FROM movimientos ORDER BY fechaHora DESC")
    suspend fun todos(): List<Movimiento>

    @Query("SELECT COUNT(*) FROM movimientos WHERE uuid = :uuid")
    suspend fun existe(uuid: String): Int
}