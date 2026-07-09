package com.anchat.pojo.anChat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import com.anchat.common.AnChatConstants;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(name = "an_chat_content")
public class AnChatContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    String messageStr;
    String modelName;
    String preview;
    Integer splitIndexId;
    String summary;
    Integer conversationId;
    Date updateTime;
    Boolean isStar;
    String rules;


    @Transient
    List<AnChatChat> messages;
    @Transient
    String content;
    @Transient
    String model;
    /*
    介于 -2.0 和 2.0 之间的数字。如果该值为正，那么新 token 会根据其在已有文本中的出现频率受到相应的惩罚，降低模型重复相同内容的可能性。
     */
//    @Transient
//    float frequency_penalty = 0.7f;
    @Transient
    int max_tokens = 3000;
    /*
    介于 -2.0 和 2.0 之间的数字。如果该值为正，那么新 token 会根据其是否已在已有文本中出现受到相应的惩罚，从而增加模型谈论新主题的可能性。
     */
//    @Transient
//    float presence_penalty = 0.7f;
    @Transient
    float temperature = 0.7f;
//    @Transient
//    float top_p = 0.92f;

    @Transient
    boolean stream;


    public AnChatContent() {
        messages = new ArrayList<>();
        model = AnChatConstants.anChatModel;
    }

    /**
     * JPQL 投影构造器：用于历史列表展示，modelName 取首个切片（原始模型）
     */
    public AnChatContent(Integer conversationId, String preview, String modelName, Date updateTime, Boolean isStar) {
        this.conversationId = conversationId;
        this.preview = preview;
        this.modelName = modelName;
        this.updateTime = updateTime;
        this.isStar = isStar;
    }

    public void addChat(String content, String role) {
        AnChatChat newChat = new AnChatChat(content, role);
        messages.add(newChat);
    }

    public String toString() {
        JSONObject json = JSON.parseObject(JSON.toJSONString(this));
        if (ModelEnums.needThinking(model)) {
            json.put("reasoning_effort", "high");
            json.put("thinking", new JSONObject().fluentPut("type", "enabled"));
        }
        if (model.equals(ModelEnums.DEEPSEEK_V4_FLASH_CHAT.getValue())){
            json.replace("model","deepseek-v4-flash");
        }
        return json.toJSONString();
    }

    private String dealContent(String content) {
        content = content.replaceAll("\n", "<br>");
        return content;
    }
}

