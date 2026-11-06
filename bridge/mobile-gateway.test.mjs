import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {createMobileGateway} from './mobile-gateway.mjs';
test('mobile access requires token and forwards only supported routes without leaking token',async()=>{
  const token='test-'.repeat(10);let auth;
  const upstream=http.createServer((req,res)=>{auth=req.headers.authorization;res.writeHead(200,{'content-type':'application/json'});res.end('{"status":"ok","backend":"antigravity-cli"}');});
  await new Promise(r=>upstream.listen(0,'127.0.0.1',r));
  const server=createMobileGateway(token,{upstreamPort:upstream.address().port});await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const base=`http://127.0.0.1:${server.address().port}`;
  try{assert.equal((await fetch(base+'/health')).status,401);const headers={Authorization:'Bearer '+token};const good=await fetch(base+'/health',{headers});assert.equal(good.status,200);assert.equal((await good.json()).backend,'antigravity-cli');assert.equal(auth,undefined);assert.equal((await fetch(base+'/secret',{headers})).status,404);assert.equal((await fetch(base+'/health',{headers:{...headers,Origin:'https://example.com'}})).status,401);}finally{server.closeAllConnections();upstream.closeAllConnections();await Promise.all([new Promise(r=>server.close(r)),new Promise(r=>upstream.close(r))]);}
});
