package com.zc.iflyzcragback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.dto.CitationVO;
import com.zc.iflyzcragback.dto.MessageVO;
import com.zc.iflyzcragback.dto.SessionVO;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import com.zc.iflyzcragback.entity.ChatSessionEntity;
import com.zc.iflyzcragback.mapper.ChatMessageMapper;
import com.zc.iflyzcragback.mapper.ChatSessionMapper;
import com.zc.iflyzcragback.service.rag.tool.WebSearchSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 会话业务服务。
 *
 * <p>会话用于把多轮消息组织在一起。RAG 路由和回答时会读取最近历史，
 * 所以会话不仅是前端展示结构，也会影响上下文理解。</p>
 */
public class SessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建新会话。
     */
    public SessionVO create(Long userId, String title) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setSessionId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTitle(title == null || title.isBlank() ? "新对话" : title);
        entity.setStatus("active");
        sessionMapper.insert(entity);
        log.info("会话创建: sessionId={}, userId={}", entity.getSessionId(), userId);
        return toVO(entity);
    }

    /**
     * 查询当前用户的会话列表。
     */
    public List<SessionVO> list(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, userId)
                        .orderByDesc(ChatSessionEntity::getUpdatedAt))
                .stream().map(this::toVO).toList();
    }

    /**
     * 删除会话及其消息。
     */
    public void delete(String sessionId, Long userId) {
        ChatSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BizException("会话不存在");
        }
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        log.info("会话删除: sessionId={}", sessionId);
    }

    /**
     * 修改会话标题。
     */
    public void rename(String sessionId, Long userId, String newTitle) {
        ChatSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BizException("会话不存在");
        }
        entity.setTitle(newTitle);
        sessionMapper.updateById(entity);
    }

    /**
     * 查询指定会话下的全部消息。
     */
    public List<MessageVO> getMessages(String sessionId, Long userId) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BizException("会话不存在");
        }
        return messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt))
                .stream().map(this::toMessageVO).toList();
    }

    /**
     * 数据库会话对象转前端展示对象。
     */
    private SessionVO toVO(ChatSessionEntity entity) {
        SessionVO vo = new SessionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 数据库消息对象转前端展示对象。
     */
    private MessageVO toMessageVO(ChatMessageEntity entity) {
        return MessageVO.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .content(entity.getContent())
                .citations(parseSourceDocuments(entity.getSourceDocuments()))
                .confidence(entity.getConfidence())
                .answerMode(entity.getAnswerMode())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<CitationVO> parseSourceDocuments(String sourceDocuments) {
        if (sourceDocuments == null || sourceDocuments.isBlank()) {
            return List.of();
        }
        try {
            List<WebSearchSource> sources = objectMapper.readValue(
                    sourceDocuments, new TypeReference<List<WebSearchSource>>() {});
            List<CitationVO> citations = new java.util.ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                WebSearchSource source = sources.get(i);
                CitationVO citation = new CitationVO();
                citation.setN(i + 1);
                citation.setSourceType("web");
                citation.setTitle(source.getTitle());
                citation.setDocumentName(source.getTitle());
                citation.setUrl(source.getUrl());
                citation.setContent(source.getContent());
                citation.setScore(source.getScore());
                citation.setPublishedDate(source.getPublishedDate());
                citations.add(citation);
            }
            return citations;
        } catch (Exception e) {
            log.debug("Failed to parse message source documents for citations", e);
            return List.of();
        }
    }
}
