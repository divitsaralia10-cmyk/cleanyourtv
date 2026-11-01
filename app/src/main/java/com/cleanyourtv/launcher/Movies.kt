package com.cleanyourtv.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Movie(
    val imdbId: String,
    val title: String,
    val year: String,
    val poster: String?,
    val plot: String,
    /** IMDb rating such as "8.8", or null if the movie has none. */
    val imdb: String?,
    /** Formatted run time such as "2h 28m", or empty. */
    val runtime: String,
    val genres: String,
)

data class Availability(
    val stream: List<String>,
    val rent: List<String>,
    val buy: List<String>,
)

data class MovieCategory(val name: String, val titles: List<Pair<String, String>>)

/** Built-in lists (title to year). OMDb cannot browse by category, so these are hand-picked. */
val MOVIE_CATEGORIES: List<MovieCategory> = listOf(
    MovieCategory(
        "Top Rated",
        listOf(
            "The Shawshank Redemption" to "1994", "The Godfather" to "1972", "The Dark Knight" to "2008",
            "Inception" to "2010", "Interstellar" to "2014", "Fight Club" to "1999", "Pulp Fiction" to "1994",
            "Forrest Gump" to "1994", "The Matrix" to "1999", "Parasite" to "2019", "Gladiator" to "2000",
            "The Prestige" to "2006", "Whiplash" to "2014", "Joker" to "2019",
        ),
    ),
    MovieCategory(
        "Bollywood",
        listOf(
            "3 Idiots" to "2009", "Dangal" to "2016", "PK" to "2014", "Lagaan" to "2001",
            "Dil Chahta Hai" to "2001", "Zindagi Na Milegi Dobara" to "2011", "Queen" to "2013",
            "Barfi!" to "2012", "Andhadhun" to "2018", "Gully Boy" to "2019", "Jawan" to "2023",
            "Pathaan" to "2023", "Animal" to "2023", "12th Fail" to "2023", "Laapataa Ladies" to "2023",
        ),
    ),
    MovieCategory(
        "South Indian",
        listOf(
            "Baahubali: The Beginning" to "2015", "RRR" to "2022", "K.G.F: Chapter 2" to "2022",
            "Pushpa: The Rise" to "2021", "Vikram" to "2022", "Kantara" to "2022", "Jai Bhim" to "2021",
            "Sita Ramam" to "2022", "Master" to "2021", "Ala Vaikunthapurramuloo" to "2020",
            "Drishyam" to "2013", "Manjummel Boys" to "2024",
        ),
    ),
    MovieCategory(
        "Action",
        listOf(
            "John Wick" to "2014", "Mad Max: Fury Road" to "2015", "Mission: Impossible - Fallout" to "2018",
            "Die Hard" to "1988", "The Raid" to "2011", "Top Gun: Maverick" to "2022", "Extraction" to "2020",
            "Casino Royale" to "2006", "Skyfall" to "2012", "Edge of Tomorrow" to "2014", "Baby Driver" to "2017",
        ),
    ),
    MovieCategory(
        "Thriller",
        listOf(
            "Se7en" to "1995", "The Silence of the Lambs" to "1991", "Gone Girl" to "2014",
            "Shutter Island" to "2010", "Prisoners" to "2013", "Memento" to "2000", "Zodiac" to "2007",
            "Drishyam" to "2015", "Searching" to "2018", "Nightcrawler" to "2014", "The Sixth Sense" to "1999",
        ),
    ),
    MovieCategory(
        "Comedy",
        listOf(
            "The Hangover" to "2009", "Superbad" to "2007", "Home Alone" to "1990", "Dumb and Dumber" to "1994",
            "Bridesmaids" to "2011", "21 Jump Street" to "2012", "Hera Pheri" to "2000",
            "Golmaal: Fun Unlimited" to "2006", "Munna Bhai M.B.B.S." to "2003", "Andaz Apna Apna" to "1994",
            "Deadpool" to "2016", "Paddington 2" to "2017",
        ),
    ),
    MovieCategory(
        "Drama",
        listOf(
            "The Green Mile" to "1999", "Good Will Hunting" to "1997", "A Beautiful Mind" to "2001",
            "The Pursuit of Happyness" to "2006", "Taare Zameen Par" to "2007", "Swades" to "2004",
            "Black" to "2005", "Schindler's List" to "1993", "12 Years a Slave" to "2013",
            "Moneyball" to "2011", "Oppenheimer" to "2023", "Manchester by the Sea" to "2016",
        ),
    ),
    MovieCategory(
        "Romance",
        listOf(
            "Titanic" to "1997", "The Notebook" to "2004", "La La Land" to "2016", "Before Sunrise" to "1995",
            "Jab We Met" to "2007", "Veer-Zaara" to "2004", "Dilwale Dulhania Le Jayenge" to "1995",
            "Yeh Jawaani Hai Deewani" to "2013", "Eternal Sunshine of the Spotless Mind" to "2004",
            "Pride & Prejudice" to "2005", "Call Me by Your Name" to "2017", "Aashiqui 2" to "2013",
        ),
    ),
    MovieCategory(
        "Sci-Fi",
        listOf(
            "Blade Runner 2049" to "2017", "Arrival" to "2016", "Dune" to "2021", "Dune: Part Two" to "2024",
            "Ex Machina" to "2014", "The Martian" to "2015", "Gravity" to "2013", "Avatar" to "2009",
            "Back to the Future" to "1985", "Koi... Mil Gaya" to "2003", "Tenet" to "2020",
        ),
    ),
    MovieCategory(
        "Horror",
        listOf(
            "The Conjuring" to "2013", "Hereditary" to "2018", "Get Out" to "2017", "A Quiet Place" to "2018",
            "Insidious" to "2010", "It" to "2017", "The Exorcist" to "1973", "Stree" to "2018",
            "Tumbbad" to "2018", "Bhool Bhulaiyaa" to "2007", "Smile" to "2022", "Talk to Me" to "2022",
        ),
    ),
    MovieCategory(
        "Animation",
        listOf(
            "Spider-Man: Into the Spider-Verse" to "2018", "Coco" to "2017", "Inside Out" to "2015",
            "Toy Story" to "1995", "Up" to "2009", "Spirited Away" to "2001", "Your Name." to "2016",
            "The Lion King" to "1994", "Finding Nemo" to "2003", "Kung Fu Panda" to "2008",
            "Zootopia" to "2016", "Soul" to "2020",
        ),
    ),
)

