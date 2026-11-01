package com.cleanyourtv.launcher

import org.json.JSONObject

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val poster: String?,
    val overview: String,
    val rating: Double,
)

data class MovieInfo(
    val imdb: String?,
    val runtimeMin: Int?,
    val genres: String,
    val stream: List<String>,
    val rentBuy: List<String>,
)

data class MovieCategory(
    val name: String,
    val genreId: Int? = null,
    val language: String? = null,
    val trending: Boolean = false,
)

val MOVIE_CATEGORIES: List<MovieCategory> = listOf(
    MovieCategory("Trending", trending = true),
    MovieCategory("Bollywood", language = "hi"),
    MovieCategory("Action", genreId = 28),
    MovieCategory("Thriller", genreId = 53),
    MovieCategory("Comedy", genreId = 35),
    MovieCategory("Drama", genreId = 18),
    MovieCategory("Romance", genreId = 10749),
    MovieCategory("Sci-Fi", genreId = 878),
    MovieCategory("Crime", genreId = 80),
    MovieCategory("Horror", genreId = 27),
    MovieCategory("Animation", genreId = 16),
    MovieCategory("Family", genreId = 10751),
)

fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w342$it" }

/**
 * Movie data: lists and "where to watch" come from TMDB (free key), IMDb ratings from OMDb (free key).
 * TMDB lists which apps stream, rent or sell a title, but not the rental price or its time limit.
 */
object Movies {
    private val listCache = HashMap<String, List<Movie>>()
    private val infoCache = HashMap<Int, MovieInfo>()

    /** Accepts either a v3 API key or a long v4 read-access token. */
    private suspend fun tmdb(key: String, pathAndQuery: String): JSONObject? {
        val base = "https://api.themoviedb.org/3$pathAndQuery"
        return if (key.length > 60) {
            httpGetJson(base, bearer = key)
        } else {
            val sep = if (pathAndQuery.contains("?")) "&" else "?"
            httpGetJson("$base${sep}api_key=$key")
        }
    }

    suspend fun list(key: String, cat: MovieCategory): List<Movie>? {
        listCache[cat.name]?.let { return it }
        val query = when {
            cat.trending -> "/trending/movie/week?language=en-US"
            cat.language != null ->
                "/discover/movie?with_original_language=${cat.language}&sort_by=popularity.desc" +
                    "&vote_count.gte=100&include_adult=false&region=${Config.REGION}"
            else ->
                "/discover/movie?with_genres=${cat.genreId}&sort_by=popularity.desc" +
                    "&vote_count.gte=300&include_adult=false&region=${Config.REGION}"
        }
        val json = tmdb(key, query) ?: return null
        val results = json.optJSONArray("results") ?: return null
        val out = ArrayList<Movie>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            out.add(
                Movie(
                    id = o.optInt("id"),
                    title = o.optString("title"),
                    year = o.optString("release_date").take(4),
                    poster = if (o.isNull("poster_path")) null else o.optString("poster_path"),
                    overview = o.optString("overview"),
                    rating = o.optDouble("vote_average", 0.0),
                )
            )
        }
        listCache[cat.name] = out
        return out
    }

    suspend fun info(tmdbKey: String, omdbKey: String, id: Int): MovieInfo? {
        infoCache[id]?.let { return it }
        val j = tmdb(tmdbKey, "/movie/$id?append_to_response=watch/providers,external_ids") ?: return null

        val region = j.optJSONObject("watch/providers")
            ?.optJSONObject("results")
            ?.optJSONObject(Config.REGION)

        fun names(kind: String): List<String> {
            val arr = region?.optJSONArray(kind) ?: return emptyList()
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i)?.optString("provider_name").orEmpty()
                if (n.isNotBlank()) list.add(n)
            }
            return list
        }

        val imdbId = j.optJSONObject("external_ids")?.optString("imdb_id").orEmpty()
        var imdb: String? = null
        if (omdbKey.isNotBlank() && imdbId.startsWith("tt")) {
            val r = httpGetJson("https://www.omdbapi.com/?i=$imdbId&apikey=$omdbKey")
                ?.optString("imdbRating").orEmpty()
            if (r.isNotBlank() && r != "N/A" && r != "null") imdb = r
        }

        val genres = j.optJSONArray("genres")
        val genreNames = ArrayList<String>()
        if (genres != null) {
            for (i in 0 until genres.length()) {
                val n = genres.optJSONObject(i)?.optString("name").orEmpty()
                if (n.isNotBlank()) genreNames.add(n)
            }
        }

        val info = MovieInfo(
            imdb = imdb,
            runtimeMin = j.optInt("runtime", 0).takeIf { it > 0 },
            genres = genreNames.joinToString(" · "),
            stream = (names("flatrate") + names("ads")).distinct(),
            rentBuy = (names("rent") + names("buy")).distinct(),
        )
        infoCache[id] = info
        return info
    }
}
