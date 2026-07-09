package com.anchat.controller;

import com.anchat.dao.AnChatHistoryDao;
import com.anchat.pojo.Result;
import com.anchat.pojo.anChat.AnChatHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/anChatLog")
public class AnChatLogController {

    @Autowired
    private AnChatHistoryDao anChatHistoryDao;

    @GetMapping("/index.html")
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        Page<AnChatHistory> historyPage = anChatHistoryDao.findAllByOrderByTimeDesc(PageRequest.of(page, size));
        model.addAttribute("historyPage", historyPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", historyPage.getTotalPages());
        model.addAttribute("totalElements", historyPage.getTotalElements());
        return "anchat/log";
    }

    @GetMapping("/detail/{id}")
    @ResponseBody
    public Result<AnChatHistory> detail(@PathVariable Integer id) {
        return anChatHistoryDao.findById(id)
                .map(Result::success)
                .orElse(Result.fail("记录不存在"));
    }
}
