package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

/** Original PMX triangle mesh with locomotion baked against its weighted rig. */
public class OutsiderRenderer extends EntityRenderer<OutsiderEntity> {
    private static final ResourceLocation MESH = new ResourceLocation(SourceOfMystery.MOD_ID,
            "meshes/entity/outsider.bin");
    private static final ResourceLocation FACIAL = new ResourceLocation(SourceOfMystery.MOD_ID,
            "meshes/entity/outsider_face.bin");
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/outsider/face.png"),
            new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/outsider/body_map2.png"),
            new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/outsider/body_map1.png"),
            new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/outsider/staff.png"),
            new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/outsider/white.png"),
    };
    private static final RenderType[] TRIANGLE_TYPES = {
            triangleMaterial(TEXTURES[0],false), triangleMaterial(TEXTURES[1],false), triangleMaterial(TEXTURES[2],false),
            triangleMaterial(TEXTURES[3],false), triangleMaterial(TEXTURES[4],false)
    };
    private static final RenderType EYE_OVERLAY = triangleMaterial(TEXTURES[0],true);
    // The PMX vertex bounds are 23.8 native units high; this gives the
    // finished boss a roughly 3.5 block silhouette, matching a Wither.
    private static final float MODEL_SCALE = OutsiderEntity.MODEL_SCALE;
    private static final int[] EYELID_MORPHS = {2,4,5,9,10};
    // Renderers are recreated on resource reload; avoid retaining stale assets
    // in a class-level singleton after F3+T.
    private final Mesh data = Mesh.load();
    private final OutsiderVertexBatch batch=new OutsiderVertexBatch();
    private final OutsiderSkyLightning skyLightning;

    public OutsiderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.skyLightning=new OutsiderSkyLightning(context);
        shadowRadius = 0.78F;
    }

    /** Native triangles avoid the extra degenerate vertex used by quad buffers. */
    private static RenderType triangleMaterial(ResourceLocation texture, boolean translucent) {
        return RenderType.create("sourceofmystery_outsider_" + texture.getPath(),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(OutsiderShaders::model))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(translucent ? States.ALPHA : States.NO_TRANSPARENCY_STATE)
                        .setDepthTestState(States.LEQUAL_DEPTH)
                        .setCullState(States.NO_CULL_STATE)
                        .setLightmapState(States.LIGHTMAP_STATE)
                        .setOverlayState(States.OVERLAY_STATE)
                        .setWriteMaskState(States.COLOR_DEPTH)
                        .createCompositeState(true));
    }

    /** Protected vanilla render states are exposed through a tiny subclass. */
    private static final class States extends RenderStateShard {
        static final ShaderStateShard ENTITY_CUTOUT_SHADER = RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER;
        static final ShaderStateShard ENTITY_TRANSLUCENT_SHADER = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard ALPHA = TRANSLUCENT_TRANSPARENCY;
        static final TransparencyStateShard NO_TRANSPARENCY_STATE = NO_TRANSPARENCY;
        static final DepthTestStateShard LEQUAL_DEPTH = LEQUAL_DEPTH_TEST;
        static final CullStateShard NO_CULL_STATE = NO_CULL;
        static final LightmapStateShard LIGHTMAP_STATE = LIGHTMAP;
        static final OverlayStateShard OVERLAY_STATE = OVERLAY;
        static final WriteMaskStateShard COLOR_DEPTH = COLOR_DEPTH_WRITE;

        private States() {
            super("outsider_states", () -> {}, () -> {});
        }
    }

    @Override
    public ResourceLocation getTextureLocation(OutsiderEntity entity) {
        return TEXTURES[0];
    }

    @Override
    public void render(OutsiderEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float age = entity.tickCount + partialTick;
        float bodyYaw = entity.visualBodyYaw(partialTick);
        float introSeconds = (entity.offeringIntroTicks()+partialTick) / 20.0f;
        boolean intro=entity.isOfferingIntro(),crossing=intro&&introSeconds<22;
        MeshPose animated = data.pose(entity, partialTick);
        if (intro && introSeconds < OfferingPortalRitual.EMERGE_START_TICK / 20.0f) {
            // The entity is already present on the server so its physics state
            // is continuous, but it stays inside the rift until the camera's
            // rupture shot has finished.
            poseStack.popPose();
            return;
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        float flinch=entity.flinchAmount(partialTick);
        if(flinch>0){
            double pivot=entity.getBbHeight()*OutsiderEntity.FLINCH_PIVOT;
            poseStack.translate(0,pivot,0);
            // The mesh faces local -Z; positive X rotation tilts its top backward.
            poseStack.mulPose(Axis.XP.rotationDegrees(OutsiderEntity.FLINCH_LEAN*flinch));
            poseStack.translate(0,-pivot,0);
        }
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        batch.prepare(animated.positions,animated.normals,matrix,normal);
        float clipZ=intro?OfferingPortalRitual.entranceDistance(introSeconds)/MODEL_SCALE:Float.POSITIVE_INFINITY;
        clipEdgeCount=0;
        boolean shaders=OutsiderShaderCompat.active();
        for (Group group : data.groups) {
            RenderType material=shaders?((group.flags&1)!=0?RenderType.entityTranslucent(TEXTURES[group.textureSlot]):
                    RenderType.entityCutoutNoCull(TEXTURES[group.textureSlot])):
                    ((group.flags&1)!=0?EYE_OVERLAY:TRIANGLE_TYPES[group.textureSlot]);
            VertexConsumer consumer = batch.begin(bufferSource.getBuffer(material));
            int light = (group.flags&2)!=0 || intro ? 15728880 : packedLight;
            for (int i = group.start; i < group.start + group.count; i += 3) {
                int bound=data.boneIndices[data.indices[i]*4];
                if(bound>=554&&bound<=556)continue; // Replaced by the HD reference halo below.
                if(crossing){
                    clippedTriangle(consumer,matrix,normal,animated,data.indices[i],data.indices[i+1],data.indices[i+2],clipZ,light,group,shaders);
                    continue;
                }
                vertex(consumer, matrix, normal, animated, data.indices[i], light, group);
                vertex(consumer, matrix, normal, animated, data.indices[i + 1], light, group);
                vertex(consumer, matrix, normal, animated, data.indices[i + 2], light, group);
                // Vanilla entity types use quads. The repeated final corner
                // preserves the original PMX triangle and all skin/UV data.
                if(shaders)vertex(consumer,matrix,normal,animated,data.indices[i+2],light,group);
            }
            batch.flush();
        }
        if(crossing)renderCrossingSeam(poseStack,bufferSource,age);
        if(!entity.isOfferingIntro()||introSeconds>=20)OutsiderRitualRenderer.renderBackHalo(poseStack,bufferSource,data.world[1],age,entity.isOfferingIntro()?Mth.clamp((introSeconds-20)/1.4f,0,1):1);
        if((entity.isOfferingIntro()&&introSeconds>17)||(!entity.isOfferingIntro()&&entity.secondPhase()))introEyes(poseStack,bufferSource,age);
        if (entity.isOfferingIntro() && introSeconds >= OfferingPortalRitual.EMERGE_END_TICK / 20.0f
                && introSeconds < OfferingPortalRitual.GLANCE_END_TICK / 20.0f) {
            // A compact violet sigil at the second wrist sells the caster's
            // left-hand dismissal gesture while using the same live skin
            // matrices as the staff and cloth.
            Vector3f palm=data.world[45].transformPosition(0,0,0,new Vector3f());
            poseStack.pushPose();
            poseStack.translate(palm.x,palm.y,palm.z);
            float snap=OfferingPortalRitual.SNAP_TICK/20f;
            float a=Mth.clamp((introSeconds-snap)/.05f,0,1)*Mth.clamp((snap+.48f-introSeconds)/.38f,0,1);
            VertexConsumer glow=OutsiderShaderCompat.glow(bufferSource,RenderType.lightning());
            for(int k=0;k<32;k++){
                double u=k*Math.PI/16+age*.015,v=(k+1)*Math.PI/16+age*.015;
                float r=1.2f*a;
                EffectGeometry.glowQuad(glow,poseStack.last().pose(),new float[]{(float)Math.cos(u)*r,(float)Math.sin(u)*r,0},new float[]{(float)Math.cos(v)*r,(float)Math.sin(v)*r,0},
                        new float[]{(float)Math.cos(v)*(r+.12f),(float)Math.sin(v)*(r+.12f),0},new float[]{(float)Math.cos(u)*(r+.12f),(float)Math.sin(u)*(r+.12f),0},new float[]{.22f,.6f,1,a*.8f},new float[]{.02f,.2f,.7f,0});
            }
            poseStack.popPose();
        }
        if(entity.combatState()==OutsiderEntity.GRASP){
            float t=entity.animationSeconds(partialTick);
            float amount=Mth.clamp((t-.7f)/.7f,0,1)*Mth.clamp((5.8f-t)/.3f,0,1);
            if(amount>0){
                Vector3f palm=data.world[35].transformPosition(0,0,-1,new Vector3f());
                poseStack.pushPose();poseStack.translate(palm.x,palm.y,palm.z);
                OutsiderSpellRenderer.orb(poseStack,bufferSource,2.1f*amount,age);poseStack.popPose();
            }
        }
        if(entity.combatState()==OutsiderEntity.METEOR){
            float t=entity.animationSeconds(partialTick);
            if(t>=com.sourceofmystery.entity.OutsiderRitual.STAFF_END&&t<com.sourceofmystery.entity.OutsiderRitual.END){
                // Same skin matrix and crystal reference as the staff vertices.
                // No independent spell entity, delayed pose cache or second skin pass.
                Vector3f core=data.skin[557].transformPosition(-2.77f,21.225f,.36f,new Vector3f());
                poseStack.pushPose();poseStack.translate(core.x,core.y,core.z);
                poseStack.scale(1/MODEL_SCALE,1/MODEL_SCALE,1/MODEL_SCALE);
                OutsiderRitualRenderer.render(poseStack,bufferSource,(t-com.sourceofmystery.entity.OutsiderRitual.STAFF_END)*20,
                        Math.max(256,entity.level().getMaxBuildHeight()-(float)entity.getY()+64));
                skyLightning.render(entity,partialTick,t,core,poseStack,bufferSource);
                OutsiderChargeWind.render(entity,partialTick,t,core,poseStack,bufferSource);
                poseStack.popPose();
            }
        }
        if(entity.combatState()==OutsiderEntity.METEOR_PREP||
                (entity.combatState()==OutsiderEntity.METEOR&&entity.animationSeconds(partialTick)<com.sourceofmystery.entity.OutsiderRitual.STAFF_END)){
            Vector3f core=data.skin[557].transformPosition(-2.77f,21.225f,.36f,new Vector3f());
            poseStack.pushPose();poseStack.translate(core.x,core.y,core.z);
            poseStack.scale(1/MODEL_SCALE,1/MODEL_SCALE,1/MODEL_SCALE);
            skyLightning.render(entity,partialTick,entity.stateAge(partialTick)/20,core,poseStack,bufferSource);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
    @Override public boolean shouldRender(OutsiderEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        if(entity.isOfferingIntro())return true;
        // Ritual planes can remain visible while the character is off screen.
        if(entity.combatState()==OutsiderEntity.METEOR)return entity.distanceToSqr(x,y,z)<384*384;
        return super.shouldRender(entity,frustum,x,y,z);
    }

    private void introEyes(PoseStack poses,MultiBufferSource buffers,float age){
        // Reuse the deformed iris surface and UVs, so eyelids and gaze shape the glow.
        VertexConsumer glow=batch.begin(buffers.getBuffer(RenderType.eyes(TEXTURES[0])));
        Group iris=data.groups[8];
        Group tint=new Group(iris.textureSlot,iris.start,iris.count,.82f,.58f,1,.72f,iris.flags);
        MeshPose mesh=new MeshPose(data.outPositions,data.outNormals,data.uvs);
        for(int i=iris.start;i<iris.start+iris.count;i+=3){
            vertex(glow,poses.last().pose(),poses.last().normal(),mesh,data.indices[i],15728880,tint);
            vertex(glow,poses.last().pose(),poses.last().normal(),mesh,data.indices[i+1],15728880,tint);
            vertex(glow,poses.last().pose(),poses.last().normal(),mesh,data.indices[i+2],15728880,tint);
            vertex(glow,poses.last().pose(),poses.last().normal(),mesh,data.indices[i+2],15728880,tint);
        }
        batch.flush();
    }
    private final float[][] clipInput=new float[3][8],clipOutput=new float[4][8];
    private final float[] clipEdges=new float[18000];
    private int clipEdgeCount;
    private void renderCrossingSeam(PoseStack poses,MultiBufferSource buffers,float age){
        VertexConsumer light=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        Matrix4f matrix=poses.last().pose();
        for(int i=0;i+5<clipEdgeCount;i+=6){
            float x=clipEdges[i],y=clipEdges[i+1],z=clipEdges[i+2]-.015f;
            float a=clipEdges[i+3],b=clipEdges[i+4];float dx=a-x,dy=b-y,len=(float)Math.sqrt(dx*dx+dy*dy);
            if(len<.0001f)continue;float width=.065f+.025f*Mth.sin(age*.2f+i);
            float ox=-dy/len*width,oy=dx/len*width;
            EffectGeometry.glowQuad(light,matrix,new float[]{x-ox,y-oy,z},new float[]{a-ox,b-oy,z},new float[]{a+ox,b+oy,z},new float[]{x+ox,y+oy,z},
                    new float[]{.22f,.57f,.96f,.72f},new float[]{.025f,.18f,.55f,.15f});
            if(i%90==0){float phase=(age*.03f+i*.017f)%1,tail=.6f*phase;
                EffectGeometry.glowQuad(light,matrix,new float[]{x-.035f,y,z-tail},new float[]{x+.035f,y,z-tail},new float[]{x+.02f,y+.22f,z-tail-.16f},new float[]{x-.02f,y+.22f,z-tail-.16f},
                        new float[]{.16f,.5f,.96f,(1-phase)*.55f},new float[]{.2f,.02f,.45f,0});}
        }
    }
    /** Clip the skinned surface against the actual vertical doorway, preserving UVs and normals. */
    private void clippedTriangle(VertexConsumer vc,Matrix4f m,Matrix3f normal,MeshPose mesh,int a,int b,int c,float z,int light,Group group,boolean quads){
        int inside=0;
        for(int i=0;i<3;i++)if(mesh.positions[(i==0?a:i==1?b:c)*3+2]<=z)inside++;
        if(inside==0)return;
        if(inside==3){vertex(vc,m,normal,mesh,a,light,group);vertex(vc,m,normal,mesh,b,light,group);vertex(vc,m,normal,mesh,c,light,group);if(quads)vertex(vc,m,normal,mesh,c,light,group);return;}
        for(int i=0;i<3;i++){int id=i==0?a:i==1?b:c;for(int j=0;j<3;j++){clipInput[i][j]=mesh.positions[id*3+j];clipInput[i][j+3]=mesh.normals[id*3+j];}clipInput[i][6]=mesh.uvs[id*2];clipInput[i][7]=mesh.uvs[id*2+1];}
        int count=0;
        boolean record=clipEdgeCount+6<=clipEdges.length;
        for(int i=0;i<3;i++){
            float[] p=clipInput[i],q=clipInput[(i+1)%3];boolean pi=p[2]<=z,qi=q[2]<=z;
            if(pi)System.arraycopy(p,0,clipOutput[count++],0,8);
            if(pi!=qi){float f=(z-p[2])/(q[2]-p[2]);for(int j=0;j<8;j++)clipOutput[count][j]=Mth.lerp(f,p[j],q[j]);
                if(record)for(int j=0;j<3;j++)clipEdges[clipEdgeCount++]=clipOutput[count][j];count++;}
        }
        for(int i=1;i<count-1;i++){clipVertex(vc,m,normal,clipOutput[0],light,group);clipVertex(vc,m,normal,clipOutput[i],light,group);clipVertex(vc,m,normal,clipOutput[i+1],light,group);if(quads)clipVertex(vc,m,normal,clipOutput[i+1],light,group);}
    }
    private static void clipVertex(VertexConsumer vc,Matrix4f m,Matrix3f n,float[] p,int light,Group g){
        vc.vertex(m,p[0],p[1],p[2]).color(g.r,g.g,g.b,g.a).uv(p[6],p[7]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,p[3],p[4],p[5]).endVertex();
    }
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               MeshPose mesh, int index, int packedLight, Group group) {
        if(consumer instanceof OutsiderVertexBatch fast){fast.indexed(index,mesh.uvs,group.packedColor,packedLight);return;}
        int p = index * 3;
        int uv = index * 2;
        float x=mesh.positions[p],y=mesh.positions[p+1],z=mesh.positions[p+2];
        float nx=mesh.normals[p],ny=mesh.normals[p+1],nz=mesh.normals[p+2];
        // The packed NEW_ENTITY entry point uses BufferBuilder/Embeddium's bulk path.
        consumer.vertex(matrix.m00()*x+matrix.m10()*y+matrix.m20()*z+matrix.m30(),
                matrix.m01()*x+matrix.m11()*y+matrix.m21()*z+matrix.m31(),
                matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32(),
                group.r,group.g,group.b,group.a,mesh.uvs[uv],mesh.uvs[uv+1],
                OverlayTexture.NO_OVERLAY,packedLight,
                normal.m00()*nx+normal.m10()*ny+normal.m20()*nz,
                normal.m01()*nx+normal.m11()*ny+normal.m21()*nz,
                normal.m02()*nx+normal.m12()*ny+normal.m22()*nz);
    }

    private static final class Group {
        final int textureSlot;
        final int start;
        final int count;
        final float r,g,b,a;
        final int flags;
        final int packedColor;

        Group(int textureSlot, int start, int count, float r, float g, float b, float a, int flags) {
            this.textureSlot = textureSlot;
            this.start = start;
            this.count = count;
            this.r=r;this.g=g;this.b=b;this.a=a;this.flags=flags;
            this.packedColor=((int)(r*255)&255)|(((int)(g*255)&255)<<8)|(((int)(b*255)&255)<<16)|(((int)(a*255)&255)<<24);
        }
    }

    private static final class Mesh {
        final float[] positions;
        final float[] normals;
        final float[] uvs;
        final int[] indices;
        final Group[] groups;
        final int[] boneIndices;
        final float[] boneWeights;
        final int[] boneParents;
        final float[] bonePositions;
        final int[] controls;
        final Map<String,OutsiderMotion> motions = new HashMap<>();
        final Map<String,OutsiderExpressions> expressions = new HashMap<>();
        final float[] faceWeights = new float[OutsiderExpressions.CHANNELS];
        final OutsiderVoiceMouth voiceMouth=new OutsiderVoiceMouth();
        final Map<OutsiderEntity,BlendState> blends = new WeakHashMap<>();
        final FacialMorphs facial;
        final OutsiderDynamics.Joint[] springJoints;
        final Matrix4f[] skin;
        final Matrix4f[] world;
        final Matrix4f[] authoredWorld;
        final float[] outPositions;
        final float[] outNormals;
        final Vector3f idlePosition = new Vector3f();
        final Vector3f walkPosition = new Vector3f();
        final Quaternionf idleRotation = new Quaternionf();
        final Quaternionf walkRotation = new Quaternionf();
        OutsiderEntity cachedEntity;
        long cachedFrame=Long.MIN_VALUE;
        float cachedAge=Float.NaN;
        MeshPose cachedPose;

        private Mesh(float[] positions, float[] normals, float[] uvs, int[] indices, Group[] groups,
                     int[] boneIndices, float[] boneWeights, int[] boneParents,
                     float[] bonePositions, int[] controls) {
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.indices = indices;
            this.groups = groups;
            this.boneIndices = boneIndices;
            this.boneWeights = boneWeights;
            this.boneParents = boneParents;
            this.bonePositions = bonePositions;
            this.controls = controls;
            for(String name:new String[]{"flight_idle","flight_move","descend","landing","grounded_idle","takeoff","dark_grasp","dark_silence","annihilation","offering_intro"}) {
                motions.put(name,OutsiderMotion.load(name,boneParents.length));
                expressions.put(name,OutsiderExpressions.load(name));
            }
            facial = FacialMorphs.load();
            springJoints=OutsiderDynamics.load(boneParents.length);
            skin = new Matrix4f[boneParents.length];
            world = new Matrix4f[boneParents.length];
            authoredWorld = new Matrix4f[boneParents.length];
            for (int i = 0; i < skin.length; i++) {
                skin[i] = new Matrix4f();
                world[i] = new Matrix4f();
                authoredWorld[i] = new Matrix4f();
                if (boneParents[i] >= i) throw new IllegalStateException("Unordered PMX rig");
            }
            outPositions = new float[positions.length];
            outNormals = new float[normals.length];
        }

        static Mesh load() {
            try {
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MESH);
                if (resource.isEmpty()) throw new IOException("Missing " + MESH);
                try (InputStream input = resource.get().open(); DataInputStream stream = new DataInputStream(input)) {
                    int magic = readInt(stream);
                    int version = readInt(stream);
                    if (magic != 0x4F555431 || version != 4) throw new IOException("Unsupported outsider mesh");
                    int vertexCount = readInt(stream);
                    int indexCount = readInt(stream);
                    int groupCount = readInt(stream);
                    float[] positions = new float[vertexCount * 3];
                    float[] normals = new float[vertexCount * 3];
                    float[] uvs = new float[vertexCount * 2];
                    int[] boneIndices = new int[vertexCount * 4];
                    float[] boneWeights = new float[vertexCount * 4];
                    for (int i = 0; i < vertexCount; i++) {
                        int p = i * 3;
                        positions[p] = readFloat(stream);
                        positions[p + 1] = readFloat(stream);
                        positions[p + 2] = readFloat(stream);
                        normals[p] = readFloat(stream);
                        normals[p + 1] = readFloat(stream);
                        normals[p + 2] = readFloat(stream);
                        int uv = i * 2;
                        uvs[uv] = readFloat(stream);
                        uvs[uv + 1] = readFloat(stream);
                        int influence = i * 4;
                        for (int j = 0; j < 4; j++) boneIndices[influence + j] = readInt(stream);
                        for (int j = 0; j < 4; j++) boneWeights[influence + j] = readFloat(stream);
                    }
                    Group[] groups = new Group[groupCount];
                    for (int i = 0; i < groupCount; i++) {
                        int material = readInt(stream);
                        int start = readInt(stream);
                        int count = readInt(stream);
                        groups[i] = new Group(material, start, count,readFloat(stream),readFloat(stream),readFloat(stream),readFloat(stream),readInt(stream));
                    }
                    int boneCount = readInt(stream);
                    int[] boneParents = new int[boneCount];
                    float[] bonePositions = new float[boneCount * 3];
                    for (int i = 0; i < boneCount; i++) {
                        boneParents[i] = readInt(stream);
                        int p = i * 3;
                        bonePositions[p] = readFloat(stream);
                        bonePositions[p + 1] = readFloat(stream);
                        bonePositions[p + 2] = readFloat(stream);
                    }
                    int controlCount = readInt(stream);
                    int[] controls = new int[controlCount];
                    for (int i = 0; i < controlCount; i++) controls[i] = readInt(stream);
                    int[] indices = new int[indexCount];
                    for (int i = 0; i < indexCount; i++) indices[i] = readInt(stream);
                    return new Mesh(positions, normals, uvs, indices, groups,
                            boneIndices, boneWeights, boneParents, bonePositions, controls);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load the Outsider mesh", e);
            }
        }

        private static int readInt(DataInputStream stream) throws IOException {
            return Integer.reverseBytes(stream.readInt());
        }

        private static float readFloat(DataInputStream stream) throws IOException {
            return Float.intBitsToFloat(readInt(stream));
        }

        private static final class BlendState {
            String clip="";float start;
            final float[] last,from;
            final float[] lastFace=new float[OutsiderExpressions.CHANNELS],fromFace=new float[OutsiderExpressions.CHANNELS];
            final OutsiderDynamics dynamics;
            BlendState(int bones){last=new float[bones*7];from=new float[bones*7];dynamics=new OutsiderDynamics(bones);}
        }
        MeshPose pose(OutsiderEntity entity, float partial) {
            if(entity.isOfferingIntro())partial=Minecraft.getInstance().getFrameTime();
            long frame=OutsiderRenderClock.frame();
            float poseAge=entity.tickCount+partial;
            if(entity.isOfferingIntro()&&cachedEntity==entity&&cachedFrame==frame&&cachedAge==poseAge&&cachedPose!=null)return cachedPose;
            float blink=entity.blinkAmount(partial);
            float age=entity.tickCount+partial;
            String clip=entity.animationName();
            OutsiderMotion motion=motions.getOrDefault(clip,motions.get("grounded_idle"));
            BlendState state=blends.computeIfAbsent(entity,e->new BlendState(skin.length));
            if(!state.clip.equals(clip)){
                state.start=state.clip.isEmpty()?age-6:age;
                System.arraycopy(state.last,0,state.from,0,state.last.length);state.clip=clip;
                System.arraycopy(state.lastFace,0,state.fromFace,0,state.lastFace.length);
            }
            float blend=Mth.clamp((age-state.start)/6,0,1);blend=blend*blend*(3-2*blend);
            expressions.getOrDefault(clip,expressions.get("grounded_idle"))
                    .sample(entity.animationSeconds(partial),entity.animationLoops(),faceWeights);
            for(int i=0;i<faceWeights.length;i++) {
                faceWeights[i]=Mth.lerp(blend,state.fromFace[i],faceWeights[i]);
                state.lastFace[i]=faceWeights[i];
            }
            faceWeights[0]=Math.max(faceWeights[0],blink);
            voiceMouth.apply(entity,partial,faceWeights);
            // Apply once, after both action and speech acting, so every eye
            // expression yields to natural/impact blinks without double damping.
            for(int i:EYELID_MORPHS)faceWeights[i]*=1-faceWeights[0];
            boolean visible=!entity.isOfferingIntro()||(entity.offeringIntroTicks()+partial)>=OfferingPortalRitual.EMERGE_START_TICK;
            if(visible)facial.mix(faceWeights);
            state.dynamics.begin(entity,partial);
            for (int bone = 0; bone < skin.length; bone++) {
                motion.sample(bone,entity.animationSeconds(partial),entity.animationLoops(),idlePosition,idleRotation);
                int local=bone*7;
                if(blend<1){
                    walkPosition.set(state.from[local],state.from[local+1],state.from[local+2]);
                    walkRotation.set(state.from[local+3],state.from[local+4],state.from[local+5],state.from[local+6]);
                    idlePosition.set(walkPosition.lerp(idlePosition,blend));
                    idleRotation.set(walkRotation.slerp(idleRotation,blend)).normalize();
                }
                if(bone==35||bone==65){
                    // Anatomy is relative to the wrist's rest local frame;
                    // a per-render-frame velocity cap was FPS dependent and
                    // preserved already twisted poses across clip transitions.
                    Quaternionf neutral=new Quaternionf();
                    motions.get("grounded_idle").sample(bone,0,false,walkPosition,neutral);
                    Quaternionf delta=new Quaternionf(neutral).conjugate().mul(idleRotation).normalize();
                    float angle=(float)(2*Math.acos(Mth.clamp(Math.abs(delta.w),-1,1)));
                    float limit=entity.combatState()==OutsiderEntity.SILENCE?.20f:.52f;
                    if(angle>limit)idleRotation.set(neutral).slerp(new Quaternionf(neutral).mul(delta),limit/angle).normalize();
                }
                // Halo phase is continuous across attack, movement and crossfade transitions.
                if(bone>=554&&bone<=556)idleRotation.rotationZ(age/20*(float)(Math.PI*2)/24*(bone==554?1:bone==555?-2:3));
                state.last[local]=idlePosition.x;state.last[local+1]=idlePosition.y;state.last[local+2]=idlePosition.z;
                state.last[local+3]=idleRotation.x;state.last[local+4]=idleRotation.y;state.last[local+5]=idleRotation.z;state.last[local+6]=idleRotation.w;
                state.dynamics.body(bone,idleRotation,entity);
                state.dynamics.chest(bone,idlePosition,idleRotation);
                int p = bone * 3;
                world[bone].translationRotate(idlePosition, idleRotation);
                authoredWorld[bone].set(world[bone]);
                int parent = boneParents[bone];
                if (parent >= 0){
                    authoredWorld[parent].mul(authoredWorld[bone],authoredWorld[bone]);
                    world[parent].mul(world[bone], world[bone]);
                }
                state.dynamics.apply(springJoints[bone],world,authoredWorld);
                if(bone==8)state.dynamics.ribcage(world[bone]);
                skin[bone].set(world[bone]).translate(-bonePositions[p], -bonePositions[p + 1], -bonePositions[p + 2]);
            }
            // Do not translate just the wrist to clear cloth. The forearm and
            // hand share weighted vertices, so that correction stretches them.
            for (int i = 0; visible&&i < positions.length / 3; i++) {
                int p = i * 3, influence = i * 4;
                float x = positions[p], y = positions[p + 1], z = positions[p + 2];
                if (p+2<facial.offsets.length) {
                    x += facial.offsets[p];
                    y += facial.offsets[p + 1];
                    z += facial.offsets[p + 2];
                }
                float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
                float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0;
                for (int j = 0; j < 4; j++) {
                    float w = boneWeights[influence + j];
                    if (w <= 0) continue;
                    Matrix4f m = skin[boneIndices[influence + j]];
                    ox += w * (m.m00()*x + m.m10()*y + m.m20()*z + m.m30());
                    oy += w * (m.m01()*x + m.m11()*y + m.m21()*z + m.m31());
                    oz += w * (m.m02()*x + m.m12()*y + m.m22()*z + m.m32());
                    onx += w * (m.m00()*nx + m.m10()*ny + m.m20()*nz);
                    ony += w * (m.m01()*nx + m.m11()*ny + m.m21()*nz);
                    onz += w * (m.m02()*nx + m.m12()*ny + m.m22()*nz);
                }
                float length = (float) Math.sqrt(onx*onx + ony*ony + onz*onz);
                if (length < 1e-6F) length = 1;
                outPositions[p] = ox;
                outPositions[p + 1] = oy;
                outPositions[p + 2] = oz;
                outNormals[p] = onx / length;
                outNormals[p + 1] = ony / length;
                outNormals[p + 2] = onz / length;
            }
            cachedEntity=entity;cachedFrame=frame;cachedAge=poseAge;
            cachedPose=new MeshPose(outPositions,outNormals,uvs);
            return cachedPose;
        }

    }

    private static final class FacialMorphs {
        final float[][] deltas;
        final float[] offsets;
        final int[] affected;

        private FacialMorphs(float[][] deltas) {
            this.deltas = deltas;
            offsets=new float[deltas[0].length];
            java.util.BitSet used=new java.util.BitSet(offsets.length/3);
            for(float[] channel:deltas)for(int p=0;p<channel.length;p+=3)
                if(channel[p]!=0||channel[p+1]!=0||channel[p+2]!=0)used.set(p/3);
            affected=used.stream().toArray();
        }

        void mix(float[] weights) {
            for(int vertex:affected) {
                int p=vertex*3;float x=0,y=0,z=0;
                for(int i=0;i<weights.length;i++) {
                    float w=weights[i];if(w==0)continue;
                    x+=deltas[i][p]*w;y+=deltas[i][p+1]*w;z+=deltas[i][p+2]*w;
                }
                offsets[p]=x;offsets[p+1]=y;offsets[p+2]=z;
            }
        }

        static FacialMorphs load() {
            try {
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FACIAL);
                if (resource.isEmpty()) throw new IOException("Missing " + FACIAL);
                try (DataInputStream stream = new DataInputStream(resource.get().open())) {
                    if (Mesh.readInt(stream) != 0x4F554D31 || Mesh.readInt(stream) != 1)
                        throw new IOException("Unsupported facial morph data");
                    int vertices = Mesh.readInt(stream), morphs = Mesh.readInt(stream);
                    Mesh.readInt(stream); // reserved
                    if (vertices <= 0 || morphs != OutsiderExpressions.CHANNELS)
                        throw new IOException("Invalid facial morph dimensions");
                    float[][] deltas = new float[morphs][vertices * 3];
                    for (int morph = 0; morph < morphs; morph++) {
                        if (Mesh.readInt(stream) != morph) throw new IOException("Unordered facial morphs");
                        int count = Mesh.readInt(stream);
                        if (count < 0 || count > vertices) throw new IOException("Invalid facial morph count");
                        for (int i = 0; i < count; i++) {
                            int vertex = Mesh.readInt(stream);
                            if (vertex < 0 || vertex >= vertices) throw new IOException("Invalid facial vertex");
                            int p = vertex * 3;
                            deltas[morph][p] = Mesh.readFloat(stream);
                            deltas[morph][p + 1] = Mesh.readFloat(stream);
                            deltas[morph][p + 2] = Mesh.readFloat(stream);
                        }
                    }
                    return new FacialMorphs(deltas);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load the Outsider facial morphs", e);
            }
        }
    }

    private record MeshPose(float[] positions, float[] normals, float[] uvs) {}
}
