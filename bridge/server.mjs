import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const config=JSON.parse(readFileSync(new URL('./config.json',import.meta.url)));
const pending=new Map(); let sequence=0, child, ready, sending=false, loadedThread;
const listeners=new Set();
export function subscribeNotifications(listener){listeners.add(listener);return ()=>listeners.delete(listener);}
export async function interruptTurn(turnId){await connect();return rpc('turn/interrupt',{threadId:config.threadId,turnId});}
export function validImage(url){return typeof url==='string'&&url.length<=12000000&&/^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(url);}
function stop(){ child?.kill(); for(const p of pending.values()){clearTimeout(p.timer);p.reject(new Error('Połączenie z Codex zamknięte.'));}pending.clear();ready=undefined;child=undefined;loadedThread=undefined; }
export const disconnect=stop;
export async function setupProjectThread({projectId,projectName,cwd}){
  await connect();
  const {thread}=await rpc('thread/start',{cwd,sandbox:'workspace-write',approvalPolicy:'on-request',model:config.model,config:{model_reasoning_effort:config.reasoningEffort}});
  loadedThread=thread;
  Object.assign(config,{threadId:thread.id,projectId,projectName,cwd});
  writeFileSync(new URL('./config.json',import.meta.url),JSON.stringify(config,null,2)+'\n');
  return thread;
}
function rpc(method,params){return new Promise((resolve,reject)=>{
  const id=++sequence;
  const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Przekroczono czas oczekiwania na Codex; sprawdź stan czatu przed ponowieniem wysyłki.'));},60000);
  pending.set(id,{resolve,reject,timer});child.stdin.write(JSON.stringify({id,method,params})+'\n');
});}
async function connect(){
  if(ready)return ready;
  ready=(async()=>{
    child=spawn(config.codexPath,['app-server','--stdio'],{windowsHide:true,env:process.env});
    child.stderr.on('data',d=>process.stderr.write(d));child.on('error',stop);child.on('exit',stop);
    createInterface({input:child.stdout}).on('line',line=>{
      let m;try{m=JSON.parse(line);}catch{return;}
      if(m.method&&m.id===undefined)for(const listener of listeners){try{listener(m);}catch{}}
      if(m.method==='turn/completed' && m.params?.threadId===loadedThread?.id)loadedThread.status={type:'idle'};
      const p=pending.get(m.id);
      if(p && !m.method){pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}
      else if(m.method && m.id!==undefined){
        // Never grant permissions automatically on behalf of another client.
        child?.stdin.write(JSON.stringify({id:m.id,error:{code:-32601,message:'Obsłuż żądanie w interfejsie Codex; ten most nie udziela zgód.'}})+'\n');
      }
    });
    await rpc('initialize',{clientInfo:{name:'local_chat_mcp',title:'Local Chat MCP',version:'1.0.0'}});
    child.stdin.write(JSON.stringify({method:'initialized'})+'\n');
  })();
  try{return await ready;}catch(e){stop();throw e;}
}
const tools=[
  {name:'chat_status',description:'Odczytaj stan jednego przypisanego czatu Codex.',inputSchema:{type:'object',properties:{},additionalProperties:false}},
  {name:'chat_history',description:'Odczytaj ostatnie tury i odpowiedzi przypisanego czatu. Użyj cursor do starszej historii.',inputSchema:{type:'object',properties:{limit:{type:'integer',minimum:1,maximum:100},cursor:{type:'string'}},additionalProperties:false}},
  {name:'chat_send',description:'Wyślij wiadomość i opcjonalne obrazy do przypisanego czatu i zwróć ID tury. Odpowiedź odbierz przez chat_history. Czatu nie może równocześnie używać inny proces Codex. Nie ponawiaj automatycznie po timeout.',inputSchema:{type:'object',properties:{message:{type:'string',minLength:1,maxLength:100000},imageUrls:{type:'array',maxItems:4,items:{type:'string',maxLength:12000000,pattern:'^data:image/(png|jpeg|webp);base64,'}}},required:['message'],additionalProperties:false}}
];
export async function callTool(name,args={}){
  if(!tools.some(t=>t.name===name))throw new Error('Nieznane narzędzie.');
  if(!args || typeof args!=='object'||Array.isArray(args))throw new Error('Nieprawidłowe argumenty.');
  if(name==='chat_send' && (typeof args.message!=='string'||!args.message.trim()||args.message.length>100000))throw new Error('Wiadomość musi mieć 1–100000 znaków.');
  if(name==='chat_send' && args.imageUrls!==undefined && (!Array.isArray(args.imageUrls)||args.imageUrls.length>4||args.imageUrls.some(url=>!validImage(url))))throw new Error('Nieprawidłowe obrazy: wymagane maksymalnie 4 obrazy PNG, JPEG lub WebP jako data URL.');
  if(name==='chat_history' && ((args.limit!==undefined&&(!Number.isInteger(args.limit)||args.limit<1||args.limit>100))||(args.cursor!==undefined&&typeof args.cursor!=='string')))throw new Error('Nieprawidłowy limit lub cursor.');
  if(name==='chat_send' && sending)throw new Error('Trwa już wysyłanie wiadomości.');
  await connect();
  const threadId=config.threadId;
  if(name==='chat_status')return rpc('thread/read',{threadId});
  if(name==='chat_history')return rpc('thread/turns/list',{threadId,limit:args.limit??10,itemsView:'full',...(args.cursor?{cursor:args.cursor}:{})});
  sending=true;
  try{
    let resumed;
    try{resumed=loadedThread?.id===threadId?{thread:loadedThread}:await rpc('thread/resume',{threadId,excludeTurns:true});loadedThread=resumed.thread;}
    catch(e){if(e.message.includes('active writer'))throw new Error('Ten czat jest otwarty do zapisu w innym procesie Codex (active writer). Zakończ jego bieżącą turę i zwolnij wątek w aplikacji przed wysyłką przez MCP. Samo zakończenie odpowiedzi może nie zwolnić blokady.');throw e;}
    const {thread}=resumed;
    if(thread.status?.type==='active')throw new Error('Czat jest zajęty. Poczekaj na zakończenie bieżącej tury.');
    const result=await rpc('turn/start',{threadId,model:config.model,effort:config.reasoningEffort,input:[{type:'text',text:args.message},...(args.imageUrls??[]).map(url=>({type:'image',url}))]});
    loadedThread.status={type:'active'};
    return {threadId,turn:result.turn,next:'Odczytuj chat_history, aż ta tura otrzyma status completed, failed albo interrupted.'};
  }finally{sending=false;}
}
async function handle(m){
  if(m.id===undefined)return;
  try{
    let result;
    switch(m.method){
      case 'initialize':result={protocolVersion:['2024-11-05','2025-03-26','2025-06-18','2025-11-25'].includes(m.params?.protocolVersion)?m.params.protocolVersion:'2024-11-05',capabilities:{tools:{}},serverInfo:{name:'local-chat-mcp',version:'1.0.0'}};break;
      case 'ping':result={};break;
      case 'tools/list':result={tools};break;
      case 'tools/call':
        try{result={content:[{type:'text',text:JSON.stringify(await callTool(m.params?.name,m.params?.arguments))}]};}
        catch(e){result={isError:true,content:[{type:'text',text:e.message}]};}break;
      default:process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,error:{code:-32601,message:'Method not found'}})+'\n');return;
    }
    process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,result})+'\n');
  }catch(e){process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,error:{code:-32603,message:e.message}})+'\n');}
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const input=createInterface({input:process.stdin});
  input.on('line',line=>{try{const m=JSON.parse(line);void handle(m);}catch{process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:null,error:{code:-32700,message:'Parse error'}})+'\n');}});
  input.on('close',stop);process.on('SIGINT',()=>{stop();process.exit();});process.on('SIGTERM',()=>{stop();process.exit();});
}
