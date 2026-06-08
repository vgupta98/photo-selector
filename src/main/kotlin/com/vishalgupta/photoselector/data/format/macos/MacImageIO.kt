package com.vishalgupta.photoselector.data.format.macos

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * Thin JNA bridge into the macOS ImageIO / CoreGraphics / CoreFoundation system frameworks, used to
 * decode formats skiko can't (HEIC/HEIF). Nothing is bundled — these frameworks ship with macOS and
 * are loaded by name; JNA provides its own dispatch native.
 *
 * The decode goes through `CGImageSourceCreateThumbnailAtIndex` with `WithTransform = true` so EXIF
 * orientation is baked in by the OS (the same decoder Finder/Preview use), and `MaxPixelSize` so the
 * downscale happens during decode. Output is rendered into an sRGB BGRA-premultiplied buffer that
 * matches [DecodedImage]'s contract exactly (wide-gamut P3 sources are colour-converted to sRGB by
 * drawing into the sRGB context).
 *
 * All JNA wiring is initialised lazily so this class loads on any platform; only an actual decode
 * call touches the frameworks, and it fails fast with a clear message off macOS.
 */
internal object MacImageIO {

    fun isAvailable(): Boolean = Platform.isMac()

    /**
     * Decodes [bytes] (an encoded HEIC/HEIF image) into oriented sRGB BGRA-premultiplied pixels.
     * [targetMaxDimensionPx], when non-null, caps the longer output edge (no upscaling beyond the
     * source). Throws [IllegalStateException] if any framework call returns null.
     */
    fun decodeToBgra(bytes: ByteArray, targetMaxDimensionPx: Int?): DecodedImage {
        check(Platform.isMac()) { "MacImageIO is only available on macOS" }
        require(bytes.isNotEmpty()) { "Empty image bytes" }

        val data = cf.CFDataCreate(null, bytes, bytes.size.toLong())
            ?: error("CFDataCreate returned null")
        try {
            val source = imageIO.CGImageSourceCreateWithData(data, null)
                ?: error("CGImageSourceCreateWithData returned null (not a decodable image?)")
            try {
                val (options, maxPixelNumber) = buildThumbnailOptions(targetMaxDimensionPx)
                try {
                    val image = imageIO.CGImageSourceCreateThumbnailAtIndex(source, 0L, options)
                        ?: error("CGImageSourceCreateThumbnailAtIndex returned null")
                    try {
                        return renderToBgra(image)
                    } finally {
                        cg.CGImageRelease(image)
                    }
                } finally {
                    cf.CFRelease(options)
                    maxPixelNumber?.let { cf.CFRelease(it) }
                }
            } finally {
                cf.CFRelease(source)
            }
        } finally {
            cf.CFRelease(data)
        }
    }

    /** Builds the thumbnail options dict; returns it plus the CFNumber to release (if any). */
    private fun buildThumbnailOptions(targetMaxDimensionPx: Int?): Pair<Pointer, Pointer?> {
        val keys = ArrayList<Pointer>(3)
        val values = ArrayList<Pointer>(3)

        keys += kCGImageSourceCreateThumbnailFromImageAlways
        values += booleanTrue
        keys += kCGImageSourceCreateThumbnailWithTransform
        values += booleanTrue

        var maxPixelNumber: Pointer? = null
        if (targetMaxDimensionPx != null && targetMaxDimensionPx > 0) {
            val mem = Memory(4).apply { setInt(0, targetMaxDimensionPx) }
            maxPixelNumber = cf.CFNumberCreate(null, K_CF_NUMBER_INT_TYPE, mem)
                ?: error("CFNumberCreate returned null")
            keys += kCGImageSourceThumbnailMaxPixelSize
            values += maxPixelNumber
        }

        val dict = cf.CFDictionaryCreate(
            null,
            keys.toTypedArray(),
            values.toTypedArray(),
            keys.size.toLong(),
            keyCallbacks,
            valueCallbacks,
        ) ?: error("CFDictionaryCreate returned null")
        return dict to maxPixelNumber
    }

