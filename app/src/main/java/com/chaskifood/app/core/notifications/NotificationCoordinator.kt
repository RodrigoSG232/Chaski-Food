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
    @AccessCoroutineScope private val scope:CoroutineScope,retrofit:Retrofit) {
    private val api=retrofit.newBuilder().baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(DeviceApi::class.java)
    private val preferences=app.getSharedPreferences("notification_installation",Context.MODE_PRIVATE)
    private val installationId=preferences.getString("id",null)?:UUID.randomUUID().toString().also {preferences.edit().putString("id",it).apply()}
    private val dedup=NoticeDeduplicator()
    private var started=false
    private var currentToken:String?=null
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
                if(BuildConfig.FIREBASE_MODE=="LOCAL" && resolved?.identity?.session?.uid==notice.recipientUid &&
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
            // HU16 remains LOCAL. FirebaseMessaging token retrieval is deliberately not invoked in Emulator.
            if(BuildConfig.FIREBASE_MODE=="LOCAL" && session.uid!=null){
                // Synthetic LOCAL token, never an FCM network request. Real token provisioning remains closed.
                val token=currentToken?:"local-fixture:$installationId"
                currentToken=token
                register(token)
            }
        }}
    }
    fun tokenChanged(token:String){
        if(BuildConfig.FIREBASE_MODE!="LOCAL")return
        currentToken=token
        scope.launch {register(token)}
    }
    @Synchronized private fun nextBindingVersion():Long {
        val version=Math.addExact(preferences.getLong("bindingVersion",0),1)
        check(preferences.edit().putLong("bindingVersion",version).commit())
        return version
    }
    private suspend fun register(token:String){
        if(BuildConfig.FIREBASE_MODE!="LOCAL")return
        val session=sessions.current();val uid=session.uid?:return
        val version=withContext(Dispatchers.IO){nextBindingVersion()}
        try {withTimeoutOrNull(10000){val bearer=tokens.token(uid,session.generation)
            if(sessions.isCurrent(session))api.put(installationId,"Bearer $bearer",DeviceRegistration(token,version))}}
        catch(e:CancellationException){throw e}catch(_:Exception){/* Retry on the next token/session update, never log tokens. */}
    }
    /** Called before FirebaseAuth.signOut. Failed offline removal never blocks logout or enables UI access. */
    suspend fun beforeSignOut(){
        manager.cancelAll();dedup.clear();clearPending()
        val session=sessions.current();val uid=session.uid
        if(BuildConfig.FIREBASE_MODE=="LOCAL" && uid!=null)try {
            withTimeoutOrNull(5000){api.remove(installationId,"Bearer ${tokens.token(uid,session.generation)}",withContext(Dispatchers.IO){nextBindingVersion()})}
        }catch(e:CancellationException){throw e}catch(_:Exception){}
        currentToken=null
        // No real Messaging calls in LOCAL. A future Cloud activation must also enable token provisioning/deletion.
    }
    internal fun allowed(notice:NewOrderNotice):Boolean {
        val session=sessions.current()
        if(BuildConfig.FIREBASE_MODE!="LOCAL"||session.uid!=notice.recipientUid)return false
        var permitted=false
        val published=access.runIfCurrent { state->
            val resolved=state.resolution as? AccessResolution.Resolved
            permitted=resolved?.identity?.session?.uid==session.uid &&
                resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS,AccessScope.Store(notice.businessId,notice.storeId))
        }
        return published && permitted
    }
    fun receive(data:Map<String,String>):Job? {
        val notice=NewOrderNotice.parse(data)?:return null
        // Refresh facts before any visual notification, then check identity again without suspension.
        return scope.launch {
            val session=sessions.current()
            if(session.uid!=notice.recipientUid)return@launch
            access.refresh()
            withTimeoutOrNull(15000){access.states.first {it.phase!=AccessContextPhase.LOADING}}
            if(!sessions.isCurrent(session)||!allowed(notice))return@launch
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(app,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return@launch
            if(!dedup.accept(notice.recipientUid,notice.eventId))return@launch
            val intent=Intent(app,MainActivity::class.java).setAction("NEW_ORDER").setData(android.net.Uri.parse("chaski-notice://${notice.eventId}"))
                .putExtra("eventId",notice.eventId).putExtra("orderId",notice.orderId).putExtra("businessId",notice.businessId)
                .putExtra("storeId",notice.storeId).putExtra("recipientUid",notice.recipientUid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(app,notice.eventId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(notice.eventId,0,NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Nuevo pedido").setContentText("Consulta la bandeja del establecimiento.").setAutoCancel(true).setContentIntent(pending).build())
        }
    }
    companion object { const val CHANNEL="new_store_orders" }
}
