package one.oraft;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RaftNode 节点存储节点状态
 */
@Slf4j
@Data
public class RaftNode {

    private final Integer timeout = 3;

    private int id;

    private int term;

    private State state;

    private LinkedList<Map<String, Object>> logEntries;

    private Integer leaderId;

    private boolean hasLeader;

    private Integer votedFor;

    private AtomicBoolean hasCandidate;

    private AtomicInteger gatherVoteCounts;

    public RaftNode(Integer id) {
        this.id = id;
        state = State.STATE_FOLLOWER;
        hasLeader = false;
        gatherVoteCounts = new AtomicInteger(0);
    }

    /**
     * 为了防止选票还被瓜分，必须采取一些额外的措施, raft采用随机election timeout的机制防止选票被持续瓜分。
     * 通过将timeout随机设为一段区间上的某个值, 因此很大概率会有某个candidate率先超时然后赢得大部分选票.
     */
    public Integer getTimeout() {
        return timeout + Double.valueOf((Math.random() * 10)).intValue();
    }
}
