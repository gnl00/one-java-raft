package one.oraft.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.function.BiConsumer;

// TODO ServerChannelHandler
@Slf4j
public class ServerChannelHandler extends SimpleChannelInboundHandler<String> {

    private final BiConsumer<ChannelHandlerContext, String> messageConsumer;

    public ServerChannelHandler(BiConsumer<ChannelHandlerContext, String> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.info("[server-channel-handler] received message: {}", msg);
        messageConsumer.accept(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[server-channel-handler] exception caught: {}", cause.getMessage());
    }
}
