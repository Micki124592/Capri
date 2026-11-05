import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway,capriPrompt,normalizeMessages,toolReply} from './gateway.mjs';
import {loreContext} from './lore.mjs';
import {readFileSync} from 'node:fs';

test('Capri tools are advertised, validated, and tool results remain associated with calls',()=>{
  const tools=[{type:'function',function:{name:'play_music',parameters:{required:['song']}}}];
  const body={model:'codex-capricorn',tools,messages:[{role:'user',content:'Włącz jakiś hit Night City'}]};
  assert.match(capriPrompt(body),/play_music/);
  const reply=toolReply(JSON.stringify({content:null,tool_calls:[{name:'play_music',arguments:{song:'I Really Want to Stay at Your House'}}]}),tools);
  assert.equal(reply.tool_calls[0].function.name,'play_music');
  assert.throws(()=>toolReply('{"content":null,"tool_calls":[{"name":"delete_files","arguments":{}}]}',tools));
  assert.throws(()=>toolReply('{"content":null,"tool_calls":[{"name":"play_music","arguments":{}}]}',tools));
  body.messages.push({role:'assistant',content:null,tool_calls:reply.tool_calls},{role:'tool',tool_call_id:reply.tool_calls[0].id,content:'Playback started'});
  assert.match(capriPrompt(body),/Playback started/);
  assert.equal(normalizeMessages(body).messages.at(-1).tool_call_id,reply.tool_calls[0].id);
});
test('HTTP returns executable OpenAI tool_calls instead of speech',async()=>{
  const server=createGateway(async name=>name==='chat_send'?{threadId:'thread',turn:{id:'t'}}:{data:[{id:'t',status:'completed',items:[{type:'agentMessage',phase:'final_answer',text:'{"content":null,"tool_calls":[{"name":"play_music","arguments":{"song":"Night City"}}]}'}]}]},{pollMs:1});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  try{
    const response=await fetch(`http://127.0.0.1:${server.address().port}/v1/chat/completions`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({model:'codex-capricorn',messages:[{role:'user',content:'Włącz hit Night City'}],tools:[{type:'function',function:{name:'play_music',parameters:{required:['song']}}}]})});
    const result=await response.json();assert.equal(response.status,200);assert.equal(result.choices[0].finish_reason,'tool_calls');assert.equal(result.choices[0].message.tool_calls[0].function.name,'play_music');assert.equal(result.choices[0].message.content,null);
  }finally{await new Promise(r=>server.close(r));}
});
test('Capri context validation',()=>{
  assert.throws(()=>capriPrompt({model:'other',messages:[]}));
  assert.throws(()=>capriPrompt({model:'codex-capricorn',stream:true,messages:[]}));
  assert.match(capriPrompt({model:'codex-capricorn',messages:[{role:'system',content:'Mów po polsku'},{role:'user',content:'Cześć'}]}),/Mów po polsku/);
});

test('lore reaches normal chat and event speech with rotating relevant themes and canon boundaries',()=>{
  const plain=capriPrompt({model:'codex-capricorn',messages:[{role:'user',content:'Co to jest braindance?'}]});
  assert.match(plain,/FAKTY:/);assert.match(plain,/Braindance/);assert.match(plain,/Nie wymyślaj spotkań Capri/);
  const messages=[{role:'user',content:'Event: throws you. Example: not a frisbee.'}];
  const first=loreContext(messages,true),second=loreContext(messages,true);
  assert.notEqual(first,second);assert.match(first,/W tej spontanicznej reakcji/);assert.match(first,/Limit długości/);
  assert.match(first,/Samo 'kto śpiewa\?' nie jest taką prośbą/);
  assert.match(first,/Nie wciskaj na siłę ciekawostek/);
  assert.match(plain,/dopiero gdy użytkownik wyraźnie zapyta/);
  assert.match(first,/Nie zdradzaj spontanicznie/);
  const lore=JSON.parse(readFileSync(new URL('./lore.json',import.meta.url),'utf8'));
  assert.equal(new Set(lore.facts.map(f=>f.id)).size,lore.facts.length);
  for(const fact of lore.facts)assert.ok(lore.sources[fact.source],`Missing source for ${fact.id}`);
  assert.ok(first.length<10000);
});

test('images travel separately from textual context',()=>{
  const image='data:image/png;base64,aGVsbG8=';
  const body={model:'codex-capricorn',stream:true,messages:[{role:'user',content:[{type:'text',text:'Co widzisz?'},{type:'image_url',image_url:{url:image}}]}]};
  assert.deepEqual(normalizeMessages(body).imageUrls,[image]);
  assert.match(capriPrompt(body),/Co widzisz/);
  assert.doesNotMatch(capriPrompt(body),/base64/);
  assert.throws(()=>normalizeMessages({...body,messages:[{role:'user',content:[{type:'image_url',image_url:{url:'http://localhost/private'}}]}]}));
});

