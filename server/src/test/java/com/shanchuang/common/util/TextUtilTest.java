package com.shanchuang.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilTest {

    @Test
    @DisplayName("中文字数去空白后按码点计，emoji 不该算成两个字")
    void cnWords() {
        assertEquals(0, TextUtil.cnWords(null));
        assertEquals(0, TextUtil.cnWords("   \n\t "));
        assertEquals(4, TextUtil.cnWords("你好 世界"));
        assertEquals(6, TextUtil.cnWords("我 你 他\n她 它 谁"));
        // emoji 是代理对，用 length() 会算 2
        assertEquals(3, TextUtil.cnWords("好👍的"));
        assertEquals(5, TextUtil.cnWords("abc中文"));
    }

    @Test
    @DisplayName("标点与空白不参与重合判定")
    void normalize() {
        assertEquals("你好世界", TextUtil.normalizeForOverlap("你好，世界！"));
        assertEquals("abc123", TextUtil.normalizeForOverlap("abc - 123"));
    }

    @Test
    @DisplayName("连续重合 16 字触发红线，15 字不触发")
    void overlapThreshold() {
        String original = "计算机专业转人工智能方向的同学最容易在第一个月踩到框架选择这个坑上面";

        // 抄 16 个连续实字
        String copy16 = "开头随便说一句。" + original.substring(0, 16) + "后面接自己的话";
        assertTrue(TextUtil.overlapExceeded(copy16, original));
        assertTrue(TextUtil.longestOverlap(copy16, original) >= 16);

        // 只抄 15 个字，不该判失败
        String copy15 = "开头随便说一句。" + original.substring(0, 15) + "后面接自己的话";
        assertFalse(TextUtil.overlapExceeded(copy15, original));
    }

    @Test
    @DisplayName("重合检测要给出真实最长长度，供审计展示")
    void overlapLength() {
        String original = "这条视频我要讲的是普通人怎么在三个月里搞明白一个新方向的入门路径";
        String rewrite = "前面几句是我自己的话。" + original.substring(0, 24) + "。后面又是我自己的。";
        assertEquals(24, TextUtil.longestOverlap(rewrite, original));
    }

    @Test
    @DisplayName("完全原创时重合为 0，不该误报")
    void noOverlap() {
        String original = "计算机专业转人工智能方向的同学最容易踩的坑是框架选择";
        String rewrite = "我今天只说一件事，你手里已经有的东西往往比从零开始更值钱";
        assertFalse(TextUtil.overlapExceeded(rewrite, original));
    }

    @Test
    @DisplayName("短文本不该越界")
    void shortText() {
        assertEquals(0, TextUtil.longestOverlap("", "abc"));
        assertEquals(0, TextUtil.longestOverlap(null, null));
        assertEquals(2, TextUtil.longestOverlap("你好", "你好世界"));
    }

    @Test
    void maskPhone() {
        assertEquals("138****1234", TextUtil.maskPhone("13800001234"));
        assertEquals("123", TextUtil.maskPhone("123"));
    }
}
