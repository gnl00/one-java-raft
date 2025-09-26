package one.oraft;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RaftNode 节点存储节点状态
 */
@Slf4j
@Data
public class RaftNode {

    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static final Integer LEADER_TIMEOUT = 15; // s

    private static final Integer ELECTION_TIMEOUT = 15; // s

    private static final Integer TIMEOUT = 3; // s

    private int id;

    private int currentTerm;

    private State state;

    private LinkedList<Map<String, Object>> logEntries;

    private Integer leaderId;

    private Integer votedFor;

    private AtomicInteger gatherVoteCount;

    private Long electionTimestamp;

    private Long heartbeatTimestamp;

    public RaftNode(Integer id) {
        this.id = id;
        state = State.STATE_FOLLOWER;
        leaderId = -1;
        votedFor = -1;
        gatherVoteCount = new AtomicInteger(0);
        electionTimestamp = -1L;
        heartbeatTimestamp = -1L;
    }

    /**
     * 为了防止选票还被瓜分，必须采取一些额外的措施, raft采用随机election timeout的机制防止选票被持续瓜分。
     * 通过将timeout随机设为一段区间上的某个值, 因此很大概率会有某个candidate率先超时然后赢得大部分选票.
     */
    public Integer getTIMEOUT() {
        return TIMEOUT + Double.valueOf((Math.random() * 10)).intValue();
    }

    public boolean checkTimeout() {
        if (heartbeatTimestamp == -1) {
            heartbeatTimestamp = System.currentTimeMillis();
        }
        // leader timeout
        if (state == State.STATE_FOLLOWER
                && System.currentTimeMillis() - heartbeatTimestamp > LEADER_TIMEOUT * 1000
        ) {
            leaderId = -1;
        }
        // if leader timeout, follower_node turn to candidate_node and try to get vote
        if (state == State.STATE_FOLLOWER
                && leaderId.equals(-1)
                && votedFor.equals(-1)
        ) {
            state = State.STATE_CANDIDATE;
            votedFor = id; // 候选人给自己投票
            gatherVoteCount.set(1);
            currentTerm = currentTerm + 1;
            electionTimestamp = System.currentTimeMillis();
            log.info("[raft-node]:{} check timeout, turn to candidate_node term: {}", id, currentTerm);
            return true;
        }
        if ((state == State.STATE_CANDIDATE && System.currentTimeMillis() - electionTimestamp > ELECTION_TIMEOUT * 1000)
                || (state == State.STATE_FOLLOWER && leaderId.equals(-1) && !votedFor.equals(-1) && System.currentTimeMillis() - electionTimestamp > ELECTION_TIMEOUT * 1000)
        ) {
            stepDown(currentTerm);
            electionTimestamp = System.currentTimeMillis();
            log.info("[raft-node]:{} election timeout, turn to follower_node", id);
        }
        return false;
    }

    public boolean voteGranted(Integer candidateId, Integer term) {
        boolean granted = false;
        if (term > currentTerm) {
            stepDown(term);
        }
        if (votedFor == candidateId && currentTerm == term) { // 对已经投过票的候选人保持幂等
            granted = true;
        }
        if (state.equals(State.STATE_FOLLOWER)
                && leaderId.equals(-1)
                && votedFor.equals(-1)
                && term == currentTerm
        ) {
            votedFor = candidateId;
            granted = true;
        }
        if (granted) {
            electionTimestamp = System.currentTimeMillis();
        }
        return granted;
    }

    public boolean tryUpgradeToLeader(Integer term, boolean voteGranted) {
        if (!voteGranted && term > currentTerm) {
            stepDown(term);
            log.info("[raft-node]:{} tryUpgradeToLeader, currentTerm: {} received higher term: {} turn to follower_node", id, currentTerm, term);
        }
        if (leaderId.equals(-1)
                && state.equals(State.STATE_CANDIDATE)
                && voteGranted
                && currentTerm == term
        ) {
            int voteCounts = gatherVoteCount.incrementAndGet();
            int majority = (Registry.getServerCount() / 2) + 1;
            if (voteCounts >= majority) {
                log.info("[raft-node]:{} got voteCount: {}/{} become leader!", id, voteCounts, majority);
                leaderId = id;
                votedFor = -1;
                gatherVoteCount.set(0);
                state = State.STATE_LEADER;
                return true;
            }
        }
        return false;
    }

    private void stepDown(Integer term) {
        state = State.STATE_FOLLOWER;
        leaderId = -1;
        votedFor = -1;
        currentTerm = term;
        gatherVoteCount.set(0);
    }

    public void handleAppendEntries(Integer fromLeaderId, Integer term, List<Object> entries) {
        long currentTimeMillis = System.currentTimeMillis();
        heartbeatTimestamp = currentTimeMillis;
        electionTimestamp = currentTimeMillis;
        if (state.isActive() && term >= currentTerm) {
            state = State.STATE_FOLLOWER;
            leaderId = fromLeaderId;
            currentTerm = term;
            votedFor = -1;
        }
        if (null == entries || entries.isEmpty()) {
            log.info("[raft-node]:{} received heart-beat from leaderId: {}", id, leaderId);
        } else {
            log.info("[raft-node]:{} received appendEntries from leaderId: {}", id, leaderId);
            handleLogAppend();
        }
    }

    public void handleLogAppend() {
        // TODO handleLogAppend logic
    }

}
