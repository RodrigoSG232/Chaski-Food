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
import kotlinx.serialization.json.JsonObject

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
    private fun api(decision:suspend (String,JsonObject)->Response<StoreOrderDecisionResponse> =
        { _, _ -> Response.error(503,"{}".toResponseBody())},
        extension:suspend (String,JsonObject)->Response<PreparationExtensionResponse> = { _, _ -> Response.error(503,"{}".toResponseBody())},
        block:suspend(String?)->Response<StoreOrdersResponse>)=object:StoreOrdersApi {
        override suspend fun page(storeId:String,authorization:String,cursor:String?,status:String,pageSize:Int):Response<StoreOrdersResponse> {
            assertEquals("store",storeId);assertEquals("Bearer fixture-token",authorization);assertEquals(20,pageSize);return block(cursor)
        }
        override suspend fun respond(storeId:String,orderId:String,authorization:String,body:JsonObject):Response<StoreOrderDecisionResponse> {
            assertEquals("store",storeId);assertEquals("Bearer fixture-token",authorization);return decision(orderId,body)
        }
        override suspend fun extendPreparation(storeId:String,orderId:String,authorization:String,body:JsonObject):Response<PreparationExtensionResponse> {
            assertEquals("store",storeId);assertEquals("Bearer fixture-token",authorization);return extension(orderId,body)
        }
        override suspend fun markReady(storeId:String,orderId:String,authorization:String,body:JsonObject)=
            Response.error<StoreOrderDecisionResponse>(503,"{}".toResponseBody())
        override suspend fun simulatePickup(storeId:String,orderId:String,authorization:String,body:JsonObject)=
            Response.error<StoreOrderDecisionResponse>(503,"{}".toResponseBody())
        override suspend fun simulateArrival(storeId:String,orderId:String,authorization:String,body:JsonObject)=
            Response.error<StoreOrderDecisionResponse>(503,"{}".toResponseBody())
    }
    private fun valid()=StoreOrdersResponse(1,"store","Local",listOf(StoreOrderResponse("order",1800000000000,"PENDING_CONFIRMATION",
        listOf(StoreOrderLineResponse("p","Plato",2,1000,2000)),2000,300,2300,"PEN","CASH_ON_DELIVERY")),null)
    @Test fun authorizedRequestUsesFirebaseTokenAndMapsLongAmounts()=runTest {
        val result=HttpStoreOrdersRepository(api {cursor->assertEquals("cursor",cursor);Response.success(valid())},tokens,source).page(context,"cursor") as StoreOrdersResult.Loaded
        assertEquals(2300L,result.page.orders.single().amounts.totalMinor);assertEquals(2,result.page.orders.single().items.single().quantity)
    }
    @Test fun decisionsUseStrictPayloadsAndMapStateOrSuspension()=runTest {
        val id="a".repeat(64)
        val accepted=HttpStoreOrdersRepository(api(decision={order,body->
            assertEquals(id,order);assertEquals("ACCEPT",body["action"]?.toString()?.trim('"'))
            assertEquals("30",body["estimatedPreparationMinutes"].toString())
            Response.success(StoreOrderDecisionResponse(1,"ACCEPTED","PREPARING",1_800_000_300_000))}){Response.success(valid())},tokens,source)
            .respond(context,id,StoreOrderDecision.Accept(30))
        assertEquals(StoreOrderDecisionResult.Accepted(1_800_000_300_000),accepted)
        val rejected=HttpStoreOrdersRepository(api(decision={_,body->
            assertEquals("OTHER",body["reason"]?.toString()?.trim('"'));assertEquals("Sin insumos",body["explanation"]?.toString()?.trim('"'))
            Response.success(StoreOrderDecisionResponse(1,"REJECTED","REJECTED"))}){Response.success(valid())},tokens,source)
            .respond(context,id,StoreOrderDecision.Reject(StoreOrderRejectionReason.OTHER," Sin insumos "))
        assertEquals(StoreOrderDecisionResult.Rejected,rejected)
        for((code,expected) in listOf(409 to StoreOrderDecisionResult.StateChanged,423 to StoreOrderDecisionResult.AcceptanceSuspended,
            403 to StoreOrderDecisionResult.AccessDenied,404 to StoreOrderDecisionResult.NotFound))
            assertEquals(expected,HttpStoreOrdersRepository(api(decision={_,_->Response.error(code,"{}".toResponseBody())}){Response.success(valid())},tokens,source)
                .respond(context,id,StoreOrderDecision.Accept(20)))
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
    private val extension=PreparationExtension("3f8a1c2e-5b7d-4e9f-8a6b-1c2d3e4f5a6b",10,1)
    private fun extend(response:suspend (JsonObject)->Response<PreparationExtensionResponse>)=
        runBlocking{HttpStoreOrdersRepository(api(extension={_,body->response(body)}){Response.success(valid())},tokens,source)
            .extendPreparation(context,"a".repeat(64),extension)}
    private fun error(code:Int,body:String="{}")=Response.error<PreparationExtensionResponse>(code,body.toResponseBody())
    @Test fun extensionSendsOperationMinutesAndRevisionAndMapsTheServerResult(){
        val result=extend{body->
            assertEquals(setOf("operationId","additionalMinutes","expectedEstimateRevision"),body.keys)
            assertEquals(extension.operationId,body["operationId"].toString().trim('"'))
            assertEquals("10",body["additionalMinutes"].toString());assertEquals("1",body["expectedEstimateRevision"].toString())
            Response.success(PreparationExtensionResponse(1,"EXTENDED","PREPARING",1_800_003_000_000,2,10))}
        assertEquals(PreparationExtensionResult.Extended(1_800_003_000_000,2,10),result)
    }
    @Test fun extensionConflictsAreDistinguishedAndUnknownOutcomesStayRetryable(){
        assertEquals(PreparationExtensionResult.RevisionChanged,extend{error(409,"""{"error":"ESTIMATE_REVISION_CHANGED"}""")})
        assertEquals(PreparationExtensionResult.OperationConflict,extend{error(409,"""{"error":"OPERATION_CONFLICT"}""")})
        assertEquals(PreparationExtensionResult.StateChanged,extend{error(409,"""{"error":"ORDER_STATE_CHANGED"}""")})
        assertEquals(PreparationExtensionResult.StateChanged,extend{error(409,"not json")})
        assertEquals(PreparationExtensionResult.AuthenticationRequired,extend{error(401)})
        assertEquals(PreparationExtensionResult.AccessDenied,extend{error(403)})
        assertEquals(PreparationExtensionResult.NotFound,extend{error(404)})
        for(code in listOf(400,500,503))assertEquals(PreparationExtensionResult.Unavailable,extend{error(code)})
        assertEquals(PreparationExtensionResult.Unavailable,extend{throw java.io.IOException()})
        // A 200 that breaks the contract is treated as an unknown outcome, never as success.
        for(bad in listOf(PreparationExtensionResponse(2,"EXTENDED","PREPARING",1_800_003_000_000,2,10),
            PreparationExtensionResponse(1,"EXTENDED","READY_FOR_PICKUP",1_800_003_000_000,2,10),
            PreparationExtensionResponse(1,"EXTENDED","PREPARING",1_800_003_000_000,0,10),
            PreparationExtensionResponse(1,"EXTENDED","PREPARING",1_800_003_000_000,2,5)))
            assertEquals(PreparationExtensionResult.Unavailable,extend{Response.success(bad)})
        assertEquals(PreparationExtensionResult.NotFound,runBlocking{HttpStoreOrdersRepository(api{Response.success(valid())},tokens,source)
            .extendPreparation(context,"../x",extension)})
    }
    @Test fun extensionRequestsAreValidatedBeforeLeavingTheDevice(){
        for(bad in listOf({PreparationExtension("not-a-uuid",10,0)},{PreparationExtension(extension.operationId,4,0)},
            {PreparationExtension(extension.operationId,61,0)},{PreparationExtension(extension.operationId,10,-1)}))
            assertThrows(IllegalArgumentException::class.java){bad()}
    }
    @Test fun inboxMapsExtensionCountersAndToleratesResponsesWithoutThem(){
        val preparing=StoreOrdersResponse(1,"store","Local",listOf(StoreOrderResponse("order",1800000000000,"PREPARING",
            listOf(StoreOrderLineResponse("p","Plato",2,1000,2000)),2000,300,2300,"PEN","CASH_ON_DELIVERY",40,1_800_002_400_000,
            estimateRevision=3,preparationExtendedMinutes=25)),null)
        val order=(runBlocking{HttpStoreOrdersRepository(api{Response.success(preparing)},tokens,source).page(context,null,OrderStatus.PREPARING)}
            as StoreOrdersResult.Loaded).page.orders.single()
        assertEquals(3L,order.estimateRevision);assertEquals(25,order.preparationExtendedMinutes);assertEquals(40,order.estimatedPreparationMinutes)
        val legacy=preparing.copy(orders=listOf(preparing.orders.single().copy(estimateRevision=0,preparationExtendedMinutes=0)))
        val old=(runBlocking{HttpStoreOrdersRepository(api{Response.success(legacy)},tokens,source).page(context,null,OrderStatus.PREPARING)}
            as StoreOrdersResult.Loaded).page.orders.single()
        assertEquals(0L,old.estimateRevision);assertEquals(0,old.preparationExtendedMinutes)
    }
}
