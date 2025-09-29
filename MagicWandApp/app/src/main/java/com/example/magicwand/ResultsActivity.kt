package com.example.magicwand

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load

import android.view.View
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import kotlin.math.min


class ResultsActivity : AppCompatActivity() {

    private var explainJob: Job? = null

    companion object {
        const val EXTRA_MOVIE = "extra_movie"

        // simple in-memory cache: movieId -> explanation
        private val explanationCache = mutableMapOf<Int, String>()
        private var lastCallAt = 0L                  // naive throttle across the app
        private const val CALL_MIN_GAP_MS = 1500L    // at least 1.5s between AI calls
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        // Read the movie from Intent
        val movie: Movie? =
            if (android.os.Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_MOVIE, Movie::class.java)
            else
                @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_MOVIE)

        if (movie != null) bind(movie)

        // Handle "Shake Again"
        findViewById<Button>(R.id.btnShakeAgain).setOnClickListener { finish() }
    }

    private fun bind(m: Movie) {
        val year = m.release_date?.take(4) ?: "-"
        val backdrop = "https://image.tmdb.org/t/p/w780${m.poster_path}"

        findViewById<ImageView>(R.id.posterImage).load(backdrop)
        findViewById<TextView>(R.id.movieTitle).text = "${m.title ?: "Unknown"} ($year)"
        findViewById<TextView>(R.id.movieMeta).text = "★ ${"%.1f".format(m.vote_average ?: 0.0)}"
        findViewById<TextView>(R.id.movieOverview).text = m.overview ?: ""

        explainWhy(m)
    }

    private fun ruleBasedWhy(m: Movie): String {
        val r = (m.vote_average ?: 0.0)
        val tags = buildList {
            if (r >= 7.5) add("well-reviewed")
            if ((m.overview ?: "").contains("love", true)) add("romantic")
            if ((m.overview ?: "").contains("thrill|suspense|chase".toRegex(RegexOption.IGNORE_CASE))) add("fast-paced")
            if ((m.overview ?: "").contains("family|kids".toRegex(RegexOption.IGNORE_CASE))) add("family-friendly")
        }.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "audience-friendly"
        return "Picked for its $tags vibe and engaging story. If you like ${m.title}, you’ll enjoy its tone and themes."
    }


    private fun explainWhy(movie: Movie) {
        val loading = findViewById<ProgressBar>(R.id.whyLoading)
        val whyText = findViewById<TextView>(R.id.whyText)

        // cached?
        explanationCache[movie.id]?.let {
            loading.visibility = View.GONE
            whyText.text = it
            return
        }

        explainJob?.cancel()
        loading.visibility = View.VISIBLE
        whyText.text = ""

        Log.d("AI", "BASE=${BuildConfig.AI_BASE_URL} MODEL=${BuildConfig.AI_MODEL}")

        // Insert the limiter check
        val mustWait = AiRateLimiter.gate()
        if (mustWait > 0) {
            // Too soon since last attempt – fallback instead of hitting AI
            whyText.text = ruleBasedWhy(movie)
            loading.visibility = View.GONE
            return
        }

        val prompt = """
        Explain in 2 short sentences why someone might enjoy this movie right now.
        Be specific, positive, no spoilers, using human language, not AI vibe.
        
        Title: ${movie.title}
        Overview: ${movie.overview.orEmpty().take(280)}
        Year: ${movie.release_date?.take(4) ?: "-"}
        Rating: ${"%.1f".format(movie.vote_average ?: 0.0)}
        """.trimIndent()

        android.util.Log.d("AI", "gate wait ms = $mustWait")


        explainJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                val now = System.currentTimeMillis()
                val wait = (CALL_MIN_GAP_MS - (now - lastCallAt)).coerceAtLeast(0L)
                if (wait > 0) delay(wait)

                val text = chatWithRetry(prompt)
                explanationCache[movie.id] = text
                whyText.text = text
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                android.util.Log.e("AI", "Explain error", e)
                whyText.text = "Couldn’t generate an explanation right now."
            } finally {
                lastCallAt = System.currentTimeMillis()
                loading.visibility = View.GONE
            }
        }
    }

    private suspend fun chatWithRetry(prompt: String): String {
        var attempt = 0
        var backoffMs = 1200L
        val maxAttempts = 4

        while (true) {
            attempt++
            try {
                val resp = withContext(Dispatchers.IO) {
                    AiModule.ai.getExplanation(
                        AiRequest(
                            model = BuildConfig.AI_MODEL,
                            messages = listOf(
                                AiMessage("system", "You craft concise, upbeat movie explanations."),
                                AiMessage("user", prompt)
                            ),
                            max_tokens = 180,
                            temperature = 0.7
                        )
                    )
                }
                return resp.choices.firstOrNull()?.message?.content?.trim()
                    ?: "A solid pick thanks to its story and reception."
            } catch (e: HttpException) {
                if (e.code() == 429 && attempt < maxAttempts) {
                    val retryAfterHeader = e.response()?.headers()?.get("Retry-After")
                    val retrySeconds = retryAfterHeader?.toLongOrNull()
                    val delayMs = retrySeconds?.times(1000L) ?: backoffMs
                    delay(delayMs)
                    backoffMs = min(backoffMs * 2, 8000L)
                    continue
                }

                // 🔍 log full error response for debugging
                val body = e.response()?.errorBody()?.string()
                android.util.Log.e("AI", "HTTP ${e.code()} body=$body", e)

                throw e
            }
        }
    }
}
