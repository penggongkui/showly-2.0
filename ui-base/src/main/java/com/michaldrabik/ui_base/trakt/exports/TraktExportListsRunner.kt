package com.michaldrabik.ui_base.trakt.exports

import com.michaldrabik.common.Mode
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.TraktAuthToken
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncRunner
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktExportListsRunner @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val settingsRepository: SettingsRepository,
  userTraktManager: UserTraktManager
) : TraktSyncRunner(userTraktManager) {

  override suspend fun run(): Int {
    Timber.d("Initialized.")
    isRunning = true

    val authToken = checkAuthorization()
    runExport(authToken)

    isRunning = false
    Timber.d("Finished with success.")
    return 0
  }

  private suspend fun runExport(authToken: TraktAuthToken) {
    try {
      exportLists(authToken)
    } catch (error: Throwable) {
      if (retryCount < MAX_RETRY_COUNT) {
        Timber.w("exportLists failed. Will retry in $RETRY_DELAY_MS ms... $error")
        retryCount += 1
        delay(RETRY_DELAY_MS)
        runExport(authToken)
      } else {
        isRunning = false
        throw error
      }
    }
  }

  private suspend fun exportLists(token: TraktAuthToken) {
    Timber.d("Exporting lists...")
    val moviesEnabled = settingsRepository.isMoviesEnabled

    val localLists = localSource.customLists.getAll()
      .map { mappers.customList.fromDatabase(it) }
    val remoteLists = remoteSource.trakt.fetchSyncLists(token.token)
      .map { mappers.customList.fromNetwork(it) }

    localLists.forEach { localList ->
      Timber.d("Processing ${localList.name}...")
      if (remoteLists.none { it.idTrakt == localList.idTrakt }) {
        Timber.d("Not found in Trakt. Creating and uploading items...")
        val listNet = remoteSource.trakt.postCreateList(token.token, localList.name, localList.description)
        Timber.d("List created in Trakt.")

        val list = mappers.customList.fromNetwork(listNet)
        val listDb = mappers.customList.toDatabase(list).copy(id = localList.id)

        localSource.customLists.update(listOf(listDb))
        delay(1000)

        val localItems = localSource.customListsItems.getItemsById(localList.id)
        if (localItems.isNotEmpty()) {
          val showsIds = localItems.filter { it.type == Mode.SHOWS.type }.map { it.idTrakt }
          val moviesIds = localItems.filter { it.type == Mode.MOVIES.type }.map { it.idTrakt }
          remoteSource.trakt.postAddListItems(token.token, listNet.ids.trakt, showsIds, moviesIds)
          Timber.d("Items added into Trakt list.")
          delay(1000)
        }
      } else {
        Timber.d("Found in Trakt.")
        val remoteList = remoteLists.first { it.idTrakt == localList.idTrakt }
        if (!localList.updatedAt.isEqual(remoteList.updatedAt)) {
          Timber.d("Timestamps are different.")
          if (localList.updatedAt.isAfter(remoteList.updatedAt)) {
            Timber.d("Local list timestamp is newer.")
            if (localList.name != remoteList.name || localList.description != remoteList.description) {
              Timber.d("Name or description are different. Updating...")
              val updateList = mappers.customList.toNetwork(localList)
              val resultList = remoteSource.trakt.postUpdateList(token.token, updateList)
              val listDb = mappers.customList.fromNetwork(resultList).copy(id = localList.id)
              localSource.customLists.update(listOf(mappers.customList.toDatabase(listDb)))
              delay(1000)
            }
          }

          Timber.d("Processing list items...")
          val listTraktId = localList.idTrakt!!
          val remoteItems = remoteSource.trakt.fetchSyncListItems(token.token, listTraktId, moviesEnabled)
            .filter { it.movie != null || it.show != null }
          val localItems = localSource.customListsItems.getItemsById(localList.id)
            .filter { localItem ->
              remoteItems.none {
                it.getTraktId() == localItem.idTrakt && it.getType() == localItem.type
              }
            }

          if (localItems.isNotEmpty()) {
            Timber.d("${localItems.size} to be exported...")

            val showsIds = localItems.filter { it.type == Mode.SHOWS.type }.map { it.idTrakt }
            val moviesIds = localItems.filter { it.type == Mode.MOVIES.type }.map { it.idTrakt }
            remoteSource.trakt.postAddListItems(token.token, listTraktId, showsIds, moviesIds)

            Timber.d("Exported!")
            delay(1000)
          }

          updateListTimestamp(token, localList.id, listTraktId)
        }
      }
    }
  }

  private suspend fun updateListTimestamp(token: TraktAuthToken, listId: Long, listTraktId: Long) {
    try {
      Timber.d("Updating timestamp...")
      val list = remoteSource.trakt.fetchSyncList(token.token, listTraktId)
        .run { mappers.customList.fromNetwork(this) }
      localSource.customLists.updateTimestamp(listId, list.updatedAt.toMillis())
      Timber.d("Local list timestamp updated.")
    } catch (error: Throwable) {
      Timber.w(error)
      // Skip timestamp update in case of failure.
    }
  }
}
