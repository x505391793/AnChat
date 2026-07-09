package com.anchat.dao;

import com.anchat.pojo.anChat.AnChatContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnChatDao extends JpaRepository<AnChatContent, Integer> {
    // 根据会话 ID 和切片序号查一个切片
    AnChatContent findTopByConversationIdOrderBySplitIndexIdDesc(Integer conversationId);

    // 查某个会话的所有切片，按 index 升序
    List<AnChatContent> findByConversationIdOrderBySplitIndexIdAsc(Integer conversationId);

    // 取每个会话的最新一条切片（用于历史列表），modelName 取首个切片（原始模型）
    @Query("SELECT NEW com.anchat.pojo.anChat.AnChatContent(" +
            "d.conversationId, d.preview, f.modelName, d.updateTime, d.isStar) " +
            "FROM AnChatContent d, AnChatContent f " +
            "WHERE d.id IN " +
            "(SELECT d2.id FROM AnChatContent d2 WHERE d2.conversationId IS NOT NULL " +
            "AND d2.messageStr IS NOT NULL AND d2.messageStr <> '' " +
            "AND d2.updateTime = (SELECT MAX(d3.updateTime) FROM AnChatContent d3 " +
            "WHERE d3.conversationId = d2.conversationId " +
            "AND d3.messageStr IS NOT NULL AND d3.messageStr <> '')) " +
            "AND f.conversationId = d.conversationId AND f.splitIndexId = 0 " +
            "ORDER BY d.updateTime DESC")
    List<AnChatContent> findLatestSlicePerConversation();


    AnChatContent findTopByConversationIdAndSummaryIsNotNullOrderBySplitIndexIdDesc(Integer conversationId);

    List<AnChatContent> findByConversationIdAndSplitIndexIdGreaterThanOrderBySplitIndexIdAsc(Integer conversationId, Integer splitIndexId);

    // 以下用于 generateSummary 内部（已有也可）
    List<AnChatContent> findByConversationIdAndSplitIndexIdLessThanOrderBySplitIndexIdDesc(Integer conversationId, Integer splitIndexId);

    // 删除指定切片之后的所有切片
    void deleteByConversationIdAndSplitIndexIdGreaterThan(Integer conversationId, Integer splitIndexId);

    // 查询某个会话的第一个切片（splitIndexId=0）
    AnChatContent findByConversationIdAndSplitIndexId(Integer conversationId, Integer splitIndexId);

    // 查询所有已收藏的主切片（splitIndexId=0 且 isStar=true），按更新时间降序
    @Query("SELECT d FROM AnChatContent d WHERE d.splitIndexId = 0 AND d.isStar = true ORDER BY d.updateTime DESC")
    List<AnChatContent> findStarredConversations();
}
