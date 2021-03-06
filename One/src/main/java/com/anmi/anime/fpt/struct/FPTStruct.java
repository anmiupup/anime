package com.anmi.anime.fpt.struct;

import com.anmi.anime.fpt.FPT4File;
import com.anmi.anime.fpt.FPTBase;
import com.anmi.anime.annotation.NoTrim;
import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.anmi.anime.fpt.struct.CommonStruct.*;
import static com.anmi.anime.utils.ByteUtil.byteToString;
import static com.anmi.anime.utils.ByteUtil.stringToByte;

/**
 * Created by wangjue on 2017/6/26.
 */
public class FPTStruct {

    private static ThreadLocal<Integer> globalReadIndex = new ThreadLocal<>();
    private static Charset encoding = FPTBase.GBK_ENCODEING;

    public static Charset getEncoding() {
        return encoding;
    }

    public static void setEncoding(Charset encoding) {
        FPTStruct.encoding = encoding;
    }



    private static byte[] readByteBuf(ChannelBuffer byteBuf,int len,int readIndex) throws Throwable {
        byteBuf.readerIndex(readIndex);
        byte[] bytes = byteBuf.readBytes(len).array();
        globalReadIndex.set(byteBuf.readerIndex());
        return bytes;
    }

    private static void resetReadIndex() throws Throwable {
        globalReadIndex.set(new Integer(0));
    }

    public static <T> T readFPTByteBuf(byte[] b,T t, Charset encoding) throws Throwable {
        setEncoding(encoding);
        try {
            T readT = readFPTByteBuf(b, t);
            resetReadIndex();
            return readT;
        } catch (Throwable tx) {
            resetReadIndex();
            throw tx;
        }
    }

    private static <T> T readFPTByteBuf(byte[] b,T t) throws Throwable {
        ChannelBuffer byteBuf = ChannelBuffers.wrappedBuffer(b);
        Class<?> clazz = t.getClass();
        int readIndex;
        if (globalReadIndex.get()==null) resetReadIndex();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            readIndex = globalReadIndex.get();
            String fieldName = field.getName();
            if (field.getType() == clazz.getDeclaringClass()) continue;
            int fieldLength = getFieldLength(field, t);
            byte[] bytes = new byte[fieldLength];
            if (field.getType().getSuperclass() != BaseStruct.class && field.getType() != List.class) {
                try {
                    bytes = readByteBuf(byteBuf,fieldLength,readIndex);
                    /*特例处理
                    * 某些FPT人像数据脱密后直接toString,导致存的是内存地址唯一值：[B...
                    * 读到人像长度，同时判断人像数据是否是[B开头，如果是，长度直接设为11()
                    * 最后buffer的readIndex回滚到人像长度
                    * */
                    if ("portraitDataLength".equals(fieldName)) {
                        byte[] portraitData = byteBuf.readBytes(11).array();
                        if (portraitData[0] == 91 && portraitData[1] == 66) {
                            bytes = stringToByte("11", fieldLength);
                        }
                        byteBuf.readerIndex(byteBuf.readerIndex()-11);
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new ArrayIndexOutOfBoundsException(String.format("%s : %s", e.getMessage(), field.getName()));
                }
            }
            Method method = clazz.getMethod("set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1), field.getType());
            if (field.getType() == String.class) {
                method.invoke(t, byteToString(bytes, getEncoding(),hasAnnotation(field, NoTrim.class)));
            } else if (field.getType() == byte.class) {
                method.invoke(t, bytes[0]);
            } else if (field.getType() == byte[].class) {
                method.invoke(t, bytes);
            } else if (field.getType().getSuperclass() == BaseStruct.class) {
                T t_o = readFPTByteBuf(b, getInnerClassInstance(field.getType(), clazz));
                method.invoke(t,t_o);
            } else if (field.getType() == List.class) {
                ParameterizedType p = (ParameterizedType) field.getGenericType();//获取泛型类型
                Type[] types = p.getActualTypeArguments();
                Type type = types[0];
                List<Object> lo = new ArrayList<>();
                byte end;
                Class<?> declaringClass = clazz.getDeclaringClass() == null ? clazz : clazz.getDeclaringClass();
                Object innerObject = getInnerClassInstance(type, declaringClass);
                if (innerObject.getClass().getSuperclass() == BaseStruct.class) {
                    String tp = getFieldValue("tpCount",t).toString();
                    String lp = getFieldValue("lpCount",t).toString();
                    boolean hasTp = tp.isEmpty() || "0".equals(tp) ? false : true;
                    boolean hasLp = lp.isEmpty() || "0".equals(lp) ? false : true;
                    if (fieldLength == 0 && (!hasTp && !hasLp)) fieldLength = 99; //FPT 中逻辑类未设置则尝试读取
                    for (int i=0;i<fieldLength;i++) {
                        innerObject = getInnerClassInstance(type, declaringClass);
                        try {
                            Object o = readFPTByteBuf(b, innerObject);
                            lo.add(o);
                        } catch (Exception e) {
                            if (fieldLength != 99) //FPT3中未设置档案或现场逻辑个数，出现异常不抛出
                                throw e;
                        }
                    }
                    method.invoke(t, lo);
                } else {
                    List<Object> loo = new ArrayList<>();
                    if (fieldLength !=0 ) {
                        do {
                            innerObject = getInnerClassInstance(type, declaringClass);
                            Object o = readFPTByteBuf(b, innerObject);
                            end = (byte) getFieldValue("end", o);
                            loo.add(o);
                        } while (end == FPTBase.GS);
                    } else {
                        readByteBuf(byteBuf,1,readIndex);//结束符
                    }
                    method.invoke(t, loo);
                }
            }
        }
        return t;
    }

    public static <T> byte[] buildFPTStruct(T t,Charset encoding) throws Throwable {
        setEncoding(encoding);
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        buildFPTStruct(t,buffer);
        int read = buffer.readableBytes();
        ChannelBuffer result = buffer.copy(0,read);
        return result.array();
    }

    public static <T> byte[] buildFPTStruct(T t,ChannelBuffer buffer) throws Throwable {
        Class<?> clazz = t.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if (field.getType() == clazz.getDeclaringClass()) continue;
            int fieldLength = getFieldLength(field, t);
            Method method =  clazz.getMethod("get"+fieldName.substring(0,1).toUpperCase()+fieldName.substring(1));
            Object o = method.invoke(t);
            if (field.getType() == String.class) {
                buffer.writeBytes(stringToByte((String)o,fieldLength,encoding));
            } else if (field.getType() == byte.class) {
                buffer.writeBytes(new byte[]{(byte)o});
            } else if (field.getType() == byte[].class) {
                buffer.writeBytes((byte[])o);
            } else if (field.getType().getSuperclass() == BaseStruct.class) {
                buildFPTStruct(o,buffer);
            } else if (field.getType() == List.class) {
                if (o == null) buffer.writeBytes(new byte[]{FPTBase.FS});
                else {
                    for (Object lo : (List) o) {
                        buildFPTStruct(lo, buffer);
                    }
                }
            }
        }
        return buffer.array();
    }

    public static void main(String[] args) {
        for (int i=0;i<1000;i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = FileUtils.readFileToByteArray(new File("D:\\2010\\A3301050000002016062360.fpt"));
                        FPT4File fpt = FPTStruct.readFPTByteBuf(bytes, new FPT4File());
                        System.out.println(Thread.currentThread().getName()+","+fpt.getFileLength()+","+fpt.getLogicLPRec().size());
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }).start();
        }
    }

}
