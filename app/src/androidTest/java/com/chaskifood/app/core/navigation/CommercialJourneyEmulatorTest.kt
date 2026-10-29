package com.chaskifood.app.core.navigation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.R
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.MainActivity
import com.chaskifood.app.core.access.data.AccessContextPhase
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import androidx.test.platform.app.InstrumentationRegistry

/** Recorridos de UI con Hilt/repositorios/Rules reales, solo contra el proyecto demo. */
class CommercialJourneyEmulatorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val project="demo-chaski-hu15"
    private val prefix="journey34-${UUID.randomUUID()}"
    private val password="fictional-local-password"
    private val auth get()=FirebaseAuth.getInstance()
    private val db get()=FirebaseFirestore.getInstance()
    private lateinit var access:AccessNavigationViewModel
    private lateinit var nav:NavHostController
    private data class Actor(val uid:String,val email:String,val token:String)
    private fun request(port:Int,path:String,body:JSONObject):JSONObject {
        val c=URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST";c.connectTimeout=10_000;c.readTimeout=15_000
            c.setRequestProperty("Authorization","Bearer owner");c.setRequestProperty("Content-Type","application/json");c.doOutput=true
            c.outputStream.use{it.write(body.toString().toByteArray())}
            check(c.responseCode==200){"Fallo de fixture demo en puerto $port"}
            return c.inputStream.bufferedReader().use{JSONObject(it.readText())}
        } finally {c.disconnect()}
    }
    private fun actor(label:String,admin:Boolean=false):Actor {
        val email="$prefix-$label@example.test"
        val uid=request(9099,"/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",JSONObject().put("email",email).put("password",password).put("returnSecureToken",true)).getString("localId")
        request(9099,"/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update",JSONObject().put("localId",uid).put("emailVerified",true).put("customAttributes",if(admin)"{\"admin\":true}" else "{}"))
        val token=request(9099,"/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key",JSONObject().put("email",email).put("password",password).put("returnSecureToken",true)).getString("idToken")
        return Actor(uid,email,token)
    }
    private fun operation(actor:Actor,action:String,revision:Long,fields:JSONObject):Long {
        val c=URL("http://127.0.0.1:5001/$project/southamerica-west1/foodApi/api/v1/access/operations").openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST";c.connectTimeout=10_000;c.readTimeout=15_000;c.doOutput=true
            c.setRequestProperty("Authorization","Bearer ${actor.token}");c.setRequestProperty("Content-Type","application/json")
            fields.put("action",action).put("operationId",UUID.randomUUID().toString()).put("expectedRevision",revision)
            c.outputStream.use{it.write(fields.toString().toByteArray())}
            check(c.responseCode==200){"Operación demo rechazada: $action/${c.responseCode}"}
            return c.inputStream.bufferedReader().use{JSONObject(it.readText()).getLong("revision")}
        }finally{c.disconnect()}
    }
    private fun value(v:Any):JSONObject=when(v){
        is Boolean->JSONObject().put("booleanValue",v)
        is Number->JSONObject().put("doubleValue",v)
        is List<*>->JSONObject().put("arrayValue",JSONObject().put("values",JSONArray(v.map{value(it!!)})))
        is Map<*,*>->JSONObject().put("mapValue",JSONObject().put("fields",fields(v.entries.associate{it.key.toString() to it.value!!})))
        else->JSONObject().put("stringValue",v.toString())
    }
    private fun fields(data:Map<String,Any>)=JSONObject().also{json->data.forEach{(k,v)->json.put(k,value(v))}}
    private fun seed(path:String,data:Map<String,Any>){request(8089,"/v1/projects/$project/databases/(default)/documents:commit",JSONObject().put("writes",JSONArray().put(JSONObject().put("update",JSONObject().put("name","projects/$project/databases/(default)/documents/$path").put("fields",fields(data))))))}
    private fun business(owner:Actor,status:String="APPROVED",reason:String?=null)=mapOf<String,Any>("ownerUid" to owner.uid,"businessName" to "Negocio de recorrido","status" to status,"ruc" to "12345678901")+(reason?.let{mapOf("suspensionReason" to it)}?:emptyMap())
    private fun store(owner:Actor,id:String,name:String){seed("stores/$id",mapOf("businessId" to owner.uid,"name" to name,"status" to "ACTIVE","operationalStatus" to "CLOSED","operatingHours" to com.chaskifood.app.feature.business.domain.DayOfWeekEnum.entries.map{mapOf("dayOfWeek" to it.name,"openTime" to "08:00","closeTime" to "22:00","enabled" to true)}))}
    @Before fun localOnly(){
        check(BuildConfig.FIREBASE_MODE=="LOCAL"&&BuildConfig.EMULATOR_HOST=="127.0.0.1")
        check(FirebaseApp.getInstance().options.projectId==project)
        compose.runOnUiThread{access=androidx.lifecycle.ViewModelProvider(compose.activity)[AccessNavigationViewModel::class.java]}
    }
    @After fun logout(){auth.signOut()}
    private suspend fun login(actor:Actor,experience:CommercialExperience){
        auth.signOut();auth.signInWithEmailAndPassword(actor.email,password).await()
        withTimeout(25_000){access.states.first{(it.resolution as? AccessResolution.Resolved)?.let{r->r.experience==experience&&r.identity.session.uid==actor.uid}==true}}
    }
    private fun main(){compose.runOnUiThread{compose.activity.setContent{nav=rememberNavController();ChaskiTheme{ChaskiNavHost(nav,ChaskiDestinations.MAIN)}}}}
    private fun visible(text:String){compose.waitUntil(25_000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun back(){compose.runOnIdle{nav.popBackStack()}}
    private suspend fun refresh(){val old=access.current().origin;access.refresh();withTimeout(25_000){access.states.first{it.phase==AccessContextPhase.RESOLVED&&it.origin!=old}}}
    private fun capture(name:String){
        compose.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(700)
        val output=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val size=compose.runOnIdle{compose.activity.window.decorView.let{it.width to it.height}}
        val bitmap=android.graphics.Bitmap.createBitmap(size.first,size.second,android.graphics.Bitmap.Config.ARGB_8888)
        val ready=java.util.concurrent.CountDownLatch(1)
        var result=-1
        compose.runOnUiThread{
            compose.activity.window.decorView.invalidate()
            android.view.PixelCopy.request(compose.activity.window,bitmap,{status->result=status;ready.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))
        }
        check(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)&&result==android.view.PixelCopy.SUCCESS){"No se pudo capturar la ventana de prueba"}
        java.io.File(output,"hu34-3b3-$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun propietarioConsultaResponsablesYGuardaHorarioDesdePantallaReal()=runBlocking{
        val admin=actor("admin",true);val owner=actor("owner")
        operation(admin,"PREPARE",0,JSONObject().put("email",owner.email))
        seed("business_requests/${owner.uid}",business(owner));val id="${owner.uid}-one";store(owner,id,"Local propietario")
        login(owner,CommercialExperience.OWNER);main();visible("Negocio de recorrido");capture("owner-real")
        compose.onNodeWithText("Locales y responsables").performScrollTo().performClick();visible("Locales (1)")
        compose.onNodeWithText("Responsables (0)").performClick();visible("No hay responsables asignados aún. Presiona el botón '+' para asociar un nuevo responsable a tus locales.")
        back();visible("Horarios de locales");compose.onNodeWithText("Horarios de locales").performScrollTo().performClick()
        visible("Local propietario");compose.onNodeWithText("Configurar Horario Semanal").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Atiende el Lunes").performScrollTo().performClick()
        compose.onNodeWithText("GUARDAR HORARIO").performScrollTo().performClick();visible("Horario de atención guardado correctamente.")
        val saved=db.document("stores/$id").get(Source.SERVER).await().get("operatingHours") as List<*>
        assertEquals(false,(saved.first() as Map<*,*>)["enabled"])
        back();visible("Catálogo general");compose.onNodeWithText("Catálogo general").performScrollTo().performClick();visible("Menú y Productos")
        compose.onNodeWithContentDescription("Agregar").performClick()
        compose.onNodeWithText("Nombre de la Categoría *").performTextInput("Categoría de recorrido")
        compose.onNodeWithText("GUARDAR").performClick();visible("Categoría de recorrido")
        val categories=db.collection("categories").whereEqualTo("businessId",owner.uid).get(Source.SERVER).await()
        assertEquals("Categoría de recorrido",categories.documents.single().getString("name"))
        back();visible("Negocio de recorrido");compose.onNodeWithText("Buscar").assertDoesNotExist()
    }
    @Test fun responsableSeleccionaLocalGuardaOfertaYRevocacionRetiraSuAcceso()=runBlocking{
        val admin=actor("admin",true);val owner=actor("owner");val manager=actor("manager")
        operation(admin,"PREPARE",0,JSONObject().put("email",owner.email))
        val revision=operation(admin,"PREPARE",0,JSONObject().put("email",manager.email))
        seed("business_requests/${owner.uid}",business(owner));val one="${owner.uid}-one";val two="${owner.uid}-two"
        store(owner,one,"Local Uno recorrido");store(owner,two,"Local Dos recorrido")

        val product="${owner.uid}-product";val category="${owner.uid}-category"
        seed("categories/$category",mapOf("businessId" to owner.uid,"name" to "Categoría demo","isActive" to true))
        seed("products/$product",mapOf("businessId" to owner.uid,"categoryId" to category,"categoryName" to "Categoría demo","name" to "Producto recorrido","price" to 10.0,"isActive" to true))
        login(manager,CommercialExperience.CLIENT);main();visible("Buscar")
        val next=operation(owner,"ASSIGN",revision,JSONObject().put("businessId",owner.uid).put("email",manager.email).put("storeIds",JSONArray(listOf(one,two))).put("fullName","Responsable ficticio").put("phone","000"))
        refresh();visible("Local Uno recorrido")
        assertEquals(CommercialExperience.MANAGER,(access.current().resolution as AccessResolution.Resolved).experience)
        compose.onNodeWithContentDescription("Cambiar local").performClick();compose.onNodeWithText("Local Dos recorrido").performClick();capture("manager-many-real")
        compose.onNodeWithText("Horarios y disponibilidad").performScrollTo().performClick();visible("Local Dos recorrido")
        compose.onNodeWithText("Local Uno recorrido").assertDoesNotExist()
        compose.onNodeWithText("ABIERTO").performScrollTo().performClick();visible("Local abierto para recibir pedidos.")
        assertEquals("OPEN",db.document("stores/$two").get(Source.SERVER).await().getString("operationalStatus"))
        assertEquals("CLOSED",db.document("stores/$one").get(Source.SERVER).await().getString("operationalStatus"))
        back();visible("Local Dos recorrido")
        compose.onNodeWithText("Oferta y precios").performScrollTo().performClick();visible("Producto recorrido")
        compose.onNodeWithText("Precio en este local (S/)").performTextReplacement("12.50")
        compose.onNodeWithContentDescription("Disponibilidad de Producto recorrido").performScrollTo().performClick()
        compose.onNodeWithText("GUARDAR CONFIGURACIÓN").performScrollTo().performClick();visible("Configuración guardada correctamente.")
        val config=db.document("store_product_configs/${two}_$product").get(Source.SERVER).await()
        assertEquals(two,config.getString("storeId"));assertEquals(12.5,config.getDouble("price")!!,0.0)
        assertFalse(db.document("store_product_configs/${one}_$product").get(Source.SERVER).await().exists())
        back();visible("Local Dos recorrido")
        val partial=operation(owner,"ASSIGN",next,JSONObject().put("businessId",owner.uid).put("email",manager.email).put("storeIds",JSONArray(listOf(one))).put("fullName","Responsable ficticio").put("phone","000"))
        refresh();visible("Local Uno recorrido");compose.onNodeWithText("Local Dos recorrido").assertDoesNotExist();capture("manager-one-real")
        compose.runOnIdle{nav.navigate(ChaskiDestinations.STORE_OPERATIONS+"?storeId=$two")}
        compose.waitUntil(25_000){nav.currentDestination?.route==ChaskiDestinations.MAIN}
        assertFalse(access.allows(ChaskiDestinations.STORE_OPERATIONS,storeId=two))
        assertFalse(access.allows(ChaskiDestinations.MANAGE_CATALOG));assertFalse(access.allows(ChaskiDestinations.CART))
        operation(owner,"REVOKE",partial,JSONObject().put("businessId",owner.uid).put("email",manager.email))
        refresh()
        assertEquals(CommercialExperience.CLIENT,(access.current().resolution as AccessResolution.Resolved).experience)
        assertFalse(access.allows(ChaskiDestinations.STORE_OPERATIONS,storeId=one))
        compose.onNodeWithText("Local Uno recorrido").assertDoesNotExist()
    }
    @Test fun suspendidoRealConYSinMotivoNoConservaGestiones()=runBlocking{
        val admin=actor("admin",true);val owner=actor("owner")
        operation(admin,"PREPARE",0,JSONObject().put("email",owner.email))
        seed("business_requests/${owner.uid}",business(owner,"SUSPENDED","Motivo de revisión local"))
        login(owner,CommercialExperience.OWNER);main();visible("Motivo de revisión local")
        compose.onAllNodesWithText("Mi negocio").assertCountEquals(2);compose.onNodeWithText("Locales y responsables").assertDoesNotExist();capture("suspended")
        seed("business_requests/${owner.uid}",business(owner,"SUSPENDED"));refresh();visible("No se ha registrado un motivo de suspensión.")
        capture("suspended-no-reason");assertFalse(access.allows(ChaskiDestinations.CART));assertFalse(access.allows(ChaskiDestinations.MANAGE_STORES))
    }
    @Test fun administradorUsaTresDestinosSinObtenerPropiedadYRetiroClaimOcultaSeccion()=runBlocking{
        val admin=actor("admin",true)
        operation(admin,"PREPARE",0,JSONObject().put("email",admin.email))
        login(admin,CommercialExperience.CLIENT);main();visible("Cuenta")
        compose.onNodeWithText("Cuenta").performClick();visible("ADMINISTRACIÓN CHASKI")
        val destinations=listOf(R.string.admin_hu06_title to ChaskiDestinations.ADMIN_BUSINESS_REVIEW,
            R.string.admin_hu07_title to ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION,
            R.string.admin_audit_title to ChaskiDestinations.ADMIN_AUDIT_LOG)
        for((label,route) in destinations){
            compose.onNodeWithText(compose.activity.getString(label)).performScrollTo().performClick()
            compose.waitUntil(25_000){nav.currentDestination?.route==route}
            assertTrue(access.allows(route));assertFalse(access.allows(ChaskiDestinations.MANAGE_STORES))
            back();visible("ADMINISTRACIÓN CHASKI")
        }
        request(9099,"/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update",JSONObject().put("localId",admin.uid).put("customAttributes","{}"))
        refresh()
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("ADMINISTRACIÓN CHASKI").assertDoesNotExist()
        assertFalse(access.allows(ChaskiDestinations.ADMIN_BUSINESS_REVIEW))
    }
    @Test fun propietarioSinLocalesConsultaVacioRealYConservaCatalogo()=runBlocking{
        val admin=actor("admin",true);val owner=actor("owner")
        operation(admin,"PREPARE",0,JSONObject().put("email",owner.email))
        login(owner,CommercialExperience.CLIENT);main();visible("Buscar")
        seed("business_requests/${owner.uid}",business(owner))
        refresh();visible("Aún no tienes locales")
        assertEquals(CommercialExperience.OWNER,(access.current().resolution as AccessResolution.Resolved).experience)
        compose.onNodeWithText("Horarios de locales").assertDoesNotExist()
        compose.onNodeWithText("Catálogo general").performScrollTo().performClick();visible("Menú y Productos")
        back();visible("Aún no tienes locales");capture("owner-empty")
    }
}
