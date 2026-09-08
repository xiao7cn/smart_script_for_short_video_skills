package com.shanchuang.modules.options.controller;

import com.shanchuang.common.result.R;
import com.shanchuang.modules.options.service.OptionService;
import com.shanchuang.workflow.model.OptionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向导选项，对应 docs/接口设计.md 3.1，免登录。
 * 直接下发 OptionCatalog：它的字段与 3.1 的 JSON 一一对应，再套一层 VO 只会让两边漂移。
 */
@RestController
@RequestMapping("/api/options")
public class OptionController {

    private final OptionService optionService;

    public OptionController(OptionService optionService) {
        this.optionService = optionService;
    }

    @GetMapping
    public R<OptionCatalog> options() {
        return R.ok(optionService.catalog());
    }
}
