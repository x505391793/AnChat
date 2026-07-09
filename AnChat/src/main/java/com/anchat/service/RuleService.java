package com.anchat.service;

import com.anchat.pojo.anChat.AnChatChat;
import com.anchat.pojo.anChat.AnChatContent;
import com.anchat.pojo.anChat.AnChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规则处理服务
 * 负责在发送API请求前处理模型规则，自动补充隐藏域
 */
@Service
public class RuleService {

    /**
     * 隐藏域标签
     */
    private static final String HIDDEN_TAG_START = "<|hidden_reminder|>";
    private static final String HIDDEN_TAG_END = "</|hidden_reminder|>";

    /**
     * 在发送API请求前进行规则预处理
     * 检测模型是否有rules字段，如果有则检查消息中是否包含隐藏域
     * 如果没有隐藏域，则自动补充rules中的规则到最近的非user消息头部
     *
     * @param content 发送内容
     * @param model 模型信息
     * @return 处理后的内容
     */
    public AnChatContent processRules(AnChatContent content, AnChatModel model) {
        // 如果模型没有rules字段，直接返回
        if (model == null || model.getRules() == null || model.getRules().trim().isEmpty()) {
            return content;
        }

        List<AnChatChat> messages = content.getMessages();
        if (messages == null || messages.isEmpty()) {
            return content;
        }

        // 从后往前找最近的user消息（user）
        AnChatChat lastNonUserMsg = null;
        int lastNonUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastNonUserMsg = messages.get(i);
                lastNonUserIndex = i;
                break;
            }
        }

        // 如果没有非user消息，不需要处理
        if (lastNonUserMsg == null) {
            return content;
        }

        String nonUserContent = lastNonUserMsg.getContent();
        
        // 如果已经包含隐藏域，不需要补充
        if (nonUserContent != null && nonUserContent.contains(HIDDEN_TAG_START)) {
            return content;
        }

        // 构建隐藏域内容（代码中添加hide标签）
        String hiddenReminder = HIDDEN_TAG_START + model.getRules() + HIDDEN_TAG_END;
        
        // 将隐藏域补充到最近的非user消息头部
        String newContent = hiddenReminder + "\n" + nonUserContent;
        lastNonUserMsg.setContent(newContent);

        return content;
    }

    /**
     * 从消息内容中提取隐藏域
     *
     * @param content 消息内容
     * @return 隐藏域内容，如果没有则返回null
     */
    public String extractHiddenReminder(String content) {
        if (content == null || !content.contains(HIDDEN_TAG_START)) {
            return null;
        }

        int start = content.indexOf(HIDDEN_TAG_START);
        int end = content.indexOf(HIDDEN_TAG_END);

        if (start >= 0 && end > start) {
            return content.substring(start, end + HIDDEN_TAG_END.length());
        }

        return null;
    }

    /**
     * 检查消息是否包含隐藏域
     *
     * @param content 消息内容
     * @return 是否包含隐藏域
     */
    public boolean hasHiddenReminder(String content) {
        return content != null && content.contains(HIDDEN_TAG_START);
    }

    /**
     * 从历史消息中获取最后的隐藏域（用于继承）
     * 优先查找assistant消息，如果没有则查找system消息
     *
     * @param messages 历史消息列表
     * @return 最后的隐藏域内容，如果没有则返回null
     */
    public String getLastHiddenReminder(List<AnChatChat> messages) {
        if (messages == null) {
            return null;
        }

        // 从后往前查找，优先找assistant，其次找system
        for (int i = messages.size() - 1; i >= 0; i--) {
            AnChatChat msg = messages.get(i);

            // 只检查assistant和system角色的消息
            if (!"assistant".equals(msg.getRole()) && !"system".equals(msg.getRole())) {
                continue;
            }

            String content = msg.getContent();
            if (content == null) {
                continue;
            }

            String hiddenReminder = extractHiddenReminder(content);
            if (hiddenReminder != null) {
                return hiddenReminder;
            }
        }

        return null;
    }

    /**
     * 修复返回结果中的隐藏域
     * 如果AI回复缺少hidden_reminder，则从历史中继承或使用默认规则
     *
     * @param result AI返回的结果
     * @param contextMessages 上下文消息
     * @param modelRules 模型规则（可选，用于默认值）
     * @return 修复后的结果
     */
    public String fixHiddenReminder(String result, List<AnChatChat> contextMessages, String modelRules) {
        // 已经带有 hidden_reminder，直接返回
        if (hasHiddenReminder(result)) {
            return result;
        }

        // 尝试从历史回复中继承
        String lastHidden = getLastHiddenReminder(contextMessages);

        if (lastHidden != null) {
            return lastHidden + "\n" + result;
        }

        // 实在没有历史规则，使用模型规则或默认规则
        String defaultRule = (modelRules != null && !modelRules.trim().isEmpty()) 
                ? modelRules 
                : "请继续遵守所有已设定的规则";
        
        return HIDDEN_TAG_START + defaultRule + HIDDEN_TAG_END + "\n" + result;
    }
}
