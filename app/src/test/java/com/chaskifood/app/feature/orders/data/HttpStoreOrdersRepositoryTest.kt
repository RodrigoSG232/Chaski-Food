package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HttpStoreOrdersRepositoryTest {
    private val target=StoreOrdersTarget("business","store")
    private val context=StoreOrdersContext("manager",3,AccessProvenance(AccessSession("manager",1),1,1),2,target)
    private val source=object:StoreOrdersAccessSource {
        var state:StoreOrdersAuthorization=StoreOrdersAuthorization.Allowed(context)
        override fun observe(target:StoreOrdersTarget)=flowOf(state)
        override fun current(target:StoreOrdersTarget)=state
    }
    private val tokens=object:BuyerTokenSource {
        override suspend fun token(uid:String,generation:Long):String {assertEquals("manager",uid);assertEquals(3L,generation);return "fixture-token"}
        override fun isCurrent(uid:String,generation:Long)=true
    }
    private fun api(block:suspend(String?)->Response<StoreOrdersResponse>)=object:StoreOrdersApi {
        override suspend fun page(storeId:String,authorization:String,cursor:String?,pageSize:Int):Response<StoreOrdersResponse> {
            assertEquals("store",storeId);assertEquals("Bearer fixture-token",authorization);assertEquals(20,pageSize);return block(cursor)
        }
    }
    private fun valid()=StoreOrdersResponse(1,"store","Local",listOf(StoreOrderResponse("order",1800000000000,"PENDING_CONFIRMATION",
        listOf(StoreOrderLineResponse("p","Plato",2,1000,2000)),2000,300,2300,"PEN","CASH_ON_DELIVERY")),null)
    @Test fun authorizedRequestUsesFirebaseTokenAndMapsLongAmounts()=runTest {
        val result=HttpStoreOrdersRepository(api {cursor->assertEquals("cursor",cursor);Response.success(valid())},tokens,source).page(context,"cursor") as StoreOrdersResult.Loaded
        assertEquals(2300L,result.page.orders.single().amounts.totalMinor);assertEquals(2,result.page.orders.single().items.single().quantity)
    }
    @Test fun malformedSourceNeverBecomesAValidOrderOrWrongStorePage()=runTest {
        val v=valid();val o=v.orders.single()
        for(body in listOf(v.copy(storeId="other"),v.copy(contractVersion=2),v.copy(orders=listOf(o.copy(totalMinor=2301))),
            v.copy(orders=listOf(o.copy(currency="USD"))),v.copy(orders=listOf(o.copy(status="DELIVERED"))),
            v.copy(orders=listOf(o,o)),v.copy(nextCursor="../bad"))) {
            assertEquals(StoreOrdersResult.Unavailable,HttpStoreOrdersRepository(api{Response.success(body)},tokens,source).page(context))
        }
    }
    @Test fun revokedAuthNotFoundAndInvalidCursorAreNotNetworkErrors()=runTest {
        for((code,expected)in listOf(401 to StoreOrdersResult.AuthenticationRequired,403 to StoreOrdersResult.AccessDenied,
            404 to StoreOrdersResult.StoreMissing,400 to StoreOrdersResult.InvalidCursor,503 to StoreOrdersResult.Unavailable))
            assertEquals(expected,HttpStoreOrdersRepository(api{Response.error(code,"{}".toResponseBody())},tokens,source).page(context))
    }
    @Test fun changedGenerationOrPermissionDiscardsLateHttpResponseAndPreventsNewCalls()=runTest {
        val gate=CompletableDeferred<Unit>();var calls=0
        val reader=HttpStoreOrdersRepository(api{calls++;gate.await();Response.success(valid())},tokens,source)
        val result=async{reader.page(context)};runCurrent();source.state=StoreOrdersAuthorization.Denied;gate.complete(Unit)
        assertEquals(StoreOrdersResult.StaleContext,result.await());assertEquals(StoreOrdersResult.StaleContext,reader.page(context));assertEquals(1,calls)
    }
    @Test fun timeoutNetworkFailureAndExternalCancellationRemainDistinctFromDenial()=runTest {
        assertEquals(StoreOrdersResult.Unavailable,HttpStoreOrdersRepository(api{delay(20000);Response.success(valid())},tokens,source).page(context))
        assertEquals(StoreOrdersResult.Unavailable,HttpStoreOrdersRepository(api{throw java.io.IOException()},tokens,source).page(context))
        val job=async{HttpStoreOrdersRepository(api{awaitCancellation()},tokens,source).page(context)}
        runCurrent();job.cancel();runCurrent();assertTrue(job.isCancelled)
    }
}
