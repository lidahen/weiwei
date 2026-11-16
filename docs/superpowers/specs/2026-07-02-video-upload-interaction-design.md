---
description: 视频生成模块前端改造设计——图生视频/多图视频/关键帧的上传图片交互
created: 2026-07-02
---

# 视频生成模块前端改造设计

## 背景

当前视频生成页面（`twelvet-ui-master/src/pages/AI/Video`）中，图生视频(img2vid)、多图视频(multi2vid)、关键帧(keyframes) 模式的图片输入方式要求用户手动填写 JSON 格式的 URL 字符串，用户体验差。需要参照图生图模块的上传交互进行改造。

## 方案

**方案 A：仿照图生图模式**

### 1. 图片输入交互

每种需要图片的模式增加「上传图片 / 图片URL」Radio 切换：

| 模式 | 上传限制 | URL 输入 |
|------|---------|---------|
| 图生视频 (img2vid) | 最多 1 张 | 单个 Input 填 URL |
| 多图视频 (multi2vid) | 最多 5 张 | TextArea 填 JSON 数组 |
| 关键帧 (keyframes) | 最多 5 张 | TextArea 填 JSON 数组 |

### 2. 上传流程

```
用户选择图片 → beforeUpload 校验(类型+大小≤10MB) → 调用 DFS 上传接口 → 拿到路径 → 拼接完整URL → 存入 imageUrls[]
```

上传成功后显示预览缩略图 + "✓ 图片已上传"提示。

### 3. 帧数智能校验

- 上方放置预设按钮组：3s(81帧) / 5s(121帧) / 10s(241帧) / 18s(441帧)
- 点击预设自动填入合法值（满足 8n+1 且 ≤441）
- 保留手动输入框，输入时校验合法性，不合法时警告

### 4. 数据提交

- 图生视频：从 imageUrls 取第一个 → 作为顶层 `image` 字符串传给后端
- 多图/关键帧：将所有 imageUrls 转为 JSON 数组 → 存入 `inputImages` 字段

### 5. 后端改动

**零改动**。后端 `AgnesVideoService.buildVideoRequestBody()` 已正确处理 `inputImages` JSON 解析。前端只需确保上传后构造正确的 URL 字符串传入。

### 6. 状态管理

```typescript
// 每种模式独立的上传状态
const [imageUrls, setImageUrls] = useState<string[]>([]);  // 当前模式的图片URL列表
const [inputMode, setInputMode] = useState<'upload' | 'url'>('upload');
const [fileList, setFileList] = useState<UploadFile[]>([]);
const [uploading, setUploading] = useState(false);
```

### 7. 涉及文件

- `twelvet-ui-master/src/pages/AI/Video/index.tsx` — 主要改动
- `twelvet-ui-master/src/pages/AI/Video/service.ts` — 无需改动（已有 upload 功能通过公共接口）
- `twelvet-fast/twelvet-ai/` — 无需改动

## 成功标准

1. 图生视频支持上传图片（1张）和 URL 两种方式
2. 多图视频/关键帧支持批量上传（最多5张）和 JSON URL 两种方式
3. 帧数输入有预设快捷选择和合法性校验
4. 上传走 DFS 接口，与图生图保持一致
5. 后端零改动
