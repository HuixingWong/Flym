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

package net.frju.flym.ui.feeds

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.frju.flym.data.entities.FeedWithCount
import net.frju.flym.ui.main.MainViewModel
import net.frju.flym.ui.views.Button


@Composable
fun MainDrawerList(
    onclick: (FeedWithCount) -> Unit,
    onLongClick: (FeedWithCount) -> Unit
) {
    val viewModel: MainViewModel = viewModel()
    val feedGroups = viewModel.feedGroups.observeAsState()
    LazyColumn {
        feedGroups.value?.forEach { group ->
            item {
                Button(onClick = {
                    onclick(group.feedWithCount)
                },onLongClick = {
                    onLongClick(group.feedWithCount)
                }, Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp)) {
                    Text(
                        text = group.feedWithCount.feed.title ?: "no title",
                        Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 5.dp, bottom = 5.dp
                        )
                    )
                }
            }
        }
    }
}