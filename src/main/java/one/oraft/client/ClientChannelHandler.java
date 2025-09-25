package one.oraft.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.BiConsumer;

// TODO ClientChannelHandler
@Slf4j
public class ClientChannelHandler extends SimpleChannelInboundHandler<String> {

    private ChannelHandlerContext ctx;

    private final BiConsumer<ChannelHandlerContext, String> messageConsumer;

    public ClientChannelHandler(BiConsumer<ChannelHandlerContext, String> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.info("[client-channel-handler] received: {}", msg);
        messageConsumer.accept(ctx, msg);
    }

    public void send(String jsonStr) {
        if (Objects.nonNull(ctx)) {
            log.info("[client-channel-handler] sending: {}", jsonStr);
            ctx.writeAndFlush(jsonStr);
        }
    }
}
