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

    private ChannelHandler channelHandler;

    private CompletableFuture<Boolean> clientFuture;

    MultiThreadIoEventLoopGroup g;

    public NettyClient(ChannelHandler channelHandler, CompletableFuture<Boolean> clientFuture) {
        this.channelHandler = channelHandler;
        this.clientFuture = clientFuture;
    }

    public void connect(String host, int port) {
        Bootstrap bootstrap = new Bootstrap();
        try {
            g = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            bootstrap.group(g)
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
            ChannelFuture future = bootstrap.connect().sync();
            log.info("[netty-client] connected to {}:{}", host, port);
            clientFuture.complete(true);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("[netty-client] connect to {}:{} error", host, port, e);
        } finally {
            if (g != null) {
                g.shutdownGracefully();
            }
        }
    }

    public boolean stop() {
        if (g != null) {
            g.shutdownGracefully();
            return true;
        }
        return false;
    }

}
