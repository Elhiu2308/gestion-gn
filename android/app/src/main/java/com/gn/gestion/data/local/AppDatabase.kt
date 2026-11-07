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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaOrigen TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaDestino TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN referencia TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN numAnexos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN observaciones TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN clasificacion TEXT NOT NULL DEFAULT 'CONFIDENCIAL'")
                db.execSQL("ALTER TABLE documentos ADD COLUMN areaActual TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documentos ADD COLUMN compartido INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documentos ADD COLUMN respuestaConsolidada TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN servidor_id INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN id_remitente INTEGER")
                db.execSQL("ALTER TABLE documentos ADD COLUMN id_destinatario INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentos ADD COLUMN fechaLimiteHora TEXT NOT NULL DEFAULT ''")
            }
        }

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