package com.tvapp.ui.browse

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvapp.ui.JumpByLetter
import com.tvapp.ui.Tok
import com.tvapp.ui.channels.KeyHints
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.requestWhenReady
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.ListFilter
import com.tvapp.ui.state.Overlay
import com.tvapp.ui.state.deviceCountry
import com.tvapp.ui.tvFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Browse (docs/ui/screens/03-browse.md): countries on the left with counts (device country pinned), the focused country's
 * categories on the right as focus rests. Every pick clears the stack and opens one Channels overlay with the new filter.
 */
@Composable
fun BrowseOverlay(vm: AppViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val countries by vm.countries.collectAsState()
    val categoryNames by vm.categoryNames.collectAsState()
    var counts by remember { mutableStateOf<Map<String?, Int>>(emptyMap()) }
    LaunchedEffect(Unit) { counts = vm.countryCounts() }
    val dc = deviceCountry()
    // Zero-count countries are omitted, so no row leads to an empty list; the device country is pinned first.
    val rows = remember(counts, countries) {
        val withChannels = counts.filter { (k, v) -> k != null && v > 0 }.keys.filterNotNull()
        listOfNotNull(dc.takeIf { it in withChannels }) + withChannels.filter { it != dc }.sortedBy { countries[it]?.name ?: it }
    }
    var country by remember { mutableStateOf(dc) }
    var worldwide by remember { mutableStateOf(false) }   // the "Country: All" chip
    var cats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(country, worldwide) { cats = vm.categoryCounts(if (worldwide) null else country) }
    var inCategories by remember { mutableStateOf(false) }
    val countryFocus = remember { FocusRequester() }
    val allFocus = remember { FocusRequester() }
    val chipFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    var countryHasFocus by remember { mutableStateOf(false) }
    var categoryHasFocus by remember { mutableStateOf(false) }

    BackHandler(enabled = inCategories) { inCategories = false; scope.launch { countryFocus.requestWhenReady { countryHasFocus } } } // Back from categories returns to the country
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) countryFocus.requestWhenReady { countryHasFocus } }

    fun open(f: ListFilter) { vm.closeAll(); vm.setFilter(f); vm.push(Overlay.CHANNELS) }
    fun intoCategories() { inCategories = true; scope.launch { (if (worldwide) chipFocus else allFocus).requestWhenReady { categoryHasFocus } } }

    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Countries panel.
        Column(Modifier.width(272.dp).fillMaxHeight().background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
            Text("Browse", style = Tok.lg.copy(fontSize = 32.sp, lineHeight = 40.sp), modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(state = list, modifier = Modifier.onPreviewKeyEvent { k ->
                val d = JumpByLetter.digitOf(k.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                if (k.type == KeyEventType.KeyDown) {
                    JumpByLetter.next(rows.map { countries[it]?.name ?: it }, rows.indexOf(country), d)?.let { i ->
                        country = rows[i]; scope.launch { list.scrollToItem((i - 3).coerceAtLeast(0)); countryFocus.requestWhenReady { countryHasFocus } }
                    }
                    vm.toast(JumpByLetter.label(d))
                }
                true
            }) {
                itemsIndexed(rows, key = { _, c -> c }) { idx, code ->
                    if (idx == 1) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0x1FFFFFFF))
                    val i = rememberInteraction()
                    var rest by remember { mutableStateOf<Job?>(null) }
                    val chosen = code == country && inCategories
                    Row(Modifier.fillMaxWidth().height(48.dp)
                        .then(if (code == country) Modifier.focusRequester(countryFocus) else Modifier)
                        .onFocusChanged { f -> if (code == country) countryHasFocus = f.isFocused; if (f.isFocused) { inCategories = false; rest?.cancel(); rest = scope.launch { delay(150); country = code } } }
                        .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { country = code; intoCategories(); true } else false }
                        .tvFocus(i)
                        .clickable(interactionSource = i, indication = null) { country = code; intoCategories() }
                        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (chosen) Box(Modifier.width(3.dp).height(24.dp).background(Tok.accent)).also { Spacer(Modifier.width(5.dp)) }
                        Text(countries[code]?.flag ?: "", style = Tok.sm); Spacer(Modifier.width(8.dp))
                        Text(countries[code]?.name ?: code, style = Tok.md.copy(color = if (chosen) Tok.accent else Tok.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("%,d".format(counts[code] ?: 0), style = Tok.sm.copy(color = Tok.muted))
                    }
                }
            }
        }

        // Categories panel: heading, the chip, "All categories", then categories alphabetically with Other last.
        Column(Modifier.width(272.dp).fillMaxHeight().background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
            Text(if (worldwide) "All countries" else countries[country]?.name ?: country, style = Tok.lg.copy(fontSize = 32.sp, lineHeight = 40.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            val chipI = rememberInteraction()
            Text(if (worldwide) "Country: All ✓" else "Country: All",
                style = Tok.sm.copy(color = if (worldwide) Color(0xFF1B2027) else Tok.text),
                modifier = Modifier.focusRequester(chipFocus).focusProperties { left = countryFocus }
                    .onFocusChanged { categoryHasFocus = it.isFocused; if (it.isFocused) inCategories = true }
                    .background(if (worldwide) Tok.accent else Color(0x1FFFFFFF), RoundedCornerShape(50)).tvFocus(chipI, RoundedCornerShape(50))
                    .clickable(interactionSource = chipI, indication = null) { worldwide = !worldwide }
                    .padding(horizontal = 16.dp, vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
            val ordered = cats.filter { it.value > 0 }.keys.sortedWith(compareBy<String> { it == "other" }.thenBy { categoryNames[it] ?: it })
            LazyColumn {
                if (!worldwide) item(key = "all") {
                    CategoryRow("All categories", counts[country] ?: 0, Modifier.focusRequester(allFocus).focusProperties { left = countryFocus }.onFocusChanged { categoryHasFocus = it.isFocused; if (it.isFocused) inCategories = true }) {
                        open(ListFilter(ListFilter.Kind.COUNTRY, country))
                    }
                }
                itemsIndexed(ordered, key = { _, id -> id }) { _, id ->
                    CategoryRow(categoryNames[id] ?: id, cats[id] ?: 0, Modifier.focusProperties { left = countryFocus }.onFocusChanged { if (it.isFocused) inCategories = true }) {
                        open(if (worldwide) ListFilter(ListFilter.Kind.CATEGORY, null, id) else ListFilter(ListFilter.Kind.COUNTRY, country, id))
                    }
                }
            }
        }

        // Right column: the inset on top, the details panel under it.
        Column(Modifier.width(288.dp).fillMaxHeight()) {
            Spacer(Modifier.height(162.dp + 16.dp))
            Column(Modifier.fillMaxSize().background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
                Text(if (worldwide) "🌐" else countries[country]?.flag ?: "", style = Tok.lg.copy(fontSize = 32.sp, lineHeight = 36.sp))
                Text(if (worldwide) "All countries" else countries[country]?.name ?: country, style = Tok.lg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val n = if (worldwide) counts.values.sum() else counts[country] ?: 0
                Text("%,d channels".format(n), style = Tok.sm.copy(color = Tok.muted))
                Spacer(Modifier.weight(1f))
                KeyHints(listOf("OK" to "Open", "2–9" to "Jump to a letter", "Back" to "Channels", "Hold Back" to "Favorites"))
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val i = rememberInteraction()
    Row(modifier.fillMaxWidth().height(48.dp).tvFocus(i).clickable(interactionSource = i, indication = null, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tok.md, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("%,d".format(count), style = Tok.sm.copy(color = Tok.muted))
    }
}
