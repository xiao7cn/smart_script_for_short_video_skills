package com.shanchuang.modules.task.controller;

import com.shanchuang.common.result.PageData;
import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.modules.task.dto.TaskVO;
import com.shanchuang.modules.task.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskNo}")
    public R<TaskVO> detail(@PathVariable String taskNo) {
        return R.ok(taskService.detail(CurrentUser.id(), taskNo));
    }

    @GetMapping
    public R<PageData<TaskVO>> page(@RequestParam(required = false) String type,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "20") long size) {
        return R.ok(taskService.page(CurrentUser.id(), type, page, Math.min(size, 100)));
    }

    @PostMapping("/{taskNo}/cancel")
    public R<Void> cancel(@PathVariable String taskNo) {
        taskService.cancel(CurrentUser.id(), taskNo);
        return R.ok();
    }
}
