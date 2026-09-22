package com.chaskifood.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

@Composable
fun <T> collectChaskiState(flow: StateFlow<T>): State<T> {
    val state = remember { mutableStateOf(flow.value) }
    LaunchedEffect(flow) {
        flow.collect { state.value = it }
    }
    return state
}