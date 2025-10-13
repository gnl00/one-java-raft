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

/*
 * 到底该不该将 Client 放到对象池中？
 * Client 在每次连接时需要：实例化 => 创建连接 => 发送消息 => 接收/处理消息
 * 刚开始觉得可以放在对象池中，下次不需要创建连接，直接复用。对比了一下数据库的对象池设计，数据库连接对象为什么需要放在对象池中？为什么可以放在对象池中复用？
 * 因为
 * - 数据库连接地址一般来说是固定的，不变的。复用连接对象，不用担心连接对象的成员变量属性被“污染”。
 * - 当前的 NettyClient 连接的对象，是一个个不同的 Node 节点，IP 和 Port 是不固定的，在每次创建 Client 并连接的时候需要根据 Node 节点的 IP 和 Port 来创建连接。
 *   主要还是，当前 Client 创建需要耗费的时间和资源都不大，连接的时候使用即可。
 */
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
