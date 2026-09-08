package com.shanchuang.modules.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.modules.persona.dto.PersonaVO;
import com.shanchuang.modules.persona.entity.Persona;
import com.shanchuang.modules.persona.mapper.PersonaMapper;
import com.shanchuang.workflow.model.PersonaSnapshot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersonaService {

    private final PersonaMapper personaMapper;

    public PersonaService(PersonaMapper personaMapper) {
        this.personaMapper = personaMapper;
    }

    /** 首次访问自动按系统默认人设建档 */
    public PersonaVO get(Long userId) {
        return toVO(ensure(userId));
    }

    /** 全量覆盖，返回保存后的对象 */
    @Transactional
    public PersonaVO save(Long userId, PersonaVO vo) {
        if (vo == null) {
            throw BizException.badRequest("人设内容不能为空");
        }
        Persona row = ensure(userId);
        apply(row, vo);
        personaMapper.updateById(row);
        return toVO(row);
    }

    @Transactional
    public PersonaVO reset(Long userId) {
        Persona row = ensure(userId);
        apply(row, toVO(PersonaSnapshot.systemDefault()));
        personaMapper.updateById(row);
        return toVO(row);
    }

    /** 注册时建默认档，避免用户第一次进工作台才建 */
    @Transactional
    public void initDefault(Long userId) {
        ensure(userId);
    }

    /** 生成链路取人设快照：任务要冻结当时的人设，事后才能复现同一批结果 */
    public PersonaSnapshot snapshot(Long userId) {
        Persona row = ensure(userId);
        return new PersonaSnapshot(
                row.getModel(),
                row.getModelDesc(),
                row.getIdentity(),
                row.getValueProp(),
                row.getTone(),
                row.getAudience(),
                JsonUtil.toStringList(row.getNeedsJson()),
                JsonUtil.toStringList(row.getBannedJson()),
                row.getMinWords() == null ? 500 : row.getMinWords(),
                row.getCtaStyle(),
                row.getCtaAsset(),
                row.getPlatform()
        );
    }

    private Persona ensure(Long userId) {
        if (userId == null) {
            throw BizException.of(401, "未登录或令牌过期");
        }
        Persona row = selectByUser(userId);
        if (row != null) {
            return row;
        }
        row = new Persona();
        row.setUserId(userId);
        apply(row, toVO(PersonaSnapshot.systemDefault()));
        try {
            personaMapper.insert(row);
            return row;
        } catch (DuplicateKeyException e) {
            // uk_user 撞车说明并发建过了，直接用那一行
            return selectByUser(userId);
        }
    }

    private Persona selectByUser(Long userId) {
        return personaMapper.selectOne(new LambdaQueryWrapper<Persona>().eq(Persona::getUserId, userId));
    }

    private void apply(Persona row, PersonaVO vo) {
        row.setModel(TextUtil.isBlank(vo.model()) ? PersonaSnapshot.systemDefault().model() : vo.model());
        row.setModelDesc(vo.modelDesc());
        row.setIdentity(vo.identity());
        row.setValueProp(vo.value());
        row.setTone(vo.tone());
        row.setAudience(vo.audience());
        row.setNeedsJson(JsonUtil.toJson(vo.needs() == null ? List.<String>of() : vo.needs()));
        row.setBannedJson(JsonUtil.toJson(vo.banned() == null ? List.<String>of() : vo.banned()));
        row.setMinWords(vo.minWords() > 0 ? vo.minWords() : 500);
        row.setCtaStyle(vo.ctaStyle());
        row.setCtaAsset(vo.ctaAsset());
        row.setPlatform(TextUtil.isBlank(vo.platform()) ? "抖音" : vo.platform());
    }

    private PersonaVO toVO(Persona row) {
        return new PersonaVO(
                row.getModel(),
                row.getModelDesc(),
                row.getIdentity(),
                row.getValueProp(),
                row.getTone(),
                row.getAudience(),
                JsonUtil.toStringList(row.getNeedsJson()),
                JsonUtil.toStringList(row.getBannedJson()),
                row.getMinWords() == null ? 500 : row.getMinWords(),
                row.getCtaStyle(),
                row.getCtaAsset(),
                row.getPlatform()
        );
    }

    private PersonaVO toVO(PersonaSnapshot s) {
        return new PersonaVO(s.model(), s.modelDesc(), s.identity(), s.value(), s.tone(), s.audience(),
                s.needs(), s.banned(), s.minWords(), s.ctaStyle(), s.ctaAsset(), s.platform());
    }
}
