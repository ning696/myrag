package com.zc.iflyzcragback.service.rag.skill;

import java.util.Map;
import java.util.Optional;

public interface EmailDraftService {
    Optional<String> draftSubject(String brief, Map<String, Object> state);

    Optional<String> draftContent(String brief, Map<String, Object> state);
}
