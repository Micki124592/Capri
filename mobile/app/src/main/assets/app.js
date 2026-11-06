import {SpriteRenderer} from './avatar/sprite.js';
import {Player,NEUTRAL,ACTS} from './avatar/acting.js';

const $=id=>document.getElementById(id);
const overlay=new URLSearchParams(location.search).has('overlay');
if(overlay)document.body.classList.add('overlay');
let cfg={},draft=null,busy=false,timer=0,started=0;
const native=(name,payload='')=>window.Android?.action(name,typeof payload==='string'?payload:JSON.stringify(payload));
const messages=$('messages');
function scroll(){messages.scrollTop=messages.scrollHeight;}
function addMessage(role,text){
  $('welcome')?.remove();
  const el=document.createElement('div');el.className=`message ${role}`;
  const who=document.createElement('span');who.className='who';who.textContent=role==='user'?'TY':role==='tool'?'DZIAŁANIE / TELEFON':'CAPRI';
  const content=document.createElement('span');content.className='content';content.textContent=text;
  el.append(who,content);messages.append(el);scroll();return el;
}
window.capriEvent=(type,p)=>{
  if(type==='config'){
    cfg=p;
    for(const id of ['ollama','model','voiceId','fishModel']) if($(id)!==document.activeElement)$(id).value=p[id]||'';
    $('voice').checked=p.voice;
    $('gatewayKey').value='';$('gatewayKey').placeholder=p.hasGatewayKey?'Klucz dostępu zapisany':'Wpisz klucz z instrukcji';
    $('apiKey').value='';$('apiKey').placeholder=p.hasKey?'Klucz zapisany — wpisz, aby zmienić':'Wpisz klucz na telefonie';
    $('key-state').textContent=p.hasKey?'Klucz jest zaszyfrowany w Android Keystore. Puste pole zachowa obecny klucz.':'Wpisz klucz Fish Audio. Bez klucza działa czat tekstowy i awatar.';
    $('connection').textContent=p.ollama?'GEMINI / '+p.model:'GEMINI / USTAW ADRES';
    $('access-state').textContent=p.accessibility?'Połączone — tylko YouTube Music':'Wyłączone';
    $('notification-state').textContent=p.notifications?'Połączony — sprawdzanie tytułu':'Niepołączony';
    $('shizuku-state').textContent=p.shizuku?'Połączone':p.shizukuAvailable?'Działa — połącz Capri':'Uruchom Shizuku na telefonie';
    $('overlay').textContent=p.overlay?'Ukryj awatara':'Pokaż nad aplikacjami';
  }
  if(type==='history'){
    if(p.items.length){messages.replaceChildren();for(const item of p.items)addMessage(item.role,item.content);}
    else if(!messages.querySelector('#welcome')){messages.replaceChildren();const empty=document.createElement('p');empty.className='hint';empty.textContent='Nowa rozmowa. Hej, choom!';messages.append(empty);}
  }
  if(type==='message'){draft?.remove();draft=null;addMessage(p.role,p.text);}
  if(type==='partial'){
    if(!p.text){draft?.remove();draft=null;}else{if(!draft){draft=addMessage('assistant','');draft.classList.add('draft');}draft.querySelector('.content').textContent=p.text;scroll();}
  }
  if(type==='busy'){
    busy=p.value;$('send').disabled=busy;
    clearInterval(timer);$('elapsed').textContent='';
    if(busy){started=Date.now();timer=setInterval(()=>$('elapsed').textContent=`${((Date.now()-started)/1000).toFixed(1)} s`,100);}
  }
  if(type==='status'){$('status').textContent=p.text;$('settings-feedback').textContent=p.text;document.body.dataset.state=p.state;avatar.state(p.state);}
  if(type==='level')avatar.level=p.value;
};
function send(text){if(!text.trim()||busy)return;native('send',text.trim());$('input').value='';$('input').style.height='auto';}
$('send').onclick=()=>send($('input').value);
$('input').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send(e.target.value);}};
$('input').oninput=e=>{e.target.style.height='auto';e.target.style.height=Math.min(e.target.scrollHeight,95)+'px';};
for(const button of document.querySelectorAll('[data-say]'))button.onclick=()=>send(button.dataset.say);
for(const id of ['mic','stop','test','voice_test','shizuku','accessibility','notifications','overlay','clear','licenses']){
  const el=$(id.replace('_','-'));if(el)el.onclick=()=>{if(id==='test'||id==='voice_test')save(false);native(id);};
}
function save(show=true){
  const data={};for(const id of ['ollama','model','voiceId','fishModel','apiKey','gatewayKey'])data[id]=$(id).value;
  data.voice=$('voice').checked;native('save',data);
  if(show){$('settings').hidden=true;$('main').hidden=false;}
}
$('save').onclick=()=>save();
$('key-clear').onclick=()=>{native('save',{...cfg,apiKey:'',clearKey:true});};
$('settings-open').onclick=()=>{$('settings').hidden=false;$('main').hidden=true;};
$('settings-close').onclick=()=>{$('settings').hidden=true;$('main').hidden=false;};

