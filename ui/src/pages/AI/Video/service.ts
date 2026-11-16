import { request } from '@umijs/max';
import { upload } from '@/utils/twelvet';

// 请求的控制器名称
const controller = '/ai/video';

/**
 * 生成视频
 * @param data 数据参数
 */
export async function generateVideo(data: { [key: string]: any }) {
    return request(`${controller}/generate`, {
        method: `post`,
        data,
    });
}

/**
 * 查询视频生成状态
 * @param taskId 任务ID
 */
export async function queryVideoStatus(taskId: string | number) {
    return request(`${controller}/status/${taskId}`, {
        method: `get`,
    });
}

/**
 * 查询AI视频任务列表
 * @param query 查询参数
 */
export async function pageQueryVideoTask(query: { [key: string]: any }) {
    return request(`${controller}/pageQuery`, {
        method: `get`,
        params: query,
    });
}

/**
 * 查询AI视频任务详细
 * @param taskId 任务ID
 */
export async function getVideoTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, {
        method: `get`,
    });
}

/**
 * 删除AI视频任务
 * @param taskId 任务ID
 */
export async function delVideoTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, {
        method: `delete`,
    });
}

/**
 * 上传视频图片到DFS
 * @param formData 文件表单数据
 */
export async function uploadVideoImage(formData: FormData) {
    return upload('/dfs/commonUpload', formData);
}
