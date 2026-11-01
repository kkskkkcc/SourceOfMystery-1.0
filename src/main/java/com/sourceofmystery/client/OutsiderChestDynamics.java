package com.sourceofmystery.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bounded soft-tissue inertia on the two existing chest deform roots.
 * Ribcage/shoulders remain authored; the weighted child chains follow these roots.
 */
final class OutsiderChestDynamics {
    private final Vector3f anchor=new Vector3f(),lastAnchor=new Vector3f();
    private final Vector3f velocity=new Vector3f(),lastVelocity=new Vector3f(),acceleration=new Vector3f();
    private final Vector3f[] offset={new Vector3f(),new Vector3f()},speed={new Vector3f(),new Vector3f()};
    private final Vector3f target=new Vector3f(),force=new Vector3f();
    private boolean initialized;

    void step(Matrix4f ribcage,Vector3f travelVelocity,float age,float dt,boolean reset){
        ribcage.getTranslation(anchor);
        if(reset||!initialized){lastAnchor.set(anchor);lastVelocity.set(travelVelocity);initialized=true;for(int i=0;i<2;i++){offset[i].zero();speed[i].zero();}return;}
        if(dt<=0)return;
        velocity.set(anchor).sub(lastAnchor).div(dt).add(travelVelocity);
        acceleration.set(velocity).sub(lastVelocity).div(dt);
        if(acceleration.lengthSquared()>2500)acceleration.normalize().mul(50);
        lastAnchor.set(anchor);lastVelocity.set(velocity);
        // A relaxed 3.8 s respiratory cycle, with soft tissue following the ribs.
        float breath=(float)Math.sin(age*(Math.PI*2/3.8));
        int count=Math.max(1,(int)Math.ceil(dt*120));float h=dt/count;
        for(int side=0;side<2;side++){
            target.set(0,.045f*breath,-.065f*breath);
            float stiffness=100+side*3,damping=17.5f+side*.3f;
            for(int i=0;i<count;i++){
                force.set(target).sub(offset[side]).mul(stiffness).fma(-damping,speed[side]).fma(-.52f,acceleration);
                speed[side].fma(h,force);offset[side].fma(h,speed[side]);
                if(offset[side].lengthSquared()>.17f*.17f){
                    offset[side].normalize().mul(.17f);
                    float outward=speed[side].dot(offset[side]);
                    if(outward>0)speed[side].fma(-outward/offset[side].lengthSquared(),offset[side]);
                }
            }
        }
    }
    void apply(int bone,Vector3f position,Quaternionf rotation){
        int side=bone==18?0:bone==19?1:-1;if(side<0)return;
        Vector3f o=offset[side];
        position.add(o);
        rotation.rotateX(Math.max(-.055f,Math.min(.055f,-o.y*.22f)))
                .rotateZ(Math.max(-.025f,Math.min(.025f,o.x*.12f)));
    }
    float maximumDisplacement(){
        return Math.max(offset[0].length(),offset[1].length())*com.sourceofmystery.entity.OutsiderEntity.MODEL_SCALE;
    }
}
