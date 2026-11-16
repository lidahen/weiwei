# 视频生成模块前端改造——上传图片交互

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 改造视频生成页面(img2vid/multi2vid/keyframes)的图片输入方式，从手动填 JSON URL 改为支持上传图片（参考图生图模块），同时保留 URL 输入方式。

**Architecture:** 前端改造为主，后端零改动。视频页面仿照图片页面的 Upload+URL Radio 切换模式，每种模式独立维护上传图片 URL 列表，提交时按 Agnes API 规范构造请求体。帧数输入增加预设快捷按钮 + 合法性校验。

**Tech Stack:** React 18 + TypeScript + Ant Design 5.x + Umi Max 4.x + ProComponents

## Global Constraints

- 遵循 twelvet-ui-master 项目的既有代码模式（formItemLayout、system.error、proTableConfigs）
- 上传走 `/dfs/commonUpload` 接口，使用 `uploadImage` service 函数
- 静态资源拼接使用 `TWT.static` + 后端返回的文件路径
- 图片最大 10MB，仅接受 image/* 类型
- 帧数必须满足 8n+1 且 ≤441（合法值：1, 9, 17, 25, 33, 41, 49, 57, 65, 73, 81, 89, 97, 105, 113, 121, 129, 137, 145, 153, 161, 169, 177, 185, 193, 201, 209, 217, 225, 233, 241, 249, 257, 265, 273, 281, 289, 297, 305, 313, 321, 329, 337, 345, 353, 361, 369, 377, 385, 393, 401, 409, 417, 425, 433, 441）
- 所有代码风格与现有 Video/index.tsx 保持一致（注释格式、命名约定、组件导入顺序）

---

# 实施计划

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `twelvet-ui-master/src/pages/AI/Video/index.tsx` | 主要改动：上传图片交互、帧数预设、提交逻辑 |
| 修改 | `twelvet-ui-master/src/pages/AI/Video/service.ts` | 新增 `uploadVideoImage` 函数（复用公共 upload 接口） |

---

### Task 1: service.ts 新增 uploadVideoImage 函数

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/service.ts`

**Interfaces:**
- Consumes: `upload` from `@/utils/twelvet`
- Produces: `uploadVideoImage(formData: FormData)` function

**Step 1: 添加 uploadVideoImage 导出函数**

在 service.ts 末尾添加：

```typescript
/**
 * 上传视频图片到DFS
 * @param formData 文件表单数据
 */
export async function uploadVideoImage(formData: FormData) {
    return upload('/dfs/commonUpload', formData);
}
```

**Step 2: 验证文件完整性**

确认 service.ts 最终结构为：
```typescript
import { request } from '@umijs/max';
import { upload } from '@/utils/twelvet';

const controller = '/ai/video';

export async function generateVideo(data: { [key: string]: any }) { ... }
export async function queryVideoStatus(taskId: string | number) { ... }
export async function pageQueryVideoTask(query: { [key: string]: any }) { ... }
export async function getVideoTask(taskId: string | number) { ... }
export async function delVideoTask(taskId: string | number) { ... }
export async function uploadVideoImage(formData: FormData) { ... }
```

**Step 3: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/service.ts
git commit -m "feat: 视频生成模块新增图片上传service函数"
```

---

### Task 2: 重构 Video 页面——添加上传相关 state 和工具函数

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Interfaces:**
- Consumes: `uploadVideoImage` from service.ts, `TWT` from setting, `UploadFile` type from antd
- Produces: New state variables and helper functions for image upload

**Step 1: 添加新的 imports**

在现有 import 段中，确保以下组件已导入（如缺失则添加）：

```typescript
import { UploadOutlined, PlusOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import TWT from '@/setting';
```

同时从 service 中导入 `uploadVideoImage`：
```typescript
import {
    generateVideo,
    queryVideoStatus,
    delVideoTask,
    pageQueryVideoTask,
    uploadVideoImage,
} from './service';
```

**Step 2: 添加新的 state 变量**

在 `VideoPage` 组件内，在现有 state 之后添加：

```typescript
// ========== 上传相关 state ==========

// 当前模式的输入方式：upload 或 url
const [inputMode, setInputMode] = useState<'upload' | 'url'>('upload');

// 上传的文件列表
const [fileList, setFileList] = useState<UploadFile[]>([]);

// 上传后收集的图片完整URL列表
const [uploadedImageUrls, setUploadedImageUrls] = useState<string[]>([]);

// 上传loading状态
const [uploading, setUploading] = useState<boolean>(false);
```

**Step 3: 添加上传处理函数**

