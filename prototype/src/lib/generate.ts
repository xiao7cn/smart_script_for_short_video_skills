import {
  gridMiddle,
  gridOuter,
  viralElements,
  scriptTypes,
  type PersonaData,
} from "../data/config";
import type { Script, ScriptType, TopicType } from "../data/scripts";

// 所有选项均支持多选，故存为数组；生成每条时从所选集合里随机取一个
export type WizardSel = {
  topicType?: string[];
  source?: string[];
  inner?: string[]; // AI就业，可为空
  middle?: string[];
  outer?: string[];
  element?: string[];
  scriptType?: string[];
  topicDraft?: string;
  refs?: string[]; // 对标视频 / 参考链接，可多条
  autoSearch?: boolean; // 自动搜索爆款视频
};

const TOPIC_TYPES: TopicType[] = ["转化类", "破圈类", "家长类"];
const SOURCES = ["客户咨询高频提问", "行业热点借势融合"];
const ELEMENTS = viralElements.map((e) => e.key);

const pick = <T>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)];

// 有选则从所选里挑，没选则从完整池里挑
const pickSel = <T extends string>(selArr: string[] | undefined, pool: T[]): T =>
  selArr && selArr.length ? (pick(selArr) as T) : pick(pool);

// 脚本类型按 4:1:3:2 加权
const SCRIPT_POOL: ScriptType[] = [
  ...Array<ScriptType>(4).fill("痛点科普"),
  ...Array<ScriptType>(1).fill("Vlog 叙事"),
  ...Array<ScriptType>(3).fill("聊天纪实"),
  ...Array<ScriptType>(2).fill("话题共鸣"),
];

function genTopic(middle: string, outer: string, tt: TopicType): string {
  const tpl = [
    `${outer}想在${middle}上突围，最该先搞清楚的一件事`,
    `${middle}这条路，${outer}到底适不适合走`,
    `关于${middle}，${outer}普遍踩的那个坑`,
    `${outer}做${middle}，方向和方法哪个先出了问题`,
    tt === "家长类"
      ? `家长该不该推孩子走${middle}这条路`
      : `${middle}的真实门槛，和大家想的不一样`,
  ];
  return pick(tpl);
}

function genTitle(middle: string, outer: string, element: string, st: ScriptType): string {
  const byElement: Record<string, string[]> = {
    成本: [`花最少的钱，把${middle}这件事搞明白`, `${middle}值不值得砸钱，我把账算给你看`],
    人群: [`${outer}想转${middle}，卡住的其实是同一件事`, `因为${middle}发愁的${outer}，这条别划走`],
    奇葩: [`${middle}这行有个外行人根本想不到的真相`, `我见过${middle}里最离谱的一种走法`],
    头牌: [`大厂招${middle}到底看什么，答案可能颠覆你`, `顶尖的人做${middle}，和普通人差在哪`],
    怀旧: [`20 年前的那道选择题，又摆到了${outer}面前`, `如果能重来，${middle}我一定这样开始`],
    反差: [`你以为的${middle}，和真实的${middle}差了十万八千里`, `越努力越吃亏？${middle}的反常识`],
    最差: [`${middle}里最不该踩的一种坑，很多人正在踩`, `别做${middle}里最没面子的那种选择`],
    荷尔蒙: [`我一个朋友靠${middle}翻了身，现在追的人排队`, `想在${middle}上变抢手？先看这条`],
  };
  const fallback = [`关于${middle}，${outer}最该知道的几件事`, `${middle}这块，我把话给你说透`];
  return pick(byElement[element] ?? fallback) + (st === "Vlog 叙事" ? "" : "");
}

