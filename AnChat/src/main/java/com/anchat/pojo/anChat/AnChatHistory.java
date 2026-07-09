package com.anchat.pojo.anChat;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "an_chat_history")
public class AnChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(columnDefinition = "TEXT")
    String request;

    @Column(columnDefinition = "TEXT")
    String response;

    Date time;
}
