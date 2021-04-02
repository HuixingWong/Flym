package net.frju.flym.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import kotlinx.android.synthetic.main.dialog_edit_feed.view.*
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.FeedWithCount
import net.frju.flym.ui.feeds.FeedListEditActivity
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.startActivity

fun longClickPop(view: View, feedWithCount: FeedWithCount) {
    PopupMenu(view.context, view).apply {
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.mark_all_as_read -> doAsync {
                    when {
                        feedWithCount.feed.id == Feed.ALL_ENTRIES_ID -> App.db.entryDao()
                            .markAllAsRead()
                        feedWithCount.feed.isGroup -> App.db.entryDao()
                            .markGroupAsRead(feedWithCount.feed.id)
                        else -> App.db.entryDao().markAsRead(feedWithCount.feed.id)
                    }
                }
                R.id.edit_feed -> {
                    @SuppressLint("InflateParams")
                    val input =
                        (view.context as Activity).layoutInflater.inflate(
                            R.layout.dialog_edit_feed, null, false
                        ).apply {
                            feed_name.setText(feedWithCount.feed.title)
                            if (feedWithCount.feed.isGroup) {
                                feed_link.isGone = true
                            } else {
                                feed_link.setText(feedWithCount.feed.link)
                            }
                        }

                    AlertDialog.Builder(view.context)
                        .setTitle(R.string.menu_edit_feed)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val newName = input.feed_name.text.toString()
                            val newLink = input.feed_link.text.toString()
                            if (newName.isNotBlank() && (newLink.isNotBlank() ||
                                        feedWithCount.feed.isGroup)) {
                                doAsync {
                                    // Need to do a copy to not directly modify the memory and being able to detect changes
                                    val newFeed = feedWithCount.feed.copy().apply {
                                        title = newName
                                        if (!feedWithCount.feed.isGroup) {
                                            link = newLink
                                        }
                                    }
                                    App.db.feedDao().update(newFeed)
                                }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                R.id.reorder -> view.context.startActivity<FeedListEditActivity>()
                R.id.delete -> {
                    AlertDialog.Builder(view.context)
                        .setTitle(feedWithCount.feed.title)
                        .setMessage(
                            if (feedWithCount.feed.isGroup)
                                R.string.question_delete_group else R.string.question_delete_feed
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            doAsync { App.db.feedDao().delete(feedWithCount.feed) }
                        }.setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                R.id.enable_full_text_retrieval -> doAsync {
                    App.db.feedDao().enableFullTextRetrieval(feedWithCount.feed.id)
                }
                R.id.disable_full_text_retrieval -> doAsync {
                    App.db.feedDao().disableFullTextRetrieval(feedWithCount.feed.id)
                }
            }
            true
        }
        inflate(R.menu.menu_drawer_feed)

        when {
            feedWithCount.feed.id == Feed.ALL_ENTRIES_ID -> {
                menu.findItem(R.id.edit_feed).isVisible = false
                menu.findItem(R.id.delete).isVisible = false
                menu.findItem(R.id.reorder).isVisible = false
                menu.findItem(R.id.enable_full_text_retrieval).isVisible = false
                menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
            }
            feedWithCount.feed.isGroup -> {
                menu.findItem(R.id.enable_full_text_retrieval).isVisible = false
                menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
            }
            feedWithCount.feed.retrieveFullText -> menu.findItem(R.id.enable_full_text_retrieval).isVisible =
                false
            else -> menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
        }

        show()
    }
}