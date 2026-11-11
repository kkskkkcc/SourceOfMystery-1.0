package com.sourceofmystery.staff;

import java.util.*;

/** Geometry shared by drawing and hit testing; additional spells grow concentric rings. */
public final class StaffWheelLayout {
    public record Segment(int index,double inner,double outer,double start,double end) {
        public double angle(){return (start+end)/2;}
        public double radius(){return (inner+outer)/2;}
    }
    public static List<Segment> segments(int count) {
        List<Segment> result=new ArrayList<>();
        for(int offset=0,ring=0;offset<count;ring++) {
            int size=Math.min(8*(ring+1),count-offset);
            int sectors=ring==0?size:Math.max(8,size);
            for(int i=0;i<size;i++)result.add(new Segment(offset+i,30+ring*90,112+ring*90,
                    i*Math.PI*2/sectors-Math.PI/2+.025,(i+1)*Math.PI*2/sectors-Math.PI/2-.025));
            offset+=size;
        }
        return List.copyOf(result);
    }
    public static int hit(List<Segment> segments,double x,double y) {
        double r=Math.hypot(x,y),a=Math.atan2(y,x);
        if(a<-Math.PI/2)a+=Math.PI*2;
        for(Segment segment:segments)if(r>=segment.inner&&r<=segment.outer&&a>=segment.start&&a<=segment.end)return segment.index;
        return -1;
    }
}
