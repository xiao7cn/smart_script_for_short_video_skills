package com.shanchuang.modules.teardown.controller;

import com.shanchuang.common.result.PageData;
import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.modules.teardown.dto.TeardownDtos.MaterialRequest;
import com.shanchuang.modules.teardown.dto.TeardownDtos.SubmitRequest;
import com.shanchuang.modules.teardown.dto.TeardownDtos.SubmitResponse;
import com.shanchuang.modules.teardown.dto.TeardownDtos.TeardownListItem;
import com.shanchuang.modules.teardown.dto.TeardownDtos.TeardownVO;
import com.shanchuang.modules.teardown.service.TeardownService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teardowns")
public class TeardownController {

    private final TeardownService teardownService;

    public TeardownController(TeardownService teardownService) {
        this.teardownService = teardownService;
    }

    @PostMapping
    public R<SubmitResponse> submit(@RequestBody SubmitRequest request,
                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = requestId == null || requestId.isBlank() ? IdUtil.requestId() : requestId;
        return R.ok(teardownService.submit(CurrentUser.id(), request, rid));
    }

    @GetMapping("/{id}")
    public R<TeardownVO> detail(@PathVariable Long id) {
        return R.ok(teardownService.detail(CurrentUser.id(), id));
    }

    @GetMapping
    public R<PageData<TeardownListItem>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        return R.ok(teardownService.page(CurrentUser.id(), page, Math.min(size, 100)));
    }

    /** 取件失败后用户补录屏，从转写续跑 */
    @PostMapping("/{id}/material")
    public R<SubmitResponse> material(@PathVariable Long id, @RequestBody MaterialRequest request) {
        return R.ok(teardownService.continueWithMaterial(CurrentUser.id(), id, request.fileId()));
    }
}
