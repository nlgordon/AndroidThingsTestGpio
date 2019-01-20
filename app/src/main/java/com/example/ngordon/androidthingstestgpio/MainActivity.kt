package com.example.ngordon.androidthingstestgpio

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import java.io.IOException
import android.util.Log
import com.google.android.things.contrib.driver.ssd1306.Ssd1306
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.tm1637.NumericDisplay
import com.google.android.things.contrib.driver.apa102.Apa102

import android.graphics.Color
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.cap12xx.Cap12xx
import com.google.android.things.contrib.driver.cap12xx.Cap12xxInputDriver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.DynamicSensorCallback
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.google.android.things.contrib.driver.pwmspeaker.Speaker

private val TAG = MainActivity::class.java.simpleName
private val gpioButtonPinName = "BUS NAME"
private val I2C_BUS = "BUS NAME"
private val GPIO_FOR_DATA = "BUS NAME"
private val GPIO_FOR_CLOCK = "BUS NAME"
// LED configuration.
private val NUM_LEDS = 7
private val LED_BRIGHTNESS = 5 // 0 ... 31
private val LED_MODE = Apa102.Mode.BGR
private val PWM_BUS = "BUS NAME"
private val SPI_BUS = "BUS NAME"

class MainActivity : Activity() {
    private lateinit var mButton: Button
    private lateinit var mInputDriver: Cap12xxInputDriver
    private lateinit var mSegmentDisplay: AlphanumericDisplay
    private lateinit var mScreen: Ssd1306
    private lateinit var mNumericSegmentDisplay: NumericDisplay
    private lateinit var mLedstrip: Apa102
    private lateinit var mLedColors: IntArray
    private lateinit var mSensorManager: SensorManager
    private val mDynamicSensorCallback = object : DynamicSensorCallback() {
        override fun onDynamicSensorConnected(sensor: Sensor) {
            if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
                Log.i(TAG, "Accelerometer sensor connected")
                mAccelerometerListener = AccelerometerListener()
                mSensorManager.registerListener(mAccelerometerListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected")
                mSensorEventListener = TemperaturePressureEventListener()
                mSensorManager.registerListener(mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }
    private lateinit var mAccelerometerListener: AccelerometerListener
    private lateinit var mSensorEventListener: TemperaturePressureEventListener
    private lateinit var mLocationManager: LocationManager
    private val mLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.v(TAG, "Location update: " + location)
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }
    private lateinit var mSpeaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupButton()
        setupCapacitiveTouchButtons()
        setupAlphanumericDisplay()
        setupNumericDisplay()
        setupOledDisplay()
        setupLedStrip()
        val hsv = floatArrayOf(1f, 1f, 1f)
        for (i in mLedColors.indices) { // Assigns gradient colors.
            hsv[0] = i * 360f / mLedColors.size
            mLedColors[i] = Color.HSVToColor(0, hsv)
        }
        try {
            mLedstrip.write(mLedColors)
        } catch (e: IOException) {
            Log.e(TAG, "Error setting LED colors", e)
        }
        startAccelerometerRequest()
        startLocationRequest()
        startTemperaturePressureRequest()
        setupSpeaker()
        try {
            mSpeaker.play(/* G4 */391.995)
        } catch (e: IOException) {
            Log.e(TAG, "Error playing note", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyButton()
        destroyCapacitiveTouchButtons()
        destroyAlphanumericDisplay()
        destroyNumericDisplay()
        destroyOledDisplay()
        destroyLedStrip()
        stopAccelerometerRequest()
        stopLocationRequest()
        stopTemperaturePressureRequest()
        destroySpeaker()
    }

    private fun setupButton() {
        try {
            mButton = Button(gpioButtonPinName,
                    // high signal indicates the button is pressed
                    // use with a pull-down resistor
                    Button.LogicState.PRESSED_WHEN_HIGH
            )
            mButton.setOnButtonEventListener(object : Button.OnButtonEventListener {
                override fun onButtonEvent(button: Button, pressed: Boolean) {
                    // do something awesome
                }
            })
        } catch (e: IOException) {
            // couldn't configure the button...
        }

    }

    private fun destroyButton() {
        Log.i(TAG, "Closing button")
        try {
            mButton.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing button", e)
        }
    }

    private fun setupCapacitiveTouchButtons() {
        // Set input key codes
        val keyCodes = intArrayOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8)

        try {
            mInputDriver = Cap12xxInputDriver(this,
                    I2C_BUS, null,
                    Cap12xx.Configuration.CAP1208,
                    keyCodes)

            // Disable repeated events
            mInputDriver.setRepeatRate(Cap12xx.REPEAT_DISABLE)
            // Block touches above 4 unique inputs
            mInputDriver.setMultitouchInputMax(4)

            mInputDriver.register()

        } catch (e: IOException) {
            Log.w(TAG, "Unable to open driver connection", e)
        }

    }

    private fun destroyCapacitiveTouchButtons() {
        mInputDriver.unregister()

        try {
            mInputDriver.close()
        } catch (e: IOException) {
            Log.w(TAG, "Unable to close touch driver", e)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Handle key events from captouch inputs
        when (keyCode) {
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8 -> {
                Log.d(TAG, "Captouch key released: " + event.keyCode)
                return true
            }
            else -> {
                Log.d(TAG, "Unknown key released: " + keyCode)
                return super.onKeyUp(keyCode, event)
            }
        }
    }

    private fun setupAlphanumericDisplay() {
        try {
            mSegmentDisplay = AlphanumericDisplay(I2C_BUS)
            mSegmentDisplay.setBrightness(1.0f)
            mSegmentDisplay.setEnabled(true)
            mSegmentDisplay.clear()
            mSegmentDisplay.display("ABCD")
        } catch (e: IOException) {
            Log.e(TAG, "Error configuring display", e)
        }

    }

    private fun destroyAlphanumericDisplay() {
        Log.i(TAG, "Closing display")
        try {
            mSegmentDisplay.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing display", e)
        }
    }

    private fun setupNumericDisplay() {
        Log.i(TAG, "Starting SegmentDisplayActivity")
        try {
            mNumericSegmentDisplay = NumericDisplay(GPIO_FOR_DATA, GPIO_FOR_CLOCK)
            mNumericSegmentDisplay.setBrightness(1.0f)
            mNumericSegmentDisplay.setColonEnabled(true)
            mNumericSegmentDisplay.display("2342")
        } catch (e: IOException) {
            Log.e(TAG, "Error configuring display", e)
        }

    }

    private fun destroyNumericDisplay() {
        Log.i(TAG, "Closing display")
        try {
            mNumericSegmentDisplay.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing display", e)
        }
    }

    private fun setupOledDisplay() {
        try {
            mScreen = Ssd1306(I2C_BUS)
        } catch (e: IOException) {
            Log.e(TAG, "Error while opening screen", e)
        }

        Log.d(TAG, "OLED screen activity created")
    }

    private fun destroyOledDisplay() {
        try {
            mScreen.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing SSD1306", e)
        }
    }

    private fun setupLedStrip() {
        mLedColors = IntArray(NUM_LEDS)
        try {
            Log.d(TAG, "Initializing LED strip")
            mLedstrip = Apa102(SPI_BUS, LED_MODE)
            mLedstrip.setBrightness(LED_BRIGHTNESS)
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing LED strip", e)
        }

    }

    private fun destroyLedStrip() {
        try {
            mLedstrip.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception closing LED strip", e)
        }
    }

    private fun startAccelerometerRequest() {
        this.startService(Intent(this, AccelerometerService::class.java))
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback)
    }

    private fun stopAccelerometerRequest() {
        this.stopService(Intent(this, AccelerometerService::class.java))
        mSensorManager.unregisterListener(mAccelerometerListener)
    }

    private fun startLocationRequest() {
        this.startService(Intent(this, GpsService::class.java))

        mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // We need permission to get location updates
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.d(TAG, "No permission")
            return
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                0, 0f, mLocationListener)
    }

    private fun stopLocationRequest() {
        this.stopService(Intent(this, GpsService::class.java))
        mLocationManager.removeUpdates(mLocationListener)
    }

    private fun startTemperaturePressureRequest() {
        this.startService(Intent(this, TemperaturePressureService::class.java))
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback)
    }

    private fun stopTemperaturePressureRequest() {
        this.stopService(Intent(this, TemperaturePressureService::class.java))
        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback)
        mSensorManager.unregisterListener(mSensorEventListener)
    }

    private fun setupSpeaker() {
        try {
            mSpeaker = Speaker(PWM_BUS)
            mSpeaker.stop() // in case the PWM pin was enabled already
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing speaker")
        }

    }

    private fun destroySpeaker() {
        try {
            mSpeaker.stop()
            mSpeaker.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing speaker", e)
        }
    }

    private inner class AccelerometerListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.i(TAG, "Accelerometer event: " +
                    event.values[0] + ", " + event.values[1] + ", " + event.values[2])
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.i(TAG, "Accelerometer accuracy changed: " + accuracy)
        }
    }

    private inner class TemperaturePressureEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.i(TAG, "sensor changed: " + event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.i(TAG, "sensor accuracy changed: " + accuracy)
        }
    }

}
