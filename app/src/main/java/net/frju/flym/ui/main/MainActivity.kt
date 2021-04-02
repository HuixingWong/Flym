/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package net.frju.flym.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_edit_feed.view.*
import kotlinx.android.synthetic.main.fragment_entries.*
import kotlinx.android.synthetic.main.view_main_drawer_header.*
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.utils.PrefConstants
import net.frju.flym.service.AutoRefreshJobService
import net.frju.flym.service.FetcherService
import net.frju.flym.ui.about.AboutActivity
import net.frju.flym.ui.discover.DiscoverActivity
import net.frju.flym.ui.entries.EntriesFragment
import net.frju.flym.ui.entrydetails.EntryDetailsActivity
import net.frju.flym.ui.entrydetails.EntryDetailsFragment
import net.frju.flym.ui.feeds.FeedAdapter
import net.frju.flym.ui.feeds.FeedListEditActivity
import net.frju.flym.ui.settings.SettingsActivity
import net.frju.flym.utils.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk21.listeners.onClick
import pub.devrel.easypermissions.EasyPermissions
import java.io.*
import java.util.*

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainNavigator {

    companion object {
        const val EXTRA_FROM_NOTIF = "EXTRA_FROM_NOTIF"

        var isInForeground = false

        private const val TAG_DETAILS = "TAG_DETAILS"
        private const val TAG_MASTER = "TAG_MASTER"


        private const val WRITE_OPML_REQUEST_CODE = 2
        private const val READ_OPML_REQUEST_CODE = 3

        private const val INTENT_UNREADS = "net.frju.flym.intent.UNREADS"
        private const val INTENT_ALL = "net.frju.flym.intent.ALL"
        private const val INTENT_FAVORITES = "net.frju.flym.intent.FAVORITES"
    }

    private val mainViewModel: MainViewModel by viewModels()

    lateinit var feedAdapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setupNoActionBarTheme()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        feedAdapter = FeedAdapter(mainViewModel.feedsData)

        more.onClick {
            it?.let { view ->
                PopupMenu(this@MainActivity, view).apply {
                    menuInflater.inflate(R.menu.menu_drawer_header, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.reorder -> startActivity<FeedListEditActivity>()
                            R.id.import_feeds -> pickOpml()
                            R.id.export_feeds -> exportOpml()
                            R.id.menu_entries__about -> goToAboutMe()
                            R.id.menu_entries__settings -> goToSettings()
                        }
                        true
                    }
                    show()
                }
            }
        }
        nav.layoutManager = LinearLayoutManager(this)
        nav.adapter = feedAdapter

        add_feed_fab.onClick {
            goToFeedSearch()
            closeDrawer()
        }

        mainViewModel.feedGroups.observe(this) {
            feedAdapter.notifyParentDataSetChanged(true)
            if (mainViewModel.hasFetchingError()) {
                drawer_hint.textColor = Color.RED
                drawer_hint.textResource = R.string.drawer_fetch_error_explanation
                toolbar.setNavigationIcon(R.drawable.ic_menu_red_highlight_24dp)
            } else {
                drawer_hint.textColor = Color.WHITE
                drawer_hint.textResource = R.string.drawer_explanation
                toolbar.setNavigationIcon(R.drawable.ic_menu_24dp)
            }
        }

        mainViewModel.toastValue.observe(this) {
            toast(it)
        }

        feedAdapter.onFeedLongClick { view, feedWithCount ->
            longClickPop(view, feedWithCount)
        }

        feedAdapter.onFeedClick { _, feedWithCount ->
            goToEntriesList(feedWithCount.feed)
            closeDrawer()
        }

        if (savedInstanceState == null) {
            // First open => we open the drawer for you
            if (getPrefBoolean(PrefConstants.FIRST_OPEN, true)) {
                putPrefBoolean(PrefConstants.FIRST_OPEN, false)
                openDrawer()

                showAlertDialog(R.string.welcome_title) { goToFeedSearch() }
            } else {
                closeDrawer()
            }

            goToEntriesList(null)
        }

        if (getPrefBoolean(PrefConstants.REFRESH_ON_STARTUP, defValue = true)) {
            try { // Some people seems to sometimes have a IllegalStateException on this
                startService(
                    Intent(this, FetcherService::class.java)
                        .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                        .putExtra(FetcherService.FROM_AUTO_REFRESH, true)
                )
            } catch (t: Throwable) {
                // Nothing to do, the refresh can still be triggered manually
            }
        }

        AutoRefreshJobService.initAutoRefresh(this)

        handleImplicitIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        if (getPrefBoolean(PrefConstants.HIDE_NAVIGATION_ON_SCROLL, false)) {
            ViewCompat.setOnApplyWindowInsetsListener(nav) { _, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                nav.updatePadding(bottom = systemInsets.bottom)
                drawer.updatePadding(left = systemInsets.left, right = systemInsets.right)
                guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideBegin = systemInsets.top
                }
                drawer_content.updatePadding(left = systemInsets.left)
                drawer_content.updateLayoutParams<DrawerLayout.LayoutParams> {
                    width =
                        resources.getDimensionPixelSize(R.dimen.nav_drawer_width) + systemInsets.left
                }
                insets
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(nav, null)
            nav.updatePadding(bottom = 0)
            drawer.updatePadding(left = 0, right = 0)
            guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideBegin = 0
            }
            drawer_content.updatePadding(left = 0)
            drawer_content.updateLayoutParams<DrawerLayout.LayoutParams> {
                width = resources.getDimensionPixelSize(R.dimen.nav_drawer_width)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        setIntent(intent)

        handleImplicitIntent(intent)
    }

    private fun handleImplicitIntent(intent: Intent?) {
        // Has to be called on onStart (when the app is closed) and on onNewIntent (when the app is in the background)

        // Add feed urls from Open with
        if (intent?.action.equals(Intent.ACTION_VIEW)) {
            val search: String = intent?.data.toString()
            DiscoverActivity.newInstance(this, search)
            setIntent(null)
        }
        // Add feed urls from Share menu
        if (intent?.action.equals(Intent.ACTION_SEND)) {
            if (intent?.hasExtra(Intent.EXTRA_TEXT) == true) {
                val search = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (search != null) {
                    DiscoverActivity.newInstance(this, search)
                }
            }
            setIntent(null)
        }

        // If we just clicked on the notification, let's go back to the default view
        if (intent?.getBooleanExtra(EXTRA_FROM_NOTIF, false) == true &&
            mainViewModel.feedGroups.value?.isNotEmpty() == true
        ) {
            feedAdapter.selectedItemId = Feed.ALL_ENTRIES_ID
            goToEntriesList(mainViewModel.feedGroups.value!![0].feedWithCount.feed)
            bottom_navigation.selectedItemId = R.id.unreads
        }
    }

    private fun handleResumeOnlyIntents(intent: Intent?) {

        // If it comes from the All feeds App Shortcuts, select the right view
        if (intent?.action.equals(INTENT_ALL) && bottom_navigation.selectedItemId != R.id.all) {
            feedAdapter.selectedItemId = Feed.ALL_ENTRIES_ID
            bottom_navigation.selectedItemId = R.id.all
        }

        // If it comes from the Favorites feeds App Shortcuts, select the right view
        if (intent?.action.equals(INTENT_FAVORITES) && bottom_navigation.selectedItemId != R.id.favorites) {
            feedAdapter.selectedItemId = Feed.ALL_ENTRIES_ID
            bottom_navigation.selectedItemId = R.id.favorites
        }

        // If it comes from the Unreads feeds App Shortcuts, select the right view
        if (intent?.action.equals(INTENT_UNREADS) && bottom_navigation.selectedItemId != R.id.unreads) {
            feedAdapter.selectedItemId = Feed.ALL_ENTRIES_ID
            bottom_navigation.selectedItemId = R.id.unreads
        }
    }

    override fun onResume() {
        super.onResume()

        isInForeground = true
        notificationManager.cancel(0)

        handleResumeOnlyIntents(intent)
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        feedAdapter.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        feedAdapter.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onBackPressed() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.closeDrawer(GravityCompat.START)
        } else if (toolbar.hasExpandedActionView()) {
            toolbar.collapseActionView()
        } else if (!goBack()) {
            super.onBackPressed()
        }
    }

    override fun goToEntriesList(feed: Feed?) {
        clearDetails()
        containers_layout.state = MainNavigator.State.TWO_COLUMNS_EMPTY

        // We try to reuse the fragment to avoid loosing the bottom tab position
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_master)
        if (currentFragment is EntriesFragment) {
            currentFragment.feed = feed
        } else {
            val master = EntriesFragment.newInstance(feed)
            supportFragmentManager
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.frame_master, master, TAG_MASTER)
                .commitAllowingStateLoss()
        }
    }

    override fun goToFeedSearch() = DiscoverActivity.newInstance(this)

    override fun goToEntryDetails(entryId: String, allEntryIds: List<String>) {
        closeKeyboard()

        if (containers_layout.hasTwoColumns()) {
            containers_layout.state = MainNavigator.State.TWO_COLUMNS_WITH_DETAILS
            val fragment = EntryDetailsFragment.newInstance(entryId, allEntryIds)
            supportFragmentManager
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.frame_details, fragment, TAG_DETAILS)
                .commitAllowingStateLoss()

            val listFragment =
                supportFragmentManager.findFragmentById(R.id.frame_master) as EntriesFragment
            listFragment.setSelectedEntryId(entryId)
        } else {
            if (getPrefBoolean(PrefConstants.OPEN_BROWSER_DIRECTLY, false)) {
                openInBrowser(entryId)
            } else {
                startActivity<EntryDetailsActivity>(
                    EntryDetailsFragment.ARG_ENTRY_ID to entryId,
                    EntryDetailsFragment.ARG_ALL_ENTRIES_IDS to allEntryIds.take(500)
                )
                // take() to avoid TransactionTooLargeException
            }
        }
    }

    override fun setSelectedEntryId(selectedEntryId: String) {
        val listFragment =
            supportFragmentManager.findFragmentById(R.id.frame_master) as EntriesFragment
        listFragment.setSelectedEntryId(selectedEntryId)
    }

    override fun goToAboutMe() {
        startActivity<AboutActivity>()
    }

    override fun goToSettings() {
        startActivity<SettingsActivity>()
    }

    private fun openInBrowser(entryId: String) {
        doAsync {
            App.db.entryDao().findByIdWithFeed(entryId)?.entry?.link?.let { url ->
                App.db.entryDao().markAsRead(listOf(entryId))
                browse(url)
            }
        }
    }

    private fun pickOpml() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // https://github.com/FredJul/Flym/issues/407
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, READ_OPML_REQUEST_CODE)
    }

    private fun exportOpml() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "Flym_" + System.currentTimeMillis() + ".opml")
        }
        startActivityForResult(intent, WRITE_OPML_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == READ_OPML_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri -> mainViewModel.importOpml(uri) }
        } else if (requestCode == WRITE_OPML_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri -> mainViewModel.exportOpml(uri) }
        }
    }



    private fun closeDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.postDelayed({ drawer.closeDrawer(GravityCompat.START) }, 100)
        }
    }

    private fun openDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == false) {
            drawer?.openDrawer(GravityCompat.START)
        }
    }

    fun toggleDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.closeDrawer(GravityCompat.START)
        } else {
            drawer?.openDrawer(GravityCompat.START)
        }
    }

    private fun goBack(): Boolean {
        if (containers_layout.state != MainNavigator.State.TWO_COLUMNS_WITH_DETAILS ||
            containers_layout.hasTwoColumns()
        ) return false
        if (clearDetails()) {
            containers_layout.state = MainNavigator.State.TWO_COLUMNS_EMPTY
            return true
        }
        return false
    }

    private fun clearDetails(): Boolean {
        supportFragmentManager.findFragmentByTag(TAG_DETAILS)?.let {
            supportFragmentManager
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .remove(it)
                .commitAllowingStateLoss()
            return true
        }
        return false
    }
}
