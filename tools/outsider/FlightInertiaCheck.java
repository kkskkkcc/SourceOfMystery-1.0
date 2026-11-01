import com.sourceofmystery.client.OutsiderFlightInertia;
import org.joml.*;
import java.lang.Math;
import java.nio.*;
import java.nio.file.*;

/** Exercise the shipped clips and actual runtime modifier under frame-rate changes. */
public final class FlightInertiaCheck {
    public static void main(String[] args)throws Exception {
        ByteBuffer mesh=ByteBuffer.wrap(Files.readAllBytes(Path.of("src/main/resources/assets/sourceofmystery/meshes/entity/outsider.bin"))).order(ByteOrder.LITTLE_ENDIAN);
        mesh.getInt();mesh.getInt();int vertices=mesh.getInt();mesh.getInt();int groups=mesh.getInt();
        mesh.position(20+vertices*64+groups*32);int bones=mesh.getInt();int[] parents=new int[bones];
        for(int i=0;i<bones;i++){parents[i]=mesh.getInt();mesh.position(mesh.position()+12);}
        double maxThigh=0,maxLeg=0,maxPelvisMotion=0;
        for(String name:new String[]{"flight_idle","flight_move","descend"}){
            ByteBuffer motion=ByteBuffer.wrap(Files.readAllBytes(Path.of("src/main/resources/assets/sourceofmystery/animations/entity/outsider/"+name+".bin"))).order(ByteOrder.LITTLE_ENDIAN);
            motion.position(12);int frames=motion.getInt(),fps=motion.getInt();float[] values=new float[frames*bones*7];motion.asFloatBuffer().get(values);
            for(int rate:new int[]{15,30,60,144}){
                var inertia=new OutsiderFlightInertia();Matrix4f[] world=new Matrix4f[bones];
                for(int i=0;i<bones;i++)world[i]=new Matrix4f();
                var q=new Quaternionf();var next=new Quaternionf();var waist=new Quaternionf();var p=new Vector3f();var p2=new Vector3f();
                for(int frame=0;frame<rate*10;frame++){
                    float t=frame/(float)rate,age=t*20,dt=frame%71==0?0:1f/rate;
                    inertia.step((float)Math.sin(t*2)*24,-24*(float)Math.cos(t*.7),90*(float)Math.sin(t*1.4),dt,frame==0);
                    float sample=t*fps%(frames-1),mix=sample-(int)sample;
                    for(int i=0;i<bones;i++){
                        int a=((int)sample*bones+i)*7,b=(((int)sample+1)*bones+i)*7;
                        p.set(values[a],values[a+1],values[a+2]);p2.set(values[b],values[b+1],values[b+2]);p.lerp(p2,mix);
                        q.set(values[a+3],values[a+4],values[a+5],values[a+6]);next.set(values[b+3],values[b+4],values[b+5],values[b+6]);q.slerp(next,mix);
                        if(i==5)waist.set(q);
                        inertia.apply(i,q,age);
                        if(i==5)maxPelvisMotion=Math.max(maxPelvisMotion,waist.difference(q,new Quaternionf()).angle());
                        world[i].translationRotate(p,q);if(parents[i]>=0)world[parents[i]].mul(world[i],world[i]);
                    }
                    Vector3f left=world[99].getTranslation(new Vector3f()).sub(world[98].getTranslation(new Vector3f()));
                    Vector3f right=world[103].getTranslation(new Vector3f()).sub(world[102].getTranslation(new Vector3f()));
                    double thigh=Math.toDegrees(left.angle(right));
                    left=world[100].getTranslation(new Vector3f()).sub(world[98].getTranslation(new Vector3f()));
                    right=world[104].getTranslation(new Vector3f()).sub(world[102].getTranslation(new Vector3f()));
                    double leg=Math.toDegrees(left.angle(right));
                    if(!Double.isFinite(thigh)||!Double.isFinite(leg)||thigh>=30||leg>=30)throw new AssertionError(name+" rate="+rate+" t="+t+" leg="+leg+" thigh="+thigh);
                    maxThigh=Math.max(maxThigh,thigh);maxLeg=Math.max(maxLeg,leg);
                }
            }
        }
        if(maxPelvisMotion<Math.toRadians(1))throw new AssertionError("Flight inertia did not reach the shared pelvis");
        System.out.println("{\"passed\":true,\"fps\":[15,30,60,144],\"maximum_thigh_degrees\":"+maxThigh+",\"maximum_whole_leg_degrees\":"+maxLeg+",\"pelvis_inertia_degrees\":"+Math.toDegrees(maxPelvisMotion)+"}");
    }
}
