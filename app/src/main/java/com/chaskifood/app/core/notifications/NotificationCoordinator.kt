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
    private val mutablePending=MutableStateFlow<NewOrderNotice?>(null)
    val pending:StateFlow<NewOrderNotice?> = mutablePending.asStateFlow()
    private var pendingSession:com.chaskifood.app.feature.cart.data.CartSession?=null
    fun open(intent:Intent?){
        if(intent?.action!="NEW_ORDER")return
        val data=listOf("eventId","orderId","businessId","storeId","recipientUid").associateWith {intent.getStringExtra(it).orEmpty()} + ("type" to "NEW_ORDER")
        val notice=NewOrderNotice.parse(data)?:return
        val session=sessions.current()
        if(session.uid!=notice.recipientUid)return
        pendingSession=session;mutablePending.value=notice
    }
    fun takeAuthorized():NewOrderNotice? {
        val notice=mutablePending.value?:return null
        if(sessions.current()!=pendingSession){clearPending();return null}
        var accepted:NewOrderNotice?=null
        access.runIfCurrent {state->
            // A stale RESOLVED emission must not discard a hint while current facts are being refreshed.
            if(state.phase==AccessContextPhase.RESOLVED){
                val resolved=state.resolution as? AccessResolution.Resolved
                if(resolved?.identity?.session?.uid==notice.recipientUid &&
                    resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS,AccessScope.Store(notice.businessId,notice.storeId)))accepted=notice
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
        scope.launch {sessions.sessions.collectLatest { session->
            if(previousSession!=session){manager.cancelAll();dedup.clear();if(pendingSession!=session)clearPending()}
            previousSession=session
        }}
        // Only a resolved store manager binds this installation; the server checks eligibility again (403) and every send.
        scope.launch {combine(sessions.sessions,access.states,tokenEpoch){session,state,_->session to state}.collectLatest {(session,state)->
            if(registered!=session)registered=null
            val eligible=NoticePolicy.canRegister(session.uid,state)
            if(eligible && registered==null){
                if(registrar.register(session)==RegistrationOutcome.REGISTERED && sessions.isCurrent(session))registered=session
            }else if(!eligible && state.phase==AccessContextPhase.RESOLVED && registered==session){
                registrar.unregister(session,signOut=false);registered=null
            }
        }}
    }
    /** FirebaseMessagingService.onNewToken: rebind the rotated token for the current eligible session only. */
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
        val notice=NewOrderNotice.parse(data)?:return false
        if(!NoticePolicy.canShow(sessions.current().uid,notice,access.currentState()))return false
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(app,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        if(!dedup.accept(notice.recipientUid,notice.eventId))return false
        val intent=Intent(app,MainActivity::class.java).setAction("NEW_ORDER").setData(android.net.Uri.parse("chaski-notice://${notice.eventId}"))
            .putExtra("eventId",notice.eventId).putExtra("orderId",notice.orderId).putExtra("businessId",notice.businessId)
            .putExtra("storeId",notice.storeId).putExtra("recipientUid",notice.recipientUid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(app,notice.eventId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(notice.eventId,0,NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nuevo pedido").setContentText("Consulta la bandeja del establecimiento.").setAutoCancel(true).setContentIntent(pending).build())
        return true
    }
    companion object { const val CHANNEL="new_store_orders" }
}
