package com.michaldrabik.ui_comments.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_comments.R
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_navigation.java.NavigationArgs.ACTION_NEW_COMMENT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class PostCommentViewModel @Inject constructor(
  private val commentsRepository: CommentsRepository,
) : ViewModel(), ChannelsDelegate by DefaultChannelsDelegate() {

  private val loadingState = MutableStateFlow(false)
  private val successState = MutableStateFlow<Event<Pair<String, Comment>>?>(null)

  fun postShowComment(showId: IdTrakt, commentText: String, isSpoiler: Boolean) {
    if (!isValid(commentText)) return
    viewModelScope.launch {
      try {
        loadingState.value = true
        val show = Show.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = showId))
        val comment = commentsRepository
          .postComment(show, commentText, isSpoiler)
          .copy(isMe = true, isSignedIn = true)
        successState.value = Event(Pair(ACTION_NEW_COMMENT, comment))
      } catch (error: Throwable) {
        handleError(error)
        rethrowCancellation(error)
      }
    }
  }

  fun postMovieComment(movieId: IdTrakt, commentText: String, isSpoiler: Boolean) {
    if (!isValid(commentText)) return
    viewModelScope.launch {
      try {
        loadingState.value = true
        val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = movieId))
        val comment = commentsRepository
          .postComment(movie, commentText, isSpoiler)
          .copy(isMe = true, isSignedIn = true)
        successState.value = Event(Pair(ACTION_NEW_COMMENT, comment))
      } catch (error: Throwable) {
        handleError(error)
        rethrowCancellation(error)
      }
    }
  }

  fun postEpisodeComment(episodeId: IdTrakt, commentText: String, isSpoiler: Boolean) {
    if (!isValid(commentText)) return
    viewModelScope.launch {
      try {
        loadingState.value = true
        val episode = Episode.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = episodeId))
        val comment = commentsRepository
          .postComment(episode, commentText, isSpoiler)
          .copy(isMe = true, isSignedIn = true)
        successState.value = Event(Pair(ACTION_NEW_COMMENT, comment))
      } catch (error: Throwable) {
        handleError(error)
        rethrowCancellation(error)
      }
    }
  }

  fun postReply(commentId: IdTrakt, commentText: String, isSpoiler: Boolean) {
    if (!isValid(commentText)) return
    viewModelScope.launch {
      try {
        loadingState.value = true
        val comment = commentsRepository
          .postReply(commentId.id, commentText, isSpoiler)
          .copy(isMe = true, isSignedIn = true)
        successState.value = Event(Pair(ACTION_NEW_COMMENT, comment))
      } catch (error: Throwable) {
        handleError(error)
        rethrowCancellation(error)
      }
    }
  }

  private fun isValid(commentText: String) = commentText
    .trim().split(" ")
    .filter { !it.startsWith("@") }
    .count { it.length > 1 } >= 5

  private suspend fun handleError(error: Throwable) {
    if (error is HttpException && error.code() == 422) {
      messageChannel.send(MessageEvent.Error(R.string.errorCommentFormat))
    } else {
      messageChannel.send(MessageEvent.Error(R.string.errorGeneral))
    }
    loadingState.value = false
  }

  val uiState = combine(
    loadingState,
    successState
  ) { loadingState, successState ->
    PostCommentUiState(
      isLoading = loadingState,
      isSuccess = successState
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = PostCommentUiState()
  )
}
