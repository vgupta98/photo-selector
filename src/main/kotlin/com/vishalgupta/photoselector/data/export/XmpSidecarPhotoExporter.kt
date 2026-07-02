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
import java.nio.file.Path
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
 * A sidecar is keyed by basename, so a RAW+JPEG pair (`IMG_1234.CR2` + `IMG_1234.JPG`) both resolve
 * to one `IMG_1234.xmp`. Rather than clobber last-writer-wins, the exporter folds every photo sharing
 * a target into one decision under the same precedence rule (reject wins) and writes it once; the
 * merge count surfaces as [XmpReport.folded].
 *
 * Sidecars are written next to the originals (not a copy destination) — that is where a DAM looks for
 * them on import. **Reader caveat:** Lightroom Classic only honors `.xmp` sidecars for proprietary
 * camera-raw files; for JPEG/TIFF/HEIC it reads embedded XMP and ignores the sidecar. Those decisions
 * still land in Capture One / Bridge, which read sidecars for every format. We write for all formats
 * regardless (harmless where unread), so a raw culler gets a full Lightroom handoff and a JPEG/HEIC
 * culler a Capture One / Bridge one. Writes go through [AtomicJsonWriter] (temp + atomic rename) so a
 * crash never leaves a half-written sidecar shadowing a photo.
 */
class XmpSidecarPhotoExporter {

    suspend fun exportSidecars(
        @Suppress("UNUSED_PARAMETER") root: RootFolder,
        photos: List<Photo>,
        favouriteIds: Set<PhotoId>,
        rejectedIds: Set<PhotoId>,
        onProgress: (written: Int, total: Int) -> Unit,
    ): XmpReport = withContext(Dispatchers.IO) {
        // Group by the sidecar target each photo would write to, so a shared basename folds into one
        // decision instead of racing two packets onto the same file. Insertion order preserved.
        val byTarget = LinkedHashMap<Path, MutableList<Photo>>()
        for (photo in photos) {
            val sidecar = photo.absolutePath.resolveSibling(
                "${photo.absolutePath.nameWithoutExtension}.xmp",
            )
            byTarget.getOrPut(sidecar) { ArrayList() }.add(photo)
        }

        var written = 0
        var skipped = 0
        var folded = 0
        val failed = ArrayList<Pair<Photo, Throwable>>()
        val total = photos.size
        var processed = 0

        for ((sidecar, group) in byTarget) {
            // Cooperative cancellation at each file boundary, mirroring CopyPhotoExporter.
            ensureActive()
            // Fold the group's memberships into one decision under the precedence rule (reject wins).
            val mapping = xmpMappingFor(
                isRejected = group.any { it.id in rejectedIds },
                isFavourite = group.any { it.id in favouriteIds },
            )
            if (mapping == XmpMapping.NONE) {
                skipped += group.size
            } else {
                if (group.size > 1) folded += group.size - 1
                try {
                    val packet = buildXmpPacket(rating = mapping.rating, label = mapping.label)
                    AtomicJsonWriter.write(sidecar, packet.toByteArray(StandardCharsets.UTF_8))
                    written++
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    group.forEach { failed += it to t }
                }
            }
            processed += group.size
            onProgress(processed, total)
        }

        XmpReport(written = written, skipped = skipped, folded = folded, failed = failed)
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
