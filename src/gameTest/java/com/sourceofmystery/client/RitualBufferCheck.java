package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;

/** CPU-only client vertex regression; deliberately performs no OpenGL draws. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="sourceofmystery",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class RitualBufferCheck {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){
        if(!Boolean.getBoolean("sourceofmystery.ritualCheck"))return;
        event.enqueueWork(()->{
            try{main(new String[0]);System.exit(0);}
            catch(Throwable error){error.printStackTrace();System.exit(1);}
        });
    }
    private static final class Buffers implements MultiBufferSource {
        final BufferBuilder builder=new BufferBuilder(65536);
        RenderType current;
        long vertices;
        int batches;
        boolean capture;
        final java.util.List<Object> snapshot=new java.util.ArrayList<>();
        public VertexConsumer getBuffer(RenderType next){
            if(next!=current){flush();builder.begin(next.mode(),next.format());current=next;}
            return builder;
        }
        void flush(){
            if(current==null)return;
            var data=builder.end();var state=data.drawState();
            int count=state.vertexCount(),stride=state.format().getVertexSize();
            var bytes=data.vertexBuffer();
            for(int i=0;i<count;i++)for(int axis=0;axis<3;axis++)
                if(!Float.isFinite(bytes.getFloat(i*stride+axis*4)))throw new AssertionError("Nonfinite position");
            if(capture&&stride==24){
                var name=java.util.regex.Pattern.compile("ritual_v2_[a-z]+\\.png").matcher(current.toString());
                if(name.find()){
                    float[][] rows=new float[count][9];
                    for(int i=0;i<count;i++){
                        int start=i*stride;
                        for(int j=0;j<3;j++)rows[i][j]=bytes.getFloat(start+j*4);
                        for(int j=0;j<4;j++)rows[i][j+3]=Byte.toUnsignedInt(bytes.get(start+12+j))/255f;
                        rows[i][7]=bytes.getFloat(start+16);rows[i][8]=bytes.getFloat(start+20);
                    }
                    snapshot.add(java.util.Map.of("texture",name.group(),"vertices",rows));
                }
            }
            if(count%4!=0)throw new AssertionError("Incomplete quad");
            vertices+=count;batches++;data.release();current=null;
        }
    }
    public static void main(String[] ignored){
        Buffers buffers=new Buffers();PoseStack poses=new PoseStack();
        for(int frame=0;frame<=1830;frame++){
            float t=frame/60f;
            buffers.capture=frame==720;
            if(t>=3)OutsiderRitualRenderer.render(poses,buffers,(t-3)*20,320);
            if(t>=22&&t<27)OutsiderRitualRenderer.renderSky(poses,buffers,t-22);
            if(!buffers.capture)OutsiderRitualRenderer.renderBackHalo(poses,buffers,new Matrix4f(),frame/3f);
            // Exercise transitions to non-textured geometry on the same buffer.
            var vc=buffers.getBuffer(OutsiderRitualRenderer.SOLID);
            EffectGeometry.glowQuad(vc,poses.last().pose(),new float[]{0,0,0},new float[]{1,0,0},
                    new float[]{1,1,0},new float[]{0,1,0},new float[]{.1f,0,0,1},new float[]{.1f,0,0,1});
            buffers.flush();
        }
        if(buffers.vertices<1000000)throw new AssertionError("Ritual emitted too little geometry");
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("../ritual-v2-geometry.json"),new com.google.gson.Gson().toJson(buffers.snapshot));}
        catch(java.io.IOException error){throw new RuntimeException(error);}
        checkHighSkyEffects();
        checkCompatibleVertices();
        checkCastingWind();
        checkSpeechFaces();
        exportAnimation();
        System.out.println("RITUAL_BUFFER_OK frames=1831 batches="+buffers.batches+" vertices="+buffers.vertices);
    }
    private static void checkCompatibleVertices(){
        var raw=new BufferBuilder(4096);raw.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        var vc=OutsiderShaderCompat.complete(raw);
        EffectGeometry.glowQuad(vc,new Matrix4f(),new float[]{0,0,0},new float[]{1,0,0},new float[]{1,1,0},new float[]{0,1,0},
                new float[]{.4f,.05f,.8f,.6f},new float[]{.4f,.05f,.8f,.6f});
        var result=raw.end();if(result.drawState().vertexCount()!=8)throw new AssertionError("Incomplete shader entity effect");result.release();
        raw.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);vc=OutsiderShaderCompat.complete(raw,7,2,14);
        EffectGeometry.voidQuad(vc,new Matrix4f(),new float[]{0,0,0},new float[]{1,0,0},new float[]{1,1,0},new float[]{0,1,0});
        result=raw.end();var data=result.vertexBuffer();
        if(result.drawState().vertexCount()!=8||Byte.toUnsignedInt(data.get(12))!=7)throw new AssertionError("Position-only portal adapter failed");
        result.release();System.out.println("SHADER_COMPAT_VERTICES_OK entity_attributes=true portal_attributes=true");
    }
    private static void checkSpeechFaces(){
        var report=new java.util.ArrayList<Object>();
        for(int voice=0;voice<com.sourceofmystery.entity.OutsiderVoice.IDS.length;voice++){
            float[] previous=null;
            for(int frame=0;frame<=com.sourceofmystery.entity.OutsiderVoice.TICKS[voice]*3+6;frame++){
                float age=frame/3f;float[] weights=new float[12];weights[0]=.37f;
                OutsiderSpeechFace.apply(voice,age,.45f,weights);
                if(weights[0]!=.37f)throw new AssertionError("Speech acting overwrites natural blink");
                for(int channel=0;channel<12;channel++){
                    if(!Float.isFinite(weights[channel])||weights[channel]<0||weights[channel]>1)throw new AssertionError("Invalid speech morph");
                    if(previous!=null&&Math.abs(weights[channel]-previous[channel])>.13f)throw new AssertionError("Abrupt speech morph transition");
                }
                previous=weights;
            }
            float[] sample=new float[12];OutsiderSpeechFace.apply(voice,10,.45f,sample);
            report.add(java.util.Map.of("voice",com.sourceofmystery.entity.OutsiderVoice.IDS[voice],"weights",sample));
        }
        float[] lead=new float[12],shout=new float[12];
        OutsiderSpeechFace.apply(9,20,.45f,lead);OutsiderSpeechFace.apply(9,85,.45f,shout);
        if(shout[9]<lead[9]+.3f||shout[11]<lead[11]+.3f||lead[6]<.15f)throw new AssertionError("Ultimate has no declaration-to-shout acting");
        float[] tail=new float[12];tail[7]=.5f;tail[8]=.2f;
        OutsiderSpeechFace.apply(9,com.sourceofmystery.entity.OutsiderVoice.SPEECH_TICKS[9]+2,.45f,tail);
        if(tail[7]!=0||tail[8]!=0)throw new AssertionError("Reverb tail holds the mouth open");
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("../speech-faces.json"),new com.google.gson.Gson().toJson(report));}
        catch(java.io.IOException error){throw new RuntimeException(error);}
        System.out.println("SPEECH_FACES_OK lines="+com.sourceofmystery.entity.OutsiderVoice.IDS.length+" transitions=smooth blink=preserved ultimate=ethereal_to_shout tail_mouth=closed");
    }
    private static void checkCastingWind(){
        for(int fps:new int[]{15,30,60,144}){
          SpringBoneSolver[] solvers={new SpringBoneSolver(),new SpringBoneSolver(),new SpringBoneSolver()};
          for(int frame=0;frame<=fps*24;frame++){
            float seconds=frame/(float)fps,strength=com.sourceofmystery.entity.OutsiderRitual.chargeEnvelope(seconds);
            if(strength<0||strength>1||!Float.isFinite(strength))throw new AssertionError("Invalid charge envelope");
            for(int category=0;category<3;category++){
                var root=new org.joml.Vector3f(1,12,.8f);var hips=new org.joml.Vector3f(0,11,0);
                var direction=new org.joml.Vector3f(0,-.55f,.045f);var rest=new org.joml.Vector3f(direction);
                OutsiderCastingWind.bend(direction,root,hips,category,seconds,strength);
                if(!direction.isFinite()||Math.abs(rest.length()-direction.length())>.00001)throw new AssertionError("Casting wind stretches a strand");
                float angle=direction.angle(rest);
                if(angle>.45f)throw new AssertionError("Casting wind goal exceeds its bounded deflection");
                if(category==0&&direction.distance(rest)>.000001)throw new AssertionError("Casting wind displaces facial bangs");
                if(strength>.99f&&category>0&&angle<.12f)throw new AssertionError("Casting wind is not visibly moving the garment/hair");
                if(frame==0)solvers[category].reset(new org.joml.Vector3f(root).add(direction));
                float dt=frame%71==0?0:1f/fps;int steps=Math.max(1,(int)Math.ceil(dt*120));
                for(int n=0;n<steps;n++)solvers[category].step(root,direction,new org.joml.Vector3f(.12f,-.5f,.35f),dt/steps,95,24,.18f,new org.joml.Vector3f[0],new float[0]);
                var solved=new org.joml.Vector3f(solvers[category].current).sub(root);
                if(!solved.isFinite()||solved.angle(direction)>.1801f||Math.abs(solved.length()-rest.length())>.00003)throw new AssertionError("Unstable simulated casting wind at fps="+fps);
            }
          }
        }
        for(long seed:new long[]{0,17,9881,-77}){
            float[] end=OutsiderChargeWind.point(seed,1);
            if(Math.abs(end[0])+Math.abs(end[1])+Math.abs(end[2])>.000001)throw new AssertionError("Charge ribbon misses staff core");
            float[] start=OutsiderChargeWind.point(seed,0);
            double radius=Math.hypot(start[0],start[2]);if(radius<35.99||radius>148.01)throw new AssertionError("Charge ribbon source radius is wrong");
        }
        Buffers storm=new Buffers();
        for(int frame=0;frame<120;frame++){
            OutsiderChargeWind.draw(117,7+frame/60f,1,new net.minecraft.world.phys.Vec3(0,4,20),new Matrix4f(),storm);storm.flush();
        }
        if(storm.vertices<100000||storm.vertices>500000)throw new AssertionError("Charge ribbon budget is outside its expected bound");
        System.out.println("CAST_WIND_OK fps=15,30,60,144 absolute_goals=true streams=48 radius=36..148 endpoints=staff");
    }
    /** No client world/compiled chunks: the world-effect pass must still emit geometry. */
    private static final class HighSpell extends com.sourceofmystery.entity.OutsiderSpell {
        private final int visualKind;
        HighSpell(int kind){super(com.sourceofmystery.entity.ModEntities.OUTSIDER_SPELL.get(),null);visualKind=kind;setPos(0,255,0);}
        @Override public int kind(){return visualKind;}
        @Override public float age(float partial){return 40;}
        @Override public net.minecraft.world.phys.Vec3 trajectoryPosition(float partial){return position();}
    }
    @SuppressWarnings("unchecked")
    private static void checkHighSkyEffects(){
        PoseStack poses=new PoseStack();var seal=new HighSpell(com.sourceofmystery.entity.OutsiderSpell.SKY_SEAL);
        for(double cameraY:new double[]{-63,-60,64,240,320}){
            Buffers output=new Buffers();output.capture=true;
            OutsiderHighSpellEffects.renderTracked(java.util.List.of(seal),new net.minecraft.world.phys.Vec3(0,cameraY,0),0,poses,output);
            output.flush();
            if(output.vertices!=768||output.snapshot.size()!=1)throw new AssertionError("Missing sky seal at camera Y="+cameraY);
            float[][] rows=(float[][])((java.util.Map<String,Object>)output.snapshot.get(0)).get("vertices");
            float xmin=Float.POSITIVE_INFINITY,xmax=-xmin;
            for(float[] row:rows){
                if(Math.abs(row[1]-(255-cameraY))>.001)throw new AssertionError("Wrong sky altitude");
                xmin=Math.min(xmin,row[0]);xmax=Math.max(xmax,row[0]);
            }
            if(Math.abs(xmax-xmin-400)>.2)throw new AssertionError("Sky radius is not two hundred blocks");
            if(!poses.last().pose().equals(new Matrix4f()))throw new AssertionError("World-effect transform leaked");
        }
        var meteor=new HighSpell(com.sourceofmystery.entity.OutsiderSpell.METEOR);Buffers output=new Buffers();
        OutsiderHighSpellEffects.renderTracked(java.util.List.of(meteor),new net.minecraft.world.phys.Vec3(0,-60,0),0,poses,output);output.flush();
        if(output.vertices<1000)throw new AssertionError("High meteor still depends on compiled sections");
        var bolt=new HighSpell(com.sourceofmystery.entity.OutsiderSpell.BOLT);Buffers excluded=new Buffers();
        OutsiderHighSpellEffects.renderTracked(java.util.List.of(bolt),net.minecraft.world.phys.Vec3.ZERO,0,poses,excluded);
        if(excluded.vertices!=0||excluded.current!=null)throw new AssertionError("Barrage drawn in both passes");
        System.out.println("SKY_WORLD_PASS_OK cameras=-63,-60,64,240,320 sky_y=255 radius=200 diameter=400 meteor=true compiled_sections=none");
    }
    @SuppressWarnings("unchecked")
    private static void exportAnimation(){
        // Actual 12–18s array operation, at 30fps. Capture texture batches only:
        // omit the beam so the review shows the engraved mechanism clearly.
        try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(
                java.nio.file.Files.newOutputStream(java.nio.file.Path.of("../ritual-v2-operation.bin.gz"))))){
            out.writeInt(0x4F525631);out.writeInt(180);out.writeInt(30);
            Buffers animation=new Buffers();PoseStack poses=new PoseStack();animation.capture=true;
            for(int frame=0;frame<180;frame++){
                float t=12+frame/30f;animation.snapshot.clear();
                OutsiderRitualRenderer.render(poses,animation,(t-3)*20,320);animation.flush();
                out.writeFloat(t);out.writeInt(animation.snapshot.size());
                for(Object entry:animation.snapshot){
                    var batch=(java.util.Map<String,Object>)entry;
                    out.writeUTF((String)batch.get("texture"));
                    float[][] rows=(float[][])batch.get("vertices");out.writeInt(rows.length);
                    for(float[] row:rows)for(float value:row)out.writeFloat(value);
                }
            }
        }catch(java.io.IOException error){throw new RuntimeException(error);}
        System.out.println("RITUAL_ANIMATION_EXPORTED frames=180 fps=30 seconds=12..18");
    }
}
