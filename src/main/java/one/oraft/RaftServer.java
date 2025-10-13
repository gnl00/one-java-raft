package one.oraft;

import com.alibaba.fastjson2.JSON;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.oraft.client.ClientChannelHandler;
import one.oraft.client.NettyClient;
import one.oraft.server.NettyServer;
import one.oraft.server.ServerChannelHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Data
public class RaftServer implements BaseServer {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static volatile AtomicBoolean STOP_THE_FIRST_LEADER = new AtomicBoolean(false);

    private final Map<Integer, NettyClient> clientMap = Collections.synchronizedMap(new HashMap<>());

    private int port;

    private String host;

    private RaftNode node;

    private NettyServer server;

    private ScheduledFuture<?> leaderScheduledFuture;

    public RaftServer(int port, String host) {
        this.port = port;
        this.host = host;
    }

    public Integer getId() {
        return node.getId();
    }

    public void start() {
        Integer nextNodeId = Registry.getNextNodeId();
        node = new RaftNode(nextNodeId);
        server = new NettyServer(port, host, new ServerChannelHandler(((ctx, msg) -> onRpcMessage(ctx, msg))));
        EXECUTOR_SERVICE.execute(() -> server.start());
        Registry.doRegistry(nextNodeId, this);
        SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> checkTimeout(), node.getTIMEOUT(), node.getTIMEOUT(), TimeUnit.SECONDS);
        log.info("[raft-server]:{} checkTimeout start timeout={}/s", node.getId(), node.getTIMEOUT());
    }

    public void stop() {
        if (Objects.nonNull(server) && server.stop()) {
            node.stop();
            for (NettyClient client : clientMap.values()) {
                if (client != null) {
                    client.stop();
                }
            }
            clientMap.clear();
            log.info("[raft-server]:{} server stopped", getId());
        }
    }

    /**
     * 在timeout信号的驱使下，follower会转变成candidate去拉取选票，
     * 获得大多数选票后就会成为leader，这时候如果其他候选人发现了新的leader已经诞生，就会自动转变为follower
     */
    private void checkTimeout() {
        log.info("[raft-server]:{} check-timeout", node.getId());
        if (node.getId() != node.getLeaderId() && Objects.nonNull(leaderScheduledFuture)) {
            stopLeaderScheduled();
        }
        if (node.checkTimeout()) {
            requestVote(Map.of("term", node.getCurrentTerm(), "candidateId", node.getId(), "lastLogTerm", node.getCurrentTerm() - 1, "lastLogIndex", -1));
        }
    }

    @Override
    public void requestVote(Map<String, Object> request) {
        try {
            Map<String, Object> reqMap = new HashMap<>(request);
            log.info("[raft-server]:{} requestVote", node.getId());
            reqMap.put("RPC_TYPE", "RequestVote");
            Map<Integer, RaftServer> serverMap = Registry.discoveryMap();
            for (RaftServer remoteServer : serverMap.values()) {
                if (Objects.equals(remoteServer.getId(), this.getId())) {
                    continue;
                }
                EXECUTOR_SERVICE.execute(() -> {
                    ClientChannelHandler clientChannelHandler = new ClientChannelHandler(((ctx, msg) -> onRpcMessage(ctx, msg)));
                    CompletableFuture<Boolean> connectedCallback = new CompletableFuture<>();
                    NettyClient c = new NettyClient(clientChannelHandler);
                    c.connect(remoteServer.getHost(), remoteServer.getPort(), connectedCallback);
                    connectedCallback.whenComplete((connected, t) -> {
                        if (connected) {
                            clientMap.put(remoteServer.getId(), c);
                            log.info("[raft-server]:{} requestVote, client connected", getId());
                            c.send(JSON.toJSONString(reqMap));
                        } else {
                            log.info("[raft-server]:{} requestVote, client connect error", getId(), t);
                            c.stop();
                        }
                    });
                });
            }
        } catch (Exception e) {
            log.error("[raft-server] requestVote nodeId: {}, error", getId(), e);
        }
    }

    @Override
    public void appendEntries() {
        if (node.getId() != node.getLeaderId() && Objects.nonNull(leaderScheduledFuture)) {
            stopLeaderScheduled();
            return;
        }
        String json = JSON.toJSONString(Map.of(
                "RPC_TYPE", RPCTypeEnum.AppendEntries.getType(),
                "timestamp", System.currentTimeMillis(),
                "term", node.getCurrentTerm(),
                "leaderId", node.getId(),
                "prevLogIndex", -1,
                "prevLogTerm", -1,
                "leaderCommit", -1,
                "entries", Collections.emptyList()
        ));
        for (Map.Entry<Integer, NettyClient> entry : clientMap.entrySet()) {
            if (entry.getKey().equals(getId())) continue;
            entry.getValue().send(json);
        }
    }


    private void stopLeaderScheduled() {
        if (Objects.nonNull(leaderScheduledFuture)) {
            leaderScheduledFuture.cancel(true);
            leaderScheduledFuture = null;
        }
    }

    private void onRpcMessage(ChannelHandlerContext ctx, String msg) {
        /*
         * 会收到两种消息：
         * - RequestVote RPC：它由选举过程中的candidate发起，用于拉取选票
         * - AppendEntries RPC：它由leader发起，用于复制日志或者发送心跳信号。
         */
        Map<String, Object> request = (Map<String, Object>) JSON.parse(msg);
        String rpcType = (String) request.get("RPC_TYPE");
        RPCTypeEnum byType = RPCTypeEnum.getByType(rpcType);
        switch (byType) {
            case RequestVote:
                // 处理 RequestVote RPC
                handleRequestVote(ctx, request);
                break;
            case VoteGranted:
                handleVoteGranted(request);
                break;
            case AppendEntries:
                // 处理 AppendEntries RPC
                handleAppendEntries(request);
                break;
            default:
                // 处理其他类型的 RPC
                break;
        }
    }

    /**
     * 如果收到了来自leader或candidate的RPC，那它就保持follower状态，避免争抢成为candidate
     * 每个 node 在当前 term 只能投票一次
     */
    private void handleRequestVote(ChannelHandlerContext ctx, Map<String, Object> request) {
        log.info("[server-channel-handler]:{} handleRequestVote leaderId: {} votedFor: {}", node.getId(), node.getLeaderId(), node.getVotedFor());
        Integer candidateId = (Integer) request.get("candidateId");
        Integer term = (Integer) request.get("term");
        boolean voteGranted = node.voteGranted(candidateId, term);
        log.info("[server-channel-handler]:{} handleRequestVote voted, vote for candidateId: {} ? {}", node.getId(), candidateId, voteGranted);
        ctx.writeAndFlush(RpcMessageBuilder.build(JSON.toJSONString(Map.of("RPC_TYPE", RPCTypeEnum.VoteGranted.getType(), "term", node.getCurrentTerm(), "voteGranted", voteGranted))));
    }

    /**
     * STATE_CANDIDATE 如果收到了来自follower的投票结果RPC，进行统计，如果超过半数的节点同意，则成为leader
     */
    private void handleVoteGranted(Map<String, Object> request) {
        log.info("[server-channel-handler]:{} handleVoteGranted leaderId: {} votedFor: {}", node.getId(), node.getLeaderId(), node.getVotedFor());
        Integer term = (Integer) request.get("term");
        boolean voteGranted = (boolean) request.get("voteGranted");
        boolean upgradedToLeader = node.tryUpgradeToLeader(term, voteGranted);
        if (upgradedToLeader) {
            leaderScheduledFuture = SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> appendEntries(), 0, 5, TimeUnit.SECONDS);
            if (STOP_THE_FIRST_LEADER.compareAndSet(false, true)) {
                SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
                    stop();
                    log.info("[raft-server]:{} stopped the first leader", node.getId());
                }, 10, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * STATE_FOLLOWER/STATE_CANDIDATE 如果收到了来自leader的AppendEntries RPC，则转为follower状态
     */
    public void handleAppendEntries(Map<String, Object> request) {
        Integer leaderId = (Integer) request.get("leaderId");
        Integer term = (Integer) request.get("term");
        List<Object> entries = (List<Object>) request.get("entries");
        node.handleAppendEntries(leaderId, term, entries);
    }
}
