package one.oraft.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.oraft.RpcMessageBuilder;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Data
@NoArgsConstructor
public class NettyClient {

    private static final MultiThreadIoEventLoopGroup G = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    private ChannelHandler channelHandler;

    private ChannelFuture clientConnectedFuture;

    public NettyClient(ChannelHandler channelHandler) {
        this.channelHandler = channelHandler;
    }

    public void connect(String host, int port, CompletableFuture<Boolean> clientConnectedCallback) {
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.group(G)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(host, port)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Unpooled.unreleasableBuffer(RpcMessageBuilder.SPLITTER_BUF)))
                                    .addLast(new StringEncoder())
                                    .addLast(new StringDecoder())
                                    .addLast(channelHandler)
                            ;
                        }
                    })
            ;
            clientConnectedFuture = bootstrap.connect().sync();
            log.info("[netty-client] connected to {}:{}", host, port);
            clientConnectedCallback.complete(true);
        } catch (InterruptedException e) {
            log.error("[netty-client] connect to {}:{} error", host, port, e);
            clientConnectedCallback.completeExceptionally(e);
        }
    }

    public void send(String msg) {
        ((ClientChannelHandler) channelHandler).send(msg);
    }

    public boolean stop() {
        if (clientConnectedFuture != null) {
            clientConnectedFuture.channel().close();
            return true;
        }
        return false;
    }

}
