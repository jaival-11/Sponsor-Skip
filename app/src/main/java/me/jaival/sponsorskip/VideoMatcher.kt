/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright (C) 2026 Jaival
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.jaival.sponsorskip

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.abs

/**
 * Finds the video that is playing and returns its segments.
 *
 * The media session gives us the title, the channel and the duration, but not
 * the video id, so the id has to be recovered from a search. Taking the first
 * `/watch?v=` in the search HTML is right most of the time and silently wrong
 * the rest of the time: there is no signal that the id belongs to a different
 * video.
 *
 * Measured on a Pixel 8 Pro with the released 1.2.2 build and detailed logging
 * turned on:
 *
 *   watching vsL9rXc96s4 (20:42, has a sponsor segment 54s-242s in the
 *   database) the app logged
 *     [SCRAPER] Extracted ID: '6l9moefdgEg'
 *     [API] Response Code: 404
 *   so the sponsor was not skipped at all,
 *
 *   watching 4mHb6CcOvTE (13:18) the app logged
 *     [SCRAPER] Extracted ID: 'bV73Dr-vUzY'
 *     [API] Response Code: 404
 *
 * Both ids belong to other videos. The usual damage is a missed skip. The worse
 * case, skipping at timestamps that belong to someone else's video, needs the
 * wrong video to have segments of its own; I did not catch that happening, but
 * nothing in the current code prevents it and the database only gets denser.
 *
 * This class uses the two pieces of metadata that the media session already
 * provides and the old code ignored - channel and duration - and refuses to
 * answer when the evidence is not good enough. [Result.Unconfirmed] means
 * "do not skip", never "skip and hope".
 */
object VideoMatcher {

    /**
     * How far the duration from the API may differ from the duration reported
     * by the media session before the two are treated as different videos.
     * Rounding accounts for well under a second.
     */
    private const val DURATION_TOLERANCE_SECONDS = 3.0

    /**
     * Upper bound on how many candidates may be checked. With the first search
     * hit being correct most of the time this is a budget for the unlucky
     * cases, not the normal cost.
     */
    private const val MAX_CANDIDATES = 6

    private val WATCH_ID = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""")
    private val SHORTS_ID = Regex("""/shorts/([a-zA-Z0-9_-]{11})""")
    private val INITIAL_DATA = Regex(
        """ytInitialData\s*=\s*(\{.*?\});</script>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Letters that NFKD does not decompose because Unicode treats them as
     * separate letters rather than a base letter plus a mark. Without this map
     * the stripping below turns them into spaces, which quietly breaks title
     * comparison for several languages.
     */
    private val STANDALONE_LETTERS = mapOf(
        'ł' to "l", 'đ' to "d", 'ø' to "o", 'æ' to "ae",
        'œ' to "oe", 'ß' to "ss", 'þ' to "th", 'ð' to "d",
        'ı' to "i"
    )

    data class Candidate(
        val videoId: String,
        val title: String = "",
        val channel: String = "",
        val durationSeconds: Double? = null,
        val position: Int = 0
    )

    /**
     * Anything other than [Confirmed] means do not skip. [NoSegments] and
     * [Unconfirmed] are kept apart on purpose: "this video has nothing to skip"
     * and "I could not tell which video this is" are different states, they are
     * diagnosed differently and they deserve different wording to the user.
     */
    sealed class Result {
        data class Confirmed(
            val videoId: String,
            val segments: JSONArray,
            val evidence: List<String>
        ) : Result()

        data class NoSegments(val checked: Int) : Result()
        data class Unconfirmed(val reason: String, val rejected: Int) : Result()
        data class LookupFailed(val reason: String) : Result()
    }

    // ---------- comparison helpers ----------

