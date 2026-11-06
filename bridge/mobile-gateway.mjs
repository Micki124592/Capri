import http from 'node:http';
import {timingSafeEqual} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

export function createMobileGateway(token,{upstreamPort=8787}={}){
  if(typeof token!=='string'||token.length<32)throw new Error('Klucz telefonu musi mieć przynajmniej 32 znaki.');
  return http.createServer((req,res)=>{
    const received=Buffer.from(req.headers.authorization??''),expected=Buffer.from('Bearer '+token);
    if(received.length!==expected.length||!timingSafeEqual(received,expected)||req.headers.origin){res.writeHead(401,{'Content-Type':'application/json'});res.end('{"error":{"message":"Nieprawidłowy klucz telefonu"}}');return;}
    if(!((req.method==='GET'&&req.url==='/health')||(req.method==='POST'&&req.url==='/v1/chat/completions'))){res.writeHead(404);res.end();return;}
    const upstream=http.request({hostname:'127.0.0.1',port:upstreamPort,path:req.url,method:req.method,headers:{'Content-Type':'application/json','Accept':req.headers.accept??'application/json'}},response=>{res.writeHead(response.statusCode,{'Content-Type':response.headers['content-type']??'application/json','Cache-Control':'no-store'});response.pipe(res);});
    upstream.setTimeout(125000,()=>upstream.destroy(new Error('timeout')));
    upstream.on('error',()=>{if(!res.headersSent){res.writeHead(502,{'Content-Type':'application/json'});res.end('{"error":{"message":"Główny serwer Capri jest niedostępny"}}');}else res.destroy();});
    let bytes=0;req.on('data',chunk=>{bytes+=chunk.length;if(bytes>16*1024*1024){upstream.destroy();req.destroy();}});
    req.pipe(upstream);res.on('close',()=>upstream.destroy());
  });
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const config=JSON.parse(readFileSync(new URL('./mobile.local.json',import.meta.url)));
  createMobileGateway(config.token).listen(8788,'127.0.0.1',()=>console.log('Capri Mobile: lokalny proxy 8788 gotowy.'));
}
