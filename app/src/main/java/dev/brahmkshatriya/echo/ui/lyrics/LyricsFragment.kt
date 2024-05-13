package dev.brahmkshatriya.echo.ui.lyrics

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.CONSUMED
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.LyricsSearchClient
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Lyric
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.databinding.FragmentLyricsBinding
import dev.brahmkshatriya.echo.databinding.ItemLyricsItemBinding
import dev.brahmkshatriya.echo.plugger.ExtensionType
import dev.brahmkshatriya.echo.ui.extension.ExtensionsListBottomSheet
import dev.brahmkshatriya.echo.utils.autoCleared
import dev.brahmkshatriya.echo.utils.emit
import dev.brahmkshatriya.echo.utils.loadWith
import dev.brahmkshatriya.echo.utils.observe
import dev.brahmkshatriya.echo.viewmodels.PlayerViewModel
import dev.brahmkshatriya.echo.viewmodels.UiViewModel


class LyricsFragment : Fragment() {

    private var binding by autoCleared<FragmentLyricsBinding>()
    private val viewModel by activityViewModels<LyricsViewModel>()
    private val playerVM by activityViewModels<PlayerViewModel>()
    private val uiViewModel by activityViewModels<UiViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentLyricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ -> CONSUMED }
        observe(uiViewModel.infoSheetState) {
            binding.root.keepScreenOn = it == BottomSheetBehavior.STATE_EXPANDED
        }
        binding.searchBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_lyrics -> ExtensionsListBottomSheet.newInstance(ExtensionType.LYRICS)
                    .show(parentFragmentManager, null)
            }
            true
        }
        val menu = binding.searchBar.menu
        val extension = binding.searchBar.findViewById<View>(R.id.menu_lyrics)
        observe(viewModel.currentExtension) { current ->
            binding.searchBar.hint = current?.metadata?.name
            current?.metadata?.iconUrl?.toImageHolder()
                .loadWith(extension, R.drawable.ic_extension) {
                    menu.findItem(R.id.menu_lyrics).icon = it
                }
            val isSearchable = current?.let { it.client is LyricsSearchClient }
            binding.searchBar.setNavigationIcon(
                when (isSearchable) {
                    true -> R.drawable.ic_search_outline
                    else -> R.drawable.ic_queue_music
                }
            )
            binding.searchView.editText.isEnabled = isSearchable ?: false
            binding.searchView.hint = if (isSearchable == true)
                getString(R.string.search_extension, current.metadata.name)
            else current?.metadata?.name
        }
        binding.searchView.editText.setOnEditorActionListener { v, _, _ ->
            viewModel.search(v.text.toString().takeIf { it.isNotBlank() })
            true
        }

        val lyricsItemAdapter = LyricsItemAdapter(this) { lyrics ->
            viewModel.onLyricsSelected(lyrics)
            binding.searchView.hide()
        }
        binding.searchRecyclerView.adapter = lyricsItemAdapter
        observe(viewModel.searchResults) {
            lyricsItemAdapter.submitData(it ?: PagingData.empty())
        }

        var currentLyric: Lyric? = null
        var lyricAdapter: LyricAdapter? = null
        val smoothScroller = CenterSmoothScroller(binding.lyricsRecyclerView)
        val layoutManager = binding.lyricsRecyclerView.layoutManager as LinearLayoutManager
        fun updateLyrics(current: Long) {
            if ((currentLyric?.endTime ?: 0) < current || current <= 0) {
                val list = viewModel.currentLyrics.value?.lyrics?.map { lyric ->
                    val isCurrent = lyric.startTime <= current
                    if (isCurrent) currentLyric = lyric
                    isCurrent to lyric
                } ?: emptyList()
                lyricAdapter?.submitList(list)
                val currentIndex = list.indexOfLast { it.first }
                    .takeIf { it != -1 } ?: return
                smoothScroller.targetPosition = currentIndex
                layoutManager.startSmoothScroll(smoothScroller)
                binding.appBarLayout.setExpanded(false)
                slideDown()
            }
        }
        lyricAdapter = LyricAdapter { lyric ->
            currentLyric = null
            emit(playerVM.seekTo) { lyric.startTime }
            updateLyrics(lyric.startTime)
        }
        binding.lyricsRecyclerView.adapter = lyricAdapter
        binding.lyricsRecyclerView.itemAnimator = null
        observe(viewModel.currentLyrics) {
            binding.lyricsItem.bind(it)
            currentLyric = null
            lyricAdapter.submitList(it?.lyrics?.map { lyric -> false to lyric })
            binding.noLyrics.isVisible = it?.lyrics.isNullOrEmpty()
        }

        observe(playerVM.progress) { updateLyrics(it.first.toLong()) }
        observe(viewModel.loading) { binding.lyricsLoading.root.isVisible = it }
        viewModel.initialize()
    }

    fun ItemLyricsItemBinding.bind(lyrics: Lyrics?) = root.run {
        if (lyrics == null) {
            isVisible = false
            return
        }
        isVisible = true
        setTitle(lyrics.title)
        setSubtitle(lyrics.subtitle)
    }

    class CenterSmoothScroller(private val recyclerView: RecyclerView) :
        LinearSmoothScroller(recyclerView.context) {

        override fun calculateDtToFit(
            viewStart: Int, viewEnd: Int,
            boxStart: Int, boxEnd: Int, snapPreference: Int
        ): Int {
            val midPoint = recyclerView.height / 2
            val targetMidPoint = ((viewEnd - viewStart) / 2) + viewStart
            return midPoint - targetMidPoint
        }

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
            return 100f / displayMetrics.densityDpi
        }

        override fun calculateTimeForScrolling(dx: Int): Int {
            val time = super.calculateTimeForScrolling(dx)
            return (time * 1.5f).toInt()
        }

        override fun calculateTimeForDeceleration(dx: Int): Int {
            return (super.calculateTimeForDeceleration(dx) * 1.5f).toInt()
        }
    }

    private fun slideDown() {
        val params = binding.lyricsItem.root.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as HideBottomViewOnScrollBehavior
        behavior.slideDown(binding.lyricsItem.root)
    }
}