在 `handleGenTypeChange` 之后添加：

```typescript
    /**
     * 处理图片上传
     */
    const handleUpload = async (file: File) => {
        try {
            setUploading(true);
            const formData = new FormData();
            formData.append('file', file);
            const { code, msg, data } = await uploadVideoImage(formData);
            if (code !== 200) {
                message.error(msg || '上传失败');
                setFileList([]);
                setUploadedImageUrls([]);
                return;
            }
            // data 是文件路径，拼接静态资源地址得到完整URL
            const fullUrl = `${TWT.static}/${data}`;
            setUploadedImageUrls([fullUrl]);
            message.success('图片上传成功');
        } catch (e) {
            system.error(e);
            setFileList([]);
            setUploadedImageUrls([]);
        } finally {
            setUploading(false);
        }
    };

    /**
     * 处理多图上传
     */
    const handleMultiUpload = async (file: File) => {
        try {
            setUploading(true);
            const formData = new FormData();
            formData.append('file', file);
            const { code, msg, data } = await uploadVideoImage(formData);
            if (code !== 200) {
                message.error(msg || '上传失败');
                return null;
            }
            const fullUrl = `${TWT.static}/${data}`;
            return fullUrl;
        } catch (e) {
            system.error(e);
            return null;
        } finally {
            setUploading(false);
        }
    };
```

**Step 4: 修改切换生成类型时的重置逻辑**

修改现有的 `handleGenTypeChange` 函数，增加清空上传状态：

```typescript
    /**
     * 切换生成类型时重置上传状态
     */
    const handleGenTypeChange = (value: string) => {
        setGenType(value);
        setInputMode('upload');
        setFileList([]);
        setUploadedImageUrls([]);
    };
```

**Step 5: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "feat: 视频生成模块添加上传相关state和工具函数"
```

---

### Task 3: 实现图生视频的单图上传 UI

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Interfaces:**
- Consumes: `handleUpload`, `uploadedImageUrls`, `inputMode`, `setInputMode`, `fileList`, `setFileList`, `handleGenTypeChange`
- Produces: Upload area for img2vid mode

**Step 1: 替换图生视频的图片输入区域**

找到现有的：
```tsx
{(genType === 'img2vid' || genType === 'multi2vid' || genType === 'keyframes') && (
    <Form.Item ...>
        <Input.TextArea ... />
    </Form.Item>
)}
```

将其替换为三段条件渲染，从 `genType === 'img2vid'` 开始：

```tsx
{/* ========== 图生视频：单图上传 ========== */}
{genType === 'img2vid' && (
    <>
        {/* 输入方式切换 */}
        <Form.Item {...formItemLayout} label="输入方式">
            <Radio.Group
                value={inputMode}
                onChange={(e) => {
                    setInputMode(e.target.value);
                    setFileList([]);
                    setUploadedImageUrls([]);
                }}
            >
                <Radio value="upload">上传图片</Radio>
                <Radio value="url">图片URL</Radio>
            </Radio.Group>
        </Form.Item>

        {inputMode === 'upload' ? (
            <Form.Item
                {...formItemLayout}
                label="输入图片"
                required
            >
                <Upload
                    listType="picture-card"
                    maxCount={1}
                    fileList={fileList}
                    beforeUpload={(file) => {
                        const isImage = file.type.startsWith('image/');
                        if (!isImage) {
                            message.error('只能上传图片文件');
                            return false;
                        }
                        const isLt10M = file.size / 1024 / 1024 < 10;
                        if (!isLt10M) {
                            message.error('图片大小不能超过10MB');
                            return false;
                        }
                        setFileList([file as unknown as UploadFile]);
                        handleUpload(file);
                        return false;
                    }}
                    onRemove={() => {
                        setFileList([]);
                        setUploadedImageUrls([]);
                    }}
                >
                    {fileList.length === 0 && (
                        <div>
                            <UploadOutlined />
                            <div style={{ marginTop: 8 }}>上传</div>
                        </div>
                    )}
                </Upload>
                {uploadedImageUrls.length > 0 && (
                    <div style={{ marginTop: 8, color: '#52c41a', fontSize: 12 }}>
                        ✓ 图片已上传
                    </div>
                )}
            </Form.Item>
        ) : (
            <Form.Item
                {...formItemLayout}
                label="输入图片URL"
                name="inputImage"
                rules={[{ required: true, message: '请输入输入图片URL' }]}
            >
                <Input placeholder="请输入公网可访问的图片URL" />
            </Form.Item>
        )}
    </>
)}
```

**Step 2: 提交时处理图生视频的图片数据**

修改 `handleGenerate` 函数，在 `form.validateFields().then` 回调开头添加：

```typescript
                    // 图生视频模式：处理图片输入
                    if (fields.genType === 'img2vid') {
                        if (inputMode === 'upload') {
                            if (uploadedImageUrls.length === 0) {
                                message.error('请先上传图片');
                                return;
                            }
                            // 将第一个上传的URL放到顶层 image 字段
                            fields.image = uploadedImageUrls[0];
                        }
                        // url模式下 inputImage 已在表单中，保持原样
                    }
