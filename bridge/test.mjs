import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {callTool} from './server.mjs';
test('odrzuca błędne argumenty przed połączeniem',async()=>{
  await assert.rejects(callTool('chat_send',{message:''}));
  await assert.rejects(callTool('chat_send',{message:'Obraz',imageUrls:['https://example.com/image.png']}));
  await assert.rejects(callTool('chat_send',{message:'Obraz',imageUrls:Array(5).fill('data:image/png;base64,aGVsbG8=')}));
  await assert.rejects(callTool('chat_history',{limit:101}));
  await assert.rejects(callTool('unknown',{}));
});
test('MCP initialize i narzędzia przez stdio',async()=>{
  const p=spawn(process.execPath,[new URL('./server.mjs',import.meta.url).pathname.replace(/^\/(\w:)/,'$1')],{windowsHide:true});
  const lines=createInterface({input:p.stdout});let seq=0;const pending=new Map();
  lines.on('line',l=>{const m=JSON.parse(l);pending.get(m.id)?.(m);pending.delete(m.id);});
  const request=(method,params)=>new Promise(resolve=>{const id=++seq;pending.set(id,resolve);p.stdin.write(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n');});
  try{
    const init=await request('initialize',{protocolVersion:'2025-03-26'});assert.equal(init.result.protocolVersion,'2025-03-26');
    const list=await request('tools/list',{});assert.equal(list.result.tools.length,3);
    const bad=await request('tools/call',{name:'chat_send',arguments:{message:''}});assert.equal(bad.result.isError,true);
  }finally{p.stdin.end();p.kill();lines.close();}
});
