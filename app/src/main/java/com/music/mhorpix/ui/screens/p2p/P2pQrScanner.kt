package com.music.mhorpix.ui.screens.p2p

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * A full-screen QR scanner. Uses CameraX for the preview + frame analysis and
 * ZXing's [MultiFormatReader] for decoding. Asks for CAMERA permission if not
 * granted. Calls [onCode] with the raw QR payload and [onDismiss] to close.
 */
@Composable
fun P2pQrScannerScreen(
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(Color.Black) }) {
        if (hasPermission) {
            val executor = remember { Executors.newSingleThreadExecutor() }
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx)
                    provider.addListener({
                        val cameraProvider = provider.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyser ->
                                analyser.setAnalyzer(executor) { image ->
                                    scan(image, onCode)
                                }
                            }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Scan frame overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.3f))
                    },
            )
        } else {
            Text(
                "Camera permission needed to scan QR codes.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close", tint = Color.White)
        }
    }
}

/** Feeds a single CameraX frame to ZXing. */
private fun scan(image: ImageProxy, onCode: (String) -> Unit) {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        image.width,
        image.height,
        0, 0,
        image.width, image.height,
        false,
    )
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    try {
        val result = MultiFormatReader().decode(bitmap)
        onCode(result.text)
    } catch (_: NotFoundException) {
        // No QR in this frame — try the next.
    } finally {
        image.close()
    }
}
