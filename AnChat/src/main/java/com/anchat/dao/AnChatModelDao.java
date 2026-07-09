package com.anchat.dao;

import com.anchat.pojo.anChat.AnChatModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnChatModelDao extends JpaRepository<AnChatModel, Integer> {

}
