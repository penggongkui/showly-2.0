package com.michaldrabik.repository.shows.ratings

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.TraktRating
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowsRatingsRepository @Inject constructor(
  val external: ShowsExternalRatingsRepository,
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers
) {

  companion object {
    private const val TYPE_SHOW = "show"
    private const val TYPE_EPISODE = "episode"
    private const val TYPE_SEASON = "season"
    private const val CHUNK_SIZE = 250
  }

  suspend fun preloadRatings(token: String) = supervisorScope {

    suspend fun preloadShowsRatings(token: String) {
      val ratings = remoteSource.trakt.fetchShowsRatings(token)
      val entities = ratings
        .filter { it.rated_at != null && it.show.ids.trakt != null }
        .map { mappers.userRatings.toDatabaseShow(it) }
      localSource.ratings.replaceAll(entities, TYPE_SHOW)
    }

    suspend fun preloadEpisodesRatings(token: String) {
      val ratings = remoteSource.trakt.fetchEpisodesRatings(token)
      val entities = ratings
        .filter { it.rated_at != null && it.episode.ids.trakt != null }
        .map { mappers.userRatings.toDatabaseEpisode(it) }
      localSource.ratings.replaceAll(entities, TYPE_EPISODE)
    }

    suspend fun preloadSeasonsRatings(token: String) {
      val ratings = remoteSource.trakt.fetchSeasonsRatings(token)
      val entities = ratings
        .filter { it.rated_at != null && it.season.ids.trakt != null }
        .map { mappers.userRatings.toDatabaseSeason(it) }
      localSource.ratings.replaceAll(entities, TYPE_SEASON)
    }

    val errorHandler = CoroutineExceptionHandler { _, _ -> Timber.e("Failed to preload some of ratings.") }
    launch(errorHandler) { preloadShowsRatings(token) }
    launch(errorHandler) { preloadEpisodesRatings(token) }
    launch(errorHandler) { preloadSeasonsRatings(token) }
  }

  suspend fun loadShowsRatings(): List<TraktRating> {
    val ratings = localSource.ratings.getAllByType(TYPE_SHOW)
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRatings(shows: List<Show>): List<TraktRating> {
    val ratings = mutableListOf<Rating>()
    shows.chunked(CHUNK_SIZE).forEach { chunk ->
      val items = localSource.ratings.getAllByType(chunk.map { it.traktId }, TYPE_SHOW)
      ratings.addAll(items)
    }
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRatingsSeasons(seasons: List<Season>): List<TraktRating> {
    val ratings = mutableListOf<Rating>()
    seasons.chunked(CHUNK_SIZE).forEach { chunk ->
      val items = localSource.ratings.getAllByType(chunk.map { it.ids.trakt.id }, TYPE_SEASON)
      ratings.addAll(items)
    }
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRating(episode: Episode): TraktRating? {
    val rating = localSource.ratings.getAllByType(listOf(episode.ids.trakt.id), TYPE_EPISODE)
    return rating.firstOrNull()?.let {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRating(season: Season): TraktRating? {
    val rating = localSource.ratings.getAllByType(listOf(season.ids.trakt.id), TYPE_SEASON)
    return rating.firstOrNull()?.let {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun addRating(token: String, show: Show, rating: Int) {
    remoteSource.trakt.postRating(
      token,
      mappers.show.toNetwork(show),
      rating
    )
    val entity = mappers.userRatings.toDatabaseShow(show, rating, nowUtc())
    localSource.ratings.replace(entity)
  }

  suspend fun addRating(token: String, episode: Episode, rating: Int) {
    remoteSource.trakt.postRating(
      token,
      mappers.episode.toNetwork(episode),
      rating
    )
    val entity = mappers.userRatings.toDatabaseEpisode(episode, rating, nowUtc())
    localSource.ratings.replace(entity)
  }

  suspend fun addRating(token: String, season: Season, rating: Int) {
    remoteSource.trakt.postRating(
      token,
      mappers.season.toNetwork(season),
      rating
    )
    val entity = mappers.userRatings.toDatabaseSeason(season, rating, nowUtc())
    localSource.ratings.replace(entity)
  }

  suspend fun deleteRating(token: String, show: Show) {
    remoteSource.trakt.deleteRating(
      token,
      mappers.show.toNetwork(show)
    )
    localSource.ratings.deleteByType(show.traktId, TYPE_SHOW)
  }

  suspend fun deleteRating(token: String, episode: Episode) {
    remoteSource.trakt.deleteRating(
      token,
      mappers.episode.toNetwork(episode)
    )
    localSource.ratings.deleteByType(episode.ids.trakt.id, TYPE_EPISODE)
  }

  suspend fun deleteRating(token: String, season: Season) {
    remoteSource.trakt.deleteRating(
      token,
      mappers.season.toNetwork(season)
    )
    localSource.ratings.deleteByType(season.ids.trakt.id, TYPE_SEASON)
  }

  suspend fun clear() {
    with(localSource) {
      transactions.withTransaction {
        ratings.deleteAllByType(TYPE_EPISODE)
        ratings.deleteAllByType(TYPE_SEASON)
        ratings.deleteAllByType(TYPE_SHOW)
      }
    }
  }
}
