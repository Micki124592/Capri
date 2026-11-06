package pl.capri.mobile;
import java.util.Random;

/** Local desktop-pet motion; no network, screen inspection or model requests. */
final class PetMotion {
    float x,y,maxX,maxY,targetX,targetY,vx,vy;
    boolean dragging,falling,moving,left;
    String act="hop";
    private final Random random;
    private double nextDecision;
    PetMotion(Random random){this.random=random;}
    void bounds(float width,float height){maxX=Math.max(0,width);maxY=Math.max(0,height);x=clamp(x,maxX);y=clamp(y,maxY);targetX=clamp(targetX,maxX);targetY=clamp(targetY,maxY);}
    private float clamp(float value,float max){return Math.max(0,Math.min(max,value));}
    void grab(){dragging=true;moving=false;falling=false;act="grabbed";}
    void drag(float nx,float ny){x=clamp(nx,maxX);y=clamp(ny,maxY);}
    void drop(float speedX,float speedY){dragging=false;falling=true;vx=Math.max(-500,Math.min(500,speedX));vy=Math.max(-500,Math.min(500,speedY));act="tumble";}
    void tap(double now){dragging=false;falling=false;moving=false;nextDecision=now+3;act=random.nextBoolean()?"hop":"victory";}
    void step(double now,float delta){
        float dt=Math.max(0,Math.min(.1f,delta));
        if(dragging)return;
        if(falling){
            vy+=650*dt;x+=vx*dt;y+=vy*dt;
            if(x<0||x>maxX){x=clamp(x,maxX);vx*=-.35f;}
            y=Math.max(0,y);
            if(y>=maxY){y=maxY;falling=false;moving=false;act="land";nextDecision=now+2;}
            return;
        }
        if(now>=nextDecision){
            if(random.nextInt(4)==0){moving=false;String[] choices={"stretch","curious","look","spin","hop","sit"};act=choices[random.nextInt(choices.length)];nextDecision=now+2+random.nextDouble()*4;}
            else{targetX=random.nextFloat()*maxX;targetY=random.nextInt(3)==0?random.nextFloat()*maxY:maxY;moving=true;act="stand";nextDecision=now+12;}
        }
        if(moving){float dx=targetX-x,dy=targetY-y,distance=(float)Math.sqrt(dx*dx+dy*dy),speed=55*dt;
            left=dx<0;
            if(distance<=speed){x=targetX;y=targetY;moving=false;act="look";nextDecision=now+1+random.nextDouble()*3;}
            else{x+=dx/distance*speed;y+=dy/distance*speed;}
        }
        x=clamp(x,maxX);y=clamp(y,maxY);
    }
}