    /**
     * Case, accents and punctuation removed. Digits are kept on purpose: an
     * episode number is sometimes the only difference between two titles.
     */
    fun normalize(text: String): String {
        val lower = text.lowercase()
        val mapped = buildString(lower.length) {
            for (c in lower) append(STANDALONE_LETTERS[c] ?: c.toString())
        }
        return Normalizer.normalize(mapped, Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    /** "1:23:45" or "12:34" to seconds; null when the text is not a duration. */
    fun durationToSeconds(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":")
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        var total = 0L
        for (p in parts) total = total * 60 + p.toLong()
        return total.toDouble()
    }

    // ---------- reading the search results ----------

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) out.add(v)
                    collect(v, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun textOf(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("simpleText", "").let { if (it.isNotEmpty()) return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text", "") ?: "")
        }
    }

    /**
     * Candidates in the order they appear on the page.
     *
     * Two sources, because neither is enough on its own: the structured blocks
     * carry title, channel and duration but do not cover Shorts, which live in
     * a different renderer; the raw pattern catches everything but carries no
     * data to compare against.
     */
    fun candidates(client: OkHttpClient, query: String, strict: Boolean): List<Candidate> {
        val searchFor = if (strict) "intitle:\"$query\"" else query
        val url = "https://www.youtube.com/results?search_query=" +
            URLEncoder.encode(searchFor, "UTF-8")
        val request = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Cookie", "SOCS=CAI")
            .build()
        val html = client.newCall(request).execute().use { it.body?.string() } ?: return emptyList()

        val found = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()

        INITIAL_DATA.find(html)?.groupValues?.get(1)?.let { json ->
            try {
                val blocks = mutableListOf<JSONObject>()
                collect(JSONObject(json), "videoRenderer", blocks)
                for (b in blocks) {
                    val id = b.optString("videoId", "")
                    if (id.isEmpty() || !seen.add(id)) continue
                    found.add(
                        Candidate(
                            videoId = id,
                            title = textOf(b.optJSONObject("title")),
                            channel = textOf(b.optJSONObject("ownerText")).ifEmpty {
                                textOf(b.optJSONObject("longBylineText"))
                            },
                            durationSeconds = durationToSeconds(textOf(b.optJSONObject("lengthText"))),
                            position = found.size + 1
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.log("[MATCHER] ytInitialData could not be parsed: ${e.message}")
            }
        }

        for (pattern in listOf(WATCH_ID, SHORTS_ID)) {
            for (m in pattern.findAll(html)) {
                val id = m.groupValues[1]
                if (!seen.add(id)) continue
                found.add(Candidate(videoId = id, position = found.size + 1))
            }
        }
        return found
    }

    // ---------- segments ----------

    private fun segments(
        client: OkHttpClient,
        videoId: String,
        encodedCategories: String
    ): Pair<String, JSONArray?> {
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=" +
            URLEncoder.encode(videoId, "UTF-8") + "&categories=" + encodedCategories
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.code == 404 -> "NONE" to null
                    !response.isSuccessful -> "ERROR" to null
                    else -> "OK" to JSONArray(response.body?.string() ?: "[]")
                }
            }
        } catch (e: Exception) {
            AppLogger.log("[MATCHER] network error for $videoId: ${e.message}")
            "ERROR" to null
        }
    }

    private fun evidenceFor(
        c: Candidate,
        title: String,
        channel: String,
        duration: Double?
    ): List<String> {
        val found = mutableListOf<String>()
        if (c.title.isNotEmpty() && normalize(c.title) == title) found.add("title")
        if (channel.isNotEmpty() && c.channel.isNotEmpty() && normalize(c.channel) == channel) {
            found.add("channel")
        }
        if (duration != null && c.durationSeconds != null &&
            abs(c.durationSeconds - duration) <= DURATION_TOLERANCE_SECONDS
        ) {
            found.add("duration")
        }
        return found
    }

    private class Counters {
        val checked = mutableSetOf<String>()
        val rejected = mutableSetOf<String>()
        val withoutSegments = mutableSetOf<String>()
    }

    private fun tryPool(
        client: OkHttpClient,
        pool: List<Candidate>,
        title: String,
        channel: String,
        duration: Double?,
        encodedCategories: String,
        cache: MutableMap<String, Pair<String, JSONArray?>>,
        counters: Counters
    ): Result.Confirmed? {
        val ranked = pool.map { it to evidenceFor(it, title, channel, duration) }
            .sortedWith(
                compareByDescending<Pair<Candidate, List<String>>> { it.second.size }
                    .thenBy { it.first.position }
            )

        for ((candidate, evidence) in ranked.take(MAX_CANDIDATES)) {
            val (state, data) = cache.getOrPut(candidate.videoId) {
                segments(client, candidate.videoId, encodedCategories)
            }
            counters.checked.add(candidate.videoId)

            if (state == "ERROR") continue
            if (state == "NONE" || data == null || data.length() == 0) {
                counters.withoutSegments.add(candidate.videoId)
                continue
            }

            // The response carries videoDuration, so the match can be verified
            // without a single extra request.
            var closest: Double? = null
            if (duration != null) {
                for (i in 0 until data.length()) {
                    val reported = data.optJSONObject(i)?.optDouble("videoDuration", 0.0) ?: 0.0
                    if (reported > 0.0 &&
                        (closest == null || abs(reported - duration) < abs(closest!! - duration))
                    ) {
                        closest = reported
                    }
                }
            }

            if (closest != null && duration != null) {
                if (abs(closest!! - duration) > DURATION_TOLERANCE_SECONDS) {
                    counters.rejected.add(candidate.videoId)
                    AppLogger.log(
                        "[MATCHER] ${candidate.videoId} rejected: API says " +
                            "${closest!!.toInt()}s, session says ${duration.toInt()}s"
                    )
                    continue
                }
                AppLogger.log("[MATCHER] ${candidate.videoId} confirmed by duration (${closest!!.toInt()}s)")
                return Result.Confirmed(candidate.videoId, data, evidence + "api duration")
            }

            // Older submissions have no videoDuration. Then the search result
            // itself has to carry the evidence, and one witness is not enough.
            if (evidence.contains("title") &&
                (evidence.contains("channel") || evidence.contains("duration"))
            ) {
                AppLogger.log("[MATCHER] ${candidate.videoId} accepted on $evidence (API has no duration)")
                return Result.Confirmed(candidate.videoId, data, evidence)
            }
            counters.rejected.add(candidate.videoId)
        }
        return null
    }

    /**
     * Returns segments only for a video whose identity could be confirmed.
     *
     * Three query strategies, each tried only when the previous one did not
     * settle it. Each one rescued a different measured case:
     *   1. the title alone handles the vast majority,
     *   2. title plus channel rescues titles the search truncates with an
     *      ellipsis, such as "So I played with Rank 1...",
     *   3. a strict intitle search rescues titles with emoji and videos that
     *      the plain search does not surface at all.
     * The normal cost stays one search request and one API request.
     */
    fun match(
        client: OkHttpClient,
        sessionTitle: String,
        sessionChannel: String,
        sessionDurationMs: Long,
        encodedCategories: String
    ): Result {
        if (sessionTitle.isBlank()) return Result.LookupFailed("media session reported no title")

        val duration = if (sessionDurationMs > 0) sessionDurationMs / 1000.0 else null
        val title = normalize(sessionTitle)
        val channel = if (sessionChannel.isNotBlank()) normalize(sessionChannel) else ""

        val strategies = mutableListOf(Triple("title", sessionTitle, false))
        if (sessionChannel.isNotBlank()) {
            strategies.add(Triple("title and channel", "$sessionTitle $sessionChannel", false))
        }
        strategies.add(Triple("strict intitle", sessionTitle, true))

        val cache = mutableMapOf<String, Pair<String, JSONArray?>>()
        val counters = Counters()
        var emptyPools = 0

        for ((name, query, strict) in strategies) {
            val pool = try {
                candidates(client, query, strict)
            } catch (e: Exception) {
                AppLogger.log("[MATCHER] [$name] search request failed: ${e.message}")
                emptyList()
            }
            if (pool.isEmpty()) {
                emptyPools++
                AppLogger.log("[MATCHER] [$name] search returned no ids")
                continue
            }
            AppLogger.log("[MATCHER] [$name] ${pool.size} candidates")
            tryPool(client, pool, title, channel, duration, encodedCategories, cache, counters)
                ?.let { return it }
        }

        // Every checked candidate has to land in exactly one bucket. A mismatch
        // means the counting is wrong, which would make the summary lie even
        // when the decision was right.
        val accounted = counters.rejected.size + counters.withoutSegments.size
        if (accounted != counters.checked.size) {
            AppLogger.log(
                "[MATCHER] counting error: checked ${counters.checked.size}, " +
                    "rejected ${counters.rejected.size}, without segments ${counters.withoutSegments.size}"
            )
        }

        return when {
            emptyPools == strategies.size ->
                Result.LookupFailed("no strategy returned any candidate")
            counters.rejected.isEmpty() && counters.withoutSegments.isNotEmpty() ->
                Result.NoSegments(counters.checked.size)
            else -> Result.Unconfirmed(
                "checked ${counters.checked.size} candidates, " +
                    "${counters.rejected.size} rejected as a different video",
                counters.rejected.size
            )
        }
    }
}