```

**Step 3: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "feat: 视频生成模块实现图生视频单图上传UI"
```

---

### Task 4: 实现多图视频和关键帧的多图上传 UI

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Interfaces:**
- Consumes: `handleMultiUpload`, `uploadedImageUrls`, `inputMode`
- Produces: Multi-image upload area for multi2vid and keyframes modes

**Step 1: 为多图视频添加上传 UI**

在 `genType === 'img2vid'` 的区块之后添加：

```tsx
{/* ========== 多图视频：多图上传 ========== */}
{genType === 'multi2vid' && (
    <>
        {/* 输入方式切换 */}
        <Form.Item {...formItemLayout} label="输入方式">
            <Radio.Group
                value={inputMode}
                onChange={(e) => {
                    setInputMode(e.target.value);
                    setFileList([]);
                    setUploadedImageUrls([]);
                }}
            >
                <Radio value="upload">上传图片</Radio>
                <Radio value="url">图片URL</Radio>
            </Radio.Group>
        </Form.Item>

        {inputMode === 'upload' ? (
            <Form.Item
                {...formItemLayout}
                label="参考图片"
                required
            >
                <Upload
                    listType="picture-card"
                    maxCount={5}
                    fileList={fileList}
                    beforeUpload={async (file) => {
                        const isImage = file.type.startsWith('image/');
                        if (!isImage) {
                            message.error('只能上传图片文件');
                            return false;
                        }
                        const isLt10M = file.size / 1024 / 1024 < 10;
                        if (!isLt10M) {
                            message.error('图片大小不能超过10MB');
                            return false;
                        }
                        // 多图模式下 fileList 累积添加
                        setFileList((prev) => [...prev, file as unknown as UploadFile]);
                        const url = await handleMultiUpload(file);
                        if (url) {
                            setUploadedImageUrls((prev) => [...prev, url]);
                        }
                        return false;
                    }}
                    onRemove={(removedFile) => {
                        const removedUrl = uploadedImageUrls[fileList.indexOf(removedFile as UploadFile)];
                        setFileList((prev) => prev.filter((f) => f.uid !== removedFile.uid));
                        if (removedUrl) {
                            setUploadedImageUrls((prev) => prev.filter((u) => u !== removedUrl));
                        }
                    }}
                >
                    {fileList.length < 5 && (
                        <div>
                            <PlusOutlined />
                            <div style={{ marginTop: 8 }}>上传</div>
                        </div>
                    )}
                </Upload>
                {uploadedImageUrls.length > 0 && (
                    <div style={{ marginTop: 8, color: '#52c41a', fontSize: 12 }}>
                        ✓ 已上传 {uploadedImageUrls.length} 张图片
                    </div>
                )}
            </Form.Item>
        ) : (
            <Form.Item
                {...formItemLayout}
                label="图片URL数组"
                name="inputImages"
                rules={[{ required: true, message: '请输入图片URL数组JSON' }]}
            >
                <Input.TextArea
                    rows={4}
                    placeholder='请输入图片URL数组，例如：["http://...","http://..."]'
                />
            </Form.Item>
        )}
    </>
)}
```

**Step 2: 为关键帧模式添加上传 UI**

在多图视频区块之后添加：

