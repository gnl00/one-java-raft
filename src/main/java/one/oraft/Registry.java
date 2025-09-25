package one.oraft;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Registry {

    private final static Map<Integer, RaftServer> REGISTRY_CENTER = new ConcurrentHashMap<>();

    private static final AtomicInteger NODE_ID_GENERATOR = new AtomicInteger(0);

    public static Integer getNextNodeId() {
        return NODE_ID_GENERATOR.incrementAndGet();
    }

    public static void doRegistry(Integer nodeId, RaftServer serverNode) {
        log.info("[registry] registered nodeId: {}", nodeId);
        REGISTRY_CENTER.put(nodeId, serverNode);
    }

    public static Map<Integer, RaftServer> discoveryMap() {
        return REGISTRY_CENTER;
    }

    public static List<RaftServer> discovery() {
        return REGISTRY_CENTER.values().stream().toList();
    }

    public static int getServerCount() {
        return REGISTRY_CENTER.size();
    }

}
