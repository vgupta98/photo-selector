package com.vishalgupta.photoselector.data.format.macos

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.vishalgupta.photoselector.data.format.ExifCaptureInfo
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Path

/**
 * Thin JNA bridge into the macOS ImageIO / CoreGraphics / CoreFoundation system frameworks, used to
 * decode formats skiko can't (HEIC/HEIF) and camera RAW. Nothing is bundled — these frameworks ship
 * with macOS and are loaded by name; JNA provides its own dispatch native.
 *
 * Besides decoding pixels it also reads capture metadata ([readCaptureInfo]) from a HEIC's
 * EXIF/TIFF properties via `CGImageSourceCopyPropertiesAtIndex` — the only way to get HEIC capture
 * time, since the JVM-side [ExifReader] is JPEG-only. The values ImageIO parses out of the HEIC's
 * EXIF block are the same raw fields [ExifReader] yields for JPEG, so it reuses [ExifCaptureInfo] as
 * the shared raw shape rather than introducing a near-twin holder.
 *
 * The decode goes through `CGImageSourceCreateThumbnailAtIndex` with `WithTransform = true` so EXIF
 * orientation is baked in by the OS (the same decoder Finder/Preview use), and `MaxPixelSize` so the
 * downscale happens during decode. Output is rendered into an sRGB BGRA-premultiplied buffer that
 * matches [DecodedImage]'s contract exactly (wide-gamut P3 sources are colour-converted to sRGB by
 * drawing into the sRGB context).
 *
 * There are two entry points because RAW and HEIC need different *source* creation. Both decode a
 * file that's already local — the difference is only whether ImageIO is handed the bytes or the
 * on-disk location:
 * - [decodeToBgra] reads the file into an in-memory byte buffer and decodes that
 *   ([CGImageSourceCreateWithData]). Fine for HEIC.
 * - [decodeFileToBgra] points ImageIO at the local file's path and lets it read the file itself
 *   ([CGImageSourceCreateWithURL] — a `file://` `CFURL`, i.e. a local path reference, not a network
 *   URL). RAW **requires** this: the macOS RAW codec returns an empty source (`count == 0`) for
 *   several makers — notably Sony ARW — when handed bytes, and silently downgrades Nikon NEF to its
 *   tiny embedded thumbnail; given the file path it decodes the full preview for every maker (the
 *   codec seems to want the file on disk, likely memory-mapping or reading regions lazily). RAW also
 *   returns a null thumbnail unless a `MaxPixelSize` is set, so the full-resolution request passes a
 *   large sentinel.
 *
 * All JNA wiring is initialised lazily so this class loads on any platform; only an actual decode
 * call touches the frameworks, and it fails fast with a clear message off macOS.
 */
internal object MacImageIO {

    fun isAvailable(): Boolean = Platform.isMac()

