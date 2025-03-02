package com.michaldrabik.ui_my_movies.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ReloadData
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType.POSTER
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_movies.main.FollowedMoviesUiState
import com.michaldrabik.ui_my_movies.watchlist.cases.WatchlistLoadMoviesCase
import com.michaldrabik.ui_my_movies.watchlist.cases.WatchlistRatingsCase
import com.michaldrabik.ui_my_movies.watchlist.cases.WatchlistSortOrderCase
import com.michaldrabik.ui_my_movies.watchlist.recycler.WatchlistListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.michaldrabik.ui_base.events.Event as EventSync

@HiltViewModel
class WatchlistViewModel @Inject constructor(
  private val sortOrderCase: WatchlistSortOrderCase,
  private val ratingsCase: WatchlistRatingsCase,
  private val loadMoviesCase: WatchlistLoadMoviesCase,
  private val imagesProvider: MovieImagesProvider,
  private val eventsManager: EventsManager,
) : ViewModel() {

  private var loadItemsJob: Job? = null

  private val itemsState = MutableStateFlow<List<WatchlistListItem>>(emptyList())
  private val sortOrderState = MutableStateFlow<Event<Pair<SortOrder, SortType>>?>(null)
  private val scrollState = MutableStateFlow<Event<Boolean>?>(null)

  private var searchQuery: String? = null

  init {
    viewModelScope.launch { eventsManager.events.collect { onEvent(it) } }
  }

  fun onParentState(state: FollowedMoviesUiState) {
    when {
      this.searchQuery != state.searchQuery -> {
        this.searchQuery = state.searchQuery
        loadMovies(resetScroll = state.searchQuery.isNullOrBlank())
      }
    }
  }

  fun loadMovies(resetScroll: Boolean = false) {
    loadItemsJob?.cancel()
    loadItemsJob = viewModelScope.launch {
      val dateFormat = loadMoviesCase.loadDateFormat()
      val items = loadMoviesCase.loadMovies(searchQuery ?: "")
        .map {
          val image = imagesProvider.findCachedImage(it.first, POSTER)
          WatchlistListItem(it.first, image, false, it.second, null, dateFormat)
        }
      itemsState.value = items
      scrollState.value = Event(resetScroll)
      loadRatings(items, resetScroll)
    }
  }

  private fun loadRatings(items: List<WatchlistListItem>, resetScroll: Boolean) {
    if (items.isEmpty()) return
    viewModelScope.launch {
      try {
        val listItems = ratingsCase.loadRatings(items)
        itemsState.value = listItems
        scrollState.value = Event(resetScroll)
      } catch (error: Throwable) {
        Logger.record(error, "Source" to "WatchlistViewModel::loadRatings()")
      }
    }
  }

  fun loadSortOrder() {
    viewModelScope.launch {
      val sortOrder = sortOrderCase.loadSortOrder()
      sortOrderState.value = Event(sortOrder)
    }
  }

  fun setSortOrder(sortOrder: SortOrder, sortType: SortType) {
    viewModelScope.launch {
      sortOrderCase.setSortOrder(sortOrder, sortType)
      loadMovies(resetScroll = true)
    }
  }

  fun loadMissingImage(item: WatchlistListItem, force: Boolean) {
    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = imagesProvider.loadRemoteImage(item.movie, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)))
      }
    }
  }

  fun loadMissingTranslation(item: WatchlistListItem) {
    if (item.translation != null || loadMoviesCase.language == DEFAULT_LANGUAGE) return
    viewModelScope.launch {
      try {
        val translation = loadMoviesCase.loadTranslation(item.movie, false)
        updateItem(item.copy(translation = translation))
      } catch (error: Throwable) {
        Logger.record(error, "Source" to "WatchlistViewModel::loadMissingTranslation()")
      }
    }
  }

  private fun updateItem(new: WatchlistListItem) {
    val currentItems = uiState.value.items.toMutableList()
    currentItems.findReplace(new) { it isSameAs new }
    itemsState.value = currentItems
  }

  private fun onEvent(event: EventSync) =
    when (event) {
      is TraktSyncSuccess -> loadMovies()
      is TraktSyncError -> loadMovies()
      is ReloadData -> loadMovies()
      else -> Unit
    }

  val uiState = combine(
    itemsState,
    sortOrderState,
    scrollState
  ) { itemsState, sortOrderState, scrollState ->
    WatchlistUiState(
      items = itemsState,
      sortOrder = sortOrderState,
      resetScroll = scrollState
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = WatchlistUiState()
  )
}
