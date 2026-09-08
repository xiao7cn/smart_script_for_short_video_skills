package com.shanchuang.modules.credit.controller;

import com.shanchuang.common.result.PageData;
import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.modules.credit.dto.CreditAccountVO;
import com.shanchuang.modules.credit.dto.CreditPackVO;
import com.shanchuang.modules.credit.dto.CreditTxnVO;
import com.shanchuang.modules.credit.dto.RechargeReq;
import com.shanchuang.modules.credit.dto.RechargeVO;
import com.shanchuang.modules.credit.service.CreditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 额度与充值，对应 docs/接口设计.md 第 10 章 */
@RestController
@RequestMapping("/api/credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping
    public R<CreditAccountVO> overview() {
        return R.ok(creditService.overview(CurrentUser.id()));
    }

    @GetMapping("/packs")
    public R<List<CreditPackVO>> packs() {
        return R.ok(creditService.packs());
    }

    @PostMapping("/recharge")
    public R<RechargeVO> recharge(@RequestBody RechargeReq req) {
        return R.ok(creditService.recharge(CurrentUser.id(), req == null ? null : req.packId()));
    }

    @GetMapping("/transactions")
    public R<PageData<CreditTxnVO>> transactions(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return R.ok(creditService.transactions(CurrentUser.id(), page, size));
    }
}
