package com.gn.gestion.views.perfil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gn.gestion.R
import com.gn.gestion.services.ApiConfig
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class PerfilFragment : Fragment(R.layout.fragment_perfil) {

    private val vm: GestionViewModel by activityViewModels()

    private var qrInstitucional: String? = null

    private val seleccionarGaleria = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            guardarYCargarFotoDesdeUri(uri)
        }
    }

    private val tomarFotoCamara = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            guardarYCargarFotoBitmap(bitmap)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cargarDatosLocales(view)
        cargarFotoPerfilGuardada()
        sincronizarPerfilWeb(view)

        view.findViewById<View>(R.id.container_perfil_avatar)?.setOnClickListener {
            mostrarOpcionesFotoPerfil()
        }

        view.findViewById<MaterialButton>(R.id.btn_actualizar_perfil)?.setOnClickListener {
            sincronizarPerfilWeb(view, mostrarAviso = true)
        }
    }

    private fun mostrarOpcionesFotoPerfil() {
        val archivo = File(requireContext().filesDir, "foto_perfil.jpg")
        val tieneFotoPersonalizada = archivo.exists() && archivo.length() > 0

        val opciones = if (tieneFotoPersonalizada) {
            arrayOf(
                "Tomar fotografía (Cámara)",
                "Elegir de la galería",
                "Restablecer imagen predeterminada"
            )
        } else {
            arrayOf(
                "Tomar fotografía (Cámara)",
                "Elegir de la galería"
            )
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Fotografía de perfil")
            .setItems(opciones) { _, position ->
                when (position) {
                    0 -> tomarFotoCamara.launch(null)
                    1 -> seleccionarGaleria.launch("image/*")
                    2 -> restablecerFotoPredeterminada()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarYCargarFotoBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val archivo = File(requireContext().filesDir, "foto_perfil.jpg")
                FileOutputStream(archivo).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                withContext(Dispatchers.Main) {
                    cargarFotoPerfilGuardada()
                    if (isAdded) {
                        Snackbar.make(requireView(), "Fotografía de perfil actualizada", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Snackbar.make(requireView(), "Error al guardar la imagen", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun guardarYCargarFotoDesdeUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                    val bitmap = BitmapFactory.decodeStream(ins)
                    if (bitmap != null) {
                        val archivo = File(requireContext().filesDir, "foto_perfil.jpg")
                        FileOutputStream(archivo).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        withContext(Dispatchers.Main) {
                            cargarFotoPerfilGuardada()
                            if (isAdded) {
                                Snackbar.make(requireView(), "Fotografía de perfil actualizada", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Snackbar.make(requireView(), "Error al cargar la imagen seleccionada", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun restablecerFotoPredeterminada() {
        val archivo = File(requireContext().filesDir, "foto_perfil.jpg")
        if (archivo.exists()) {
            archivo.delete()
        }
        cargarFotoPerfilGuardada()
        Snackbar.make(requireView(), "Imagen predeterminada restaurada", Snackbar.LENGTH_SHORT).show()
    }

    private fun cargarFotoPerfilGuardada() {
        val viewRef = view ?: return
        val imgAvatar = viewRef.findViewById<ImageView>(R.id.img_perfil_avatar) ?: return
        val archivo = File(requireContext().filesDir, "foto_perfil.jpg")

        if (archivo.exists() && archivo.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(archivo.absolutePath)
            if (bitmap != null) {
                imgAvatar.setImageBitmap(bitmap)
                return
            }
        }
        imgAvatar.setImageResource(R.drawable.logo_app)
    }

    private fun cargarDatosLocales(view: View) {
        val ctx = requireContext()
        val usr = TokenProvider.usuario(ctx) ?: "usuario"
        val nombre = TokenProvider.nombreCompleto(ctx).ifBlank { TokenProvider.nombre(ctx) }
        val rol = TokenProvider.rol(ctx).ifBlank { "OPERADOR" }
        val cargo = TokenProvider.cargo(ctx).ifBlank { "Servidor Público GN" }
        val grado = TokenProvider.grado(ctx)
        val area = TokenProvider.area(ctx).ifBlank { "Guardia Nacional Sede Central" }
        val correo = TokenProvider.correo(ctx).ifBlank { "$usr@gn.gob.mx" }

        view.findViewById<TextView>(R.id.tv_perfil_nombre)?.text = nombre.ifBlank { "Usuario GN" }
        view.findViewById<TextView>(R.id.tv_perfil_usuario)?.text = "@$usr"
        view.findViewById<TextView>(R.id.tv_perfil_rol)?.text = rol
        view.findViewById<TextView>(R.id.tv_perfil_cargo)?.text = cargo
        view.findViewById<TextView>(R.id.tv_perfil_grado)?.text = grado.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tv_perfil_area)?.text = area
        view.findViewById<TextView>(R.id.tv_perfil_correo)?.text = correo

        mostrarQrInstitucional(view)
    }

    private fun mostrarQrInstitucional(view: View) {
        val contenido = qrInstitucional
        val imagen = view.findViewById<ImageView>(R.id.img_firma_qr)
        val hash = view.findViewById<TextView>(R.id.tv_perfil_token_hash)

        if (contenido.isNullOrBlank()) {
            imagen?.setImageDrawable(null)
            hash?.text = "Hash Firma: —"
            return
        }

        hash?.text = "Hash Firma: ${hashFirma(contenido)}"

        lifecycleScope.launch(Dispatchers.Default) {
            val qrBitmap = generarQrBitmap(contenido, 512, 512)
            if (qrBitmap != null) {
                withContext(Dispatchers.Main) {
                    imagen?.setImageBitmap(qrBitmap)
                }
            }
        }
    }

    private fun hashFirma(contenido: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(contenido.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
            .take(8)
    }

    private fun sincronizarPerfilWeb(view: View, mostrarAviso: Boolean = false) {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(requireContext())) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dto = withContext(Dispatchers.IO) {
                    ApiFactory.api(requireContext()).me()
                }

                TokenProvider.guardarSesion(
                    context = requireContext(),
                    token = TokenProvider.token(requireContext()).orEmpty(),
                    usuario = dto.usuario.ifBlank { TokenProvider.usuario(requireContext()).orEmpty() },
                    nombre = dto.nombre.ifBlank { TokenProvider.nombre(requireContext()) },
                    rol = dto.rol.ifBlank { TokenProvider.rol(requireContext()) },
                    permisos = dto.permisos,
                    nombreCompleto = dto.nombreCompleto,
                    cargo = dto.cargo,
                    grado = dto.grado,
                    area = dto.area,
                    subdireccion = dto.subdireccion,
                    correo = dto.correo
                )

                val qr = withContext(Dispatchers.IO) {
                    runCatching {
                        ApiFactory.api(requireContext()).qrUsuario(dto.id)
                    }.getOrNull()
                }
                qrInstitucional = qr?.get("texto")?.asString?.takeIf { it.isNotBlank() }

                cargarDatosLocales(view)

                if (mostrarAviso && isAdded) {
                    Snackbar.make(view, "Perfil sincronizado con el sistema web", Snackbar.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                if (mostrarAviso && isAdded) {
                    Snackbar.make(view, "No se pudo conectar al servidor web", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generarQrBitmap(contenido: String, ancho: Int, alto: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, ancho, alto)
            val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
            for (x in 0 until ancho) {
                for (y in 0 until alto) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}