import { request } from '@umijs/max';

// 请求的控制器名称
const controller = '/ai/creation';

// ════════════ 类型定义（与 ai-service /ai/creation 返回结构对齐） ════════════

export interface AiShot {
    shot_id: number;
    index: number;
    role?: string;
    text: string;
    mode: string;
    image_prompt?: string;
    video_prompt?: string;
    image_url?: string;
    clip_url?: string;
    audio_url?: string;
    audio_duration_ms?: number;
    shot_status: string;
    error_msg?: string;
}

export interface AiPublishPackage {
    title?: string;
    caption?: string;
    tags?: string[];
    ai_declaration?: string;
}

export interface AiProject {
    project_id: number;
    title: string;
    source_type: string;
    status: string;
    topic?: string;
    script_text?: string;
    /** §4 分镜 JSON（meta/audio/subtitles/shots 外壳） */
    storyboard?: Record<string, any> | null;
    persona_id?: number;
    template_id?: number;
    final_video_url?: string;
    cover_url?: string;
    publish_package?: AiPublishPackage | null;
    error_msg?: string;
    create_time?: string;
    shots: AiShot[];
}

export interface AiViralTemplate {
    template_id: number;
    title?: string;
    status: string;
    hook_type?: string;
    duration_sec?: number;
    shot_count?: number;
    asr_text?: string;
    structure?: Record<string, any> | null;
    source_video_url?: string;
    error_msg?: string;
    create_time?: string;
}

export interface AiPersona {
    persona_id: number;
    name: string;
    description?: string;
    style_fewshot?: string;
    look_image_urls?: string[];
    voice_id?: string;
    voice_name?: string;
    banned_words?: string;
    status: number;
    create_time?: string;
}

// ════════════ 拆解 ════════════

/**
 * 提交爆款拆解（分享链接）
 */
export async function decompose(data: { source_url: string }) {
    return request<{ code: number; msg?: string; data?: { template_id: number } }>(
        `${controller}/decompose`,
        { method: 'post', data },
    );
}

/**
 * 上传视频拆解（yt-dlp 解析失败的兜底）
 */
export async function decomposeUpload(formData: FormData) {
    return request<{ code: number; msg?: string; data?: { template_id: number } }>(
        `${controller}/decompose-upload`,
        {
            method: 'post',
            data: formData,
            requestType: 'form',
        },
    );
}

/**
 * 拆解模板详情（轮询用）
 */
export async function getTemplate(templateId: string | number) {
    return request<{ code: number; msg?: string; data?: AiViralTemplate }>(
        `${controller}/template/${templateId}`,
        { method: 'get' },
    );
}

/**
 * 模板库列表
 */
export async function listTemplates() {
    return request<{ code: number; msg?: string; data?: { rows: AiViralTemplate[] } }>(
        `${controller}/templates`,
        { method: 'get' },
    );
}

export async function removeTemplates(templateIds: string) {
    return request<{ code: number; msg?: string }>(
        `${controller}/template/${templateIds}`,
        { method: 'delete' },
    );
}

export interface AiModelOption {
    model_id: number;
    model_name: string;
    model_type: string;
    default_flag: number;
}

// ════════════ 模型 ════════════

/**
 * 创作域可用模型列表（模型管理中启用的 LLM/IMAGE/VIDEO）
 */
export async function listCreationModels(modelType?: string) {
    return request<{ code: number; msg?: string; data?: { rows: AiModelOption[] } }>(
        `${controller}/models${modelType ? `?model_type=${modelType}` : ''}`,
        { method: 'get' },
    );
}

// ════════════ 项目 ════════════

/**
 * 创建项目（同步生成口播稿 + 分镜，约 20-60s）
 */
export async function createProject(data: {
    source_type: string;
    template_id?: number;
    persona_id?: number;
    topic: string;
    llm_model_id?: number;
}) {
    return request<{ code: number; msg?: string; data?: AiProject }>(
        `${controller}/projects`,
        // 口播稿+审核+分镜+16 次 prompt 增强的全链路同步生成，最慢可到数分钟
        { method: 'post', data, timeout: 300000 },
    );
}