function genBody(
  st: ScriptType,
  ctx: { topic: string; middle: string; outer: string; element: string; persona: PersonaData },
): string {
  const { topic, middle, outer } = ctx;
  let paras: string[] = [];

  if (st === "痛点科普") {
    paras = [
      `如果你正卡在「${topic}」这件事上，先别急着否定自己，这条我给你讲明白。`,
      `我先说个可能不太中听的判断：大多数人卡住，不是不够努力，是方向没找对。${middle}这块，真正的门槛跟大家想的不一样。`,
      `第一，别再盲目堆时间。我见过太多人把力气花在跟目标无关的地方，学了半年，一到实战全崩，问题不是学得少，是学杂了。`,
      `第二，找一个你能落地的小切口。哪怕很小，把${middle}里一件重复又烦人的事，用能上手的方法做出个结果来。它比十张证书都有用，因为别人能顺着它往下问。`,
      `第三，也是${outer}最容易忽略的——你手里已经有的东西，往往就是别人补不上的那块，别一上来就从零开始，把自己最强的地方扔了。`,
      `我把${middle}这几个方向的路线图整理出来了，写清楚了门槛、大概学多久、哪类人不建议走。评论区留个「路线」，我发你，不适合你的我也会直说。`,
    ];
  } else if (st === "Vlog 叙事") {
    paras = [
      `今天记录一下。想搞明白「${topic}」，我这几天遇到的几个人，特别有代表性。`,
      `【片段一】早上见了一个人，一聊到${middle}就叹气，说自己是不是没戏。其实他手里的东西挺值钱，只是他自己不知道。`,
      `【片段二】中午又接了个咨询，问的还是那句：我要不要先从头学一遍。我说先别，把你已经会的盘一盘再说。`,
      `【片段三】傍晚这个最有意思，讲起自己的老本行滔滔不绝，一换成讲${middle}就卡壳。可那股劲儿，恰恰是最难得的。`,
      `一天下来我最大的感触是：真正学不会的人很少，低估自己的人很多。${outer}要做的，是先看清自己已经站在哪。`,
      `如果你也在这个坎上，别急着报班。先把过去干过的事一条条写下来，你可能会发现，起点比你以为的高。`,
    ];
  } else if (st === "聊天纪实") {
    paras = [
      `前几天一个人来找我，开口就问：${topic}？`,
      `我没直接回答，先反问他一句：你现在最担心的到底是哪一点？`,
      `他愣了一下，说了半天，其实核心就一个——怕在${middle}上花了时间和钱，最后白搭。`,
      `我说这个担心很实在。但你换个角度想，${outer}真正的优势，从来不是从零学一个新东西，是把新工具接到你已经有的底子上。`,
      `我给他举了个例子，讲完他眼睛就亮了，说原来我不用去跟人硬拼那条最卷的路。`,
      `聊到最后他的问题变了，从「我行不行」变成了「我该从哪一步开始」。这就是我想说的：答案往往不在别处，在你怎么看自己手里的牌。`,
      `我把这套判断清单整理好了，评论区留个「路线」，我发你，帮你少走点弯路。`,
    ];
  } else {
    paras = [
      `${topic}？今天说说我的看法，可能跟你想的不太一样。`,
      `大多数人讨论这个，比的是眼前的得失。我觉得这个比法一开始就偏了。`,
      `真正的差别不在表面，在你把安全感押在哪儿——是押在一个现成的位置上，还是押在不断更新的自己身上。`,
      `${middle}这条路给的确定性，是你手里有能拿得出手的本事，此处不留你自有留你处。代价是这份底气得你自己每年重新挣一次。`,
      `这没有标准答案。我见过两种活法都过得很好的人，他们的共同点不是选对了路，是选的那条路跟自己的性格对上了。`,
      `所以我想问你的是：换成是你，会怎么选？评论区聊聊你的情况，我看看能不能帮你判断这条路合不合适。`,
    ];
  }

  return paras.join("\n\n");
}

