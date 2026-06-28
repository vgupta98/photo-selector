package com.vishalgupta.photoselector.data.update

import com.vishalgupta.photoselector.domain.update.UpdateManifest
import com.vishalgupta.photoselector.domain.update.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches the update feed over HTTPS. This is the app's only outbound request — a plain GET of a static
 * public file, carrying no body and no identifier, so it reads no differently from opening the releases
 * page in a browser. Built on the JDK's [HttpClient] to avoid pulling an HTTP library into an otherwise
 * offline app. Every failure path (offline, non-2xx, malformed JSON, unusable manifest) collapses to
 * null per [UpdateRepository]'s contract, so a missed check is silent.
 */
class HttpUpdateRepository(
    private val manifestUrl: String,
    private val json: Json,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : UpdateRepository {

    override suspend fun fetchManifest(): UpdateManifest? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder(URI.create(manifestUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return@withContext null
            json.decodeFromString(UpdateManifestDto.serializer(), response.body()).toDomainOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
