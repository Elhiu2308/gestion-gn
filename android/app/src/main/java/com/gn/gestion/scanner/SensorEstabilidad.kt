package com.gn.gestion.scanner;

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.ArrayDeque
import kotlin.math.abs

class SensorEstabilidad(context: Context) {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometro = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val giroscopio = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val ventana = 20
    private val gyros = ArrayDeque<Float>()
    private val distanciasAccel = ArrayDeque<Float>()

    @Volatile
    var estable = false
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    val mag = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    gyros.addLast(mag)
                    if (gyros.size > ventana) gyros.removeFirst()
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val mag = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    distanciasAccel.addLast(abs(mag - 9.81f))
                    if (distanciasAccel.size > ventana) distanciasAccel.removeFirst()
                }
            }
            if (gyros.size >= ventana && distanciasAccel.size >= ventana) {
                val gVar = varianza(gyros)
                val gMed = media(gyros)
                val aVar = varianza(distanciasAccel)
                estable = gMed < 0.6f && gVar < 0.03f && aVar < 0.05f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun registrar() {
        accelerometro?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        giroscopio?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun liberar() {
        sm.unregisterListener(listener)
        gyros.clear()
        distanciasAccel.clear()
        estable = false
    }

    private fun media(list: ArrayDeque<Float>): Float =
        if (list.isEmpty()) 0f else list.sum() / list.size

    private fun varianza(list: ArrayDeque<Float>): Float {
        if (list.isEmpty()) return 0f
        val m = media(list)
        var s = 0f
        for (v in list) s += (v - m) * (v - m)
        return s / list.size
    }
}