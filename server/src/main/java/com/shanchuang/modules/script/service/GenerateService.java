package com.shanchuang.modules.script.service;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.options.service.OptionService;
import com.shanchuang.modules.persona.service.PersonaService;
import com.shanchuang.modules.script.dto.ScriptDtos.GenerateRequest;
import com.shanchuang.modules.script.dto.ScriptDtos.GenerateResponse;
import com.shanchuang.modules.task.TaskStatus;
import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.service.TaskService;
import com.shanchuang.workflow.ParamPicker;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.WizardSel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成的受理入口。
 *
 * 只做三件事：校验与限流、预扣额度、建任务并交给执行器。
 * 真正的模型调用在 GenerateRunner 里异步跑——单条要 3 次模型调用，
 * 20 条批量约 10 分钟，同步返回是不可能的。
 */
@Service
public class GenerateService {

    private final TaskService taskService;
    private final CreditService creditService;
    private final PersonaService personaService;
    private final OptionService optionService;
    private final GenerateRunner runner;

    private final int maxCount;
    private final int maxRunningTasks;

    public GenerateService(TaskService taskService, CreditService creditService,
                           PersonaService personaService, OptionService optionService,
                           GenerateRunner runner,
                           @Value("${app.generate.max-count:20}") int maxCount,
                           @Value("${app.generate.max-running-tasks-per-user:2}") int maxRunningTasks) {
        this.taskService = taskService;
        this.creditService = creditService;
        this.personaService = personaService;
        this.optionService = optionService;
        this.runner = runner;
        this.maxCount = maxCount;
        this.maxRunningTasks = maxRunningTasks;
    }

    public GenerateResponse submit(Long userId, GenerateRequest request, String requestId) {
        int requested = request.count() == null ? 1 : request.count();
        if (requested < 1 || requested > maxCount) {
            throw BizException.of(3002, "生成条数需在 1~" + maxCount + " 之间");
        }

        // 幂等：同一个 requestId 重复提交直接回首次结果
        Task existing = taskService.findByRequestId(userId, requestId);
        if (existing != null) {
            return new GenerateResponse(existing.getTaskNo(), existing.getTotal(), requested,
                    existing.getCreditsHold(), creditService.overview(userId).balance(), null);
        }

        if (taskService.countRunning(userId) >= maxRunningTasks) {
            throw BizException.of(429, "已有 " + maxRunningTasks + " 个任务在进行中，请稍后再提交");
        }

        WizardSel sel = request.sel() == null ? WizardSel.empty() : request.sel();
        OptionCatalog catalog = optionService.catalog();
        PersonaSnapshot persona = personaService.snapshot(userId);

        // 预扣：额度不足时按剩余额度受理，与原型「额度仅够生成 N 条」的行为一致
        int accepted = creditService.hold(userId, requested, "pending");
        if (accepted <= 0) {
            throw BizException.of(3001, "额度不足，请先充值");
        }

        List<ParamCard> cards = new ParamPicker(catalog).pick(sel, accepted);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sel", sel);
        snapshot.put("persona", persona);
        snapshot.put("promptOverride", request.promptOverride());
        snapshot.put("ratio", ParamPicker.ratioSummary(cards));

        Task task = taskService.create(userId, TaskStatus.TYPE_GENERATE, requestId,
                accepted, snapshot, accepted);
        taskService.createItems(task.getId(), cards);

        runner.runAsync(task.getId(), userId, cards, persona, catalog, request.promptOverride());

        String message = accepted < requested ? "额度仅够生成 " + accepted + " 条，已受理" : null;
        return new GenerateResponse(task.getTaskNo(), accepted, requested, accepted,
                creditService.overview(userId).balance(), message);
    }
}
