package net.frju.flym.ui.main

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rometools.opml.feed.opml.Attribute
import com.rometools.opml.feed.opml.Opml
import com.rometools.opml.feed.opml.Outline
import com.rometools.opml.io.impl.OPML20Generator
import com.rometools.rome.io.WireFeedInput
import com.rometools.rome.io.WireFeedOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.FeedWithCount
import net.frju.flym.ui.feeds.FeedGroup
import java.io.*
import java.net.URL
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val OLD_GNEWS_TO_IGNORE = "http://news.google.com/news?"
        private const val RETRIEVE_FULLTEXT_OPML_ATTR = "retrieveFullText"
    }

    val feedGroups = MutableLiveData(mutableListOf<FeedGroup>())
    val toastValue = MutableLiveData<String>()

    init {
        viewModelScope.launch {
            App.db.feedDao().observeAllWithCount.collect { feeds ->
                val newFeedGroups = mutableListOf<FeedGroup>()
                val all = FeedWithCount(feed = Feed().apply {
                    id = Feed.ALL_ENTRIES_ID
                    title = App.context.getString(R.string.all_entries)
                }, entryCount = feeds.sumBy { it.entryCount })
                newFeedGroups.add(FeedGroup(all, listOf()))
                val subFeedMap = feeds.groupBy { it.feed.groupId }
                newFeedGroups.addAll(
                    subFeedMap[null]?.map {
                        FeedGroup(it, subFeedMap[it.feed.id].orEmpty())
                    }.orEmpty()
                )
                // Do not always call notifyParentDataSetChanged to avoid selection loss during refresh
                if (hasFeedGroupsChanged(feedGroups.value!!, newFeedGroups)) {
                    feedGroups.value = newFeedGroups
                }
            }
        }
    }

    fun hasFetchingError(): Boolean {
        // Also need to check all sub groups (can't be checked in FeedGroup's equals)
        feedGroups.value?.forEach { feedGroup ->
            if (feedGroup.feedWithCount.feed.fetchError ||
                feedGroup.subFeeds.any { it.feed.fetchError }
            ) {
                return true
            }
        }
        return false
    }

    private fun hasFeedGroupsChanged(
        feedGroups: List<FeedGroup>,
        newFeedGroups: List<FeedGroup>
    ): Boolean {
        if (feedGroups != newFeedGroups) {
            return true
        }
        // Also need to check all sub groups (can't be checked in FeedGroup's equals)
        feedGroups.forEachIndexed { index, feedGroup ->
            if (feedGroup.feedWithCount != newFeedGroups[index].feedWithCount ||
                feedGroup.subFeeds != newFeedGroups[index].subFeeds
            ) {
                return true
            }
        }
        return false
    }

    fun importOpml(uri: Uri) {
        GlobalScope.launch {
            try {
                InputStreamReader(
                    App.context.contentResolver.openInputStream(uri)!!
                ).use { reader -> parseOpml(reader) }
            } catch (e: Exception) {
                try {
                    // We try to remove the opml version number, it may work better in some cases
                    val content = BufferedInputStream(
                        App.context.contentResolver.openInputStream(uri)!!
                    ).bufferedReader().use { it.readText() }
                    val fixedReader = StringReader(
                        content.replace(
                            "<opml version=['\"][0-9]\\.[0-9]['\"]>".toRegex(),
                            "<opml>"
                        )
                    )
                    parseOpml(fixedReader)
                } catch (e: Exception) {
                    toastValue.postValue(App.context.getString(R.string.cannot_find_feeds))
                }
            }
        }
    }

    private fun parseOpml(opmlReader: Reader) {
        var genId = 1L
        val feedList = mutableListOf<Feed>()
        val opml = WireFeedInput().build(opmlReader) as Opml
        opml.outlines.forEach { outline ->
            if (outline.xmlUrl != null || outline.children.isNotEmpty()) {
                val topLevelFeed = Feed().apply {
                    id = genId++
                    title = outline.title
                }
                if (outline.xmlUrl != null) {
                    if (!outline.xmlUrl.startsWith(OLD_GNEWS_TO_IGNORE)) {
                        topLevelFeed.link = outline.xmlUrl
                        topLevelFeed.retrieveFullText =
                            outline.getAttributeValue(RETRIEVE_FULLTEXT_OPML_ATTR) == "true"
                        feedList.add(topLevelFeed)
                    }
                } else {
                    topLevelFeed.isGroup = true
                    feedList.add(topLevelFeed)

                    outline.children.filter {
                        it.xmlUrl != null && !it.xmlUrl.startsWith(
                            OLD_GNEWS_TO_IGNORE
                        )
                    }.forEach {
                        val subLevelFeed = Feed().apply {
                            id = genId++
                            title = it.title
                            link = it.xmlUrl
                            retrieveFullText =
                                it.getAttributeValue(RETRIEVE_FULLTEXT_OPML_ATTR) == "true"
                            groupId = topLevelFeed.id
                        }
                        feedList.add(subLevelFeed)
                    }
                }
            }
        }
        if (feedList.isNotEmpty()) {
            App.db.feedDao().insert(*feedList.toTypedArray())
        }
    }


    private fun exportOpml(opmlWriter: Writer) {
        val feeds = App.db.feedDao().all.groupBy { it.groupId }

        val opml = Opml().apply {
            feedType = OPML20Generator().type
            encoding = "utf-8"
            created = Date()
            outlines = feeds[null]?.map { feed ->
                Outline(
                    feed.title,
                    if (feed.link.isNotBlank()) URL(feed.link) else null,
                    null
                ).apply {
                    children = feeds[feed.id]?.map {
                        Outline(
                            it.title,
                            if (it.link.isNotBlank()) URL(it.link) else null,
                            null
                        ).apply {
                            if (it.retrieveFullText) {
                                attributes.add(Attribute(RETRIEVE_FULLTEXT_OPML_ATTR, "true"))
                            }
                        }
                    }
                    if (feed.retrieveFullText) {
                        attributes.add(Attribute(RETRIEVE_FULLTEXT_OPML_ATTR, "true"))
                    }
                }
            }
        }

        WireFeedOutput().output(opml, opmlWriter)
    }

    fun exportOpml(uri: Uri) {
        GlobalScope.launch {
            runCatching {
                OutputStreamWriter(
                    App.context.contentResolver.openOutputStream(uri)!!,
                    Charsets.UTF_8
                ).use { writer ->
                    exportOpml(writer)
                }
                App.context.contentResolver.query(
                    uri, null, null,
                    null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val fileName =
                            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                        toastValue.postValue(
                            String.format(
                                App.context.getString(R.string.message_exported_to),
                                fileName
                            )
                        )
                    }
                }
            }.onFailure {
                toastValue.postValue(App.context.getString(R.string.error_feed_export))
            }
        }
    }


}