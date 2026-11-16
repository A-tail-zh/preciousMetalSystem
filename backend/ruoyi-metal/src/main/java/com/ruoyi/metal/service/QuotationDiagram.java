package com.ruoyi.metal.service;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import javax.imageio.ImageIO;

/** Versioned, deliberately unscaled drawings; saved PNGs are never regenerated. */
public final class QuotationDiagram {
    public static final int VERSION=1;
    private QuotationDiagram() {}
    public static byte[] render(Map<String,Object> item){
        String type=ProductTypes.require(String.valueOf(item.get("productType")));
        if(!(item.get("dimensions") instanceof Map<?,?> dimensions))throw new IllegalArgumentException("缺少产品尺寸");
        var image=new BufferedImage(1200,800,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);g.fillRect(0,0,1200,800);g.setColor(new Color(35,50,65));
            text(g,String.valueOf(item.get("metalTypeName"))+" · "+type,40,58,1120,32);
            text(g,"产品规格尺寸示意图（非按比例）",40,104,1120,22);
            g.setStroke(new BasicStroke(4));g.setColor(new Color(42,92,128));
            switch(type){
                case "环" -> {g.drawOval(70,240,450,230);g.drawOval(160,285,270,135);g.drawArc(70,300,450,230,180,180);g.drawLine(70,355,70,415);g.drawLine(520,355,520,415);}
                case "杆" -> {g.drawRect(140,270,360,210);g.drawOval(75,270,130,210);g.drawArc(435,270,130,210,-90,180);if(new java.math.BigDecimal(dimensions.get("innerDiameter").toString()).signum()>0)g.drawOval(108,320,65,110);}
                case "靶" -> {g.drawOval(85,240,450,220);g.drawArc(85,285,450,220,180,180);g.drawLine(85,350,85,395);g.drawLine(535,350,535,395);g.drawOval(120,257,380,175);}
                case "方形盖","方形坩埚" -> {
                    polygon(g,new int[]{90,380,530,240},new int[]{290,290,200,200});
                    polygon(g,new int[]{90,380,530,530,380,90},new int[]{290,290,200,450,560,560});g.drawLine(380,290,380,560);
                    if("方形坩埚".equals(type))polygon(g,new int[]{155,365,460,250},new int[]{270,270,220,220});
                }
                case "弧底" -> {
                    var p=new Path2D.Double();p.moveTo(90,220);p.lineTo(90,440);p.quadTo(300,670,510,440);p.lineTo(510,220);p.lineTo(470,220);p.lineTo(470,430);p.quadTo(300,590,130,430);p.lineTo(130,220);p.closePath();g.draw(p);
                }
                case "梯形" -> {polygon(g,new int[]{80,520,430,170},new int[]{220,220,560,560});polygon(g,new int[]{130,470,395,205},new int[]{220,220,510,510});g.drawArc(160,470,120,120,0,70);}
                case "平底坩埚" -> {polygon(g,new int[]{90,130,130,470,470,510,510,90},new int[]{220,220,510,510,220,220,560,560});}
                default -> throw new IllegalArgumentException("产品类型无效");
            }
            g.setColor(new Color(225,232,238));g.drawLine(600,160,600,690);g.setColor(new Color(35,50,65));
            int y=205;for(String key:ProductGeometry.FIELDS.get(type)){
                String value=BusinessDecimal.display(dimensions.get(key)),unit="angle".equals(key)?"°":" cm";
                text(g,ProductGeometry.label(type,key)+"："+value+unit,645,y,510,24);y+=62;
            }
            text(g,"具体尺寸以右侧标注为准；形状仅用于识别规格。",40,755,1120,20);
        }finally{g.dispose();}
        try(var out=new ByteArrayOutputStream()){if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");return out.toByteArray();}catch(IOException e){throw new IllegalStateException("规格图片生成失败",e);}
    }
    private static void polygon(Graphics2D g,int[] x,int[] y){g.drawPolygon(x,y,x.length);}
    private static void text(Graphics2D g,String text,int x,int y,int width,int size){while(size>8&&g.getFontMetrics(new Font(Font.SANS_SERIF,Font.PLAIN,size)).stringWidth(text)>width)size--;g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,size));g.drawString(text,x,y);}
}
