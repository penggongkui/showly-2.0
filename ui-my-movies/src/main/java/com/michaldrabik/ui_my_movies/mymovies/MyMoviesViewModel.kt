package com.michaldrabik.ui_my_movies.mymovies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ReloadData
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.ImageType.POSTER
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.MyMoviesSection.ALL
import com.michaldrabik.ui_model.MyMoviesSection.RECENTS
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_movies.main.FollowedMoviesUiState
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesLoadCase
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesRatingsCase
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesSortingCase
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.ALL_MOVIES_ITEM
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.RECENT_MOVIE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.michaldrabik.ui_base.events.Event as EventSync

@HiltViewModel
class MyMoviesViewModel @Inject constructor(
  private val loadMoviesCase: MyMoviesLoadCase,
  private val ratingsCase: MyMoviesRatingsCase,
  private val sortingCase: MyMoviesSortingCase,
  private val eventsManager: EventsManager,
) : ViewModel() {

  private var loadItemsJob: Job? = null

  private val itemsState = MutableStateFlow<List<MyMoviesItem>?>(null)
  private val itemsUpdateState = MutableStateFlow<Event<Boolean>?>(null)

  private var searchQuery: String? = null

  init {
    viewModelScope.launch { eventsManager.events.collect { onEvent(it) } }
  }

  fun onParentState(state: FollowedMoviesUiState) {
    when {
      this.searchQuery != state.searchQuery -> {
        this.searchQuery = state.searchQuery
        loadMovies(notifyListsUpdate = state.searchQuery.isNullOrBlank())
      }
    }
  }

  fun loadMovies(notifyListsUpdate: Boolean = false) {
    loadItemsJob?.cancel()
    loadItemsJob = viewModelScope.launch {
      val settings = loadMoviesCase.loadSettings()
      val dateFormat = loadMoviesCase.loadDateFormat()
      val movies = loadMoviesCase.loadAll().map { toListItemAsync(ALL_MOVIES_ITEM, it, dateFormat) }.awaitAll()
      val sortOrder = sortingCase.loadSortOrder()

      val allMovies = loadMoviesCase.filterSectionMovies(movies, sortOrder, searchQuery)
      val recentMovies = if (settings.myMoviesRecentIsEnabled) {
        loadMoviesCase.loadRecentMovies().map { toListItemAsync(RECENT_MOVIE, it, dateFormat, ImageType.FANART) }.awaitAll()
      } else {
        emptyList()
      }

      val isNotSearching = searchQuery.isNullOrBlank()
      val listItems = mutableListOf<MyMoviesItem>()
      listItems.run {
        if (isNotSearching && recentMovies.isNotEmpty()) {
          add(MyMoviesItem.createHeader(RECENTS, recentMovies.count(), null))
          add(MyMoviesItem.createRecentsSection(recentMovies))
        }
        if (allMovies.isNotEmpty()) {
          add(MyMoviesItem.createHeader(ALL, allMovies.count(), sortOrder))
          addAll(allMovies)
        }
      }

      itemsState.value = listItems
      itemsUpdateState.value = Event(notifyListsUpdate)

      loadRatings(listItems, notifyListsUpdate)
    }
  }

  private fun loadRatings(items: MutableList<MyMoviesItem>, notifyListsUpdate: Boolean) {
    if (items.isEmpty()) return
    viewModelScope.launch {
      try {
        val listItems = ratingsCase.loadRatings(items)
        itemsState.value = listItems
        itemsUpdateState.value = Event(notifyListsUpdate)
      } catch (error: Throwable) {
        Logger.record(error, "Source" to "MyMoviesViewModel::loadRatings()")
      }
    }
  }

  fun setSortOrder(order: SortOrder, type: SortType) {
    viewModelScope.launch {
      sortingCase.setSortOrder(order, type)
      loadMovies(notifyListsUpdate = true)
    }
  }

  fun loadMissingImage(item: MyMoviesItem, force: Boolean) {
    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = loadMoviesCase.loadMissingImage(item.movie, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)))
      }
    }
  }

  fun loadMissingTranslation(item: MyMoviesItem) {
    if (item.translation != null || loadMoviesCase.language == DEFAULT_LANGUAGE) return
    viewModelScope.launch {
      try {
        val translation = loadMoviesCase.loadTranslation(item.movie, false)
        updateItem(item.copy(translation = translation))
      } catch (error: Throwable) {
        Logger.record(error, "Source" to "MyMoviesViewModel::loadMissingTranslation()")
      }
    }
  }

  private fun updateItem(new: MyMoviesItem) {
    val items = uiState.value.items?.toMutableList()
    items?.findReplace(new) { it isSameAs new }
    itemsState.value = items
  }

  private fun CoroutineScope.toListItemAsync(
    itemType: Type,
    movie: Movie,
    dateFormat: DateTimeFormatter,
    type: ImageType = POSTER,
  ) = async {
    val image = loadMoviesCase.findCachedImage(movie, type)
    val translation = loadMoviesCase.loadTranslation(movie, true)
    MyMoviesItem(itemType, null, null, null, movie, image, false, translation, null, dateFormat)
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
    itemsUpdateState
  ) { itemsState, itemsUpdateState ->
    MyMoviesUiState(
      items = itemsState,
      resetScroll = itemsUpdateState
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = MyMoviesUiState()
  )
}
