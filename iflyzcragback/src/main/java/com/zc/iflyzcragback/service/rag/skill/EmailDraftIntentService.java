package com.zc.iflyzcragback.service.rag.skill;

import java.util.Map;
import java.util.Optional;

public interface EmailDraftIntentService {
    Optional<EmailDraftIntent> detect(String input, String currentStep, Map<String, Object> state);
}
