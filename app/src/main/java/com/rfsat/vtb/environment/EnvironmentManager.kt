package com.rfsat.vtb.environment

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.rfsat.vtb.ballistics.Atmosphere
import com.rfsat.vtb.log.Logger

/**
 * Holds the CURRENT range conditions and acquires them from the phone's
 * environmental sensors (v17.0), replacing the standard-atmosphere
 * assumption at analysis time.
 *
 * Sensor availability is very device-dependent: nearly every modern
 * flagship has a barometer (TYPE_PRESSURE — the S24 does), while ambient
 * temperature and humidity sensors are rare on phones (common on Kestrel
 * meters, hence [KestrelProvider]). Whatever isn't measurable stays at the
 * ICAO default and is labelled so.
 *
 * PRESSURE MAPPING: the barometer reads STATION pressure — the actual
 * pressure where the shooter stands. Feeding it to [Atmosphere] as
 * seaLevelPressurePa with altitudeM = 0 makes the density computation use
 * the measured local pressure directly, which is exactly right: altitude
 * only ever affects ballistics through pressure, and we measured pressure.
 * The altitude shown in the status line is informational only (derived
 * back from the pressure via the standard atmosphere).
 */
object EnvironmentManager {

    private const val TAG = "EnvironmentManager"
    private const val SENSOR_TIMEOUT_MS = 2500L

    /** Where each value came from, for the status line and the log. */
    data class Reading(
        val atmosphere: Atmosphere,
        val temperatureSource: String,
        val pressureSource: String,
        val humiditySource: String,
        val informationalAltitudeM: Double?
    )

    @Volatile
    var current: Reading = Reading(
        Atmosphere(), "default", "default", "default", null
    )
        private set

    fun setFromKestrel(temperatureC: Double?, pressurePa: Double?, humidityFrac: Double?) {
        val prev = current
        current = Reading(
            Atmosphere(
                seaLevelPressurePa = pressurePa ?: prev.atmosphere.seaLevelPressurePa,
                temperatureC = temperatureC ?: prev.atmosphere.temperatureC,
                altitudeM = 0.0,
                relativeHumidity = humidityFrac ?: prev.atmosphere.relativeHumidity
            ),
            temperatureSource = if (temperatureC != null) "Kestrel" else prev.temperatureSource,
            pressureSource = if (pressurePa != null) "Kestrel" else prev.pressureSource,
            humiditySource = if (humidityFrac != null) "Kestrel" else prev.humiditySource,
            informationalAltitudeM = pressurePa?.let {
                SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, (it / 100.0).toFloat()).toDouble()
            } ?: prev.informationalAltitudeM
        )
        Logger.i(TAG, "Environment from Kestrel: ${describe()}")
    }

    /**
     * One-shot read of whatever environmental sensors the phone has.
     * Registers listeners, takes the first value of each, unregisters after
     * all report or [SENSOR_TIMEOUT_MS]. Calls [onDone] on the main thread.
     */
    fun refreshFromPhoneSensors(context: Context, onDone: (Reading) -> Unit = {}) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val temp = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        val humidity = sm.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        Logger.i(TAG, "Phone sensors: pressure=${pressure != null} ambientTemp=${temp != null} humidity=${humidity != null}")

        if (pressure == null && temp == null && humidity == null) {
            onDone(current)
            return
        }

        var pHpa: Float? = null
        var tC: Float? = null
        var hPct: Float? = null
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_PRESSURE -> if (pHpa == null) pHpa = e.values[0]
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> if (tC == null) tC = e.values[0]
                    Sensor.TYPE_RELATIVE_HUMIDITY -> if (hPct == null) hPct = e.values[0]
                }
                val allIn = (pressure == null || pHpa != null) &&
                    (temp == null || tC != null) && (humidity == null || hPct != null)
                if (allIn) finish()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            fun finish() {
                if (finished) return
                finished = true
                sm.unregisterListener(this)
                val prev = current
                current = Reading(
                    Atmosphere(
                        seaLevelPressurePa = pHpa?.let { it * 100.0 } ?: prev.atmosphere.seaLevelPressurePa,
                        temperatureC = tC?.toDouble() ?: prev.atmosphere.temperatureC,
                        altitudeM = 0.0, // measured station pressure carries the altitude effect
                        relativeHumidity = hPct?.let { it / 100.0 } ?: prev.atmosphere.relativeHumidity
                    ),
                    temperatureSource = if (tC != null) "phone" else prev.temperatureSource,
                    pressureSource = if (pHpa != null) "phone" else prev.pressureSource,
                    humiditySource = if (hPct != null) "phone" else prev.humiditySource,
                    informationalAltitudeM = pHpa?.let {
                        SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).toDouble()
                    } ?: prev.informationalAltitudeM
                )
                Logger.i(TAG, "Environment from phone sensors: ${describe()}")
                handler.post { onDone(current) }
            }
        }

        pressure?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        temp?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        humidity?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        handler.postDelayed({ listener.finish() }, SENSOR_TIMEOUT_MS)
    }

    fun describe(): String {
        val r = current
        val a = r.atmosphere
        val alt = r.informationalAltitudeM?.let { " ~%.0f m ASL".format(it) } ?: ""
        return "%.1f°C (%s) · %.0f hPa (%s) · %.0f%% RH (%s)%s".format(
            a.temperatureC, r.temperatureSource,
            a.seaLevelPressurePa / 100.0, r.pressureSource,
            a.relativeHumidity * 100.0, r.humiditySource, alt
        )
    }
}
