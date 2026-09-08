package com.shanchuang.modules.script.service;

import com.shanchuang.modules.options.service.OptionService;
import com.shanchuang.modules.persona.service.PersonaService;
import com.shanchuang.modules.script.dto.ScriptDtos.PromptPreviewRequest;
import com.shanchuang.modules.script.dto.ScriptDtos.PromptPreviewResponse;
import com.shanchuang.workflow.ParamPicker;
import com.shanchuang.workflow.PromptBuilder;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.WizardSel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词预览。
 *
 * 前端为了秒级响应会本地也拼一份，这个接口给出服务端口径——
 * 真正提交生成时以服务端（或用户手改版）为准，两边不一致时以这里为真。
 */
@Service
public class PromptPreviewService {

    /** 预览用固定 seed，同样的参数每次看到同样的提示词，不然用户会以为在乱变 */
    private static final long PREVIEW_SEED = 20260905L;

    private final PersonaService personaService;
    private final OptionService optionService;
    private final PromptBuilder promptBuilder;

    public PromptPreviewService(PersonaService personaService, OptionService optionService,
                               PromptBuilder promptBuilder) {
        this.personaService = personaService;
        this.optionService = optionService;
        this.promptBuilder = promptBuilder;
    }

    public PromptPreviewResponse preview(Long userId, PromptPreviewRequest request) {
        WizardSel sel = request.sel() == null ? WizardSel.empty() : request.sel();
        OptionCatalog catalog = optionService.catalog();
        PersonaSnapshot persona = personaService.snapshot(userId);

        List<ParamCard> cards = new ParamPicker(catalog).pick(sel, 1, PREVIEW_SEED);
        ParamCard card = cards.get(0);

        String topic = sel.topicDraft() == null || sel.topicDraft().isBlank()
                ? "由你结合以上参数拟定"
                : sel.topicDraft().trim();
        String prompt = promptBuilder.bodySystem(persona) + "\n\n"
                + promptBuilder.bodyUser(card, persona, topic, "由你拟定 3 个吸睛标题");

        return new PromptPreviewResponse(prompt, promptBuilder.deaiPrompt());
    }
}
