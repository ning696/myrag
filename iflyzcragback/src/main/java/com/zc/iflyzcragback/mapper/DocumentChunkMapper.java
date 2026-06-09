package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.dto.BM25Hit;
import com.zc.iflyzcragback.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
/**
 * 文档 chunk Mapper。
 *
 * <p>除 MyBatis-Plus 的基础 CRUD 外，这里还定义了 BM25 全文检索 SQL，
 * 用于混合检索中的关键词召回。</p>
 */
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    /**
     * BM25 全文检索（MySQL FULLTEXT + ngram parser）。仅返回属于 userId 且未删除的 chunk。
     * JOIN documents 表带回 documentName，用于混合检索后的 prompt [来源] 渲染与 citations 回显。
     */
    @Select("""
            SELECT c.id, c.document_id AS documentId, c.user_id AS userId,
                   c.chunk_index AS chunkIndex, c.content, c.title, c.keywords,
                   c.summary, c.vector_id AS vectorId, c.created_at AS createdAt, c.deleted,
                   d.filename AS documentName,
                   MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) AS bm25Score
            FROM document_chunks c
            LEFT JOIN documents d ON d.id = c.document_id
            WHERE c.user_id = #{userId} AND c.deleted = 0
              AND MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE)
            ORDER BY bm25Score DESC
            LIMIT #{limit}
            """)
    List<BM25Hit> bm25Search(@Param("q") String q,
                             @Param("userId") Long userId,
                             @Param("limit") int limit);

    /**
     * 查询某个命中 chunk 前后的上下文窗口。仅返回当前用户自己的未删除 chunk。
     */
    @Select("""
            SELECT c.id, c.document_id AS documentId, c.user_id AS userId,
                   c.chunk_index AS chunkIndex, c.content, c.title, c.keywords,
                   c.summary, c.vector_id AS vectorId, c.created_at AS createdAt, c.deleted,
                   d.filename AS documentName,
                   0.0 AS bm25Score
            FROM document_chunks c
            LEFT JOIN documents d ON d.id = c.document_id
            WHERE c.user_id = #{userId} AND c.deleted = 0
              AND c.document_id = #{documentId}
              AND c.chunk_index BETWEEN #{startIndex} AND #{endIndex}
            ORDER BY c.chunk_index ASC
            """)
    List<BM25Hit> selectWindowChunks(@Param("userId") Long userId,
                                     @Param("documentId") Long documentId,
                                     @Param("startIndex") int startIndex,
                                     @Param("endIndex") int endIndex);
}
