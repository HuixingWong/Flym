package net.frju.flym.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.FeedWithCount
import net.frju.flym.ui.feeds.FeedGroup
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    val feedGroups = MutableLiveData(mutableListOf<FeedGroup>())
    val feedsData = mutableListOf<FeedGroup>()

    fun compareData(feeds: List<FeedWithCount>) {
        viewModelScope.launch {
            val newFeedGroups = mutableListOf<FeedGroup>()
            val all = FeedWithCount(feed = Feed().apply {
                id = Feed.ALL_ENTRIES_ID
                title = App.context.getString(R.string.all_entries)
            }, entryCount = feeds.sumBy { it.entryCount })
            newFeedGroups.add(FeedGroup(all, listOf()))
            val subFeedMap = feeds.groupBy { it.feed.groupId }
            newFeedGroups.addAll(
                subFeedMap[null]?.map { FeedGroup(it, subFeedMap[it.feed.id].orEmpty()) }.orEmpty()
            )
            // Do not always call notifyParentDataSetChanged to avoid selection loss during refresh
            if (hasFeedGroupsChanged(feedGroups.value!!, newFeedGroups)) {
                feedsData.clear()
                feedsData.addAll(newFeedGroups)
                feedGroups.value = newFeedGroups
            }
        }
    }


    fun hasFetchingError(): Boolean {
        // Also need to check all sub groups (can't be checked in FeedGroup's equals)
        feedGroups.value?.forEach { feedGroup ->
            if (feedGroup.feedWithCount.feed.fetchError || feedGroup.subFeeds.any { it.feed.fetchError }) {
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
            if (feedGroup.feedWithCount != newFeedGroups[index].feedWithCount || feedGroup.subFeeds != newFeedGroups[index].subFeeds) {
                return true
            }
        }
        return false
    }

}