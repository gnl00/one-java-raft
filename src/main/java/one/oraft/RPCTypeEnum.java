package one.oraft;

import lombok.Getter;

public enum RPCTypeEnum {
    RequestVote("RequestVote"),
    VoteGranted("VoteGranted"),
    AppendEntries("AppendEntries"),
    Unknown("Unknown");

    @Getter
    private final String type;

    RPCTypeEnum(String type) {
        this.type = type;
    }

    public static RPCTypeEnum getByType(String type) {
        for (RPCTypeEnum value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return Unknown;
    }
}
