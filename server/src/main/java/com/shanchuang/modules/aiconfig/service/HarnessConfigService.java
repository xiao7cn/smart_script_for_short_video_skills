package com.shanchuang.modules.aiconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanchuang.harness.HarnessRouter;
import com.shanchuang.harness.HarnessSelectionSource;
import com.shanchuang.modules.aiconfig.entity.HarnessConfig;
import com.shanchuang.modules.aiconfig.mapper.HarnessConfigMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * harness 配置。
 *
 * 实现 harness 层定义的 HarnessSelectionSource，让 HarnessRouter 能读到库里
 * is_default=1 的那一行——这就是「不重启切换 harness」的入口。
 */
@Service
public class HarnessConfigService implements HarnessSelectionSource {

    private final HarnessConfigMapper mapper;
    private final HarnessRouter router;

    /** router 反过来依赖本类（通过 ObjectProvider），用 @Lazy 打破构造期的循环 */
    public HarnessConfigService(HarnessConfigMapper mapper, @Lazy HarnessRouter router) {
        this.mapper = mapper;
        this.router = router;
    }

    @Override
    public Optional<String> defaultHarnessName() {
        HarnessConfig row = mapper.selectOne(Wrappers.<HarnessConfig>lambdaQuery()
                .eq(HarnessConfig::getIsDefault, 1)
                .eq(HarnessConfig::getEnabled, 1)
                .last("limit 1"));
        return Optional.ofNullable(row).map(HarnessConfig::getName);
    }

    @Override
    public Optional<String> endpointOf(String name) {
        HarnessConfig row = findByName(name);
        return Optional.ofNullable(row).map(HarnessConfig::getEndpoint);
    }

    public List<HarnessConfig> listAll() {
        return mapper.selectList(Wrappers.<HarnessConfig>lambdaQuery().orderByAsc(HarnessConfig::getId));
    }

    public HarnessConfig findByName(String name) {
        return mapper.selectOne(Wrappers.<HarnessConfig>lambdaQuery()
                .eq(HarnessConfig::getName, name)
                .last("limit 1"));
    }

    /** 切换当前 harness：单默认，切完清路由缓存让新端点生效 */
    public void switchDefault(String name) {
        List<HarnessConfig> all = listAll();
        for (HarnessConfig row : all) {
            Integer expected = row.getName().equals(name) ? 1 : 0;
            if (!expected.equals(row.getIsDefault())) {
                row.setIsDefault(expected);
                if (expected == 1) {
                    row.setEnabled(1);
                }
                mapper.updateById(row);
            }
        }
        router.invalidate();
    }

    public void recordHealth(String name, boolean ok, String version) {
        HarnessConfig row = findByName(name);
        if (row == null) {
            return;
        }
        row.setLastHealthOk(ok ? 1 : 0);
        row.setLastHealthAt(LocalDateTime.now());
        if (version != null) {
            row.setVersion(version);
        }
        mapper.updateById(row);
    }
}
