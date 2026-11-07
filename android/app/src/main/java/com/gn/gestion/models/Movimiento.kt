package com.gn.gestion.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movimientos")
data class Movimiento(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = "",
    val documentoUuid: String = "",
    val documentoId: Long = 0,
    val accion: String = "",
    val estadoAnterior: String = "",
    val estadoNuevo: String = "",
    val comentario: String = "",
    val fechaHora: Long = System.currentTimeMillis(),
    val usuarioNombre: String = "",
    val area: String = "",
    val dispositivo: String = "",
    val sincronizado: Boolean = false
)

fun MovimientoDto.aMovimiento(): Movimiento =
    Movimiento(
        uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
        documentoUuid = documentoUuid,
        accion = accion,
        estadoAnterior = estadoAnterior ?: "",
        estadoNuevo = estadoNuevo ?: "",
        comentario = comentario ?: "",
        fechaHora = fechaHora ?: 0,
        usuarioNombre = usuarioNombre ?: "",
        area = area ?: "",
        dispositivo = dispositivo ?: "",
        sincronizado = true
    )