package one.oraft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class RpcMessageBuilder {

    public final static String SPLITTER = "\n\n\n";

    public final static ByteBuf SPLITTER_BUF = Unpooled.wrappedBuffer(SPLITTER.getBytes(StandardCharsets.UTF_8));

    public static String build(String origin) {
        return origin + SPLITTER;
    }
}
