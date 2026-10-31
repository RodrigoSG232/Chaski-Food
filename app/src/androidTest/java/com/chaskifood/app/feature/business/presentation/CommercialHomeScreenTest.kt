package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.core.navigation.*
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.profile.presentation.AccountContent
import com.chaskifood.app.feature.main.presentation.CommercialBottomNavContent
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.*
import org.junit.Assert.*

class CommercialHomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val origin=AccessProvenance(AccessSession("fictional-test",1),1,1)
    private fun owner(suspended:Boolean=false, empty:Boolean=false, reason:String?="Motivo ficticio") = CommercialHomeState.Owner(origin,
        BusinessRequest(id="business",businessName="Negocio ficticio de pruebas"),suspended,reason,
        if(empty || suspended) emptyList() else listOf(BusinessStore(id="one",businessId="business")),
        canManageStores=!suspended,canManageCatalog=!suspended,canOperate=!suspended)
    private fun manager(multiple:Boolean=false,inactive:Boolean=false) = CommercialHomeState.Manager(origin,
        listOf(CommercialLocal(BusinessStore(id="one",businessId="business",name="Local Uno", status=if(inactive)StoreStatus.INACTIVE else StoreStatus.ACTIVE,
            operationalStatus=OperationalStatus.CLOSED),!inactive,false,"09:00–18:00")) +
            if(multiple) listOf(CommercialLocal(BusinessStore(id="two",businessId="business",name="Local Dos"),true,true,"08:00–20:00")) else emptyList(),"one")
    private fun show(state:CommercialHomeState) { compose.setContent { ChaskiTheme { CommercialHomeContent(state) } } }
    @Test fun responsableSuspendidoAbreSoloBandejaDelLocalAsignado() {
        val state=manager(inactive=true).let {it.copy(locals=it.locals.map{local->local.copy(canReadOrders=true)})}
        var selected:Pair<String,String>?=null
        compose.setContent { ChaskiTheme {CommercialHomeContent(state,onOrders={business,store->selected=business to store})} }
        compose.onNodeWithText("Pedidos pendientes").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("business" to "one",selected)}
        compose.onNodeWithText("Horarios y disponibilidad").assertDoesNotExist()
        compose.onNodeWithText("Aceptar pedido").assertDoesNotExist()
    }
    @Test fun propietarioAbreDestinosRealesMedianteCallbacks() {
        var stores=0; var catalog=0
        compose.setContent { ChaskiTheme { CommercialHomeContent(owner(),onStores={stores++},onCatalog={catalog++}) } }
        compose.onNodeWithText("Locales y responsables").performScrollTo().performClick()
        compose.onNodeWithText("Catálogo general").performScrollTo().performClick()
        assertEquals(1,stores);assertEquals(1,catalog)
        compose.onNodeWithText("Carrito").assertDoesNotExist(); compose.onNodeWithText("Pedidos recibidos").assertDoesNotExist()
    }
    @Test fun propietarioSinLocalesConservaCatalogoSinOperaciones() {
        show(owner(empty=true));compose.onNodeWithText("Aún no tienes locales").assertIsDisplayed()
        compose.onNodeWithText("Catálogo general").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Horarios de locales").assertDoesNotExist()
    }
    @Test fun suspendidoConMotivoNoMuestraGestiones() {
        show(owner(suspended=true)); compose.onNodeWithText("SUSPENDIDO").assertIsDisplayed()
        compose.onNodeWithText("Motivo ficticio").assertIsDisplayed()
        compose.onNodeWithText("Locales y responsables").assertDoesNotExist()
        compose.onNodeWithText("Catálogo general").assertDoesNotExist()
        compose.onNodeWithText("Ir a Cuenta").assertDoesNotExist()
    }
    @Test fun suspendidoSinMotivoTieneTextoExplicito() {
        show(owner(suspended=true,reason=null));compose.onNodeWithText("No se ha registrado un motivo de suspensión.").assertIsDisplayed()
    }
    @Test fun responsableDistingueEstadosYEnviaIdSeleccionado() {
        var actual:Pair<String?,String?>?=null
        compose.setContent { ChaskiTheme { CommercialHomeContent(manager(),onHours={b,s->actual=b to s}) } }
        compose.onNodeWithText("Mi local").assertIsDisplayed()
        compose.onNodeWithText("Estado administrativo: Activo").assertIsDisplayed()
        compose.onNodeWithText("Estado operativo: Cerrado").assertIsDisplayed()
        compose.onNodeWithText("Horarios y disponibilidad").performScrollTo().performClick()
        assertEquals("business" to "one",actual)
        compose.onNodeWithText("Locales y responsables").assertDoesNotExist()
    }
    @Test fun selectorCompactoSoloOfreceLocalesEntregadosYActualizaContenido() {
        compose.setContent {
            var state by remember { mutableStateOf(manager(multiple=true)) }
            ChaskiTheme { CommercialHomeContent(state,onSelectStore={state=state.copy(selectedId=it)}) }
        }
        compose.onNodeWithText("Mis locales").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cambiar local").performClick()
        compose.onNodeWithText("Local Dos").performClick()
        compose.onAllNodesWithText("Local Dos").assertCountEquals(2)
        compose.onNodeWithText("Local Uno").assertDoesNotExist()
    }
    @Test fun inactivoSigueIdentificadoPeroNoMuestraBotones() {
        show(manager(inactive=true));compose.onNodeWithText("INACTIVO").assertIsDisplayed()
        compose.onNodeWithText("Asignación: verificada").assertIsDisplayed()
        compose.onNodeWithText("Horarios y disponibilidad").assertDoesNotExist()
        compose.onNodeWithText("Oferta y precios").assertDoesNotExist()
    }
    @Test fun revocacionQuitaDatosYAccionesAnteriores() {
        lateinit var change:()->Unit
        compose.setContent {
            var state by remember { mutableStateOf(manager(multiple=true).copy(selectedId="two")) }
            change={state=state.copy(locals=state.locals.filter {it.store.id=="one"},selectedId="one",permissionsChanged=true)}
            ChaskiTheme {CommercialHomeContent(state)}
        }
        compose.runOnIdle {change()}
        compose.onNodeWithText("Local Dos").assertDoesNotExist()
        compose.onNodeWithText("Permisos actualizados. El local anterior ya no está disponible.").assertIsDisplayed()
    }
    @Test fun cambioDeCuentaNoConservaContenidoAnterior() {
        lateinit var change:()->Unit
        compose.setContent {
            var state:CommercialHomeState by remember {mutableStateOf(owner())}
            change={state=CommercialHomeState.Loading}
            ChaskiTheme {CommercialHomeContent(state)}
        }
        compose.runOnIdle {change()};compose.onNodeWithText("Negocio ficticio de pruebas").assertDoesNotExist()
    }
    @Test fun errorNoSeConfundeConListaVacia() {
        show(CommercialHomeState.Error(origin,"Error de prueba"));compose.onNodeWithText("Error de prueba").assertIsDisplayed()
        compose.onNodeWithText("Aún no tienes locales").assertDoesNotExist()
    }
    @Test fun cuentaAdminAgrupadaConservaSalidaEnPantallaPequena() {
        var signedOut=false
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {
            ChaskiTheme { Box(Modifier.width(320.dp).height(640.dp)) {AccountContent(verifiedAdmin=true,experience=CommercialExperience.OWNER,onSignOut={signedOut=true})} }
        } }
        compose.onNodeWithText("ADMINISTRACIÓN CHASKI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Operaciones de Locales").assertDoesNotExist()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick();assertTrue(signedOut)
    }
    @Test fun cuentaSinClaimNoMuestraAdministracion() {
        compose.setContent {ChaskiTheme {AccountContent(experience=CommercialExperience.MANAGER)}}
        compose.onNodeWithText("ADMINISTRACIÓN CHASKI").assertDoesNotExist()
    }
    @Test fun estadosRestrictivosNoContienenDatosPrivados() {
        compose.setContent {ChaskiTheme {AuthorizationPanelContent(AuthorizationPanel.CONFLICT)}}
        compose.onNodeWithText("Conflicto de autorizaciones").assertIsDisplayed()
        compose.onNodeWithText("Se requiere revisión autorizada. Volver a consultar no garantiza resolver el conflicto.").assertIsDisplayed()
    }
    @Test fun reconciliacionNoOfreceVinculacionAutomatica() {
        compose.setContent {ChaskiTheme {AuthorizationPanelContent(AuthorizationPanel.RECONCILIATION)}}
        compose.onNodeWithText("Reconciliación pendiente").assertIsDisplayed()
    }
    @Test fun barraComercialSoloTieneDosDestinos() {
        compose.setContent {ChaskiTheme {CommercialBottomNavContent("Mis locales",false){}}}
        compose.onNodeWithText("Mis locales").assertIsDisplayed();compose.onNodeWithText("Cuenta").assertIsDisplayed()
        compose.onNodeWithText("Buscar").assertDoesNotExist();compose.onNodeWithText("Pedidos").assertDoesNotExist()
    }
    @Test fun textoAmpliadoPermiteDesplazarseALasOperaciones() {
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
            ChaskiTheme {Box(Modifier.width(320.dp).height(600.dp)){CommercialHomeContent(owner())}}
        }}
        compose.onNodeWithText("Oferta y precios por local").performScrollTo().assertIsDisplayed()
    }
    private fun capture(state:CommercialHomeState,name:String,label:String) {
        compose.setContent {
            ChaskiTheme {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f)){CommercialHomeContent(state)}
                CommercialBottomNavContent(label,false){}
            }}
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(250) // Frame estable del Surface; no espera de red.
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        java.io.File(output,"hu34-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun capturaPropietarioConDatosFicticios() {capture(owner(),"owner","Mi negocio")}
    @Test fun capturaSuspendidoConDatosFicticios() {capture(owner(suspended=true),"suspended","Mi negocio")}
    @Test fun capturaResponsableConDatosFicticios() {capture(manager(multiple=true),"manager","Mis locales")}
    @Test fun ochoEstadosDeAutorizacionSinAccionesComerciales() {
        lateinit var show:(AuthorizationPanel)->Unit
        compose.setContent {
            var panel by remember {mutableStateOf(AuthorizationPanel.VERIFYING)}
            show={panel=it}
            ChaskiTheme {AuthorizationPanelContent(panel)}
        }
        val titles=listOf("Verificando permisos","No pudimos comprobar tus permisos","Sin conexión","Conflicto de autorizaciones",
            "Reconciliación pendiente","Permisos no disponibles","Cambio de cuenta","Contexto no disponible")
        AuthorizationPanel.entries.forEachIndexed {index,panel ->
            compose.runOnIdle {show(panel)}
            compose.onNodeWithText(titles[index]).assertIsDisplayed()
            compose.onNodeWithText("Locales y responsables").assertDoesNotExist()
        }
    }    @Test fun capturaSelectorAbiertoEnPantallaEstrechaYTextoAmpliado() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ChaskiTheme {Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()){
                    CommercialHomeContent(manager(multiple=true))
                }}
            }
        }
        compose.onNodeWithContentDescription("Cambiar local").performClick()
        compose.onNodeWithText("Local Dos").assertIsDisplayed()
        saveVisual("selector-open-320-130")
    }
    @Test fun capturaCuentaAdministrativaEnPantallaEstrechaYTextoAmpliado() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ChaskiTheme {Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()){
                    AccountContent(verifiedAdmin=true,experience=CommercialExperience.OWNER)
                }}
            }
        }
        compose.onNodeWithText("ADMINISTRACIÓN CHASKI").performScrollTo().assertIsDisplayed()
        saveVisual("admin-320-130")
    }
    @Test fun capturaPanelRestrictivoEnPantallaEstrechaYTextoAmpliado() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ChaskiTheme {Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()){
                    AuthorizationPanelContent(AuthorizationPanel.CONFLICT)
                }}
            }
        }
        compose.onNodeWithText("Volver a comprobar").assertIsDisplayed()
        saveVisual("conflict-320-130")
    }
    private fun saveVisual(name:String) {
        compose.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(700)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null),"hu34-3b3-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }}