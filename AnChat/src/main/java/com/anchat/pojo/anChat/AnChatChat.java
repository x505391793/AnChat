package com.anchat.pojo.anChat;

import lombok.Data;

@Data
public class AnChatChat {
    String content;
    String role;

    public AnChatChat(String content, String role) {
        this.content = content;
        this.role = role;
    }
}