/**
 * Movie data. Posters, IMDb ratings and plots come from OMDb (free key). Where-to-watch and rent/buy
 * prices come from Watchmode (free key, optional). Neither service provides rental time limits.
 */
object Movies {
    private val listCache = HashMap<String, List<Movie>>()
    private val availCache = HashMap<String, Availability>()

    private fun clean(s: String?): String? = s?.takeIf { it.isNotBlank() && it != "N/A" && it != "null" }

    private fun parse(raw: String): Movie? {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val id = clean(o.optString("imdbID")) ?: return null
        val minutes = clean(o.optString("Runtime"))?.filter { it.isDigit() }?.toIntOrNull()
        return Movie(
            imdbId = id,
            title = o.optString("Title"),
            year = o.optString("Year"),
            poster = clean(o.optString("Poster")),
            plot = clean(o.optString("Plot")).orEmpty(),
            imdb = clean(o.optString("imdbRating")),
            runtime = if (minutes != null && minutes > 0) "${minutes / 60}h ${minutes % 60}m" else "",
            genres = clean(o.optString("Genre")).orEmpty(),
        )
    }

    /** Looks a movie up by title and year. Results are stored on the TV so each movie is fetched only once. */
    private suspend fun lookup(prefs: SharedPreferences, key: String, title: String, year: String): Movie? {
        val cacheKey = "t:$title|$year"
        prefs.getString(cacheKey, null)?.let { cached -> parse(cached)?.let { return it } }

        val enc = URLEncoder.encode(title, "UTF-8")
        val base = "https://www.omdbapi.com/?apikey=$key"
        var json: JSONObject = httpGetJson("$base&t=$enc&y=$year&type=movie&plot=short") ?: return null
        if (json.optString("Response") != "True") {
            // Exact title not found: take the first search hit instead.
            val hit = httpGetJson("$base&s=$enc&y=$year&type=movie")
                ?.optJSONArray("Search")?.optJSONObject(0)?.optString("imdbID").orEmpty()
            if (!hit.startsWith("tt")) return null
            json = httpGetJson("$base&i=$hit&plot=short") ?: return null
            if (json.optString("Response") != "True") return null
        }
        val raw = json.toString()
        prefs.edit().putString(cacheKey, raw).apply()
        return parse(raw)
    }

    suspend fun list(context: Context, key: String, cat: MovieCategory): List<Movie>? {
        listCache[cat.name]?.let { return it }
        val prefs = context.getSharedPreferences("cleantv_movies", Context.MODE_PRIVATE)
        val movies = coroutineScope {
            cat.titles.map { (title, year) -> async { lookup(prefs, key, title, year) } }.awaitAll()
        }.filterNotNull().distinctBy { it.imdbId }
        if (movies.isEmpty()) return null
        listCache[cat.name] = movies
        return movies
    }

    private fun addPrice(map: MutableMap<String, Double>, name: String, price: Double) {
        val p = if (price.isNaN() || price <= 0.0) -1.0 else price
        val old = map[name]
        map[name] = when {
            old == null -> p
            old < 0.0 -> p
            p < 0.0 -> old
            else -> minOf(old, p)
        }
    }

    private fun label(name: String, price: Double): String =
        if (price > 0.0) "$name ₹${price.toInt()}" else name

    /** Where a movie can be streamed, rented or bought in [Config.REGION], via Watchmode. */
    suspend fun availability(watchKey: String, imdbId: String): Availability? {
        availCache[imdbId]?.let { return it }
        val body = httpGet(
            "https://api.watchmode.com/v1/title/$imdbId/sources/?apiKey=$watchKey&regions=${Config.REGION}"
        ) ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null

        val stream = LinkedHashSet<String>()
        val rent = LinkedHashMap<String, Double>()
        val buy = LinkedHashMap<String, Double>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val price = o.optDouble("price", Double.NaN)
            when (o.optString("type")) {
                "sub", "free" -> stream.add(name)
                "rent" -> addPrice(rent, name, price)
                "buy" -> addPrice(buy, name, price)
            }
        }
        val result = Availability(
            stream = stream.toList(),
            rent = rent.map { (n, p) -> label(n, p) },
            buy = buy.map { (n, p) -> label(n, p) },
        )
        availCache[imdbId] = result
        return result
    }
}
