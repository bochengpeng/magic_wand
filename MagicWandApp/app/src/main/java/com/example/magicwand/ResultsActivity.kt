package com.example.magicwand

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load


class ResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        //Read the movie from Internet
        val movie: Movie? = intent.getParcelableExtra(EXTRA_MOVIE)
        if (movie != null) bind(movie)

        // Handle "Shake Again"
        findViewById<Button>(R.id.btnShakeAgain).setOnClickListener {
            finish()
        }
    }

    private fun bind(m: Movie) {
        val year = m.release_date?.take(4) ?: "-"
        val backdrop = "https://image.tmdb.org/t/p/w780${m.poster_path}"

        findViewById<ImageView>(R.id.posterImage).load(backdrop)
        findViewById<TextView>(R.id.movieTitle).text = "${m.title ?: "Unknown"} ($year)"
        findViewById<TextView>(R.id.movieMeta).text = "★ ${"%.1f".format(m.vote_average ?: 0.0)}"
        findViewById<TextView>(R.id.movieOverview).text = m.overview ?: ""
    }

    companion object {
        const val EXTRA_MOVIE = "extra_movie"
    }
}