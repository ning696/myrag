package com.zc.iflyzcragback.service.rag;

/**
 * 回答模式。
 *
 * <p>前端和日志可以通过这个枚举知道本次回答到底走了哪条链路。</p>
 */
public enum AnswerMode {
    /** 普通聊天，不使用知识库。 */
    CHAT,
    /** 知识库命中资料，并基于检索片段回答。 */
    RAG_ANSWER,
    /** 知识库没有命中可靠依据。 */
    NO_KB_HIT,
    /** 用户需要实时外部数据，但当前系统未接入实时查询能力。 */
    REALTIME_UNAVAILABLE
}
