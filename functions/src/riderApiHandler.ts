import {getFirestore} from "firebase-admin/firestore";
import type {onRequest} from "firebase-functions/v2/https";
import {RuntimeEnvironment} from "./runtimeEnvironment";
import {parseRiderEvent,parseRiderTracking} from "./integration/riderIntegrationPolicy";
import {RiderIntegrationService} from "./integration/riderIntegrationService";
import {OrderHttpError} from "./orders/orderRequest";

type Dependencies={runtime:()=>RuntimeEnvironment;app:(runtime:RuntimeEnvironment)=>any};
type RiderHttpHandler=Parameters<typeof onRequest>[0];
export function createRiderApiHandler(dependencies:Dependencies):RiderHttpHandler{
  return async(request,response)=>{
    const error=(status:number,code:string)=>response.status(status).json({error:{code,retryable:status>=500}});
    response.set("Cache-Control","no-store");
    try{
      const runtime=dependencies.runtime(),service=new RiderIntegrationService(getFirestore(dependencies.app(runtime)));
      const collection=/^\/integration\/v1\/orders\/?$/.test(request.path);
      const detail=/^\/integration\/v1\/orders\/([^/]+)\/?$/.exec(request.path);
      const tracking=/^\/integration\/v1\/orders\/([^/]+)\/tracking\/?$/.exec(request.path);
      if(collection){
        if(request.method!=="GET")return void error(405,"METHOD_NOT_ALLOWED");
        if(Object.keys(request.query).some(key=>key!=="limit"))throw new OrderHttpError("INVALID_REQUEST",400);
        const raw=Array.isArray(request.query.limit)?request.query.limit[0]:request.query.limit;
        response.status(200).json(await service.available(raw===undefined?20:Number(raw)));return;
      }
      if(detail){
        if(request.method!=="GET")return void error(405,"METHOD_NOT_ALLOWED");
        if(Object.keys(request.query).length)throw new OrderHttpError("INVALID_REQUEST",400);
        response.status(200).json(await service.detail(decodeURIComponent(detail[1])));return;
      }
      if(/^\/integration\/v1\/rider-events\/?$/.test(request.path)){
        if(request.method!=="POST")return void error(405,"METHOD_NOT_ALLOWED");
        if(Object.keys(request.query).length)throw new OrderHttpError("INVALID_REQUEST",400);
        response.status(200).json(await service.event(parseRiderEvent(request.body),request.body));return;
      }
      if(tracking){
        if(request.method!=="PUT")return void error(405,"METHOD_NOT_ALLOWED");
        if(Object.keys(request.query).length)throw new OrderHttpError("INVALID_REQUEST",400);
        response.status(200).json(await service.tracking(decodeURIComponent(tracking[1]),parseRiderTracking(request.body),request.body));return;
      }
      error(404,"NOT_FOUND");
    }catch(caught){
      if(caught instanceof OrderHttpError)error(caught.status,caught.code);
      else if(caught instanceof URIError)error(404,"NOT_FOUND");
      else{console.error("RIDER_API_FAILURE",caught instanceof Error?caught.message:"UNKNOWN");error(503,"RIDER_API_UNAVAILABLE");}
    }
  };
}
