package pl.capri.mobile;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetMotionTest {
    @Test public void autonomousMovementAlwaysStaysOnScreen(){PetMotion pet=new PetMotion(new Random(7));pet.bounds(240,700);boolean moved=false;for(int i=1;i<12000;i++){pet.step(i*.05,.05f);assertTrue(pet.x>=0&&pet.x<=240);assertTrue(pet.y>=0&&pet.y<=700);moved|=pet.x>10;}assertTrue(moved);}
    @Test public void dragPausesAutonomyAndThrowLands(){PetMotion pet=new PetMotion(new Random(7));pet.bounds(240,700);pet.grab();pet.drag(100,200);pet.step(10,.1f);assertEquals(100,pet.x,0);assertEquals(200,pet.y,0);pet.drop(200,-200);for(int i=1;i<50;i++)pet.step(10+i*.05,.05f);assertFalse(pet.falling);assertEquals(700,pet.y,0);pet.bounds(50,100);assertTrue(pet.x<=50&&pet.y<=100);}
}
