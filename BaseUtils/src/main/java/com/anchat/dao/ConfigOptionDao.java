package com.anchat.dao;

import com.anchat.pojo.ConfigOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigOptionDao extends JpaRepository<ConfigOption, Integer> {

    ConfigOption findByKey(String key);
}
