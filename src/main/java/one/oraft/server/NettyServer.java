package one.oraft.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.oraft.RaftNode;
import one.oraft.RpcMessageBuilder;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NettyServer {

    private int port;

    private String host;

    private RaftNode serverNode;

    private ChannelHandler channelHandler;

    private AtomicBoolean isStarted = new AtomicBoolean(false);

    MultiThreadIoEventLoopGroup boss = null;
    MultiThreadIoEventLoopGroup worker = null;

    public NettyServer(int port, String host) {
        this.port = port;
        this.host = host;
    }

    public NettyServer(int port, String host, ServerChannelHandler channelHandler) {
        this.port = port;
        this.host = host;
        this.channelHandler = channelHandler;
    }

    public void start() {
        try {
            boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            worker = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Unpooled.unreleasableBuffer(RpcMessageBuilder.SPLITTER_BUF)))
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    .addLast(channelHandler)
                            ;
                        }
                    })
            ;
            ChannelFuture future = b.bind(host, port).sync();
            isStarted.compareAndSet(false, true);
            log.info("[netty-server] started at port: {}", port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("[netty-server] start error", e);
        } finally {
            if (boss != null) {
                boss.shutdownGracefully();
            }
            if (worker != null) {
                worker.shutdownGracefully();
            }
        }
    }

    public boolean stop() {
        if (isStarted()) {
            try {
                boss.shutdownGracefully();
                worker.shutdownGracefully();
                isStarted.compareAndSet(true, false);
                return true;
            } catch (Exception e) {
                log.error("[netty-server] {}:{} stop error", host, port, e);
                return false;
            }
        }
        return true;
    }

    public boolean isStarted() {
        return isStarted.get();
    }

    public static void main(String[] args) {
        new NettyServer(8181, "localhost", new ServerChannelHandler((ctx, msg) -> log.info("[netty-server] receive message: {}", msg))).start();
    }

}