test('SSE forwards final answer deltas before completion, excludes commentary, and forwards images',async()=>{
  let notify,done=false,forwarded;
  const image='data:image/png;base64,aGVsbG8=';
  const server=createGateway(async(name,args)=>{
    if(name==='chat_send'){
      forwarded=args;
      setTimeout(()=>{
        const params={threadId:'thread',turnId:'ours'};
        notify({method:'item/started',params:{...params,item:{id:'thinking',type:'agentMessage',phase:'commentary'}}});
        notify({method:'item/agentMessage/delta',params:{...params,itemId:'thinking',delta:'SECRET'}});
        notify({method:'item/started',params:{...params,item:{id:'answer',type:'agentMessage',phase:'final_answer'}}});
        notify({method:'item/agentMessage/delta',params:{...params,itemId:'answer',delta:'Widzę '}});
      },10);
      return {threadId:'thread',turn:{id:'ours'}};
    }
    return {data:[{id:'ours',status:done?'completed':'inProgress',items:done?[{type:'agentMessage',phase:'final_answer',text:'Widzę obraz.'}]:[]}]};
  },{pollMs:5,subscribe:fn=>{notify=fn;return ()=>{notify=null;};}});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  try{
    const response=await fetch(`http://127.0.0.1:${server.address().port}/v1/chat/completions`,{method:'POST',body:JSON.stringify({model:'codex-capricorn',stream:true,messages:[{role:'user',content:[{type:'text',text:'Co widzisz?'},{type:'image_url',image_url:{url:image}}]}]})});
    assert.match(response.headers.get('content-type'),/text\/event-stream/);
    const reader=response.body.getReader();let received='';
    while(!received.includes('Widzę ')){const part=await reader.read();assert.equal(part.done,false);received+=new TextDecoder().decode(part.value);}
    assert.equal(done,false);
    done=true;
    while(true){const part=await reader.read();if(part.done)break;received+=new TextDecoder().decode(part.value);}
    assert.doesNotMatch(received,/SECRET/);assert.match(received,/obraz\./);assert.match(received,/\[DONE\]/);assert.deepEqual(forwarded.imageUrls,[image]);
  }finally{done=true;await new Promise(r=>server.close(r));}
});
test('HTTP waits for matching turn and returns final answer only',async()=>{
  const calls=[];
  const server=createGateway(async(name,args)=>{
    calls.push([name,args]);
    if(name==='chat_send')return {turn:{id:'ours'}};
    return {data:[{id:'another',status:'completed',items:[]},{id:'ours',status:'completed',items:[{type:'agentMessage',phase:'commentary',text:'thinking'},{type:'agentMessage',phase:'final_answer',text:'Witaj!'}]}]};
  },{pollMs:1});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const base=`http://127.0.0.1:${server.address().port}`;
  try{
    const request={method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({model:'codex-capricorn',stream:false,messages:[{role:'user',content:'Hej'}]})};
    const res=await fetch(base+'/v1/chat/completions',request);assert.equal(res.status,200);assert.equal((await res.json()).choices[0].message.content,'Witaj!');assert.equal(calls[0][0],'chat_send');
    const rejected=await fetch(base+'/v1/chat/completions',{...request,headers:{origin:'https://example.com'}});assert.equal(rejected.status,403);
  }finally{await new Promise(r=>server.close(r));}
});

test('queued questions precede animation reactions without concurrent Codex turns',async()=>{
  let unlock,turn=0;
  const firstGate=new Promise(resolve=>{unlock=resolve;});
  const order=[];
  const server=createGateway(async(name,args)=>{
    if(name==='chat_send'){
      order.push(args.message.match(/FIRST|REACTION|QUESTION/)[0]);
      turn++;
      return {threadId:'thread',turn:{id:String(turn)}};
    }
    if(turn===1)await firstGate;
    return {data:[{id:String(turn),status:'completed',items:[{type:'agentMessage',phase:'final_answer',text:'OK'}]}]};
  },{pollMs:1});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  const send=(text,event=false)=>fetch(base+'/v1/chat/completions',{method:'POST',body:JSON.stringify({model:'codex-capricorn',capri_event:event,messages:[{role:'user',content:text}]})}).then(async response=>{assert.equal(response.status,200);return response.json();});
  try{
    const first=send('FIRST');
    while(order.length===0)await new Promise(resolve=>setTimeout(resolve,1));
    const reaction=send('REACTION',true);
    while((await (await fetch(base+'/health')).json()).queued<1)await new Promise(resolve=>setTimeout(resolve,1));
    const question=send('QUESTION');
    while((await (await fetch(base+'/health')).json()).queued<2)await new Promise(resolve=>setTimeout(resolve,1));
    unlock();await Promise.all([first,reaction,question]);
    assert.deepEqual(order,['FIRST','QUESTION','REACTION']);
  }finally{unlock();await new Promise(resolve=>server.close(resolve));}
});

test('user question interrupts an active background reaction instead of waiting for it',async()=>{
  let activeId=0,wasInterrupted=false;
  const calls=[];
  const server=createGateway(async(name,args)=>{
    if(name==='chat_send'){calls.push(args.message);return {threadId:'thread',turn:{id:String(++activeId)}};}
    return {data:[{id:String(activeId),status:activeId===1?(wasInterrupted?'interrupted':'inProgress'):'completed',items:[{type:'agentMessage',phase:'final_answer',text:'OK'}]}]};
  },{pollMs:2,interrupt:async id=>{assert.equal(id,'1');wasInterrupted=true;}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  const send=(text,event)=>fetch(base+'/v1/chat/completions',{method:'POST',body:JSON.stringify({model:'codex-capricorn',capri_event:event,messages:[{role:'user',content:text}]})});
  try{
    const background=send('background reaction',true);
    while(activeId===0)await new Promise(resolve=>setTimeout(resolve,1));
    const question=await send('user question',false);
    assert.equal(question.status,200);assert.equal(wasInterrupted,true);
    assert.equal((await background).status,502);assert.equal(calls.length,2);
  }finally{wasInterrupted=true;await new Promise(resolve=>server.close(resolve));}
});