    /**
     * Decodes the local file at [path] into oriented sRGB BGRA-premultiplied pixels, letting ImageIO
     * read the file itself rather than handing it the bytes. Required for camera RAW (see the class
     * doc). [targetMaxDimensionPx], when non-null and positive, caps the longer output edge; when
     * null the full resolution is decoded (a large sentinel `MaxPixelSize`, since RAW yields a null
     * thumbnail with no cap). Throws [IllegalStateException] if any framework call returns null.
     */
    fun decodeFileToBgra(path: Path, targetMaxDimensionPx: Int?): DecodedImage {
        check(Platform.isMac()) { "MacImageIO is only available on macOS" }
        val fsBytes = path.toString().toByteArray(Charsets.UTF_8)
        require(fsBytes.isNotEmpty()) { "Empty path" }

        // Wrap the local path in a file:// CFURL (a path reference, not a network URL) so ImageIO
        // opens the file directly — the RAW codec only decodes fully when it owns the file handle.
        val fileUrl = cf.CFURLCreateFromFileSystemRepresentation(null, fsBytes, fsBytes.size.toLong(), false)
            ?: error("CFURLCreateFromFileSystemRepresentation returned null")
        try {
            val source = imageIO.CGImageSourceCreateWithURL(fileUrl, null)
                ?: error("CGImageSourceCreateWithURL returned null (not a decodable image?)")
            try {
                // RAW codecs return a null thumbnail unless a max is set; for a full-res request we
                // ask for a size large enough to never downscale (ImageIO caps to the source).
                val effectiveMax = targetMaxDimensionPx?.takeIf { it > 0 } ?: RAW_FULL_RES_MAX
                val (options, maxPixelNumber) = buildThumbnailOptions(effectiveMax)
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
            cf.CFRelease(fileUrl)
        }
    }

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

    /**
     * Reads capture metadata (DateTimeOriginal/SubSecTimeOriginal, Make, Model, Orientation) from the
     * local file at [path] via ImageIO's image properties, shaped into the same raw [ExifCaptureInfo]
     * the JVM EXIF reader produces for JPEG. Returns null off macOS, when the file isn't decodable, or
     * when none of the fields are present. Never throws — any framework failure degrades to null, so a
     * caller (the HEIC capture-metadata source) falls back to "no capture time" exactly like a JPEG
     * with a stripped EXIF block.
     */
    fun readCaptureInfo(path: Path): ExifCaptureInfo? {
        if (!Platform.isMac()) return null
        val fsBytes = path.toString().toByteArray(Charsets.UTF_8)
        if (fsBytes.isEmpty()) return null
        return runCatching {
            val fileUrl = cf.CFURLCreateFromFileSystemRepresentation(null, fsBytes, fsBytes.size.toLong(), false)
                ?: return@runCatching null
            try {
                val source = imageIO.CGImageSourceCreateWithURL(fileUrl, null) ?: return@runCatching null
                try {
                    // Copy* returns an owned dict we must release; the sub-dicts and values fetched from
                    // it with CFDictionaryGetValue are *not* owned, so they are never released here.
                    val props = imageIO.CGImageSourceCopyPropertiesAtIndex(source, 0L, null)
                        ?: return@runCatching null
                    try {
                        val exif = cf.CFDictionaryGetValue(props, kCGImagePropertyExifDictionary)
                        val tiff = cf.CFDictionaryGetValue(props, kCGImagePropertyTIFFDictionary)
                        val dateTimeOriginal =
                            exif?.let { cfString(cf.CFDictionaryGetValue(it, kCGImagePropertyExifDateTimeOriginal)) }
                        val subSec =
                            exif?.let { cfString(cf.CFDictionaryGetValue(it, kCGImagePropertyExifSubsecTimeOriginal)) }
                        val make = tiff?.let { cfString(cf.CFDictionaryGetValue(it, kCGImagePropertyTIFFMake)) }
                        val model = tiff?.let { cfString(cf.CFDictionaryGetValue(it, kCGImagePropertyTIFFModel)) }
                        val orientation =
                            cfInt(cf.CFDictionaryGetValue(props, kCGImagePropertyOrientation))?.takeIf { it in 1..8 }
                        if (make == null && model == null && dateTimeOriginal == null && orientation == null) {
                            null
                        } else {
                            ExifCaptureInfo(dateTimeOriginal, subSec, make, model, orientation)
                        }
                    } finally {
                        cf.CFRelease(props)
                    }
                } finally {
                    cf.CFRelease(source)
                }
            } finally {
                cf.CFRelease(fileUrl)
            }
        }.getOrNull()
    }

    /** Marshals a (non-owned) CFStringRef to a Kotlin String, or null if absent/empty/too long. */
    private fun cfString(value: Pointer?): String? {
        if (value == null) return null
        val buffer = ByteArray(CF_STRING_BUFFER)
        if (!cf.CFStringGetCString(value, buffer, buffer.size.toLong(), K_CF_STRING_ENCODING_UTF8)) return null
        val end = buffer.indexOf(0).let { if (it < 0) buffer.size else it }
        return String(buffer, 0, end, Charsets.UTF_8).trim().ifEmpty { null }
    }

    /** Marshals a (non-owned) CFNumberRef to an Int, or null if absent/unreadable. */
    private fun cfInt(value: Pointer?): Int? {
        if (value == null) return null
        val mem = Memory(4)
        return if (cf.CFNumberGetValue(value, K_CF_NUMBER_INT_TYPE, mem)) mem.getInt(0) else null
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
    private const val K_CF_STRING_ENCODING_UTF8: Int = 0x08000100 // kCFStringEncodingUTF8

    // Capture-metadata strings are short (timestamps, camera make/model); 256 bytes is ample headroom.
    private const val CF_STRING_BUFFER: Int = 256

    // Sentinel MaxPixelSize for a full-resolution RAW decode: larger than any current sensor's long
    // edge, so ImageIO caps to the source rather than upscaling. RAW needs *some* cap or the
    // thumbnail comes back null.
    private const val RAW_FULL_RES_MAX: Int = 1_000_000

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

    // CGImageProperties dictionary keys (CFStringRef globals exported by ImageIO), used by
    // [readCaptureInfo]. The {Exif}/{TIFF} sub-dictionaries hold the capture fields.
    private val kCGImagePropertyExifDictionary: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyExifDictionary").getPointer(0)
    }
    private val kCGImagePropertyTIFFDictionary: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyTIFFDictionary").getPointer(0)
    }
    private val kCGImagePropertyExifDateTimeOriginal: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyExifDateTimeOriginal").getPointer(0)
    }
    private val kCGImagePropertyExifSubsecTimeOriginal: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyExifSubsecTimeOriginal").getPointer(0)
    }
    private val kCGImagePropertyTIFFMake: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyTIFFMake").getPointer(0)
    }
    private val kCGImagePropertyTIFFModel: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyTIFFModel").getPointer(0)
    }
    private val kCGImagePropertyOrientation: Pointer by lazy {
        imageIOLib.getGlobalVariableAddress("kCGImagePropertyOrientation").getPointer(0)
    }

    // The dictionary callback structs: pass the address of the struct itself (no dereference).
    private val keyCallbacks: Pointer by lazy { cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks") }
    private val valueCallbacks: Pointer by lazy { cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks") }

    // --- JNA interface mappings (size_t / CFIndex are 64-bit on macOS -> Kotlin Long) ---

    private interface CoreFoundation : Library {
        fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: Long): Pointer?
        fun CFRelease(cf: Pointer)
        fun CFNumberCreate(allocator: Pointer?, theType: Int, valuePtr: Pointer): Pointer?
        fun CFURLCreateFromFileSystemRepresentation(
            allocator: Pointer?,
            buffer: ByteArray,
            bufLen: Long,
            isDirectory: Boolean,
        ): Pointer?
        fun CFDictionaryCreate(
            allocator: Pointer?,
            keys: Array<Pointer>,
            values: Array<Pointer>,
            numValues: Long,
            keyCallBacks: Pointer?,
            valueCallBacks: Pointer?,
        ): Pointer?
        fun CFDictionaryGetValue(theDict: Pointer, key: Pointer): Pointer?
        fun CFStringGetCString(theString: Pointer, buffer: ByteArray, bufferSize: Long, encoding: Int): Boolean
        fun CFNumberGetValue(number: Pointer, theType: Int, valuePtr: Pointer): Boolean
    }

    private interface ImageIO : Library {
        fun CGImageSourceCreateWithData(data: Pointer, options: Pointer?): Pointer?
        fun CGImageSourceCreateWithURL(url: Pointer, options: Pointer?): Pointer?
        fun CGImageSourceCreateThumbnailAtIndex(source: Pointer, index: Long, options: Pointer?): Pointer?
        fun CGImageSourceCopyPropertiesAtIndex(source: Pointer, index: Long, options: Pointer?): Pointer?
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
