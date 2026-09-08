package com.shanchuang.modules.aiconfig;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.shanchuang.modules.aiconfig.entity.AiModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 锁住「可选配置项能被清空」这件事。
 *
 * 管理端改模型配置是全量覆盖语义，但 MyBatis-Plus 的 updateById 默认跳过 null，
 * 于是「把 baseUrl 清掉、从中转站换回官方端点」会静默失效：
 * 界面显示 deepseek，实际请求仍打到残留的中转站地址。
 * 这个故障真实发生过一次，所以用测试把注解钉住。
 */
class AiModelConfigFieldStrategyTest {

    @ParameterizedTest
    @ValueSource(strings = {"baseUrl", "apiKeyEnv", "temperature", "maxTokens", "remark"})
    @DisplayName("可选字段必须允许更新为 null，否则无法换回官方端点")
    void nullableFieldsMustAlwaysUpdate(String fieldName) throws NoSuchFieldException {
        Field field = AiModelConfig.class.getDeclaredField(fieldName);
        TableField annotation = field.getAnnotation(TableField.class);

        assertNotNull(annotation,
                fieldName + " 缺 @TableField，updateById 会跳过它的 null 值");
        assertEquals(FieldStrategy.ALWAYS, annotation.updateStrategy(),
                fieldName + " 的 updateStrategy 必须是 ALWAYS，否则清空操作静默失效");
    }
}
