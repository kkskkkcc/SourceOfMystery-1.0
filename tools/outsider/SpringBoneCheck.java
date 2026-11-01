import com.sourceofmystery.client.SpringBoneSolver;
import org.joml.*;
import java.lang.Math;

/** Stress real spring code under yaw changes, variable frame time and paused poses. */
public class SpringBoneCheck {
    public static void main(String[] args){
        double maximumAngle=0,maximumLengthError=0;
        for(int fps:new int[]{15,30,60,144}){
            SpringBoneSolver[] chain=new SpringBoneSolver[18];
            for(int i=0;i<chain.length;i++)chain[i]=new SpringBoneSolver();
            Vector3f tail=new Vector3f(0,-.55f,.045f),shift=new Vector3f();
            float length=tail.length(),cone=.16f;
            for(int frame=0;frame<fps*12;frame++){
                float t=frame/(float)fps;
                Quaternionf authored=new Quaternionf().rotateXYZ(.35f*(float)Math.sin(t*1.7),.9f*(float)Math.sin(t*2),0);
                Vector3f rest=authored.transform(new Vector3f(tail));
                Vector3f root=new Vector3f(0,18,0);Quaternionf parent=new Quaternionf(authored);
                for(int i=0;i<chain.length;i++){
                    SpringBoneSolver solver=chain[i];
                    if(frame==0)solver.reset(new Vector3f(root).add(rest));
                    else solver.rebase(.65f/fps,shift.set(.02f,0,.09f));
                    Vector3f inherited=parent.transform(new Vector3f(tail));
                    float dt=frame%71==0?0:1f/fps;
                    int substeps=Math.max(1,(int)Math.ceil(dt*120));
                    for(int n=0;n<substeps;n++)solver.step(root,rest,new Vector3f(.2f,-.7f,.25f),dt/substeps,95,24,cone,new Vector3f[0],new float[0]);
                    Vector3f solved=new Vector3f(solver.current).sub(root);
                    Quaternionf world=new Quaternionf().rotationTo(inherited,solved).mul(parent).normalize();
                    Vector3f actual=world.transform(new Vector3f(tail));
                    double angle=Math.acos(Math.max(-1,Math.min(1,actual.dot(rest)/(actual.length()*rest.length()))));
                    double error=Math.abs(solved.length()-length);
                    if(!solver.current.isFinite()||!solver.previous.isFinite()||angle>cone+.0002||error>.00002)
                        throw new AssertionError("Unstable strand: fps="+fps+" frame="+frame+" joint="+i+" angle="+angle+" length error="+error);
                    maximumAngle=Math.max(maximumAngle,angle);maximumLengthError=Math.max(maximumLengthError,error);
                    root.add(actual);parent.set(world);
                }
            }
        }
        System.out.println("{\"passed\":true,\"fps\":[15,30,60,144],\"chain_bones\":18,\"maximum_world_cone_degrees\":"+Math.toDegrees(maximumAngle)+",\"maximum_tail_length_error\":"+maximumLengthError+"}");
    }
}
