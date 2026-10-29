package com.pet.measure.ar

import android.app.Activity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlin.math.abs

sealed interface ArStartResult {
    data class Ready(val session: Session) : ArStartResult
    /** ARCore is being installed by the Play Store; try again on the next resume. */
    data object Installing : ArStartResult
    data object NeedsInstall : ArStartResult
    data object Unsupported : ArStartResult
    data class Failed(val message: String) : ArStartResult
}

/** Creates and owns the single ARCore session for the activity. */
class ArSessionHolder {
    var session: Session? = null
        private set
    private var installRequested = false
    val measureState = MeasureSessionState()

    fun start(activity: Activity): ArStartResult {
        session?.let { return ArStartResult.Ready(it) }
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return ArStartResult.Installing
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
                else -> Unit
            }
            val s = Session(activity)
            chooseCameraConfig(s)
            s.configure(
                Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) depthMode = Config.DepthMode.AUTOMATIC
                },
            )
            session = s
            ArStartResult.Ready(s)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            ArStartResult.NeedsInstall
        } catch (e: UnavailableDeviceNotCompatibleException) {
            ArStartResult.Unsupported
        } catch (e: Exception) {
            ArStartResult.Failed(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    /** Prefers a CPU image around 1280 px wide: enough detail for the jack, still fast to scan. */
    private fun chooseCameraConfig(s: Session) {
        val filter = CameraConfigFilter(s).setFacingDirection(CameraConfig.FacingDirection.BACK)
        val best = s.getSupportedCameraConfigs(filter)
            .filter { it.imageSize.width <= 1920 }
            .minByOrNull { abs(it.imageSize.width - 1280) }
        if (best != null) s.cameraConfig = best
    }

    fun close() {
        session?.close()
        session = null
    }
}
