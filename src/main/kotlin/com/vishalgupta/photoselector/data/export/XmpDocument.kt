package com.vishalgupta.photoselector.data.export

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** The rating a cull decision maps to in a sidecar, or [Undecided] for a photo in neither bucket. */
enum class RatingDecision(val rating: Int?) {
    Favourite(5),
    Rejected(-1),
    Undecided(null),
}

/** What [XmpDocument.merge] resolved to for one photo. */
sealed interface XmpMergeOutcome {
    /** New sidecar bytes to write atomically. [cleared] is true when this removed a rating we owned. */
    class Write(val bytes: ByteArray, val cleared: Boolean) : XmpMergeOutcome

    /** Nothing to write: no existing sidecar and no decision, or a user-overridden rating left intact. */
    data object Skip : XmpMergeOutcome
}

/**
 * Merges a cull decision into an XMP sidecar **without clobbering it**. Adobe Bridge's real sidecar is
 * a full RDF document (mirrored EXIF, `xmpMM:History`, an embedded-XMP digest); overwriting it would
 * destroy all of that, so we parse the existing bytes with the JDK DOM, mutate *only* our owned fields
 * ([XMP_RATING] and our private ownership stamp), and re-serialize — every other field is left byte-
 * for-byte untouched. When no sidecar exists we emit a minimal valid packet.
 *
 * Two fields are ours:
 *  - `xmp:Rating` — the single Adobe field shared by stars and the reject flag: reject = `-1`,
 *    favourite = `5` (validated in Bridge). We deliberately do **not** write `xmp:Label` (the wrong
 *    field) nor `photoshop:LabelColor` (deferred: the model has no category-colour data yet).
 *  - `rhenium:managedRating` — a stamp under a namespace we own recording the rating we wrote. It lets
 *    an un-decide (a photo dropped from both buckets) clear `xmp:Rating` **only** when the on-disk
 *    value still equals what we stamped, so a rating the user changed in Bridge is never touched.
 *
 * Both fields are read and written in either representation a DAM may use — an attribute on
 * `rdf:Description` **or** a child element — so a merge round-trips whatever shape the sidecar is in.
 * The parser is non-namespace-aware on purpose: XMP prefixes are conventional (`xmp`, `rdf`,
 * `rhenium`), so operating on the literal prefixed names keeps attribute and element handling uniform.
 */
object XmpDocument {

    const val STAMP_PREFIX = "rhenium"
    const val STAMP_LOCAL = "managedRating"
    const val STAMP_QNAME = "$STAMP_PREFIX:$STAMP_LOCAL"

    private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
    private const val STAMP_NS = "http://ns.rhenium.app/xmp/1.0/"
    private const val RATING_QNAME = "xmp:Rating"

    /**
     * Resolves the sidecar bytes to write for [decision], given the [existing] sidecar bytes (null =
     * none on disk yet). A favourite/reject always yields a [XmpMergeOutcome.Write]; an [Undecided]
     * photo yields a clearing write only when we own the on-disk rating, else [XmpMergeOutcome.Skip].
     */
    fun merge(existing: ByteArray?, decision: RatingDecision): XmpMergeOutcome {
        val rating = decision.rating
        if (rating != null) {
            if (existing == null) {
                return XmpMergeOutcome.Write(minimalPacket(rating).toByteArray(StandardCharsets.UTF_8), cleared = false)
            }
            val doc = parse(existing)
            val descs = descriptionElements(doc)
            // Update the block that already carries a rating we manage, so a sidecar whose rating
            // lives in a non-first rdf:Description gets that statement rewritten in place instead of
            // a second, conflicting xmp:Rating appended to block 0. Prefer our own stamped block,
            // then any block with a rating, then the first block.
            val desc = descs.firstOrNull { readField(it, STAMP_QNAME) != null }
                ?: descs.firstOrNull { readField(it, RATING_QNAME) != null }
                ?: descs.firstOrNull()
                ?: return XmpMergeOutcome.Write(minimalPacket(rating).toByteArray(StandardCharsets.UTF_8), cleared = false)
            setField(desc, "xmp", XMP_NS, RATING_QNAME, rating.toString())
            setField(desc, STAMP_PREFIX, STAMP_NS, STAMP_QNAME, rating.toString())
            return XmpMergeOutcome.Write(serialize(doc), cleared = false)
        }

        // Undecided: only clear a rating we still own (stamp present AND on-disk rating == stamped).
        if (existing == null) return XmpMergeOutcome.Skip
        val doc = parse(existing)
        // Operate on whichever block holds our stamp, not blindly the first block.
        val desc = descriptionElements(doc).firstOrNull { readField(it, STAMP_QNAME) != null }
            ?: return XmpMergeOutcome.Skip
        val stamped = readField(desc, STAMP_QNAME) ?: return XmpMergeOutcome.Skip
        if (readField(desc, RATING_QNAME) != stamped) return XmpMergeOutcome.Skip
        removeField(desc, RATING_QNAME)
        removeField(desc, STAMP_QNAME)
        return XmpMergeOutcome.Write(serialize(doc), cleared = true)
    }

    // --- DOM plumbing --------------------------------------------------------------------------

    private fun minimalPacket(rating: Int): String = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
         xmlns:xmp="$XMP_NS"
         xmlns:$STAMP_PREFIX="$STAMP_NS"
         xmp:Rating="$rating"
         $STAMP_QNAME="$rating"/>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>
"""

    private fun parse(bytes: ByteArray): Document =
        documentBuilder().parse(ByteArrayInputStream(bytes))

    private fun documentBuilder(): DocumentBuilder =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Sidecars never carry a DOCTYPE; refusing one hardens the parse against XXE.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder()

    private fun serialize(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun descriptionElements(doc: Document): List<Element> {
        val nodes = doc.getElementsByTagName("rdf:Description")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Reads a field in either form: an attribute on [desc] or a direct child element's text. */
    private fun readField(desc: Element, qname: String): String? {
        if (desc.hasAttribute(qname)) return desc.getAttribute(qname)
        return directChild(desc, qname)?.textContent
    }

    /** Sets a field, preserving whichever form it already takes (child element vs attribute). */
    private fun setField(desc: Element, prefix: String, namespace: String, qname: String, value: String) {
        if (!desc.hasAttribute("xmlns:$prefix")) desc.setAttribute("xmlns:$prefix", namespace)
        val child = directChild(desc, qname)
        if (child != null) child.textContent = value else desc.setAttribute(qname, value)
    }

    /** Removes a field in whichever form(s) it takes. */
    private fun removeField(desc: Element, qname: String) {
        if (desc.hasAttribute(qname)) desc.removeAttribute(qname)
        directChild(desc, qname)?.let { desc.removeChild(it) }
    }

    private fun directChild(parent: Element, qname: String): Element? {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName == qname) return n
        }
        return null
    }
}
