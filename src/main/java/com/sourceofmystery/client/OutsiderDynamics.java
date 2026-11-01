package com.sourceofmystery.client;

import com.sourceofmystery.entity.OutsiderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.*;
import java.io.*;
import java.lang.Math;

/** Per-entity simulation; resets on teleport/reload and never alters gameplay sockets. */
final class OutsiderDynamics {
    record Joint(int bone,Vector3f tail,float stiffness,float damping,float cone,int category){}
    static Joint[] load(int bones){
        Joint[] joints=new Joint[bones];
        try(var in=new DataInputStream(Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                new ResourceLocation("sourceofmystery","meshes/entity/outsider_springs.bin")).open())) {
            if(integer(in)!=0x4F535031||integer(in)!=1)throw new IOException("Invalid spring rig");
            int count=integer(in);if(count<1||count>bones)throw new IOException("Invalid spring count");
            for(int j=0;j<count;j++) {
                int bone=integer(in);Vector3f tail=new Vector3f(number(in),number(in),number(in));
                float stiffness=number(in),damping=number(in),cone=number(in);int category=integer(in);
                if(bone<0||bone>=bones||joints[bone]!=null||!tail.isFinite()||tail.length()<.01f)
                    throw new IOException("Invalid spring joint");
                joints[bone]=new Joint(bone,tail,stiffness,damping,cone,category);
            }
            com.sourceofmystery.SourceOfMystery.LOGGER.info("Outsider spring rig loaded: {} joints",count);
        }catch(IOException ex){throw new IllegalStateException("Cannot load Outsider spring rig",ex);}
        return joints;
    }
    private static int integer(DataInputStream in)throws IOException{return Integer.reverseBytes(in.readInt());}
    private static float number(DataInputStream in)throws IOException{return Float.intBitsToFloat(integer(in));}
    private final SpringBoneSolver[] solvers;
    private final OutsiderFreeFlightSpring[] freeFlightSolvers;
    private final Vector3f root=new Vector3f(),direction=new Vector3f(),inheritedDirection=new Vector3f(),targetDirection=new Vector3f(),force=new Vector3f(),shift=new Vector3f(),velocity=new Vector3f();
    private final Vector3f[] capsules={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    private final float[] hairRadii={.82f,.70f},clothRadii={.55f,.55f},noRadii={};
    private final Quaternionf rotation=new Quaternionf(),originalRotation=new Quaternionf();
    private final OutsiderFlightInertia flight=new OutsiderFlightInertia();
    private final OutsiderChestDynamics chest=new OutsiderChestDynamics();
    private final Vector3f hips=new Vector3f();
    private float ritualWind;
    private boolean cinematic,sampleOnly,wasCinematic;
    private boolean freeFlight,wasFreeFlight;
    private final Quaternionf[] cachedOffsets;
    private float lastAge=-1,lastYaw,step,age,bank;private double x,y,z;private boolean reset;
    OutsiderDynamics(int bones){solvers=new SpringBoneSolver[bones];freeFlightSolvers=new OutsiderFreeFlightSpring[bones];cachedOffsets=new Quaternionf[bones];}
    void begin(OutsiderEntity entity,float partial){
        age=entity.tickCount+partial;float yaw=entity.visualBodyYaw(partial);
        cinematic=entity.isOfferingIntro();
        freeFlight=!cinematic&&entity.combatState()==OutsiderEntity.AIR;
        sampleOnly=OutsiderShaderCompat.shadowPass()&&!cinematic;
        if(sampleOnly)return; // Shadow passes must never advance or reset the physical clock.
        ritualWind=entity.combatState()==OutsiderEntity.METEOR?
                com.sourceofmystery.entity.OutsiderRitual.chargeEnvelope(entity.animationSeconds(partial)):0;
        double nx=Mth.lerp(partial,entity.xOld,entity.getX()),ny=Mth.lerp(partial,entity.yOld,entity.getY()),nz=Mth.lerp(partial,entity.zOld,entity.getZ());
        float dt=(age-lastAge)/20;
        reset=lastAge<0||dt<0||dt>.25||cinematic!=wasCinematic||freeFlight!=wasFreeFlight||new org.joml.Vector3d(nx-x,ny-y,nz-z).lengthSquared()>16;
        wasCinematic=cinematic;
        wasFreeFlight=freeFlight;
        step=reset?0:Math.max(0,dt);
        shift.set((float)(nx-x),(float)(ny-y),(float)(nz-z)).div(OutsiderEntity.MODEL_SCALE).rotateY((yaw-180)*Mth.DEG_TO_RAD);
        if(reset){velocity.zero();bank=0;}else if(step>0){
            velocity.lerp(new Vector3f(shift).div(step),1-(float)Math.exp(-step*7));
            bank=Mth.lerp(1-(float)Math.exp(-step*6),bank,Mth.clamp(Mth.wrapDegrees(yaw-lastYaw)/step,-90,90));
            for(SpringBoneSolver solver:solvers)if(solver!=null)solver.rebase(Mth.wrapDegrees(yaw-lastYaw)*Mth.DEG_TO_RAD,shift);
            if(freeFlight)for(OutsiderFreeFlightSpring solver:freeFlightSolvers)if(solver!=null)solver.rebase(Mth.wrapDegrees(yaw-lastYaw)*Mth.DEG_TO_RAD,shift);
        }
        flight.step(velocity.x,velocity.z,bank,step,reset);
        lastAge=age;lastYaw=yaw;x=nx;y=ny;z=nz;
    }
    void ribcage(Matrix4f matrix){if(!sampleOnly)chest.step(matrix,velocity,age/20,step,reset);}
    void chest(int bone,Vector3f position,Quaternionf rotation){chest.apply(bone,position,rotation);}
    float chestDisplacement(){return chest.maximumDisplacement();}
    void body(int bone,Quaternionf local,OutsiderEntity entity){
        if(cinematic)return; // Authored contact IK; spring cloth/hair still runs below.
        if(!entity.attacking()&&!entity.isFlyingPose())return;
        // Contact-driven palms and the two-handed staff use their complete
        // authored arm chain. Runtime shoulder offsets invalidate those IK
        // solutions and visibly rotate/stretch a planted wrist.
        if(entity.combatState()==OutsiderEntity.SILENCE||entity.combatState()==OutsiderEntity.METEOR)return;
        float sway=(float)Math.sin(age*.19f)*.045f;
        // The common waist drives both sibling branches. Counterbalance the
        // chest so its net tilt stays the same while the legs follow with lag.
        flight.apply(bone,local,age);
        if(bone==11)local.rotateZ(Mth.clamp(bank*.0006f,-.07f,.07f));
        // Secondary inertia on the limbs keeps an authored flight loop from
        // reading as a rigid T-pose when the entity accelerates or banks.
        if(bone==25)local.rotateY(Mth.clamp(-velocity.x*.0025f,-.11f,.11f)).rotateZ(-sway);
        if(bone==30)local.rotateY(Mth.clamp(-velocity.x*.0018f,-.08f,.08f)).rotateZ(-sway*.65f);
        if(bone==55)local.rotateY(Mth.clamp(-velocity.x*.0025f,-.11f,.11f)).rotateZ(sway);
        if(bone==60)local.rotateY(Mth.clamp(-velocity.x*.0018f,-.08f,.08f)).rotateZ(sway*.65f);
        if(entity.combatState()==OutsiderEntity.GRASP||entity.combatState()==OutsiderEntity.SILENCE){
            float cast=(float)Math.sin(age*.32f)*.035f+(float)Math.sin(age*.11f)*.018f;
            if(bone==5)local.rotateX(-cast*.45f).rotateZ(cast*.3f);
            if(bone==6)local.rotateX(cast).rotateZ(-cast*.35f);
            if(bone==25||bone==30)local.rotateX(cast*.7f).rotateZ(-cast*.8f);
            if(bone==55||bone==60)local.rotateX(cast*.7f).rotateZ(cast*.8f);
            if(bone==83||bone==98)local.rotateZ(cast*.28f);
            if(bone==90||bone==102)local.rotateZ(-cast*.28f);
        }
    }
    void apply(Joint joint,Matrix4f[] world,Matrix4f[] authored){
        if(joint==null)return;
        Matrix4f matrix=world[joint.bone];matrix.getTranslation(root);
        if(sampleOnly){
            Quaternionf cached=cachedOffsets[joint.bone];
            if(cached!=null){matrix.getUnnormalizedRotation(originalRotation);cached.mul(originalRotation,rotation);matrix.translationRotate(root,rotation);}
            return;
        }
        matrix.transformDirection(joint.tail,inheritedDirection);
        // Absolute authored direction, not the already-deflected parent. A
        // 15-bone strand must have ONE bounded cone, not 15 cumulative bends.
        authored[joint.bone].transformDirection(joint.tail,direction);
        authored[5].getTranslation(hips);
        OutsiderCastingWind.bend(direction,root,hips,joint.category,age/20,ritualWind);
        SpringBoneSolver solver=solvers[joint.bone];
        if(solver==null){solver=new SpringBoneSolver();solvers[joint.bone]=solver;resetJoint(solver);}
        else if(reset)resetJoint(solver);
        // velocity is expressed in PMX units/sec (the model is rendered at
        // MODEL_SCALE blocks per unit). The old coefficients treated it as block
        // velocity and injected several times too much energy into every
        // independent strand.  Use a small shared wind response and let the
        // spring's cone/damping provide the visible follow-through.
        float windX=Mth.clamp(velocity.x,-18f,18f),windZ=Mth.clamp(velocity.z,-18f,18f);
        float flutter=(float)Math.sin(age*.11+joint.bone*.13);
        float wind=.025f*(joint.category==1?1.0f:joint.category==2?1.35f:.6f);
        float gravity=joint.category==1?-.7f:joint.category==2?-1.15f:-.45f;
        force.set(-windX*wind+flutter*.18f,gravity,-windZ*wind+flutter*.10f);
        if(cinematic)force.mul(.20f);
        else if(freeFlight)force.mul(.30f);
        if(joint.category>0)force.add(ritualWind*.12f,ritualWind*.85f,ritualWind*.35f);
        float[] radii=noRadii;
        if(joint.category==1){
            world[11].getTranslation(capsules[0]).add(0,.8f,.25f);capsules[1].set(capsules[0]);
            world[8].getTranslation(capsules[2]).add(0,0,.3f);world[9].getTranslation(capsules[3]).add(0,0,.3f);radii=hairRadii;
        }else if(joint.category==2){
            world[98].getTranslation(capsules[0]);world[100].getTranslation(capsules[1]);
            world[102].getTranslation(capsules[2]);world[104].getTranslation(capsules[3]);radii=clothRadii;
        }
        int steps=Math.max(1,(int)Math.ceil(step*120));
        float stiffness=joint.stiffness*(cinematic?(joint.category==2?1.8f:2.6f):1),damping=joint.damping*(cinematic?(joint.category==2?1.6f:2.0f):1);
        float cone=cinematic?Math.min(joint.cone,joint.category==1?.055f:joint.category==2?.030f:.04f):joint.cone;
        if(freeFlight){
            OutsiderFreeFlightSpring attached=freeFlightSolvers[joint.bone];
            if(attached==null){attached=new OutsiderFreeFlightSpring();freeFlightSolvers[joint.bone]=attached;attached.reset(new Vector3f(root).add(direction));}
            else if(reset)attached.reset(new Vector3f(root).add(direction));
            float freeCone=Math.min(cone,joint.category==1?.085f:joint.category==2?.060f:.045f);
            for(int i=0;i<steps;i++)attached.step(root,direction,force,step/steps,stiffness*(joint.category==2?2f:1.7f),damping*(joint.category==2?1.8f:1.5f),freeCone,capsules,radii);
            targetDirection.set(attached.current).sub(root);
        }else{
            for(int i=0;i<steps;i++)solver.step(root,direction,force,step/steps,stiffness,damping,cone,capsules,radii);
            targetDirection.set(solver.current).sub(root);
        }
        Quaternionf offset=rotation.rotationTo(inheritedDirection,targetDirection).normalize();
        if(cachedOffsets[joint.bone]==null)cachedOffsets[joint.bone]=new Quaternionf();
        cachedOffsets[joint.bone].set(offset);
        // Use a separate quaternion: getRotation must not overwrite offset.
        Quaternionf original=matrix.getUnnormalizedRotation(originalRotation);
        offset.mul(original,rotation);
        matrix.translationRotate(root,rotation);
    }
    private void resetJoint(SpringBoneSolver solver){solver.reset(new Vector3f(root).add(direction));}
}
