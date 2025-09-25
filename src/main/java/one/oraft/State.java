package one.oraft;

public enum State {
    STATE_LEADER,
    STATE_CANDIDATE,
    STATE_FOLLOWER,
    STATE_ERROR,
    STATE_UNINITIALIZED,
    STATE_SHUTTING,
    STATE_SHUTDOWN,
    ;

    // 该方法判断当前节点是否处于活跃状态
    public boolean isActive() {
        // 原理很简单，就是判断当前状态的枚举对象是否小于STATE_ERROR的值
        // ordinal方法用于返回一个int，排在前面的枚举对象的这个int值小于排在后面的
        // 只要是小于STATE_ERROR，就意味着当前节点还在正常工作，大于STATE_ERROR，当前节点不是出错，就是要停止工作了
        return this.ordinal() < STATE_ERROR.ordinal();
    }
}
