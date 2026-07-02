package com.vishalgupta.photoselector.data.export

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.XmpReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.io.path.nameWithoutExtension

/**
 * Writes an XMP sidecar (`IMG_1234.CR2` -> `IMG_1234.xmp`) next to each source photo so the cull's
 * keep/reject decisions travel into Lightroom / Capture One, which both read `xmp:Rating` and
 * `xmp:Label` from a sidecar sharing the photo's basename.
 *
 * A photo can sit in N categories here, but XMP rating/label is singular, so the mapping follows one
 * documented precedence — **reject wins**:
 *  1. In Rejects        -> Rating = -1, Label = "Rejected"  (Adobe's rejected-flag convention)
 *  2. else in Favourites -> Rating = 5
 *  3. else               -> no rating/label; nothing to hand off, so no sidecar is written
 *
 * Reject beats favourite because a reject is a deliberate cut: a stray favourite membership must not
 * silently promote a rejected frame back into the keeper set on the far side of the handoff.
 *
 * Sidecars are written next to the originals (not a copy destination) — that is where a DAM looks for
 * them on import. Writes go through [AtomicJsonWriter] (temp + atomic rename) so a crash never leaves
 * a half-written sidecar shadowing a photo.
 */
class XmpSidecarPhotoExporter {

    suspend fun exportSidecars(
        @Suppress("UNUSED_PARAMETER") root: RootFolder,
        photos: List<Photo>,
        favouriteIds: Set<PhotoId>,
        rejectedIds: Set<PhotoId>,
        onProgress: (written: Int, total: Int) -> Unit,
    ): XmpReport = withContext(Dispatchers.IO) {
        var written = 0
        var skipped = 0
        val failed = ArrayList<Pair<Photo, Throwable>>()
        val total = photos.size

        for ((index, photo) in photos.withIndex()) {
            // Cooperative cancellation at each file boundary, mirroring CopyPhotoExporter.
            ensureActive()
            val mapping = xmpMappingFor(
                isRejected = photo.id in rejectedIds,
                isFavourite = photo.id in favouriteIds,
            )
            if (mapping == XmpMapping.NONE) {
                skipped++
            } else {
                try {
                    val sidecar = photo.absolutePath.resolveSibling(
                        "${photo.absolutePath.nameWithoutExtension}.xmp",
                    )
                    val packet = buildXmpPacket(rating = mapping.rating, label = mapping.label)
                    AtomicJsonWriter.write(sidecar, packet.toByteArray(StandardCharsets.UTF_8))
                    written++
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    failed += photo to t
                }
            }
            onProgress(index + 1, total)
        }

        XmpReport(written = written, skipped = skipped, failed = failed)
    }
}

/**
 * The XMP rating/label a photo resolves to under the reject-wins precedence rule. [NONE] means the
 * photo is in neither built-in bucket, so nothing is worth writing (no rating and no label).
 */
enum class XmpMapping(val rating: Int?, val label: String?) {
    REJECTED(rating = -1, label = "Rejected"),
    FAVOURITE(rating = 5, label = null),
    NONE(rating = null, label = null),
}

/** Resolves the singular XMP mapping for a photo from its two built-in memberships (reject wins). */
fun xmpMappingFor(isRejected: Boolean, isFavourite: Boolean): XmpMapping = when {
    isRejected -> XmpMapping.REJECTED
    isFavourite -> XmpMapping.FAVOURITE
    else -> XmpMapping.NONE
}

/**
 * Builds a minimal, well-formed XMP packet carrying [rating] (`xmp:Rating`) and [label]
 * (`xmp:Label`), each written only when non-null. The `<?xpacket?>` wrapper and the
 * `x:xmpmeta` / `rdf:RDF` / `rdf:Description` skeleton are the standard shape Lightroom and
 * Capture One read from a sidecar.
 */
fun buildXmpPacket(rating: Int?, label: String?): String {
    val attrs = buildString {
        if (rating != null) append("\n         xmp:Rating=\"$rating\"")
        if (label != null) append("\n         xmp:Label=\"${xmlEscapeAttr(label)}\"")
    }
    return """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
         xmlns:xmp="http://ns.adobe.com/xap/1.0/"$attrs/>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>
"""
}

private fun xmlEscapeAttr(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
