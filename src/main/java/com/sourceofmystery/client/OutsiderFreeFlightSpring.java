package com.sourceofmystery.client;

import org.joml.Vector3f;

/** Root-attached secondary motion used only during the boss's ordinary AIR state.
 * The cinematic and combat spring solver retain their existing behavior. */
public final class OutsiderFreeFlightSpring {
    private final SpringBoneSolver solver=new SpringBoneSolver();
    public final Vector3f current=solver.current,previous=solver.previous;
    private final Vector3f anchor=new Vector3f(),transport=new Vector3f(),goal=new Vector3f(),axis=new Vector3f(),nearest=new Vector3f(),delta=new Vector3f();
    private float[] reachableRadii=new float[0];
    private boolean anchored;
    public void reset(Vector3f target){solver.reset(target);anchored=false;}
    public void rebase(float yaw,Vector3f shift){solver.rebase(yaw,shift);if(anchored)anchor.rotateY(yaw).sub(shift);}
    public void step(Vector3f root,Vector3f rest,Vector3f force,float dt,float stiffness,float damping,float cone,Vector3f[] capsules,float[] radii){
        // Transport the attachment with the body. Treating every tiny control
        // bone as a loose world-space particle made regular flight translation
        // repeatedly pull it across the entire angular cone.
        if(anchored){transport.set(root).sub(anchor);current.add(transport);previous.add(transport);}
        anchor.set(root);anchored=true;goal.set(root).add(rest);
        if(reachableRadii.length!=radii.length)reachableRadii=new float[radii.length];
        for(int i=0;i<radii.length;i++){
            Vector3f a=capsules[i*2],b=capsules[i*2+1];axis.set(b).sub(a);
            delta.set(goal).sub(a);float along=Math.max(0,Math.min(1,delta.dot(axis)/Math.max(1e-6f,axis.lengthSquared())));
            nearest.set(a).fma(along,axis);
            // Some PMX control points intentionally start inside the body.
            // Preserve their authored clearance instead of forcing impossible
            // collision and cone constraints against one another every frame.
            reachableRadii[i]=Math.min(radii[i],Math.max(0,goal.distance(nearest)-.005f));
        }
        solver.step(root,rest,force,dt,stiffness,damping,cone,capsules,reachableRadii);
        if(dt>0){
            delta.set(current).sub(previous);float distance=delta.length(),limit=rest.length()*1.25f*dt;
            if(distance>limit)previous.set(current).fma(-limit/distance,delta);
        }
    }
}
