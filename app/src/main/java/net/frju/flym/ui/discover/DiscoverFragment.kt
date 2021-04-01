package net.frju.flym.ui.discover

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import net.fred.feedex.R


class DiscoverFragment : Fragment() {

    companion object {
        const val TAG = "DiscoverFragment"

        @JvmStatic
        fun newInstance() = DiscoverFragment()
    }

    private lateinit var manageFeeds: FeedManagementInterface

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_discover,
            container,
            false
        ).apply {
            findViewById<ComposeView>(R.id.compose_view).setContent {
                MaterialTheme {
                    ComposeTopic(::onClick)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        manageFeeds = context as FeedManagementInterface
    }

    private fun onClick(name: String) = run { manageFeeds.searchForFeed("#$name") }

    @Composable
    fun ComposeTopic(onclick: (String) -> Unit) {
        val topics = context?.resources?.getStringArray(R.array.discover_topics)
        if (topics != null) {
            LazyColumn {
                topics.forEach {
                    item {
                        TopicItem(it = it, onclick)
                    }
                }
            }
        }
    }

    @Composable
    fun TopicItem(
        it: String,
        onclick: (String) -> Unit
    ) {
        TextButton(onClick = {
            onclick(it)
        }) {
            Text(text = it)
        }
    }
}