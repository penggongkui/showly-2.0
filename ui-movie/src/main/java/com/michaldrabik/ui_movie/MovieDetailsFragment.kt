package com.michaldrabik.ui_movie

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Typeface.BOLD
import android.graphics.Typeface.NORMAL
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.michaldrabik.common.Config
import com.michaldrabik.common.Config.IMAGE_FADE_DURATION_MS
import com.michaldrabik.common.Mode
import com.michaldrabik.ui_base.Analytics
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.AppCountry
import com.michaldrabik.ui_base.common.AppCountry.UNITED_STATES
import com.michaldrabik.ui_base.common.WidgetsProvider
import com.michaldrabik.ui_base.common.sheets.links.LinksBottomSheet
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet.Options.Operation
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet.Options.Type
import com.michaldrabik.ui_base.common.sheets.remove_trakt.RemoveTraktBottomSheet
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.addDivider
import com.michaldrabik.ui_base.utilities.extensions.capitalizeWords
import com.michaldrabik.ui_base.utilities.extensions.copyToClipboard
import com.michaldrabik.ui_base.utilities.extensions.crossfadeTo
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.requireLong
import com.michaldrabik.ui_base.utilities.extensions.screenHeight
import com.michaldrabik.ui_base.utilities.extensions.screenWidth
import com.michaldrabik.ui_base.utilities.extensions.setTextIfEmpty
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.trimWithSuffix
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.extensions.withFailListener
import com.michaldrabik.ui_base.utilities.extensions.withSuccessListener
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.Genre
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageFamily.MOVIE
import com.michaldrabik.ui_model.ImageStatus.UNAVAILABLE
import com.michaldrabik.ui_model.ImageType.FANART
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Person
import com.michaldrabik.ui_model.Person.Department
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.Ratings
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_movie.MovieDetailsUiState.StreamingsState
import com.michaldrabik.ui_movie.actors.ActorsAdapter
import com.michaldrabik.ui_movie.helpers.MovieLink
import com.michaldrabik.ui_movie.helpers.MovieLink.IMDB
import com.michaldrabik.ui_movie.helpers.MovieLink.METACRITIC
import com.michaldrabik.ui_movie.helpers.MovieLink.ROTTEN
import com.michaldrabik.ui_movie.helpers.MovieLink.TRAKT
import com.michaldrabik.ui_movie.related.RelatedListItem
import com.michaldrabik.ui_movie.related.RelatedMovieAdapter
import com.michaldrabik.ui_movie.views.AddToMoviesButton.State.ADD
import com.michaldrabik.ui_movie.views.AddToMoviesButton.State.IN_HIDDEN
import com.michaldrabik.ui_movie.views.AddToMoviesButton.State.IN_MY_MOVIES
import com.michaldrabik.ui_movie.views.AddToMoviesButton.State.IN_WATCHLIST
import com.michaldrabik.ui_navigation.java.NavigationArgs
import com.michaldrabik.ui_navigation.java.NavigationArgs.ACTION_NEW_COMMENT
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_COMMENT
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_COMMENT_ACTION
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_COMMENT_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_CUSTOM_IMAGE_CLEARED
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_FAMILY
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_REPLY_USER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_COMMENT
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_CUSTOM_IMAGE
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_MANAGE_LISTS
import com.michaldrabik.ui_people.details.PersonDetailsBottomSheet
import com.michaldrabik.ui_people.list.PeopleListBottomSheet
import com.michaldrabik.ui_streamings.recycler.StreamingAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_movie_details.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.util.Locale.ENGLISH
import java.util.Locale.ROOT

@SuppressLint("SetTextI18n", "DefaultLocale", "SourceLockedOrientationActivity")
@AndroidEntryPoint
class MovieDetailsFragment : BaseFragment<MovieDetailsViewModel>(R.layout.fragment_movie_details) {

  override val viewModel by viewModels<MovieDetailsViewModel>()
  override val navigationId = R.id.movieDetailsFragment

  private val movieId by lazy { IdTrakt(requireLong(ARG_MOVIE_ID)) }

  private var actorsAdapter: ActorsAdapter? = null
  private var streamingAdapter: StreamingAdapter? = null
  private var relatedAdapter: RelatedMovieAdapter? = null
  private var lastOpenedPerson: Person? = null

