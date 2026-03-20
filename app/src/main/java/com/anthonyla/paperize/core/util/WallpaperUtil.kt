package com.anthonyla.paperize.core.util
import com.anthonyla.paperize.core.constants.Constants

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.HardwareRenderer
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.ui.util.fastRoundToInt
import androidx.exifinterface.media.ExifInterface
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.WallpaperMediaType

/**
 * Wallpaper utility functions for bitmap processing and effects
 */

private const val TAG = "WallpaperUtil"

/**
 * Get the dimensions of the image from the URI
 */
fun Uri.getImageDimensions(context: Context): Size? {
    try {
        context.contentResolver.openInputStream(this)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (width > 0 && height > 0) {
                return Size(width, height)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error reading EXIF dimensions for $this, falling back: $e")
    }

    try {
        context.contentResolver.openInputStream(this)?.use { inputStream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return Size(options.outWidth, options.outHeight)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting image dimensions with BitmapFactory for $this: $e")
        return null
    }

    return null
}

/**
 * Get EXIF orientation from URI
 */
fun Uri.getExifOrientation(context: Context): Int {
    return try {
        context.contentResolver.openInputStream(this)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        } ?: ExifInterface.ORIENTATION_UNDEFINED
    } catch (e: Exception) {
        Log.w(TAG, "Error reading EXIF orientation: $e")
        ExifInterface.ORIENTATION_UNDEFINED
    }
}

/**
 * Create transformation matrix for EXIF orientation
 */
fun getExifTransformationMatrix(orientation: Int, width: Int, height: Int): Matrix {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.setScale(-1f, 1f)
            matrix.postTranslate(width.toFloat(), 0f)
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.setRotate(180f)
            matrix.postTranslate(width.toFloat(), height.toFloat())
        }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setScale(1f, -1f)
            matrix.postTranslate(0f, height.toFloat())
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.setRotate(90f)
            matrix.postTranslate(height.toFloat(), 0f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.setRotate(-90f)
            matrix.postTranslate(0f, width.toFloat())
        }
    }
    return matrix
}

/**
 * Calculate the inSampleSize for the image
 */
fun calculateInSampleSize(imageSize: Size, width: Int, height: Int): Int {
    if (imageSize.width == 0 || imageSize.height == 0) return 1
    if (width == 0 || height == 0) return 1

    if (imageSize.width > width || imageSize.height > height) {
        val heightRatio = (imageSize.height.toFloat() / height.toFloat()).fastRoundToInt()
        val widthRatio = (imageSize.width.toFloat() / width.toFloat()).fastRoundToInt()
        return (if (heightRatio < widthRatio) heightRatio else widthRatio).coerceAtLeast(1)
    }
    return 1
}

/**
 * Get device screen size
 */
fun getDeviceScreenSize(context: Context): Size {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        Size(metrics.bounds.width(), metrics.bounds.height())
    } else {
        val display = windowManager.defaultDisplay
        val point = android.graphics.Point()
        display.getRealSize(point)
        Size(point.x, point.y)
    }
}

/**
 * Retrieve a bitmap from a URI
 */
fun retrieveBitmap(
    context: Context,
    wallpaperUri: Uri,
    width: Int,
    height: Int,
    scaling: ScalingType = ScalingType.FIT
): Bitmap? {
    val imageSize = wallpaperUri.getImageDimensions(context) ?: return null
    
    val bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, wallpaperUri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                val (targetWidth, targetHeight) = when (scaling) {
                    ScalingType.FILL -> {
                        val widthRatio = width.toFloat() / imageSize.width
                        val heightRatio = height.toFloat() / imageSize.height
                        val scale = maxOf(widthRatio, heightRatio)
                        Pair((imageSize.width * scale).fastRoundToInt(), (imageSize.height * scale).fastRoundToInt())
                    }
                    ScalingType.FIT -> {
                        val widthRatio = width.toFloat() / imageSize.width
                        val heightRatio = height.toFloat() / imageSize.height
                        val scale = minOf(widthRatio, heightRatio)
                        Pair((imageSize.width * scale).fastRoundToInt(), (imageSize.height * scale).fastRoundToInt())
                    }
                    ScalingType.STRETCH -> Pair(width, height)
                    ScalingType.NONE -> Pair(imageSize.width, imageSize.height)
                }
                decoder.setTargetSize(targetWidth, targetHeight)
                decoder.isMutableRequired = true
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(wallpaperUri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(imageSize, width, height)
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error retrieving bitmap: $e")
        null
    }

    return bitmap?.let { applyExifOrientation(it, wallpaperUri, context) }
}

private fun applyExifOrientation(source: Bitmap, uri: Uri, context: Context): Bitmap {
    val orientation = uri.getExifOrientation(context)
    if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
        return source
    }
    return try {
        val matrix = getExifTransformationMatrix(orientation, source.width, source.height)
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        rotated
    } catch (e: Exception) {
        source
    }
}

/**
 * Darken the bitmap
 */