// Original Capri rig + animation exposure sheets, adapted to touch and phone states.
const avatar={mode:'idle',player:null,stance:new Player('stand'),level:0,renderer:null,last:0,nextIdle:0,x:0,y:0,started:performance.now(),
  state(mode){this.mode=mode;if(mode==='speaking')this.act('talk');else if(mode==='listening')this.act('curious');else if(mode==='thinking')this.act('look');else if(mode==='acting')this.act('zap');else if(mode==='success')this.act('victory');else if(mode==='error')this.act('flinch');else if(mode==='alert')this.act('alarm');},
  act(name){if(!ACTS[name])return;this.player=new Player(name);this.nextIdle=performance.now()+3500;if(['victory','zap','spin','hop'].includes(name))burst();},
  resize(){if(!this.renderer)return;const height=$('stage').clientHeight;this.scale=Math.min(overlay?.48:.67,Math.max(.25,height/490));this.renderer.resize(this.scale);},
};
function burst(){
  if(matchMedia('(prefers-reduced-motion: reduce)').matches)return;
  for(let i=0;i<16;i++){const p=document.createElement('i');p.className='particle';p.style.left='50%';p.style.top='45%';p.style.setProperty('--x',`${(Math.random()-.5)*220}px`);p.style.setProperty('--y',`${(Math.random()-.5)*180}px`);$('particles').append(p);setTimeout(()=>p.remove(),800);}
}
const image=new Image();image.src='art/capri-full.png';
image.onload=()=>{
  try{avatar.renderer=new SpriteRenderer($('sprite'),image);avatar.resize();avatar.act('hop');requestAnimationFrame(frame);}
  catch(error){$('sprite').hidden=true;$('fallback').hidden=false;$('status').textContent='WebGL niedostępne — awatar w trybie podstawowym.';}
};
function frame(now){
  requestAnimationFrame(frame);
  if(document.hidden)return;
  const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fps=reduced?8:avatar.mode==='idle'?18:24;if(now-avatar.last<1000/fps)return;avatar.last=now;
  let base=avatar.stance.sample(now)?.pose||{};
  let pose={...NEUTRAL,...base};
  if(avatar.player){const sample=avatar.player.sample(now);if(sample)Object.assign(pose,avatar.player.resolve(sample.pose,1));else avatar.player=null;}
  if(!avatar.player && !reduced){
    if(avatar.mode==='speaking')avatar.act(Math.random()<.2?'hop':'talk');
    else if(now>avatar.nextIdle){avatar.act(avatar.mode==='thinking'?'look':['curious','look','hop','spin','stretch','split'][Math.floor(Math.random()*6)]);avatar.nextIdle=now+3500+Math.random()*4500;}
  }
  const t=now/1000;
  const p={...pose,seed:Math.floor(now/83),boil:reduced?0:.25,hair:pose.hair+(reduced?0:Math.sin(t*2.5)*1.6),
    glitch:!reduced && avatar.mode==='acting'?.10:0,glitchSeed:Math.floor(now/120),split:avatar.mode==='thinking'?Math.sin(t*4)*.5:0,
    collapse:reduced?0:Math.max(0,1-(now-avatar.started)/700),dissolve:0,sparkle:.7,grade:1,sil:[0,0,0],silAmount:0,misreg:[.35,-.1],alpha:1,
    sit:0,bend:pose.bend||0,longL:pose.longL||1,longR:pose.longR||1};
  if(avatar.mode==='listening'){p.mane+=avatar.level*.18;p.hair+=avatar.level*2;avatar.level*=.95;}
  if(overlay && walking){p.bob=(p.bob||0)+Math.abs(Math.sin(t*8))*6;p.tilt=(p.tilt||0)+Math.sin(t*8)*2;p.hair+=Math.sin(t*8)*2;p.sx*=facingLeft?-1:1;}
  avatar.renderer.draw(p);
  const floating=reduced?0:Math.sin(t*1.7)*3;
  $('avatar-wrap').style.transform=`translate(calc(-50% + ${avatar.x}px),${avatar.y+(pose.bob||0)+floating}px) rotate(${pose.tilt||0}deg) scale(${pose.sx||1},${pose.sy||1})`;
}
window.addEventListener('resize',()=>avatar.resize());
$('spark').onclick=()=>avatar.act('victory');
let touch=null;
$('avatar-wrap').addEventListener('pointerdown',e=>{touch={x:e.clientX,y:e.clientY,ox:avatar.x,oy:avatar.y,at:Date.now()};e.currentTarget.setPointerCapture(e.pointerId);});
$('avatar-wrap').addEventListener('pointermove',e=>{if(!touch)return;const max=$('stage').clientWidth*.3;avatar.x=Math.max(-max,Math.min(max,touch.ox+e.clientX-touch.x));avatar.y=Math.max(-45,Math.min(20,touch.oy+e.clientY-touch.y));});
$('avatar-wrap').addEventListener('pointerup',()=>{if(!touch)return;if(Date.now()-touch.at<230)avatar.act(['hop','spin','victory','split'][Math.floor(Math.random()*4)]);touch=null;});
native('ready');
window.capriPreview={avatar};

let walking=false,facingLeft=false;
window.capriWalk=(moving,left)=>{walking=moving;facingLeft=left;};
