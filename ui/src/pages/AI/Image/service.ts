import { request } from '@umijs/max';
import { upload } from '@/utils/twelvet';

// 请求的控制器名称
const controller = '/ai/image';

/**
 * 生成图片
 * @param data 数据参数
 */
export async function generateImage(data: { [key: string]: any }) {
    return request(`${controller}/generate`, {
        method: `post`,
        data,
    });
}

/**
 * 查询AI图片生成任务状态（轮询用）
 * @param taskId 任务ID
 */
export async function getImageTaskStatus(taskId: string | number) {
    return request(`${controller}/status/${taskId}`, {
        method: `get`,
    });
}

/**
 * 查询AI图片任务列表
 * @param query 查询参数
 */
export async function pageQueryImageTask(query: { [key: string]: any }) {
    return request(`${controller}/pageQuery`, {
        method: `get`,
        params: query,
    });
}

/**
 * 查询AI图片任务详细
 * @param taskId 任务ID
 */
export async function getImageTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, {
        method: `get`,
    });
}

/**
 * 删除AI图片任务
 * @param taskId 任务ID
 */
export async function delImageTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, {
        method: `delete`,
    });
}

/**
 * 上传图片到DFS
 * @param formData 文件表单数据
 */
export async function uploadImage(formData: FormData) {
    return upload('/dfs/commonUpload', formData);
}