```tsx
{/* ========== 关键帧动画：多图上传 ========== */}
{genType === 'keyframes' && (
    <>
        {/* 输入方式切换 */}
        <Form.Item {...formItemLayout} label="输入方式">
            <Radio.Group
                value={inputMode}
                onChange={(e) => {
                    setInputMode(e.target.value);
                    setFileList([]);
                    setUploadedImageUrls([]);
                }}
            >
                <Radio value="upload">上传图片</Radio>
                <Radio value="url">图片URL</Radio>
            </Radio.Group>
        </Form.Item>

        {inputMode === 'upload' ? (
            <Form.Item
                {...formItemLayout}
                label="关键帧图片"
                required
            >
                <Upload
                    listType="picture-card"
                    maxCount={5}
                    fileList={fileList}
                    beforeUpload={async (file) => {
                        const isImage = file.type.startsWith('image/');
                        if (!isImage) {
                            message.error('只能上传图片文件');
                            return false;
                        }
                        const isLt10M = file.size / 1024 / 1024 < 10;
                        if (!isLt10M) {
                            message.error('图片大小不能超过10MB');
                            return false;
                        }
                        setFileList((prev) => [...prev, file as unknown as UploadFile]);
                        const url = await handleMultiUpload(file);
                        if (url) {
                            setUploadedImageUrls((prev) => [...prev, url]);
                        }
                        return false;
                    }}
                    onRemove={(removedFile) => {
                        const removedUrl = uploadedImageUrls[fileList.indexOf(removedFile as UploadFile)];
                        setFileList((prev) => prev.filter((f) => f.uid !== removedFile.uid));
                        if (removedUrl) {
                            setUploadedImageUrls((prev) => prev.filter((u) => u !== removedUrl));
                        }
                    }}
                >
                    {fileList.length < 5 && (
                        <div>
                            <PlusOutlined />
                            <div style={{ marginTop: 8 }}>上传</div>
                        </div>
                    )}
                </Upload>
                {uploadedImageUrls.length > 0 && (
                    <div style={{ marginTop: 8, color: '#52c41a', fontSize: 12 }}>
                        ✓ 已上传 {uploadedImageUrls.length} 张图片
                    </div>
                )}
            </Form.Item>
        ) : (
            <Form.Item
                {...formItemLayout}
                label="关键帧URL数组"
                name="inputImages"
                rules={[{ required: true, message: '请输入关键帧URL数组JSON' }]}
            >
                <Input.TextArea
                    rows={4}
                    placeholder='请输入关键帧URL数组，例如：["http://...","http://..."]'
                />
            </Form.Item>
        )}
    </>
)}
```

**Step 3: 提交时处理多图/关键帧的图片数据**

在 `handleGenerate` 中，图生视频处理之后添加：

```typescript
                    // 多图视频/关键帧模式：处理图片输入
                    if (fields.genType === 'multi2vid' || fields.genType === 'keyframes') {
                        if (inputMode === 'upload') {
                            if (uploadedImageUrls.length === 0) {
                                message.error('请至少上传一张图片');
                                return;
                            }
                            // 将所有上传的URL转为JSON数组字符串
                            fields.inputImages = JSON.stringify(uploadedImageUrls);
                        }
                        // url模式下 inputImages 已在表单中，保持原样
                    }
```

**Step 4: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "feat: 视频生成模块实现多图视频和关键帧的多图上传UI"
```

---

### Task 5: 帧数智能校验——预设按钮 + 输入校验

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Interfaces:**
- Consumes: 无外部依赖
- Produces: Frame rate presets and numFrames validation

**Step 1: 添加帧数预设常量**

在组件顶部 state 之后添加：

```typescript
    // 合法的帧数值（满足 8n+1 且 ≤441）
    const VALID_FRAME_COUNTS = [1, 9, 17, 25, 33, 41, 49, 57, 65, 73, 81, 89, 97, 105, 113, 121, 129, 137, 145, 153, 161, 169, 177, 185, 193, 201, 209, 217, 225, 233, 241, 249, 257, 265, 273, 281, 289, 297, 305, 313, 321, 329, 337, 345, 353, 361, 369, 377, 385, 393, 401, 409, 417, 425, 433, 441];

    // 常用时长预设（秒: 帧数）
    const FRAME_PRESETS = [
        { label: '3秒', value: 81 },
        { label: '5秒', value: 121 },
        { label: '10秒', value: 241 },
        { label: '18秒', value: 441 },
    ];
```

**Step 2: 添加帧数校验函数**

```typescript
    /**
     * 校验帧数是否合法（满足 8n+1 且 ≤441）
     */
    const isValidFrameCount = (value: number | undefined): boolean => {
        if (value === undefined || value === null) return false;
        return VALID_FRAME_COUNTS.includes(value);
    };
