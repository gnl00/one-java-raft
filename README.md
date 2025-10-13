# one-java-raft

A Raft-based distributed consensus system implementation in Java, providing leader election and log replication capabilities for building distributed systems.

## Overview

This project implements the Raft consensus algorithm, a protocol designed to be easy to understand and implement while providing the same guarantees as Paxos. The implementation uses Netty for high-performance network communication and supports multi-node cluster deployment.

## Features

- **Leader Election**: Automatic leader election with randomized timeouts to prevent vote splitting
- **Heartbeat Mechanism**: Regular heartbeats from leader to maintain cluster stability
- **State Management**: Clean state transitions between Follower, Candidate, and Leader states
- **RPC Communication**: Netty-based asynchronous RPC with proper message framing
- **Connection Management**: Efficient NettyClient connection lifecycle management
- **Fault Tolerance**: Automatic failover and re-election when leader becomes unavailable

## Architecture

### Core Components

- **RaftNode** (`one.oraft.RaftNode`): Manages node state and handles state transitions
  - Tracks current term, voted candidates, and vote counts
  - Implements timeout checking and election logic
  - Handles heartbeat and log entry processing

- **RaftServer** (`one.oraft.RaftServer`): Encapsulates network operations for each node
  - Manages both NettyClient and NettyServer instances
  - Handles RPC message routing (RequestVote, VoteGranted, AppendEntries)
  - Coordinates leader election and heartbeat broadcasting

- **NettyClient/NettyServer**: Network communication layer
  - Uses DelimiterBasedFrameDecoder for message framing
  - Supports asynchronous message sending and receiving
  - Manages connection lifecycle and resource cleanup

### Node States

- **STATE_FOLLOWER**: Initial state, receives heartbeats from leader
- **STATE_CANDIDATE**: Actively requesting votes during election
- **STATE_LEADER**: Elected leader, sends heartbeats to followers
- **STATE_SHUTDOWN**: Node is shutting down

### RPC Types

- **RequestVote**: Sent by candidates to request votes during election
- **VoteGranted**: Response from followers indicating vote decision
- **AppendEntries**: Sent by leader for log replication and heartbeats

## Technical Stack

- **Java**: 21
- **Build Tool**: Maven
- **Network**: Netty 4.2.2.Final
- **Serialization**: Fastjson2 2.0.56
- **Logging**: Log4j 2.24.3 with SLF4J
- **Utilities**: Lombok 1.18.38, Apache Commons Lang3 3.17.0

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6+

### Build

```bash
mvn clean package
```

### Run

Start a 5-node Raft cluster:

```java
public class ORaftApp {
    public static void main(String[] args) {
        new RaftServer(6001, "localhost").start();
        new RaftServer(6002, "localhost").start();
        new RaftServer(6003, "localhost").start();
        new RaftServer(6004, "localhost").start();
        new RaftServer(6005, "localhost").start();
    }
}
```

## Implementation Details

### Leader Election

1. All nodes start as followers with random election timeouts (3-13 seconds)
2. When a follower's election timeout expires without receiving heartbeat:
   - Transitions to candidate state
   - Increments term and votes for itself
   - Sends RequestVote RPCs to all other nodes
3. A candidate becomes leader when receiving votes from majority of nodes
4. Each node can only vote once per term

### Timeout Mechanism

- **Election Timeout**: 15 seconds (with randomization to prevent vote splitting)
- **Leader Timeout**: 15 seconds (follower expects heartbeat within this period)
- **Heartbeat Interval**: 5 seconds (leader sends heartbeats to maintain authority)

### Connection Management

The implementation maintains a connection map for each RaftServer:
- Reuses existing connections when available
- Creates new connections on-demand with CompletableFuture callbacks
- Properly cleans up resources on node shutdown

## Recent Improvements

Based on git commit history:

- Refined leader election logic for better stability
- Implemented DelimiterBasedFrameDecoder for proper RPC message splitting
- Improved NettyClient connection management and resource lifecycle
- Enhanced error handling and logging

## Roadmap

- [x] Leader Election
- [ ] Log Replication
- [ ] Cluster Membership Changes
- [ ] Log Compaction
- [ ] Performance Optimization

## Project Structure

```
src/main/java/one/oraft/
├── RaftNode.java              # Node state management
├── RaftServer.java            # Server-side RPC handling
├── State.java                 # Node state enum
├── RPCTypeEnum.java           # RPC message types
├── Registry.java              # Node registry
├── RpcMessageBuilder.java     # RPC message construction
├── client/
│   ├── NettyClient.java       # Client-side networking
│   └── ClientChannelHandler.java
├── server/
│   ├── NettyServer.java       # Server-side networking
│   └── ServerChannelHandler.java
└── test/
    └── ORaftApp.java          # Demo application
```

## References

### Raft Algorithm

- [The Raft Consensus Algorithm](https://raft.github.io/)
- [Raft Paper](https://raft.github.io/raft.pdf)

### Implementation Resources

- [分布式系统的一致性与共识算法-Paxos](https://chinalhr.github.io/post/distributed-systems-consensus-algorithm-paxos/)
- [Raft 实现细节](https://www.cnblogs.com/xiaoxiongcanguan/p/17569697.html)
- [Raft 算法详解](https://zhuanlan.zhihu.com/p/91288179)

## License

This project is for educational and research purposes.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.