fun darkenBitmap(source: Bitmap, darkenPercent: Int): Bitmap {
    if (darkenPercent <= 0) return source
    val mutableBitmap = if (source.isMutable) source else source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val factor = (100 - darkenPercent.coerceIn(0, 100)) / 100f
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setScale(factor, factor, factor, 1f)
        })
    }
    Canvas(mutableBitmap).drawBitmap(mutableBitmap, 0f, 0f, paint)
    if (mutableBitmap !== source) source.recycle()
    return mutableBitmap
}

/**
 * Blur the bitmap - Android 10 compatible
 */
fun blurBitmap(source: Bitmap, percent: Int): Bitmap {
    val clampedPercent = percent.coerceIn(0, 100)
    if (clampedPercent == 0) return source
    
    val radius = (clampedPercent / 100.0f) * Constants.MAX_BLUR_RADIUS
    
    // Use RenderEffect for Android 12+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return blurBitmapHardware(source, clampedPercent)
    }
    
    // Fallback for Android 10/11: Simple scaling down and up for blur effect
    // This is much faster and more compatible than ScriptIntrinsicBlur on some devices
    return try {
        val scaleFactor = 8
        val width = (source.width / scaleFactor).coerceAtLeast(1)
        val height = (source.height / scaleFactor).coerceAtLeast(1)
        
        val smallBitmap = Bitmap.createScaledBitmap(source, width, height, true)
        val blurredBitmap = Bitmap.createScaledBitmap(smallBitmap, source.width, source.height, true)
        
        smallBitmap.recycle()
        source.recycle()
        blurredBitmap
    } catch (e: Exception) {
        Log.e(TAG, "Error blurring bitmap: $e")
        source
    }
}

fun blurBitmapHardware(source: Bitmap, percent: Int): Bitmap {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return source
    
    val radius = (percent.coerceIn(0, 100) / 100.0f) * Constants.MAX_BLUR_RADIUS
    val imageReader = ImageReader.newInstance(
        source.width, source.height,
        PixelFormat.RGBA_8888, 1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    )
    val renderNode = RenderNode("BlurEffect")
    val hardwareRenderer = HardwareRenderer()
    try {
        hardwareRenderer.setSurface(imageReader.surface)
        hardwareRenderer.setContentRoot(renderNode)
        renderNode.setPosition(0, 0, source.width, source.height)
        renderNode.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR))
        val canvas = renderNode.beginRecording()
        canvas.drawBitmap(source, 0f, 0f, null)
        renderNode.endRecording()
        hardwareRenderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = imageReader.acquireNextImage() ?: return source
        val hardwareBuffer = image.hardwareBuffer ?: return source
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null) ?: return source
        val result = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true)
        image.close()
        source.recycle()
        return result
    } catch (e: Exception) {
        return source
    } finally {
        hardwareRenderer.destroy()
        imageReader.close()
    }
}

/**
 * Vignette effect
 */
fun vignetteBitmap(source: Bitmap, percent: Int): Bitmap {
    if (percent <= 0) return source
    val mutableBitmap = if (source.isMutable) source else source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    try {
        val canvas = Canvas(mutableBitmap)
        val dim = maxOf(source.width, source.height)
        val rad = (dim * (1 - (percent.coerceIn(0, 100) / Constants.VIGNETTE_DIVISOR))).coerceAtLeast(Constants.VIGNETTE_MIN_RADIUS)
        val centerX = source.width / 2f
        val centerY = source.height / 2f
        val colors = intArrayOf(Color.TRANSPARENT, Color.argb(128, 0, 0, 0), Color.BLACK)
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(centerX, centerY, rad, colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), vignettePaint)
    } catch (e: Exception) {}
    if (mutableBitmap !== source) source.recycle()
    return mutableBitmap
}

/**
 * Grayscale filter
 */
fun grayscaleBitmap(source: Bitmap, percent: Int): Bitmap {
    if (percent <= 0) return source
    val mutableBitmap = if (source.isMutable) source else source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val factor = percent.coerceIn(0, 100) / 100f
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1 - factor) })
    }
    Canvas(mutableBitmap).drawBitmap(mutableBitmap, 0f, 0f, paint)
    if (mutableBitmap !== source) source.recycle()
    return mutableBitmap
}

/**
 * Process bitmap with all effects
 */
fun processBitmap(
    source: Bitmap,
    enableDarken: Boolean = false,
    darkenPercent: Int = 0,
    enableBlur: Boolean = false,
    blurPercent: Int = 0,
    enableVignette: Boolean = false,
    vignettePercent: Int = 0,
    enableGrayscale: Boolean = false,
    grayscalePercent: Int = 0
): Bitmap {
    var result = source
    if (enableDarken) result = darkenBitmap(result, darkenPercent)
    if (enableBlur) result = blurBitmap(result, blurPercent)
    if (enableVignette) result = vignetteBitmap(result, vignettePercent)
    if (enableGrayscale) result = grayscaleBitmap(result, grayscalePercent)
    return result
}

fun Uri.detectMediaType(context: Context): WallpaperMediaType? {
    val mimeType = context.contentResolver.getType(this)
    return if (mimeType?.startsWith("image/") == true) WallpaperMediaType.IMAGE else null
}

fun isPaperizeLiveWallpaperActive(context: Context): Boolean {
    val wallpaperManager = WallpaperManager.getInstance(context)
    val info = wallpaperManager.wallpaperInfo ?: return false
    return info.packageName == context.packageName
}
