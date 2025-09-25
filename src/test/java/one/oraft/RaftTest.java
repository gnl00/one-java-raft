package one.oraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaftTest {

    /**
     * 启动 raft 集群
     */
    @Test
    public void testRaftServers() {
        new RaftServer(6001, "localhost").start();
        new RaftServer(6002, "localhost").start();
        new RaftServer(6003, "localhost").start();
    }

}