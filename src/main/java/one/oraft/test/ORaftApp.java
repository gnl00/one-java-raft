package one.oraft.test;

import one.oraft.RaftServer;

public class ORaftApp {
    public static void main(String[] args) {
        // 启动 raft 集群
        new RaftServer(6001, "localhost").start();
        new RaftServer(6002, "localhost").start();
        new RaftServer(6003, "localhost").start();
        new RaftServer(6004, "localhost").start();
        new RaftServer(6005, "localhost").start();
    }
}