    private fun renderToBgra(image: Pointer): DecodedImage {
        val width = cg.CGImageGetWidth(image).toInt()
        val height = cg.CGImageGetHeight(image).toInt()
        check(width > 0 && height > 0) { "Decoded HEIC has zero dimension ${width}x$height" }

        val rowBytes = width.toLong() * 4
        val buffer = Memory(rowBytes * height)
        val colorSpace = cg.CGColorSpaceCreateWithName(sRGBColorSpaceName)
            ?: error("CGColorSpaceCreateWithName(sRGB) returned null")
        try {
            val ctx = cg.CGBitmapContextCreate(
                buffer, width.toLong(), height.toLong(), 8L, rowBytes, colorSpace, BGRA_PREMUL_LE,
            ) ?: error("CGBitmapContextCreate returned null")
            try {
                val rect = CGRect.ByValue().apply {
                    x = 0.0; y = 0.0; this.width = width.toDouble(); this.height = height.toDouble()
                }
                cg.CGContextDrawImage(ctx, rect, image)
                val out = buffer.getByteArray(0, (rowBytes * height).toInt())
                return DecodedImage(width = width, height = height, bgraBytes = out)
            } finally {
                cg.CGContextRelease(ctx)
            }
        } finally {
            cg.CGColorSpaceRelease(colorSpace)
        }
    }

    // --- Constants ---

    // kCGImageAlphaPremultipliedFirst (2) | kCGBitmapByteOrder32Little (2 << 12) -> in-memory BGRA.
    private const val BGRA_PREMUL_LE: Int = 2 or (2 shl 12)
    private const val K_CF_NUMBER_INT_TYPE: Int = 9 // kCFNumberIntType

    // --- Lazy framework wiring (so the class loads on any platform) ---

    private val cf: CoreFoundation by lazy { Native.load("CoreFoundation", CoreFoundation::class.java) }
    private val cg: CoreGraphics by lazy { Native.load("CoreGraphics", CoreGraphics::class.java) }
    private val imageIO: ImageIO by lazy { Native.load("ImageIO", ImageIO::class.java) }

    private val cfLib by lazy { NativeLibrary.getInstance("CoreFoundation") }
    private val cgLib by lazy { NativeLibrary.getInstance("CoreGraphics") }
    private val imageIOLib by lazy { NativeLibrary.getInstance("ImageIO") }

    // CFBooleanRef / CFStringRef globals hold a pointer to the CF object: dereference once.
    private val booleanTrue: Pointer by lazy { cfLib.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0) }
    private val sRGBColorSpaceName: Pointer by lazy { cgLib.getGlobalVariableAddress("kCGColorSpaceSRGB").getPointer(0) }
    private val kCGImageSourceCreateThumbnailFromImageAlways: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImageSourceCreateThumbnailFromImageAlways").getPointer(0)
    }
    private val kCGImageSourceCreateThumbnailWithTransform: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImageSourceCreateThumbnailWithTransform").getPointer(0)
    }
    private val kCGImageSourceThumbnailMaxPixelSize: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImageSourceThumbnailMaxPixelSize").getPointer(0)
    }

    // The dictionary callback structs: pass the address of the struct itself (no dereference).
    private val keyCallbacks: Pointer by lazy { cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks") }
    private val valueCallbacks: Pointer by lazy { cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks") }

    // --- JNA interface mappings (size_t / CFIndex are 64-bit on macOS -> Kotlin Long) ---

    private interface CoreFoundation : Library {
        fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: Long): Pointer?
        fun CFRelease(cf: Pointer)
        fun CFNumberCreate(allocator: Pointer?, theType: Int, valuePtr: Pointer): Pointer?
        fun CFDictionaryCreate(
            allocator: Pointer?,
            keys: Array<Pointer>,
            values: Array<Pointer>,
            numValues: Long,
            keyCallBacks: Pointer?,
            valueCallBacks: Pointer?,
        ): Pointer?
    }

    private interface ImageIO : Library {
        fun CGImageSourceCreateWithData(data: Pointer, options: Pointer?): Pointer?
        fun CGImageSourceCreateThumbnailAtIndex(source: Pointer, index: Long, options: Pointer?): Pointer?
    }

    private interface CoreGraphics : Library {
        fun CGImageGetWidth(image: Pointer): Long
        fun CGImageGetHeight(image: Pointer): Long
        fun CGColorSpaceCreateWithName(name: Pointer): Pointer?
        fun CGColorSpaceRelease(space: Pointer)
        fun CGBitmapContextCreate(
            data: Pointer,
            width: Long,
            height: Long,
            bitsPerComponent: Long,
            bytesPerRow: Long,
            space: Pointer,
            bitmapInfo: Int,
        ): Pointer?
        fun CGContextDrawImage(c: Pointer, rect: CGRect.ByValue, image: Pointer)
        fun CGContextRelease(c: Pointer)
        fun CGImageRelease(image: Pointer)
    }

    // CGRect = { CGPoint origin; CGSize size; } = four CGFloat (double on 64-bit macOS).
    @Structure.FieldOrder("x", "y", "width", "height")
    open class CGRect : Structure() {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
        @JvmField var width: Double = 0.0
        @JvmField var height: Double = 0.0

        class ByValue : CGRect(), Structure.ByValue
    }
}
