package com.shanchuang.modules.persona.controller;

import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.modules.persona.dto.PersonaVO;
import com.shanchuang.modules.persona.service.PersonaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 人设档案，对应 docs/接口设计.md 第 4 章 */
@RestController
@RequestMapping("/api/persona")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping
    public R<PersonaVO> get() {
        return R.ok(personaService.get(CurrentUser.id()));
    }

    @PutMapping
    public R<PersonaVO> save(@RequestBody PersonaVO body) {
        return R.ok(personaService.save(CurrentUser.id(), body));
    }

    @PostMapping("/reset")
    public R<PersonaVO> reset() {
        return R.ok(personaService.reset(CurrentUser.id()));
    }
}
