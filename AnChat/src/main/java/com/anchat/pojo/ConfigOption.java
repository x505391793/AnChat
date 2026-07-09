package com.anchat.pojo;

import lombok.Data;

import javax.persistence.*;

/**
 * 通用配置项实体，对应 config_option 表
 * 所有模块统一通过 ConfigOptionService 读写，禁止直接操作 DAO
 */
@Data
@Entity
@Table
public class ConfigOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(name = "`key`", unique = true, nullable = false)
    String key;

    @Column(name = "`value`")
    String value;
}
