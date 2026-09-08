package com.shanchuang.modules.persona;

import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.persona.dto.PersonaVO;
import com.shanchuang.modules.persona.service.PersonaService;
import com.shanchuang.modules.testsupport.ModuleTestBase;
import com.shanchuang.workflow.model.PersonaSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonaServiceTest extends ModuleTestBase {

    private static final long UID = 7001L;

    @Autowired
    private PersonaService personaService;

    @Test
    void 首次访问自动建档为系统默认() {
        PersonaVO vo = personaService.get(UID);

        PersonaSnapshot def = PersonaSnapshot.systemDefault();
        assertEquals(def.model(), vo.model());
        assertEquals(def.identity(), vo.identity());
        assertEquals(def.value(), vo.value());
        assertEquals(def.needs(), vo.needs());
        assertEquals(def.banned(), vo.banned());
        assertEquals(500, vo.minWords());
        assertEquals("抖音", vo.platform());
        assertEquals(1, count("sv_persona WHERE user_id = ?", UID));
        // value 落在 value_prop 列
        assertEquals(def.value(), jdbc.queryForObject("SELECT value_prop FROM sv_persona WHERE user_id = ?",
                String.class, UID));
    }

    @Test
    void 下发字段与接口设计一致() {
        Map<String, Object> json = JsonUtil.toMap(JsonUtil.toJson(personaService.get(UID)));

        assertEquals(Set.of("model", "modelDesc", "identity", "value", "tone", "audience",
                "needs", "banned", "minWords", "ctaStyle", "ctaAsset", "platform"), json.keySet());
    }

    @Test
    void 重复访问不会重复建档() {
        personaService.get(UID);
        personaService.get(UID);
        personaService.snapshot(UID);

        assertEquals(1, count("sv_persona WHERE user_id = ?", UID));
    }

    @Test
    void 保存是全量覆盖() {
        personaService.get(UID);

        PersonaVO body = new PersonaVO("犀利导师", "说话直", "十年面试官", "帮人少走弯路",
                "犀利", "应届生", List.of("不知道投哪家"), List.of("包过"),
                800, "私信领清单", "面试题库", "小红书");
        PersonaVO saved = personaService.save(UID, body);

        assertEquals(body, saved);
        assertEquals(body, personaService.get(UID));
        assertEquals(1, count("sv_persona WHERE user_id = ?", UID));
        assertEquals(800, intOf("SELECT min_words FROM sv_persona WHERE user_id = ?", UID));
    }

    @Test
    void 保存时空值走缺省() {
        PersonaVO body = new PersonaVO(null, null, null, null, null, null, null, null,
                0, null, null, null);

        PersonaVO saved = personaService.save(UID, body);

        assertEquals("靠谱顾问", saved.model());
        assertEquals(500, saved.minWords());
        assertEquals("抖音", saved.platform());
        assertTrue(saved.needs().isEmpty());
        assertTrue(saved.banned().isEmpty());
    }

    @Test
    void 恢复默认() {
        personaService.save(UID, new PersonaVO("犀利导师", "说话直", "十年面试官", "帮人少走弯路",
                "犀利", "应届生", List.of("不知道投哪家"), List.of("包过"),
                800, "私信领清单", "面试题库", "小红书"));

        PersonaVO reset = personaService.reset(UID);

        PersonaSnapshot def = PersonaSnapshot.systemDefault();
        assertEquals(def.model(), reset.model());
        assertEquals(def.banned(), reset.banned());
        assertEquals(def.minWords(), reset.minWords());
        assertEquals("抖音", reset.platform());
        assertEquals(1, count("sv_persona WHERE user_id = ?", UID));
    }

    @Test
    void 快照供生成链路取用() {
        personaService.save(UID, new PersonaVO("犀利导师", "说话直", "十年面试官", "帮人少走弯路",
                "犀利", "应届生", List.of("不知道投哪家", "怕被裁"), List.of("包过", "内推名额"),
                800, "私信领清单", "面试题库", "小红书"));

        PersonaSnapshot s = personaService.snapshot(UID);

        assertEquals("犀利导师", s.model());
        assertEquals("帮人少走弯路", s.value());
        assertEquals(List.of("不知道投哪家", "怕被裁"), s.needs());
        assertEquals(List.of("包过", "内推名额"), s.banned());
        assertEquals(800, s.minWords());
        assertEquals("小红书", s.platform());
    }

    @Test
    void 快照在未建档时自动建默认档() {
        PersonaSnapshot s = personaService.snapshot(UID);

        assertEquals(PersonaSnapshot.systemDefault().model(), s.model());
        assertEquals(500, s.minWordsOrDefault());
        assertEquals(1, count("sv_persona WHERE user_id = ?", UID));
    }
}
