package com.anchat.pojo.anChat;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "an_chat_model")
public class AnChatModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    String name;
    String description;
    String rules;

}