  private val imageHeight by lazy {
    if (resources.configuration.orientation == ORIENTATION_PORTRAIT) screenHeight()
    else screenWidth()
  }
  private val imageRatio by lazy { resources.getString(R.string.detailsImageRatio).toFloat() }
  private val imagePadded by lazy { resources.getBoolean(R.bool.detailsImagePadded) }

  private val animationEnterRight by lazy { AnimationUtils.loadAnimation(requireContext(), R.anim.anim_slide_in_from_right) }
  private val animationExitRight by lazy { AnimationUtils.loadAnimation(requireContext(), R.anim.anim_slide_out_from_right) }
  private val animationEnterLeft by lazy { AnimationUtils.loadAnimation(requireContext(), R.anim.anim_slide_in_from_left) }
  private val animationExitLeft by lazy { AnimationUtils.loadAnimation(requireContext(), R.anim.anim_slide_out_from_left) }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    requireActivity().requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
    setupView()
    setupStatusBar()
    setupActorsList()
    setupStreamingsList()
    setupRelatedList()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { renderSnack(it) } },
      doAfterLaunch = {
        if (!isInitialized) {
          viewModel.loadDetails(movieId)
          isInitialized = true
        }
        viewModel.loadPremium()
        lastOpenedPerson?.let { openPersonSheet(it) }
      }
    )
  }

  private fun setupView() {
    hideNavigation()
    movieDetailsImageGuideline.setGuidelineBegin((imageHeight * imageRatio).toInt())
    listOf(movieDetailsBackArrow, movieDetailsBackArrow2).onClick { requireActivity().onBackPressed() }
    movieDetailsImage.onClick {
      val bundle = bundleOf(
        ARG_MOVIE_ID to movieId.id,
        ARG_FAMILY to MOVIE,
        ARG_TYPE to FANART
      )
      navigateToSafe(R.id.actionMovieDetailsFragmentToArtGallery, bundle)
      Analytics.logMovieGalleryClick(movieId.id)
    }
    movieDetailsCommentsButton.onClick {
      movieDetailsCommentsView.clear()
      showCommentsView()
      viewModel.loadComments()
    }
    movieDetailsCommentsView.run {
      onRepliesClickListener = { viewModel.loadCommentReplies(it) }
      onReplyCommentClickListener = { openPostCommentSheet(comment = it) }
      onDeleteCommentClickListener = { openDeleteCommentDialog(it) }
      onPostCommentClickListener = { openPostCommentSheet() }
    }
    movieDetailsAddButton.run {
      isEnabled = false
      onAddMyMoviesClickListener = {
        viewModel.addFollowedMovie()
      }
      onAddWatchLaterClickListener = { viewModel.addWatchlistMovie() }
      onRemoveClickListener = { viewModel.removeFromFollowed() }
    }
    movieDetailsManageListsLabel.onClick { openListsDialog() }
    movieDetailsHideLabel.onClick { viewModel.addHiddenMovie() }
    movieDetailsTitle.onClick {
      requireContext().copyToClipboard(movieDetailsTitle.text.toString())
      showSnack(MessageEvent.Info(R.string.textCopiedToClipboard))
    }
    movieDetailsPremiumAd.onClick {
      navigateTo(R.id.actionMovieDetailsFragmentToPremium)
    }
  }

  private fun setupStatusBar() {
    movieDetailsBackArrow.doOnApplyWindowInsets { view, insets, _, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
      if (imagePadded) {
        movieDetailsMainLayout
          .updatePadding(top = inset)
      } else {
        (movieDetailsShareButton.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = inset)
      }
      arrayOf<View>(view, movieDetailsBackArrow2, movieDetailsCommentsView)
        .forEach { v ->
          (v.layoutParams as ViewGroup.MarginLayoutParams).updateMargins(top = inset)
        }
    }
  }

  private fun setupActorsList() {
    actorsAdapter = ActorsAdapter().apply {
      itemClickListener = { openPersonSheet(it) }
    }
    movieDetailsActorsRecycler.apply {
      setHasFixedSize(true)
      adapter = actorsAdapter
      layoutManager = LinearLayoutManager(requireContext(), HORIZONTAL, false)
      addDivider(R.drawable.divider_horizontal_list, HORIZONTAL)
    }
  }

  private fun setupStreamingsList() {
    streamingAdapter = StreamingAdapter()
    movieDetailsStreamingsRecycler.apply {
      setHasFixedSize(true)
      adapter = streamingAdapter
      layoutManager = LinearLayoutManager(requireContext(), HORIZONTAL, false)
      addDivider(R.drawable.divider_horizontal_list, HORIZONTAL)
    }
  }

  private fun setupRelatedList() {
    relatedAdapter = RelatedMovieAdapter(
      itemClickListener = {
        val bundle = Bundle().apply { putLong(ARG_MOVIE_ID, it.movie.ids.trakt.id) }
        navigateTo(R.id.actionMovieDetailsFragmentToSelf, bundle)
      },
      missingImageListener = { ids, force -> viewModel.loadMissingImage(ids, force) }
    )
    movieDetailsRelatedRecycler.apply {
      setHasFixedSize(true)
      adapter = relatedAdapter
      layoutManager = LinearLayoutManager(requireContext(), HORIZONTAL, false)
      addDivider(R.drawable.divider_horizontal_list, HORIZONTAL)
    }
  }

  private fun showCommentsView() {
    movieDetailsCommentsView.run {
      fadeIn(275)
      startAnimation(animationEnterRight)
    }
    movieDetailsMainLayout.run {
      fadeOut(200)
      startAnimation(animationExitRight)
    }
    movieDetailsBackArrow.crossfadeTo(movieDetailsBackArrow2)
  }

  private fun hideExtraView(view: View) {
    if (view.animation != null) return

    view.run {
      fadeOut(300)
      startAnimation(animationExitLeft)
    }
    movieDetailsMainLayout.run {
      fadeIn()
      startAnimation(animationEnterLeft)
    }
    movieDetailsBackArrow2.crossfadeTo(movieDetailsBackArrow)
  }

  private fun render(uiState: MovieDetailsUiState) {
    uiState.run {
      movie?.let { movie ->
        movieDetailsTitle.text = movie.title
        movieDetailsDescription.setTextIfEmpty(if (movie.overview.isNotBlank()) movie.overview else getString(R.string.textNoDescription))
        movieDetailsStatus.text = getString(movie.status.displayName)

        val releaseDate =
          when {
            movie.released != null -> String.format(ENGLISH, "%s", dateFormat?.format(movie.released)?.capitalizeWords())
            movie.year > 0 -> movie.year.toString()
            else -> ""
          }

        val country = if (movie.country.isNotBlank()) String.format(ENGLISH, "(%s)", movie.country) else ""
        movieDetailsExtraInfo.text = getString(
          R.string.textMovieExtraInfo,
          releaseDate,
          country.uppercase(ROOT),
          movie.runtime.toString(),
          getString(R.string.textMinutesShort),
          renderGenres(movie.genres)
        )
        movieDetailsCommentsButton.visible()
        movieDetailsShareButton.run {
          isEnabled = movie.ids.imdb.id.isNotBlank()
          alpha = if (isEnabled) 1.0F else 0.35F
          onClick { openShareSheet(movie) }
        }
        movieDetailsTrailerButton.run {
          isEnabled = movie.trailer.isNotBlank()
          alpha = if (isEnabled) 1.0F else 0.35F
          onClick {
            openWebUrl(movie.trailer) ?: showSnack(MessageEvent.Info(R.string.errorCouldNotFindApp))
            Analytics.logMovieTrailerClick(movie)
          }
        }
        movieDetailsLinksButton.run {
          onClick {
            val args = LinksBottomSheet.createBundle(movie)
            navigateTo(R.id.actionMovieDetailsFragmentToLinks, args)
          }
        }
        movieDetailsSeparator5.visible()
        movieDetailsCustomImagesLabel.visibleIf(Config.SHOW_PREMIUM)
        movieDetailsCustomImagesLabel.onClick { openCustomImagesSheet(movie.traktId, isPremium) }
        movieDetailsAddButton.isEnabled = true
      }
      movieLoading?.let {
        if (!movieDetailsCommentsView.isVisible) {
          movieDetailsMainLayout.fadeIf(!it, hardware = true)
          movieDetailsMainProgress.visibleIf(it)
        }
      }
      followedState?.let {
        when {
          it.isMyMovie -> movieDetailsAddButton.setState(IN_MY_MOVIES, it.withAnimation)
          it.isWatchlist -> movieDetailsAddButton.setState(IN_WATCHLIST, it.withAnimation)
          it.isHidden -> movieDetailsAddButton.setState(IN_HIDDEN, it.withAnimation)
          else -> movieDetailsAddButton.setState(ADD, it.withAnimation)
        }
        movieDetailsHideLabel.visibleIf(!it.isHidden)
        (requireAppContext() as WidgetsProvider).requestMoviesWidgetsUpdate()
      }
      image?.let { renderImage(it) }
      actors?.let { renderActors(it) }
      crew?.let { renderCrew(it) }
      streamings?.let { renderStreamings(it) }
      translation?.let { renderTranslation(it) }
      relatedMovies?.let { renderRelatedMovies(it) }
      comments?.let {
        movieDetailsCommentsView.bind(it, commentsDateFormat)
        if (isSignedIn) {
          movieDetailsCommentsView.showCommentButton()
        }
      }
      listsCount?.let {
        val text =
          if (it > 0) getString(R.string.textMovieManageListsCount, it)
          else getString(R.string.textMovieManageLists)
        movieDetailsManageListsLabel.text = text
      }
      ratingState?.let { renderRating(it) }
      ratings?.let { renderRatings(it, movie) }
      removeFromTrakt?.let { event ->
        event.consume()?.let { openRemoveTraktSheet(it) }
      }
      isPremium.let {
        movieDetailsPremiumAd.visibleIf(!it)
      }
      isFinished?.let { event ->
        event.consume()?.let {
          if (it) requireActivity().onBackPressed()
        }
      }
    }
  }

  private fun renderGenres(genres: List<String>) =
    genres
      .take(3)
      .mapNotNull { Genre.fromSlug(it) }
      .joinToString(", ") { getString(it.displayName) }

  private fun renderRating(rating: RatingState) {
    movieDetailsRateButton.visibleIf(rating.rateLoading == false, gone = false)
    movieDetailsRateProgress.visibleIf(rating.rateLoading == true)

    movieDetailsRateButton.text =
      if (rating.hasRating()) "${rating.userRating?.rating}/10"
      else getString(R.string.textMovieRate)

    val typeFace = if (rating.hasRating()) BOLD else NORMAL
    movieDetailsRateButton.setTypeface(null, typeFace)

    movieDetailsRateButton.onClick {
      if (rating.rateAllowed == true) {
        openRateDialog()
      } else {
        showSnack(MessageEvent.Info(R.string.textSignBeforeRateMovie))
      }
    }
  }

  private fun renderRatings(ratings: Ratings, movie: Movie?) {
    if (movieDetailsRatings.isBound()) return
    movieDetailsRatings.bind(ratings)
    movie?.let {
      movieDetailsRatings.onTraktClick = { openMovieLink(TRAKT, movie.traktId.toString()) }
      movieDetailsRatings.onImdbClick = { openMovieLink(IMDB, movie.ids.imdb.id) }
      movieDetailsRatings.onMetaClick = { openMovieLink(METACRITIC, movie.title) }
      movieDetailsRatings.onRottenClick = {
        val url = it.rottenTomatoesUrl
        if (!url.isNullOrBlank()) {
          openWebUrl(url) ?: openMovieLink(ROTTEN, "${movie.title} ${movie.year}")
        } else {
          openMovieLink(ROTTEN, "${movie.title} ${movie.year}")
        }
      }
    }
  }

  private fun renderImage(image: Image) {
    if (image.status == UNAVAILABLE) {
      movieDetailsImageProgress.gone()
      movieDetailsPlaceholder.visible()
      movieDetailsImage.isClickable = false
      movieDetailsImage.isEnabled = false
      return
    }
    Glide.with(this)
      .load(image.fullFileUrl)
      .transform(CenterCrop())
      .transition(withCrossFade(IMAGE_FADE_DURATION_MS))
      .withFailListener {
        movieDetailsImageProgress.gone()
        movieDetailsPlaceholder.visible()
        movieDetailsImage.isClickable = true
        movieDetailsImage.isEnabled = true
      }
      .withSuccessListener {
        movieDetailsImageProgress.gone()
        movieDetailsPlaceholder.gone()
      }
      .into(movieDetailsImage)
  }

  private fun renderActors(actors: List<Person>) {
    if (actorsAdapter?.itemCount != 0) return
    actorsAdapter?.setItems(actors)
    movieDetailsActorsRecycler.visibleIf(actors.isNotEmpty())
    movieDetailsActorsEmptyView.visibleIf(actors.isEmpty())
    movieDetailsActorsProgress.gone()
  }

  private fun renderCrew(crew: Map<Department, List<Person>>) {

    fun renderPeople(labelView: View, valueView: TextView, people: List<Person>, department: Department) {
      labelView.visibleIf(people.isNotEmpty())
      valueView.visibleIf(people.isNotEmpty())
      valueView.text = people
        .take(2)
        .joinToString("\n") { it.name.trimWithSuffix(20, "…") }
        .plus(if (people.size > 2) "\n…" else "")
      valueView.onClick { openPeopleListSheet(people, department) }
    }

    if (!crew.containsKey(Department.DIRECTING)) {
      return
    }

    val directors = crew[Department.DIRECTING] ?: emptyList()
    val writers = crew[Department.WRITING] ?: emptyList()
    val sound = crew[Department.SOUND] ?: emptyList()

    renderPeople(movieDetailsDirectingLabel, movieDetailsDirectingValue, directors, Department.DIRECTING)
    renderPeople(movieDetailsWritingLabel, movieDetailsWritingValue, writers, Department.WRITING)
    renderPeople(movieDetailsMusicLabel, movieDetailsMusicValue, sound, Department.SOUND)
  }

  private fun renderStreamings(streamings: StreamingsState) {
    if (streamingAdapter?.itemCount != 0) return
    val (items, isLocal) = streamings
    streamingAdapter?.setItems(items)
    if (items.isNotEmpty()) {
      if (isLocal) {
        movieDetailsStreamingsRecycler.visible()
      } else {
        movieDetailsStreamingsRecycler.fadeIn(withHardware = true)
      }
    } else if (!isLocal) {
      movieDetailsStreamingsRecycler.gone()
    }
  }

  private fun renderRelatedMovies(items: List<RelatedListItem>) {
    relatedAdapter?.setItems(items)
    movieDetailsRelatedRecycler.visibleIf(items.isNotEmpty())
    movieDetailsRelatedLabel.fadeIf(items.isNotEmpty(), hardware = true)
    movieDetailsRelatedProgress.gone()
  }

  private fun renderTranslation(translation: Translation?) {
    if (translation?.overview?.isNotBlank() == true) {
      movieDetailsDescription.text = translation.overview
    }
    if (translation?.title?.isNotBlank() == true) {
      movieDetailsTitle.text = translation.title
    }
  }

  private fun renderSnack(event: MessageEvent) {
    if (event.textResId == R.string.errorMalformedMovie) {
      event.consume()?.let {
        val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
        val snack = host.showInfoSnackbar(getString(it), length = Snackbar.LENGTH_INDEFINITE) {
          viewModel.removeMalformedMovie(movieId)
        }
        snackbars.add(snack)
      }
      return
    }
    showSnack(event)
  }

  private fun openIMDbLink(id: IdImdb, type: String) {
    val i = Intent(Intent.ACTION_VIEW)
    i.data = Uri.parse("imdb:///$type/${id.id}")
    try {
      startActivity(i)
    } catch (e: ActivityNotFoundException) {
      // IMDb App not installed. Start in web browser
      openWebUrl("http://www.imdb.com/$type/${id.id}") ?: showSnack(MessageEvent.Info(R.string.errorCouldNotFindApp))
    }
  }

  private fun openMovieLink(
    link: MovieLink,
    id: String,
    country: AppCountry = UNITED_STATES,
  ) {
    if (link == IMDB) {
      openIMDbLink(IdImdb(id), "title")
    } else {
      openWebUrl(link.getUri(id, country)) ?: showSnack(MessageEvent.Info(R.string.errorCouldNotFindApp))
    }
  }

  private fun openRemoveTraktSheet(@IdRes action: Int) {
    setFragmentResultListener(NavigationArgs.REQUEST_REMOVE_TRAKT) { _, bundle ->
      if (bundle.getBoolean(NavigationArgs.RESULT, false)) {
        val text = resources.getString(R.string.textTraktSyncMovieRemovedFromTrakt)
        (requireActivity() as SnackbarHost).provideSnackbarLayout().showInfoSnackbar(text)
      }
    }
    val args = RemoveTraktBottomSheet.createBundle(movieId, RemoveTraktBottomSheet.Mode.MOVIE)
    navigateTo(action, args)
  }

  private fun openPersonSheet(person: Person) {
    lastOpenedPerson = null
    setFragmentResultListener(NavigationArgs.REQUEST_PERSON_DETAILS) { _, _ ->
      lastOpenedPerson = person
    }
    val bundle = PersonDetailsBottomSheet.createBundle(person, movieId)
    navigateToSafe(R.id.actionMovieDetailsFragmentToPerson, bundle)
  }

  private fun openPeopleListSheet(people: List<Person>, department: Department) {
    if (people.isEmpty()) return
    if (people.size == 1) {
      openPersonSheet(people.first())
      return
    }
    clearFragmentResultListener(NavigationArgs.REQUEST_PERSON_DETAILS)
    val title = movieDetailsTitle.text.toString()
    val bundle = PeopleListBottomSheet.createBundle(movieId, title, Mode.MOVIES, department)
    navigateToSafe(R.id.actionMovieDetailsFragmentToPeopleList, bundle)
  }

  private fun openShareSheet(movie: Movie) {
    val intent = Intent().apply {
      val text = "Hey! Check out ${movie.title}:\nhttps://trakt.tv/movies/${movie.ids.slug.id}\nhttps://www.imdb.com/title/${movie.ids.imdb.id}"
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, text)
      type = "text/plain"
    }

    val shareIntent = Intent.createChooser(intent, "Share ${movie.title}")
    startActivity(shareIntent)

    Analytics.logMovieShareClick(movie)
  }

  private fun openRateDialog() {
    setFragmentResultListener(NavigationArgs.REQUEST_RATING) { _, bundle ->
      when (bundle.getParcelable<Operation>(NavigationArgs.RESULT)) {
        Operation.SAVE -> renderSnack(MessageEvent.Info(R.string.textRateSaved))
        Operation.REMOVE -> renderSnack(MessageEvent.Info(R.string.textRateRemoved))
        else -> Timber.w("Unknown result.")
      }
      viewModel.loadRating()
    }
    val bundle = RatingsBottomSheet.createBundle(movieId, Type.MOVIE)
    navigateTo(R.id.actionMovieDetailsFragmentToRating, bundle)
  }

  private fun openDeleteCommentDialog(comment: Comment) {
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textCommentConfirmDeleteTitle)
      .setMessage(R.string.textCommentConfirmDelete)
      .setPositiveButton(R.string.textYes) { _, _ -> viewModel.deleteComment(comment) }
      .setNegativeButton(R.string.textNo) { _, _ -> }
      .show()
  }

  private fun openListsDialog() {
    setFragmentResultListener(REQUEST_MANAGE_LISTS) { _, _ -> viewModel.loadListsCount() }
    val bundle = bundleOf(
      ARG_ID to movieId.id,
      ARG_TYPE to Mode.MOVIES.type
    )
    navigateToSafe(R.id.actionMovieDetailsFragmentToManageLists, bundle)
  }

  private fun openCustomImagesSheet(movieId: Long, isPremium: Boolean?) {
    if (isPremium == false) {
      navigateTo(R.id.actionMovieDetailsFragmentToPremium)
      return
    }

    setFragmentResultListener(REQUEST_CUSTOM_IMAGE) { _, bundle ->
      viewModel.loadBackgroundImage()
      if (!bundle.getBoolean(ARG_CUSTOM_IMAGE_CLEARED)) openCustomImagesSheet(movieId, true)
    }

    val bundle = bundleOf(
      ARG_MOVIE_ID to movieId,
      ARG_FAMILY to MOVIE
    )
    navigateToSafe(R.id.actionMovieDetailsFragmentToCustomImages, bundle)
  }

  private fun openPostCommentSheet(comment: Comment? = null) {
    setFragmentResultListener(REQUEST_COMMENT) { _, bundle ->
      showSnack(MessageEvent.Info(R.string.textCommentPosted))
      when (bundle.getString(ARG_COMMENT_ACTION)) {
        ACTION_NEW_COMMENT -> {
          val newComment = bundle.getParcelable<Comment>(ARG_COMMENT)!!
          viewModel.addNewComment(newComment)
          if (comment == null) movieDetailsCommentsView.resetScroll()
        }
      }
    }
    val bundle = when {
      comment != null -> bundleOf(
        ARG_COMMENT_ID to comment.getReplyId(),
        ARG_REPLY_USER to comment.user.username
      )
      else -> bundleOf(ARG_MOVIE_ID to movieId.id)
    }
    navigateToSafe(R.id.actionMovieDetailsFragmentToPostComment, bundle)
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      when {
        movieDetailsCommentsView.isVisible -> {
          hideExtraView(movieDetailsCommentsView)
          return@addCallback
        }
        else -> {
          isEnabled = false
          findNavControl()?.popBackStack()
        }
      }
    }
  }

  override fun onDestroyView() {
    actorsAdapter = null
    streamingAdapter = null
    relatedAdapter = null
    super.onDestroyView()
  }
}
