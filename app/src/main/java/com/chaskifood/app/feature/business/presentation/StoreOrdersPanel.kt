package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.checkout.data.CheckoutRemote
import com.chaskifood.app.feature.checkout.data.StoreOrder
import com.chaskifood.app.ui.theme.ChaskiBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface StoreOrdersState {
    data object Loading : StoreOrdersState
    data class Ready(val orders: List<StoreOrder>) : StoreOrdersState
    data class Error(val message: String) : StoreOrdersState
}

@HiltViewModel
class StoreOrdersViewModel @Inject constructor(private val remote: CheckoutRemote) : ViewModel() {
    private val _state = MutableStateFlow<StoreOrdersState>(StoreOrdersState.Loading)
    val state = _state.asStateFlow()
    private var storeId: String? = null
    private var request: Job? = null

    fun load(id: String) {
        if (id.isBlank()) return
        if (storeId != id) {
            storeId = id
            _state.value = StoreOrdersState.Loading
        }
        request?.cancel()
        request = viewModelScope.launch {
            try {
                _state.value = StoreOrdersState.Ready(remote.storeOrders(id))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.value = StoreOrdersState.Error(remote.message(error)) }
        }
    }
}

@Composable
fun StoreOrdersPanel(
    storeId: String,
    storeName: String,
    onDismiss: () -> Unit,
    viewModel: StoreOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(storeId) {
        while (isActive) {
            viewModel.load(storeId)
            delay(45_000)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = ChaskiBackground) {
            Column {
                AuthAppBar(onBackClick = onDismiss)
                Text("Pedidos · $storeName", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                TextButton(onClick = { viewModel.load(storeId) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("ACTUALIZAR PEDIDOS")
                }
                when (val current = state) {
                    StoreOrdersState.Loading -> CircularProgressIndicator(Modifier.padding(20.dp))
                    is StoreOrdersState.Error -> Column(Modifier.padding(20.dp)) {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.load(storeId) }) { Text("REINTENTAR") }
                    }
                    is StoreOrdersState.Ready -> {
                        if (current.orders.isEmpty()) Text("Este local todavía no tiene pedidos.", Modifier.padding(20.dp))
                        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(current.orders, key = StoreOrder::orderId) { order -> OrderCard(order) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: StoreOrder) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pedido ${order.orderId.take(8).uppercase()}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(if (order.status == "PENDING_CONFIRMATION") "Pendiente de confirmación" else order.status)
            if (order.createdAtMillis > 0) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                DateFormat.SHORT).format(Date(order.createdAtMillis)), style = MaterialTheme.typography.bodySmall)
            order.items.forEach { item ->
                Text("${item.quantity} × ${item.name} · ${formatCartPrice(item.lineTotalMinor)}")
            }
            order.address?.let { address ->
                Text("Entrega: ${address.addressText}")
                if (address.reference.isNotBlank()) Text("Referencia: ${address.reference}")
                if (address.instructions.isNotBlank()) Text("Indicaciones: ${address.instructions}")
            }
            Text("Total: ${formatCartPrice(order.totalMinor)}", fontWeight = FontWeight.Bold)
            Text(if (order.paymentMethod == "CASH_ON_DELIVERY") "Pago: efectivo al recibir" else order.paymentMethod,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
