package one.oraft;

import java.util.Map;

public interface BaseServer {
    /**
     * Invoke by candidate to gather votes
     *
     * @param term candidate's term
     * @param candidateId candidate requesting vote
     * @param lastLogIndex index of candidate's last log entry
     * @param lastLogTerm term of candidate's last log entry
     *
     * @return term currentTerm for candidate to update itself
     * @return voteGranted true means candidate received vote
     */
    void requestVote(Map<String, Object> request);

    /**
     * 追加日志条目 AppendEntries
     * */
    void appendEntries();
}
