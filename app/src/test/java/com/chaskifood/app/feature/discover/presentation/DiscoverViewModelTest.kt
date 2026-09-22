package com.chaskifood.app.feature.discover.presentation

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.discover.domain.DiscoverRepository
import com.chaskifood.app.feature.discover.domain.Restaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadRestaurants expone estado Success con restaurantes`() = runTest(dispatcher.scheduler) {
        val repository = object : DiscoverRepository {
            override suspend fun getNearbyRestaurants(): ApiResult<List<Restaurant>> =
                ApiResult.Success(listOf(sampleRestaurant()))
        }

        val viewModel = DiscoverViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(1, (state as UiState.Success).data.size)
    }

    @Test
    fun `loadRestaurants falla y expone estado Error`() = runTest(dispatcher.scheduler) {
        val repository = object : DiscoverRepository {
            override suspend fun getNearbyRestaurants(): ApiResult<List<Restaurant>> =
                ApiResult.Failure("No se pudo conectar con el servidor")
        }

        val viewModel = DiscoverViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("No se pudo conectar con el servidor", (state as UiState.Error).message)
    }

    @Test
    fun `retry vuelve a cargar despues de un error`() = runTest(dispatcher.scheduler) {
        var attempts = 0
        val repository = object : DiscoverRepository {
            override suspend fun getNearbyRestaurants(): ApiResult<List<Restaurant>> {
                attempts++
                return if (attempts == 1) {
                    ApiResult.Failure("Error temporal")
                } else {
                    ApiResult.Success(listOf(sampleRestaurant()))
                }
            }
        }

        val viewModel = DiscoverViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is UiState.Error)

        val retry = (viewModel.uiState.value as UiState.Error).retry
        retry?.invoke()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Success)
        assertEquals(2, attempts)
    }

    private fun sampleRestaurant() = Restaurant(
        id = "1",
        name = "Bon Appetit",
        cuisine = "Peruana · Criolla",
        rating = 4.8,
        deliveryTimeMin = 20,
        deliveryFee = 5.0,
    )
}