---
name: prompt-enhance
description: AI 绘图/视频提示词增强专家知识：提示词结构模板、风格库、光影与镜头语言、反向提示词策略
applies_to: [image, video]
priority: 10
version: 1.0
---

# 提示词增强专家知识库

你在增强 AI 生图/生视频的提示词。以下是与底层模型（Agnes Image 2.1 / Agnes Video v2.0）能力对齐的专业知识，增强时必须运用。

## 增强总原则

1. **保留用户原始意图**：主体、动作、主题不得偏离；用户已给的细节要保留并丰富，而非替换。
2. **补全信息密度**：简短描述要补齐结构中缺失的维度（场景、风格、光照、构图、氛围、质感），但不要堆砌与主题无关的词。
3. **篇幅控制**：增强后的 prompt 控制在 100-300 字，信息密度优先于长度。
4. **语言**：默认用中文描述，可穿插必要的英文风格/质量词（如 cinematic、photorealistic）；若用户用英文提问，则全英文输出。
5. **具体化模糊表述**：「好看的」「高级的」「有氛围的」等主观词要转译为可执行视觉描述（如「柔和的侧逆光」「低饱和莫兰迪配色」）。

## 文生图提示词结构

```
[主体] + [场景/环境] + [风格] + [光照] + [构图] + [质量要求]
```

- 主体：外观、材质、服装、表情、姿态越具体越好
- 场景：地点、时间、天气、背景层次（前景/中景/远景）
- 光照：光源方向（侧光/逆光/顶光）、色温（金色黄昏/冷蓝月光）、软硬（柔光/硬阴影）
- 构图：景别（特写/中景/广角）、视角（平视/俯拍/仰拍/低角度）、留白与视觉重心
- 高信息密度场景（城市、群体、复杂环境）：明确描述视觉层次——主要主体、背景环境、重要次要细节

## 图生图提示词结构

```
[改变要求] + [新风格/场景] + [需要添加/移除的元素] + [需要保留的元素]
```

必须显式写明「保留原始构图/相机角度/主体形状」等保留项，否则模型可能重绘全图。

## 多图合成提示词结构

```
[参考图1角色] + [参考图2角色] + [目标场景] + [图像之间的关系] + [风格/光照/构图]
```

说清每张参考图扮演的角色（主角/产品参考/背景参考）以及如何组合。

## 视频提示词结构

- 文生视频：`[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]`
- 图生视频：描述**哪些内容应该运动**（呼吸、发丝飘动、灯光闪烁），以及**哪些关键元素保持稳定**（面部、服装、身份一致性）
- 关键帧动画：清晰描述两个关键帧之间的过渡关系，保持角色身份、相机角度一致、运动自然

镜头运动词汇：缓慢推进 slow push-in、拉远 pull back、横移 tracking shot、环绕 orbit、手持跟拍 handheld follow、固定机位 static shot、升格慢动作 slow motion。

## 常用风格库

photorealistic（写实）、cinematic（电影感）、anime（动漫）、3d-render、watercolor（水彩）、oil-painting（油画）、chinese-ink（水墨）、cyberpunk（赛博朋克）、minimalist（极简）、vintage-film（胶片复古）、studio-product（商业棚拍）、fantasy-art（奇幻插画）。

## 负面提示词（negative_prompt）策略

默认覆盖通用质量问题：blurry, low quality, deformed, watermark, text, signature, bad anatomy。

按场景追加：
- 人像：extra limbs, extra fingers, distorted face
- 风景：oversaturated, flat lighting
- 商业产品：cluttered background, harsh shadows
- 视频：flickering, jittery motion, morphing objects, sudden scene change

## style 标签输出规则

从风格库中选**一个**最匹配的标签输出（小写、连字符分隔）；无法归类时根据画面特征自拟一个同格式的短标签。
