/** AI 音乐 API service
 *
 * ⚠️ 必须用 `@umijs/max` 的 request（app.tsx 里注册的 errorConfig 会注入
 * Bearer token / X-Tenant-Code 并拼接 REQUEST_URI）。
 * 曾用 `@alita/request`：它是独立的 umi-request 实例，没人调 setRequestConfig，
 * 于是不带 token 且 URL 前缀约定不同 → 请求 401，页面下拉框「看不到任何模型」。
 *
 * ⚠️ URL 不要写 `/api` 前缀：拦截器会自动拼 `http://localhost:8080`，
 * 写成 `/api/ai/...` 会变成 `/api/ai/...`，后端无此路由（报 No static resource）。
 */
import { request } from '@umijs/max';

/**
 * `@umijs/max` 的 request 默认返回响应体 `{ code, msg, data }`（getResponse=false）。
 * 业务侧只关心 data，这里统一解开一层，使各函数的声明返回类型与实际一致。
 */
async function unwrap<T>(p: ReturnType<typeof request>): Promise<T> {
    const res: any = await p;
    return (res?.data ?? res) as T;
}

export interface PlatformVoice {
    voice_id: string;
    voice_name: string;
    description: string[];
    category: 'system' | 'voice_clone' | 'voice_generation';
    vendor: string;
    created_time?: string;
}

export interface VoiceProfile {
    id: number;
    userId: number;
    name: string;
    vendor: string;
    vendorVoiceId: string;
    vendorModel?: string;
    sampleText?: string;
    durationMs?: number;
    status: 0 | 1 | 2 | 3; // 0=上传中 1=训练中 2=可用 3=失败
    consentSigned: number;
    errorMsg?: string;
    createTime?: string;
}

export interface LyricsItem {
    title?: string;
    lyrics?: string;
    caption?: string;
    bpm?: number;
    key_scale?: string;
    time_signature?: string;
    duration?: number;
    vocal_language?: string;
}

export interface MusicWork {
    id: number;
    userId: number;
    type: 1 | 2 | 3 | 4;
    title?: string;
    styleTags?: string;
    bpm?: number;
    keySignature?: string;
    lyrics?: string;
    prompt?: string;
    voiceProfileId?: number;
    vendor: string;
    vendorTaskId?: string;
    vendorVoiceId?: string;
    audioUrl?: string;
    audioDurationMs?: number;
    coverUrl?: string;
    status: 0 | 1 | 2 | 3;
    errorMsg?: string;
    quotaCost?: number;
    createTime?: string;
    completedAt?: string;
}

// ── 平台音色(代理 BFF, BFF 再代理 Python ai-service) ──

export async function listPlatformVoices(voiceType = 'all') {
    return unwrap<{ voices: PlatformVoice[] }>(
        request('/ai/music/voices/platform', {
            method: 'GET',
            params: { voice_type: voiceType },
        }),
    );
}

// ── 用户音色 ──

export async function listMyVoices(params?: { status?: number; name?: string }) {
    // ⚠️ 分页接口由 Java 的 TableDataInfo 承载，字段是 `records`（不是 `rows`）：
    //    TableDataInfo 只声明了 { total, records }，写 rows 会永远是 undefined。
    //    （Python 侧 /ai/model/by-type 返回的才是 rows，两者不要混用）
    return unwrap<{ records: VoiceProfile[]; total: number }>(
        request('/ai/music/voices/pageQuery', {
            method: 'GET',
            params,
        }),
    );
}

export async function deleteVoice(id: number) {
    return unwrap<string>(request(`/ai/music/voices/${id}`, { method: 'DELETE' }));
}

/** 上传样本训练克隆音色 */
export async function trainVoice(form: {
    name: string;
    text: string;
    durationMs?: number;
    consentSigned: boolean;
    consentIp?: string;
    file: File;
}) {
    const fd = new FormData();
    fd.append('name', form.name);
    fd.append('text', form.text);
    if (form.durationMs !== null && form.durationMs !== undefined)
        fd.append('durationMs', String(form.durationMs));
    fd.append('consentSigned', String(form.consentSigned));
    if (form.consentIp) fd.append('consentIp', form.consentIp);
    fd.append('file', form.file);
    return unwrap<{ id: number; vendor: string; vendorVoiceId: string; status: number }>(
        request('/ai/music/voices/train', { method: 'POST', data: fd }),
    );
}

// ── AI 歌词 ──

export async function draftLyrics(req: {
    prompt: string;
    style?: string;
    rhyme?: string;
    language?: string;
    expect_duration?: number;
}) {
    return unwrap<{ candidates: LyricsItem[] }>(
        request('/ai/music/lyrics/draft', {
            method: 'POST',
            data: req,
        }),
    );
}

export async function reviseLyrics(req: { original_lyrics: string; instruction: string }) {
    return unwrap<{ candidates: LyricsItem[] }>(
        request('/ai/music/lyrics/revise', {
            method: 'POST',
            data: req,
        }),
    );
}

// ── 模型列表(按类型) ──

export interface ModelOption {
    model_id: number;
    model_name: string;
    model_alias?: string;
    model_provider: string;
    is_default: boolean;
    /** IMAGE 模型声明的输出尺寸白名单；空数组/缺失表示不限制 */
    sizes?: string[];
}

export async function listModelsByType(modelType: 'IMAGE' | 'VIDEO' | 'MUSIC') {
    return unwrap<{ total: number; rows: ModelOption[] }>(
        request(`/ai/model/by-type/${modelType}`, { method: 'GET' }),
    );
}

// ── 音乐作品 ──

export async function submitSong(req: {
    type: 'text_to_song' | 'platform_cover' | 'clone_tts' | 'instrumental';
    prompt?: string;
    style?: string;
    lyrics?: string;
    voiceId?: string;
    voiceProfileId?: number;
    gender?: 'male' | 'female' | 'any' | 'duet';
    bpm?: number;
    expectDuration?: number;
    language?: string;
    title?: string;
    styleTags?: string;
    modelId?: number;
}) {
    return unwrap<{ id: number; vendorTaskId: string; status: number }>(
        request('/ai/music/songs/submit', { method: 'POST', data: req }),
    );
}

export async function pollSongStatus(id: number) {
    return unwrap<{
        id: number;
        status: number;
        audioUrl: string;
        coverUrl: string;
        durationMs: number;
        errorMsg: string;
    }>(request(`/ai/music/songs/status/${id}`, { method: 'GET' }));
}

export async function listMyWorks(params?: { status?: number; type?: number; title?: string }) {
    // 同上：Java TableDataInfo 的字段是 `records`
    return unwrap<{ records: MusicWork[]; total: number }>(
        request('/ai/music/songs/pageQuery', {
            method: 'GET',
            params,
        }),
    );
}

export async function getWork(id: number) {
    return unwrap<MusicWork>(request(`/ai/music/songs/${id}`, { method: 'GET' }));
}

export async function deleteWork(id: number) {
    return unwrap<string>(request(`/ai/music/songs/${id}`, { method: 'DELETE' }));
}
