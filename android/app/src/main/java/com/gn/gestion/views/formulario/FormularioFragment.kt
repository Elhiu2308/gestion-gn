package com.gn.gestion.views.formulario

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.models.*
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.util.OpcionItem
import com.gn.gestion.util.colorRes
import com.gn.gestion.util.Hora
import com.gn.gestion.util.formatFecha
import com.gn.gestion.util.mensajeServidor
import com.gn.gestion.util.pintarOpciones
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class FormularioFragment : Fragment(R.layout.fragment_formulario) {

    private companion object {
        private const val TAG_GUARDAR = "GN_GUARDAR"
    }

    private val vm: GestionViewModel by activityViewModels()

    private var esEdicion = false
    private var original: Documento? = null
    private lateinit var tipo: String
    private lateinit var estado: String
    private lateinit var urgencia: String
    private lateinit var clasificacion: String
    private var fecha: Long = System.currentTimeMillis()
    private var fechaLimite: String = ""
    private lateinit var inputFechaLimite: TextView
    private lateinit var inputHoraLimite: EditText
    private lateinit var inputFolio: EditText
    private lateinit var inputAsunto: EditText
    private lateinit var inputDestinatario: EditText
    private lateinit var inputReferencia: EditText
    private lateinit var inputNumAnexos: EditText
    private lateinit var inputNotas: EditText

    private lateinit var cascadaDest: CascadaPersonas
    private lateinit var spRemAreaLock: Spinner
    private lateinit var spRemSubLock: Spinner
    private lateinit var spRemPerLock: Spinner

    private lateinit var lblDestDept: View
    private lateinit var cardDestDept: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val id = arguments?.getLong("id", -1L) ?: -1L
        if (id > 0) {
            original = vm.todos.value.firstOrNull { it.id == id }
            esEdicion = original != null
        }
        val doc = original

        val folioArg = arguments?.getString("folio").orEmpty()
        val asuntoArg = arguments?.getString("asunto").orEmpty()
        val pdfPathArg = arguments?.getString("pdfPath").orEmpty()

        tipo = doc?.tipo ?: TipoDocumento.MENSAJE.name
        estado = doc?.estado ?: EstadoDocumento.RECIBIDO.name
        urgencia = doc?.urgencia ?: Urgencia.ORDINARIO.name
        clasificacion = doc?.clasificacion ?: Clasificacion.PUBLICO
        fecha = doc?.fecha ?: System.currentTimeMillis()

        fechaLimite = doc?.fechaLimite.orEmpty().ifBlank { formatFechaISO(System.currentTimeMillis()) }

        view.findViewById<TextView>(R.id.lbl_title).text =
            if (esEdicion) "Editar Documento" else "Nuevo Documento"

        inputFolio = view.findViewById(R.id.input_folio)
        inputAsunto = view.findViewById(R.id.input_asunto)
        inputDestinatario = view.findViewById(R.id.input_destinatario)

        spRemAreaLock = view.findViewById(R.id.sp_rem_area_lock)
        spRemSubLock = view.findViewById(R.id.sp_rem_sub_lock)
        spRemPerLock = view.findViewById(R.id.sp_rem_per_lock)
        inputReferencia = view.findViewById(R.id.input_referencia)

        val remitenteArea = TokenProvider.area(requireContext()).ifBlank { "—" }
        val remitenteSub = TokenProvider.subdireccion(requireContext()).ifBlank { "—" }
        val remitentePersona = TokenProvider.nombreCompleto(requireContext())
            .ifBlank { TokenProvider.usuario(requireContext()).orEmpty() }
            .ifBlank { "—" }

        spRemAreaLock.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf(remitenteArea)).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spRemSubLock.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf(remitenteSub)).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spRemPerLock.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf(remitentePersona)).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spRemAreaLock.isEnabled = false
        spRemSubLock.isEnabled = false
        spRemPerLock.isEnabled = false

        inputNumAnexos = view.findViewById(R.id.input_num_anexos)
        inputNotas = view.findViewById(R.id.input_notas)

        inputFolio.setText(doc?.folio ?: folioArg)
        inputAsunto.setText(doc?.asunto ?: asuntoArg)
        inputDestinatario.setText(doc?.destinatario ?: "")
        inputReferencia.setText(doc?.referencia ?: "")
        inputNumAnexos.setText(if (doc != null && doc.numAnexos > 0) doc.numAnexos.toString() else "")
        inputNotas.setText(doc?.notas ?: "")

        inputFechaLimite = view.findViewById(R.id.limite_fecha_text)
        inputHoraLimite = view.findViewById(R.id.input_hora_limite)

        inputFechaLimite.text = fechaLimite
        view.findViewById<View>(R.id.limite_fecha_card)?.setOnClickListener { mostrarPickerLimite() }

        configurarSpinnersHora(view, doc?.fechaLimiteHora.orEmpty())

        lblDestDept = view.findViewById(R.id.lbl_dest_dept)
        cardDestDept = view.findViewById(R.id.card_dest_dept)

        cascadaDest = CascadaPersonas(
            spArea = view.findViewById(R.id.sp_dest_area),
            spSub = view.findViewById(R.id.sp_dest_sub),
            spDept = view.findViewById(R.id.sp_dest_dept),
            spPer = view.findViewById(R.id.sp_dest_per),
            ayuda = view.findViewById(R.id.ayuda_dest),
            texto = inputDestinatario
        )
        prepararCascada(cascadaDest, doc?.idDestinatario, doc?.destinatario)

        val pdfPath = doc?.pdfPath?.takeIf { it.isNotBlank() } ?: pdfPathArg
        renderizarVistaPrevia(view, pdfPath)

        actualizarFecha()
        view.findViewById<View>(R.id.fecha_card).setOnClickListener {
            mostrarPicker()
        }

        val containerTipo = view.findViewById<Spinner>(R.id.spinner_tipo)
        val containerClasificacion = view.findViewById<Spinner>(R.id.spinner_clasificacion)
        val containerUrgencia = view.findViewById<LinearLayout>(R.id.container_urgencia)

        configurarSpinner(
            containerTipo,
            TipoDocumento.entries.map { it.label },
            TipoDocumento.entries.indexOfFirst { it.name == tipo }
        ) { pos -> tipo = TipoDocumento.entries[pos].name }

        val clasificaciones = Clasificacion.ETIQUETAS.entries.toList()
        configurarSpinner(
            containerClasificacion,
            clasificaciones.map { it.value },
            clasificaciones.indexOfFirst { it.key == clasificacion }.coerceAtLeast(0)
        ) { pos -> clasificacion = clasificaciones[pos].key }

        containerUrgencia.pintarOpciones(
            Urgencia.entries.map { OpcionItem(it.name, it.label, it.colorRes()) },
            urgencia
        ) { nuevo -> urgencia = nuevo }

        val btnGuardar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_guardar)
        btnGuardar.setOnClickListener {
            guardar(doc)
        }

        val permiso = if (esEdicion) Permisos.DOCUMENTO_EDITAR
        else Permisos.DOCUMENTO_CREAR
        if (!TokenProvider.puede(requireContext(), permiso)) {
            btnGuardar.isEnabled = false
            view.findViewById<TextView>(R.id.lbl_sin_permiso).isVisible = true
        }
    }

    private fun configurarSpinnersHora(view: View, horaInicial: String) {
        val spHora = view.findViewById<Spinner>(R.id.sp_hora_limite)
        val spMinuto = view.findViewById<Spinner>(R.id.sp_minuto_limite)
        val spAmPm = view.findViewById<Spinner>(R.id.sp_ampm_limite)

        val horas = (1..12).map { String.format("%02d", it) }
        val minutos = (0..55 step 5).map { String.format("%02d", it) }
        val ampm = listOf("AM", "PM")

        configurarSpinner(spHora, horas, 11) { actualizarHoraLimite(spHora, spMinuto, spAmPm) }
        configurarSpinner(spMinuto, minutos, 0) { actualizarHoraLimite(spHora, spMinuto, spAmPm) }
        configurarSpinner(spAmPm, ampm, 1) { actualizarHoraLimite(spHora, spMinuto, spAmPm) }

        if (horaInicial.isNotBlank()) {
            parsearEEstablecerHora(horaInicial, spHora, spMinuto, spAmPm, horas, minutos, ampm)
        } else {
            actualizarHoraLimite(spHora, spMinuto, spAmPm)
        }
    }

    private fun actualizarHoraLimite(spHora: Spinner, spMinuto: Spinner, spAmPm: Spinner) {
        val h = spHora.selectedItem?.toString() ?: "12"
        val m = spMinuto.selectedItem?.toString() ?: "00"
        val ap = spAmPm.selectedItem?.toString() ?: "PM"
        val horaTexto = "$h:$m $ap"
        inputHoraLimite.setText(horaTexto)
    }

    private fun parsearEEstablecerHora(
        texto: String,
        spHora: Spinner,
        spMinuto: Spinner,
        spAmPm: Spinner,
        horas: List<String>,
        minutos: List<String>,
        ampm: List<String>
    ) {
        try {
            val partes = texto.trim().split(" ", ":")
            if (partes.size >= 2) {
                val h = String.format("%02d", partes[0].toIntOrNull() ?: 12)
                val m = String.format("%02d", partes[1].toIntOrNull() ?: 0)
                val ap = if (partes.size >= 3) partes[2].uppercase() else "PM"

                val idxH = horas.indexOf(h).coerceAtLeast(0)
                val idxM = minutos.indexOf(m).coerceAtLeast(0)
                val idxAp = ampm.indexOf(ap).coerceAtLeast(0)

                spHora.setSelection(idxH, false)
                spMinuto.setSelection(idxM, false)
                spAmPm.setSelection(idxAp, false)
            }
        } catch (_: Exception) {
        }
    }

    private fun formatFechaISO(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private inner class CascadaPersonas(
        val spArea: Spinner,
        val spSub: Spinner,
        val spDept: Spinner,
        val spPer: Spinner,
        val ayuda: TextView,
        val texto: EditText
    ) {
        var areas: List<NivelOrgDto> = emptyList()
        var subs: List<NivelOrgDto> = emptyList()
        var depts: List<NivelOrgDto> = emptyList()
        var personas: List<PersonaDto> = emptyList()
        var areaSel: Long? = null
        var subSel: Long? = null
        var deptSel: Long? = null
        var personaSel: Long? = null
        var textoBase: String = ""
        var posArea = 0
        var posSub = 0
        var posDept = 0
        var posPer = 0
        var generacion = 0
        var escribiendo = false
    }

    private data class Preseleccion(val sub: Long?, val persona: Long, val texto: String)

    private fun prepararCascada(c: CascadaPersonas, id: Long?, texto: String?) {
        bloquearArea(c, "Cargando…")
        bloquearSub(c, "Primero seleccione el Área")
        bloquearDept(c, "Primero seleccione la Subdirección")
        bloquearPer(c, "Primero seleccione el Departamento")
        c.ayuda.text = ""

        c.spArea.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == c.posArea) return
                c.posArea = position
                alElegirArea(c, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        c.spSub.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == c.posSub) return
                c.posSub = position
                alElegirSub(c, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        c.spDept.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == c.posDept) return
                c.posDept = position
                alElegirDept(c, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        c.spPer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == c.posPer) return
                c.posPer = position
                alElegirPersona(c, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        c.texto.doAfterTextChanged {
            if (!c.escribiendo) limpiarRelacion(c)
        }

        viewLifecycleOwner.lifecycleScope.launch { preseleccionar(c, id, texto) }
    }

    private fun alElegirArea(c: CascadaPersonas, posicion: Int) {
        c.generacion++
        val area = c.areas.getOrNull(posicion - 1)
        c.areaSel = area?.id
        c.subSel = null
        c.deptSel = null
        c.personaSel = null
        c.subs = emptyList()
        c.depts = emptyList()
        c.personas = emptyList()
        escribirTexto(c, c.textoBase)
        if (area == null) {
            bloquearSub(c, "Primero seleccione el Área")
            bloquearDept(c, "Primero seleccione la Subdirección")
            bloquearPer(c, "Primero seleccione el Departamento")
            c.ayuda.text = ""
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { cargarSub(c, null) }
    }

    private fun alElegirSub(c: CascadaPersonas, posicion: Int) {
        c.generacion++
        val sub = c.subs.getOrNull(posicion - 1)
        c.subSel = sub?.id
        c.deptSel = null
        c.personaSel = null
        c.depts = emptyList()
        c.personas = emptyList()
        escribirTexto(c, c.textoBase)
        if (sub != null) {
            viewLifecycleOwner.lifecycleScope.launch { cargarDept(c, null) }
            return
        }
        if (c.areaSel != null && c.subs.isEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                cargarPersonas(c, dir = c.areaSel, sub = null, preId = null, preTexto = null)
            }
        } else {
            bloquearDept(c, "Primero seleccione la Subdirección")
            bloquearPer(c, "Primero seleccione el Departamento")
            c.ayuda.text = ""
        }
    }

    private fun alElegirDept(c: CascadaPersonas, posicion: Int) {
        c.generacion++
        if (posicion <= 0) {
            c.deptSel = null
        } else {
            val dept = c.depts.getOrNull(posicion - 1)
            c.deptSel = dept?.id
        }
        c.personaSel = null
        c.personas = emptyList()
        escribirTexto(c, c.textoBase)
        viewLifecycleOwner.lifecycleScope.launch {
            cargarPersonas(c, dir = c.areaSel, sub = c.subSel, preId = null, preTexto = null)
        }
    }

    private fun alElegirPersona(c: CascadaPersonas, posicion: Int) {
        val p = c.personas.getOrNull(posicion - 1)
        c.personaSel = p?.clave
        escribirTexto(c, if (p != null) p.nombreCompleto else c.textoBase)
        c.ayuda.text = ""
    }

    private fun vigente(c: CascadaPersonas, generacion: Int) = generacion == c.generacion

    private suspend fun preseleccionar(c: CascadaPersonas, id: Long?, texto: String?) {
        if (!isAdded) return
        c.textoBase = if (id == null) texto.orEmpty() else ""
        bloquearSub(c, "Primero seleccione el Área")
        bloquearDept(c, "Primero seleccione la Subdirección")
        bloquearPer(c, "Primero seleccione el Departamento")
        c.ayuda.text = ""

        val areas: List<NivelOrgDto>? = try {
            val api = ApiFactory.api(requireContext())
            val dgs = api.organigrama("direcciones-generales", soloPadre = "1")
            val dgId = dgs.firstOrNull()?.id ?: 0L
            if (dgId > 0L) api.organigrama("direcciones", direccionGeneral = dgId) else emptyList()
        } catch (e: Exception) {
            null
        }
        if (!isAdded) return
        if (areas == null || areas.isEmpty()) {
            c.areas = emptyList()
            bloquearArea(c, "Sin Áreas disponibles")
            c.ayuda.text = "No hay Áreas registradas en el servidor."
            if (id == null) escribirTexto(c, c.textoBase)
            return
        }
        c.areas = areas
        pintar(c.spArea, etiquetasAreas(areas), 0) { c.posArea = it }
        c.spArea.isEnabled = true
        if (id == null) {
            c.ayuda.text = "Seleccione el Área, la Subdirección y la Persona."
            escribirTexto(c, c.textoBase)
            return
        }

        val persona: PersonaDto? = try {
            ApiFactory.api(requireContext()).persona(id)
        } catch (e: Exception) {
            null
        }
        if (!isAdded) return
        val dirId = persona?.idDireccion
        if (persona == null || dirId == null || areas.none { it.id == dirId }) {
            forzar(c, id, persona?.nombreCompleto?.takeIf { it.isNotBlank() } ?: texto.orEmpty())
            return
        }
        c.areaSel = dirId
        pintar(c.spArea, etiquetasAreas(areas), areas.indexOfFirst { it.id == dirId } + 1) {
            c.posArea = it
        }
        cargarSub(
            c,
            Preseleccion(
                sub = persona.idSubdireccion,
                persona = id,
                texto = persona.nombreCompleto.takeIf { it.isNotBlank() } ?: texto.orEmpty()
            )
        )
    }

    private suspend fun cargarSub(c: CascadaPersonas, pre: Preseleccion?) {
        val dirId = c.areaSel ?: return
        val generacion = ++c.generacion
        bloquearSub(c, "Cargando…")
        bloquearDept(c, "Primero seleccione la Subdirección")
        bloquearPer(c, "Primero seleccione el Departamento")
        val subs: List<NivelOrgDto>? = try {
            ApiFactory.api(requireContext()).organigrama("subdirecciones", direccion = dirId)
        } catch (e: Exception) {
            null
        }
        if (!vigente(c, generacion) || !isAdded) return
        if (subs == null) {
            bloquearSub(c, "No se pudo consultar")
            bloquearDept(c, "Primero seleccione la Subdirección")
            bloquearPer(c, "Primero seleccione el Departamento")
            c.ayuda.text = "No se pudo consultar la estructura organizacional."
            return
        }
        c.subs = subs
        val etiquetas = listOf("Seleccionar Subdirección") + subs.map { it.nombre }
        if (subs.isNotEmpty()) {
            val idx = pre?.sub?.let { s -> subs.indexOfFirst { it.id == s } } ?: -1
            pintar(c.spSub, etiquetas, idx + 1) { c.posSub = it }
            c.spSub.isEnabled = true
            if (idx >= 0) {
                c.subSel = subs[idx].id
                cargarDept(c, pre)
            } else {
                c.subSel = null
                c.ayuda.text = "Ahora seleccione la Subdirección."
            }
            return
        }
        c.subSel = null
        pintar(c.spSub, listOf("Sin Subdirecciones en esta Área"), 0) { c.posSub = 0 }
        c.spSub.isEnabled = false
        cargarPersonas(c, dirId, null, pre?.persona, pre?.texto)
    }

    private suspend fun cargarDept(c: CascadaPersonas, pre: Preseleccion?) {
        val subId = c.subSel ?: return
        val generacion = ++c.generacion
        bloquearDept(c, "Cargando…")
        bloquearPer(c, "Primero seleccione el Departamento")
        val depts: List<NivelOrgDto>? = try {
            ApiFactory.api(requireContext()).organigrama("departamentos", subdireccion = subId)
        } catch (e: Exception) {
            null
        }
        if (!vigente(c, generacion) || !isAdded) return
        if (depts == null) {
            bloquearDept(c, "No se pudo consultar")
            c.ayuda.text = "No se pudo consultar los departamentos."
            return
        }
        c.depts = depts

        if (depts.isNotEmpty()) {
            lblDestDept.isVisible = true
            cardDestDept.isVisible = true
            val etiquetas = listOf("Todos los departamentos") + depts.map { it.nombre }
            pintar(c.spDept, etiquetas, 0) { c.posDept = it }
            c.spDept.isEnabled = true
            c.deptSel = null
            c.ayuda.text = "Seleccione la Persona o elija un Departamento específico."

            cargarPersonas(c, c.areaSel, subId, pre?.persona, pre?.texto)
            return
        }

        c.deptSel = null
        lblDestDept.isVisible = false
        cardDestDept.isVisible = false
        c.spDept.isEnabled = false
        cargarPersonas(c, c.areaSel, subId, pre?.persona, pre?.texto)
    }

    private suspend fun cargarPersonas(
        c: CascadaPersonas,
        dir: Long?,
        sub: Long?,
        preId: Long?,
        preTexto: String?
    ) {
        if (!isAdded) return
        val generacion = ++c.generacion
        bloquearPer(c, "Cargando…")
        val filas: List<PersonaDto>? = try {
            ApiFactory.api(requireContext())
                .listarPersonas(direccion = dir, subdireccion = sub, limite = 500)
        } catch (e: Exception) {
            null
        }
        if (!vigente(c, generacion) || !isAdded) return
        if (filas == null) {
            bloquearPer(c, "No se pudo consultar")
            c.ayuda.text = "No se pudo consultar el catálogo de personas."
            return
        }
        val hayPre = preId != null
        if (filas.isEmpty() && !hayPre) {
            bloquearPer(c, "Sin personas en esta ubicación")
            c.ayuda.text = "No hay personas registradas en esta ubicación."
            return
        }
        c.personas = filas
        val etiquetas = mutableListOf("Seleccionar Persona")
        filas.forEach { etiquetas.add(it.nombreCompleto) }
        var pos = 0
        var inyectada = false
        if (hayPre) {
            val idx = filas.indexOfFirst { it.clave == preId }
            if (idx >= 0) {
                pos = idx + 1
            } else if (!preTexto.isNullOrBlank()) {
                etiquetas.add(preTexto)
                pos = etiquetas.size - 1
                inyectada = true
            }
        }
        pintar(c.spPer, etiquetas, pos) { c.posPer = it }
        c.spPer.isEnabled = true
        if (pos > 0) {
            c.personaSel = if (inyectada) preId else c.personas.getOrNull(pos - 1)?.clave
            escribirTexto(c, if (inyectada) preTexto.orEmpty() else c.personas[pos - 1].nombreCompleto)
            c.ayuda.text = if (inyectada) "Persona no asignada a la ubicación actual." else ""
        } else {
            c.personaSel = null
            c.ayuda.text = "Ahora seleccione la Persona."
        }
    }

    private fun escribirTexto(c: CascadaPersonas, valor: String) {
        c.escribiendo = true
        c.texto.setText(valor)
        c.escribiendo = false
    }

    private fun limpiarRelacion(c: CascadaPersonas) {
        c.personaSel = null
        c.posArea = 0
        c.posSub = 0
        c.posDept = 0
        c.posPer = 0
        if (c.spArea.adapter != null && c.spArea.adapter.count > 0) c.spArea.setSelection(0)
        bloquearSub(c, "Primero seleccione el Área")
        bloquearDept(c, "Primero seleccione la Subdirección")
        bloquearPer(c, "Primero seleccione el Departamento")
        c.ayuda.text = ""
    }

    private fun forzar(c: CascadaPersonas, id: Long, nombre: String) {
        c.personaSel = id
        pintar(c.spArea, listOf("— Ubicación no disponible —"), 0) { c.posArea = 0 }
        bloquearSub(c, "—")
        bloquearDept(c, "—")
        pintar(c.spPer, listOf(nombre), 0) { c.posPer = 0 }
        c.spArea.isEnabled = false
        c.spSub.isEnabled = false
        c.spDept.isEnabled = false
        c.spPer.isEnabled = false
        c.ayuda.text = "Servidor público sin ubicación vigente registrada en el sistema."
        escribirTexto(c, nombre)
    }

    private fun bloquearArea(c: CascadaPersonas, msj: String) {
        c.areaSel = null
        c.areas = emptyList()
        pintar(c.spArea, listOf(msj), 0) { c.posArea = 0 }
        c.spArea.isEnabled = false
    }

    private fun bloquearSub(c: CascadaPersonas, msj: String) {
        c.subSel = null
        c.subs = emptyList()
        pintar(c.spSub, listOf(msj), 0) { c.posSub = 0 }
        c.spSub.isEnabled = false
    }

    private fun bloquearDept(c: CascadaPersonas, msj: String) {
        c.deptSel = null
        c.depts = emptyList()
        lblDestDept.isVisible = false
        cardDestDept.isVisible = false
        pintar(c.spDept, listOf(msj), 0) { c.posDept = 0 }
        c.spDept.isEnabled = false
    }

    private fun bloquearPer(c: CascadaPersonas, msj: String) {
        c.personaSel = null
        c.personas = emptyList()
        pintar(c.spPer, listOf(msj), 0) { c.posPer = 0 }
        c.spPer.isEnabled = false
    }

    private fun etiquetasAreas(list: List<NivelOrgDto>): List<String> {
        val ret = mutableListOf("Seleccionar Área")
        list.forEach { ret.add(it.nombre) }
        return ret
    }

    private fun pintar(sp: Spinner, lista: List<String>, selec: Int, alPintar: (Int) -> Unit) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lista)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        sp.setSelection(selec, false)
        alPintar(selec)
    }

    private fun configurarSpinner(
        spinner: Spinner,
        opciones: List<String>,
        posicionInicial: Int,
        alSeleccionar: (Int) -> Unit
    ) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opciones)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(posicionInicial.coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                alSeleccionar(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderizarVistaPrevia(root: View, pdfPath: String) {
        val card = root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_preview)
        val btnVerPdf = root.findViewById<View>(R.id.btn_ver_pdf_completo)
        val containerPaginas = root.findViewById<LinearLayout>(R.id.container_paginas_preview)

        if (pdfPath.isBlank()) {
            card.visibility = View.GONE
            return
        }
        val file = File(pdfPath)
        if (!file.exists()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        btnVerPdf?.setOnClickListener {
            findNavController().navigate(
                R.id.pdf_viewer,
                Bundle().apply { putString("pdfPath", pdfPath) }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val paginas = withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val listBitmaps = mutableListOf<Pair<Int, Bitmap>>()

                    val density = resources.displayMetrics.density
                    val count = renderer.pageCount

                    for (i in 0 until count) {
                        val page = renderer.openPage(i)
                        val w = (page.width * density * 0.75f).toInt().coerceAtLeast(1)
                        val h = (page.height * density * 0.75f).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        listBitmaps.add(Pair(i + 1, bmp))
                    }

                    renderer.close()
                    pfd.close()
                    listBitmaps
                } catch (e: Exception) {
                    Log.e("FormularioFragment", "Error renderizando páginas del PDF $pdfPath", e)
                    emptyList()
                }
            }

            containerPaginas?.removeAllViews()
            if (paginas.isNotEmpty()) {
                val ctx = requireContext()
                for ((numPag, bitmap) in paginas) {
                    val pageContainer = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = com.gn.gestion.util.dp(ctx, 10) }
                    }

                    val tvLabel = TextView(ctx).apply {
                        text = "Página $numPag"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, com.gn.gestion.util.dp(ctx, 6), 0, com.gn.gestion.util.dp(ctx, 10))
                    }

                    val imgView = ImageView(ctx).apply {
                        setImageBitmap(bitmap)
                        setBackgroundColor(Color.WHITE)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Página $numPag"
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    pageContainer.addView(imgView)
                    pageContainer.addView(tvLabel)
                    containerPaginas?.addView(pageContainer)
                }
            } else {
                card.visibility = View.GONE
            }
        }
    }

    private fun actualizarFecha() {
        val v = view ?: return
        v.findViewById<TextView>(R.id.fecha_text).text = formatFecha(fecha)
    }

    private fun mostrarPicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = fecha }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val c = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                fecha = c.timeInMillis
                actualizarFecha()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun mostrarPickerLimite() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                fechaLimite = String.format("%04d-%02d-%02d", year, month + 1, day)
                inputFechaLimite.text = fechaLimite
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun guardar(originalDoc: Documento?) {
        val folio = inputFolio.text.toString().trim()
        val asunto = inputAsunto.text.toString().trim()
        val destText = inputDestinatario.text.toString().trim()
        val ref = inputReferencia.text.toString().trim()
        val numAnexos = inputNumAnexos.text.toString().trim().toIntOrNull() ?: 0
        val notas = inputNotas.text.toString().trim()
        val horaLimite = inputHoraLimite.text.toString().trim()

        if (folio.isBlank()) {
            inputFolio.error = "Indica el folio"
            inputFolio.requestFocus()
            return
        }
        if (asunto.isBlank()) {
            inputAsunto.error = "Indica el asunto"
            inputAsunto.requestFocus()
            return
        }

        val pdfArg = arguments?.getString("pdfPath").orEmpty()
        val pdfPathFinal = originalDoc?.pdfPath?.takeIf { it.isNotBlank() } ?: pdfArg

        val idDestinoVal = cascadaDest.personaSel
        val areaDestinoVal = cascadaDest.areas.firstOrNull { it.id == cascadaDest.areaSel }?.nombre.orEmpty()

        val baseDoc = originalDoc ?: nuevoDocumento(tipo, estado, urgencia, fecha)
        val doc = baseDoc.copy(
            folio = folio,
            asunto = asunto,
            tipo = tipo,
            destinatario = destText,
            idDestinatario = idDestinoVal,
            areaDestino = areaDestinoVal,
            referencia = ref,
            numAnexos = numAnexos,
            notas = notas,
            estado = estado,
            urgencia = urgencia,
            clasificacion = clasificacion,
            fecha = fecha,
            fechaDocumento = formatFechaISO(fecha),
            fechaLimite = fechaLimite,
            fechaLimiteHora = horaLimite,
            pdfPath = pdfPathFinal
        )

        lifecycleScope.launch {
            try {
                Log.i(
                    TAG_GUARDAR,
                    "Guardando local folio=${doc.folio} horaLocal=${doc.fechaLimiteHora} " +
                            "horaServidor=${Hora.a24(doc.fechaLimiteHora)}"
                )
                val nuevoId = vm.guardar(doc)

                if (originalDoc == null) {
                    val persona = cascadaDest.personas.firstOrNull {
                        it.clave == cascadaDest.personaSel
                    }
                    val uid = persona?.idUsuario
                    val destino = when {
                        persona?.idDepartamento != null ->
                            "departamento" to persona.idDepartamento!!
                        persona?.idSubdireccion != null ->
                            "subdireccion" to persona.idSubdireccion!!
                        persona?.idDireccion != null ->
                            "direccion" to persona.idDireccion!!
                        uid != null -> "usuario" to uid
                        else -> null
                    }
                    if (destino != null) {
                        Log.i(
                            TAG_GUARDAR,
                            "Alta local id=$nuevoId; turnando al guardar a " +
                                    "${destino.first}/${destino.second} usuario=$uid"
                        )
                        vm.turnarAlGuardar(nuevoId, destino.first, destino.second, uid)
                    } else {
                        Log.i(TAG_GUARDAR, "Alta local id=$nuevoId sin persona destino; no se turna")
                    }
                }
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e(TAG_GUARDAR, "Error al guardar el documento", e)
                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Error al guardar")
                    .setMessage(e.mensajeServidor())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}