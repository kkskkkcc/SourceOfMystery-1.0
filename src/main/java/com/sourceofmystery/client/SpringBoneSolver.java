package com.sourceofmystery.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Inertial tail integration, length/cone constraints and capsule collision.
 * Based on the VRMC_springBone reference algorithm; no Minecraft state here.
 */
public final class SpringBoneSolver {
    public final Vector3f current=new Vector3f(), previous=new Vector3f();
    private final Vector3f next=new Vector3f(), direction=new Vector3f(), delta=new Vector3f(), closest=new Vector3f();
    private final Quaternionf correction=new Quaternionf();
    private float lastStep=1/60f;
    public void reset(Vector3f goal){current.set(goal);previous.set(goal);lastStep=1/60f;}
    public void rebase(float yaw,Vector3f shift){current.rotateY(yaw).sub(shift);previous.rotateY(yaw).sub(shift);}
    public Quaternionf step(Vector3f root,Vector3f restDirection,Vector3f force,float dt,
                            float stiffness,float damping,float maxAngle,Vector3f[] capsules,float[] radii){
        float length=restDirection.length();
        if(dt>0){
            next.set(current).sub(previous).mul((float)Math.exp(-damping*dt)*dt/lastStep).add(current);
            delta.set(root).add(restDirection).sub(current).mul(stiffness).add(force);
            next.fma(dt*dt,delta);previous.set(current);lastStep=dt;
            for(int pass=0;pass<4;pass++) {
                constrain(root,restDirection,length,maxAngle);
                for(int i=0;i<radii.length;i++) {
                    Vector3f a=capsules[i*2],b=capsules[i*2+1];delta.set(b).sub(a);
                    float along=Math.max(0,Math.min(1,newDot(next,a,delta)/Math.max(1e-6f,delta.lengthSquared())));
                    closest.set(a).fma(along,delta);direction.set(next).sub(closest);
                    float distance=direction.length();
                    if(distance>1e-5f&&distance<radii[i])next.set(closest).fma(radii[i]/distance,direction);
                }
            }
            constrain(root,restDirection,length,maxAngle);current.set(next);
        }
        // A zero-delta render, a pose change or rebase still needs projection.
        // Otherwise an old tail can temporarily escape the cone without any
        // integration step (notably during pauses and resource reloads).
        next.set(current);constrain(root,restDirection,length,maxAngle);current.set(next);
        direction.set(current).sub(root);
        if(direction.lengthSquared()<1e-8f)return correction.identity();
        return correction.rotationTo(restDirection,direction).normalize();
    }
    private static float newDot(Vector3f point,Vector3f origin,Vector3f direction){
        return (point.x-origin.x)*direction.x+(point.y-origin.y)*direction.y+(point.z-origin.z)*direction.z;
    }
    private void constrain(Vector3f root,Vector3f rest,float length,float angle){
        direction.set(next).sub(root);
        if(direction.lengthSquared()<1e-8f)direction.set(rest);
        direction.normalize();delta.set(rest).normalize();
        float measured=(float)Math.acos(Math.max(-1,Math.min(1,direction.dot(delta))));
        if(measured>angle){correction.rotationTo(delta,direction);new Quaternionf().slerp(correction,angle/measured).transform(delta);direction.set(delta);}
        next.set(root).fma(length,direction);
    }
}
