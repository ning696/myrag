package com.zc.iflyzcragback.service.rag;

/**
 * 查询路由结果。
 *
 * <p>这是 QueryRouter 对用户问题的初步判断，后续 RagOrchestrator 会结合检索分数再做最终决策。</p>
 */
public enum QueryRoute {
    /** 问候、感谢、助手能力说明等普通聊天。 */
    CHAT,
    /** 需要查用户上传的知识库文档。 */
    KB_QA,
    /** 需要联网搜索实时或最新公开信息。 */
    WEB_SEARCH,
    /** 需要当前价格、天气、新闻等实时外部数据。 */
    REALTIME_UNAVAILABLE,
    /** 模型无法明确判断问题类型。 */
    UNCLEAR
}
