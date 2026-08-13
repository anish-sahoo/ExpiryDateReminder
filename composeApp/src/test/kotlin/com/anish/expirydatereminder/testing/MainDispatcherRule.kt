package com.anish.expirydatereminder.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Points `Dispatchers.Main` at a test dispatcher for the duration of a test.
 *
 * `viewModelScope` is hardwired to `Dispatchers.Main`, which does not exist off-device, so
 * without this every view model test fails at the first `launch` rather than at whatever it
 * was actually checking.
 *
 * Unconfined by default so work started in a view model runs eagerly: these tests are about
 * what the state ends up as, not about scheduling, and a standard dispatcher would need an
 * `advanceUntilIdle` after every call for no added confidence.
 */
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
