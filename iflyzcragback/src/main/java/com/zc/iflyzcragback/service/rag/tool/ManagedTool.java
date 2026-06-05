package com.zc.iflyzcragback.service.rag.tool;

public interface ManagedTool {

    String name();

    String displayName();

    String description();

    default boolean available() {
        return true;
    }

    default Object toolInstance() {
        return this;
    }
}
