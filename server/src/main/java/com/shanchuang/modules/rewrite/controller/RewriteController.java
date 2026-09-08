package com.shanchuang.modules.rewrite.controller;

import com.shanchuang.common.result.PageData;
import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.PublishResponse;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.RewriteListItem;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.RewriteVO;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.SubmitRequest;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.SubmitResponse;
import com.shanchuang.modules.rewrite.service.RewriteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rewrites")
public class RewriteController {

    private final RewriteService rewriteService;

    public RewriteController(RewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    @PostMapping
    public R<SubmitResponse> submit(@RequestBody SubmitRequest request,
                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = requestId == null || requestId.isBlank() ? IdUtil.requestId() : requestId;
        return R.ok(rewriteService.submit(CurrentUser.id(), request, rid));
    }

    @GetMapping("/{id}")
    public R<RewriteVO> detail(@PathVariable Long id) {
        return R.ok(rewriteService.detail(CurrentUser.id(), id));
    }

    @GetMapping
    public R<PageData<RewriteListItem>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size) {
        return R.ok(rewriteService.page(CurrentUser.id(), page, Math.min(size, 100)));
    }

    /** 定稿：把第九段写入文案库 */
    @PostMapping("/{id}/publish")
    public R<PublishResponse> publish(@PathVariable Long id) {
        return R.ok(rewriteService.publish(CurrentUser.id(), id));
    }
}
