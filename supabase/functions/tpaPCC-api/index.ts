import { withSupabase } from "npm:@supabase/server@1";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS","Content-Type":"application/json"};
const json=(d:unknown,s=200)=>new Response(JSON.stringify(d),{status:s,headers:cors});
function code6(){const a=new Uint32Array(1);crypto.getRandomValues(a);return String(a[0]%1_000_000).padStart(6,"0");}
function tokenFrom(req:Request){const h=req.headers.get("authorization")||"";return h.startsWith("Bearer ")?h.slice(7).trim():"";}
async function notifyTelegram(db:any,deviceIds:string[],text:string){
  const t=Deno.env.get("TELEGRAM_BOT_TOKEN")||"";
  if(!t)return;
  const ids=[...new Set(deviceIds.filter(Boolean))];
  const {data:links}=await db.from("telegram_links").select("telegram_user_id,chat_id,device_id").in("device_id",ids);
  for(const link of links||[]){try{await fetch(`https://api.telegram.org/bot${t}/sendMessage`,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({chat_id:link.chat_id,text})});}catch(_){}}}
async function hashToken(token:string){const d=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(token));return [...new Uint8Array(d)].map(x=>x.toString(16).padStart(2,"0")).join("");}
export default {fetch:withSupabase({auth:"none"},async(req,ctx)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors}); if(req.method!=="POST")return json({error:"POST required"},405);
 const body=await req.json().catch(()=>({})),action=String(body.action||""),db=ctx.supabaseAdmin;
 if(action==="register_device"){const token=String(body.device_token||""),name=String(body.name||"").trim().slice(0,80),platform=String(body.platform||"").trim();
  if(!/^[0-9a-f]{64}$/.test(token)||!name||!["android","windows","linux","macos"].includes(platform))return json({error:"Invalid device registration"},400);
  const hash=await hashToken(token); const {data:existing}=await db.from("device_auth_tokens").select("device_id").eq("token_hash",hash).maybeSingle();
  if(existing?.device_id){await db.from("devices").update({name,platform,last_seen_at:new Date().toISOString()}).eq("id",existing.device_id);return json({device_id:existing.device_id});}
  const {data:device,error}=await db.from("devices").insert({owner_id:crypto.randomUUID(),name,platform}).select("id").single(); if(error)return json({error:error.message},400);
  const {error:te}=await db.from("device_auth_tokens").insert({device_id:device.id,token_hash:hash}); if(te){await db.from("devices").delete().eq("id",device.id);return json({error:te.message},400);}
  return json({device_id:device.id});
 }
 const token=tokenFrom(req);if(!/^[0-9a-f]{64}$/.test(token))return json({error:"Device token required"},401);const hash=await hashToken(token);
 const {data:auth}=await db.from("device_auth_tokens").select("device_id").eq("token_hash",hash).maybeSingle();if(!auth?.device_id)return json({error:"Unknown device token"},401);
 const deviceId=auth.device_id;await db.from("device_auth_tokens").update({last_seen_at:new Date().toISOString()}).eq("device_id",deviceId);await db.from("devices").update({last_seen_at:new Date().toISOString()}).eq("id",deviceId);
 if(action==="create_code"){const expires=new Date(Date.now()+600000).toISOString();let code=code6();for(let i=0;i<5;i++){const {data:e}=await db.from("pairing_codes").select("id").eq("code",code).is("used_at",null).gt("expires_at",new Date().toISOString()).maybeSingle();if(!e)break;code=code6();}const {data,error}=await db.from("pairing_codes").insert({device_id:deviceId,code,expires_at:expires}).select("code,expires_at").single();if(error)return json({error:error.message},400);return json(data);}
 if(action==="list_devices"){const {data:trusted}=await db.from("trusted_devices").select("trusted_device_id").eq("device_id",deviceId).is("revoked_at",null);const ids=[deviceId,...(trusted||[]).map(x=>x.trusted_device_id)];const {data,error}=await db.from("devices").select("id,name,platform,last_seen_at").in("id",ids);if(error)return json({error:error.message},400);return json({devices:data||[]});}
 if(action==="redeem_code"){const code=String(body.code||"").trim();if(!/^[0-9]{6}$/.test(code))return json({error:"Invalid code"},400);const {data:pair}=await db.from("pairing_codes").select("id,device_id").eq("code",code).is("used_at",null).gt("expires_at",new Date().toISOString()).maybeSingle();if(!pair)return json({error:"Code expired or invalid"},404);if(pair.device_id===deviceId)return json({error:"Cannot pair device with itself"},400);const {error:mark}=await db.from("pairing_codes").update({used_at:new Date().toISOString()}).eq("id",pair.id).is("used_at",null);if(mark)return json({error:mark.message},409);await db.from("trusted_devices").upsert([{device_id:pair.device_id,trusted_device_id:deviceId},{device_id:deviceId,trusted_device_id:pair.device_id}],{onConflict:"device_id,trusted_device_id"});return json({paired:true,device_id:pair.device_id,trusted_device_id:deviceId});}
 if(action==="start_session"){const b=String(body.device_b||"");if(!b||b===deviceId)return json({error:"Invalid device"},400);const {data:trusted}=await db.from("trusted_devices").select("id").eq("device_id",deviceId).eq("trusted_device_id",b).is("revoked_at",null).maybeSingle();if(!trusted)return json({error:"Devices are not paired"},403);const started=new Date(),minutes=Math.min(Math.max(Number(body.duration_minutes)||60,1),1440),expires=new Date(started.getTime()+minutes*60000);const {data:session,error}=await db.from("sessions").insert({device_a:deviceId,device_b:b,started_at:started.toISOString(),expires_at:expires.toISOString(),requested_at:started.toISOString()}).select("id,device_a,device_b,started_at,expires_at,requested_at,approved_at").single();if(error)return json({error:error.message},400);const allowed=new Set(["screen","screenshot","screen_recording","microphone","files","notifications","brightness","volume","accessibility"]);const requested=Array.isArray(body.permissions)?body.permissions.map(String):[],permissions=[...new Set(requested.filter(p=>allowed.has(p)))];if(permissions.length)await db.from("session_permissions").insert(permissions.map(permission=>({session_id:session.id,permission})));await db.from("audit_events").insert({session_id:session.id,actor_device_id:deviceId,event_type:"session_requested",metadata:{permissions}});return json({session,permissions,pending_approval:true});}
 if(action==="approve_session"){
  const id=String(body.session_id||"");
  const {data:session}=await db.from("sessions").select("id,device_a,device_b").eq("id",id).is("ended_at",null).maybeSingle();
  if(!session||session.device_b!==deviceId)return json({error:"Only the phone owner can approve"},403);
  const {data:updated,error}=await db.from("sessions").update({approved_at:new Date().toISOString(),approved_by:deviceId}).eq("id",id).is("approved_at",null).select("id,approved_at,approved_by").single();
  if(error)return json({error:error.message},400);
  await db.from("audit_events").insert({session_id:id,actor_device_id:deviceId,event_type:"session_approved",metadata:{}});
  await notifyTelegram(db,[session.device_a,session.device_b],"🟢 TPaPCC: подключение разрешено владельцем телефона. Сессия "+id+" активна.");
  return json({approved:true,session:updated});
 }
 if(action==="end_session"){const id=String(body.session_id||"");const {data:session}=await db.from("sessions").select("id,device_a,device_b").eq("id",id).maybeSingle();if(!session||![session.device_a,session.device_b].includes(deviceId)||!session.approved_at)return json({error:"Session is not approved"},403);const {error}=await db.from("sessions").update({ended_at:new Date().toISOString()}).eq("id",id).is("ended_at",null);if(error)return json({error:error.message},400);await db.from("audit_events").insert({session_id:id,actor_device_id:deviceId,event_type:"session_ended",metadata:{}});return json({ended:true});}
 if(action==="send_signal"){
  const sessionId=String(body.session_id||""),messageType=String(body.message_type||""),payload=body.payload;
  const allowedTypes=new Set(["offer","answer","ice","bye","ping"]);
  if(!sessionId||!allowedTypes.has(messageType)||payload===undefined)return json({error:"Invalid signal"},400);
  const {data:session}=await db.from("sessions").select("id,device_a,device_b,approved_at").eq("id",sessionId).is("ended_at",null).gt("expires_at",new Date().toISOString()).maybeSingle();
  if(!session||![session.device_a,session.device_b].includes(deviceId)||!session.approved_at)return json({error:"Session is not approved"},403);
  const {error}=await db.from("signaling_messages").insert({session_id:sessionId,sender_device_id:deviceId,message_type:messageType,payload});
  if(error)return json({error:error.message},400);
  return json({sent:true});
 }
 if(action==="poll_signals"){
  const sessionId=String(body.session_id||""),after=Number(body.after_id||0);
  const {data:session}=await db.from("sessions").select("id,device_a,device_b").eq("id",sessionId).is("ended_at",null).gt("expires_at",new Date().toISOString()).maybeSingle();
  if(!session||![session.device_a,session.device_b].includes(deviceId))return json({error:"Not authorized"},403);
  const {data,error}=await db.from("signaling_messages").select("id,sender_device_id,message_type,payload,created_at").eq("session_id",sessionId).neq("sender_device_id",deviceId).gt("id",after).order("id",{ascending:true}).limit(100);
  if(error)return json({error:error.message},400);
  return json({messages:data||[]});
 }
 if(action==="session_status"){const {data}=await db.from("sessions").select("id,device_a,device_b,started_at,expires_at,ended_at,requested_at,approved_at,approved_by").or("device_a.eq."+deviceId+",device_b.eq."+deviceId).is("ended_at",null).gt("expires_at",new Date().toISOString()).order("started_at",{ascending:false}).limit(1).maybeSingle();return json({session:data||null});}
 return json({error:"Unknown action"},400);
})};