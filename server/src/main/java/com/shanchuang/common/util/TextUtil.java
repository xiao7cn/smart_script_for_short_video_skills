package com.shanchuang.common.util;

import java.util.HashSet;
import java.util.Set;

/**
 * 文本度量。
 *
 * 这里的两个算法是质量门的基础，规则来自 skills/video-script-rewrite/scripts/check.py：
 * 中文字数按去空白后的码点数算，与原文连续重合 ≥16 字视为洗稿失败。
 */
public final class TextUtil {

    /** 与 check.py 一致的重合阈值 */
    public static final int OVERLAP_THRESHOLD = 16;

    private TextUtil() {
    }

    /**
     * 中文字数：去掉所有空白后按码点计。
     * 不能用 length()——emoji 与生僻字是代理对，会被算成 2 个字。
     */
    public static int cnWords(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        String t = s.replaceAll("\\s+", "");
        return t.codePointCount(0, t.length());
    }

    /**
     * 归一化：去空白与标点，只留下参与重合判定的实字。
     * 不做繁简转换——原文什么样就按什么样比，转换会造出原文里不存在的重合。
     */
    public static String normalizeForOverlap(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    /**
     * 求两段文本的最长连续重合字数。
     *
     * 朴素做法是 O(n·m·L)。这里用定长窗口哈希把「是否存在 ≥16 的重合」降到 O(n+m)：
     * 阈值本身就是 16，所以存在更长重合时必然存在某个 16 长窗口重合，不需要枚举更长的窗口。
     * 命中后再向右扩张，求出真实最长长度供审计。
     *
     * @return 最长连续重合的字数；小于阈值时返回的是「已探到的最长值」，可能为 0
     */
    public static int longestOverlap(String rewrite, String original) {
        String a = normalizeForOverlap(rewrite);
        String b = normalizeForOverlap(original);
        if (a.length() < OVERLAP_THRESHOLD || b.length() < OVERLAP_THRESHOLD) {
            return bruteForceLongest(a, b, Math.min(a.length(), b.length()));
        }

        Set<String> windows = new HashSet<>();
        for (int i = 0; i + OVERLAP_THRESHOLD <= b.length(); i++) {
            windows.add(b.substring(i, i + OVERLAP_THRESHOLD));
        }

        int best = 0;
        for (int i = 0; i + OVERLAP_THRESHOLD <= a.length(); i++) {
            String window = a.substring(i, i + OVERLAP_THRESHOLD);
            if (!windows.contains(window)) {
                continue;
            }
            // 命中阈值，向右扩张求真实长度
            int at = b.indexOf(window);
            int len = OVERLAP_THRESHOLD;
            while (i + len < a.length() && at + len < b.length() && a.charAt(i + len) == b.charAt(at + len)) {
                len++;
            }
            best = Math.max(best, len);
        }
        if (best > 0) {
            return best;
        }
        // 没到阈值，仍给出已探到的最长值（用于报告里展示 overlapMax）
        return bruteForceLongest(a, b, OVERLAP_THRESHOLD - 1);
    }

    /** 短文本或未达阈值时的兜底：只在很小的上界内找，代价可控 */
    private static int bruteForceLongest(String a, String b, int cap) {
        int limit = Math.min(cap, Math.min(a.length(), b.length()));
        for (int len = limit; len >= 1; len--) {
            for (int i = 0; i + len <= a.length(); i++) {
                if (b.contains(a.substring(i, i + len))) {
                    return len;
                }
            }
        }
        return 0;
    }

    /** 是否触发洗稿重合红线 */
    public static boolean overlapExceeded(String rewrite, String original) {
        return longestOverlap(rewrite, original) >= OVERLAP_THRESHOLD;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 手机号脱敏，用于列表展示与默认昵称 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 截断到指定字数，用于日志与摘要 */
    public static String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
