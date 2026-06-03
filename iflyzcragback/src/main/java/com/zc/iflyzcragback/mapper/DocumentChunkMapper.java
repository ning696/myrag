package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    /**
     * BM25 全文检索（MySQL FULLTEXT + ngram parser）。仅返回属于 userId 且未删除的 chunk。
     * P1 阶段启用；P0 不调用。
     */
    @Select("""
            SELECT id, document_id AS documentId, user_id AS userId, chunk_index AS chunkIndex,
                   content, title, keywords, summary, vector_id AS vectorId,
                   created_at AS createdAt, deleted,
                   MATCH(content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) AS score
            FROM document_chunks
            WHERE user_id = #{userId} AND deleted = 0
              AND MATCH(content) AGAINST(#{q} IN NATURAL LANGUAGE MODE)
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<DocumentChunkEntity> bm25Search(@Param("q") String q,
                                         @Param("userId") Long userId,
                                         @Param("limit") int limit);
}
