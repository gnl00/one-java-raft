package one.oraft.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Data
@NoArgsConstructor
public class NettyClient {

    private ChannelHandler channelHandler;

    private CompletableFuture<Boolean> clientFuture;

    public NettyClient(ChannelHandler channelHandler, CompletableFuture<Boolean> clientFuture) {
        this.channelHandler = channelHandler;
        this.clientFuture = clientFuture;
    }

    public void connect(String host, int port) {
        Bootstrap bootstrap = new Bootstrap();
        try (MultiThreadIoEventLoopGroup g = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())) {
            bootstrap.group(g)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(host, port)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) {
                            ch.pipeline()
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
        }
    }

}
