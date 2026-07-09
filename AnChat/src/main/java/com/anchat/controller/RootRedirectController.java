package com.anchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 站点根路径重定向到 anchat 聊天页。
 * 独立成 Controller 是因为 AnChatController 带有 /anChat 类级前缀，
 * 无法拦截真正的上下文根路径（/ 与 /index.html）。
 */
@Controller
public class RootRedirectController {

    @GetMapping({"/", "/index.html"})
    public String root() {
        return "redirect:/anChat/init.html";
    }
}
