import { getMessaging, Message } from "firebase-admin/messaging";
import { App } from "firebase-admin/app";
export type NewOrderNotice=Readonly<{type:"NEW_ORDER";eventId:string;orderId:string;storeId:string;businessId:string;recipientUid:string}>;
export type SendResult="ACCEPTED"|"INVALID_TOKEN"|"PERMANENT_FAILURE";
export interface NoticeSender {send(token:string,data:NewOrderNotice):Promise<SendResult>}
/** LOCAL never contacts Messaging. Accepted means transport acceptance, not delivery. */
export class SimulatedNoticeSender implements NoticeSender {
  async send(_token:string,_data:NewOrderNotice):Promise<SendResult>{return "ACCEPTED";}
}
export class FirebaseNoticeSender implements NoticeSender {
  constructor(app:App,private readonly sendMessage:(message:Message)=>Promise<string> = message=>getMessaging(app).send(message)){}
  async send(token:string,data:NewOrderNotice):Promise<SendResult>{
    try {
      await this.sendMessage({token,data:{...data},android:{priority:"high",ttl:300000}});
      return "ACCEPTED";
    }catch(error){
      const code=(error as {code?:string}).code;
      if(["messaging/registration-token-not-registered","messaging/invalid-registration-token"].includes(code??""))return "INVALID_TOKEN";
      if(["messaging/invalid-argument","messaging/mismatched-credential","messaging/invalid-package-name"].includes(code??""))return "PERMANENT_FAILURE";
      // Never persist or log raw SDK errors: they can contain tokens/payloads.
      throw new Error("NOTIFICATION_TRANSPORT_UNAVAILABLE");
    }
  }
}
