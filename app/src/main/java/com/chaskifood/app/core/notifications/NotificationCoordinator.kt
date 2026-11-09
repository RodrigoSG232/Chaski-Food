package com.chaskifood.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.MainActivity
import com.chaskifood.app.R
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Retrofit
import java.util.UUID

@Singleton
class NotificationCoordinator @Inject internal constructor(@ApplicationContext private val app:Context,
    private val sessions:CartSessionSource,private val tokens:BuyerTokenSource,private val access:SharedAccessContext,
    @AccessCoroutineScope private val scope:CoroutineScope,retrofit:Retrofit,private val push:PushTokenSource) {
    private val api=retrofit.newBuilder().baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(DeviceApi::class.java)
    private val preferences=app.getSharedPreferences("notification_installation",Context.MODE_PRIVATE)
    private val installationId=preferences.getString("id",null)?:UUID.randomUUID().toString().also {preferences.edit().putString("id",it).apply()}
    private val registrar=DeviceRegistrar(api,sessions,tokens,installationId,{withContext(Dispatchers.IO){nextBindingVersion()}},::pushToken)
    private val dedup=NoticeDeduplicator()
    private var started=false
    // Main-scope state: the session whose binding this process already refreshed, and a nudge for rotated tokens.
    @Volatile private var registered:com.chaskifood.app.feature.cart.data.CartSession?=null
    private val tokenEpoch=MutableStateFlow(0)
    private var previousSession=sessions.current()
    private val mutablePending=MutableStateFlow<OrderNotice?>(null)
    val pending:StateFlow<OrderNotice?> = mutablePending.asStateFlow()
    private var pendingSession:com.chaskifood.app.feature.cart.data.CartSession?=null
    fun open(intent:Intent?){
        val notice:OrderNotice=when(intent?.action){
            "NEW_ORDER" -> {
                val data=listOf("eventId","orderId","businessId","storeId","recipientUid")
                    .associateWith {intent.getStringExtra(it).orEmpty()} + ("type" to "NEW_ORDER")
                NewOrderNotice.parse(data)
            }
            "BUYER_ORDER_NOTICE" -> {
                val data=listOf("eventId","orderId","recipientUid","type")
                    .associateWith {intent.getStringExtra(it).orEmpty()}
                BuyerOrderNotice.parse(data)
            }
            else -> null
        }?:return
        val session=sessions.current()
        if(session.uid!=notice.recipientUid)return
        pendingSession=session;mutablePending.value=notice
    }
    fun takeAuthorized():OrderNotice? {
        val notice=mutablePending.value?:return null
        if(sessions.current()!=pendingSession){clearPending();return null}
        var accepted:OrderNotice?=null
        access.runIfCurrent {state->
            // A stale RESOLVED emission must not discard a hint while current facts are being refreshed.
            if(state.phase==AccessContextPhase.RESOLVED){
                val resolved=state.resolution as? AccessResolution.Resolved
                if(resolved?.identity?.session?.uid==notice.recipientUid && when(notice){
                    is NewOrderNotice -> resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS,
                        AccessScope.Store(notice.businessId,notice.storeId))
                    is BuyerOrderNotice -> resolved.capabilities.allows(AccessCapability.READ_PERSONAL_PURCHASES,
                        AccessScope.Account(notice.recipientUid))
                })accepted=notice
                clearPending()
            }
        }
        return accepted
    }
    fun clearPending(){mutablePending.value=null;pendingSession=null}

    private val manager=app.getSystemService(NotificationManager::class.java)
    fun start(){
        if(started)return
        started=true
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"Nuevos pedidos",NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(BUYER_CHANNEL,"Actualizaciones de pedidos",NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch {sessions.sessions.collectLatest { session->
            if(previousSession!=session){manager.cancelAll();dedup.clear();if(pendingSession!=session)clearPending()}
            previousSession=session
        }}
        // Any resolved account can receive own-order hints; store scope is checked again for manager sends.
        scope.launch {combine(sessions.sessions,access.states,tokenEpoch){session,state,_->session to state}.collectLatest {(session,state)->
            if(registered!=session)registered=null
            val eligible=NoticePolicy.canRegister(session.uid,state)
            if(eligible && registered==null){
                var retryDelayMs=15_000L
                while(sessions.isCurrent(session) && registered!=session){
                    when(registrar.register(session)){
                        RegistrationOutcome.REGISTERED->{
                            if(sessions.isCurrent(session))registered=session
                            break
                        }
                        RegistrationOutcome.FAILED->{
                            delay(retryDelayMs)
                            retryDelayMs=(retryDelayMs*2).coerceAtMost(60_000L)
                        }
                        RegistrationOutcome.NOT_ELIGIBLE,RegistrationOutcome.SESSION_CHANGED->break
                    }
                }
            }else if(!eligible && state.phase==AccessContextPhase.RESOLVED && registered==session){
                registrar.unregister(session,signOut=false);registered=null
            }
        }}
    }
    /** FirebaseMessagingService.onNewToken: rebind the rotated token for the current resolved session only. */
    fun tokenChanged(@Suppress("UNUSED_PARAMETER") token:String){
        // LOCAL never provisions real tokens; the value is read again through PushTokenSource, never stored here.
        if(BuildConfig.FIREBASE_MODE=="LOCAL")return
        scope.launch {registered=null;tokenEpoch.value++}
    }
    /** Synthetic LOCAL token, never an FCM network request; CONECTADO asks Firebase Messaging for the current token. */
    private suspend fun pushToken():String=if(BuildConfig.FIREBASE_MODE=="LOCAL")"local-fixture:$installationId" else push.token()
    @Synchronized private fun nextBindingVersion():Long {
        val version=Math.addExact(preferences.getLong("bindingVersion",0),1)
        check(preferences.edit().putLong("bindingVersion",version).commit())
        return version
    }
    /** Called before FirebaseAuth.signOut. Failed offline removal never blocks logout or enables UI access. */
    suspend fun beforeSignOut(){
        manager.cancelAll();dedup.clear();clearPending()
        registrar.unregister(sessions.current(),signOut=true)
        registered=null
        // A new token for the next account; an offline failure leaves a binding the server rebinds or the UID check drops.
        if(BuildConfig.FIREBASE_MODE!="LOCAL")try {withTimeoutOrNull(5000){push.delete()}}
            catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    internal fun allowed(notice:NewOrderNotice):Boolean {
        val session=sessions.current()
        if(session.uid!=notice.recipientUid)return false
        var permitted=false
        val published=access.runIfCurrent { state->
            val resolved=state.resolution as? AccessResolution.Resolved
            permitted=resolved?.identity?.session?.uid==session.uid &&
                resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS,AccessScope.Store(notice.businessId,notice.storeId))
        }
        return published && permitted
    }
    /** Runs on the FCM worker thread with local, synchronous checks only: no network wait inside onMessageReceived.
     * The persisted Firebase Auth UID must match the recipient; opening the hint reauthorizes against the HTTP inbox.
     * Push delivery is never guaranteed, so nothing here is required for orders to remain available. */
    fun receive(data:Map<String,String>):Boolean {
        val notice:OrderNotice=NewOrderNotice.parse(data)?:BuyerOrderNotice.parse(data)?:return false
        val visible=when(notice){
            is NewOrderNotice -> NoticePolicy.canShow(sessions.current().uid,notice,access.currentState())
            is BuyerOrderNotice -> NoticePolicy.canShowBuyer(sessions.current().uid,notice,access.currentState())
        }
        if(!visible)return false
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(app,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        if(!dedup.accept(notice.recipientUid,notice.eventId))return false
        val intent=Intent(app,MainActivity::class.java)
            .setAction(if(notice is NewOrderNotice)"NEW_ORDER" else "BUYER_ORDER_NOTICE")
            .setData(android.net.Uri.parse("chaski-notice://${notice.eventId}"))
            .putExtra("eventId",notice.eventId).putExtra("orderId",notice.orderId)
            .putExtra("recipientUid",notice.recipientUid)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if(notice is NewOrderNotice){intent.putExtra("businessId",notice.businessId).putExtra("storeId",notice.storeId)}
        if(notice is BuyerOrderNotice)intent.putExtra("type",notice.type.name)
        val pending=PendingIntent.getActivity(app,notice.eventId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel=if(notice is BuyerOrderNotice)BUYER_CHANNEL else CHANNEL
        val title=if(notice is BuyerOrderNotice)notice.type.title else "Nuevo pedido"
        val body=if(notice is BuyerOrderNotice)notice.type.body else "Consulta la bandeja del establecimiento."
        manager.notify(notice.eventId,0,NotificationCompat.Builder(app,channel).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pending).build())
        return true
    }
    companion object { const val CHANNEL="new_store_orders"; const val BUYER_CHANNEL="buyer_order_updates" }
}