// 对标爆款拆解 → 合成原文案 + 拆解要点 + 二次创作差异说明
function genBreakdown(
  sel: WizardSel,
  ctx: { middle: string; outer: string; element: string },
): NonNullable<Script["breakdown"]> {
  const { middle, outer, element } = ctx;
  const original = [
    `${middle}这行，我真的劝你别再瞎努力了！`,
    `今天这条我说点得罪人的大实话，看完能帮你少走三年弯路，先点个赞别刷走。`,
    `我身边一个${outer}，之前在${middle}上死磕，越用力越惨，差点就放弃了。`,
    `后来他就换了一个思路，三个月直接翻盘，现在过得比谁都好。`,
    `方法就三步：第一步找对方向，第二步只做一件事，第三步死磕到出结果。`,
    `想要完整方法的，评论区扣「1」，我看到就私发你，名额不多，手慢无。`,
  ].join("\n");

  const points = [
    {
      label: "开场钩子",
      detail: `用「别再瞎努力」这类反常识断言制造冲突，3 秒内逼停划走，是这条爆款最核心的抓手。`,
    },
    {
      label: "情绪叙事",
      detail: `拿「${outer}死磕失败又逆袭」的强反差故事替代讲道理，让观众代入而不是听课。`,
    },
    {
      label: "结构节奏",
      detail: `把干货压成「三步法」清单，配上${element}这类爆款元素，降低理解成本、提高完播。`,
    },
    {
      label: "转化设计",
      detail: `结尾用「扣 1 私发 + 名额有限」制造紧迫感和互动，把流量往私域引。`,
    },
  ];

  const rewrite = [
    `保留原视频的「反常识开场 + 三步清单」骨架，这是它跑起来的真正原因。`,
    `换掉贩卖焦虑式的钩子和「朋友逆袭」的空泛案例，替换成你人设里的真实咨询场景，更可信也更像你。`,
    `结尾从「扣 1 引流」改成符合顾问人设的软引导，稳重不油腻，避免掉粉。`,
  ].join("\n");

  const refs = sel.refs?.filter((r) => r.trim());
  return {
    refs: refs && refs.length ? refs : undefined,
    autoSearch: sel.autoSearch,
    original,
    points,
    rewrite,
  };
}

export function generateBatch(
  sel: WizardSel,
  count: number,
  persona: PersonaData,
  startN: number,
): Script[] {
  const out: Script[] = [];
  const now = Date.now();
  for (let i = 0; i < count; i++) {
    const topicType = pickSel<TopicType>(sel.topicType, TOPIC_TYPES);
    const source = pickSel(sel.source, SOURCES);
    const inner = sel.inner && sel.inner.length ? pick(sel.inner) : "";
    const middle = pickSel(sel.middle, gridMiddle);
    const outer = pickSel(sel.outer, gridOuter);
    const element = pickSel(sel.element, ELEMENTS);
    const st = pickSel<ScriptType>(sel.scriptType, SCRIPT_POOL);

    const gridParts = [inner, middle, outer].filter(Boolean);
    if (gridParts.length === 0) gridParts.push("AI就业");
    const grid = gridParts.join(" × ");

    const topic = sel.topicDraft?.trim() || genTopic(middle, outer, topicType);
    const title = genTitle(middle, outer, element, st);
    const structure = scriptTypes.find((s) => s.key === st)?.formula ?? "";
    const body = genBody(st, { topic, middle, outer, element, persona });
    const words = body.replace(/\s/g, "").length;

    out.push({
      id: `gen-${now}-${i}`,
      n: startN + i,
      createdAt: now + i,
      generated: true,
      title,
      topic,
      scriptType: st,
      topicType,
      source,
      grid,
      element,
      structure,
      words,
      needsMaterial:
        st === "Vlog 叙事"
          ? "Vlog 三个片段的身份、专业、对话细节，请换成你真实接待过的人。结构可照用，具体信息不要沿用。"
          : undefined,
      body,
      breakdown:
        source === "对标爆款拆解"
          ? genBreakdown(sel, { middle, outer, element })
          : undefined,
    });
  }
  return out;
}
