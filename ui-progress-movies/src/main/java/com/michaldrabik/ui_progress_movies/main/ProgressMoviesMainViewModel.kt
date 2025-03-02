package com.michaldrabik.ui_progress_movies.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.events.Event
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.trakt.TraktSyncStatusProvider
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_model.CalendarMode
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_progress_movies.main.cases.ProgressMoviesMainCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgressMoviesMainViewModel @Inject constructor(
  private val moviesCase: ProgressMoviesMainCase,
  private val syncStatusProvider: TraktSyncStatusProvider,
  private val eventsManager: EventsManager,
) : ViewModel() {

  private val timestampState = MutableStateFlow<Long?>(null)
  private val searchQueryState = MutableStateFlow<String?>(null)
  private val calendarModeState = MutableStateFlow<CalendarMode?>(null)
  private val syncingState = MutableStateFlow(false)

  private var calendarMode = CalendarMode.PRESENT_FUTURE

  init {
    with(viewModelScope) {
      launch { syncStatusProvider.status.collect { syncingState.value = it } }
      launch { eventsManager.events.collect { onEvent(it) } }
    }
  }

  fun loadProgress() {
    viewModelScope.launch {
      timestampState.value = System.currentTimeMillis()
      calendarModeState.value = calendarMode
    }
  }

  fun onSearchQuery(searchQuery: String) {
    searchQueryState.value = searchQuery
  }

  fun toggleCalendarMode() {
    calendarMode = when (calendarMode) {
      CalendarMode.PRESENT_FUTURE -> CalendarMode.RECENTS
      CalendarMode.RECENTS -> CalendarMode.PRESENT_FUTURE
    }
    calendarModeState.value = calendarMode
  }

  fun setWatchedMovie(movie: Movie) {
    viewModelScope.launch {
      moviesCase.addToMyMovies(movie)
      timestampState.value = System.currentTimeMillis()
    }
  }

  private fun onEvent(event: Event) {
    if (event in arrayOf(TraktSyncError, TraktSyncSuccess)) {
      loadProgress()
    }
  }

  val uiState = combine(
    timestampState,
    searchQueryState,
    calendarModeState,
    syncingState
  ) { s1, s2, s3, s4 ->
    ProgressMoviesMainUiState(
      timestamp = s1,
      searchQuery = s2,
      calendarMode = s3,
      isSyncing = s4
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ProgressMoviesMainUiState()
  )
}
