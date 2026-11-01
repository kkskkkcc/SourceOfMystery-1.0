package com.sourceofmystery.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;

/** Test-only asynchronous PBO readback and NVENC capture. No PNG encoding on the render path. */
final class OfferingMovieCapture {
    private static final int FRAMES=34*60; // Includes two seconds after handing control back.
    private record Frame(int index,byte[] bytes){}
    private static final Frame END=new Frame(-1,null);
    private final int[] pbo=new int[3],pending={-1,-1,-1};
    private final int width,height,size;
    private int cursor,lastIndex=-1;
    private final ArrayBlockingQueue<byte[]> pool=new ArrayBlockingQueue<>(7);
    private final ArrayBlockingQueue<Frame> queue=new ArrayBlockingQueue<>(4);
    private final Process encoder;
    private final Thread worker;
    private volatile Throwable failure;

    OfferingMovieCapture()throws IOException{
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        width=target.width;height=target.height;size=width*height*4;
        for(int i=0;i<7;i++)pool.add(new byte[size]);
        int binding=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        for(int i=0;i<pbo.length;i++){
            pbo[i]=GL15.glGenBuffers();GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER,size,GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,binding);
        Path directory=mc.gameDirectory.toPath();
        encoder=new ProcessBuilder(System.getProperty("sourceofmystery.offeringFfmpeg"),
                "-hide_banner","-loglevel","error","-y","-f","rawvideo","-pix_fmt","rgba",
                "-video_size",width+"x"+height,"-framerate","60","-i","pipe:0",
                "-vf","vflip","-an","-c:v","h264_nvenc","-preset","p4","-cq","19",
                "-b:v","0","-pix_fmt","yuv420p","-movflags","+faststart",
                directory.resolve("capture-silent.mp4").toString())
                .redirectError(directory.resolve("encoder.log").toFile()).start();
        worker=new Thread(this::encode,"Offering NVENC capture");worker.setDaemon(true);worker.start();
    }
    void capture(float seconds){
        int index=Math.min(FRAMES-1,(int)(seconds*60));if(index<=lastIndex)return;lastIndex=index;
        if(failure!=null)throw new IllegalStateException("Video encoder failed",failure);
        int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if(pending[cursor]>=0)drain(cursor);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,Minecraft.getInstance().getMainRenderTarget().frameBufferId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo[cursor]);
        GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        pending[cursor]=index;
        int oldest=(cursor+1)%pbo.length;if(pending[oldest]>=0)drain(oldest);
        cursor=(cursor+1)%pbo.length;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
    }
    private void drain(int slot){
        try{
            byte[] bytes=pool.take();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo[slot]);
            ByteBuffer mapped=GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER,0,size,GL30.GL_MAP_READ_BIT);
            if(mapped==null)throw new IllegalStateException("Pixel buffer map failed");
            mapped.get(bytes);GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            queue.put(new Frame(pending[slot],bytes));pending[slot]=-1;
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}
    }
    private void encode(){
        Frame last=null;int next=0;
        try(OutputStream out=new BufferedOutputStream(encoder.getOutputStream(),1<<20)){
            while(true){
                Frame frame=queue.take();if(frame==END)break;
                while(next<=frame.index){out.write(frame.bytes);next++;}
                if(last!=null)pool.put(last.bytes);last=frame;
            }
            if(last!=null)while(next<FRAMES){out.write(last.bytes);next++;}
        }catch(Throwable error){failure=error;}
    }
    void finish(){
        int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        while(true){
            int slot=-1;for(int i=0;i<pending.length;i++)if(pending[i]>=0&&(slot<0||pending[i]<pending[slot]))slot=i;
            if(slot<0)break;drain(slot);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);
        for(int buffer:pbo)GL15.glDeleteBuffers(buffer);
        try{
            queue.put(END);worker.join(30000);
            if(worker.isAlive()||!encoder.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Video encoder did not finish");
            if(failure!=null||encoder.exitValue()!=0)throw new IllegalStateException("Video encoder failed; inspect encoder.log",failure);
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}
    }
}
