function join(a) {
  return (a || []).join("、");
}

function gridPathOf(sel) {
  return [sel.inner, sel.middle, sel.outer]
    .map((a) => (a || []).join("/"))
    .filter(Boolean)
    .join(" × ");
}

function buildPrompt(sel, persona, scriptTypes) {
  const selScripts = sel.scriptType || [];
  const st = selScripts.length === 1 ? scriptTypes.find((s) => s.key === selScripts[0]) : null;
  const gridPath = gridPathOf(sel);
  const lines = [
    "你现在是一名资深的短视频文案写手，写文案前会搜罗分析同选题的高赞视频做参考，再按脚本结构撰写口播文案。全程口语化，多用「我」和「你」，一句话只讲一件事，写完能读出声、你奶奶也听得懂。只要纯文案，不要画面，不低于 500 字。",
    "",
    "【人设】" + persona.model + "｜" + persona.identity,
    "【价值定位】" + persona.value,
    "【目标人群】" + persona.audience,
    "【表达风格】" + persona.tone,
    "",
    "【选题类型】" + (sel.topicType && sel.topicType.length ? join(sel.topicType) + "类选题" : "不限"),
    "【选题来源】" + (sel.source && sel.source.length ? join(sel.source) : "不限"),
  ];
  const refs = (sel.refs || []).filter((r) => r && r.trim());
  if (refs.length) lines.push("【参考链接】" + refs.join("  "));
  if (sel.autoSearch) lines.push("【自动搜索】开启：优先检索同选题高赞爆款视频作为对标参考。");
  lines.push(
    "【25 宫格配对】" + (gridPath || "不限"),
    "【爆款元素】" + (sel.element && sel.element.length ? join(sel.element) : "不限"),
    "【选题方向】" + ((sel.topicDraft && sel.topicDraft.trim()) || "由你结合以上参数拟定"),
    "",
    "【脚本类型】" + (sel.scriptType && sel.scriptType.length ? join(sel.scriptType) : "按 4:1:3:2 配比随机"),
    "【结构公式】" + (st ? st.formula : "按所选脚本类型对应结构（多选则逐条轮换）"),
    "",
    "【硬性红线】通篇避开这些词：" + (persona.banned || []).join("、") + "。",
    "先给我 3 个吸睛标题（标题≠选题，只为让人停留），再按结构公式输出正文。",
  );
  return lines.join("\n");
}

module.exports = { join, gridPathOf, buildPrompt };
