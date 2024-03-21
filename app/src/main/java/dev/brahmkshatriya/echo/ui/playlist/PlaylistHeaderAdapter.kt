package dev.brahmkshatriya.echo.ui.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.databinding.ItemAlbumInfoBinding
import dev.brahmkshatriya.echo.databinding.SkeletonItemAlbumInfoBinding
import dev.brahmkshatriya.echo.player.PlayerHelper.Companion.toTimeString
import dev.brahmkshatriya.echo.utils.loadInto

class PlaylistHeaderAdapter(
    private val listener: PlaylistHeaderListener
) :
    RecyclerView.Adapter<PlaylistHeaderAdapter.ViewHolder>() {

    override fun getItemCount() = 1

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class Info(
            val binding: ItemAlbumInfoBinding,
            val playlist: Playlist,
            listener: PlaylistHeaderListener
        ) :
            ViewHolder(binding.root) {
            init {
                binding.albumPlay.setOnClickListener {
                    listener.onPlayClicked(playlist)
                }
                binding.albumRadio.setOnClickListener {
                    listener.onRadioClicked(playlist)
                }
            }
        }

        class ShimmerViewHolder(binding: SkeletonItemAlbumInfoBinding) :
            ViewHolder(binding.root)
    }

    interface PlaylistHeaderListener {
        fun onPlayClicked(playlist: Playlist)
        fun onRadioClicked(playlist: Playlist)
    }

    override fun getItemViewType(position: Int) = if (_playlist == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == 0) {
            ViewHolder.ShimmerViewHolder(
                SkeletonItemAlbumInfoBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ), parent, false
                )
            )
        } else {
            val playlist = _playlist!!
            ViewHolder.Info(
                ItemAlbumInfoBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                playlist,
                listener
            )
        }
    }

    private var _playlist: Playlist? = null
    private var _radio: Boolean = false

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder !is ViewHolder.Info) return
        val binding = holder.binding
        val playlist = holder.playlist
        val artist = playlist.authors.firstOrNull()
        binding.albumArtistContainer.isVisible = artist != null
        if (artist != null) {
            binding.albumArtist.text = artist.name
            binding.albumArtistSubtitle.isVisible = false
            binding.albumArtistContainer.transitionName = artist.id
            artist.let {
                it.cover.loadInto(binding.albumArtistCover, R.drawable.art_artist)
            }
        }
        binding.albumDescription.text = playlist.subtitle
        binding.albumDescription.isVisible = !playlist.subtitle.isNullOrBlank()
        var info = binding.root.context.resources.getQuantityString(
            R.plurals.number_songs,
            playlist.tracks.size,
            playlist.tracks.size
        )
        playlist.duration?.toTimeString()?.let {
            info += " • $it"
        }
        playlist.creationDate?.let {
            info += "\n$it"
        }
        binding.albumInfo.text = info
        binding.albumRadio.isVisible = _radio
    }

    fun submit(playlist: Playlist, radio: Boolean) {
        _playlist = playlist
        _radio = radio
        notifyItemChanged(0)
    }
}