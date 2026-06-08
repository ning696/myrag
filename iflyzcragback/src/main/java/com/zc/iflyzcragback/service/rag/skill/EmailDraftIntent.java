package com.zc.iflyzcragback.service.rag.skill;

public record EmailDraftIntent(EmailDraftTarget target, String brief, double confidence) {
    public boolean requestsSubjectDraft() {
        return target == EmailDraftTarget.SUBJECT || target == EmailDraftTarget.BOTH;
    }

    public boolean requestsContentDraft() {
        return target == EmailDraftTarget.CONTENT || target == EmailDraftTarget.BOTH;
    }
}
