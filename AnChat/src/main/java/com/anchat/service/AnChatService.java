package com.anchat.service;

import com.anchat.pojo.Result;
import com.anchat.pojo.anChat.AnChatContent;
import com.anchat.pojo.anChat.AnChatModel;

import java.util.List;

public interface AnChatService {
    Result<AnChatContent> sendMessage(Integer id, String content, Integer modelId);

    List<AnChatModel> getAllModel();

    List<AnChatContent> getHistoryChat();

    Result<AnChatContent> reloadChat(Integer id);

    Result<Void> updateModelDescription(Integer modelId, String description, String rules);

    Result<Void> deleteModel(Integer modelId);

    Result<Void> addModel(String name, String description, String rules);

    Result<Void> deleteChat(Integer conversationId, Integer msgIndex);

    Result<Void> editChat(Integer conversationId, Integer msgIndex, String newContent);

    Result<Void> updatePreview(Integer conversationId, String preview);

    Result<AnChatContent> branchChat(Integer conversationId, Integer msgIndex);

    Result<Void> toggleStar(Integer conversationId);

    List<AnChatContent> getStarredChat();

    Result<Void> saveOption(String key, String value);

    Result<Void> saveOptions(java.util.Map<String, String> options);

    Result<Void> reloadConstants();
//    SseEmitter sendMessageStream(Integer id, String content);
}
