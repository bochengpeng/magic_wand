package com.example.magicwand

import android.content.Intent
import android.os.Bundle
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
        // Intent extra key
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

        val prompt = buildString {
            appendLine("Explain in 2–3 friendly sentences why this movie might be a good pick.")
            appendLine("Focus on mood, themes, strengths, and who might enjoy it.")
            appendLine("Keep it concise and positive.")
            appendLine("Title: ${movie.title}")
            appendLine("Overview: ${movie.overview.orEmpty().take(500)}")
            appendLine("Release date: ${movie.release_date ?: "-"}")
            appendLine("Rating: ${movie.vote_average ?: 0.0}")
        }

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
        val maxAttempts = 3

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
                throw e
            }
        }
    }
}
