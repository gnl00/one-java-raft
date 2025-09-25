# oraft

> a raft based distributed consensus system

1、3个节点，使用RaftServer包装Node作为一个服务；
2、~~每一个服务都是一个 NettyServer~~每一个RaftServer都需要包含一个 NettyClient 和一个 NettyServer。作为 client 用来给别的 node 发送 RPC 请求，获取投票；作为 server 接受来自别的 node 的请求，并返回结果。
3、启动的时候每个 Node 都是 state_follower 状态
4、启动时，每个 Node 都会生成一个随机的 id，并生成一个随机的 term，并生成一个空的日志列表

## Improvement

- netty server and client poolize

## Reference

- https://zhuanlan.zhihu.com/p/91288179