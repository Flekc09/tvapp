package com.tvapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tvapp.ui.App
import com.tvapp.ui.state.Action
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.KeyRouter
import com.tvapp.ui.state.ListFilter
import com.tvapp.ui.state.Overlay
import com.tvapp.ui.state.deviceCountry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * TEMPORARY (plan Task 12 step 7): Task 16's key dispatch without the first-launch screen, so Task 12 can be checked on the
 * emulator. A debug build with no catalog downloads the live one and opens on the device country's list. Task 16 replaces this file.
 */
class MainActivity : ComponentActivity() {
    private val container get() = (application as TvApp).container
    lateinit var viewModel: AppViewModel; private set
    private var stoppedOnce = false
    private var lastCenterUp = 0L
    private var centerLongHandled = false

    companion object {
        private val SURF_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
        })[AppViewModel::class.java]
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { applyAction(KeyRouter.route(KeyEvent.KEYCODE_BACK, false, false, viewModel.top, routedBannerVisible())) }
        })
        setContent { App(viewModel) }
        lifecycleScope.launch {
            if (!container.catalogSync.hasCatalog()) {
                if (!BuildConfig.DEBUG) return@launch
                viewModel.toast("Loading channels…")
                runCatching { container.catalogSync.download { _, _, _ -> } }.onFailure { viewModel.toast("Channel list failed: ${it.message}"); return@launch }
                viewModel.setFilter(ListFilter(ListFilter.Kind.COUNTRY, deviceCountry()))
            }
            val recent = container.db.local().recents().first().firstOrNull { viewModel.allowed(it.channelId) }?.channelId
            val id = recent ?: viewModel.visibleChannels.first { it.isNotEmpty() }.first().id
            viewModel.select(id)
        }
    }

    override fun onStart() { super.onStart(); if (stoppedOnce) container.playerController.retuneToLive() }
    override fun onStop() { super.onStop(); stoppedOnce = true; container.playerController.cancel() }

    private fun routedBannerVisible() = viewModel.bannerVisible.value && !container.playerController.state.value.tuning

    // Lint's RestrictedApi flags every super.dispatchKeyEvent here: androidx.core's ComponentActivity overrides the public
    // Activity.dispatchKeyEvent and marks its override @RestrictTo(LIBRARY_GROUP_PREFIX). Overriding and calling super is the
    // documented way to see keys first, so the check is a false positive for this method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        viewModel.wake()
        val top = viewModel.top
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) { viewModel.onBackDown(event.repeatCount); return true }
            if (event.action == KeyEvent.ACTION_UP) {
                if (viewModel.holdBackHandled) { viewModel.holdBackHandled = false; return true }
                onBackPressedDispatcher.onBackPressed(); return true
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && (top == Overlay.NONE || top == Overlay.NOT_WORKING)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                if (!centerLongHandled) { centerLongHandled = true; applyAction(KeyRouter.route(event.keyCode, longPress = true, doubleTap = false, top, routedBannerVisible())) }
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                if (centerLongHandled) { centerLongHandled = false; return true }
                val now = SystemClock.uptimeMillis(); val dbl = now - lastCenterUp < 350; lastCenterUp = if (dbl) 0 else now
                return applyAction(KeyRouter.route(event.keyCode, false, dbl, top, routedBannerVisible())) || super.dispatchKeyEvent(event)
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode in SURF_KEYS) {
            val action = KeyRouter.route(event.keyCode, false, false, top, routedBannerVisible())
            if (action == Action.SurfUp || action == Action.SurfDown) { if (event.action == KeyEvent.ACTION_DOWN) applyAction(action); return true }
        }
        if (event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)
        val action = KeyRouter.route(event.keyCode, false, false, top, routedBannerVisible())
        return if (action == Action.None) super.dispatchKeyEvent(event) else applyAction(action)
    }

    private fun applyAction(a: Action): Boolean {
        val vm = viewModel
        when (a) {
            Action.SurfUp -> vm.surf(+1); Action.SurfDown -> vm.surf(-1)
            Action.ShowBanner -> vm.okOnPlayer(); Action.ContextMenu -> vm.push(Overlay.CONTEXT_MENU)
            Action.Previous -> vm.previous(); Action.Strip -> vm.push(Overlay.STRIP)
            Action.OpenChannels -> { vm.closeAll(); vm.push(Overlay.CHANNELS) }
            Action.OpenFavorites -> vm.openFavorites()
            Action.PlayPause -> vm.playPause(); is Action.Digit -> vm.favoriteByNumber(a.n)
            Action.Back -> vm.back { finish() }
            Action.None -> return false
        }
        return true
    }
}
