package com.anchat.controller;

import com.anchat.common.AnChatConstants;
import com.anchat.pojo.Result;
import com.anchat.pojo.anChat.AnChatContent;
import com.anchat.pojo.anChat.AnChatModel;
import com.anchat.service.AnChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/anChat")
public class AnChatController {

    @Autowired
    AnChatService anChatService;

    @GetMapping("/init.html")
    public String init(Model model) {
        List<AnChatModel> allModel = anChatService.getAllModel();
        model.addAttribute("allModel", allModel);
        model.addAttribute("historyChat",anChatService.getHistoryChat());
        model.addAttribute("starredChat",anChatService.getStarredChat());
        return "anchat/init";
    }

    @GetMapping("/settings.html")
    public String settings(Model model) {
        List<AnChatModel> allModel = anChatService.getAllModel();
        model.addAttribute("allModel", allModel);
        model.addAttribute("maxSliceChars", AnChatConstants.maxSliceChars);
        model.addAttribute("minContextTurns", AnChatConstants.minContextTurns);
        model.addAttribute("hiddenPrompt", AnChatConstants.HIDDEN_PROMPT);
        model.addAttribute("currentModel", AnChatConstants.anChatModel);
        model.addAttribute("startupTime", AnChatConstants.startupTime);
        return "anchat/settings";
    }

    @PostMapping("/chat")
    @ResponseBody
    public Result<AnChatContent> chat(Integer id, String content, Integer modelId) {
        return anChatService.sendMessage(id, content, modelId);
    }
    @PostMapping("/reloadChat")
    @ResponseBody
    public Result<AnChatContent> reloadChat(Integer chatId) {
        return anChatService.reloadChat(chatId);
    }

    @PostMapping("/updateModelDescription")
    @ResponseBody
    public Result<Void> updateModelDescription(Integer modelId, String description, String rules) {
        return anChatService.updateModelDescription(modelId, description, rules);
    }

    @PostMapping("/deleteModel")
    @ResponseBody
    public Result<Void> deleteModel(Integer modelId) {
        return anChatService.deleteModel(modelId);
    }

    @PostMapping("/addModel")
    @ResponseBody
    public Result<Void> addModel(String name, String description, String rules) {
        return anChatService.addModel(name, description, rules);
    }

    @PostMapping("/deleteChat")
    @ResponseBody
    public Result<Void> deleteChat(Integer conversationId, Integer msgIndex) {
        return anChatService.deleteChat(conversationId, msgIndex);
    }

    @PostMapping("/editChat")
    @ResponseBody
    public Result<Void> editChat(Integer conversationId, Integer msgIndex, String newContent) {
        return anChatService.editChat(conversationId, msgIndex, newContent);
    }

    @PostMapping("/updatePreview")
    @ResponseBody
    public Result<Void> updatePreview(Integer conversationId, String preview) {
        return anChatService.updatePreview(conversationId, preview);
    }

    @PostMapping("/branchChat")
    @ResponseBody
    public Result<AnChatContent> branchChat(Integer conversationId, Integer msgIndex) {
        return anChatService.branchChat(conversationId, msgIndex);
    }

    @PostMapping("/toggleStar")
    @ResponseBody
    public Result<Void> toggleStar(Integer conversationId) {
        return anChatService.toggleStar(conversationId);
    }

    @PostMapping("/saveOption")
    @ResponseBody
    public Result<Void> saveOption(String key, String value) {
        return anChatService.saveOption(key, value);
    }

    @PostMapping("/saveOptions")
    @ResponseBody
    public Result<Void> saveOptions(@RequestBody Map<String, String> options) {
        return anChatService.saveOptions(options);
    }

    @PostMapping("/reloadConstants")
    @ResponseBody
    public Result<Void> reloadConstants() {
        return anChatService.reloadConstants();
    }

    /**
     * 全局注入 basePath，供 anchat 页面（index.html 片段）中的 AJAX 同域调用使用。
     * 原 BaseController 被移除后，此处补齐，保持页面 JS 中 basePath + '/anChat/...' 可用。
     */
    @ModelAttribute("basePath")
    public String basePath() {
        return "";
    }

//    @PostMapping("/chatStream")
//    public SseEmitter chatStream(@RequestParam(required = false) Integer id, @RequestParam String content) {
//        return anChatService.sendMessageStream(id, content);
//    }

}
