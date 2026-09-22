package com.chaskifood.app.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.discover.domain.DiscoverRepository
import com.chaskifood.app.feature.discover.domain.FoodItem
import com.chaskifood.app.feature.discover.domain.Restaurant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: DiscoverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Restaurant>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Restaurant>>> = _uiState.asStateFlow()

    init {
        loadRestaurants()
    }

    fun loadRestaurants() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getNearbyRestaurants()) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.message) { loadRestaurants() }
            }
        }
    }

    companion object {
        val recommendedFood = listOf(
            FoodItem(
                id = "f1",
                restaurantId = "1",
                name = "Lomo Saltado",
                description = "Trozos de lomo salteados con cebolla, tomate y papas fritas.",
                price = 28.0,
            ),
            FoodItem(
                id = "f2",
                restaurantId = "2",
                name = "Brunch Chaski",
                description = "Pan artesanal, huevo pochado, palta y café de especialidad.",
                price = 24.0,
            ),
            FoodItem(
                id = "f3",
                restaurantId = "3",
                name = "Maki Tempura",
                description = "Roll de camarón tempura con queso crema y anguila glaseada.",
                price = 32.0,
            ),
        )
    }
}