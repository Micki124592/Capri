import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import {AntigravityBackend} from './antigravity.mjs';

test('Antigravity completes a turn, hides its console and sends prompts over stdin',async()=>{
  let invocation,prompt='',finish;const completed=new Promise(resolve=>{finish=resolve;});
  const backend=new AntigravityBackend({antigravityPath:'agy',antigravityModel:'gemini-3.8-flash-low'},{launch:(file,args,options)=>{
    invocation={file,args,options};
    const child=new EventEmitter();child.stdin=new PassThrough();child.stdout=new PassThrough();child.stderr=new PassThrough();child.kill=()=>{};
    child.stdin.on('data',data=>{prompt+=data;});
    child.stdin.on('finish',()=>setImmediate(()=>{
      child.stdout.write(JSON.stringify({event:'result',result:{status:'SUCCESS',conversation_id:'conversation',response:'Cześć!'}})+'\n');
      child.emit('close',0);
      finish();
    }));return child;
  }});
  const started=await backend.callTool('chat_send',{message:'Hej Capri'});
  await completed;
  const history=await backend.callTool('chat_history',{});
  assert.equal(history.data[0].id,started.turn.id);assert.equal(history.data[0].status,'completed');assert.equal(history.data[0].items[0].text,'Cześć!');
  assert.equal(invocation.options.windowsHide,true);assert.equal(JSON.parse(prompt).message.content,'Hej Capri');
  assert.ok(invocation.args.includes('gemini-3.8-flash-low'));assert.ok(!invocation.args.includes('--dangerously-skip-permissions'));
});
test('Antigravity errors cannot be mistaken for successful speech',async()=>{
  let finish;const completed=new Promise(resolve=>{finish=resolve;});
  const backend=new AntigravityBackend({},{launch:()=>{
    const child=new EventEmitter();child.stdin=new PassThrough();child.stdout=new PassThrough();child.stderr=new PassThrough();child.kill=()=>{};
    child.stdin.on('finish',()=>setImmediate(()=>{child.stdout.write(JSON.stringify({event:'result',result:{status:'ERROR',error:'authentication required'}})+'\n');child.emit('close',1);finish();}));return child;
  }});
  await backend.callTool('chat_send',{message:'Hej'});await completed;
  const {data}=await backend.callTool('chat_history',{});assert.equal(data[0].status,'failed');assert.match(data[0].error.message,/authentication/);
});
