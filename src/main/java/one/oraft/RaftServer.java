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

@Slf4j
@Data
public class RaftServer implements BaseServer {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private int port;

    private String host;

    private RaftNode node;

    private NettyServer server;

    private Map<Integer, ClientChannelHandler> clientChannelHandlerMap = Collections.synchronizedMap(new HashMap<>());

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
                ClientChannelHandler clientChannelHandler = new ClientChannelHandler(((ctx, msg) -> onRpcMessage(ctx, msg)));
                CompletableFuture<Boolean> connectedFuture = new CompletableFuture<>();
                EXECUTOR_SERVICE.execute(() -> {
                    NettyClient client = new NettyClient(clientChannelHandler, connectedFuture);
                    client.connect(remoteServer.getHost(), remoteServer.getPort());
                });
                connectedFuture.whenComplete((connected, t) -> {
                    if (connected) {
                        log.info("[raft-server]:{} requestVote, client connected", node.getId());
                        clientChannelHandlerMap.put(remoteServer.getId(), clientChannelHandler);
                        clientChannelHandler.send(JSON.toJSONString(reqMap));
                    } else {
                        log.info("[raft-server]:{} requestVote, client connect error", node.getId(), t);
                    }
                });

            }
        } catch (Exception e) {
            log.error("[raft-server] requestVote nodeId: {}, error", node.getId(), e);
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
                "term", node.getCurrentTerm(),
                "leaderId", node.getId(),
                "prevLogIndex", -1,
                "prevLogTerm", -1,
                "leaderCommit", -1,
                "entries", Collections.emptyList()
        ));
        for (Map.Entry<Integer, ClientChannelHandler> entry : clientChannelHandlerMap.entrySet()) {
            if (entry.getKey().equals(node.getId())) continue;
            entry.getValue().send(json);
        }
    }


    private void stopLeaderScheduled() {
        leaderScheduledFuture.cancel(true);
        leaderScheduledFuture = null;
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
        ctx.writeAndFlush(JSON.toJSONString(Map.of("RPC_TYPE", RPCTypeEnum.VoteGranted.getType(), "term", node.getCurrentTerm(), "voteGranted", voteGranted)));
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
            leaderScheduledFuture = SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> appendEntries(), 0, 3, TimeUnit.SECONDS);
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
