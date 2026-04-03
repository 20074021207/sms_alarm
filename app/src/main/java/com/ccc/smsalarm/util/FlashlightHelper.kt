package com.ccc.smsalarm.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashlightHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var flashJob: Job? = null
    private var isOn = false

    private val cameraId: String? by lazy {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun startFlashlight(scope: CoroutineScope) {
        stopFlashlight()
        if (cameraId == null) return

        flashJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                isOn = !isOn
                try {
                    cameraManager.setTorchMode(cameraId!!, isOn)
                } catch (_: Exception) {
                    break
                }
                delay(500)
            }
        }
    }

    fun stopFlashlight() {
        flashJob?.cancel()
        flashJob = null
        isOn = false
        try {
            cameraId?.let { cameraManager.setTorchMode(it, false) }
        } catch (_: Exception) {
        }
    }
}
