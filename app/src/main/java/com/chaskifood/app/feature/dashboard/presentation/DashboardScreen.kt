package com.chaskifood.app.feature.dashboard.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.dashboard.domain.DashboardData
import com.chaskifood.app.feature.dashboard.domain.DashboardDay
import com.chaskifood.app.feature.dashboard.domain.DashboardStore
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.github.mikephil.charting.compose.BarChart
import com.github.mikephil.charting.compose.LineChart
import com.github.mikephil.charting.compose.PieChart
import com.github.mikephil.charting.data.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(onBack:()->Unit,viewModel:DashboardViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar={TopAppBar(title={Text("Dashboard comercial")},navigationIcon={
        IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}
    })}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Indicadores de pedidos registrados",style=MaterialTheme.typography.bodyMedium)
            Text("Período: días calendario de Lima, incluido hoy",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(7,30,90).forEach{days->FilterChip(selected=state.days==days,onClick={viewModel.selectDays(days)},label={Text("$days días")})}
            }
            state.data?.let{data->
                Text("Local",fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=state.storeId==null,onClick={viewModel.selectStore(null)},label={Text("Todos autorizados")})
                    data.stores.forEach{store->FilterChip(selected=state.storeId==store.storeId,
                        onClick={viewModel.selectStore(store.storeId)},label={Text(store.name)})}
                }
                if(data.stores.isEmpty())Text("Aún no hay locales para este negocio.")
                DashboardCharts(data)
            }
            if(state.loading)CircularProgressIndicator()
            if(state.message!=null)Text(state.message!!,color=MaterialTheme.colorScheme.error)
            if(!state.loading)OutlinedButton(onClick=viewModel::refresh,enabled=!state.denied){Text("Actualizar")}
        }
    }
}

@Composable private fun DashboardCharts(data:DashboardData) {
    val chartColor=ChaskiPrimary.toArgb()
    val secondary=MaterialTheme.colorScheme.tertiary.toArgb()
    val ordersData=remember(data.series,chartColor){BarData(BarDataSet(data.series.mapIndexed{i,d->
        BarEntry(i.toFloat(),d.orders.toFloat())},"Pedidos creados").apply{color=chartColor})}
    val salesData=remember(data.series,chartColor){LineData(LineDataSet(data.series.mapIndexed{i,d->
        Entry(i.toFloat(),d.salesMinor/100f)},"Productos entregados (S/)").apply{color=chartColor;lineWidth=2f})}
    val decisionsData=remember(data.accepted,data.rejected,chartColor,secondary){PieData(PieDataSet(listOf(
        PieEntry(data.accepted.toFloat(),"Aceptados"),PieEntry(data.rejected.toFloat(),"Rechazados")),
        "Decisiones del local").apply{colors=listOf(chartColor,secondary)})}
    MetricCard("Pedidos",data.orders.toString(),"Creados en el período seleccionado") {
        BarChart(data=ordersData,modifier=Modifier.fillMaxWidth().height(210.dp),
            contentDescription="Pedidos creados por día: ${data.orders} en total",setup={
                description.isEnabled=false;axisRight.isEnabled=false;axisLeft.axisMinimum=0f
            })
    }
    MetricCard("Ventas de productos",money(data.salesMinor),
        "Suma del subtotal de pedidos entregados con éxito; pago simulado. Fecha de entrega.") {
        LineChart(data=salesData,modifier=Modifier.fillMaxWidth().height(210.dp),
            contentDescription="Subtotal de productos entregados por día: ${money(data.salesMinor)}",setup={
                description.isEnabled=false;axisRight.isEnabled=false;axisLeft.axisMinimum=0f
            })
    }
    val decisions=data.accepted+data.rejected
    MetricCard("Aceptación de pedidos",data.acceptanceRate?.let{"${it}%"}?:"Sin decisiones",
        "${data.accepted} aceptados · ${data.rejected} rechazados de $decisions decididos") {
        if(decisions>0)PieChart(data=decisionsData,modifier=Modifier.fillMaxWidth().height(210.dp),
            contentDescription="${data.accepted} pedidos aceptados y ${data.rejected} rechazados",setup={description.isEnabled=false})
        else Text("Sin decisiones en este período.")
    }
}

@Composable private fun MetricCard(title:String,value:String,explanation:String,content:@Composable ()->Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(value,style=MaterialTheme.typography.headlineMedium,color=ChaskiPrimary)
        Text(explanation,style=MaterialTheme.typography.bodySmall)
        content()
    }}
}
private fun money(minor:Long)=String.format(Locale.Builder().setLanguage("es").setRegion("PE").build(),"S/ %,.2f",minor/100.0)

@Preview(showBackground=true,widthDp=360,heightDp=900)
@Composable private fun DashboardPreview() {
    val sample=DashboardData("preview",7,null,0,listOf(DashboardStore("preview-store","Local ficticio")),
        4,2400,3,1,75.0,listOf(0,1,2,3,4,5,6).map { index ->
            DashboardDay("2026-11-${(index+2).toString().padStart(2,'0')}",if(index%2==0)1 else 0,
                if(index%2==0)index*200L else 0) })
    MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Dashboard comercial",style=MaterialTheme.typography.headlineSmall)
        DashboardCharts(sample)
    }}
}
