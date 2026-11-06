import {SpriteRenderer} from './avatar/sprite.js';
import {Player,NEUTRAL,ACTS} from './avatar/acting.js';
const canvas=document.getElementById('pet'),pose=document.getElementById('pose');
let renderer,action=new Player('hop'),stance=new Player('stand'),state={moving:false,left:false,dragging:false,falling:false},last=0;
window.capriPet=next=>{
  if(next.act&&ACTS[next.act]&&next.act!==state.act)action=new Player(next.act);
  state={...state,...next};
};
window.capriEvent=(type,value)=>{if(type!=='status')return;const names={speaking:'talk',thinking:'look',listening:'curious',success:'victory',error:'flinch'};if(names[value.state])action=new Player(names[value.state]);};
function resize(){if(renderer)renderer.resize(Math.min((innerWidth-8)/352,(innerHeight-14)/532));}
const image=new Image();image.src='art/capri-full.png';image.onload=()=>{renderer=new SpriteRenderer(canvas,image);resize();requestAnimationFrame(frame);window.PetNative?.ready();};
function frame(now){
  requestAnimationFrame(frame);if(document.hidden||now-last<1000/24)return;last=now;
  const t=now/1000;let drawing={...NEUTRAL,...stance.sample(now)?.pose};
  if(action){const sample=action.sample(now);if(sample)Object.assign(drawing,action.resolve(sample.pose,1));else action=null;}
  const walking=state.moving&&!state.dragging&&!state.falling;
  const wave=Math.sin(t*9);
  const p={...drawing,seed:Math.floor(now/83),boil:.18,head:drawing.head||0,mane:drawing.mane||0,hair:(drawing.hair||0)+Math.sin(t*2.5)*1.5,
    glitch:0,glitchSeed:Math.floor(now/120),split:0,collapse:0,dissolve:0,sparkle:.7,grade:1,sil:[0,0,0],silAmount:0,misreg:[.2,-.1],alpha:1,
    sit:state.act==='sit'?1:0,bend:drawing.bend||0,longL:drawing.longL||1,longR:drawing.longR||1};
  if(walking){p.armL+=wave*.18;p.armR-=wave*.18;p.hair+=wave*2;}
  renderer.draw(p);
  const bob=(drawing.bob||0)+(walking?-Math.abs(wave)*4:Math.sin(t*1.8)*1.5);
  const tilt=(drawing.tilt||0)+(walking?wave*2:0);
  pose.style.transform=`translate(-50%,calc(-50% + ${bob}px)) rotate(${tilt}deg) scale(${(drawing.sx||1)*(state.left?-1:1)},${drawing.sy||1})`;
}
addEventListener('resize',resize);
