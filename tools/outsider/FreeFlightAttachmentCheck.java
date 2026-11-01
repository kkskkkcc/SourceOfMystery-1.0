import com.sourceofmystery.client.OutsiderFreeFlightSpring;
import org.joml.*;
import java.lang.Math;

public final class FreeFlightAttachmentCheck {
    public static void main(String[] args){
        double maxRate=0,maxAngle=0,motion=0;
        for(int fps:new int[]{15,30,60,144})for(float length:new float[]{.085f,.55f,1.8f}){
            var solver=new OutsiderFreeFlightSpring();var root=new Vector3f(0,18,0);var rest=new Vector3f(0,-length,0);var previous=new Vector3f(rest).normalize();float time=0;
            Vector3f[] capsules={new Vector3f(0,16,0),new Vector3f(0,19,0)};float[] radii={.55f};
            for(int frame=0;frame<fps*16;frame++){
                float dt=frame%137==0?0:frame%83==0?.12f:1f/fps;time+=dt;
                float yaw=(float)Math.sin(time*.9)*1.8f*dt;
                var shift=new Vector3f((float)Math.sin(time*1.2)*18*dt,(float)Math.sin(time*.6)*2*dt,-18*dt);
                rest.set(.025f*(float)Math.sin(time),-length,.025f*(float)Math.cos(time)).normalize().mul(length);
                if(frame==0)solver.reset(new Vector3f(root).add(rest));else solver.rebase(yaw,shift);
                int count=Math.max(1,(int)Math.ceil(dt*120));
                for(int i=0;i<count;i++)solver.step(root,rest,new Vector3f(.15f,-.25f,.18f),dt/count,180,40,.085f,capsules,radii);
                var actual=new Vector3f(solver.current).sub(root);var unit=new Vector3f(actual).normalize();
                double angle=Math.acos(Math.max(-1,Math.min(1,unit.dot(new Vector3f(rest).normalize()))));
                if(!solver.current.isFinite()||!solver.previous.isFinite()||angle>.0853||Math.abs(actual.length()-length)>.00003)throw new AssertionError("Escaped flight attachment");
                if(time>2&&dt>0){maxRate=Math.max(maxRate,Math.atan2(new Vector3f(unit).cross(previous).length(),unit.dot(previous))/dt);motion+=unit.distance(previous);}
                maxAngle=Math.max(maxAngle,angle);previous.set(unit);
                var before=new Vector3f(solver.current);solver.step(root,rest,new Vector3f(),0,180,40,.085f,capsules,radii);
                if(before.distance(solver.current)>.00002)throw new AssertionError("Zero-delta drift");
            }
        }
        if(motion<.1||Math.toDegrees(maxRate)>60)throw new AssertionError("Frozen or snapping flight attachment");
        System.out.println("{\"passed\":true,\"fps\":[15,30,60,144],\"maximum_degrees\":"+Math.toDegrees(maxAngle)+",\"maximum_degrees_per_second\":"+Math.toDegrees(maxRate)+",\"motion\":"+motion+"}");
    }
}
