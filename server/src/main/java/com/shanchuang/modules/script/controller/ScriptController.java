package com.shanchuang.modules.script.controller;

import com.shanchuang.common.result.PageData;
import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.modules.script.dto.ScriptDtos.GenerateRequest;
import com.shanchuang.modules.script.dto.ScriptDtos.GenerateResponse;
import com.shanchuang.modules.script.dto.ScriptDtos.PromptPreviewRequest;
import com.shanchuang.modules.script.dto.ScriptDtos.PromptPreviewResponse;
import com.shanchuang.modules.script.dto.ScriptDtos.ScriptDetail;
import com.shanchuang.modules.script.dto.ScriptDtos.ScriptListItem;
import com.shanchuang.modules.script.service.GenerateService;
import com.shanchuang.modules.script.service.PromptPreviewService;
import com.shanchuang.modules.script.service.ScriptService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScriptController {

    private final GenerateService generateService;
    private final ScriptService scriptService;
    private final PromptPreviewService promptPreviewService;

    public ScriptController(GenerateService generateService, ScriptService scriptService,
                            PromptPreviewService promptPreviewService) {
        this.generateService = generateService;
        this.scriptService = scriptService;
        this.promptPreviewService = promptPreviewService;
    }

    /** 提交即返回 taskNo，前端跳文案库后轮询 */
    @PostMapping("/scripts/generate")
    public R<GenerateResponse> generate(@RequestBody GenerateRequest request,
                                        @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = requestId == null || requestId.isBlank() ? IdUtil.requestId() : requestId;
        return R.ok(generateService.submit(CurrentUser.id(), request, rid));
    }

    @GetMapping("/scripts")
    public R<PageData<ScriptListItem>> list(@RequestParam(required = false) String topicType,
                                            @RequestParam(required = false) String scriptType,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        return R.ok(scriptService.page(CurrentUser.id(), topicType, scriptType, page, Math.min(size, 100)));
    }

    @GetMapping("/scripts/{id}")
    public R<ScriptDetail> detail(@PathVariable Long id) {
        return R.ok(scriptService.detail(CurrentUser.id(), id));
    }

    @DeleteMapping("/scripts/{id}")
    public R<Void> delete(@PathVariable Long id) {
        scriptService.delete(CurrentUser.id(), id);
        return R.ok();
    }

    @PostMapping("/prompts/preview")
    public R<PromptPreviewResponse> preview(@RequestBody PromptPreviewRequest request) {
        return R.ok(promptPreviewService.preview(CurrentUser.id(), request));
    }
}
