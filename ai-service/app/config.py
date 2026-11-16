from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_host: str = "0.0.0.0"
    app_port: int = 8001
    database_url: str = "mysql+aiomysql://root:123456@localhost:3306/twelvet-fast"
    # Redis（Agent 会话 checkpointer / 工具确认令牌 / 幂等键）。
    # 不可用时自动退化为进程内实现（重启丢会话、令牌不跨进程），不影响其他功能
    redis_url: str = "redis://localhost:6379/0"
    agent_session_ttl_minutes: int = 7 * 24 * 60  # 会话 checkpoint 保留 7 天
    redis_connect_timeout: int = 2
    # ── Java 内部接口（写操作必须经 Java，禁止直写业务表）──
    # 背景：AI 导购 Agent 原先直接 INSERT trade_order（列清单无 tenant_id），会落到 DB 的
    # NOT NULL DEFAULT 1 上；而订单租户本应与商品同租户。tenant.enabled 当前为 true，
    # 因此那样的写入会造成跨租户数据混入。现改为调用 Java 内部端点，由 Java 统一负责
    # 租户、运费、商品快照等全部订单写入细节。
    java_base_url: str = "http://localhost:8080"
    # 服务间令牌，需与 Java 侧 twelvet.internal.token（环境变量 INTERNAL_API_TOKEN）一致。
    # 留空 = Java 侧按"失败关闭"拒绝内部接口：此时 Agent 下单会明确报错，绝不回退为直写数据库。
    internal_api_token: str = ""
    java_internal_timeout: int = 15
    # 文本 LLM：统一使用 Agnes 3.0 Flash（512K 上下文，支持文本 + 图像 URL 输入）。
    # 依据《docs/ai/模型调用文档说明.md》；覆盖导购对话 / RAG 对话 / 提示词增强等 config 驱动路径。
    # 表驱动路径（文案/日报/识图/初审/配图/图片/视频/音乐）走 ai_model 表的 default_flag。
    ai_base_url: str = "https://apihub.agnes-ai.com/v1"
    ai_api_key: str = ""
    ai_model: str = "agnes-3.0-flash"
    ai_temperature: float = 0.3
    # 稳定性：LLM 显式超时与 SDK 层重试
    ai_llm_timeout: int = 60
    ai_llm_max_retries: int = 1
    # ── Agent 韧性（超时与循环保护）──
    agent_tool_timeout: int = 20      # 单次工具执行超时（秒），防 DB/外部调用卡死拖垮对话
    agent_graph_timeout: int = 120    # 单轮对话整图执行超时（秒），超时中止并回错误事件
    agent_recursion_limit: int = 30   # LangGraph 图步数上限（防死循环）
    agnes_connect_timeout: int = 10
    agnes_read_timeout: int = 600
    # RAG 配置
    embedding_base_url: str = "https://api.siliconflow.cn/v1"
    embedding_api_key: str = ""
    embedding_model: str = "BAAI/bge-m3"
    faiss_index_path: str = "./data/faiss_indexes"
    # RAG 重排（cross-encoder 精排；失败自动回退原序）
    rerank_enabled: bool = True
    rerank_model: str = "BAAI/bge-reranker-v2-m3"
    rerank_top_n: int = 5
    rerank_timeout: int = 5

    # ── 平台规则 RAG（rag_showcase）──
    # 语料是 Git 版本化的 Markdown；索引是派生产物，可随时重建
    rag_showcase_corpus_path: str = "./data/rag_corpus/rules"
    rag_showcase_index_dir: str = "./data/faiss_indexes"
    rag_showcase_trace_path: str = "./data/rag_traces.jsonl"
    rag_showcase_trace_limit: int = 100
    rag_showcase_top_k: int = 5
    rag_showcase_recall_k: int = 20
    rag_showcase_min_rerank_score: float = 0.05
    rag_showcase_min_vector_score: float = 0.30
    rag_showcase_auto_rebuild: bool = True

    # TTS 语音合成（OpenAI 兼容 /audio/speech 协议，默认 SiliconFlow CosyVoice2；
    # tts_api_key 为空时复用 embedding_api_key）
    tts_base_url: str = "https://api.siliconflow.cn/v1"
    tts_api_key: str = ""
    tts_model: str = "FunAudioLLM/CosyVoice2-0.5B"
    tts_voice: str = "FunAudioLLM/CosyVoice2-0.5B:alex"
    tts_response_format: str = "mp3"
    tts_max_chars: int = 1000
    tts_timeout: int = 60
    # 上传文件静态资源前缀（UPLOAD 文档解析/视觉审查时用于拼接相对路径）
    # 与前端 ui/src/setting.tsx 的 static 保持一致（2026-09-23 已迁至自有 OSS bucket）
    static_base_url: str = "https://your-bucket.oss-cn-beijing.aliyuncs.com"
    doc_download_timeout: int = 30

    # 提示词智能增强
    ai_prompt_enhance_enabled: bool = True
    ai_prompt_enhance_model: str = ""
    ai_prompt_enhance_temperature: float = 0.7
    ai_prompt_min_length: int = 10

    # ── AI 音乐: 商汤 senseaudio ──
    senseaudio_base_url: str = "https://api.senseaudio.cn"
    senseaudio_api_key: str = ""
    senseaudio_music_model: str = "senseaudio-music-2.0-260626"
    senseaudio_timeout: int = 30
    senseaudio_poll_interval: int = 5
    senseaudio_poll_max_wait: int = 300

    # ── AI 音乐: 硅基流动 克隆音色 ──
    siliconflow_voice_clone_model: str = "IndexTeam/IndexTTS-2"
    siliconflow_voice_api_key: str = ""  # 为空时复用 tts_api_key / embedding_api_key
    siliconflow_voice_min_duration_s: int = 10
    siliconflow_voice_max_duration_s: int = 60

    # ── AI 音乐: 配额 ──
    music_quota_song_cost: int = 5
    music_quota_lyrics_cost: int = 1
    music_quota_voice_train_cost: int = 3
    music_quota_voice_train_daily_limit: int = 1

    # ── OSS 转存（阿里云）──
    # 背景：SenseNova（sensenova-* 模型）返回 24h 过期的预签名 URL，直接落库会导致图片裂图。
    # 生成完成后下载图片并上传自有 OSS，落库永久 URL；Agnes/senseaudio 链接实测永久有效，无需转存。
    # oss_access_key 为空时自动跳过转存（落库原始 URL，行为与旧版一致）
    oss_access_key: str = ""
    oss_secret_key: str = ""
    oss_bucket: str = "your-bucket"
    oss_endpoint: str = "https://oss-cn-beijing.aliyuncs.com"
    oss_transfer_enabled: bool = True
    oss_transfer_timeout: int = 120

    # ── AI 创作工作台（creation 域）──
    # 本地工作目录（下载原片/分镜产物/合成中间件），产物最终转存 OSS；
    # OSS 未配置时回退本地存储，经 /ai/creation/media/{path} 提供访问
    creation_work_dir: str = "./data/creation"
    # ASR 语音识别（OpenAI 兼容 /audio/transcriptions，默认 SiliconFlow SenseVoice；
    # asr_api_key 为空时复用 tts_api_key / embedding_api_key）
    asr_base_url: str = "https://api.siliconflow.cn/v1"
    asr_api_key: str = ""
    asr_model: str = "FunAudioLLM/SenseVoiceSmall"
    asr_timeout: int = 180
    # 合成引擎
    creation_video_poll_interval: int = 5
    creation_video_poll_timeout: int = 900
    creation_max_shots: int = 8
    creation_shot_max_chars: int = 25
    # 逐镜生成并行度（设计 §6.3）：各镜独立会话/任务，2 路并发对 vendor 友好且近乎减半总时长
    creation_shot_concurrency: int = 2
    # 人设未配音色时的默认 TTS 音色（商汤系统音色 ID，34 个可用，见 /ai/music/voices/platform）
    creation_default_voice: str = "female_0006_a"  # 温柔御姐
    # 创作渲染首选模型（按 model_name 匹配 ai_model 表，空串回落 default_flag 默认）。
    # 创作是"质量敏感"场景，与全局默认（flash 速度档）解耦：
    # 图 → Doubao Seedream 5.0，视频 → Doubao Seedance 2.0；失败自动回落默认及其它同类型模型
    creation_image_model: str = "doubao-seedream-5-0-260128"
    creation_video_model: str = "doubao-seedance-2-0-260128"
    # 创作分镜图档位（resolve_size 档位表），2K 起步——1K 竖屏成片放大后明显糊
    creation_image_size_tier: str = "2K"
    # 口播稿质量管线：一次生成 N 个不同开头策略的候选并自评择优（+1 次调用的输出成本），
    # 随后由「优化师」做一轮修订（弱钩子/书面语/长句），失败降级用初稿
    creation_script_candidates: int = 2
    creation_script_refine: bool = True
    # WhisperX 词级对齐（可选增强，对应 hypit 的"锚定到词"）：本地 pip install whisperx 后生效，
    # 未安装/初始化失败自动回退句级 SRT。TTS 干声对齐用 base/small 档足够；
    # device=auto 时 cuda 可用即用（RTX 5060 8G 建议 cuda+float16，CPU 退 int8）
    creation_align_enabled: bool = True
    creation_align_model: str = "base"
    creation_align_language: str = "zh"
    creation_align_device: str = "auto"  # auto|cuda|cpu
    creation_align_compute_type: str = "auto"  # auto→cuda:float16 / cpu:int8
    # 字幕/封面字体：Windows 用系统字体名（libass 经 fontconfig 匹配），
    # 容器内建议 fonts-noto-cjk（Dockerfile 已装），样式经 force_style 传入
    creation_subtitle_font: str = "Noto Sans CJK SC"
    creation_workdir_ttl_hours: int = 24

    # 腾讯位置服务 IP 定位（首页城市识别主源，免费 key 申请 lbs.qq.com，留空则退 ip-api）
    tencent_lbs_key: str = ""

    # ── Agent 长期记忆（用户画像）──
    agent_memory_enabled: bool = True
    agent_memory_extract_model: str = ""        # 空=ai_model 表 LLM 默认（勿硬编码 settings.ai_model）
    agent_memory_inject_limit: int = 20          # 单轮最多注入条数
    agent_memory_max_rows: int = 50              # 每用户行数上限
    agent_memory_half_life_days: int = 90        # 置信度衰减半衰期
    agent_memory_min_confidence: float = 0.3     # 低于此有效置信度不注入
    agent_memory_trait_min_hits: int = 3         # 特质升格：最少证据数
    agent_memory_trait_min_sessions: int = 2     # 特质升格：最少跨会话数
    agent_memory_trait_cooldown_hours: int = 24  # 同用户聚合冷却
    agent_memory_session_gap_hours: int = 6      # 同 session 内超过此间隔才算一次独立命中（替代纯 session_id 判定）
    agent_memory_extract_max_msgs: int = 6       # 提取输入消息条数
    agent_memory_extract_min_chars: int = 6      # 短于此时不提取

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


def missing_critical_settings(s: Settings) -> list[str]:
    """列出为空的关键配置项（纯函数，接收 Settings 参数便于单测）。

    对应降级行为：ai_api_key → LLM 全挂；internal_api_token → Java 侧失败关闭、
    Agent 下单必失败；embedding_api_key → RAG 检索不可用。启动时汇总告警（不阻断）。
    """
    return [name for name in ("ai_api_key", "internal_api_token", "embedding_api_key") if not getattr(s, name)]


settings = Settings()