/**
 * 项目列表
 */
export async function listProjects() {
    return request<{ code: number; msg?: string; data?: { rows: AiProject[] } }>(
        `${controller}/projects`,
        { method: 'get' },
    );
}

/**
 * 删除项目（Java 本地库软删）
 */
export async function removeProjects(projectIds: string) {
    return request<{ code: number; msg?: string }>(
        `${controller}/projects/${projectIds}`,
        { method: 'delete' },
    );
}

/**
 * 项目详情（状态机 + 分镜，轮询用）
 */
export async function getProject(projectId: string | number) {
    return request<{ code: number; msg?: string; data?: AiProject }>(
        `${controller}/projects/${projectId}`,
        { method: 'get' },
    );
}

/**
 * 保存人工编辑后的分镜（§4 结构 JSON，按 index 同步分镜行）
 */
export async function saveStoryboard(
    projectId: string | number,
    storyboard: Record<string, any>,
) {
    return request<{ code: number; msg?: string; data?: AiProject }>(
        `${controller}/projects/${projectId}/storyboard`,
        {
            method: 'put',
            data: { storyboard },
        },
    );
}

/**
 * 启动渲染（可选指定生图/生视频模型，选择会落库供单镜重试沿用）
 */
export async function renderProject(
    projectId: string | number,
    data?: { image_model_id?: number; video_model_id?: number },
) {
    return request<{ code: number; msg?: string; data?: { project_id: number; status: string } }>(
        `${controller}/projects/${projectId}/render`,
        { method: 'post', data },
    );
}

/**
 * 镜级重试
 */
export async function retryShot(
    projectId: string | number,
    shotId: string | number,
    stage: string,
) {
    return request<{ code: number; msg?: string }>(
        `${controller}/projects/${projectId}/shots/${shotId}/retry`,
        {
            method: 'post',
            data: { stage },
        },
    );
}

/**
 * 生成发布包
 */
export async function publishPackage(
    projectId: string | number,
    data?: { extra_instructions?: string },
) {
    return request<{ code: number; msg?: string; data?: AiPublishPackage }>(
        `${controller}/projects/${projectId}/publish-package`,
        {
            method: 'post',
            data: data || {},
        },
    );
}

/**
 * 一键多变体：克隆项目并生成 N 版替换钩子（正文与各镜产物复用）
 */
export async function createHookVariants(projectId: string | number, count: number) {
    return request<{
        code: number;
        msg?: string;
        data?: { parent_id: number; variants: { project_id: number; title: string; hook: string; angle: string }[] };
    }>(`${controller}/projects/${projectId}/variants`, {
        method: 'post',
        data: { count },
        timeout: 120000,
    });
}

// ════════════ 人设 ════════════

export async function listPersonas() {
    return request<{ code: number; msg?: string; data?: { rows: AiPersona[] } }>(
        `${controller}/personas`,
        { method: 'get' },
    );
}

export async function savePersona(data: Partial<AiPersona> & { persona_id?: number }) {
    return request<{ code: number; msg?: string; data?: AiPersona }>(
        `${controller}/personas`,
        { method: 'post', data },
    );
}

export async function removePersona(personaId: string | number) {
    return request<{ code: number; msg?: string }>(
        `${controller}/personas/${personaId}`,
        { method: 'delete' },
    );
}

export async function generateLook(data: {
    persona_id: number;
    appearance_prompt: string;
    count?: number;
}) {
    return request<{ code: number; msg?: string }>(
        `${controller}/personas/look`,
        { method: 'post', data },
    );
}

/**
 * 一键设为定妆照：把一张已有图片提升为人设形象（primary=true 时插到数组首位）
 */
export async function setPersonaLook(data: {
    persona_id: number;
    image_url: string;
    primary?: boolean;
}) {
    return request<{ code: number; msg?: string; data?: { persona_id: number; look_image_urls: string[] } }>(
        `${controller}/personas/look/set`,
        { method: 'post', data },
    );
}
