package com.gn.gestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gn.gestion.models.Documento
import com.gn.gestion.models.Movimiento
import com.gn.gestion.services.SyncQueue

@Database(
    entities = [Documento::class, Movimiento::class, SyncQueue::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentoDao(): DocumentoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: nuevas columnas de documento + tablas de trazabilidad y cola de sinc. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN dependencia TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN fechaDocumento TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN fechaLimite TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN fechaRecepcion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN textoOcr TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN pdfNombre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN pdfVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN hashArchivo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN codigoQr TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN usuarioServidorId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN creadorServidorId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE documentos ADD COLUMN syncPendiente INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN creadoEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN actualizadoEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN eliminadoEn INTEGER")

                // Documentos heredados de la v1: asigna uuid estable y sello de tiempo.
                db.execSQL(
                    "UPDATE documentos SET uuid = 'v1-' || CAST(id AS TEXT), " +
                        "creadoEn = fechaRegistro, actualizadoEn = fechaRegistro, " +
                        "syncPendiente = 1 WHERE uuid = ''"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS movimientos (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL DEFAULT '', " +
                        "documentoUuid TEXT NOT NULL DEFAULT '', " +
                        "documentoId INTEGER NOT NULL DEFAULT 0, " +
                        "accion TEXT NOT NULL DEFAULT '', " +
                        "estadoAnterior TEXT NOT NULL DEFAULT '', " +
                        "estadoNuevo TEXT NOT NULL DEFAULT '', " +
                        "comentario TEXT NOT NULL DEFAULT '', " +
                        "fechaHora INTEGER NOT NULL, " +
                        "usuarioNombre TEXT NOT NULL DEFAULT '', " +
                        "dispositivo TEXT NOT NULL DEFAULT '', " +
                        "sincronizado INTEGER NOT NULL DEFAULT 0" +
                        ")"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_queue (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "documentoId INTEGER NOT NULL, " +
                        "uuid TEXT NOT NULL, " +
                        "accion TEXT NOT NULL, " +
                        "pendiente INTEGER NOT NULL DEFAULT 1, " +
                        "creadoEn INTEGER NOT NULL DEFAULT 0" +
                        ")"
                )
            }
        }

        /** v2 -> v3 (RBAC): campos del formulario registral completo. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaOrigen TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaDestino TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN referencia TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN numAnexos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN observaciones TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 -> v4 (flujo FGN): clasificación, área actual, compartición y respuesta consolidada. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN clasificacion TEXT NOT NULL DEFAULT 'CONFIDENCIAL'")
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaActual TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN compartido INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN respuestaConsolidada TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v4 -> v5: se persiste el ID del backend (`servidor_id`).
         *
         * La columna es Nullable a propósito: NULL significa "aún no se conoce
         * el ID del servidor". NO se rellena con la PK local de Room — hacer eso
         * reintroduciría exactamente el bug que esta migración corrige. Los
         * documentos ya sincronizados se rellenan en el siguiente sync, cuando el
         * servidor devuelve su `id` dentro del DTO.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN servidor_id INTEGER")
            }
        }

        /**
         * v5 -> v6: relación con el catálogo institucional de personas.
         *
         * `id_remitente` / `id_destinatario` son las claves foráneas a
         * `personas.id_persona` (1:1 con `usuarios`). Ambas son Nullable a
         * propósito: NULL significa "sin relación", que es el caso de los
         * documentos legados que sólo guardan el nombre como texto. Nunca se
         * rellena con el id local de Room.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN id_remitente INTEGER")
                db.execSQL("ALTER TABLE documentos ADD COLUMN id_destinatario INTEGER")
            }
        }

        /** v6 -> v7: hora límite del documento (fecha_limite_hora). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN fechaLimiteHora TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 -> v8: área del usuario en la trazabilidad (movimientos.area). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movimientos ADD COLUMN area TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gestion.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}