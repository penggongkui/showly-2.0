package com.michaldrabik.ui_show.cases

import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.network.NetworkStatusProvider
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_show.episodes.EpisodeListItem
import com.michaldrabik.ui_show.helpers.SeasonsBundle
import com.michaldrabik.ui_show.seasons.SeasonListItem
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@ViewModelScoped
class ShowDetailsEpisodesCase @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val showsRepository: ShowsRepository,
  private val settingsRepository: SettingsRepository,
  private val ratingsRepository: RatingsRepository,
  private val translationsRepository: TranslationsRepository,
  private val episodesManager: EpisodesManager,
  private val userManager: UserTraktManager,
  private val dateFormatProvider: DateFormatProvider,
  private val networkStatusProvider: NetworkStatusProvider
) {

  suspend fun loadNextEpisode(traktId: IdTrakt): Episode? {
    val episode = remoteSource.trakt.fetchNextEpisode(traktId.id) ?: return null
    return mappers.episode.fromNetwork(episode)
  }

  suspend fun loadSeasons(show: Show): SeasonsBundle =
    coroutineScope {
      val showSpecialSeasons = settingsRepository.load().specialSeasonsEnabled
      try {
        if (!networkStatusProvider.isOnline()) {
          loadLocalSeasons(show, showSpecialSeasons)
        }

        val remoteSeasons = remoteSource.trakt.fetchSeasons(show.traktId)
          .map { mappers.season.fromNetwork(it) }
          .filter { it.episodes.isNotEmpty() }
          .filter { if (!showSpecialSeasons) !it.isSpecial() else true }

        val isFollowed = showsRepository.myShows.load(show.ids.trakt) != null
        if (isFollowed) {
          episodesManager.invalidateSeasons(show, remoteSeasons)
        }

        val seasonsItems = mapToSeasonItems(remoteSeasons, show)
        SeasonsBundle(seasonsItems, isLocal = false)
      } catch (error: Throwable) {
        loadLocalSeasons(show, showSpecialSeasons)
      }
    }

  private suspend fun loadLocalSeasons(show: Show, showSpecials: Boolean): SeasonsBundle {
    val localEpisodes = localSource.episodes.getAllByShowId(show.traktId)
    val localSeasons = localSource.seasons.getAllByShowId(show.traktId).map { season ->
      val seasonEpisodes = localEpisodes.filter { ep -> ep.idSeason == season.idTrakt }
      mappers.season.fromDatabase(season, seasonEpisodes)
    }
      .filter { it.episodes.isNotEmpty() }
      .filter { if (!showSpecials) !it.isSpecial() else true }

    val seasonsItems = mapToSeasonItems(localSeasons, show)
    return SeasonsBundle(seasonsItems, isLocal = true)
  }

  private suspend fun mapToSeasonItems(remoteSeasons: List<Season>, show: Show) = coroutineScope {
    val isSignedIn = userManager.isAuthorized()
    val format = dateFormatProvider.loadFullHourFormat()
    val seasonsRatings = ratingsRepository.shows.loadRatingsSeasons(remoteSeasons)
    remoteSeasons
      .map {
        val userRating = RatingState(
          userRating = seasonsRatings.find { rating -> rating.idTrakt == it.ids.trakt },
          rateAllowed = isSignedIn,
        )
        val episodes = it.episodes.map { episode ->
          async {
            val rating = ratingsRepository.shows.loadRating(episode)
            val translation = translationsRepository.loadTranslation(episode, show.ids.trakt, onlyLocal = true)
            EpisodeListItem(episode, it, false, translation, rating, format, isAnime = show.isAnime)
          }
        }.awaitAll()
        SeasonListItem(
          show = show,
          season = it,
          episodes = episodes,
          isWatched = false,
          userRating = userRating,
          updatedAt = nowUtcMillis()
        )
      }
      .sortedByDescending { it.season.number }
  }
}
