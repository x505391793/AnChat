package com.anchat.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import com.anchat.common.AnChatConstants;
import com.anchat.dao.AnChatDao;
import com.anchat.dao.AnChatHistoryDao;
import com.anchat.dao.AnChatModelDao;
import com.anchat.pojo.Result;
import com.anchat.pojo.anChat.*;
import com.anchat.service.ConfigOptionService;
import com.anchat.service.AnChatService;
import com.anchat.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnChatServiceImpl implements AnChatService {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = System.getenv("ANCHAT_API_KEY");

    @Autowired
    private AnChatDao anChatDao;

    @Autowired
    private AnChatModelDao anChatModelDao;

    @Autowired
    private AnChatHistoryDao anChatHistoryDao;

    @Autowired
    private ConfigOptionService configOptionService;

    @Autowired
    private AnChatConstants anChatConstants;

    @Autowired
    private RuleService ruleService;

    // ==================== 工具方法 ====================

    private int calcTotalChars(List<AnChatChat> messages) {
        return messages.stream().mapToInt(m -> m.getContent().length()).sum();
    }

    private List<AnChatChat> deepCopyMessages(List<AnChatChat> src) {
        return src.stream()
                .map(m -> new AnChatChat(m.getContent(), m.getRole()))
                .collect(Collectors.toList());
    }

    private List<AnChatChat> parseMessages(String messageStr) {
        if (messageStr == null || messageStr.isEmpty()) {
            return new ArrayList<>();
        }
        return JSONObject.parseObject(messageStr, new TypeReference<List<AnChatChat>>() {
        });
    }

    // ==================== 业务方法（sendMessage、saveOrSplitSlice 等保持原样） ====================

    public AnChatModel setRobot(int id) {
        Optional<AnChatModel> byId = anChatModelDao.findById(id);
        return byId.orElse(null);
    }

    @Override
    public Result<AnChatContent> sendMessage(Integer id, String content, Integer modelId) {
        AnChatContent currentSlice;
        List<AnChatChat> oldMessagesCopy = null;
        AnChatModel model = null;

        if (id != null) {
            currentSlice = anChatDao.findTopByConversationIdOrderBySplitIndexIdDesc(id);
            if (currentSlice == null) {
                return Result.fail("会话不存在");
            }
            currentSlice.setMessages(parseMessages(currentSlice.getMessageStr()));
            oldMessagesCopy = deepCopyMessages(currentSlice.getMessages());
            currentSlice.addChat(content, "user");

            // 获取模型信息用于规则处理
            if (currentSlice.getModelName() != null) {
                List<AnChatModel> models = anChatModelDao.findAll();
                for (AnChatModel m : models) {
                    if (m.getName().equals(currentSlice.getModelName())) {
                        model = m;
                        break;
                    }
                }
            }
        } else {
            currentSlice = new AnChatContent();
            if (modelId != null) {
                model = setRobot(modelId);
                currentSlice.setModelName(model.getName());

                // 新建会话时，如果有模型描述，先添加system消息
                if (model.getDescription() != null && !model.getDescription().trim().isEmpty()) {
                    currentSlice.addChat(model.getDescription(), "system");
                }
            } else {
                currentSlice.setModelName("自定义");
            }
            currentSlice.addChat(content, "user");

            String preview = content.length() > 50 ? content.substring(0, 50) : content;
            currentSlice.setPreview(preview);
        }

        // 发送（内部会构建压缩上下文并追加助手回复）
        try {
            sendApiMessage(currentSlice, model);
        } catch (Exception e) {
            log.error("API 调用失败", e);
            return Result.fail();
        }

        // 保存切片（可能拆分）
        currentSlice = saveOrSplitSlice(currentSlice, oldMessagesCopy, id == null);
        return Result.success(currentSlice);
    }

    /**
     * 发送 API 的封装方法 —— 现在包含构建压缩上下文、实际发送、结果追加
     */
    private void sendApiMessage(AnChatContent currentSlice, AnChatModel model) {
        Integer conversationId = currentSlice.getConversationId();
        // 新会话未持久化时 conversationId 为 null 或 -1，直接用当前切片发送（此时切片内容就是全部消息）
        if (conversationId == null || conversationId <= 0) {
            // 新会话也需要规则处理
            currentSlice = ruleService.processRules(currentSlice, model);
            sendHttp(currentSlice, model);
            return;
        }

        // 构建压缩上下文（包含内存中最新的 user 消息）
        AnChatContent sendContext = buildSendContext(conversationId, currentSlice);

        // 在发送前进行规则预处理（此时所有消息包括system都已加载）
        sendContext = ruleService.processRules(sendContext, model);

        sendHttp(sendContext, model);   // 回复被添加到 sendContext 中

        // 提取助手回复并注入到 currentSlice
        String assistantReply = null;
        List<AnChatChat> ctxMsgs = sendContext.getMessages();
        if (ctxMsgs != null) {
            for (int i = ctxMsgs.size() - 1; i >= 0; i--) {
                if ("assistant".equals(ctxMsgs.get(i).getRole())) {
                    assistantReply = ctxMsgs.get(i).getContent();
                    break;
                }
            }
        }
        if (assistantReply == null) throw new RuntimeException("未获取到助手回复");
        currentSlice.addChat(assistantReply, "assistant");
        currentSlice.setContent(assistantReply);
    }


    private AnChatContent buildSendContext(Integer conversationId, AnChatContent currentSlice) {
        int maxSliceChars = AnChatConstants.maxSliceChars;
        int minContextTurns = AnChatConstants.minContextTurns;
        // 1. 获取所有切片（按 index 升序）
        List<AnChatContent> allSlices = anChatDao.findByConversationIdOrderBySplitIndexIdAsc(conversationId);
        if (allSlices.isEmpty()) {
            AnChatContent temp = new AnChatContent();
            temp.setMessages(new ArrayList<>(currentSlice.getMessages()));
            copyParams(temp, currentSlice);
            return temp;
        }

        // 2. 提取系统消息（第一个切片中的 system 角色）
        AnChatContent firstSlice = allSlices.get(0);
        List<AnChatChat> firstMsgs = parseMessages(firstSlice.getMessageStr());
        List<AnChatChat> systemMsgs = firstMsgs.stream()
                .filter(m -> "system".equals(m.getRole()))
                .collect(Collectors.toList());

        // 3. 最新摘要
        AnChatContent summarySlice = anChatDao
                .findTopByConversationIdAndSummaryIsNotNullOrderBySplitIndexIdDesc(conversationId);
        String latestSummary = (summarySlice != null) ? summarySlice.getSummary() : null;
        int summaryIndex = (summarySlice != null) ? summarySlice.getSplitIndexId() : -1;

        // 4. 收集历史消息：跳过 summaryIndex 之前的，且跳过 currentSlice 对应的旧版本（用内存中的替换）
        int currentIndex = currentSlice.getSplitIndexId();
        List<AnChatChat> historyMessages = new ArrayList<>();
        for (AnChatContent slice : allSlices) {
            if (slice.getSplitIndexId() <= summaryIndex) continue;
            if (slice.getSplitIndexId() == currentIndex) continue;
            // 过滤掉 system 消息
            historyMessages.addAll(parseMessages(slice.getMessageStr()).stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .collect(Collectors.toList()));
        }
// 加上当前切片内存中的消息（包含刚添加的 user），并过滤 system
        historyMessages.addAll(currentSlice.getMessages().stream()
                .filter(m -> !"system".equals(m.getRole()))
                .collect(Collectors.toList()));

        // 5. 开始构建最终上下文
        List<AnChatChat> contextMsgs = new ArrayList<>();
        int currentLen = 0;

        // 系统消息
        for (AnChatChat sys : systemMsgs) {
            contextMsgs.add(new AnChatChat(sys.getContent(), "system"));
            currentLen += sys.getContent().length();
        }

        // 前情提要
        if (latestSummary != null && !latestSummary.isEmpty()) {
            String summaryText = "【前情提要】" + latestSummary;
            if (currentLen + summaryText.length() <= maxSliceChars) {
                contextMsgs.add(new AnChatChat(summaryText, "assistant"));
                currentLen += summaryText.length();
            }
        }

        // 6. 从后往前塞入 historyMessages，保证最后一条 user 在
        List<AnChatChat> selected = new ArrayList<>();
        boolean hasLastUser = false;
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            AnChatChat msg = historyMessages.get(i);
            int msgLen = msg.getContent().length();
            if (currentLen + msgLen <= maxSliceChars) {
                selected.add(0, msg);
                currentLen += msgLen;
                if ("user".equals(msg.getRole())) hasLastUser = true;
            } else {
                if (!hasLastUser && "user".equals(msg.getRole()) && selected.isEmpty()) {
                    selected.add(0, msg);
                }
                break;
            }
        }

        // 7. 若上下文对话轮次不足 minContextTurns，从摘要之前的切片补充
        //    排除 system 和最后一条 user（当前提问），计算对话消息数
        int contextTurns = 0;
        for (AnChatChat msg : selected) {
            if ("assistant".equals(msg.getRole())) {
                contextTurns++;
            }
        }
        // 排除最后一条 user（当前提问）
        if (!selected.isEmpty() && "user".equals(selected.get(selected.size() - 1).getRole())) {
            contextTurns--;
        }

        if (contextTurns < minContextTurns && summaryIndex >= 0) {
            log.info("上下文对话轮次 {} 不足 {}，从历史切片补充", contextTurns, minContextTurns);
            List<AnChatChat> prePadding = new ArrayList<>();
            int paddingLen = 0;
            // 从当前切片往前倒序取，覆盖 summaryIndex 到 currentIndex 之间的中间切片
            for (int s = currentIndex - 1; s >= 0 && contextTurns + countNonSystemMsgs(prePadding) < minContextTurns; s--) {
                // 跳过已在 historyMessages 中的切片（summaryIndex < index < currentIndex 的切片已被 step4 收集）
                if (s > summaryIndex && s < currentIndex) continue;
                AnChatContent preSlice = allSlices.get(s);
                List<AnChatChat> preMsgs = parseMessages(preSlice.getMessageStr());
                for (int m = preMsgs.size() - 1; m >= 0; m--) {
                    AnChatChat msg = preMsgs.get(m);
                    if (!"system".equals(msg.getRole()) && currentLen + paddingLen + msg.getContent().length() <= maxSliceChars) {
                        prePadding.add(0, msg);
                        paddingLen += msg.getContent().length();
                    }
                }
            }
            // 将补充的消息插入到 selected 前面
            selected.addAll(0, prePadding);
        }

        contextMsgs.addAll(selected);

        AnChatContent temp = new AnChatContent();
        temp.setMessages(contextMsgs);
        copyParams(temp, currentSlice);
        temp.setStream(false);
        return temp;
    }

    private int countNonSystemMsgs(List<AnChatChat> msgs) {
        int count = 0;
        for (AnChatChat msg : msgs) {
            if (!"system".equals(msg.getRole())) count++;
        }
        return count;
    }

    private void copyParams(AnChatContent dest, AnChatContent src) {
        dest.setModel(src.getModel());
        dest.setMax_tokens(src.getMax_tokens());
        dest.setTemperature(src.getTemperature());
//        dest.setFrequency_penalty(src.getFrequency_penalty());
//        dest.setPresence_penalty(src.getPresence_penalty());
    }

    // ==================== 保存切片（保持原样） ====================
    private AnChatContent saveOrSplitSlice(AnChatContent currentSlice,
                                             List<AnChatChat> oldMessagesCopy,
                                             boolean isNewConversation) {
        int maxSliceChars = AnChatConstants.maxSliceChars;
        List<AnChatChat> messages = currentSlice.getMessages();
        int totalChars = calcTotalChars(messages);

        if (isNewConversation) {
            currentSlice.setSplitIndexId(0);
            currentSlice.setConversationId(-1);
            persistSlice(currentSlice);
            currentSlice.setConversationId(currentSlice.getId());
            persistSlice(currentSlice);
            return currentSlice;
        }

        if (totalChars <= maxSliceChars) {
            persistSlice(currentSlice);
            return currentSlice;
        } else {
            // 超限：回退旧切片并生成摘要
            currentSlice.setMessages(oldMessagesCopy);
            // 修复旧切片的content，改为旧消息中最后一条assistant的内容
            String oldLastAssistant = null;
            for (int i = oldMessagesCopy.size() - 1; i >= 0; i--) {
                if ("assistant".equals(oldMessagesCopy.get(i).getRole())) {
                    oldLastAssistant = oldMessagesCopy.get(i).getContent();
                    break;
                }
            }
            currentSlice.setContent(oldLastAssistant);

            // 生成摘要
            String previousSummary = getPreviousSummary(currentSlice.getConversationId(), currentSlice.getSplitIndexId());
            String newSummary = generateSummary(previousSummary, oldMessagesCopy);
            if (newSummary != null && !newSummary.isEmpty()) {
                currentSlice.setSummary(newSummary);
            }
            persistSlice(currentSlice);

            // 新切片
            AnChatContent newSlice = new AnChatContent();
            newSlice.setConversationId(currentSlice.getConversationId());
            newSlice.setSplitIndexId(currentSlice.getSplitIndexId() + 1);
            newSlice.setModelName(currentSlice.getModelName());

            int size = messages.size();
            AnChatChat userMsg = messages.get(size - 2);
            AnChatChat assistantMsg = messages.get(size - 1);
            newSlice.addChat(userMsg.getContent(), userMsg.getRole());
            newSlice.addChat(assistantMsg.getContent(), assistantMsg.getRole());
            newSlice.setContent(assistantMsg.getContent());

            String preview = userMsg.getContent().length() > 50
                    ? userMsg.getContent().substring(0, 50)
                    : userMsg.getContent();
            newSlice.setPreview(preview);

            persistSlice(newSlice);
            return newSlice;
        }
    }

    private void persistSlice(AnChatContent slice) {
        slice.setMessageStr(JSONObject.toJSONString(slice.getMessages()));
        slice.setUpdateTime(new Date());
        anChatDao.save(slice);
    }

    private String getPreviousSummary(Integer conversationId, Integer currentIndex) {
        List<AnChatContent> prevSlices = anChatDao
                .findByConversationIdAndSplitIndexIdLessThanOrderBySplitIndexIdDesc(conversationId, currentIndex);
        for (AnChatContent s : prevSlices) {
            if (s.getSummary() != null && !s.getSummary().isEmpty()) return s.getSummary();
        }
        return null;
    }

    private String generateSummary(String previousSummary, List<AnChatChat> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("下面给你一个已有摘要,和新的故事情节。\n" +
                "请不要重新生成摘要。而是基于已有摘要进行更新。\n" +
                "要求：\n" +
                "保留人物状态\n" +
                "保留所有未解决伏笔\n" +
                "保留世界规则\n" +
                "删除重复剧情\n" +
                "删除已经结束的小事件\n" +
                "输出新的摘要。");
        if (previousSummary != null) prompt.append("已有前情提要：").append(previousSummary).append("\n");
        prompt.append("新增对话内容：\n");
        for (AnChatChat msg : messages) {
            prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        AnChatContent summaryRequest = new AnChatContent();
        summaryRequest.addChat(prompt.toString(), "system");
        summaryRequest.setMax_tokens(2000);
        summaryRequest.setTemperature(0.3f);
        summaryRequest.setStream(false);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        try {
            String body = summaryRequest.toString();
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            Response response = client.newCall(request).execute();
            String resp = response.body().string();

            // 保存摘要请求的完整记录
            AnChatHistory history = new AnChatHistory();
            history.setRequest(body);
            history.setResponse(resp);
            history.setTime(new Date());
            anChatHistoryDao.save(history);

            AnChatResponse respObj = JSONObject.parseObject(resp, AnChatResponse.class);
            return respObj.getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("生成摘要失败", e);
            return null;
        }
    }

    // ==================== 其他接口 ====================
    @Override
    public List<AnChatModel> getAllModel() {
        return anChatModelDao.findAll();
    }

    @Override
    public List<AnChatContent> getHistoryChat() {
        return anChatDao.findLatestSlicePerConversation();
    }

    @Override
    public Result<AnChatContent> reloadChat(Integer id) {
        List<AnChatContent> slices = anChatDao.findByConversationIdOrderBySplitIndexIdAsc(id);
        if (slices.isEmpty()) {
            return Result.fail("会话不存在");
        }

        List<AnChatChat> allMessages = new ArrayList<>();
        for (AnChatContent slice : slices) {
            List<AnChatChat> msgs = parseMessages(slice.getMessageStr());
            if (msgs != null) {
                allMessages.addAll(msgs);
            }
        }

        AnChatContent result = new AnChatContent();
        result.setId(slices.get(0).getConversationId());
        result.setModelName(slices.get(0).getModelName());
        result.setMessages(allMessages);
        result.setIsStar(slices.get(0).getIsStar());
        return Result.success(result);
    }

    private void sendHttp(AnChatContent anChatContent, AnChatModel model) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build();

        anChatContent.setStream(false);
        String sendBody = anChatContent.toString();

        try {
            RequestBody body = RequestBody.create(sendBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            // 保存请求和响应的完整记录
            AnChatHistory history = new AnChatHistory();
            history.setRequest(sendBody);
            history.setResponse(responseBody);
            history.setTime(new Date());
            anChatHistoryDao.save(history);

            AnChatResponse anChatResponse = JSONObject.parseObject(responseBody, AnChatResponse.class);
            String result = anChatResponse.getChoices().get(0).getMessage().getContent();

            // 使用RuleService修复隐藏域
            String modelRules = (model != null) ? model.getRules() : null;
//            result = ruleService.fixHiddenReminder(
//                    result,
//                    anChatContent.getMessages(),
//                    modelRules
//            );

            anChatContent.addChat(result, "assistant");
            anChatContent.setContent(result);
        } catch (IOException e) {
            log.error("请求 AnChat API 失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<Void> updateModelDescription(Integer modelId, String description, String rules) {
        try {
            Optional<AnChatModel> optional = anChatModelDao.findById(modelId);
            if (!optional.isPresent()) {
                return Result.fail("模型不存在");
            }

            AnChatModel model = optional.get();
            model.setDescription(description);
            if (rules != null) {
                model.setRules(rules);
            }
            anChatModelDao.save(model);

            return Result.success();
        } catch (Exception e) {
            log.error("更新模型失败", e);
            return Result.fail("更新失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Void> deleteModel(Integer modelId) {
        try {
            Optional<AnChatModel> optional = anChatModelDao.findById(modelId);
            if (!optional.isPresent()) {
                return Result.fail("模型不存在");
            }

            anChatModelDao.deleteById(modelId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除模型失败", e);
            return Result.fail("删除失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Void> addModel(String name, String description, String rules) {
        try {
            AnChatModel model = new AnChatModel();
            model.setName(name);
            model.setDescription(description);
            model.setRules(rules);
            anChatModelDao.save(model);
            return Result.success();
        } catch (Exception e) {
            log.error("新增模型失败", e);
            return Result.fail("新增失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Void> deleteChat(Integer conversationId, Integer msgIndex) {
        if (conversationId == null || msgIndex == null || msgIndex < 0) {
            return Result.fail("参数不合法");
        }
        List<AnChatContent> slices = anChatDao.findByConversationIdOrderBySplitIndexIdAsc(conversationId);
        if (slices.isEmpty()) {
            return Result.fail("会话不存在");
        }

        int globalIndex = 0;
        AnChatContent targetSlice = null;
        int localIndex = -1;

        for (AnChatContent slice : slices) {
            List<AnChatChat> msgs = parseMessages(slice.getMessageStr());
            int sliceMsgCount = (msgs != null) ? msgs.size() : 0;
            if (msgIndex < globalIndex + sliceMsgCount) {
                targetSlice = slice;
                localIndex = msgIndex - globalIndex;
                break;
            }
            globalIndex += sliceMsgCount;
        }

        if (targetSlice == null) {
            return Result.fail("消息索引超出范围");
        }

        // 截断当前切片：只保留 localIndex 之前的消息
        List<AnChatChat> msgs = parseMessages(targetSlice.getMessageStr());
        if (localIndex > 0) {
            List<AnChatChat> truncated = new ArrayList<>(msgs.subList(0, localIndex));
            targetSlice.setMessageStr(JSONObject.toJSONString(truncated));
            targetSlice.setUpdateTime(new Date());
            anChatDao.save(targetSlice);
        } else {
            // 第一条就被删，整个切片清空
            anChatDao.delete(targetSlice);
        }

        // 删除当前切片之后的所有切片
        anChatDao.deleteByConversationIdAndSplitIndexIdGreaterThan(
                conversationId, targetSlice.getSplitIndexId());

        return Result.success();
    }

    @Override
    public Result<Void> editChat(Integer conversationId, Integer msgIndex, String newContent) {
        if (conversationId == null || msgIndex == null || msgIndex < 0 || newContent == null) {
            return Result.fail("参数不合法");
        }
        List<AnChatContent> slices = anChatDao.findByConversationIdOrderBySplitIndexIdAsc(conversationId);
        if (slices.isEmpty()) {
            return Result.fail("会话不存在");
        }

        int globalIndex = 0;
        AnChatContent targetSlice = null;
        int localIndex = -1;

        for (AnChatContent slice : slices) {
            List<AnChatChat> msgs = parseMessages(slice.getMessageStr());
            int sliceMsgCount = (msgs != null) ? msgs.size() : 0;
            if (msgIndex < globalIndex + sliceMsgCount) {
                targetSlice = slice;
                localIndex = msgIndex - globalIndex;
                break;
            }
            globalIndex += sliceMsgCount;
        }

        if (targetSlice == null) {
            return Result.fail("消息索引超出范围");
        }

        List<AnChatChat> msgs = parseMessages(targetSlice.getMessageStr());
        msgs.get(localIndex).setContent(newContent);
        targetSlice.setMessageStr(JSONObject.toJSONString(msgs));
        targetSlice.setUpdateTime(new Date());
        anChatDao.save(targetSlice);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<AnChatContent> branchChat(Integer conversationId, Integer msgIndex) {
        if (conversationId == null || msgIndex == null || msgIndex < 0) {
            return Result.fail("参数不合法");
        }
        List<AnChatContent> slices = anChatDao.findByConversationIdOrderBySplitIndexIdAsc(conversationId);
        if (slices.isEmpty()) {
            return Result.fail("会话不存在");
        }

        // 收集从开头到 msgIndex（含）的所有消息
        List<AnChatChat> branchMessages = new ArrayList<>();
        int globalIndex = 0;
        boolean found = false;

        for (AnChatContent slice : slices) {
            List<AnChatChat> msgs = parseMessages(slice.getMessageStr());
            int sliceMsgCount = (msgs != null) ? msgs.size() : 0;
            for (int i = 0; i < sliceMsgCount; i++) {
                branchMessages.add(msgs.get(i));
                if (globalIndex + i == msgIndex) {
                    found = true;
                    break;
                }
            }
            if (found) break;
            globalIndex += sliceMsgCount;
        }

        if (!found) {
            return Result.fail("消息索引超出范围");
        }

        // 构建新会话的第一个切片
        AnChatContent newSlice = new AnChatContent();
        newSlice.setMessages(branchMessages);
        newSlice.setModelName(slices.get(0).getModelName());
        newSlice.setSplitIndexId(0);
        newSlice.setConversationId(-1);

        // 从第一条 user 消息生成 preview
        String preview = "";
        for (AnChatChat msg : branchMessages) {
            if ("user".equals(msg.getRole())) {
                String content = msg.getContent();
                preview = content.length() > 50 ? content.substring(0, 50) : content;
                break;
            }
        }
        newSlice.setPreview(preview);

        // 保存：第一次获取 id，第二次设置 conversationId = id
        persistSlice(newSlice);
        newSlice.setConversationId(newSlice.getId());
        persistSlice(newSlice);

        return Result.success(newSlice);
    }

    @Override
    public Result<Void> updatePreview(Integer conversationId, String preview) {
        if (conversationId == null || preview == null) {
            return Result.fail("参数不合法");
        }
        AnChatContent firstSlice = anChatDao.findByConversationIdAndSplitIndexId(conversationId, 0);
        if (firstSlice == null) {
            return Result.fail("会话不存在");
        }
        firstSlice.setPreview(preview);
        firstSlice.setUpdateTime(new Date());
        anChatDao.save(firstSlice);
        return Result.success();
    }

    @Override
    public Result<Void> toggleStar(Integer conversationId) {
        if (conversationId == null) {
            return Result.fail("参数不合法");
        }
        AnChatContent firstSlice = anChatDao.findByConversationIdAndSplitIndexId(conversationId, 0);
        if (firstSlice == null) {
            return Result.fail("会话不存在");
        }
        boolean currentStar = firstSlice.getIsStar() != null && firstSlice.getIsStar();
        firstSlice.setIsStar(!currentStar);
        anChatDao.save(firstSlice);
        return Result.success();
    }

    @Override
    public List<AnChatContent> getStarredChat() {
        return anChatDao.findStarredConversations();
    }

    @Override
    public Result<Void> saveOption(String key, String value) {
        if (key == null || key.isEmpty()) {
            return Result.fail("参数不合法");
        }
        configOptionService.saveValue(key, value);
        return Result.success();
    }

    @Override
    public Result<Void> saveOptions(java.util.Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Result.fail("参数不合法");
        }
        for (java.util.Map.Entry<String, String> entry : options.entrySet()) {
            configOptionService.saveValue(entry.getKey(), entry.getValue());
        }
        return Result.success();
    }

    @Override
    public Result<Void> reloadConstants() {
        configOptionService.reload();
        anChatConstants.reload();
        return Result.success();
    }

}