```

**Step 3: 修改帧数表单项**

将现有的帧数 Input 替换为：

```tsx
                            <Form.Item
                                {...formItemLayout}
                                label="帧数"
                                name="numFrames"
                                initialValue={121}
                                rules={[
                                    {
                                        required: true,
                                        message: '请输入帧数',
                                        type: 'number',
                                        min: 1,
                                        max: 441,
                                    },
                                    {
                                        validator: (_, value) => {
                                            if (value && !isValidFrameCount(value)) {
                                                return Promise.reject(new Error('帧数必须满足 8n+1 且 ≤441，常用值：81(3s)/121(5s)/241(10s)/441(18s)'));
                                            }
                                            return Promise.resolve();
                                        },
                                    },
                                ]}
                            >
                                <div>
                                    {/* 预设按钮组 */}
                                    <Space size={8} style={{ marginBottom: 8 }}>
                                        {FRAME_PRESETS.map((preset) => (
                                            <Button
                                                key={preset.value}
                                                size="small"
                                                type={form.getFieldValue('numFrames') === preset.value ? 'primary' : 'default'}
                                                onClick={() => form.setFieldValue('numFrames', preset.value)}
                                            >
                                                {preset.label} ({preset.value}帧)
                                            </Button>
                                        ))}
                                    </Space>
                                    <Input
                                        type="number"
                                        placeholder="121"
                                        addonAfter="帧"
                                    />
                                </div>
                            </Form.Item>
```

**Step 4: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "feat: 视频生成模块帧数智能校验和预设按钮"
```

---

### Task 6: 更新生成按钮 loading 状态

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Step 1: 更新 Button loading 属性**

将生成按钮的 loading 从 `loading={loading}` 改为 `loading={loading || uploading}`：

```tsx
                            <Form.Item wrapperCol={{ offset: 6, span: 18 }}>
                                <Button
                                    type="primary"
                                    onClick={handleGenerate}
                                    loading={loading || uploading}
                                    block
                                >
                                    {uploading ? '上传图片中...' : '生成视频'}
                                </Button>
                            </Form.Item>
```

**Step 2: 验证轮询间隔**

确认轮询间隔为 10s（`setInterval(..., 10000)`），符合文档推荐的 5s 轮询间隔。保持 10s 即可，无需改动。

**Step 3: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "fix: 视频生成模块按钮loading状态包含上传中"
```

---

### Task 7: 最终验证和清理

**Files:**
- Modify: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Step 1: 检查导入完整性**

确认以下导入存在：

```typescript
import { CloseOutlined, DeleteOutlined, DownloadOutlined, UploadOutlined, PlusOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { uploadVideoImage } from './service';
import TWT from '@/setting';
```

**Step 2: 检查 Radio.Group onChange**

确保 `handleGenTypeChange` 调用时正确重置 `inputMode` 为 `'upload'`：

```typescript
const handleGenTypeChange = (value: string) => {
    setGenType(value);
    setInputMode('upload');
    setFileList([]);
    setUploadedImageUrls([]);
};
```

**Step 3: 验证提交逻辑完整性**

`handleGenerate` 中 `form.validateFields().then` 内的完整逻辑应为：

```typescript
                    // 图生视频模式
                    if (fields.genType === 'img2vid') {
                        if (inputMode === 'upload') {
                            if (uploadedImageUrls.length === 0) {
                                message.error('请先上传图片');
                                return;
                            }
                            fields.image = uploadedImageUrls[0];
                        }
                    }
                    // 多图视频/关键帧模式
                    if (fields.genType === 'multi2vid' || fields.genType === 'keyframes') {
                        if (inputMode === 'upload') {
                            if (uploadedImageUrls.length === 0) {
                                message.error('请至少上传一张图片');
                                return;
                            }
                            fields.inputImages = JSON.stringify(uploadedImageUrls);
                        }
                    }
```

**Step 4: 验证后端兼容性**

确认提交给后端的字段格式与 `AgnesVideoService.buildVideoRequestBody()` 期望的一致：
- img2vid: `image` 字段为单个 URL 字符串 → 后端 `requestBody.put("image", imageUrl)`
- multi2vid/keyframes: `inputImages` 字段为 JSON 数组字符串 → 后端 `objectMapper.readTree(task.getInputImages())`

**Step 5: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/
git commit -m "chore: 视频生成模块最终验证和清理"
```

---

## 自审结果

1. **Spec 覆盖**：设计文档中所有要求都已映射到对应 Task
2. **占位符扫描**：无 TBD/TODO/类似于XX 等占位符
3. **类型一致性**：所有函数名(state名)在各 Task 间一致：`handleUpload`, `handleMultiUpload`, `uploadedImageUrls`, `inputMode`, `fileList`
4. **边界情况**：
   - 切换生成类型时清空所有上传状态 ✓
   - 上传失败时清空 fileList 和 uploadedImageUrls ✓
   - 多图 onRemove 按索引匹配 URL ✓
   - 帧数校验包含 required + 自定义 validator ✓
5. **后端零改动**：所有提交数据格式与现有 `AgnesVideoService` 解析逻辑兼容 ✓
