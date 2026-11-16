-- MySQL dump 10.13  Distrib 8.0.37, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: twelvet-fast
-- ------------------------------------------------------
-- Server version	8.0.37

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `_bak_ai_image_task_20260923`
--

DROP TABLE IF EXISTS `_bak_ai_image_task_20260923`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_bak_ai_image_task_20260923` (
  `task_id` bigint NOT NULL DEFAULT '0' COMMENT '任务ID',
  `model_id` bigint NOT NULL COMMENT '模型ID',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词',
  `negative_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '反向提示词',
  `size` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1024x1024' COMMENT '输出尺寸（如1024x1024）',
  `gen_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text2img' COMMENT '生成类型：text2img-文生图，img2img-图生图',
  `input_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入图片URL（img2img模式）',
  `response_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'url' COMMENT '输出格式：url，b64_json',
  `width` int DEFAULT '1024' COMMENT '图片宽度',
  `height` int DEFAULT '1024' COMMENT '图片高度',
  `seed` bigint DEFAULT NULL COMMENT '随机种子',
  `style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风格',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
  `result_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成结果URL',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `_bak_ai_image_task_20260923`
--

LOCK TABLES `_bak_ai_image_task_20260923` WRITE;
/*!40000 ALTER TABLE `_bak_ai_image_task_20260923` DISABLE KEYS */;
INSERT INTO `_bak_ai_image_task_20260923` VALUES (2,1,'穿上粉色裙子',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-06-27/1782530904870.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/fcd6e7af9cd141b8a523598d6a4e924e/output.png',NULL,'admin','2026-06-27 11:28:30','2026-06-27 11:29:07',0),(4,1,'将图片中人物变得更加性感',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783046533161.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 10:42:44','2026-07-03 10:48:45',0),(6,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047198555.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 10:54:16','2026-07-03 11:00:16',0),(7,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047731567.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:02:13','2026-07-03 11:08:13',0),(8,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:12:08','2026-07-03 11:18:08',0),(9,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:32:10','2026-07-03 11:42:11',0),(10,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783051033926.png','url',1024,1024,NULL,NULL,'failed',NULL,'error code: 520\n','admin','2026-07-03 11:57:27','2026-07-03 13:31:27',0),(32,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_67RyRPfnxENzlcijXpMgrKaR0MBSSPLX/output.png',NULL,NULL,'2026-08-06 16:09:56','2026-08-06 16:10:17',0),(33,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6gkjf5QTMUaxfDdwrpdBxfCjo8BsWCu3/output.png',NULL,NULL,'2026-08-06 16:09:57','2026-08-06 16:10:19',0),(34,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_W1IjuIKZuPxhBLPcJbye5L7W8HljEE3H/output.png',NULL,NULL,'2026-08-06 16:10:14','2026-08-06 16:10:57',0),(35,1,'将图片人物变成朋克赛博风格',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6uzo0OxlW2Y8lQoXwgSMRKUxVkCMbgko/output.png',NULL,NULL,'2026-08-10 10:38:05','2026-08-10 10:38:30',0),(36,1,'将图片人物变成朋克赛博风格，并且要正脸',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_EjrymtBgwwUEyGdPaEBLhGtMGDzDFEJr/output.png',NULL,NULL,'2026-08-10 10:41:29','2026-08-10 10:41:49',0),(37,1,'将图片人物变成朋克赛博风格，并且要正脸',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_hU9zHs3j83VS1Sm166UnDbpKR6ShOHHN/output.png',NULL,NULL,'2026-08-10 10:56:23','2026-08-10 10:56:52',0),(38,1,'美化一下，变成大帅哥，但是要保留原本的人物的特征',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786330715467.png','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6yL7LYzYbn0KONMxYYiZUnfMj70FZS9S/output.png',NULL,NULL,'2026-08-10 11:00:33','2026-08-10 11:00:51',0),(39,1,'正脸改成中国水墨侠客风格',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_dZDslANaNGRFMa2zX2UsEntELcUgkyXP/output.png',NULL,NULL,'2026-08-11 11:22:48','2026-08-11 11:23:14',0),(40,1,'正脸改成中国水墨侠客风格',NULL,'1024x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png',NULL,NULL,'2026-08-11 11:22:53','2026-08-11 11:23:15',0),(45,1,'一位优雅的女性角色，身穿精致的粉色小裙子，裙摆轻盈飘逸，采用柔和的粉色调，带有细腻的蕾丝或薄纱材质细节。裙子设计简约而甜美，可能带有蝴蝶结、荷叶边等装饰元素。整体画面呈现梦幻浪漫的氛围，光线柔和温暖，色彩以粉色为主调，搭配清新的背景环境，人物姿态优雅自然，展现出青春活力的气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted, poorly drawn, bad proportions, extra limbs, missing limbs','768x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg','url',768,1024,3221788435,'cinematic','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_BslMpw17QcGdZK1dtdR9XMdx8NrKtQo2/output.png',NULL,NULL,'2026-08-11 11:47:30','2026-08-11 11:47:47',0),(46,1,'一位年轻女性身穿甜美俏皮可爱的粉色小裙子，裙子采用轻盈的雪纺或棉质面料，带有精致的荷叶边装饰和蕾丝花边细节。裙摆蓬松灵动，呈现柔和的樱花粉色调，搭配小巧的蝴蝶结点缀。整体造型充满少女感与活泼气息，姿态轻盈俏皮，面带甜美微笑，眼神明亮灵动。画面采用柔和的自然光或梦幻柔光效果，色调温暖清新，背景可选用花丛、糖果色房间或童话般的小径，营造出浪漫可爱的氛围。构图聚焦人物全身或半身，突出裙子的飘逸质感与甜美气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted face, poor lighting, dull colors, oversaturated, noisy, grainy, low resolution','768x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg','url',768,1024,1921687663,'anime','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_4YA7cs3qAkcXnpQZ20UCuJRYXNvLhxPN/output.png',NULL,NULL,'2026-08-11 11:49:20','2026-08-11 11:49:40',0),(47,1,'一位年轻可爱的女孩，身穿甜美俏皮的粉色小裙子，裙子采用轻盈的薄纱材质，带有精致的荷叶边装饰和蝴蝶结点缀，裙摆蓬松柔软，呈现出梦幻的少女感。整体造型清新活泼，配色以柔和的粉色为主调，搭配白色蕾丝细节。人物表情甜美灵动，眼神清澈明亮，散发青春活力的气息。背景为柔和的浅色调，光线温暖柔和，营造出浪漫温馨的氛围，构图注重展现裙子的飘逸质感和人物的可爱气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted face, poorly drawn hands, extra limbs, bad proportions, oversaturated, dark, gloomy','768x1024','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420527155.webp','url',768,1024,863355902,'anime','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_T4RBYofefRcLRVVmNWBqVbVkMhIShK8A/output.png',NULL,NULL,'2026-08-11 11:55:43','2026-08-11 11:56:03',0),(48,1,'一位年轻女性平躺于柔软的白色床单上，羞涩的表情——双颊微红，眼神温柔地望向镜头，睫毛轻垂，嘴角含着一丝腼腆的微笑。双腿高高抬起并微微弯曲，姿态轻盈自然，穿着简约的浅色家居服，肌肤质感细腻柔和。场景为温馨的卧室，清晨柔和的自然光从窗户斜射进来，形成温暖的金黄色侧逆光，在人物轮廓边缘勾勒出一圈柔和的光晕。构图采用中景平视视角，背景虚化处理，隐约可见床头与淡雅的窗帘。整体氛围宁静、私密而唯美，色调偏暖，低饱和度，带有胶片感的柔和对比度，呈现出一种清新文艺的摄影风格。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, oversaturated, flat lighting, harsh shadows','2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-01/1788266947088.jpg','url',2304,1728,2314301223,'photorealistic','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_oWmX33wngmnyXkabecXbT0XY6WKCRuve/output.png',NULL,NULL,'2026-09-01 20:50:58','2026-09-01 21:01:47',0),(53,38,'一位人物平躺在柔软的平面上，身体放松，双臂自然放置于身体两侧。开始缓慢抬起双腿，双腿从床面缓缓升起，保持腿部线条流畅自然。随后双腿向两侧轻轻岔开，形成一个舒展的Y字形姿态。最后小腿部分微微下垂放松，膝盖保持柔和弯曲，整个动作缓慢优雅，呼吸感明显，展现出身体的柔韧与舒展感。采用中景构图，固定机位平视角度，柔和的室内漫射光，自然肤色质感，画面干净简洁的背景。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, flickering, jittery motion, morphing objects, sudden scene change, extra limbs, distorted joints, unnatural movement, inconsistent character appearance','2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,1052261344,'cinematic','failed',NULL,'Agnes API returned status 404: 404 page not found\n',NULL,'2026-09-10 06:53:47','2026-09-10 07:04:04',1),(54,38,'一位人物平躺在床上，身体舒展放松，双腿缓慢抬起离床面约45度角，随后向两侧自然岔开至舒适幅度，小腿部分微微下垂放松。镜头采用中景固定机位，从正侧面或斜上方俯拍，完整呈现肢体动作轨迹。床品质感柔软，环境光线柔和温暖，氛围宁静居家。动作流畅连贯，无突兀跳跃，保持身体平衡与优雅姿态。','flickering, jittery motion, morphing objects, sudden scene change, deformed limbs, extra limbs, distorted face, blurry, low quality, watermark, text','2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,1358541431,'cinematic','failed',NULL,'Agnes API returned status 404: 404 page not found\n',NULL,'2026-09-10 06:55:48','2026-09-10 07:04:04',1),(57,38,'一位成年女性平躺于白色床单的床上，身体放松伸展，双臂自然放置于身体两侧。她缓慢抬起双腿至垂直角度，随后双腿向两侧岔开形成V字姿态，膝盖保持轻微弯曲，小腿部分缓缓下降至约45度位置后停下。整个动作流畅连贯、速度适中，展现腿部柔韧性与控制力。卧室场景，柔和的自然光从侧窗洒入，营造温馨安静的氛围。固定机位，全身入镜，平视拍摄角度，确保动作全程清晰可见。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, flickering, jittery motion, morphing objects, sudden scene change, distorted proportions, unnatural movement','2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,3748082089,'cinematic','completed','https://dynamic.senseaudio.cn/image/b7804641-9ca7-4770-8f57-530bbef31e05',NULL,NULL,'2026-09-10 07:02:03','2026-09-10 07:03:39',0),(58,38,'一位年轻女性，穿着黑色透肉丝袜，腿部线条修长优雅，面部带有自然的红润气色，双颊泛着淡淡羞涩的红晕。保留原始构图的相机角度与主体姿态，整体穿搭精致时尚。光线柔和均匀，突出丝袜的细腻质感与面部的健康血色感，画面干净高级。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, oversaturated, flat lighting, harsh shadows','2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-11/1789139434533.jpg','url',2304,1728,125189725,'photorealistic','failed',NULL,'senseaudio 调用失败: 商汤调用失败(POST /v1/image/sync status=400): {\"code\":\"upstream_invalid_request\",\"message\":\"图片生成请求无效，请检查输入内容\",\"ref_code\":400100}',NULL,'2026-09-11 23:11:28','2026-09-11 23:11:58',0),(59,38,'一位年轻女性，穿着黑色透肉丝袜，腿部线条修长优雅，面部带有自然的红润气色，双颊泛着淡淡羞涩的红晕。保留原始构图的相机角度与主体姿态，整体穿搭精致时尚。光线柔和均匀，突出丝袜的细腻质感与面部的健康血色感，画面干净高级。',NULL,'2304x1728','img2img','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-11/1789139434533.jpg','url',2304,1728,3107532524,NULL,'completed','https://dynamic.senseaudio.cn/image/72542db3-f0ff-4a73-979b-3cbdaa36fad3',NULL,NULL,'2026-09-11 23:22:56','2026-09-11 23:23:23',0);
/*!40000 ALTER TABLE `_bak_ai_image_task_20260923` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_history`
--

DROP TABLE IF EXISTS `ai_chat_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_history` (
  `msg_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识库ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：USER-用户，ASSISTANT-AI',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`msg_id`),
  KEY `idx_knowledge_id` (`knowledge_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_history`
--

LOCK TABLES `ai_chat_history` WRITE;
/*!40000 ALTER TABLE `ai_chat_history` DISABLE KEYS */;
INSERT INTO `ai_chat_history` VALUES (1,5,NULL,'USER','项目使用什么语言开发的',NULL,'2026-08-10 14:25:36'),(2,5,NULL,'USER','项目是用什么语言写的',NULL,'2026-08-10 14:34:25'),(3,5,NULL,'USER','你是谁',NULL,'2026-08-10 15:10:43'),(4,5,NULL,'USER','你好',NULL,'2026-08-10 15:50:37'),(5,5,NULL,'USER','你好',NULL,'2026-08-10 16:22:11'),(6,5,NULL,'USER','项目是用什么语言写的',NULL,'2026-08-10 16:23:22'),(7,5,NULL,'USER','项目是用什么语言写的',NULL,'2026-08-10 16:33:20'),(8,5,NULL,'USER','项目是用什么语言写的',NULL,'2026-08-10 16:35:25'),(9,5,NULL,'ASSISTANT','\n\n根据资料，TwelveT Fast 后端采用 **Java 17** 开发，前端采用 **TypeScript**（基于 React 18）。',NULL,'2026-08-10 16:35:33'),(10,5,NULL,'USER','你可以回答什么',NULL,'2026-08-10 16:35:50'),(11,5,NULL,'ASSISTANT','\n\n我可以回答关于 **TwelveT Fast** 项目的各类问题，包括但不限于：\n\n- **项目启动**：如何启动项目、模块结构（admin、twelvet-system、twelvet-ai、twelvet-quartz、twelvet-gen）\n- **技术栈**：系统管理、监控、日志、AI 模块等功能说明\n- **RAG 机制**：知识库问答原理、文本切片、向量检索、Embedding 模型等\n- **代码生成器**：twelvet-gen 的使用方式\n- **AI 功能**：AI 聊天、文档管理、知识库管理等\n\n如果你有具体问题，欢迎直接提问！',NULL,'2026-08-10 16:36:01'),(12,5,NULL,'USER','那你挺聪明的',NULL,'2026-08-10 16:36:16'),(13,5,NULL,'ASSISTANT','\n\n谢谢！我是 Agnes，由 Sapiens AI 开发。有什么我可以帮你的吗？',NULL,'2026-08-10 16:36:20'),(14,5,NULL,'USER','what is the return policy?',NULL,'2026-08-29 08:53:00'),(15,5,NULL,'ASSISTANT','\n\n抱歉，提供的知识库中没有关于退货政策（return policy）的信息。该知识库主要涵盖 TwelveT Fast 的技术架构、RAG 机制和模块说明等内容。如有其他项目相关问题，欢迎继续提问。',NULL,'2026-08-29 08:53:09'),(16,5,NULL,'USER','hello',NULL,'2026-08-29 09:11:49'),(17,5,NULL,'ASSISTANT','\n\n你好！我是 Agnes，由 Sapiens AI 开发。有什么我可以帮你的吗？',NULL,'2026-08-29 09:11:52'),(18,5,NULL,'USER','hi there',NULL,'2026-08-29 11:53:53'),(19,5,NULL,'ASSISTANT','\n\n你好！有什么我可以帮你的吗？',NULL,'2026-08-29 11:53:55');
/*!40000 ALTER TABLE `ai_chat_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_creation_project`
--

DROP TABLE IF EXISTS `ai_creation_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_creation_project` (
  `project_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) DEFAULT NULL COMMENT '作品标题',
  `source_type` varchar(20) NOT NULL COMMENT 'viral_clone/original/image_text',
  `template_id` bigint DEFAULT NULL COMMENT '套用的爆款模板',
  `persona_id` bigint DEFAULT NULL COMMENT '使用的人设',
  `topic` varchar(500) DEFAULT NULL COMMENT '选题/主题描述',
  `script_text` text COMMENT '口播稿全文',
  `storyboard_json` text COMMENT '当前生效的分镜 JSON',
  `status` varchar(30) DEFAULT 'draft' COMMENT 'draft/storyboard_ready/generating/composing/done/failed',
  `final_video_url` varchar(500) DEFAULT NULL COMMENT '成片地址',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面地址',
  `publish_package_json` text COMMENT '发布包：标题/文案/标签/AI声明',
  `error_msg` text,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` smallint DEFAULT '0',
  PRIMARY KEY (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_source_type` (`source_type`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作-作品项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_creation_project`
--

LOCK TABLES `ai_creation_project` WRITE;
/*!40000 ALTER TABLE `ai_creation_project` DISABLE KEYS */;
INSERT INTO `ai_creation_project` VALUES (1,'AI浪潮来了，普通人怎么上车？','original',NULL,1,'分析讲解当选ai浪潮普通人如何抓住机遇','别再刷视频了，AI这波红利，普通人再不上车就晚了！很多人觉得AI是程序员的事，错了。现在最赚钱的，是会用AI的普通人。第一步，别学原理，直接上手用。写文案、做图、剪视频，让AI帮你干，效率翻十倍。第二步，把你会的技能加上AI。比如你是做PPT的，用AI十分钟出十套方案，客户排队找你。第三步，边用边分享。你踩过的坑，就是别人的教程，发出来就是流量。记住，淘汰你的不是AI，是会用AI的人。关注我，下期教你三个免费AI神器。','{\"meta\": {\"aspect\": \"9:16\", \"target_duration_sec\": 52, \"llm_model\": \"glm-5.3-flash\", \"video_model_id\": 46}, \"audio\": {\"voice\": {\"type\": \"clone\"}, \"bgm\": {\"type\": \"none\"}}, \"subtitles\": {\"style\": \"sentence\", \"font_size\": 56, \"highlight\": \"#FFE24A\"}, \"shots\": [{\"index\": 1, \"role\": \"hook\", \"text\": \"别再刷视频了，AI这波红利，普通人再不上车就晚了！\", \"mode\": \"img2vid\", \"image_prompt\": \"近景特写镜头，聚焦于一位年轻普通男性，他正蜷缩坐在深灰色布艺沙发上，姿态放松但略显疲惫。他低头凝视手中的智能手机，屏幕发出的强烈冷调蓝光（Cyan-Blue light）是他面部的主要光源，精准照亮他的鼻梁、脸颊和下颌线，并在瞳孔中形成清晰的反射点，周围阴影深沉。背景是昏暗杂乱的客厅，隐约可见失焦的落地灯、散落的抱枕和书架轮廓，营造出景深效果。整体色调采用低饱和度的冷色系，强调蓝色与黑色的对比，辅以少量暖色环境光作为平衡。光影硬朗，营造出强烈的电影感（Cinematic lighting）和悬疑、紧迫的氛围。细节真实，皮肤纹理清晰可见，衣物材质自然，仿佛一部惊悚片中的关键静止帧，静止中充满张力。\", \"video_prompt\": \"夜晚昏暗的卧室，人物猛然抬头直视镜头，眼神中充满惊恐与紧迫感；手中举着的手机屏幕发出剧烈闪烁的冷白光，光影在面部快速交替，制造出动态的明暗变化；镜头以极快的速度向前推近，从近景瞬间压缩至面部大特写，焦点牢牢锁定在人物瞪大的双眼和紧锁的眉宇之间；背景完全虚化为深黑色，仅保留屏幕光在头发和皮肤边缘形成的微弱轮廓光；整体画面色调阴冷压抑，高对比度光影，电影级惊悚氛围，2.35:1宽银幕画幅，4K超高清质感。\", \"reference_images\": [], \"transition\": \"whip\"}, {\"index\": 2, \"role\": \"body\", \"text\": \"很多人觉得AI是程序员的事，错了。\", \"mode\": \"img2vid\", \"image_prompt\": \"中景镜头，写实风格，办公室场景。一名戴眼镜的程序员坐在工位前，专注地盯着电脑屏幕，手指正在敲击键盘，面部有轻微反光。画面前景被一个巨大的、半透明的红色叉号（X）视觉元素覆盖，仿佛象征错误或拒绝，红色叉号边缘锐利，色彩饱和度高，与背景形成强烈对比。光源主要来自头顶的冷白顶光，在人物头部和桌面形成高光，并投下清晰的硬阴影，营造出冷峻、紧张的氛围。背景为模糊的办公室环境，可见其他工位和显示器蓝光。整体画质 sharp focus, high contrast, realistic textures, cinematic lighting.\", \"video_prompt\": \"高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 3, \"role\": \"body\", \"text\": \"现在最赚钱的，是会用AI的普通人。\", \"mode\": \"img2vid\", \"image_prompt\": \"一位穿着休闲普通装扮的年轻男子，双手抱胸，神情自信从容，站在高楼天台边缘。背景是璀璨的城市夜景，霓虹灯火模糊成景深光斑。中景仰拍视角，凸显人物的高大与气势。侧面金色暖光打亮人物轮廓，形成强烈的明暗对比和电影感光影。励志电影风格，画面构图宏大，氛围充满希望与力量，高清晰度，细节丰富，色彩对比强烈但和谐。\", \"video_prompt\": \"低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 4, \"role\": \"body\", \"text\": \"第一步，别学原理，直接上手用。\", \"mode\": \"img2vid\", \"image_prompt\": \"极致特写镜头，聚焦于在笔记本电脑键盘上快速敲击的一双手。背景是简洁的极简风格桌面，笔记本屏幕发出柔和且均匀的冷调蓝光，作为主要光源照亮指尖与键盘缝隙，形成高对比度的科技感光影。采用浅景深效果，前景手指清晰锐利，背景与远处桌面呈现柔和的虚化光斑。色彩以冷青色、深灰与纯白为主，营造高效、专注且充满未来感的数字工作氛围。写实摄影风格，8k分辨率，锐度极高，捕捉皮肤纹理与塑料键盘的材质细节，电影级构图。\", \"video_prompt\": \"特写镜头聚焦于手指在机械键盘上快速敲击的特写画面，指尖精准触动键帽，键盘轴体发出轻微反馈；屏幕散发出冷调蓝紫色光晕，随着敲击节奏产生高频脉动与呼吸感，光影柔和地映在皮肤与金属边框上；镜头以缓慢平稳的速度围绕手部进行360度环绕移动，捕捉不同角度下的手指动态与屏幕反光变化；背景为深暗的科技感工作室，景深极浅，背景虚化柔和，突出前景的手部动作与键盘细节；画面具有电影级质感，色彩对比度高，细节锐利，营造出专注且充满科技氛围的视觉效果。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 5, \"role\": \"body\", \"text\": \"写文案、做图、剪视频，让AI帮你干，效率翻十倍。\", \"mode\": \"img2vid\", \"image_prompt\": \"分屏式概念画面，中景视角，深色科技空间背景。画面由三个悬浮的半透明屏幕组成：左侧为极简文案编辑界面，显示代码与文本；中间为大型设计软件画布，展示色彩鲜艳的图形编辑过程；右侧为复杂的视频剪辑时间轴，轨道清晰可见。整体笼罩在霓虹蓝紫混合的光效中，屏幕边缘发出柔和的辉光，反射在周围的镜面或金属表面上。未来感、赛博朋克风格，高对比度，锐利焦点，玻璃材质质感，深度虚化背景，电影级布光，8k分辨率，超写实渲染，科技感氛围强烈，视觉层次分明。\", \"video_prompt\": \"0-2秒：深邃的暗色背景中，三个半透明的悬浮全息屏幕依次从中心向外亮起，屏幕边缘闪烁着幽蓝色的科技光晕，屏幕内显示着动态的数据流与抽象的几何图形，伴随轻微的机械旋转展示内部内容；2-5秒：成千上万颗微小的金色光粒子在屏幕之间穿梭流动，形成灵动的粒子轨迹，光影交织出梦幻般的立体空间感，粒子碰撞时产生柔和的光爆效果；6-10秒：镜头保持水平缓慢横移，模拟视线扫过屏幕群的流畅视角，景深逐渐拉远展现屏幕阵列的全貌，整体氛围充满未来感与静谧的高级科技感，画面清晰锐利，色彩以深蓝与亮金为主。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 6, \"role\": \"body\", \"text\": \"第二步，把你会的技能加上AI。比如你是做PPT的，用AI十分钟出十套方案，客户排队找你。\", \"mode\": \"img2vid\", \"image_prompt\": \"一位穿着剪裁合身深色西装的职场人士站在大型投影屏幕前，神情自信专业，正展示幻灯片。屏幕上色彩丰富、排版清晰的演示页面呈扇形展开，悬浮于空中，营造出现代科技感。背景为现代简约的会议室，隐约可见长桌与座椅。采用中景构图，明亮均匀的商务打光，面部柔和无阴影。整体风格为专业写实，高分辨率，细节锐利，色彩平衡，展现商务演示的专业氛围。\", \"video_prompt\": \"动态视觉序列，多张半透明幻灯片页面如同扑克牌般以高速度快速连续展开，层层叠叠铺满整个屏幕，边缘锐利，光影交错；画面底部，深色的客户剪影依次淡入出现，整齐排成横向队列，保持静止姿态，身份清晰；镜头从近景特写缓缓拉远至大远景，展现整体构图，背景为深蓝色科技风格，柔和顶光，高清晰度，电影感色调，流畅连贯的动画过渡。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 7, \"role\": \"body\", \"text\": \"第三步，边用边分享。你踩过的坑，就是别人的教程，发出来就是流量。\", \"mode\": \"img2vid\", \"image_prompt\": \"中景镜头，一位年轻人坐在温馨的居家工作室里，正对着架在支架上的手机和环形补光灯录制分享视频。桌上摆放着一台打开的笔记本电脑，周围点缀着绿植和书籍，营造出舒适、生活化的氛围。暖色调的环形光柔和地打亮人物面部，眼神专注且充满亲和力。背景略微虚化，突显主体，整体画面呈现生活化写实风格，光影细腻自然。\", \"video_prompt\": \"中近景视角，一位时尚年轻博主正对着镜头进行充满活力的口型讲述，面部表情生动夸张，眼神直视前方，配合言语节奏做出富有感染力的手部手势，如比心或强调性挥动。背景为简约温馨的室内直播间，暖色调LED灯带营造柔和氛围，光影立体。随着讲述高潮，手机屏幕界面中红色的点赞爱心图标密集生成并不断向上漂浮，粒子特效梦幻且明亮，与人物动态形成虚实互动。镜头采用轻微手持晃动模拟真实直播质感，焦点始终锁定在人物面部与上半身，背景微虚化，色彩饱和度高，整体呈现高清、活泼、社交媒体的视觉风格。\", \"reference_images\": [], \"transition\": \"fade\"}, {\"index\": 8, \"role\": \"cta\", \"text\": \"记住，淘汰你的不是AI，是会用AI的人。关注我，下期教你三个免费AI神器。\", \"mode\": \"img2vid\", \"image_prompt\": \"一位自信的女性博主站在画面中央，直视镜头，眼神坚定且充满感染力。背景为深邃的深色渐变，隐约可见发光的微弱电路纹理光效，营造出神秘且高科技的氛围。中近景构图，焦点清晰集中在人物面部。主光从正前方柔和打亮面部，消除多余阴影，突显皮肤质感；侧后方设置冷色调轮廓光，清晰勾勒出身体与发丝的立体边缘。整体色调偏向冷色系的蓝紫或青绿，具有高级感的科技美学风格，光影层次丰富，细节锐利，呈现出商业级的人像摄影质感。\", \"video_prompt\": \"未来科技感场景，人物站在画面中央，指尖指向镜头下方，眼神坚定，表情自信；背景由多层全息电路板与发光线路构成，光效呈冷蓝色，数据流缓缓流动，节点周期性闪烁，营造稳定、秩序、数字化的氛围；机位保持正面平视，从全景缓慢推近至半身中景，人物头部与肩部始终居中，焦点锁定面部与手势，背景虚化但光效保持清晰；冷色调霓虹光影，轻微体积光与丁达尔效应，电影级科幻质感，节奏平稳，一镜到底。\", \"reference_images\": [], \"transition\": \"cut\"}]}','done','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/final/a8574d22cbdf4bf6a74ae375c9855e15.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/cover/5a051e16e5b044d6b574ca1e886e5057.jpg','{\"title\": \"AI红利普通人怎么上车？3步实操法\", \"caption\": \"别再只看不练了！普通人上车的三步法：1.直接上手用；2.技能+AI放大价值；3.分享经验赚流量。淘汰你的不是AI，是会用AI的人。关注我，下期送三个免费神器，带你一起抓住这波红利！\", \"tags\": [\"AI干货\", \"职场逆袭\", \"效率工具\", \"普通人机会\", \"搞钱思维\", \"技能变现\", \"AI教程\", \"个人成长\"], \"ai_declaration\": \"本视频由 AI 辅助生成\"}',NULL,'1','2026-10-03 00:47:24','2026-10-03 08:47:07',0),(2,'公司先裁的，是拒绝用AI的人','original',NULL,1,'ai时代的开启普通人如何在这场浪潮中打赢','AI不会淘汰你，但会用AI的同事会。你以为AI来了大家都失业？错。公司先裁的，是拒绝用AI的人。我朋友做设计，以前改一张图要3天。现在用AI，一天交20版。3天对20版，差距就这么拉开了。老板没裁他，反而给他涨了薪。你不用成为专家。先把AI当助手用起来就行。想看我用的工具清单？评论区扣1。','{\"meta\": {\"aspect\": \"9:16\", \"target_duration_sec\": 36, \"llm_model\": \"glm-5.3-flash\", \"script_strategy\": \"反常识\", \"image_model_id\": 38, \"video_model_id\": 46}, \"audio\": {\"voice\": {\"type\": \"clone\"}, \"bgm\": {\"type\": \"none\"}}, \"subtitles\": {\"style\": \"sentence\", \"font_size\": 56, \"highlight\": \"#FFE24A\"}, \"shots\": [{\"index\": 1, \"role\": \"hook\", \"text\": \"AI不会淘汰你，但会用AI的同事会。\", \"mode\": \"img2vid\", \"image_prompt\": \"现代开放式办公室内部，中景构图，聚焦于一位年轻白领女性，身穿剪裁合体的白衬衫，领带微松，神情焦虑紧张，眉头紧锁，手指无意识地敲击桌面，汗珠在微光下若隐若现。其右侧，一位资深同事正自信地单手操作机械键盘，面带微笑，姿态从容。强烈的电影感侧逆光从画面左侧后方射入，形成清晰的轮廓光，勾勒出人物肩颈线条。办公室整体处于冷色调氛围中，主光源为电脑屏幕散发的幽蓝冷光，照亮人物面部并投射在桌面与键盘上，与周围昏暗的暖色环境光形成高对比度。背景虚化，可见模糊的办公格挡、电脑显示器微光及窗外城市夜景，景深较浅。极致写实风格，皮肤质感逼真，眼神专注，空气中漂浮着细微尘埃颗粒，画面具有强烈的叙事张力和职场压迫感。\", \"video_prompt\": \"写实主义风格，4K 高清电影质感。镜头缓缓推近一名白领的面部特写，他面色凝重，眼神中流露不安，视线紧张地瞥向左侧；旁边同事手指在键盘上快速敲击，机械键盘的轻微振动与屏幕发出的冷白光微微闪烁，映照在两人面部；背景虚化，隐约可见模糊的人影在办公区走动。采用浅景深，侧光照明，冷暖色调对比，营造出压抑且充满悬疑感的职场氛围，画面稳定，细节丰富，光影自然过渡。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 2, \"role\": \"body\", \"text\": \"你以为AI来了大家都失业？错。\", \"mode\": \"img2vid\", \"image_prompt\": \"特写镜头：一位表情笃定的演讲者正对镜头，面部占据画面主体，神情严肃且自信，手指果断竖起做出否定手势。背景是深度虚化的深色演播室，隐约可见暗调环境轮廓，突出主体。一束强烈的顶光精准打在人物面部与手部，形成戏剧化的明暗对比，阴影深邃，轮廓清晰。写实摄影风格，电影级质感，细节丰富，纹理真实，色彩还原自然，氛围专注而充满张力。\", \"video_prompt\": \"中近景镜头捕捉演讲者面部，他神情严肃，坚定有力地摇头并挥动手臂，动作果断且富有张力；背景光影缓慢流动，色彩由暗转亮再回落，营造紧张转折氛围；镜头起始有轻微手持晃动感，随后迅速稳定，焦点始终锁定在演讲者面部；环境为室内演讲舞台，深蓝色调，侧逆光突出轮廓，画面电影感强，高清晰度，情绪凝重。\", \"reference_images\": [], \"transition\": \"whip\"}, {\"index\": 3, \"role\": \"body\", \"text\": \"公司先裁的，是拒绝用AI的人。\", \"mode\": \"img2vid\", \"image_prompt\": \"一张写实风格的中景照片，展示空旷办公室的一角。画面中心是一张收拾得干干净净的办公桌，桌面上空无一物，仅有少许痕迹。一把办公椅上放着一个棕色的纸板箱，暗示着离职或搬离。背景中，几位身穿商务正装的员工坐在各自的工位上，低着头，专注地工作着，他们的身影略显模糊，营造出一种疏离感。整个场景笼罩在冷灰色的色调中，窗外是阴天的天空，柔和而阴冷的自然光线透过窗户洒入室内，在办公桌和地板上投下淡淡的阴影。氛围显得压抑、寂静且略带忧郁，光影对比自然，材质细节真实，展现了现代职场中一种孤独与转变的瞬间。\", \"video_prompt\": \"镜头从空桌缓慢横移到远处忙碌的员工，纸箱上灰尘微粒在光束中飘浮，画面安静肃穆。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 4, \"role\": \"proof\", \"text\": \"我朋友做设计，以前改一张图要3天。\", \"mode\": \"img2vid\", \"image_prompt\": \"深夜昏暗的室内场景，一位设计师身穿休闲便服，疲惫地趴在数位板前，双手揉着眼睛，神态显得十分疲乏。桌面上杂乱无章地堆满了卷曲的草稿纸和几个空咖啡杯，显示出长时间的工作痕迹。前景是复杂的3D设计稿屏幕，散发着清冷的蓝白色光芒，与背景中暖黄色的台灯光形成鲜明的冷暖对比，光影交错营造出一种孤独而专注的氛围。中近景构图，聚焦于人物疲惫的神态与桌面细节，写实风格，高画质，电影级布光，8k分辨率，纹理清晰，浅景深效果突出主体。\", \"video_prompt\": \"深夜办公室，冷色调蓝光从电脑屏幕投射在设计师疲惫的脸上，特写镜头捕捉他/她痛苦地揉捏眼珠，随后缓慢伸懒腰，手臂舒展，动作充满疲惫感；屏幕上UI设计稿反复闪烁修改，光标快速跳动，反映焦虑状态；镜头缓慢向后拉远并略微升高，逐渐揭示桌面杂乱无章的现实，堆满揉皱的草图纸张、废弃的马克笔、咖啡杯和凌乱线缆，背景昏暗，强调孤立与高压氛围，写实主义风格，电影级景深。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 5, \"role\": \"proof\", \"text\": \"现在用AI，一天交20版。\", \"mode\": \"img2vid\", \"image_prompt\": \"明亮宽敞的现代办公环境，一位年轻设计师身着休闲衬衫，面带自信而精神饱满的微笑，正专注操作面前的电脑。中近景构图，视觉重心集中在人物面部表情及屏幕内容。屏幕上整齐排列着多张色彩鲜艳的设计稿缩略图，界面清晰，细节丰富。自然光从落地窗柔和洒入，营造出通透、明亮且专业的氛围。色彩明快清新，以白色、浅灰及屏幕中的多彩色调为主，呈现高保真写实风格，光影细腻，材质真实。\", \"video_prompt\": \"中近景镜头捕捉一位年轻设计师在明亮的工作台前，面前多台显示器上UI设计稿与3D模型快速刷新、滚动生成，屏幕光影映照在设计师专注的面庞上；设计师嘴角微微上扬，自信地点头微笑，眼神中透着满足与肯定；随着情绪推进，背景环境光线逐渐由柔和过渡至明亮通透，自然光与屏幕光交织，营造出高效、现代的科技感氛围；镜头缓慢轻微上扬，模拟仰视视角，强化空间感与积极向上的视觉张力，整体色调清新明亮，细节锐利，电影级画质。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 6, \"role\": \"proof\", \"text\": \"3天对20版，差距就这么拉开了。\", \"mode\": \"img2vid\", \"image_prompt\": \"分屏对比构图，画面正中一条锐利垂直明暗分界线。左侧展现昏暗压抑的工作空间，冷色调主导，光线微弱且杂乱，主角面容疲惫，背景模糊杂乱，营造低效与焦虑氛围；右侧呈现明亮高效的工作场景，暖金色调，光线充足均匀，主角神情专注从容，背景整洁有序，突显高效与成就感。中景景别，对称式布局强化对比，写实摄影风格，强调光影反差与色彩冷暖碰撞，细节丰富，高清质感。\", \"video_prompt\": \"镜头从分界线缓慢向两侧拉开，左侧画面逐渐变暗，右侧画面逐渐变亮，对比感不断强化。\", \"reference_images\": [], \"transition\": \"fade\"}, {\"index\": 7, \"role\": \"body\", \"text\": \"老板没裁他，反而给他涨了薪。\", \"mode\": \"img2vid\", \"image_prompt\": \"中景镜头，写实风格，画面中心是一位面带自信温和笑容的老板，身着剪裁得体的深灰色西装，正与一位穿着简约时尚衬衫的设计师握手，设计师身穿米白色亚麻外套，动作自然优雅。办公桌上整齐摆放着摊开的建筑图纸与平板电脑，纸张边缘泛着柔和的高光。背景是巨大的落地窗，透过玻璃可见模糊的城市天际线，天空呈现淡蓝色调，明亮的暖阳光从左侧射入，在人物面部和文件上投下柔和的阴影，营造出积极、专业且充满信任的商务氛围，画面清晰，构图平衡。\", \"video_prompt\": \"现代简约明亮的办公室内，身穿深色西装的老板与穿着休闲职业装的设计师面对面站立，两人动作自然流畅地紧握双手，随后老板有力地竖起大拇指，设计师面部肌肉放松，露出惊喜且真诚的灿烂笑容。背景是落地窗，午后黄金时段的阳光透过玻璃洒入室内，形成清晰的丁达尔光束，照亮空气中的微尘。镜头以人物为圆心进行缓慢的水平环绕拍摄，始终保持中近景构图，浅景深效果虚化背景办公设施，焦点牢牢锁定在两人互动的面部表情与手部细节上。整体色调温暖明快，光影柔和，充满职业成就感与温馨积极的商务氛围，电影级写实画质。\", \"reference_images\": [], \"transition\": \"cut\"}, {\"index\": 8, \"role\": \"cta\", \"text\": \"你不用成为专家。先把AI当助手用起来就行。想看我用的工具清单？评论区扣1。\", \"mode\": \"img2vid\", \"image_prompt\": \"演讲者正面面对镜头，微笑着指向镜头下方，背景是简洁明亮的渐变色墙面，柔和均匀的布光，中近景，亲和力强，写实风格。\", \"video_prompt\": \"演讲者微笑点头，手指向镜头下方示意，身体微微前倾，镜头缓慢推近，营造互动感。\", \"reference_images\": [], \"transition\": \"fade\"}]}','failed',NULL,NULL,NULL,'镜 1: 镜 1 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）；镜 2: 镜 2 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）；镜 4: 镜 4 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）（共 7 镜失败，可单镜重试）','1','2026-10-07 09:45:38','2026-10-07 10:04:15',0);
/*!40000 ALTER TABLE `ai_creation_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_doc`
--

DROP TABLE IF EXISTS `ai_doc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_doc` (
  `doc_id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识库ID',
  `doc_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型：INPUT-手动输入，UPLOAD-文件上传',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '原始内容（手动输入时存储）',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件URL（上传类型）',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始文件名',
  `slice_count` int DEFAULT '0' COMMENT '切片数量',
  `char_count` int DEFAULT '0' COMMENT '字符数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`doc_id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_doc`
--

LOCK TABLES `ai_doc` WRITE;
/*!40000 ALTER TABLE `ai_doc` DISABLE KEYS */;
INSERT INTO `ai_doc` VALUES (1,1,'项目简介与技术栈','INPUT','TwelveT Fast（十二T极速开发后台管理系统）是一个基于 Spring Boot 3.x 的单体后台管理系统，当前版本为 3.5.4，采用 Java 17 语言开发。前端采用 Umi Max 4.x（React 18 + TypeScript），UI 组件库为 Ant Design 5.x 与 ProComponents 2.x。后端技术栈包括 MyBatis 与 PageHelper 作为 ORM，Sa-Token 负责安全认证，Redisson 负责 Redis 缓存，Knife4j 与 SpringDoc 生成 API 文档。数据库默认使用 MySQL，同时支持 Oracle、PostgreSQL、SQLServer、达梦、瀚高、DB2 等多种数据库。项目采用 Maven 多模块结构，主要模块有 twelvet-admin（启动入口）、twelvet-system（系统业务）、twelvet-ai（AI 知识库与聊天）、twelvet-quartz（定时任务）、twelvet-gen（代码生成器）。',NULL,NULL,34,463,'completed','admin','2026-08-10 13:56:31',NULL,'2026-08-10 13:59:08',1),(2,1,'启动与部署','INPUT','后端启动方式：在 IDEA 中打开项目根目录 pom.xml，等待 Maven 依赖下载完成后，启动 twelvet-admin 模块下的 FastApplication 主类，应用默认端口为 8080。启动前需要保证 MySQL 和 Redis 已经启动，并在 application.yml 中配置正确的数据库连接。前端启动方式：进入 twelvet-ui-master 目录，执行 yarn install 安装依赖，然后执行 yarn start 启动开发服务器，默认端口为 8000。开发环境下前端通过 Umi 代理把 /api 前缀的请求转发到后端 8080 端口。也可以使用 bin 目录下的 run-web.bat 一键启动前端。AI 能力依赖单独的 Python 服务（FastAPI），默认运行在 8001 端口，Java 后端 8080 会代理转发 AI 相关请求到该服务。',NULL,NULL,1,398,'completed','admin','2026-08-10 13:56:34',NULL,'2026-08-10 13:59:08',1),(3,1,'系统功能模块','INPUT','系统管理模块包含用户管理、角色管理、菜单权限、部门管理、岗位管理、字典管理、国际化管理。监控模块包含定时任务（Quartz）管理与 Redis 缓存监控。日志模块包含登录日志、操作日志与任务日志。AI 模块包含 AI 聊天、文档管理、知识库管理、切片管理、MCP 服务与模型管理。知识库管理用于创建主题仓库，文档管理用于向知识库导入 PDF、Word、txt、md 等格式资料，上传后系统会自动将文档切成多个切片并构建向量索引。AI 聊天时先检索知识库中最相关的切片，再结合大模型生成带依据的回答。代码生成器（twelvet-gen）支持多数据库，可根据数据库表结构一键生成前后端代码。',NULL,NULL,1,293,'completed','admin','2026-08-10 13:56:36',NULL,'2026-08-10 13:59:08',1),(4,1,'RAG 知识库机制','INPUT','RAG 即检索增强生成，是知识库问答的核心机制。文本切片时按照 slice_size 参数控制每个切片的字符数，默认 3000 字符，切片之间保留少量重叠保证上下文连贯。切片完成后通过 Embedding 模型（默认 BAAI/bge-m3）将每个切片转换为向量，存入按知识库隔离的 FAISS 索引文件。用户提问时，系统先将问题转为向量，在索引中检索最相似的 top_k 个切片（默认 3 个），score 参数是相似度阈值，默认 0.5，低于阈值的切片会被过滤。检索到的切片作为参考资料拼接进 prompt，再由大模型生成回答。如果未配置 Embedding 服务，系统会自动退化为数据库 LIKE 全文匹配，功能仍可用但检索效果较弱。删除知识库或文档时会同步清理对应的向量索引。',NULL,NULL,1,342,'completed','admin','2026-08-10 13:56:38',NULL,'2026-08-10 13:59:08',1),(5,2,'AI 聊天使用说明','INPUT','AI 聊天页面左侧是知识库选择器，必须选择一个知识库才能发送消息，知识库决定了回答时检索的资料范围。聊天支持流式输出，回答内容会边生成边显示。每条回答上方显示引用的知识来源，点击可查看对应的切片原文。回答支持一键复制和语音播放。每个知识库的聊天历史相互独立，multi_round 参数控制多轮对话的最大轮数。新建知识库后需要先在文档管理页面向知识库导入资料，否则聊天时检索不到任何切片，模型只能凭自身知识回答。',NULL,NULL,1,206,'completed','admin','2026-08-10 13:56:41',NULL,'2026-08-10 13:59:10',1),(6,3,'项目简介与技术栈','INPUT','TwelveT Fast（十二T极速开发后台管理系统）是一个基于 Spring Boot 3.x 的单体后台管理系统，当前版本为 3.5.4，采用 Java 17 语言开发。前端采用 Umi Max 4.x（React 18 + TypeScript），UI 组件库为 Ant Design 5.x 与 ProComponents 2.x。后端技术栈包括 MyBatis 与 PageHelper 作为 ORM，Sa-Token 负责安全认证，Redisson 负责 Redis 缓存，Knife4j 与 SpringDoc 生成 API 文档。数据库默认使用 MySQL，同时支持 Oracle、PostgreSQL、SQLServer、达梦、瀚高、DB2 等多种数据库。项目采用 Maven 多模块结构，主要模块有 twelvet-admin（启动入口）、twelvet-system（系统业务）、twelvet-ai（AI 知识库与聊天）、twelvet-quartz（定时任务）、twelvet-gen（代码生成器）。',NULL,NULL,34,463,'completed','admin','2026-08-10 13:59:16',NULL,'2026-08-10 14:14:07',1),(7,3,'启动与部署','INPUT','后端启动方式：在 IDEA 中打开项目根目录 pom.xml，等待 Maven 依赖下载完成后，启动 twelvet-admin 模块下的 FastApplication 主类，应用默认端口为 8080。启动前需要保证 MySQL 和 Redis 已经启动，并在 application.yml 中配置正确的数据库连接。前端启动方式：进入 twelvet-ui-master 目录，执行 yarn install 安装依赖，然后执行 yarn start 启动开发服务器，默认端口为 8000。开发环境下前端通过 Umi 代理把 /api 前缀的请求转发到后端 8080 端口。也可以使用 bin 目录下的 run-web.bat 一键启动前端。AI 能力依赖单独的 Python 服务（FastAPI），默认运行在 8001 端口，Java 后端 8080 会代理转发 AI 相关请求到该服务。',NULL,NULL,1,398,'completed','admin','2026-08-10 13:59:19',NULL,'2026-08-10 14:14:07',1),(8,3,'系统功能模块','INPUT','系统管理模块包含用户管理、角色管理、菜单权限、部门管理、岗位管理、字典管理、国际化管理。监控模块包含定时任务（Quartz）管理与 Redis 缓存监控。日志模块包含登录日志、操作日志与任务日志。AI 模块包含 AI 聊天、文档管理、知识库管理、切片管理、MCP 服务与模型管理。知识库管理用于创建主题仓库，文档管理用于向知识库导入 PDF、Word、txt、md 等格式资料，上传后系统会自动将文档切成多个切片并构建向量索引。AI 聊天时先检索知识库中最相关的切片，再结合大模型生成带依据的回答。代码生成器（twelvet-gen）支持多数据库，可根据数据库表结构一键生成前后端代码。',NULL,NULL,1,293,'completed','admin','2026-08-10 13:59:21',NULL,'2026-08-10 14:14:07',1),(9,3,'RAG 知识库机制','INPUT','RAG 即检索增强生成，是知识库问答的核心机制。文本切片时按照 slice_size 参数控制每个切片的字符数，默认 3000 字符，切片之间保留少量重叠保证上下文连贯。切片完成后通过 Embedding 模型（默认 BAAI/bge-m3）将每个切片转换为向量，存入按知识库隔离的 FAISS 索引文件。用户提问时，系统先将问题转为向量，在索引中检索最相似的 top_k 个切片（默认 3 个），score 参数是相似度阈值，默认 0.5，低于阈值的切片会被过滤。检索到的切片作为参考资料拼接进 prompt，再由大模型生成回答。如果未配置 Embedding 服务，系统会自动退化为数据库 LIKE 全文匹配，功能仍可用但检索效果较弱。删除知识库或文档时会同步清理对应的向量索引。',NULL,NULL,1,342,'completed','admin','2026-08-10 13:59:23',NULL,'2026-08-10 14:14:07',1),(10,4,'AI 聊天使用说明','INPUT','AI 聊天页面左侧是知识库选择器，必须选择一个知识库才能发送消息，知识库决定了回答时检索的资料范围。聊天支持流式输出，回答内容会边生成边显示。每条回答上方显示引用的知识来源，点击可查看对应的切片原文。回答支持一键复制和语音播放。每个知识库的聊天历史相互独立，multi_round 参数控制多轮对话的最大轮数。新建知识库后需要先在文档管理页面向知识库导入资料，否则聊天时检索不到任何切片，模型只能凭自身知识回答。',NULL,NULL,1,206,'completed','admin','2026-08-10 13:59:26',NULL,'2026-08-10 14:14:09',1),(11,5,'项目简介与技术栈','INPUT','TwelveT Fast（十二T极速开发后台管理系统）是一个基于 Spring Boot 3.x 的单体后台管理系统，当前版本为 3.5.4，采用 Java 17 语言开发。前端采用 Umi Max 4.x（React 18 + TypeScript），UI 组件库为 Ant Design 5.x 与 ProComponents 2.x。后端技术栈包括 MyBatis 与 PageHelper 作为 ORM，Sa-Token 负责安全认证，Redisson 负责 Redis 缓存，Knife4j 与 SpringDoc 生成 API 文档。数据库默认使用 MySQL，同时支持 Oracle、PostgreSQL、SQLServer、达梦、瀚高、DB2 等多种数据库。项目采用 Maven 多模块结构，主要模块有 twelvet-admin（启动入口）、twelvet-system（系统业务）、twelvet-ai（AI 知识库与聊天）、twelvet-quartz（定时任务）、twelvet-gen（代码生成器）。',NULL,NULL,2,463,'completed','admin','2026-08-10 14:14:16',NULL,'2026-08-10 14:14:16',0),(12,5,'启动与部署','INPUT','后端启动方式：在 IDEA 中打开项目根目录 pom.xml，等待 Maven 依赖下载完成后，启动 twelvet-admin 模块下的 FastApplication 主类，应用默认端口为 8080。启动前需要保证 MySQL 和 Redis 已经启动，并在 application.yml 中配置正确的数据库连接。前端启动方式：进入 twelvet-ui-master 目录，执行 yarn install 安装依赖，然后执行 yarn start 启动开发服务器，默认端口为 8000。开发环境下前端通过 Umi 代理把 /api 前缀的请求转发到后端 8080 端口。也可以使用 bin 目录下的 run-web.bat 一键启动前端。AI 能力依赖单独的 Python 服务（FastAPI），默认运行在 8001 端口，Java 后端 8080 会代理转发 AI 相关请求到该服务。',NULL,NULL,1,398,'completed','admin','2026-08-10 14:14:20',NULL,'2026-08-10 14:14:20',0),(13,5,'系统功能模块','INPUT','系统管理模块包含用户管理、角色管理、菜单权限、部门管理、岗位管理、字典管理、国际化管理。监控模块包含定时任务（Quartz）管理与 Redis 缓存监控。日志模块包含登录日志、操作日志与任务日志。AI 模块包含 AI 聊天、文档管理、知识库管理、切片管理、MCP 服务与模型管理。知识库管理用于创建主题仓库，文档管理用于向知识库导入 PDF、Word、txt、md 等格式资料，上传后系统会自动将文档切成多个切片并构建向量索引。AI 聊天时先检索知识库中最相关的切片，再结合大模型生成带依据的回答。代码生成器（twelvet-gen）支持多数据库，可根据数据库表结构一键生成前后端代码。',NULL,NULL,1,293,'completed','admin','2026-08-10 14:14:23',NULL,'2026-08-10 14:14:23',0),(14,5,'RAG 知识库机制','INPUT','RAG 即检索增强生成，是知识库问答的核心机制。文本切片时按照 slice_size 参数控制每个切片的字符数，默认 3000 字符，切片之间保留少量重叠保证上下文连贯。切片完成后通过 Embedding 模型（默认 BAAI/bge-m3）将每个切片转换为向量，存入按知识库隔离的 FAISS 索引文件。用户提问时，系统先将问题转为向量，在索引中检索最相似的 top_k 个切片（默认 3 个），score 参数是相似度阈值，默认 0.5，低于阈值的切片会被过滤。检索到的切片作为参考资料拼接进 prompt，再由大模型生成回答。如果未配置 Embedding 服务，系统会自动退化为数据库 LIKE 全文匹配，功能仍可用但检索效果较弱。删除知识库或文档时会同步清理对应的向量索引。',NULL,NULL,1,342,'completed','admin','2026-08-10 14:14:26',NULL,'2026-08-10 14:14:26',0),(15,6,'AI 聊天使用说明','INPUT','AI 聊天页面左侧是知识库选择器，必须选择一个知识库才能发送消息，知识库决定了回答时检索的资料范围。聊天支持流式输出，回答内容会边生成边显示。每条回答上方显示引用的知识来源，点击可查看对应的切片原文。回答支持一键复制和语音播放。每个知识库的聊天历史相互独立，multi_round 参数控制多轮对话的最大轮数。新建知识库后需要先在文档管理页面向知识库导入资料，否则聊天时检索不到任何切片，模型只能凭自身知识回答。',NULL,NULL,1,206,'completed','admin','2026-08-10 14:14:30',NULL,'2026-08-10 14:14:30',0);
/*!40000 ALTER TABLE `ai_doc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_doc_slice`
--

DROP TABLE IF EXISTS `ai_doc_slice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_doc_slice` (
  `slice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '切片ID',
  `doc_id` bigint NOT NULL COMMENT '文档ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识库ID',
  `slice_index` int NOT NULL COMMENT '切片序号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '切片内容',
  `char_count` int DEFAULT NULL COMMENT '字符数',
  `vector_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '向量化状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`slice_id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档切片表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_doc_slice`
--

LOCK TABLES `ai_doc_slice` WRITE;
/*!40000 ALTER TABLE `ai_doc_slice` DISABLE KEYS */;
INSERT INTO `ai_doc_slice` VALUES (77,11,5,1,'TwelveT Fast（十二T极速开发后台管理系统）是一个基于 Spring Boot 3.x 的单体后台管理系统，当前版本为 3.5.4，采用 Java 17 语言开发。前端采用 Umi Max 4.x（React 18 + TypeScript），UI 组件库为 Ant Design 5.x 与 ProComponents 2.x。后端技术栈包括 MyBatis 与 PageHelper 作为 ORM，Sa-Token 负责安全认证，Redisson 负责 Redis 缓存，Knife4j 与 SpringDoc 生成 API 文档。数据库默认使用 MySQL，同时支持 Oracle、PostgreSQL、SQLServer、达梦、瀚高、DB2 等多种数据库。项目采用 Maven 多模块结构，主要模块有 twelvet-admin（启动入口）、twelvet-system（系统业务）',400,'completed','2026-08-10 14:14:16'),(78,11,5,2,'admin（启动入口）、twelvet-system（系统业务）、twelvet-ai（AI 知识库与聊天）、twelvet-quartz（定时任务）、twelvet-gen（代码生成器）。',95,'completed','2026-08-10 14:14:16'),(79,12,5,1,'后端启动方式：在 IDEA 中打开项目根目录 pom.xml，等待 Maven 依赖下载完成后，启动 twelvet-admin 模块下的 FastApplication 主类，应用默认端口为 8080。启动前需要保证 MySQL 和 Redis 已经启动，并在 application.yml 中配置正确的数据库连接。前端启动方式：进入 twelvet-ui-master 目录，执行 yarn install 安装依赖，然后执行 yarn start 启动开发服务器，默认端口为 8000。开发环境下前端通过 Umi 代理把 /api 前缀的请求转发到后端 8080 端口。也可以使用 bin 目录下的 run-web.bat 一键启动前端。AI 能力依赖单独的 Python 服务（FastAPI），默认运行在 8001 端口，Java 后端 8080 会代理转发 AI 相关请求到该服务。',398,'completed','2026-08-10 14:14:20'),(80,13,5,1,'系统管理模块包含用户管理、角色管理、菜单权限、部门管理、岗位管理、字典管理、国际化管理。监控模块包含定时任务（Quartz）管理与 Redis 缓存监控。日志模块包含登录日志、操作日志与任务日志。AI 模块包含 AI 聊天、文档管理、知识库管理、切片管理、MCP 服务与模型管理。知识库管理用于创建主题仓库，文档管理用于向知识库导入 PDF、Word、txt、md 等格式资料，上传后系统会自动将文档切成多个切片并构建向量索引。AI 聊天时先检索知识库中最相关的切片，再结合大模型生成带依据的回答。代码生成器（twelvet-gen）支持多数据库，可根据数据库表结构一键生成前后端代码。',293,'completed','2026-08-10 14:14:23'),(81,14,5,1,'RAG 即检索增强生成，是知识库问答的核心机制。文本切片时按照 slice_size 参数控制每个切片的字符数，默认 3000 字符，切片之间保留少量重叠保证上下文连贯。切片完成后通过 Embedding 模型（默认 BAAI/bge-m3）将每个切片转换为向量，存入按知识库隔离的 FAISS 索引文件。用户提问时，系统先将问题转为向量，在索引中检索最相似的 top_k 个切片（默认 3 个），score 参数是相似度阈值，默认 0.5，低于阈值的切片会被过滤。检索到的切片作为参考资料拼接进 prompt，再由大模型生成回答。如果未配置 Embedding 服务，系统会自动退化为数据库 LIKE 全文匹配，功能仍可用但检索效果较弱。删除知识库或文档时会同步清理对应的向量索引。',342,'completed','2026-08-10 14:14:26'),(82,15,6,1,'AI 聊天页面左侧是知识库选择器，必须选择一个知识库才能发送消息，知识库决定了回答时检索的资料范围。聊天支持流式输出，回答内容会边生成边显示。每条回答上方显示引用的知识来源，点击可查看对应的切片原文。回答支持一键复制和语音播放。每个知识库的聊天历史相互独立，multi_round 参数控制多轮对话的最大轮数。新建知识库后需要先在文档管理页面向知识库导入资料，否则聊天时检索不到任何切片，模型只能凭自身知识回答。',206,'completed','2026-08-10 14:14:30');
/*!40000 ALTER TABLE `ai_doc_slice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_image_task`
--

DROP TABLE IF EXISTS `ai_image_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_image_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `model_id` bigint NOT NULL COMMENT '模型ID',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词',
  `negative_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '反向提示词',
  `size` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1024x1024' COMMENT '输出尺寸（如1024x1024）',
  `gen_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text2img' COMMENT '生成类型：text2img-文生图，img2img-图生图',
  `input_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入图片URL（img2img模式）',
  `response_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'url' COMMENT '输出格式：url，b64_json',
  `width` int DEFAULT '1024' COMMENT '图片宽度',
  `height` int DEFAULT '1024' COMMENT '图片高度',
  `seed` bigint DEFAULT NULL COMMENT '随机种子',
  `style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风格',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
  `result_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成结果URL',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`task_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI图像生成任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_image_task`
--

LOCK TABLES `ai_image_task` WRITE;
/*!40000 ALTER TABLE `ai_image_task` DISABLE KEYS */;
INSERT INTO `ai_image_task` VALUES (1,1,'美女图片',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/e3933023150e463fb89567c07de69d47.png',NULL,'admin','2026-06-27 10:24:00','2026-06-27 10:24:27',0),(2,1,'穿上粉色裙子',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-06-27/1782530904870.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/fcd6e7af9cd141b8a523598d6a4e924e/output.png',NULL,'admin','2026-06-27 11:28:30','2026-09-23 11:09:01',0),(3,1,'一部专业相机的写实肖像摄影，一位28岁的亚洲美女，近距离特写，自然的皮肤纹理与细小的毛孔可见，淡淡的雀斑，清透真实的皮肤质感，柔和朦胧的窗边自然光，眼神有光，写实的眼球反光，极其细致的睫毛与发丝，平淡的写实色调，富士胶片模拟风格，背景虚化，景深效果，8k高分辨率，逼真得令人惊叹，杰作。\n\nNegative Prompt（负向词，必加）： 卡通，油画，3D渲染，光滑磨皮，塑料质感，过度美颜，假睫毛，浓妆艳抹，模糊，低画质。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/e9dd11e6d9a04716983ad91e6171bb57.png',NULL,'admin','2026-06-28 12:37:01','2026-06-28 12:37:30',0),(4,1,'将图片中人物变得更加性感',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783046533161.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 10:42:44','2026-09-23 11:09:01',0),(5,1,'将图片中人物变得更加性感',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/4c624a3a2f304c5f8f6fbaa2385df6d3.png',NULL,'admin','2026-07-03 10:49:00','2026-07-03 10:49:11',0),(6,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047198555.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 10:54:16','2026-09-23 11:09:01',0),(7,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047731567.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:02:13','2026-09-23 11:09:01',0),(8,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:12:08','2026-09-23 11:09:01',0),(9,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png','url',1024,1024,NULL,NULL,'failed',NULL,NULL,'admin','2026-07-03 11:32:10','2026-09-23 11:09:01',0),(10,1,'将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-07-03/1783051033926.png','url',1024,1024,NULL,NULL,'failed',NULL,'error code: 520\n','admin','2026-07-03 11:57:27','2026-09-23 11:09:01',0),(11,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/10d708e726794eefba734868f6ec0c3a.png',NULL,NULL,'2026-07-11 06:56:54','2026-07-11 06:57:11',0),(12,1,'让图片中的任务更加精美一点',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/1b593d8c012b495ea5e4470f0e7db6fb.png',NULL,NULL,'2026-07-11 07:32:04','2026-07-11 07:32:21',0),(13,1,'让图片中的任务更加精美一点',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/1723c5752dfc450f805ce949c7b5d7e4.png',NULL,NULL,'2026-07-11 07:36:21','2026-07-11 07:36:38',0),(14,1,'a cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'queued',NULL,NULL,'test','2026-08-06 14:41:31','2026-08-06 14:41:31',0),(15,1,'一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'queued',NULL,NULL,NULL,'2026-08-06 14:56:08','2026-08-06 14:56:08',0),(16,1,'一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'queued',NULL,NULL,NULL,'2026-08-06 14:59:26',NULL,0),(17,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,12345,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:01:05',NULL,0),(18,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,12345,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:01:54',NULL,0),(19,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,12345,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:01:55',NULL,0),(20,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,12345,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:02:44',NULL,0),(21,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:03:45',NULL,0),(22,1,'一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'queued',NULL,NULL,NULL,'2026-08-06 15:06:17',NULL,0),(23,1,'a cute cat',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'failed',NULL,'Agnes API returned status 401: {\"error\":{\"code\":\"\",\"message\":\"该令牌状态不可用 (request id: 20260806071659349616729wxCQQDqG)\",\"type\":\"AgnesAI_error\"}}',NULL,'2026-08-06 15:14:40','2026-08-06 15:17:01',0),(24,1,'一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/3e8c1f084a354ea6a8d8ed0795ea7f47.png',NULL,NULL,'2026-08-06 15:21:55','2026-08-06 15:22:08',0),(25,1,'让图中人物带上猫耳发卡',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/f38a9f3dc4874e60acdaf1b52135adeb.png',NULL,NULL,'2026-08-06 15:24:09','2026-08-06 15:24:22',0),(26,1,'让图中人物带上猫耳发卡',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/82faa7751f04417aad7e886aa9b34138.png',NULL,NULL,'2026-08-06 15:40:38','2026-08-06 15:40:49',0),(27,1,'让图中人物带上猫耳发卡',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/1bce0905200049d1b41497b5b98d2524.png',NULL,NULL,'2026-08-06 15:42:13','2026-08-06 15:42:27',0),(28,1,'test',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/e4cd1d72fcc94df7bc69dd67d76f1a94.png',NULL,NULL,'2026-08-06 15:53:10','2026-08-06 15:53:23',0),(29,1,'verify fix',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/a49fa944e07a4ea58f078e8de33f88bd.png',NULL,NULL,'2026-08-06 15:55:51','2026-08-06 15:56:03',0),(30,1,'verify fix v2',NULL,'1024x1024','img2img','https://example.com/fix-test-v2.jpg','url',1024,1024,NULL,NULL,'failed',NULL,'Agnes API returned status 400: {\"error\":{\"message\":\"upload image queue input image[0]: image queue input invalid: download input image failed: HTTP 404 (request id: 2026080607593452767842zNwtllNB)\",\"type\":\"AgnesAI_error\",\"param\":\"\"',NULL,'2026-08-06 15:59:35','2026-08-06 15:59:37',0),(31,1,'让图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://example.com/test.jpg','url',1024,1024,NULL,NULL,'failed',NULL,'Agnes API returned status 400: {\"error\":{\"message\":\"upload image queue input image[0]: image queue input invalid: download input image failed: HTTP 404 (request id: 20260806080737578119486pphr94d3)\",\"type\":\"AgnesAI_error\",\"param\":\"',NULL,'2026-08-06 16:07:38','2026-08-06 16:07:41',0),(32,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_67RyRPfnxENzlcijXpMgrKaR0MBSSPLX/output.png',NULL,NULL,'2026-08-06 16:09:56','2026-09-23 11:09:01',0),(33,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6gkjf5QTMUaxfDdwrpdBxfCjo8BsWCu3/output.png',NULL,NULL,'2026-08-06 16:09:57','2026-09-23 11:09:01',0),(34,1,'将图中人物带上猫耳发卡',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_W1IjuIKZuPxhBLPcJbye5L7W8HljEE3H/output.png',NULL,NULL,'2026-08-06 16:10:14','2026-09-23 11:09:01',0),(35,1,'将图片人物变成朋克赛博风格',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6uzo0OxlW2Y8lQoXwgSMRKUxVkCMbgko/output.png',NULL,NULL,'2026-08-10 10:38:05','2026-09-23 11:09:01',0),(36,1,'将图片人物变成朋克赛博风格，并且要正脸',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_EjrymtBgwwUEyGdPaEBLhGtMGDzDFEJr/output.png',NULL,NULL,'2026-08-10 10:41:29','2026-09-23 11:09:01',0),(37,1,'将图片人物变成朋克赛博风格，并且要正脸',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_hU9zHs3j83VS1Sm166UnDbpKR6ShOHHN/output.png',NULL,NULL,'2026-08-10 10:56:23','2026-09-23 11:09:01',0),(38,1,'美化一下，变成大帅哥，但是要保留原本的人物的特征',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-10/1786330715467.png','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_6yL7LYzYbn0KONMxYYiZUnfMj70FZS9S/output.png',NULL,NULL,'2026-08-10 11:00:33','2026-09-23 11:09:01',0),(39,1,'正脸改成中国水墨侠客风格',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_dZDslANaNGRFMa2zX2UsEntELcUgkyXP/output.png',NULL,NULL,'2026-08-11 11:22:48','2026-09-23 11:09:01',0),(40,1,'正脸改成中国水墨侠客风格',NULL,'1024x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png',NULL,NULL,'2026-08-11 11:22:53','2026-09-23 11:09:01',0),(41,1,'正脸改成中国水墨侠客风格',NULL,'1024x1024','img2img','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png','url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_DKz7DGblyCsOanw1xBQVqLdHaneeqx1B/output.png',NULL,NULL,'2026-08-11 11:24:06','2026-08-11 11:24:27',0),(42,1,'正脸改成赛博朋克风格',NULL,'768x1024','img2img','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png','url',768,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_SzRCLNuOQmRwzIDzCcPJUqACNM90FAJO/output.png',NULL,NULL,'2026-08-11 11:26:18','2026-08-11 11:26:35',0),(43,1,'正脸改成赛博朋克风格',NULL,'768x1024','img2img','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png','url',768,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_CvDd3HPBym5WCfqW19xCR0wy1X85b7yW/output.png',NULL,NULL,'2026-08-11 11:26:55','2026-08-11 11:27:19',0),(44,1,'正面肖像，赛博朋克风格，未来都市夜景背景，霓虹灯光影交织，蓝色与洋红色调为主，人物面部带有机械义体植入细节，半透明发光元件，高科技科技感氛围，精致面部特征，强烈的明暗对比，电影级构图，8K超高清，细节丰富，未来主义美学','blurry, low quality, deformed, watermark, text, signature, bad anatomy, distorted face, ugly, poorly drawn, low resolution, oversaturated, flat lighting','768x1024','img2img','https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png','url',768,1024,2307858878,'cyberpunk','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_8wUHnSvF8QZQdrZkM4oxDaGoEHLVj54p/output.png',NULL,NULL,'2026-08-11 11:45:45','2026-08-11 11:46:02',0),(45,1,'一位优雅的女性角色，身穿精致的粉色小裙子，裙摆轻盈飘逸，采用柔和的粉色调，带有细腻的蕾丝或薄纱材质细节。裙子设计简约而甜美，可能带有蝴蝶结、荷叶边等装饰元素。整体画面呈现梦幻浪漫的氛围，光线柔和温暖，色彩以粉色为主调，搭配清新的背景环境，人物姿态优雅自然，展现出青春活力的气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted, poorly drawn, bad proportions, extra limbs, missing limbs','768x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg','url',768,1024,3221788435,'cinematic','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_BslMpw17QcGdZK1dtdR9XMdx8NrKtQo2/output.png',NULL,NULL,'2026-08-11 11:47:30','2026-09-23 11:09:01',0),(46,1,'一位年轻女性身穿甜美俏皮可爱的粉色小裙子，裙子采用轻盈的雪纺或棉质面料，带有精致的荷叶边装饰和蕾丝花边细节。裙摆蓬松灵动，呈现柔和的樱花粉色调，搭配小巧的蝴蝶结点缀。整体造型充满少女感与活泼气息，姿态轻盈俏皮，面带甜美微笑，眼神明亮灵动。画面采用柔和的自然光或梦幻柔光效果，色调温暖清新，背景可选用花丛、糖果色房间或童话般的小径，营造出浪漫可爱的氛围。构图聚焦人物全身或半身，突出裙子的飘逸质感与甜美气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted face, poor lighting, dull colors, oversaturated, noisy, grainy, low resolution','768x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg','url',768,1024,1921687663,'anime','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_4YA7cs3qAkcXnpQZ20UCuJRYXNvLhxPN/output.png',NULL,NULL,'2026-08-11 11:49:20','2026-09-23 11:09:01',0),(47,1,'一位年轻可爱的女孩，身穿甜美俏皮的粉色小裙子，裙子采用轻盈的薄纱材质，带有精致的荷叶边装饰和蝴蝶结点缀，裙摆蓬松柔软，呈现出梦幻的少女感。整体造型清新活泼，配色以柔和的粉色为主调，搭配白色蕾丝细节。人物表情甜美灵动，眼神清澈明亮，散发青春活力的气息。背景为柔和的浅色调，光线温暖柔和，营造出浪漫温馨的氛围，构图注重展现裙子的飘逸质感和人物的可爱气质。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, ugly, distorted face, poorly drawn hands, extra limbs, bad proportions, oversaturated, dark, gloomy','768x1024','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420527155.webp','url',768,1024,863355902,'anime','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_T4RBYofefRcLRVVmNWBqVbVkMhIShK8A/output.png',NULL,NULL,'2026-08-11 11:55:43','2026-09-23 11:09:01',0),(48,1,'一位年轻女性平躺于柔软的白色床单上，羞涩的表情——双颊微红，眼神温柔地望向镜头，睫毛轻垂，嘴角含着一丝腼腆的微笑。双腿高高抬起并微微弯曲，姿态轻盈自然，穿着简约的浅色家居服，肌肤质感细腻柔和。场景为温馨的卧室，清晨柔和的自然光从窗户斜射进来，形成温暖的金黄色侧逆光，在人物轮廓边缘勾勒出一圈柔和的光晕。构图采用中景平视视角，背景虚化处理，隐约可见床头与淡雅的窗帘。整体氛围宁静、私密而唯美，色调偏暖，低饱和度，带有胶片感的柔和对比度，呈现出一种清新文艺的摄影风格。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, oversaturated, flat lighting, harsh shadows','2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-01/1788266947088.jpg','url',2304,1728,2314301223,'photorealistic','completed','https://platform-outputs.agnes-ai.space/images/i2i/task_oWmX33wngmnyXkabecXbT0XY6WKCRuve/output.png',NULL,NULL,'2026-09-01 20:50:58','2026-09-23 11:09:01',0),(49,1,'二手交易平台商品实拍图：iPhone 14 Pro，95新，手机。干净明亮的背景，柔和自然光，商品居中构图，细节清晰，真实感商品摄影风格，高质量，无文字水印。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_2o79AEQJknDOb4xrU9U8orNa0qLmerFG/output_fb94c71fc08e49a69133c55077de0c69.png',NULL,'ai-assist','2026-09-06 00:18:02','2026-09-06 00:18:10',0),(50,1,'二手交易平台商品实拍图：iPhone 14 Pro，95新，手机。干净明亮的背景，柔和自然光，商品居中构图，细节清晰，真实感商品摄影风格，高质量，无文字水印。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_nF7A1q1xwQhgqtSPcCG1R1vjNQQgf6M3/output_8646031ca0f34318aaeb8ab14e9a76a9.png',NULL,'ai-assist','2026-09-06 00:18:55','2026-09-06 00:19:03',0),(51,1,'二手交易平台商品实拍图：iPhone 14 Pro，95新，手机。干净明亮的背景，柔和自然光，商品居中构图，细节清晰，真实感商品摄影风格，高质量，无文字水印。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_RG7yLyY98cmMzuirBnoytEdumvk048lR/output_08bca1458017480f875b116d2a4b9063.png',NULL,'ai-assist','2026-09-06 00:19:38','2026-09-06 00:19:46',0),(52,1,'二手交易平台商品实拍图：苹果iPhone手机 9成新 功能完好 成色不错，9成新，数码/手机，出一台二手苹果手机，整体成色约9成新，外观保持得不错，没有明显的磕碰或划痕，日常使用痕迹较少。功能一切正常，系统运行流畅，屏幕显示清晰，触控灵敏，电池状态稳定，续航表现符合正常使用水平。机器无拆修，所有功能按键均可正常使用。因为是日常自用换下来的，所以对这台机器比较熟悉，没有隐瞒问题。适合想要入手电梯性价比苹果手机的朋友，成色真实可靠，诚意出售，欢迎感兴趣的朋友联系了解。。干净明亮的背景，柔和自然光，商品居中构图，细节清晰，真实感商品摄影风格，高质量，无文字水印。',NULL,'1024x1024','text2img',NULL,'url',1024,1024,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_IH22K8lLnZedw1R9JkHh8UsCmh2ayY3B/output_71fd95597c17438c87b6cb3f1025cb46.png',NULL,'ai-assist','2026-09-06 17:10:40','2026-09-06 17:10:49',0),(53,38,'一位人物平躺在柔软的平面上，身体放松，双臂自然放置于身体两侧。开始缓慢抬起双腿，双腿从床面缓缓升起，保持腿部线条流畅自然。随后双腿向两侧轻轻岔开，形成一个舒展的Y字形姿态。最后小腿部分微微下垂放松，膝盖保持柔和弯曲，整个动作缓慢优雅，呼吸感明显，展现出身体的柔韧与舒展感。采用中景构图，固定机位平视角度，柔和的室内漫射光，自然肤色质感，画面干净简洁的背景。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, flickering, jittery motion, morphing objects, sudden scene change, extra limbs, distorted joints, unnatural movement, inconsistent character appearance','2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,1052261344,'cinematic','failed',NULL,'Agnes API returned status 404: 404 page not found\n',NULL,'2026-09-10 06:53:47','2026-09-23 11:09:01',1),(54,38,'一位人物平躺在床上，身体舒展放松，双腿缓慢抬起离床面约45度角，随后向两侧自然岔开至舒适幅度，小腿部分微微下垂放松。镜头采用中景固定机位，从正侧面或斜上方俯拍，完整呈现肢体动作轨迹。床品质感柔软，环境光线柔和温暖，氛围宁静居家。动作流畅连贯，无突兀跳跃，保持身体平衡与优雅姿态。','flickering, jittery motion, morphing objects, sudden scene change, deformed limbs, extra limbs, distorted face, blurry, low quality, watermark, text','2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,1358541431,'cinematic','failed',NULL,'Agnes API returned status 404: 404 page not found\n',NULL,'2026-09-10 06:55:48','2026-09-23 11:09:01',1),(55,38,'一只毛茸茸的小橘猫坐在柔软的白色沙滩上，背景是波光粼粼的蔚蓝大海与轻轻拍打岸边的白色浪花，远处海平线上挂着一轮温暖的金色夕阳，天空染上柔和的橙粉渐变色。小猫体型娇小圆润，毛发蓬松柔软，在阳光下泛着温暖的光泽，圆润的脸庞上有一双清澈的大眼睛，表情好奇又呆萌，小小的爪子轻轻踩在沙粒上，偶尔低头闻一闻脚边的贝壳。画面采用中景构图，低角度平视拍摄，营造童话般的治愈氛围，前景有散落的贝壳与小石子增加层次感，整体色调偏暖，带有梦幻柔光感，细节清晰锐利，电影级质感，8K分辨率，光影自然柔和，色温温暖明亮。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, oversaturated, flat lighting, dull colors, noisy, grainy, cropped, out of frame','1024x1024','text2img',NULL,'url',1024,1024,1879321604,'photorealistic','queued',NULL,NULL,NULL,'2026-09-10 06:58:42',NULL,0),(56,38,'一只毛茸茸的小猫站在柔软的白色沙滩上，背景是碧蓝的大海和淡蓝色的天空，远处有轻柔的浪花拍打着岸边，几朵白云漂浮在天空中。小猫毛发呈现温暖的橘黄色与白色相间，耳朵微微竖起，眼神温柔好奇地望向镜头，姿态轻盈灵动。阳光从侧后方洒下，形成柔和的金色轮廓光，照亮了小猫的毛发边缘和沙滩上的细沙纹理。画面采用中景构图，低角度拍摄，突出小猫的主体地位，前景有少量贝壳和海草作为点缀，整体氛围宁静治愈，充满自然美感，细节清晰，色彩明亮温暖。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, oversaturated, flat lighting','1024x1024','text2img',NULL,'url',1024,1024,2557145455,'photorealistic','completed','https://dynamic.senseaudio.cn/image/78d45fa9-4f69-44a0-990b-fb7166562b85',NULL,NULL,'2026-09-10 07:01:41','2026-09-10 07:02:13',0),(57,38,'一位成年女性平躺于白色床单的床上，身体放松伸展，双臂自然放置于身体两侧。她缓慢抬起双腿至垂直角度，随后双腿向两侧岔开形成V字姿态，膝盖保持轻微弯曲，小腿部分缓缓下降至约45度位置后停下。整个动作流畅连贯、速度适中，展现腿部柔韧性与控制力。卧室场景，柔和的自然光从侧窗洒入，营造温馨安静的氛围。固定机位，全身入镜，平视拍摄角度，确保动作全程清晰可见。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, flickering, jittery motion, morphing objects, sudden scene change, distorted proportions, unnatural movement','2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg','url',2304,1728,3748082089,'cinematic','completed','https://dynamic.senseaudio.cn/image/b7804641-9ca7-4770-8f57-530bbef31e05',NULL,NULL,'2026-09-10 07:02:03','2026-09-23 11:09:01',0),(58,38,'一位年轻女性，穿着黑色透肉丝袜，腿部线条修长优雅，面部带有自然的红润气色，双颊泛着淡淡羞涩的红晕。保留原始构图的相机角度与主体姿态，整体穿搭精致时尚。光线柔和均匀，突出丝袜的细腻质感与面部的健康血色感，画面干净高级。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, oversaturated, flat lighting, harsh shadows','2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-11/1789139434533.jpg','url',2304,1728,125189725,'photorealistic','failed',NULL,'senseaudio 调用失败: 商汤调用失败(POST /v1/image/sync status=400): {\"code\":\"upstream_invalid_request\",\"message\":\"图片生成请求无效，请检查输入内容\",\"ref_code\":400100}',NULL,'2026-09-11 23:11:28','2026-09-23 11:09:01',0),(59,38,'一位年轻女性，穿着黑色透肉丝袜，腿部线条修长优雅，面部带有自然的红润气色，双颊泛着淡淡羞涩的红晕。保留原始构图的相机角度与主体姿态，整体穿搭精致时尚。光线柔和均匀，突出丝袜的细腻质感与面部的健康血色感，画面干净高级。',NULL,'2304x1728','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-11/1789139434533.jpg','url',2304,1728,3107532524,NULL,'completed','https://dynamic.senseaudio.cn/image/72542db3-f0ff-4a73-979b-3cbdaa36fad3',NULL,NULL,'2026-09-11 23:22:56','2026-09-23 11:09:01',0),(60,45,'竖屏9:16构图，日系清新漫剧风格，2D扁平上色，清晰锐利的黑色轮廓线，超高细节度。主体为22岁年轻女性角色标准立绘，呈现上半身至大腿中部的半身视角，自然站立姿态，表情温柔恬静。人物特征：浅棕色长卷发自然垂落，发丝纹理细腻，杏眼清澈，浅豆沙色唇妆，身穿米色质地柔软的针织长裙，针织纹理清晰可见。背景为纯净的浅淡纯色，无杂物干扰，突出主体。光照为柔和漫射光，无强烈硬影，整体色彩低饱和度，色调温暖清新，氛围宁静优雅。','blurry, low quality, deformed face, facial distortion, misplaced五官, extra limbs, malformed hands, extra fingers, cluttered background, heavy shadows, harsh lighting, 3d render, photorealistic, watermark, text, signature','2048x2048','text2img',NULL,'url',2048,2048,1633604341,'anime','completed','https://platform-outputs.agnes-ai.space/images/t2i/task_bGIMwRFXqJzGAba6Uw0U9Z7XV45tP35R/output_f8a9648427694dca8f461addca346e44.png',NULL,NULL,'2026-09-19 07:08:05','2026-09-19 07:08:19',0),(61,45,'竖屏9:16构图，日系清新漫剧风格，平涂上色与清晰硬朗的轮廓线，超高细节。主体为22岁年轻女性，浅棕色长卷发自然垂落，杏眼清澈，浅豆沙色唇妆，身穿米色针织长裙，呈现温柔恬静的气质。角色采取自然站立姿态，呈现半身立绘视角。背景为纯净的浅色纯色平面，无杂物干扰。光照采用柔和的漫射光，色彩整体保持低饱和度与柔和质感，营造出干净、通透且舒适的视觉氛围，强调角色面部与服装的高清细节表现。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, facial features misalignment, limb deformity, cluttered decorations, heavy shadows, harsh lighting, complex background, 3d render, realistic photo','1472x2624','text2img',NULL,'url',1472,2624,105343616,'anime','completed','https://platform-outputs.agnes-ai.space/images/t2i/task_2RafgYm05DsUPs8a2OombmHiT3U2iNVm/output_76e6995b1be742db8ef23052ad2eacfa.png',NULL,NULL,'2026-09-19 07:08:47','2026-09-19 07:09:02',0),(62,41,'竖屏9:16构图，日系清新漫剧风格半身立绘。主体为22岁年轻女性，拥有浅棕色长卷发，发丝柔顺且带有自然光泽，杏眼清澈温柔，浅豆沙色唇妆精致淡雅，身穿米色针织长裙，衣物材质呈现柔软细腻的纹理。角色保持自然放松的站姿，双手轻放身侧，气质温婉柔和。背景为纯净的浅色单色底，无杂乱装饰，突出人物主体。光照采用柔和漫射光，色彩整体低饱和、高明度，营造出清新、治愈、通透的氛围。画面具备清晰流畅的轮廓线，平涂上色技法，细节刻画精致，线条干净利落，无多余噪点。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, misaligned facial features, complex cluttered background, heavy shadows, harsh lighting, noisy texture, broken lines','1472x2624','text2img',NULL,'url',1472,2624,430880583,'anime','failed',NULL,'senseaudio 调用失败: 商汤调用失败(POST /v1/image/sync status=400): {\"code\":\"invalid\",\"message\":\"参数错误：size\",\"ref_code\":400000,\"ref_scope\":\"common\"}',NULL,'2026-09-19 07:10:12','2026-09-19 07:10:14',0),(63,38,'一张竖屏9:16比例的女性角色标准立绘，采用日系清新漫剧画风。主体为一名22岁年轻女生，拥有浅棕色长卷发，发丝光泽自然，杏眼清澈，唇色为浅豆沙，身穿米色针织长裙，质地柔软蓬松。角色呈自然站姿，半身构图，神情温柔静谧。背景为纯色浅调，无杂乱元素。光照采用柔和漫射光，无强烈硬影，色彩整体呈现低饱和度的莫兰迪色系，视觉清新治愈。画面线条清晰流畅，平涂上色风格，细节极致丰富，轮廓线锐利，展现高级感的二次元美学。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, messy decorations, heavy shadows, oversaturated, flat lighting, jagged lines, inconsistent style','864x1536','text2img',NULL,'url',864,1536,1076434457,'anime','completed','https://dynamic.senseaudio.cn/image/e963308e-3176-4a54-b3fd-4916b446db2d',NULL,NULL,'2026-09-19 07:10:32','2026-09-19 07:11:11',0),(64,41,'一只圆滚滚的白色小海豹幼崽，全身覆盖着极厚实、蓬松且柔软的纯白绒毛，质感细腻逼真，仿佛能感受到其温暖的触感。它惬意地漂浮在如镜面般平静的深蓝色海面上，身体大部分浸没在水中，仅露出圆润的背部和头部，姿态慵懒而安详。背景是清晨薄雾笼罩的极简海景，远处海天一色，过渡平滑。柔和的清晨金色阳光从侧前方低角度洒落，营造出温暖而宁静的氛围，光线在海豹的绒毛边缘勾勒出细腻的金边（轮廓光），并在清澈的海水表面形成柔和的波光粼粼倒影。采用85mm中焦镜头，平视视角，大光圈营造梦幻般的浅景深效果，背景虚化柔和。高分辨率，8k，极致写实摄影风格，展现生物特有的萌态与自然界静谧美感，色彩清新通透，光影层次丰富。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, harsh shadows, oversaturated, jagged edges, noisy, flat lighting, cartoonish, illustration, painting','1024x1024','text2img',NULL,'url',1024,1024,2162351036,'photorealistic','completed','https://aoss.cn-sh-01.sensecoreapi-oss.cn/nova-clotho-images-sh02/image/019f5fc6-c427-7cb2-b226-e88635fcd8bc/2026/9/18/c722d682-2343-4a4f-a56f-514f49aafd75_0.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=7EA4DE1929F0442E9402B33E0B6EEF70%2F20260918%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260918T233314Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host&X-Amz-Signature=d96fbdc0b165269ea7c3b182f6a764bf63f311ca4af2d093547a8a62bb71a3b0','图片为商汤24h临时URL已过期(2026-09-25核查403)，已废弃',NULL,'2026-09-19 07:33:00','2026-09-25 08:52:23',1),(65,40,'日系清新漫剧风格，竖屏9:16构图。画面主体为一位22岁的年轻女性，呈现半身标准立绘姿态。她拥有浅棕色长卷发，发丝细腻柔顺，自然垂落；五官精致，杏眼清澈温柔，唇色为浅豆沙红，皮肤白皙通透。身穿米色针织长裙，材质纹理清晰可见，剪裁简约舒适，展现出温柔恬静的气质。人物保持自然站姿，四肢比例正常，面部表情平和。背景为纯净的浅色纯色背景，无多余杂乱装饰，突出人物主体。光影采用柔和漫射光，无强烈阴影，色彩整体低饱和度，色调清新柔和。绘画技法强调平涂上色与清晰轮廓线，线条流畅准确，细节超高精度，画面干净整洁，视觉重心明确。','deformed face, misaligned facial features, bad anatomy, extra limbs, distorted body, messy background, cluttered decorations, blurry, low quality, harsh shadows, overexposed, heavy dark shadows, text, watermark, signature','1472x2624','text2img',NULL,'url',1472,2624,1720157580,'anime','failed',NULL,'senseaudio 调用失败: 商汤调用失败(POST /v1/image/sync status=400): {\"code\":\"invalid\",\"message\":\"参数错误：size\",\"ref_code\":400000,\"ref_scope\":\"common\"}',NULL,'2026-09-19 07:40:37','2026-09-19 07:40:39',0),(66,40,'一只毛茸茸的橘色虎斑猫慵懒地趴在窗台上，身体舒展，胡须清晰可见，眼神柔和地注视着前方。旁边是一个质朴的木质花盆，纹理自然，略显粗糙。午后温暖的金色阳光透过玻璃斜射进来，在猫身上形成柔和的高光，并在窗台投下细长的光影。采用浅景深效果，背景虚化，突出主体。写实摄影风格，高分辨率，细节丰富，氛围温馨宁静。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, oversaturated, flat lighting','1472x2624','text2img',NULL,'url',1472,2624,3834605076,'photorealistic','completed','https://dynamic.senseaudio.cn/image/81082f63-945f-4fbf-a0df-98601b2746f9',NULL,NULL,'2026-09-19 07:48:42','2026-09-19 07:49:09',0),(67,45,'竖屏9:16构图，日系清新漫剧风格，平涂上色，线条清晰锐利，超高细节度。画面主体为22岁年轻女性半身立绘，浅棕色长卷发自然垂落，发丝细腻有光泽，杏眼温润有神，眼神柔和专注，浅豆沙色唇妆，米色针织长裙质感柔软贴身，体现温柔知性气质。自然站姿，双手自然下垂或微垂，肢体比例修长标准。背景为纯净浅色系（如淡米白或极浅灰），无杂乱元素，突出人物主体。光影采用柔和漫射光，无强烈高光与深重阴影，色彩低饱和度高纯度，整体氛围清新治愈，视觉层次分明，细节丰富。','脸部变形, 五官错位, 肢体畸形, 杂乱装饰, 画面模糊, 阴影过重, bad anatomy, extra limbs, extra fingers, deformed hands, low quality, blurry, watermark, text, signature','1472x2624','text2img',NULL,'url',1472,2624,1942894402,'anime','completed','https://platform-outputs.agnes-ai.space/images/t2i/task_GGvvqHssX126hk8F9c6jnWyhFbNEYHMb/output_4d826f7558ca4116b3a8cf77862d897a.png',NULL,NULL,'2026-09-19 07:56:57','2026-09-19 07:57:10',0),(68,45,'让图中人物穿黑色丝袜',NULL,'1472x2624','img2img','https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-23/1790153188463.jpg','url',1472,2624,1433416628,NULL,'completed','https://platform-outputs.agnes-ai.space/images/i2i/task_iTDYKUZ8iSDRtveGMeeQ2Zc1AijzJwR8/output.png',NULL,NULL,'2026-09-23 16:49:58','2026-09-23 16:50:39',0),(69,45,'生成一个活泼可爱的唐朝小公主的形象',NULL,'1024x1024','text2img',NULL,'url',1024,1024,397756767,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_W8nGnKuaAFHcflKIyzpHqrJtNNoK2QZ0/output_f01431d3d2a744b59c92758cec24f754.png',NULL,NULL,'2026-09-23 17:01:58','2026-09-23 17:02:10',0),(70,40,'生成一个活泼可爱的唐朝小公主的形象',NULL,'2048x2048','text2img',NULL,'url',2048,2048,2868787378,NULL,'completed','https://dynamic.senseaudio.cn/image/0354fd46-9e0f-46f4-af23-a7069391475a',NULL,NULL,'2026-09-23 17:07:42','2026-09-23 17:08:05',0),(71,38,'生成一个活泼可爱的唐朝小公主的形象',NULL,'1024x1024','text2img',NULL,'url',1024,1024,2119731368,NULL,'completed','https://dynamic.senseaudio.cn/image/f3f0cc45-c2c6-43c8-b545-3f5148085acb',NULL,NULL,'2026-09-23 17:12:30','2026-09-23 17:12:50',0),(72,47,'A weary male figure in his 30s, slumped posture, dull unexpressive eyes, wearing faded, wrinkled gray office clothes, sitting in a dimly lit, cluttered apartment. The lighting is cold and flat, casting long, uninteresting shadows. The color palette is desaturated, dominated by grays, browns, and muted blues, conveying a sense of monotony and lack of vibrancy. The composition is centered but unengaging, with the character occupying the majority of the frame in a static, lifeless pose. The atmosphere is heavy with a sense of ennui and stagnation, emphasizing the mundane and tedious nature of his daily existence.','vibrant, colorful, high energy, smiling, expressive, dynamic, action, bright lighting, saturated colors, happy, energetic, lively, detailed background, high contrast','2048x2048','text2img',NULL,'url',2048,2048,3991658757,'cinematic','completed','https://aoss.cn-sh-01.sensecoreapi-oss.cn/nova-clotho-images-sh02/image/019f5fc6-c427-7cb2-b226-e88635fcd8bc/2026/9/23/96e1a9589e8a4b2c936f93868a711989_0.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=7EA4DE1929F0442E9402B33E0B6EEF70%2F20260923%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260923T123224Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host&X-Amz-Signature=68cce31aa1c0e8eaee497052ef3c7c480e06e8efa66f7dda57a9448e05f4a18e','图片为商汤24h临时URL已过期(2026-09-25核查403)，已废弃',NULL,'2026-09-23 20:31:57','2026-09-25 08:52:23',1),(73,45,'A stunningly beautiful woman wearing a delicate light-black cotton-haltered top with thin straps and a diamond-patterned apron featuring intricate gold line accents; backless design without rear ties, paired with a matching mini skirt of the same soft cotton and gold detailing. Her pose is graceful and slightly turned, highlighting her elegant posture and the outfit\'s refined structure. Soft studio lighting creates gentle highlights on the fabric and skin, with a clean gradient background that emphasizes the subject. Hyper-realistic skin texture, natural makeup, and crisp fabric details with visible weave. Cinematic composition, shallow depth of field, fashion photography, ultra-detailed, 8k resolution, elegant and sophisticated atmosphere.','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, bad hands, messy hair, low resolution, grainy, poor lighting, harsh shadows, oversaturated colors, cartoonish, pixelated, duplicated elements','1728x2304','text2img',NULL,'url',1728,2304,2081496268,'photorealistic','completed','https://platform-outputs.agnes-ai.space/images/t2i/task_eHPXAVQTB3d0OIsQ8XlIY0xAvBplrQWx/output_0661548f8013411491a2a3ba6090c747.png',NULL,NULL,'2026-09-30 15:29:01','2026-09-30 15:29:14',0),(74,47,'A stunningly beautiful woman with delicate features and an elegant pose, wearing a light black soft-cotton halter bodysuit with thin straps and a distinctive diamond-shaped apron-style front panel. The garment features an open back design with no ties or fasteners, highlighting her shoulders and spine. She wears a matching light black soft-cotton mini-skirt with a similar texture. Both the top and skirt are embellished with intricate, subtle gold line patterns and floral embroidery that contrast beautifully against the matte fabric. The lighting is soft and cinematic, highlighting the sheen of the gold threads and the softness of the cotton. The background is a minimalist studio setting with a neutral tone to emphasize the outfit\'s details.','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, messy hair, overly bright colors, pixelated, distorted face, duplicate patterns, loose fabric, rough texture, cartoonish, 3d render artifacts, ugly','1728x2304','text2img',NULL,'url',1728,2304,2866333740,'photorealistic','completed','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-image/f109428aa11b41c597a35280332d25b2.png',NULL,NULL,'2026-09-30 15:30:29','2026-09-30 15:31:10',0),(75,38,'An extreme close-up and full-body cinematic shot of a stunningly beautiful woman with flawless skin and delicate features, exuding an air of modern elegance. She wears a sophisticated light-black cotton halter top with a diamond-patterned apron, featuring thin straps that cross at the neck. The back is designed with an open cut, lacking any ties, highlighting her elegant back and shoulder blades. Her lower body is adorned in a matching light-black soft cotton mini skirt, both garments intricately embroidered with thin, shimmering gold linear floral patterns that contrast beautifully against the dark fabric. The lighting is soft and diffused, creating a gentle glow on her skin while emphasizing the texture of the cotton and the metallic sheen of the gold accents. The composition focuses on the interplay of light and shadow, with a blurred neutral background to keep attention on the subject. High-resolution, 8k, detailed fabric texture, refined fashion photography, ethereal atmosphere.','blurry, low quality, deformed hands, bad anatomy, ugly face, extra limbs, watermark, text, signature, messy hair, dirty clothes, harsh shadows, overexposed, underexposed, cartoonish, 3d render, plastic skin','864x1536','text2img',NULL,'url',864,1536,570023301,'cinematic-fashion','completed','https://dynamic.senseaudio.cn/image/87cf7fe0-a4f6-407a-ba1c-012cd3600cb4',NULL,NULL,'2026-09-30 15:32:37','2026-09-30 15:33:13',0),(76,45,'热衷于科技分享的数字人，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_pBI2vRS4TlRK5W1pWHOCP6YrSxcjInLA/output_a43592f66a86485bbe3c22ea80677158.png',NULL,'creation','2026-10-03 00:26:05','2026-10-03 00:26:17',0),(77,45,'热衷于科技分享的数字人，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_oVALOo0foWO5IxdMpW36uGvbT0boiTD1/output_2c62e55a59ee4ddeae0507de854c955c.png',NULL,'creation','2026-10-03 00:26:17','2026-10-03 00:27:34',0),(78,45,'热衷于科技分享的数字人，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_5iLveUJqkOo2emG8QwFPKlEgGYtKZ7w2/output_1db8b98b04674ab2ae90d432630fa597.png',NULL,'creation','2026-10-03 00:27:35','2026-10-03 00:28:54',0),(79,45,'中景镜头，写实风格，办公室场景。一名戴眼镜的程序员坐在工位前，专注地盯着电脑屏幕，手指正在敲击键盘，面部有轻微反光。画面前景被一个巨大的、半透明的红色叉号（X）视觉元素覆盖，仿佛象征错误或拒绝，红色叉号边缘锐利，色彩饱和度高，与背景形成强烈对比。光源主要来自头顶的冷白顶光，在人物头部和桌面形成高光，并投下清晰的硬阴影，营造出冷峻、紧张的氛围。背景为模糊的办公室环境，可见其他工位和显示器蓝光。整体画质 sharp focus, high contrast, realistic textures, cinematic lighting.',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_ESsyK1ABJUjG8oSnHkTeusEYDSSzIKAN/output_d47d050c10df4148b8704b2eca3a1dbd.png',NULL,'creation','2026-10-03 05:58:39','2026-10-03 05:58:50',0),(80,45,'近景特写镜头，聚焦于一位年轻普通男性，他正蜷缩坐在深灰色布艺沙发上，姿态放松但略显疲惫。他低头凝视手中的智能手机，屏幕发出的强烈冷调蓝光（Cyan-Blue light）是他面部的主要光源，精准照亮他的鼻梁、脸颊和下颌线，并在瞳孔中形成清晰的反射点，周围阴影深沉。背景是昏暗杂乱的客厅，隐约可见失焦的落地灯、散落的抱枕和书架轮廓，营造出景深效果。整体色调采用低饱和度的冷色系，强调蓝色与黑色的对比，辅以少量暖色环境光作为平衡。光影硬朗，营造出强烈的电影感（Cinematic lighting）和悬疑、紧迫的氛围。细节真实，皮肤纹理清晰可见，衣物材质自然，仿佛一部惊悚片中的关键静止帧，静止中充满张力。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_oRqhxFSzlLrGNihmPjTQgUIkzOunlCIb/output_f5213c5be3a4439e8dcc98d21ab81686.png',NULL,'creation','2026-10-03 05:58:39','2026-10-03 05:58:55',0),(81,45,'一位穿着休闲普通装扮的年轻男子，双手抱胸，神情自信从容，站在高楼天台边缘。背景是璀璨的城市夜景，霓虹灯火模糊成景深光斑。中景仰拍视角，凸显人物的高大与气势。侧面金色暖光打亮人物轮廓，形成强烈的明暗对比和电影感光影。励志电影风格，画面构图宏大，氛围充满希望与力量，高清晰度，细节丰富，色彩对比强烈但和谐。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_bqDSANKrEs9GBobNpeDhwCTojd8HyHbl/output_8c397ce2ecb4490f941c22ad18105d58.png',NULL,'creation','2026-10-03 05:58:53','2026-10-03 05:59:03',0),(82,45,'极致特写镜头，聚焦于在笔记本电脑键盘上快速敲击的一双手。背景是简洁的极简风格桌面，笔记本屏幕发出柔和且均匀的冷调蓝光，作为主要光源照亮指尖与键盘缝隙，形成高对比度的科技感光影。采用浅景深效果，前景手指清晰锐利，背景与远处桌面呈现柔和的虚化光斑。色彩以冷青色、深灰与纯白为主，营造高效、专注且充满未来感的数字工作氛围。写实摄影风格，8k分辨率，锐度极高，捕捉皮肤纹理与塑料键盘的材质细节，电影级构图。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_fdbwqNimbugNLeXj3GAlNBVhEhXLKxmW/output_e1a41593d8ce469597ff131299e2e94e.png',NULL,'creation','2026-10-03 05:58:58','2026-10-03 05:59:08',0),(83,45,'分屏式概念画面，中景视角，深色科技空间背景。画面由三个悬浮的半透明屏幕组成：左侧为极简文案编辑界面，显示代码与文本；中间为大型设计软件画布，展示色彩鲜艳的图形编辑过程；右侧为复杂的视频剪辑时间轴，轨道清晰可见。整体笼罩在霓虹蓝紫混合的光效中，屏幕边缘发出柔和的辉光，反射在周围的镜面或金属表面上。未来感、赛博朋克风格，高对比度，锐利焦点，玻璃材质质感，深度虚化背景，电影级布光，8k分辨率，超写实渲染，科技感氛围强烈，视觉层次分明。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_SGB6FcXf9WruK07YQNdLn0nJAEGXTGgP/output_98e4ea26858a426c8d5480b374645885.png',NULL,'creation','2026-10-03 05:59:07','2026-10-03 05:59:19',0),(84,45,'一位穿着剪裁合身深色西装的职场人士站在大型投影屏幕前，神情自信专业，正展示幻灯片。屏幕上色彩丰富、排版清晰的演示页面呈扇形展开，悬浮于空中，营造出现代科技感。背景为现代简约的会议室，隐约可见长桌与座椅。采用中景构图，明亮均匀的商务打光，面部柔和无阴影。整体风格为专业写实，高分辨率，细节锐利，色彩平衡，展现商务演示的专业氛围。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_MTXdpJOaw8Ouj573euS499fiysDlYPKb/output_34c384623ef74922883519fd621d8188.png',NULL,'creation','2026-10-03 05:59:13','2026-10-03 05:59:23',0),(85,45,'中景镜头，一位年轻人坐在温馨的居家工作室里，正对着架在支架上的手机和环形补光灯录制分享视频。桌上摆放着一台打开的笔记本电脑，周围点缀着绿植和书籍，营造出舒适、生活化的氛围。暖色调的环形光柔和地打亮人物面部，眼神专注且充满亲和力。背景略微虚化，突显主体，整体画面呈现生活化写实风格，光影细腻自然。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_XWP9gnXOtl12N7Q9HofStbfbFBFQ5Q8m/output_c72cf35affcb43c1af356f278055d952.png',NULL,'creation','2026-10-03 05:59:23','2026-10-03 05:59:35',0),(86,45,'一位自信的女性博主站在画面中央，直视镜头，眼神坚定且充满感染力。背景为深邃的深色渐变，隐约可见发光的微弱电路纹理光效，营造出神秘且高科技的氛围。中近景构图，焦点清晰集中在人物面部。主光从正前方柔和打亮面部，消除多余阴影，突显皮肤质感；侧后方设置冷色调轮廓光，清晰勾勒出身体与发丝的立体边缘。整体色调偏向冷色系的蓝紫或青绿，具有高级感的科技美学风格，光影层次丰富，细节锐利，呈现出商业级的人像摄影质感。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_HcvOaDXXaTrrxBhuO9yOZBqtpzGZmOh3/output_6b3a398c70774f6c893eb617f7f345b4.png',NULL,'creation','2026-10-03 05:59:28','2026-10-03 05:59:45',0),(87,45,'近景特写镜头，聚焦于一位年轻普通男性，他正蜷缩坐在深灰色布艺沙发上，姿态放松但略显疲惫。他低头凝视手中的智能手机，屏幕发出的强烈冷调蓝光（Cyan-Blue light）是他面部的主要光源，精准照亮他的鼻梁、脸颊和下颌线，并在瞳孔中形成清晰的反射点，周围阴影深沉。背景是昏暗杂乱的客厅，隐约可见失焦的落地灯、散落的抱枕和书架轮廓，营造出景深效果。整体色调采用低饱和度的冷色系，强调蓝色与黑色的对比，辅以少量暖色环境光作为平衡。光影硬朗，营造出强烈的电影感（Cinematic lighting）和悬疑、紧迫的氛围。细节真实，皮肤纹理清晰可见，衣物材质自然，仿佛一部惊悚片中的关键静止帧，静止中充满张力。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_a9BgGpscTC8I64O9HrN5yE0D1sJlZDWK/output_ca3cbc84d7cf4afdbf2bf261f40355c4.png',NULL,'creation','2026-10-03 06:13:47','2026-10-03 06:13:58',0),(88,45,'中景镜头，写实风格，办公室场景。一名戴眼镜的程序员坐在工位前，专注地盯着电脑屏幕，手指正在敲击键盘，面部有轻微反光。画面前景被一个巨大的、半透明的红色叉号（X）视觉元素覆盖，仿佛象征错误或拒绝，红色叉号边缘锐利，色彩饱和度高，与背景形成强烈对比。光源主要来自头顶的冷白顶光，在人物头部和桌面形成高光，并投下清晰的硬阴影，营造出冷峻、紧张的氛围。背景为模糊的办公室环境，可见其他工位和显示器蓝光。整体画质 sharp focus, high contrast, realistic textures, cinematic lighting.',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png',NULL,'creation','2026-10-03 06:14:25','2026-10-03 06:14:37',0),(89,45,'一位穿着休闲普通装扮的年轻男子，双手抱胸，神情自信从容，站在高楼天台边缘。背景是璀璨的城市夜景，霓虹灯火模糊成景深光斑。中景仰拍视角，凸显人物的高大与气势。侧面金色暖光打亮人物轮廓，形成强烈的明暗对比和电影感光影。励志电影风格，画面构图宏大，氛围充满希望与力量，高清晰度，细节丰富，色彩对比强烈但和谐。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png',NULL,'creation','2026-10-03 06:14:48','2026-10-03 06:15:03',0),(90,45,'极致特写镜头，聚焦于在笔记本电脑键盘上快速敲击的一双手。背景是简洁的极简风格桌面，笔记本屏幕发出柔和且均匀的冷调蓝光，作为主要光源照亮指尖与键盘缝隙，形成高对比度的科技感光影。采用浅景深效果，前景手指清晰锐利，背景与远处桌面呈现柔和的虚化光斑。色彩以冷青色、深灰与纯白为主，营造高效、专注且充满未来感的数字工作氛围。写实摄影风格，8k分辨率，锐度极高，捕捉皮肤纹理与塑料键盘的材质细节，电影级构图。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_3sk0bFG2RB4A17RcM7fPz0qy2sg9mEGZ/output_3e23a3dfe7874aa5a52876e10d183db8.png',NULL,'creation','2026-10-03 06:18:30','2026-10-03 06:18:41',0),(91,45,'分屏式概念画面，中景视角，深色科技空间背景。画面由三个悬浮的半透明屏幕组成：左侧为极简文案编辑界面，显示代码与文本；中间为大型设计软件画布，展示色彩鲜艳的图形编辑过程；右侧为复杂的视频剪辑时间轴，轨道清晰可见。整体笼罩在霓虹蓝紫混合的光效中，屏幕边缘发出柔和的辉光，反射在周围的镜面或金属表面上。未来感、赛博朋克风格，高对比度，锐利焦点，玻璃材质质感，深度虚化背景，电影级布光，8k分辨率，超写实渲染，科技感氛围强烈，视觉层次分明。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_sKkqAaXlNHXJGQ9O13crQToGh0W7OSeH/output_5cec61476e9e40648d74b2de7e771457.png',NULL,'creation','2026-10-03 06:22:07','2026-10-03 06:22:18',0),(92,45,'一位穿着剪裁合身深色西装的职场人士站在大型投影屏幕前，神情自信专业，正展示幻灯片。屏幕上色彩丰富、排版清晰的演示页面呈扇形展开，悬浮于空中，营造出现代科技感。背景为现代简约的会议室，隐约可见长桌与座椅。采用中景构图，明亮均匀的商务打光，面部柔和无阴影。整体风格为专业写实，高分辨率，细节锐利，色彩平衡，展现商务演示的专业氛围。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_P7jiopbpfMmWr0Mk82NkqQ25e4rG9B44/output_d689e14192e54de7b46b2ce99d64f47d.png',NULL,'creation','2026-10-03 06:29:28','2026-10-03 06:29:41',0),(93,45,'中景镜头，一位年轻人坐在温馨的居家工作室里，正对着架在支架上的手机和环形补光灯录制分享视频。桌上摆放着一台打开的笔记本电脑，周围点缀着绿植和书籍，营造出舒适、生活化的氛围。暖色调的环形光柔和地打亮人物面部，眼神专注且充满亲和力。背景略微虚化，突显主体，整体画面呈现生活化写实风格，光影细腻自然。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_dQ3EkErXrRp0WFGWaBfgJaVfHj9yI9Sc/output_c93c6ce8c3ce458ca13fdf3f8bf20f9e.png',NULL,'creation','2026-10-03 06:31:34','2026-10-03 06:31:46',0),(94,45,'一位自信的女性博主站在画面中央，直视镜头，眼神坚定且充满感染力。背景为深邃的深色渐变，隐约可见发光的微弱电路纹理光效，营造出神秘且高科技的氛围。中近景构图，焦点清晰集中在人物面部。主光从正前方柔和打亮面部，消除多余阴影，突显皮肤质感；侧后方设置冷色调轮廓光，清晰勾勒出身体与发丝的立体边缘。整体色调偏向冷色系的蓝紫或青绿，具有高级感的科技美学风格，光影层次丰富，细节锐利，呈现出商业级的人像摄影质感。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_teRKhbfRsAHIwbt9HD37KgvYhBmYeuDu/output_1b37d05fb11e4aa1814a5360e5434f0a.png',NULL,'creation','2026-10-03 06:37:48','2026-10-03 06:37:58',0),(95,45,'一位穿着休闲普通装扮的年轻男子，双手抱胸，神情自信从容，站在高楼天台边缘。背景是璀璨的城市夜景，霓虹灯火模糊成景深光斑。中景仰拍视角，凸显人物的高大与气势。侧面金色暖光打亮人物轮廓，形成强烈的明暗对比和电影感光影。励志电影风格，画面构图宏大，氛围充满希望与力量，高清晰度，细节丰富，色彩对比强烈但和谐。',NULL,'736x1312','text2img',NULL,'url',736,1312,NULL,NULL,'completed','https://platform-outputs.agnes-ai.space/images/t2i/task_kV2yPDrEUE7ZesmlD8WN5h6Qrwlilccy/output_721707b3a18848d9a1c35581930954ec.png',NULL,'creation','2026-10-03 08:25:19','2026-10-03 08:25:29',0),(96,45,'一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-image/99c7cd3cd3f8472cb441d3ced26bf6b6.png',NULL,'creation','2026-10-07 09:02:24','2026-10-07 09:02:41',0),(97,45,'一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-image/0a21dee399634aa8bfb50bc03ca1b7e8.png',NULL,'creation','2026-10-07 09:02:42','2026-10-07 09:04:03',0),(98,45,'一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间，统一角色形象定妆照，竖版半身构图，简洁背景，柔和布光，真实感摄影风格，高细节',NULL,'1472x2624','text2img',NULL,'url',1472,2624,NULL,NULL,'completed','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-image/986fefac938a48b08ffc2143cb52d3b0.png',NULL,'creation','2026-10-07 09:04:04','2026-10-07 09:05:25',0),(99,47,'一位28岁中国女程序员半身口播镜头，正对镜头直视观众，面带自然亲和的微笑，嘴唇微张似在生动讲解。她佩戴细框眼镜，梳着整洁的低马尾，身穿燕麦色针织开衫内搭纯白T恤，坐姿端正，双手做出自然专业的讲解手势。背景为现代居家办公桌，双显示器上虚化的代码界面、机械键盘及绿植细节丰富，暖色台灯与屏幕冷光形成冷暖交织的氛围，背景干净且深度虚化。采用50mm镜头，f/1.8大光圈浅景深效果，柔和正面补光，竖屏构图，顶部预留字幕空间，呈现出专业又亲切的口播博主风格，真实摄影质感，高清细节清晰锐利。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, flat lighting, oversaturated, cluttered background','1472x2624','text2img',NULL,'url',1472,2624,3720828539,'photorealistic','completed','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-image/6165cb1a606d43929131e393c84d44bd.png',NULL,NULL,'2026-10-07 09:05:54','2026-10-07 09:06:23',0),(100,40,'一位28岁中国女性程序员博主，半身特写，竖屏构图，上方预留字幕空间。她身穿燕麦色针织开衫内搭白色T恤，佩戴细框眼镜，扎着利落的低马尾，坐姿端正。面部表情自然亲和，嘴角微扬，嘴唇微张仿佛正在语重心长地讲解。双手位于画面下方，做出自然流畅的讲解手势。背景为现代居家办公桌，双显示器上显示着虚化的代码界面，桌案上摆放着机械键盘与生机盎然的绿植。光线采用暖色台灯与屏幕冷光混合，辅以柔和正面补光，营造专业且温馨的氛围。背景干净且深度虚化，使用50mm镜头，f/1.8大光圈产生浅景深效果，突出主体。风格为真实摄影，高清细节，口播博主质感。','blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, extra fingers, distorted face, cluttered background, harsh shadows, oversaturated, flat lighting','1600x2848','text2img',NULL,'url',1600,2848,148228676,'photorealistic','completed','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c',NULL,NULL,'2026-10-07 09:07:56','2026-10-07 09:08:38',0),(101,38,'现代开放式办公室内部，中景构图，聚焦于一位年轻白领女性，身穿剪裁合体的白衬衫，领带微松，神情焦虑紧张，眉头紧锁，手指无意识地敲击桌面，汗珠在微光下若隐若现。其右侧，一位资深同事正自信地单手操作机械键盘，面带微笑，姿态从容。强烈的电影感侧逆光从画面左侧后方射入，形成清晰的轮廓光，勾勒出人物肩颈线条。办公室整体处于冷色调氛围中，主光源为电脑屏幕散发的幽蓝冷光，照亮人物面部并投射在桌面与键盘上，与周围昏暗的暖色环境光形成高对比度。背景虚化，可见模糊的办公格挡、电脑显示器微光及窗外城市夜景，景深较浅。极致写实风格，皮肤质感逼真，眼神专注，空气中漂浮着细微尘埃颗粒，画面具有强烈的叙事张力和职场压迫感。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/0a112adb-c304-4686-8c1b-a63ed825401a',NULL,'creation','2026-10-07 09:48:08','2026-10-07 09:49:40',0),(102,38,'特写镜头：一位表情笃定的演讲者正对镜头，面部占据画面主体，神情严肃且自信，手指果断竖起做出否定手势。背景是深度虚化的深色演播室，隐约可见暗调环境轮廓，突出主体。一束强烈的顶光精准打在人物面部与手部，形成戏剧化的明暗对比，阴影深邃，轮廓清晰。写实摄影风格，电影级质感，细节丰富，纹理真实，色彩还原自然，氛围专注而充满张力。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/4cdcb95b-7343-4331-87c6-18db27d1a16e',NULL,'creation','2026-10-07 09:48:08','2026-10-07 09:49:21',0),(103,38,'一张写实风格的中景照片，展示空旷办公室的一角。画面中心是一张收拾得干干净净的办公桌，桌面上空无一物，仅有少许痕迹。一把办公椅上放着一个棕色的纸板箱，暗示着离职或搬离。背景中，几位身穿商务正装的员工坐在各自的工位上，低着头，专注地工作着，他们的身影略显模糊，营造出一种疏离感。整个场景笼罩在冷灰色的色调中，窗外是阴天的天空，柔和而阴冷的自然光线透过窗户洒入室内，在办公桌和地板上投下淡淡的阴影。氛围显得压抑、寂静且略带忧郁，光影对比自然，材质细节真实，展现了现代职场中一种孤独与转变的瞬间。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/d30b77d4-fc02-4c7f-9693-d3794951d544',NULL,'creation','2026-10-07 09:49:45','2026-10-07 09:51:12',0),(104,38,'深夜昏暗的室内场景，一位设计师身穿休闲便服，疲惫地趴在数位板前，双手揉着眼睛，神态显得十分疲乏。桌面上杂乱无章地堆满了卷曲的草稿纸和几个空咖啡杯，显示出长时间的工作痕迹。前景是复杂的3D设计稿屏幕，散发着清冷的蓝白色光芒，与背景中暖黄色的台灯光形成鲜明的冷暖对比，光影交错营造出一种孤独而专注的氛围。中近景构图，聚焦于人物疲惫的神态与桌面细节，写实风格，高画质，电影级布光，8k分辨率，纹理清晰，浅景深效果突出主体。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/cb96eaee-15a6-4ca6-a482-11b5a1e34c25',NULL,'creation','2026-10-07 09:50:03','2026-10-07 09:51:19',0),(105,38,'明亮宽敞的现代办公环境，一位年轻设计师身着休闲衬衫，面带自信而精神饱满的微笑，正专注操作面前的电脑。中近景构图，视觉重心集中在人物面部表情及屏幕内容。屏幕上整齐排列着多张色彩鲜艳的设计稿缩略图，界面清晰，细节丰富。自然光从落地窗柔和洒入，营造出通透、明亮且专业的氛围。色彩明快清新，以白色、浅灰及屏幕中的多彩色调为主，呈现高保真写实风格，光影细腻，材质真实。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/79e5dd90-2c43-4976-9eea-54029b2f6245',NULL,'creation','2026-10-07 09:51:42','2026-10-07 09:53:03',0),(106,38,'分屏对比构图，画面正中一条锐利垂直明暗分界线。左侧展现昏暗压抑的工作空间，冷色调主导，光线微弱且杂乱，主角面容疲惫，背景模糊杂乱，营造低效与焦虑氛围；右侧呈现明亮高效的工作场景，暖金色调，光线充足均匀，主角神情专注从容，背景整洁有序，突显高效与成就感。中景景别，对称式布局强化对比，写实摄影风格，强调光影反差与色彩冷暖碰撞，细节丰富，高清质感。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/6803149f-a58a-4e87-9eaf-beedb19d474d',NULL,'creation','2026-10-07 09:53:26','2026-10-07 09:55:12',0),(107,38,'中景镜头，写实风格，画面中心是一位面带自信温和笑容的老板，身着剪裁得体的深灰色西装，正与一位穿着简约时尚衬衫的设计师握手，设计师身穿米白色亚麻外套，动作自然优雅。办公桌上整齐摆放着摊开的建筑图纸与平板电脑，纸张边缘泛着柔和的高光。背景是巨大的落地窗，透过玻璃可见模糊的城市天际线，天空呈现淡蓝色调，明亮的暖阳光从左侧射入，在人物面部和文件上投下柔和的阴影，营造出积极、专业且充满信任的商务氛围，画面清晰，构图平衡。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/1800a799-055c-4f74-ba39-1fa86df29c54',NULL,'creation','2026-10-07 09:53:31','2026-10-07 09:55:10',0),(108,38,'演讲者正面面对镜头，微笑着指向镜头下方，背景是简洁明亮的渐变色墙面，柔和均匀的布光，中近景，亲和力强，写实风格。',NULL,'736x1312','img2img','https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c','url',736,1312,NULL,NULL,'completed','https://dynamic.senseaudio.cn/image/1614da6a-3b94-4963-beb6-9b797a71221e',NULL,'creation','2026-10-07 09:55:34','2026-10-07 09:56:00',0),(109,40,'8K ultra-high resolution, Unreal Engine 5 visual quality, PBR physical material rendering, precise individual hair strand simulation. Character design sheet of a female protagonist in an ancient Chinese manga style, embodying Eastern classical aesthetics. An original character depicted as a cold, dignified daughter of a noble family, aged 18-22, resembling a \'moonlight\' figure from an old book. Oval face with fine, slender eyebrows and eyes, calm and detached gaze, straight and elegant nose, pale lips, cool white skin tone, and light pink eye makeup, projecting a restrained and pure temperament. Hairstyle is a \'Flowing Cloud Loose Bun\': a light low bun on top with loose side buns falling naturally, semi-covered long hair at the back, soft cloud-like lines, avoiding heavy palace styles. Accessories include a white jade hairpin, fine pearl chain, and minimal silver tassels placed at the bun top and behind the ears, elegant and cool. Hair details feature airy baby hairs on the forehead, two thin strands near the cheeks, and smooth falling ends. Outfit is a moon-white crossover collar ruqun with light silver dark-pattern embroidery, a thin white silk veil, a simple waistband, and delicate silk sheen. Lighting is soft cold-toned side light against a beige xuan paper background with a thin Chinese frame, clean composition with negative space. Single-person half-body character sheet, frontal slightly angled view, clearly showing hair, accessories, and clothing, refined manga texture.','blurry, low quality, deformed, watermark, text, signature, bad anatomy, modern jewelry, heavy makeup, influencer face, exaggerated big eyes, thick palace hairstyle, cluttered background, harsh lighting, western style, 3d cartoon, low resolution, jpeg artifacts','3456x2592','text2img',NULL,'url',3456,2592,3127969833,'chinese-ink','completed','https://dynamic.senseaudio.cn/image/1fbc0059-ff43-40fc-9be9-b92c7f65d1e2',NULL,NULL,'2026-10-07 18:17:22','2026-10-07 18:17:59',0),(110,40,'8K ultra-high resolution, Unreal Engine 5 aesthetic, PBR physically based rendering, intricate hair strand simulation. Character design sheet of an ancient Chinese drama female lead, embodying Eastern classical aesthetics. An 18-22 year old elite family heiress with a cool, detached \'white moonlight\' aura. Oval face, slender eyebrows and eyes, quiet and aloof gaze, prominent nose bridge, pale lips, cool fair skin with subtle pink eyeshadow, elegant and restrained temperament. Hairstyle: \'Flowing Cloud Drifting Bun\' (Liu Yun Chui Huan Ji), featuring a light low bun on top, side buns falling naturally, half-covered long hair at the back with soft, cloud-like lines, avoiding heavy palace styles. Accessories: white jade hairpin, delicate pearl chain, minimal silver tassels concentrated near the bun and behind ears, keeping the look simple and cold. Hair details: airy stray hairs on forehead, two thin strands framing the cheeks, distinct individual strands, smooth falling ends. Attire: Moon-white cross-collar Ruqun dress with faint silver embroidered patterns, thin white gauze shawl, simple waistband, fabric with delicate silk sheen. Lighting and background: Soft cold-toned side lighting, beige rice paper ancient style background with thin Chinese borders, clean composition with negative space. Single-person half-body character reference sheet, front slightly angled, showing hair, accessories, and costume clearly, high-quality comic drama texture.','blurry, low quality, deformed, watermark, text, signature, bad anatomy, modern jewelry, heavy makeup, influencer face, exaggerated big eyes, thick palace hair bun, cluttered background, noisy, distorted hands','2048x2048','text2img',NULL,'url',2048,2048,60535187,'cinematic-anime','completed','https://dynamic.senseaudio.cn/image/6fa069fe-154f-4520-b3de-b6fdf6281159',NULL,NULL,'2026-10-07 18:19:29','2026-10-07 18:20:02',0),(111,38,'8K ultra-high definition, Unreal Engine 5 render quality, PBR physical materials, intricate single-hair rendering. Character design sheet for a heroine in an ancient Eastern fantasy drama, reflecting classical Oriental aesthetics. The subject is a cool, restrained noble daughter aged 18-22, appearing like a \'white moonlight\' character from old pages. She has an oval face, slender eyebrows and eyes, a calm and distant gaze, a delicate nose bridge, and pale lips with cool fair skin and light pink eye makeup. Her hairstyle is the \'Flowing Cloud Hanging Buns\' (Liuyun Chui Huan Ji): a light low bun on top with natural hanging buns on the sides, half-open long hair at the back with soft, cloud-like lines. Accessories are minimal and elegant, including a white jade hairpin, thin pearl chains, and silver tassels concentrated above the bun and behind the ears. Detailed hair strands show airiness with sparse bangs and side locks. She wears a moon-white cross-collared Ru skirt with light silver hidden embroidery, a thin white gauze shawl, and a simple waistband, featuring fine silk luster. Lighting is soft, cold-toned side light against a beige xuan paper background with a thin Chinese-style border. Single-person half-body character sheet, front slightly angled view, clean composition with ample white space, exquisite comic drama texture.','blurry, low quality, deformed, watermark, text, signature, bad anatomy, modern jewelry, heavy makeup, influencer face, exaggerated large eyes, thick palace hair bun, noisy, chaotic background, western style, cartoonish, flat lighting, oversaturated','1024x1024','text2img',NULL,'url',1024,1024,4053488019,'chinese-ink','completed','https://dynamic.senseaudio.cn/image/e9b56e58-2348-45b3-b2bb-9bc9572a8aab',NULL,NULL,'2026-10-07 18:20:37','2026-10-07 18:22:02',0);
/*!40000 ALTER TABLE `ai_image_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge`
--

DROP TABLE IF EXISTS `ai_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge` (
  `knowledge_id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  `knowledge_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `knowledge_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识库描述',
  `welcome_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '欢迎语',
  `multi_round` int DEFAULT '5' COMMENT '携带上下文会话轮数',
  `top_k` int DEFAULT '3' COMMENT '检索匹配条数',
  `score` decimal(3,2) DEFAULT '0.50' COMMENT '匹配率阈值',
  `slice_size` int DEFAULT '3000' COMMENT '切片大小(字符)',
  `knowledge_sort` int DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`knowledge_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI知识库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge`
--

LOCK TABLES `ai_knowledge` WRITE;
/*!40000 ALTER TABLE `ai_knowledge` DISABLE KEYS */;
INSERT INTO `ai_knowledge` VALUES (1,'邻里二手 开发文档','项目介绍、技术栈、启动方式、开发规范','你好，我是邻里二手开发助手，可以问我项目如何启动、技术栈、RAG 机制等问题。',5,3,0.30,400,1,'admin','2026-08-10 13:56:27',NULL,'2026-10-21 14:16:38',1),(2,'AI 助手使用手册','AI 聊天、文档、知识库、MCP 功能使用说明','你好，我是 AI 助手使用顾问，可以问我怎么用聊天、怎么建知识库。',5,3,0.30,400,2,'admin','2026-08-10 13:56:29',NULL,'2026-08-10 13:59:10',1),(3,'邻里二手 开发文档','项目介绍、技术栈、启动方式、开发规范','你好，我是邻里二手开发助手，可以问我项目如何启动、技术栈、RAG 机制等问题。',5,3,0.30,400,1,'admin','2026-08-10 13:59:12',NULL,'2026-10-21 14:16:38',1),(4,'AI 助手使用手册','AI 聊天、文档、知识库、MCP 功能使用说明','你好，我是 AI 助手使用顾问，可以问我怎么用聊天、怎么建知识库。',5,3,0.30,400,2,'admin','2026-08-10 13:59:14',NULL,'2026-08-10 14:14:09',1),(5,'邻里二手 开发文档','项目介绍、技术栈、启动方式、开发规范','你好，我是邻里二手开发助手，可以问我项目如何启动、技术栈、RAG 机制等问题。',5,3,0.30,400,1,'admin','2026-08-10 14:14:12',NULL,'2026-10-21 14:16:38',0),(6,'AI 助手使用手册','AI 聊天、文档、知识库、MCP 功能使用说明','你好，我是 AI 助手使用顾问，可以问我怎么用聊天、怎么建知识库。',5,3,0.30,400,2,'admin','2026-08-10 14:14:14',NULL,NULL,0),(7,'日常知识',NULL,'帮助用户解答日常生活',5,3,0.50,3000,1,NULL,'2026-08-10 15:55:46',NULL,NULL,0);
/*!40000 ALTER TABLE `ai_knowledge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model`
--

DROP TABLE IF EXISTS `ai_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model` (
  `model_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `model_provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商：openai, deepseek, zhipu, ollama等',
  `model_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型类型：LLM-大语言模型，EMBEDDING-嵌入模型，IMAGE-图像生成，VIDEO-视频生成',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称（标准名称）',
  `model_alias` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型别名（调用时使用）',
  `api_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API密钥',
  `base_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API基础地址',
  `temperature` decimal(2,1) DEFAULT '0.8' COMMENT '温度参数（LLM）',
  `top_p` decimal(2,1) DEFAULT '0.8' COMMENT 'TopP参数（LLM）',
  `ext_param` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '扩展参数JSON',
  `default_flag` tinyint DEFAULT '0' COMMENT '是否默认模型',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-停用，1-启用',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`model_id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_model`
--

LOCK TABLES `ai_model` WRITE;
/*!40000 ALTER TABLE `ai_model` DISABLE KEYS */;
INSERT INTO `ai_model` VALUES (1,'agnes','IMAGE','agnes-image-2.1-flash','agnes-image-2.1-flash','sk-demo-key-replaced','https://apihub.agnes-ai.com',NULL,NULL,NULL,0,1,'admin','2026-06-25 12:50:21','2026-09-09 23:33:24',0),(2,'agnes','VIDEO','agnes-video-v2.0','agnes-video-v2.0','sk-demo-key-replaced','https://apihub.agnes-ai.com',NULL,NULL,NULL,0,1,'admin','2026-06-25 12:50:21','2026-09-17 21:26:40',0),(3,'openai','chat','gpt-4o-test','test-alias','sk-test','https://api.openai.com',NULL,NULL,NULL,0,0,'admin',NULL,'2026-09-17 20:32:37',1),(4,'openai','chat','gpt-4o-mini-test','test-alias2','sk-test','https://api.openai.com',NULL,NULL,NULL,0,0,'admin',NULL,'2026-09-17 20:32:37',1),(5,'openai','chat','create-time-check','ct-check-edited','sk-t','https://x.com',NULL,NULL,NULL,0,0,'admin','2026-09-05 23:46:43','2026-09-17 20:32:37',1),(6,'openai','LLM','agnes-2.5-flash','Agnes文案','sk-demo-key-replaced','https://apihub.agnes-ai.com/v1',0.8,0.8,NULL,0,1,'admin','2026-09-06 00:16:42','2026-09-17 20:59:55',0),(7,'openai','VISION','glm-4v-flash','视觉理解示例(停用)','sk-请替换','https://open.bigmodel.cn/api/paas/v4',0.2,0.8,NULL,0,0,'admin','2026-09-07 00:09:33','2026-09-17 20:32:37',0),(8,'senseaudio','TEXT','senseaudio-s2','SenseAudio S2 自研','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 5, \"price_out\": 30, \"context\": \"256K\"}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(9,'senseaudio','TEXT','senseaudio-s2-flash','SenseAudio S2 Flash 256K (主推荐)','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 2, \"price_out\": 12, \"context\": \"256K\"}',1,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(10,'senseaudio','TEXT','senseaudio-s2-lite','SenseAudio S2 Lite 256K','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 1, \"price_out\": 6, \"context\": \"256K\"}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(11,'senseaudio','TEXT','senseaudio-s1','SenseAudio S1 1M 超长上下文','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 5, \"price_out\": 30, \"context\": \"1M\"}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(12,'senseaudio','VISION','senseaudio-vl-1.0-260319','SenseAudio VL 1.0 视觉语言','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 5, \"price_out\": 30, \"vision\": true}',0,1,NULL,'2026-09-09 23:31:59','2026-09-17 21:05:55',0),(13,'senseaudio','VISION','senseaudio-vl-lite-1.0-260319','SenseAudio VL Lite 1.0','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 1, \"price_out\": 6, \"vision\": true}',0,1,NULL,'2026-09-09 23:31:59','2026-09-17 21:06:21',0),(14,'senseaudio','TEXT','sensenova-6.8-flash-lite','SenseNova 6.8 Flash Lite','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 5, \"price_out\": 30}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(15,'senseaudio','TEXT','qwen3.8-27b','通义千问 3.8 27B','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 3, \"price_out\": 12}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(16,'senseaudio','TEXT','qwen3.6-35b-a3b','通义千问 3.6 35B-A3B MoE','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 1.8, \"price_out\": 10.8}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(17,'senseaudio','TEXT','deepseek-v4-flash-0731','DeepSeek V4 Flash','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 3, \"price_out\": 9}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(18,'senseaudio','TEXT','kimi-k2.6','Kimi K2.6','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 6.5, \"price_out\": 27}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(19,'senseaudio','TEXT','glm-5.3-flash','GLM 5.3 Flash','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 0.8, \"price_out\": 2.8}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(20,'senseaudio','TEXT','glm-5.2','GLM 5.2 阶梯计费(32K 分界)','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in_32k\": 6, \"price_out_32k\": 24, \"price_in_128k\": 8, \"price_out_128k\": 28}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(21,'senseaudio','TEXT','minimax-m2.7','MiniMax-M2.7','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in\": 2.1, \"price_out\": 8.4}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(22,'senseaudio','TEXT','doubao-seed-2-0-pro-260215','Doubao Seed 2.0 Pro 阶梯计费','sk-demo-key-replaced','https://api.senseaudio.cn/v1',NULL,NULL,'{\"price_in_32k\": 3.2, \"price_out_32k\": 16, \"price_in_128k\": 4.8, \"price_out_128k\": 24, \"price_in_256k\": 9.6, \"price_out_256k\": 48}',0,1,NULL,'2026-09-09 23:31:59','2026-10-03 00:41:40',0),(23,'senseaudio','TTS','senseaudio-tts-1.5-260319','SenseAudio TTS 1.5 多情绪多风格','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_10k_char\": 3.5, \"max_emotions\": true}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(24,'senseaudio','TTS','sensenova-tts-2.0','SenseNova TTS 2.0 多情绪多风格','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_10k_char\": 3.5, \"max_emotions\": true}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(25,'senseaudio','ASR','senseaudio-asr-lite-1.5-260319','SenseAudio ASR Lite 1.5 轻量','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_hour\": 0.9}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(26,'senseaudio','ASR','senseaudio-asr-1.5-260319','SenseAudio ASR 1.5 通用','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_hour\": 1.8}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(27,'senseaudio','ASR','senseaudio-asr-pro-1.5-260319','SenseAudio ASR Pro 1.5 高精度','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_hour\": 3.6}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(28,'senseaudio','ASR','senseaudio-asr-deepthink-1.5-260319','SenseAudio ASR DeepThink 1.5 推理增强','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_hour\": 3.6}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(29,'senseaudio','ASR','senseaudio-asr-check-1.5-260319','SenseAudio ASR Check 1.5 复核','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_hour\": 3.6}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(30,'senseaudio','TRANSLATE','sensenova-livetranslate-1.0','SenseNova LiveTranslate 同声传译','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_sec_points\": 50}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(31,'senseaudio','ISOLATE','senseaudio-voice-isolation-1.0-260319','SenseAudio Voice Isolation 1.0','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_sec\": 0.04}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(32,'senseaudio','ISOLATE','senseaudio-voice-isolation-1.5','SenseAudio Voice Isolation 1.5 (推荐)','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_sec\": 0.04}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(33,'senseaudio','MUSIC','senseaudio-music-1.0-260319','SenseAudio Music 1.0','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_song\": 0.5, \"max_duration_sec\": 240}',0,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(34,'senseaudio','MUSIC','senseaudio-music-2.0-260626','SenseAudio Music 2.0 (主推荐)','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_song\": 0.5, \"max_duration_sec\": 600}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(35,'senseaudio','SFX','senseaudio-sfx-1.0-260626','SenseAudio SFX 1.0 音效(默认 4 条/组)','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_group\": 0.08, \"default_count\": 4}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(36,'senseaudio','AUDIO_GEN','senseaudio-a1','SenseAudio A1 数字人口播音频','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_sec_points\": 85, \"approx_cny_per_sec\": 0.017}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(37,'senseaudio','VIDEO','doubao-seedance-2-0-260128','Doubao Seedance 2.0 视频生成','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"resolutions\": [\"480p\", \"720p\", \"1080p\"], \"price_per_sec\": {\"480p\": 0.5, \"720p\": 1.0, \"1080p\": 3.1}, \"max_duration_sec\": 15, \"modes\": [\"text2vid\", \"img2vid\", \"keyframes\", \"ref2vid\"]}',0,1,NULL,'2026-09-09 23:31:59','2026-09-17 20:32:37',0),(38,'senseaudio','IMAGE','senseaudio-image-2.0-260319','SenseAudio Image 2.0 (主推荐)','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_img\":0.5,\"sizes\":[\"1024x1024\",\"1536x864\",\"864x1536\",\"2048x1152\",\"1152x2048\",\"3840x2160\",\"2160x3840\",\"2016x864\",\"2688x1152\"]}',0,1,NULL,'2026-09-09 23:31:59','2026-09-19 07:56:25',0),(39,'senseaudio','IMAGE','senseaudio-image-1.0-260319','SenseAudio Image 1.0','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_img\": 0.2}',0,0,NULL,'2026-09-09 23:31:59','2026-09-19 07:56:25',0),(40,'senseaudio','IMAGE','doubao-seedream-5-0-260128','Doubao Seedream 5.0','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_img\":0.22,\"sizes\":[\"2304x1728\",\"1728x2304\",\"2496x1664\",\"1664x2496\",\"2048x2048\",\"3136x1344\",\"2848x1600\",\"1600x2848\",\"3456x2592\",\"2592x3456\",\"2496x3744\",\"3744x2496\",\"4096x2304\",\"2304x4096\",\"3072x3072\",\"4704x2016\"]}',0,1,NULL,'2026-09-09 23:31:59','2026-09-19 07:56:25',0),(41,'sensenova','IMAGE','sensenova-u1.5-fast','SenseNova U1.5 Fast','sk-demo-key-replaced','https://token.sensenova.cn',NULL,NULL,'{\"protocol\":\"sensenova_images\",\"price_per_img\":0.5,\"sizes\":[\"1024x1024\",\"2048x2048\",\"3072x3072\",\"4096x4096\",\"1152x864\",\"864x1152\",\"2304x1728\",\"1728x2304\",\"3456x2592\",\"2592x3456\",\"1312x736\",\"736x1312\",\"2624x1472\",\"1472x2624\",\"3936x2208\",\"2208x3936\",\"1248x832\",\"832x1248\",\"2496x1664\",\"1664x2496\",\"3744x2496\",\"2496x3744\",\"1568x672\",\"3136x1344\"]}',0,1,NULL,'2026-09-09 23:31:59','2026-09-19 07:22:20',0),(42,'senseaudio','REALTIME','senseaudio-realtime-1.0','SenseAudio Realtime 1.0 实时语音','sk-demo-key-replaced','https://api.senseaudio.cn',NULL,NULL,'{\"price_per_min\": 0.6}',1,1,NULL,'2026-09-09 23:31:59','2026-09-09 23:34:22',0),(43,'openai','LLM','agnes-3.0-flash','agnes-3.0-flash','sk-demo-key-replaced','https://apihub.agnes-ai.com/v1',0.3,1.0,NULL,1,1,NULL,'2026-09-17 20:32:37','2026-09-17 20:59:55',0),(44,'openai','VISION','agnes-3.0-flash','agnes-3.0-flash','sk-demo-key-replaced','https://apihub.agnes-ai.com/v1',0.1,1.0,NULL,1,1,NULL,'2026-09-17 20:32:37','2026-09-17 21:06:21',0),(45,'agnes','IMAGE','agnes-image-2.5-flash','agnes-image-2.5-flash','sk-demo-key-replaced','https://apihub.agnes-ai.com',1.0,1.0,NULL,1,1,NULL,'2026-09-17 20:32:37','2026-09-17 20:32:37',0),(46,'agnes','VIDEO','agnes-video-2.5-flash','agnes-video-2.5-flash','sk-demo-key-replaced','https://apihub.agnes-ai.com',1.0,1.0,NULL,1,1,NULL,'2026-09-17 20:32:37','2026-09-17 21:26:40',0),(47,'sensenova','IMAGE','sensenova-u1.5-lite','SenseNova U1.5 Lite','sk-demo-key-replaced','https://token.sensenova.cn',0.8,0.8,'{\"protocol\":\"sensenova_images\",\"price_per_img\":0.3,\"sizes\":[\"1024x1024\",\"2048x2048\",\"3072x3072\",\"4096x4096\",\"1152x864\",\"864x1152\",\"2304x1728\",\"1728x2304\",\"3456x2592\",\"2592x3456\",\"1312x736\",\"736x1312\",\"2624x1472\",\"1472x2624\",\"3936x2208\",\"2208x3936\",\"1248x832\",\"832x1248\",\"2496x1664\",\"1664x2496\",\"3744x2496\",\"2496x3744\",\"1568x672\",\"3136x1344\"]}',0,1,'admin','2026-09-19 07:22:20',NULL,0);
/*!40000 ALTER TABLE `ai_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_moderation_log`
--

DROP TABLE IF EXISTS `ai_moderation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_moderation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型: product商品 / post动态',
  `biz_id` bigint NOT NULL COMMENT '业务ID(product_id/feed_id)',
  `risk_level` varchar(10) DEFAULT NULL COMMENT '风险档位: low/medium/high; NULL=本次失败',
  `action` varchar(20) NOT NULL COMMENT '处理动作: auto_pass自动通过/manual留人工/auto_reject自动驳回/error失败',
  `violations` varchar(1000) DEFAULT NULL COMMENT '违规明细JSON数组[{type,detail}]',
  `summary` varchar(500) DEFAULT NULL COMMENT 'AI结论摘要(自动驳回时作为驳回理由前缀)',
  `model_name` varchar(128) DEFAULT NULL COMMENT '使用的模型名',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因(action=error时)',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_biz` (`biz_type`,`biz_id`,`log_id`),
  KEY `idx_tenant_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI初审记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_moderation_log`
--

LOCK TABLES `ai_moderation_log` WRITE;
/*!40000 ALTER TABLE `ai_moderation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_moderation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_music_session`
--

DROP TABLE IF EXISTS `ai_music_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_music_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `work_ids` json DEFAULT NULL COMMENT '本会话关联的所有 work_id',
  `chat_history` json DEFAULT NULL COMMENT '与 AI 助手的对话(预留)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闊充箰鍒涗綔浼氳瘽';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_music_session`
--

LOCK TABLES `ai_music_session` WRITE;
/*!40000 ALTER TABLE `ai_music_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_music_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_music_work`
--

DROP TABLE IF EXISTS `ai_music_work`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_music_work` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `type` tinyint NOT NULL COMMENT '1=文生歌 2=平台音色翻唱 3=克隆说唱 4=纯音乐',
  `title` varchar(128) DEFAULT NULL,
  `style_tags` varchar(256) DEFAULT NULL COMMENT '风格标签,逗号分隔: 流行,治愈,女声',
  `bpm` int DEFAULT NULL,
  `key_signature` varchar(8) DEFAULT NULL,
  `lyrics` longtext COMMENT '歌词(含结构标签)',
  `prompt` text COMMENT '用户原始描述',
  `voice_profile_id` bigint DEFAULT NULL COMMENT '关联音色 ID(翻唱/克隆用)',
  `vendor` varchar(32) NOT NULL COMMENT 'senseaudio / siliconflow',
  `vendor_task_id` varchar(128) DEFAULT NULL COMMENT '上游任务 ID',
  `vendor_voice_id` varchar(256) DEFAULT NULL COMMENT '上游音色 ID',
  `audio_url` varchar(512) DEFAULT NULL COMMENT '最终 mp3 OSS 地址',
  `audio_duration_ms` int DEFAULT NULL,
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面图',
  `derived_audio` json DEFAULT NULL COMMENT '多格式音频地址 JSON {mp3, wav}',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=排队 1=生成中 2=已完成 3=失败',
  `error_msg` varchar(512) DEFAULT NULL,
  `quota_cost` int NOT NULL DEFAULT '0',
  `parent_work_id` bigint DEFAULT NULL COMMENT '派生自哪首(二次创作)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`,`del_flag`),
  KEY `idx_vendor_task` (`vendor`,`vendor_task_id`),
  KEY `idx_parent` (`parent_work_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闊充箰浣滃搧';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_music_work`
--

LOCK TABLES `ai_music_work` WRITE;
/*!40000 ALTER TABLE `ai_music_work` DISABLE KEYS */;
INSERT INTO `ai_music_work` VALUES (1,1,1,'旧船票',NULL,NULL,NULL,'[Verse 1]\n凌晨一点的海边\n湿沙留着余温\n浪头刚退去\n他站在水线边\n攥着那张旧船票\n票根褪成了灰\n海风把裙摆吹得响\n像那年码头上的样子\n[Verse 2]\n她说船走了别等\n他点头 没说话\n后来船真的走了\n站台也拆了\n他把船票翻来覆去\n名字看不清了\n只有出发那一格\n还留着她的笔迹\n[Chorus]\n浪退得再远\n也带不走这片沙\n他攥着那张旧船票\n站在夜里\n像还等着\n一个不上船的人\n\n[Verse 3]\n如今他学会折好\n放进口袋里\n湿沙早凉透了\n余温只剩他自己\n海风还在吹\n吹得回忆猎猎响\n他终于转过身\n走了两步\n[Outro]\n走了两步\n又停下\n把船票拿出来\n看了一眼','夏夜海边久别重逢',NULL,'senseaudio','210bf467-e98e-4f4e-8f47-5a6a6683ce82',NULL,'https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/d61cd3ec-e95f-4edc-a726-58c25e1cc023/30cec0dc-c454-41b5-843c-1c489022290b.mp3',60000,NULL,'{\"mp3\": \"https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/7f8187fd-c036-4835-9c6f-f8d16d276356/30cec0dc-c454-41b5-843c-1c489022290b.mp3\", \"wav\": \"https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/68be53b6-8c07-45c5-b293-d25117900d17/30cec0dc-c454-41b5-843c-1c489022290b.wav\"}',2,NULL,5,NULL,'2026-09-12 10:38:05','2026-09-12 10:44:34','2026-09-12 10:44:34',0,NULL,'2026-09-12 10:38:05','2026-09-12 10:44:34'),(2,1,1,NULL,NULL,NULL,NULL,'[Verse]\n七月操场 蝉在叫\n校服贴在背 汗没掉\n汽水水珠 顺着指尖绕\n\n[Chorus]\n你递来 一杯柠檬水\n指尖碰上 心跳就乱了','青春热恋',NULL,'senseaudio','fc12cefd-8b2a-4b88-8336-9f06fb36cee5',NULL,'https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/0c0c290d-dac5-45ca-b46d-80c2e71bdb53/7376c2c6-4113-4f4c-97b3-ca0ee74acde2.mp3',30000,NULL,'{\"mp3\": \"https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/0fbb400a-eac0-47c4-81ff-52c8b668efe6/7376c2c6-4113-4f4c-97b3-ca0ee74acde2.mp3\", \"wav\": \"https://dynamic.senseaudio.cn/music_audio/6f94924e-2ed1-4af2-b469-31c335dc2956/d85a1c21-d940-4d69-a4f3-78a1299f67d9/7376c2c6-4113-4f4c-97b3-ca0ee74acde2.wav\"}',2,NULL,5,NULL,'2026-09-19 08:03:38','2026-09-19 08:05:03','2026-09-19 08:05:03',0,NULL,'2026-09-19 08:03:38','2026-09-19 08:05:03');
/*!40000 ALTER TABLE `ai_music_work` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_persona`
--

DROP TABLE IF EXISTS `ai_persona`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_persona` (
  `persona_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '人设名称',
  `description` varchar(500) DEFAULT NULL COMMENT '账号定位描述',
  `style_fewshot` text COMMENT '文案风格样例（历史爆款片段，few-shot 用）',
  `look_image_urls` text COMMENT '定妆照 URL JSON 数组（≤5 张，视频 reference 用）',
  `voice_id` varchar(128) DEFAULT NULL COMMENT '克隆音色 ID（SiliconFlow voice uri）',
  `voice_name` varchar(128) DEFAULT NULL COMMENT '音色显示名',
  `banned_words` text COMMENT '禁用词，脚本生成时规避',
  `status` smallint DEFAULT '1' COMMENT '1 启用 0 停用',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` smallint DEFAULT '0',
  PRIMARY KEY (`persona_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作-人设库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_persona`
--

LOCK TABLES `ai_persona` WRITE;
/*!40000 ALTER TABLE `ai_persona` DISABLE KEYS */;
INSERT INTO `ai_persona` VALUES (1,'ai小李','ai分享','','[\"https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c\"]','female_0008_a',NULL,'',1,'1','2026-10-02 23:59:17','2026-10-07 09:43:43',0);
/*!40000 ALTER TABLE `ai_persona` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_storyboard_shot`
--

DROP TABLE IF EXISTS `ai_storyboard_shot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_storyboard_shot` (
  `shot_id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `shot_index` int NOT NULL COMMENT '镜序号，从 1 开始',
  `text` varchar(1000) DEFAULT NULL COMMENT '该镜台词',
  `mode` varchar(20) DEFAULT 'img2vid' COMMENT 'text2vid/img2vid/keyframe',
  `image_prompt` text COMMENT '生图提示词（增强后）',
  `video_prompt` text COMMENT '生视频提示词（增强后）',
  `reference_images` text COMMENT '参考图 URL JSON 数组',
  `image_task_id` bigint DEFAULT NULL COMMENT '关联 ai_image_task',
  `video_task_id` bigint DEFAULT NULL COMMENT '关联 ai_video_task',
  `image_url` varchar(500) DEFAULT NULL COMMENT '分镜图成品',
  `clip_url` varchar(500) DEFAULT NULL COMMENT '分镜视频成品',
  `audio_url` varchar(500) DEFAULT NULL COMMENT '该镜 TTS 成品',
  `audio_duration_ms` int DEFAULT NULL COMMENT '音频时长（时间轴锚点）',
  `shot_status` varchar(20) DEFAULT 'pending' COMMENT 'pending/image_done/video_done/audio_done/ready/failed',
  `error_msg` text,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` smallint DEFAULT '0',
  PRIMARY KEY (`shot_id`),
  KEY `idx_project` (`project_id`,`shot_index`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作-作品分镜';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_storyboard_shot`
--

LOCK TABLES `ai_storyboard_shot` WRITE;
/*!40000 ALTER TABLE `ai_storyboard_shot` DISABLE KEYS */;
INSERT INTO `ai_storyboard_shot` VALUES (1,1,1,'别再刷视频了，AI这波红利，普通人再不上车就晚了！','img2vid','近景特写镜头，聚焦于一位年轻普通男性，他正蜷缩坐在深灰色布艺沙发上，姿态放松但略显疲惫。他低头凝视手中的智能手机，屏幕发出的强烈冷调蓝光（Cyan-Blue light）是他面部的主要光源，精准照亮他的鼻梁、脸颊和下颌线，并在瞳孔中形成清晰的反射点，周围阴影深沉。背景是昏暗杂乱的客厅，隐约可见失焦的落地灯、散落的抱枕和书架轮廓，营造出景深效果。整体色调采用低饱和度的冷色系，强调蓝色与黑色的对比，辅以少量暖色环境光作为平衡。光影硬朗，营造出强烈的电影感（Cinematic lighting）和悬疑、紧迫的氛围。细节真实，皮肤纹理清晰可见，衣物材质自然，仿佛一部惊悚片中的关键静止帧，静止中充满张力。','夜晚昏暗的卧室，人物猛然抬头直视镜头，眼神中充满惊恐与紧迫感；手中举着的手机屏幕发出剧烈闪烁的冷白光，光影在面部快速交替，制造出动态的明暗变化；镜头以极快的速度向前推近，从近景瞬间压缩至面部大特写，焦点牢牢锁定在人物瞪大的双眼和紧锁的眉宇之间；背景完全虚化为深黑色，仅保留屏幕光在头发和皮肤边缘形成的微弱轮廓光；整体画面色调阴冷压抑，高对比度光影，电影级惊悚氛围，2.35:1宽银幕画幅，4K超高清质感。','[]',87,67,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/1a767643d9c74f9485fea1739e01c50d.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/18bc14f7870f425fbfc130908598ec07.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/09616b42aa0c4ac4b566a27ac59931e0.mp3',7452,'ready',NULL,'1','2026-10-03 00:47:24','2026-10-03 08:33:26',0),(2,1,2,'很多人觉得AI是程序员的事，错了。','img2vid','中景镜头，写实风格，办公室场景。一名戴眼镜的程序员坐在工位前，专注地盯着电脑屏幕，手指正在敲击键盘，面部有轻微反光。画面前景被一个巨大的、半透明的红色叉号（X）视觉元素覆盖，仿佛象征错误或拒绝，红色叉号边缘锐利，色彩饱和度高，与背景形成强烈对比。光源主要来自头顶的冷白顶光，在人物头部和桌面形成高光，并投下清晰的硬阴影，营造出冷峻、紧张的氛围。背景为模糊的办公室环境，可见其他工位和显示器蓝光。整体画质 sharp focus, high contrast, realistic textures, cinematic lighting.','高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','[]',88,82,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/8ba399ed3c724b27aab582da674a5fcc.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/4b11bfa16f90467a8d82cd9bf6fe4f5f.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/5d5ff3f96de94037972f9060fba40903.mp3',4320,'ready',NULL,'1','2026-10-03 00:47:24','2026-10-03 08:33:37',0),(3,1,3,'现在最赚钱的，是会用AI的普通人。','img2vid','一位穿着休闲普通装扮的年轻男子，双手抱胸，神情自信从容，站在高楼天台边缘。背景是璀璨的城市夜景，霓虹灯火模糊成景深光斑。中景仰拍视角，凸显人物的高大与气势。侧面金色暖光打亮人物轮廓，形成强烈的明暗对比和电影感光影。励志电影风格，画面构图宏大，氛围充满希望与力量，高清晰度，细节丰富，色彩对比强烈但和谐。','低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','[]',95,NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/c1a431207d7f4845976cae7e0ee1b1a1.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/d120213913584d8e8579ec261f50a1b0.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/322bbb142d5a47c09d611ea805036c25.mp3',4896,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:33:50',0),(4,1,4,'第一步，别学原理，直接上手用。','img2vid','极致特写镜头，聚焦于在笔记本电脑键盘上快速敲击的一双手。背景是简洁的极简风格桌面，笔记本屏幕发出柔和且均匀的冷调蓝光，作为主要光源照亮指尖与键盘缝隙，形成高对比度的科技感光影。采用浅景深效果，前景手指清晰锐利，背景与远处桌面呈现柔和的虚化光斑。色彩以冷青色、深灰与纯白为主，营造高效、专注且充满未来感的数字工作氛围。写实摄影风格，8k分辨率，锐度极高，捕捉皮肤纹理与塑料键盘的材质细节，电影级构图。','特写镜头聚焦于手指在机械键盘上快速敲击的特写画面，指尖精准触动键帽，键盘轴体发出轻微反馈；屏幕散发出冷调蓝紫色光晕，随着敲击节奏产生高频脉动与呼吸感，光影柔和地映在皮肤与金属边框上；镜头以缓慢平稳的速度围绕手部进行360度环绕移动，捕捉不同角度下的手指动态与屏幕反光变化；背景为深暗的科技感工作室，景深极浅，背景虚化柔和，突出前景的手部动作与键盘细节；画面具有电影级质感，色彩对比度高，细节锐利，营造出专注且充满科技氛围的视觉效果。','[]',90,74,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/ca2efe5d50d6495c8c844281348bff72.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/8022ddbf5a574ad4a1a5067d3dc16f14.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/5369818849f047b98ebe11a85d3d7b3a.mp3',4500,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:34:01',0),(5,1,5,'写文案、做图、剪视频，让AI帮你干，效率翻十倍。','img2vid','分屏式概念画面，中景视角，深色科技空间背景。画面由三个悬浮的半透明屏幕组成：左侧为极简文案编辑界面，显示代码与文本；中间为大型设计软件画布，展示色彩鲜艳的图形编辑过程；右侧为复杂的视频剪辑时间轴，轨道清晰可见。整体笼罩在霓虹蓝紫混合的光效中，屏幕边缘发出柔和的辉光，反射在周围的镜面或金属表面上。未来感、赛博朋克风格，高对比度，锐利焦点，玻璃材质质感，深度虚化背景，电影级布光，8k分辨率，超写实渲染，科技感氛围强烈，视觉层次分明。','0-2秒：深邃的暗色背景中，三个半透明的悬浮全息屏幕依次从中心向外亮起，屏幕边缘闪烁着幽蓝色的科技光晕，屏幕内显示着动态的数据流与抽象的几何图形，伴随轻微的机械旋转展示内部内容；2-5秒：成千上万颗微小的金色光粒子在屏幕之间穿梭流动，形成灵动的粒子轨迹，光影交织出梦幻般的立体空间感，粒子碰撞时产生柔和的光爆效果；6-10秒：镜头保持水平缓慢横移，模拟视线扫过屏幕群的流畅视角，景深逐渐拉远展现屏幕阵列的全貌，整体氛围充满未来感与静谧的高级科技感，画面清晰锐利，色彩以深蓝与亮金为主。','[]',91,62,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/551395e454904422ab83f6752d50a5af.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/c14f738bc7df42c08aebfb3806a07310.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/dc28bcbef2ac4f46a4d1c1bad3f19e03.mp3',7452,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:34:17',0),(6,1,6,'第二步，把你会的技能加上AI。比如你是做PPT的，用AI十分钟出十套方案，客户排队找你。','img2vid','一位穿着剪裁合身深色西装的职场人士站在大型投影屏幕前，神情自信专业，正展示幻灯片。屏幕上色彩丰富、排版清晰的演示页面呈扇形展开，悬浮于空中，营造出现代科技感。背景为现代简约的会议室，隐约可见长桌与座椅。采用中景构图，明亮均匀的商务打光，面部柔和无阴影。整体风格为专业写实，高分辨率，细节锐利，色彩平衡，展现商务演示的专业氛围。','动态视觉序列，多张半透明幻灯片页面如同扑克牌般以高速度快速连续展开，层层叠叠铺满整个屏幕，边缘锐利，光影交错；画面底部，深色的客户剪影依次淡入出现，整齐排成横向队列，保持静止姿态，身份清晰；镜头从近景特写缓缓拉远至大远景，展现整体构图，背景为深蓝色科技风格，柔和顶光，高清晰度，电影感色调，流畅连贯的动画过渡。','[]',92,75,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/4f93ea63c2a6465e9c3959711d381cff.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/6083bc9cef234d859ab90f1439b82325.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/9f2979ab86da401d8bef24f2d937282f.mp3',11196,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:34:40',0),(7,1,7,'第三步，边用边分享。你踩过的坑，就是别人的教程，发出来就是流量。','img2vid','中景镜头，一位年轻人坐在温馨的居家工作室里，正对着架在支架上的手机和环形补光灯录制分享视频。桌上摆放着一台打开的笔记本电脑，周围点缀着绿植和书籍，营造出舒适、生活化的氛围。暖色调的环形光柔和地打亮人物面部，眼神专注且充满亲和力。背景略微虚化，突显主体，整体画面呈现生活化写实风格，光影细腻自然。','中近景视角，一位时尚年轻博主正对着镜头进行充满活力的口型讲述，面部表情生动夸张，眼神直视前方，配合言语节奏做出富有感染力的手部手势，如比心或强调性挥动。背景为简约温馨的室内直播间，暖色调LED灯带营造柔和氛围，光影立体。随着讲述高潮，手机屏幕界面中红色的点赞爱心图标密集生成并不断向上漂浮，粒子特效梦幻且明亮，与人物动态形成虚实互动。镜头采用轻微手持晃动模拟真实直播质感，焦点始终锁定在人物面部与上半身，背景微虚化，色彩饱和度高，整体呈现高清、活泼、社交媒体的视觉风格。','[]',93,64,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/ccd5b54f3bd1420e8fb07af9da0eeb5b.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/7dbdda26dcc449e998e102e3de6fabd1.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/68241420f21245cd8aedd92e820ea4b0.mp3',9936,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:34:57',0),(8,1,8,'记住，淘汰你的不是AI，是会用AI的人。关注我，下期教你三个免费AI神器。','img2vid','一位自信的女性博主站在画面中央，直视镜头，眼神坚定且充满感染力。背景为深邃的深色渐变，隐约可见发光的微弱电路纹理光效，营造出神秘且高科技的氛围。中近景构图，焦点清晰集中在人物面部。主光从正前方柔和打亮面部，消除多余阴影，突显皮肤质感；侧后方设置冷色调轮廓光，清晰勾勒出身体与发丝的立体边缘。整体色调偏向冷色系的蓝紫或青绿，具有高级感的科技美学风格，光影层次丰富，细节锐利，呈现出商业级的人像摄影质感。','未来科技感场景，人物站在画面中央，指尖指向镜头下方，眼神坚定，表情自信；背景由多层全息电路板与发光线路构成，光效呈冷蓝色，数据流缓缓流动，节点周期性闪烁，营造稳定、秩序、数字化的氛围；机位保持正面平视，从全景缓慢推近至半身中景，人物头部与肩部始终居中，焦点锁定面部与手势，背景虚化但光效保持清晰；冷色调霓虹光影，轻微体积光与丁达尔效应，电影级科幻质感，节奏平稳，一镜到底。','[]',94,65,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/shot/6a8b77d619f845ac91a17dea931a2b58.jpg','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/clip/901f07aef4704aa2b1e07c3a00723609.mp4','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/b07a00b90f8e46f2846d0ea088ef1d7d.mp3',10800,'ready',NULL,'1','2026-10-03 00:47:25','2026-10-03 08:35:15',0),(9,2,1,'AI不会淘汰你，但会用AI的同事会。','img2vid','现代开放式办公室内部，中景构图，聚焦于一位年轻白领女性，身穿剪裁合体的白衬衫，领带微松，神情焦虑紧张，眉头紧锁，手指无意识地敲击桌面，汗珠在微光下若隐若现。其右侧，一位资深同事正自信地单手操作机械键盘，面带微笑，姿态从容。强烈的电影感侧逆光从画面左侧后方射入，形成清晰的轮廓光，勾勒出人物肩颈线条。办公室整体处于冷色调氛围中，主光源为电脑屏幕散发的幽蓝冷光，照亮人物面部并投射在桌面与键盘上，与周围昏暗的暖色环境光形成高对比度。背景虚化，可见模糊的办公格挡、电脑显示器微光及窗外城市夜景，景深较浅。极致写实风格，皮肤质感逼真，眼神专注，空气中漂浮着细微尘埃颗粒，画面具有强烈的叙事张力和职场压迫感。','写实主义风格，4K 高清电影质感。镜头缓缓推近一名白领的面部特写，他面色凝重，眼神中流露不安，视线紧张地瞥向左侧；旁边同事手指在键盘上快速敲击，机械键盘的轻微振动与屏幕发出的冷白光微微闪烁，映照在两人面部；背景虚化，隐约可见模糊的人影在办公区走动。采用浅景深，侧光照明，冷暖色调对比，营造出压抑且充满悬疑感的职场氛围，画面稳定，细节丰富，光影自然过渡。','[]',101,95,'https://dynamic.senseaudio.cn/image/0a112adb-c304-4686-8c1b-a63ed825401a',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/eeebae9a51884a5b8dc6c4199d448acf.mp3',3960,'failed','镜 1 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 09:58:30',0),(10,2,2,'你以为AI来了大家都失业？错。','img2vid','特写镜头：一位表情笃定的演讲者正对镜头，面部占据画面主体，神情严肃且自信，手指果断竖起做出否定手势。背景是深度虚化的深色演播室，隐约可见暗调环境轮廓，突出主体。一束强烈的顶光精准打在人物面部与手部，形成戏剧化的明暗对比，阴影深邃，轮廓清晰。写实摄影风格，电影级质感，细节丰富，纹理真实，色彩还原自然，氛围专注而充满张力。','中近景镜头捕捉演讲者面部，他神情严肃，坚定有力地摇头并挥动手臂，动作果断且富有张力；背景光影缓慢流动，色彩由暗转亮再回落，营造紧张转折氛围；镜头起始有轻微手持晃动感，随后迅速稳定，焦点始终锁定在演讲者面部；环境为室内演讲舞台，深蓝色调，侧逆光突出轮廓，画面电影感强，高清晰度，情绪凝重。','[]',102,94,'https://dynamic.senseaudio.cn/image/4cdcb95b-7343-4331-87c6-18db27d1a16e',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/4b5ee89984e5423181f0ad557e96c504.mp3',4176,'failed','镜 2 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 09:58:30',0),(11,2,3,'公司先裁的，是拒绝用AI的人。','img2vid','一张写实风格的中景照片，展示空旷办公室的一角。画面中心是一张收拾得干干净净的办公桌，桌面上空无一物，仅有少许痕迹。一把办公椅上放着一个棕色的纸板箱，暗示着离职或搬离。背景中，几位身穿商务正装的员工坐在各自的工位上，低着头，专注地工作着，他们的身影略显模糊，营造出一种疏离感。整个场景笼罩在冷灰色的色调中，窗外是阴天的天空，柔和而阴冷的自然光线透过窗户洒入室内，在办公桌和地板上投下淡淡的阴影。氛围显得压抑、寂静且略带忧郁，光影对比自然，材质细节真实，展现了现代职场中一种孤独与转变的瞬间。','镜头从空桌缓慢横移到远处忙碌的员工，纸箱上灰尘微粒在光束中飘浮，画面安静肃穆。','[]',103,88,'https://dynamic.senseaudio.cn/image/d30b77d4-fc02-4c7f-9693-d3794951d544','https://dynamic.senseaudio.cn/video/5ac9ed6f-417b-4271-91c4-6c6e341f5f05','https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/4a27383f7be84c21978a3ae9c194c714.mp3',3888,'ready',NULL,'1','2026-10-07 09:45:38','2026-10-07 09:53:28',0),(12,2,4,'我朋友做设计，以前改一张图要3天。','img2vid','深夜昏暗的室内场景，一位设计师身穿休闲便服，疲惫地趴在数位板前，双手揉着眼睛，神态显得十分疲乏。桌面上杂乱无章地堆满了卷曲的草稿纸和几个空咖啡杯，显示出长时间的工作痕迹。前景是复杂的3D设计稿屏幕，散发着清冷的蓝白色光芒，与背景中暖黄色的台灯光形成鲜明的冷暖对比，光影交错营造出一种孤独而专注的氛围。中近景构图，聚焦于人物疲惫的神态与桌面细节，写实风格，高画质，电影级布光，8k分辨率，纹理清晰，浅景深效果突出主体。','深夜办公室，冷色调蓝光从电脑屏幕投射在设计师疲惫的脸上，特写镜头捕捉他/她痛苦地揉捏眼珠，随后缓慢伸懒腰，手臂舒展，动作充满疲惫感；屏幕上UI设计稿反复闪烁修改，光标快速跳动，反映焦虑状态；镜头缓慢向后拉远并略微升高，逐渐揭示桌面杂乱无章的现实，堆满揉皱的草图纸张、废弃的马克笔、咖啡杯和凌乱线缆，背景昏暗，强调孤立与高压氛围，写实主义风格，电影级景深。','[]',104,96,'https://dynamic.senseaudio.cn/image/cb96eaee-15a6-4ca6-a482-11b5a1e34c25',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/ffebfb968b9847f48c4d5a277133657b.mp3',5040,'failed','镜 4 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 10:00:26',0),(13,2,5,'现在用AI，一天交20版。','img2vid','明亮宽敞的现代办公环境，一位年轻设计师身着休闲衬衫，面带自信而精神饱满的微笑，正专注操作面前的电脑。中近景构图，视觉重心集中在人物面部表情及屏幕内容。屏幕上整齐排列着多张色彩鲜艳的设计稿缩略图，界面清晰，细节丰富。自然光从落地窗柔和洒入，营造出通透、明亮且专业的氛围。色彩明快清新，以白色、浅灰及屏幕中的多彩色调为主，呈现高保真写实风格，光影细腻，材质真实。','中近景镜头捕捉一位年轻设计师在明亮的工作台前，面前多台显示器上UI设计稿与3D模型快速刷新、滚动生成，屏幕光影映照在设计师专注的面庞上；设计师嘴角微微上扬，自信地点头微笑，眼神中透着满足与肯定；随着情绪推进，背景环境光线逐渐由柔和过渡至明亮通透，自然光与屏幕光交织，营造出高效、现代的科技感氛围；镜头缓慢轻微上扬，模拟仰视视角，强化空间感与积极向上的视觉张力，整体色调清新明亮，细节锐利，电影级画质。','[]',105,97,'https://dynamic.senseaudio.cn/image/79e5dd90-2c43-4976-9eea-54029b2f6245',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/61bea17ce13c42c08ef78b6fd5678bef.mp3',3744,'failed','镜 5 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 10:00:25',0),(14,2,6,'3天对20版，差距就这么拉开了。','img2vid','分屏对比构图，画面正中一条锐利垂直明暗分界线。左侧展现昏暗压抑的工作空间，冷色调主导，光线微弱且杂乱，主角面容疲惫，背景模糊杂乱，营造低效与焦虑氛围；右侧呈现明亮高效的工作场景，暖金色调，光线充足均匀，主角神情专注从容，背景整洁有序，突显高效与成就感。中景景别，对称式布局强化对比，写实摄影风格，强调光影反差与色彩冷暖碰撞，细节丰富，高清质感。','镜头从分界线缓慢向两侧拉开，左侧画面逐渐变暗，右侧画面逐渐变亮，对比感不断强化。','[]',106,98,'https://dynamic.senseaudio.cn/image/6803149f-a58a-4e87-9eaf-beedb19d474d',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/b6ffcf3795914ada8d7b6a6a0803fb76.mp3',3564,'failed','镜 6 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 10:02:20',0),(15,2,7,'老板没裁他，反而给他涨了薪。','img2vid','中景镜头，写实风格，画面中心是一位面带自信温和笑容的老板，身着剪裁得体的深灰色西装，正与一位穿着简约时尚衬衫的设计师握手，设计师身穿米白色亚麻外套，动作自然优雅。办公桌上整齐摆放着摊开的建筑图纸与平板电脑，纸张边缘泛着柔和的高光。背景是巨大的落地窗，透过玻璃可见模糊的城市天际线，天空呈现淡蓝色调，明亮的暖阳光从左侧射入，在人物面部和文件上投下柔和的阴影，营造出积极、专业且充满信任的商务氛围，画面清晰，构图平衡。','现代简约明亮的办公室内，身穿深色西装的老板与穿着休闲职业装的设计师面对面站立，两人动作自然流畅地紧握双手，随后老板有力地竖起大拇指，设计师面部肌肉放松，露出惊喜且真诚的灿烂笑容。背景是落地窗，午后黄金时段的阳光透过玻璃洒入室内，形成清晰的丁达尔光束，照亮空气中的微尘。镜头以人物为圆心进行缓慢的水平环绕拍摄，始终保持中近景构图，浅景深效果虚化背景办公设施，焦点牢牢锁定在两人互动的面部表情与手部细节上。整体色调温暖明快，光影柔和，充满职业成就感与温馨积极的商务氛围，电影级写实画质。','[]',107,99,'https://dynamic.senseaudio.cn/image/1800a799-055c-4f74-ba39-1fa86df29c54',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/12abe09d18634de59ca3111146b92563.mp3',3600,'failed','镜 7 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 10:02:21',0),(16,2,8,'你不用成为专家。先把AI当助手用起来就行。想看我用的工具清单？评论区扣1。','img2vid','演讲者正面面对镜头，微笑着指向镜头下方，背景是简洁明亮的渐变色墙面，柔和均匀的布光，中近景，亲和力强，写实风格。','演讲者微笑点头，手指向镜头下方示意，身体微微前倾，镜头缓慢推近，营造互动感。','[]',108,100,'https://dynamic.senseaudio.cn/image/1614da6a-3b94-4963-beb6-9b797a71221e',NULL,'https://your-bucket.oss-cn-beijing.aliyuncs.com/ai-creation/audio/9726d54a13ff4a179db5b2cff089f4c6.mp3',8424,'failed','镜 8 生视频失败: 视频生成服务暂时不可用，请稍后再试（已重试5次）','1','2026-10-07 09:45:38','2026-10-07 10:04:15',0);
/*!40000 ALTER TABLE `ai_storyboard_shot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_usage_daily`
--

DROP TABLE IF EXISTS `ai_usage_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `calls` int NOT NULL DEFAULT '0' COMMENT '调用次数',
  `prompt_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输入 token 累计',
  `completion_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输出 token 累计',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_tenant_date` (`tenant_id`,`stat_date`)
) ENGINE=InnoDB AUTO_INCREMENT=215 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 用量日账单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_usage_daily`
--

LOCK TABLES `ai_usage_daily` WRITE;
/*!40000 ALTER TABLE `ai_usage_daily` DISABLE KEYS */;
INSERT INTO `ai_usage_daily` VALUES (1,2,'2026-08-29',14,17843,2276,'2026-08-29 11:50:22'),(15,1,'2026-08-29',1,770,23,'2026-08-29 11:53:55'),(16,2,'2026-09-01',4,7697,439,'2026-09-01 21:01:33'),(25,1,'2026-09-06',14,12045,1270,'2026-09-06 17:13:15'),(50,2,'2026-09-06',2,2361,296,'2026-09-06 12:49:29'),(69,1,'2026-09-07',1,331,107,'2026-09-07 08:55:14'),(71,1,'2026-09-10',2,0,0,'2026-09-10 23:08:10'),(73,1,'2026-09-11',3,0,0,'2026-09-11 20:57:59'),(76,1,'2026-09-17',3,0,0,'2026-09-17 21:00:24'),(79,1,'2026-09-19',1,0,0,'2026-09-19 06:52:49'),(80,1,'2026-09-23',2,0,0,'2026-09-23 16:59:42'),(83,1,'2026-09-24',2,1335,100,'2026-09-24 11:44:42'),(86,1,'2026-09-25',1,0,0,'2026-09-25 08:36:48'),(87,1,'2026-09-26',1,0,0,'2026-09-26 19:15:12'),(88,1,'2026-09-30',1,0,0,'2026-09-30 15:36:51'),(89,1,'2026-10-02',1,0,0,'2026-10-02 22:13:12'),(90,1,'2026-10-03',86,0,0,'2026-10-03 08:47:01'),(176,1,'2026-10-07',37,0,0,'2026-10-07 10:02:20'),(213,1,'2026-10-29',2,0,0,'2026-10-29 14:20:11');
/*!40000 ALTER TABLE `ai_usage_daily` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_usage_log`
--

DROP TABLE IF EXISTS `ai_usage_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '调用用户ID(Java透传X-User-Id/请求体userId)',
  `feature` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '功能标识(product-copy/product-image)',
  `model_id` bigint DEFAULT NULL COMMENT 'AI模型ID(ai_model.model_id)',
  `model_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '模型名称',
  `task_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'text' COMMENT '任务类型(text/image)',
  `cost` int NOT NULL DEFAULT '1' COMMENT '计费次数成本(按次计费=1)',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_log_tenant_time` (`tenant_id`,`create_time`),
  KEY `idx_ai_usage_log_feature` (`feature`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=152 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI调用明细账单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_usage_log`
--

LOCK TABLES `ai_usage_log` WRITE;
/*!40000 ALTER TABLE `ai_usage_log` DISABLE KEYS */;
INSERT INTO `ai_usage_log` VALUES (1,2,'2','product-copy',6,'agnes-2.5-flash','text',1,'2026-09-06 05:53:26'),(2,1,'1','product-copy',6,'agnes-2.5-flash','text',1,'2026-09-06 05:56:14'),(3,1,'1','product-copy',6,'agnes-2.5-flash','text',1,'2026-09-06 06:09:26'),(5,1,'','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 08:04:31'),(6,2,'','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 08:04:32'),(7,2,'','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 08:04:58'),(8,2,'125','product-copy',6,'agnes-2.5-flash','text',1,'2026-09-06 08:04:59'),(9,1,'1','daily-report',6,'agnes-2.5-flash','text',1,'2026-09-06 10:27:09'),(10,1,'','daily-report',6,'agnes-2.5-flash','text',1,'2026-09-06 10:36:43'),(11,1,'','daily-report',6,'agnes-2.5-flash','text',1,'2026-09-06 10:36:54'),(12,2,'','daily-report',6,'agnes-2.5-flash','text',1,'2026-09-06 10:38:30'),(13,1,'','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:40:01'),(14,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:46:16'),(15,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:47:42'),(16,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:48:06'),(17,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:48:56'),(18,2,'125','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 12:49:29'),(19,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 13:19:54'),(20,1,'1','product-copy',6,'agnes-2.5-flash','text',1,'2026-09-06 17:10:29'),(21,1,'1','product-image',1,'agnes-image-2.1-flash','image',1,'2026-09-06 17:10:49'),(22,1,'1','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-06 17:13:15'),(23,1,'','chat',NULL,'agnes-2.5-flash','text',1,'2026-09-07 08:55:14'),(24,1,'1','home-quote',NULL,'agnes-2.5-flash','text',1,'2026-09-10 23:08:12'),(25,1,'1','home-quote',NULL,'agnes-2.5-flash','text',1,'2026-09-10 23:08:17'),(26,1,'1','home-quote',NULL,'agnes-2.5-flash','text',1,'2026-09-11 20:47:04'),(27,1,'1','home-quote',NULL,'agnes-2.5-flash','text',1,'2026-09-11 20:58:03'),(28,1,'1','moderation',43,'agnes-3.0-flash','text',1,'2026-09-17 20:35:20'),(29,1,'1','moderation',43,'agnes-3.0-flash','text',1,'2026-09-17 20:35:29'),(30,1,'1','moderation',43,'agnes-3.0-flash','text',1,'2026-09-17 21:00:27'),(31,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-19 06:52:51'),(32,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-23 13:53:17'),(33,1,'1','chat',NULL,'agnes-3.0-flash','text',1,'2026-09-23 16:59:42'),(34,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-24 11:19:01'),(35,1,'1','chat',NULL,'agnes-3.0-flash','text',1,'2026-09-24 11:44:43'),(36,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-25 08:36:52'),(37,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-26 19:15:15'),(38,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-09-30 15:36:56'),(39,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-10-02 22:13:14'),(40,1,'1','creation-script',NULL,'','text',1,'2026-10-03 00:47:24'),(41,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:23'),(42,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:24'),(43,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:24'),(44,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:24'),(45,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:25'),(46,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:25'),(47,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:25'),(48,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:12:25'),(49,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:05'),(50,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:05'),(51,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:06'),(52,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:07'),(53,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:08'),(54,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:08'),(55,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:09'),(56,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:19:10'),(57,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:17'),(58,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:17'),(59,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:19'),(60,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:19'),(61,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:20'),(62,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:21'),(63,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:22'),(64,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:39:23'),(65,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:58:32'),(66,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:58:32'),(67,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:58:37'),(68,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:58:37'),(69,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:58:49'),(70,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:58:51'),(71,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:58:53'),(72,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:58:56'),(73,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:59:02'),(74,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:59:05'),(75,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:59:07'),(76,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:59:11'),(77,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:59:18'),(78,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:59:22'),(79,1,'1','creation-tts',NULL,'','audio',1,'2026-10-03 05:59:22'),(80,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 05:59:26'),(81,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:13:46'),(82,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:14:23'),(83,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:14:46'),(84,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:18:28'),(85,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:22:05'),(86,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:25:36'),(87,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:25:36'),(88,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:27:31'),(89,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:27:31'),(90,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:29:26'),(91,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:29:26'),(92,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:29:39'),(93,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:31:32'),(94,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:31:44'),(95,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 06:37:46'),(96,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:37:57'),(97,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:48:11'),(98,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:51:35'),(99,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:58:46'),(100,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 06:59:11'),(101,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:00:38'),(102,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:01:03'),(103,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:13:47'),(104,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:13:48'),(105,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:15:41'),(106,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:20:07'),(107,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-03 07:38:26'),(108,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-03 07:40:39'),(109,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 07:47:17'),(110,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-03 07:54:40'),(111,1,'1','creation-video',NULL,'agnes-video-v2.0','video',1,'2026-10-03 07:55:58'),(112,1,'1','creation-video',NULL,'agnes-video-v2.0','video',1,'2026-10-03 07:55:58'),(113,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 08:06:37'),(114,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 08:06:38'),(115,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 08:16:43'),(116,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-03 08:24:52'),(117,1,'1','creation-image',NULL,'agnes-image-2.5-flash','image',1,'2026-10-03 08:25:17'),(118,1,'1','creation-publish',NULL,'','text',1,'2026-10-03 08:47:05'),(119,1,'1','home-quote',NULL,'agnes-3.0-flash','text',1,'2026-10-07 08:57:49'),(120,1,'1','creation-script',NULL,'','text',1,'2026-10-07 09:45:38'),(121,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:48:04'),(122,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:48:04'),(123,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:48:08'),(124,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:48:08'),(125,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:49:21'),(126,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:49:41'),(127,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:49:42'),(128,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:49:45'),(129,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:50:00'),(130,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:50:03'),(131,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:51:13'),(132,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:51:19'),(133,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:51:39'),(134,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:51:42'),(135,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:53:04'),(136,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:53:24'),(137,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:53:26'),(138,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:53:28'),(139,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:53:30'),(140,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:55:10'),(141,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:55:12'),(142,1,'1','creation-tts',NULL,'','audio',1,'2026-10-07 09:55:30'),(143,1,'1','creation-image',NULL,'senseaudio-image-2.0-260319','image',1,'2026-10-07 09:55:34'),(144,1,'1','creation-video',NULL,'doubao-seedance-2-0-260128','video',1,'2026-10-07 09:56:00'),(145,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 09:56:33'),(146,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 09:56:33'),(147,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 09:58:30'),(148,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 09:58:31'),(149,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 10:00:25'),(150,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 10:00:26'),(151,1,'1','creation-video',NULL,'agnes-video-2.5-flash','video',1,'2026-10-07 10:02:20');
/*!40000 ALTER TABLE `ai_usage_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_user_memory`
--

DROP TABLE IF EXISTS `ai_user_memory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `memory_type` varchar(16) NOT NULL DEFAULT 'fact' COMMENT 'fact=事实 / trait=聚合特质',
  `mem_key` varchar(32) NOT NULL COMMENT '稳定短标识，取值见设计文档 3.2 词表',
  `mem_value` varchar(500) NOT NULL COMMENT '偏好值（自然语言短语）',
  `confidence` decimal(3,2) NOT NULL DEFAULT '0.60' COMMENT '基础置信度 0~1',
  `hit_count` int NOT NULL DEFAULT '1' COMMENT '累计命中次数（跨会话累加）',
  `session_count` int NOT NULL DEFAULT '1' COMMENT '累计命中的不同会话数',
  `last_session_id` varchar(100) NOT NULL DEFAULT '' COMMENT '最近一次命中的会话，用于会话内去重',
  `evidence` json DEFAULT NULL COMMENT '最近 5 条原话片段 [{text,at,session}]',
  `scope` varchar(64) NOT NULL DEFAULT 'global' COMMENT 'global / category:数码 等',
  `source` varchar(16) NOT NULL DEFAULT 'dialog' COMMENT 'dialog=对话提取 / behavior=工具行为',
  `first_seen` datetime NOT NULL,
  `last_seen` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=有效 0=已失效（覆盖/淘汰/超期）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_mem` (`tenant_id`,`user_id`,`memory_type`,`mem_key`,`scope`),
  KEY `idx_user_active` (`tenant_id`,`user_id`,`status`,`last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 长期记忆（每用户聚合，行数有界）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_user_memory`
--

LOCK TABLES `ai_user_memory` WRITE;
/*!40000 ALTER TABLE `ai_user_memory` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_user_memory` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_video_task`
--

DROP TABLE IF EXISTS `ai_video_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_video_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `model_id` bigint NOT NULL COMMENT '模型ID',
  `agnes_task_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agnes任务ID',
  `agnes_video_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agnes视频ID',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词',
  `gen_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text2vid' COMMENT '生成类型：text2vid, img2vid, multi2vid, keyframes',
  `input_images` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '输入图片JSON数组',
  `negative_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '反向提示词',
  `duration` int DEFAULT '5' COMMENT '视频时长（秒）',
  `width` int DEFAULT '1280' COMMENT '视频宽度',
  `height` int DEFAULT '720' COMMENT '视频高度',
  `num_frames` int DEFAULT NULL COMMENT '帧数',
  `frame_rate` int DEFAULT NULL COMMENT '帧率',
  `fps` int DEFAULT '24' COMMENT '帧率',
  `seed` bigint DEFAULT NULL COMMENT '随机种子',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
  `progress` int DEFAULT '0' COMMENT '进度百分比',
  `result_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成结果URL',
  `video_seconds` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频时长',
  `video_size` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频分辨率',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`task_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频生成任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_video_task`
--

LOCK TABLES `ai_video_task` WRITE;
/*!40000 ALTER TABLE `ai_video_task` DISABLE KEYS */;
INSERT INTO `ai_video_task` VALUES (1,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Agnes Video API error: {\"code\":\"fail_to_fetch_task\",\"message\":\"{\\\"error\\\":{\\\"message\\\":\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"Service busy (tasks: 1)\\\\\\\",\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\",\\\\\\\"type\\\\\\\":\\\\\\\"server_error\\\\\\\"}}\\\",\\\"type\\\":null,\\\"param\\\":null,\\\"code\\\":\\\"503\\\"}}\",\"data\":null}','admin','2026-06-27 11:36:33','2026-06-27 11:41:07',0),(2,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Agnes Video API error: {\"code\":\"fail_to_fetch_task\",\"message\":\"{\\\"error\\\":{\\\"message\\\":\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"Service busy (tasks: 1)\\\\\\\",\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\",\\\\\\\"type\\\\\\\":\\\\\\\"server_error\\\\\\\"}}\\\",\\\"type\\\":null,\\\"param\\\":null,\\\"code\\\":\\\"503\\\"}}\",\"data\":null}','admin','2026-06-27 11:42:26','2026-06-27 11:46:59',0),(3,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'历史任务清理（API调用未成功）','admin','2026-06-27 11:46:40','2026-06-27 13:02:06',0),(4,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Agnes Video API error: error code: 502\n','admin','2026-06-27 11:48:58','2026-06-27 11:53:01',0),(5,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,NULL,'admin','2026-06-27 12:08:57','2026-06-27 13:01:39',0),(6,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务繁忙，请稍后再试（已重试5次）','admin','2026-06-27 14:30:43','2026-06-27 14:53:11',0),(7,2,NULL,NULL,'A cat walking on the beach at sunset','text2vid',NULL,NULL,5,1152,768,81,24,24,NULL,'failed',0,NULL,NULL,NULL,'历史任务清理（服务重启前重置）','admin','2026-06-27 15:02:18','2026-06-27 15:28:45',0),(8,2,NULL,NULL,'A dog running in the park','text2vid',NULL,NULL,5,1152,768,81,24,24,NULL,'failed',0,NULL,NULL,NULL,'历史任务清理（服务重启前重置）','admin','2026-06-27 15:06:24','2026-06-27 15:28:45',0),(9,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'服务繁忙，正在重试(1/5)...','admin','2026-06-27 15:31:04','2026-06-27 19:00:31',0),(10,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,NULL,'admin','2026-06-27 16:15:28','2026-06-27 19:00:31',0),(11,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Connection reset','admin','2026-06-27 17:10:54','2026-06-27 17:10:55',0),(12,2,NULL,NULL,'一个金发碧眼美女穿着长裙在沙滩漫步','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Connection reset','admin','2026-06-27 17:11:50','2026-06-27 17:26:46',0),(13,2,NULL,NULL,'我要一个美女跳舞视频5s即可','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,NULL,'admin','2026-06-27 18:35:13','2026-06-27 19:00:31',0),(14,2,NULL,NULL,'我要一个美女跳舞视频','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'清理：等待重试时被覆盖','admin','2026-06-27 19:01:29','2026-06-27 20:27:07',0),(15,2,NULL,NULL,'电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。','text2vid',NULL,'变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿',5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'error code: 504\n','admin','2026-06-28 14:54:13','2026-06-28 14:54:29',0),(16,2,NULL,NULL,'电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。','text2vid',NULL,'变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿',5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,NULL,'admin','2026-06-28 15:18:51','2026-06-28 15:34:54',0),(17,2,NULL,NULL,'电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。','text2vid',NULL,'变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿',5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Timeout on blocking read for 120000000000 NANOSECONDS','admin','2026-06-28 15:20:46','2026-06-28 15:22:46',0),(18,2,NULL,NULL,'电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Timeout on blocking read for 120000000000 NANOSECONDS','admin','2026-06-28 15:35:41','2026-06-28 15:37:41',0),(19,2,'task_p6lvPaXIvDzrlfMaZTgLeqPBQATOQrkj','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvXzM4ZTZjYTlhMTlhM2E3ZjEyZDM5ZTBkODNhZGI3ZGY3ZDczY2U3MGEzNWI1MDlkNg==','电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/2026/06/28/video_38e6ca9a19a3a7f12d39e0d83adb7df7d73ce70a35b509d6.mp4','5.0','1088x832',NULL,'admin','2026-06-28 15:49:38','2026-06-28 15:56:49',0),(20,2,NULL,NULL,'3D卡通渲染（三渲二）风格，一位银白色长发的萝莉，穿着黑色哥特洛丽塔裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为海飞丝般的光滑高光，无锯齿。','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'{\"code\":\"content_policy_violation\",\"message\":\"Unable to generate this content. Please modify your prompt and try again.\",\"data\":null}','admin','2026-06-28 16:20:24','2026-06-28 16:20:26',0),(21,2,NULL,NULL,'3D cel-shading animation style (MMD style), long silver-white hair Lolita girl, wearing black gothic dress, waltzing elegantly on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion.','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'{\"code\":\"content_policy_violation\",\"message\":\"Unable to generate this content. Please modify your prompt and try again.\",\"data\":null}','admin','2026-06-28 16:23:16','2026-06-28 16:23:16',0),(22,2,'task_kSIfgTkZQdyDVGnTR2KipzRru6Vp4Ujc','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvXzJlN2EzYTFkNjEzMjE3OWFkNDU3ZTBlODE5OTNmNzNkYTQ3MDg4Y2E1YjVhYTgwZA==','3D动画风格，一位银白色长发的可爱少女，穿着华丽的黑色多层蕾丝裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为顺滑的亮丽高光，无锯齿。','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'in_progress',30,NULL,'5.0','1088x832',NULL,'admin','2026-06-28 16:26:52','2026-06-28 16:35:00',1),(23,2,'task_B9kFM2AM5DM44UUHznFV8IGjZeo5UMe0','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2NmNGQwZjY3MGIxNjBkZDc4YjI5NDkyNmRlMjZhYzdkNWFjZmM5YzI1NTEyNTVjNw==','3D anime style, a girl with long silver hair in a black dress, dancing waltz on a starry stage, 360 camera orbit.','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/2026/06/28/video_cf4d0f670b160dd78b294926de26ac7d5acfc9c2551255c7.mp4','5.0','1088x832',NULL,'admin','2026-06-28 16:29:47','2026-06-28 16:34:48',0),(24,2,NULL,NULL,'3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'failed',0,NULL,NULL,NULL,'Timeout on blocking read for 180000000000 NANOSECONDS','admin','2026-06-28 16:34:53','2026-06-28 16:37:53',0),(25,2,'task_Y5Abd4Vs3x9ZzEvCbaWUSxG8O4Uxqb7h','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2Y5YjBkMDAyZTRmYWM2OTA0MWJlNTVmZGNjYmRmMDI3ZDU3MmQ4OTEyMGZiMjliYQ==','3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion','text2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/2026/06/28/video_f9b0d002e4fac69041be55fdccbdf027d572d89120fb29ba.mp4','5.0','1088x832',NULL,'admin','2026-06-28 16:44:38','2026-06-28 16:51:11',0),(26,2,'task_TFMXbdnb2gikCeOhnkg4orgOfDmpjbkJ','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2MzMjkzZWQ3YjU5YWQyYzkzNTJhNTkxNjdiODhlZGMwYjA3NjI0YTkxZGE4MzE3Mg==','让图片中的任务大喊咕咕嘎嘎','img2vid',NULL,NULL,5,1152,768,241,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/2026/07/02/video_c3293ed7b59ad2c9352a59167b88edc0b07624a91da83172.mp4','10.0','1088x832',NULL,'admin','2026-07-02 14:16:22','2026-07-02 14:22:47',0),(27,2,'task_FTkCTH4QUP8ehMcqlkxxvDmzElApE4uc','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2Y4YjhjZjE2ZWY4MWVmZTJjZDA3ZDIzODYzZTZmNTM1ZTA1OTA5MDc3MDExZmZlZg==','让图片中的人物大喊咕咕嘎嘎，人物一定要保持一致','img2vid',NULL,NULL,5,1152,768,241,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/2026/07/02/video_f8b8cf16ef81efe2cd07d23863e6f535e05909077011ffef.mp4','10.0','1088x832','Connection reset','admin','2026-07-02 14:22:42','2026-07-02 15:16:41',0),(28,2,'task_m3YmzLaiD7PVTdBIhJfb6QOd0XnLTIIt','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2E5Yzc0YTNhMWFmOTRmNDdiMzRjODBjOGU2MDI0ZDc0','a dog running','text2vid',NULL,NULL,5,NULL,NULL,NULL,NULL,24,NULL,'queued',0,NULL,'5.0','1088x832',NULL,NULL,'2026-07-11 06:51:13','2026-09-02 19:47:12',0),(29,2,'task_bjl2A2PlyD4oSXoUcaJxAvRxxYaO28Re','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2ZiMjRlZGQ4MTJiODQ0OTFhOTJjNWM4ZGM1NDY5ZDg1','a dog running','text2vid',NULL,NULL,5,1280,720,NULL,NULL,24,NULL,'queued',0,NULL,'5.0','1280x704',NULL,NULL,'2026-07-11 06:56:56','2026-09-02 19:47:12',0),(30,2,'task_8fMoxD3v96ZfZYtbMKXgkZ9J7VvkC9FT','task_8fMoxD3v96ZfZYtbMKXgkZ9J7VvkC9FT','让图中角色跳性感的舞蹈','img2vid',NULL,NULL,5,1152,768,121,24,24,NULL,'completed',100,NULL,'5.0','1152x768',NULL,NULL,'2026-08-06 16:42:10','2026-09-02 19:47:12',0),(31,2,'task_XaJ5AdB53Rfo4ktvkOJ7hAfk6dR9PWkp','task_XaJ5AdB53Rfo4ktvkOJ7hAfk6dR9PWkp','让图中角色跳性感的舞蹈','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786004600710.jpg',NULL,5,1152,768,121,24,24,NULL,'completed',100,NULL,'5.0','1152x768',NULL,NULL,'2026-08-06 16:42:16','2026-09-02 19:47:12',0),(32,1,'task_bkijCSmAlbhff0L2flbBlfHjMJdpWhf6','task_bkijCSmAlbhff0L2flbBlfHjMJdpWhf6','test progress fix','text2vid',NULL,NULL,5,1280,720,NULL,NULL,24,NULL,'completed',100,NULL,NULL,'1280x720',NULL,NULL,'2026-08-06 16:38:50','2026-09-02 19:47:12',0),(33,2,'task_cO0HwZBChhMrsGwdCVewi1VbJPisWuMk','task_cO0HwZBChhMrsGwdCVewi1VbJPisWuMk','让图中角色跳性感的舞蹈','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786006928353.jpg',NULL,5,1152,768,121,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/task_cO0HwZBChhMrsGwdCVewi1VbJPisWuMk.mp4','5.0','1152x768',NULL,NULL,'2026-08-06 17:42:12','2026-09-02 19:47:12',0),(34,2,'task_WJtKWNb9k9NornikKrqE9JK4aKZYNyHC','task_WJtKWNb9k9NornikKrqE9JK4aKZYNyHC','让图中角色跳性感的舞蹈','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786009562911.jpg',NULL,5,1152,768,121,24,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/task_WJtKWNb9k9NornikKrqE9JK4aKZYNyHC.mp4','5.0','1152x768','服务暂时不可用(503), 正在重试(1/5)...',NULL,'2026-08-06 17:48:11','2026-09-02 19:47:12',0),(35,2,NULL,NULL,'一个年轻时尚的人物站在简约背景前，右手举起比出胜利的V字手势，表情自信开心，身体姿态自然放松，穿着现代休闲服装，光影柔和均匀，构图居中突出人物主体，皮肤质感细腻真实，手指细节清晰准确，不发胖变形，服装褶皱自然，整体氛围阳光积极充满活力，高细节高质量，摄影级别画质','img2vid','https://upload-bbs.miyoushe.com/upload/2026/06/30/291110358/3eea55124730ccd463bf99ba4a40645d_7897922056117889759.png?x-oss-process=image//resize,s_600/quality,q_80/auto-orient,0/interlace,1/format,png','blurry, low quality, deformed hands, extra fingers, missing fingers, bad anatomy, distorted face, watermark, text, signature, ugly, mutated, disfigured, poorly drawn, malformed limbs, extra limbs, cross-eyed, gross proportions',5,1152,768,121,24,24,3523995326,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）',NULL,'2026-08-13 10:26:36','2026-09-02 19:47:12',0),(36,2,'task_IEsQHaDTmeTSpln3QTAQLT38IQBWa2vq','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2QxMTZhOTA0NDM5ODRlOTQ4YzBkNTcyNGIwMWMxOGIz','画面中的角色开始随音乐节奏舞动，身体轻盈摆动，手臂优雅挥动，脚尖点地旋转跳跃，发丝与衣摆随动作飘动；面部表情愉悦生动，眼神专注投入，保持原有面部特征与服装风格不变；镜头采用中景跟随拍摄，角度保持平视，背景环境稳定不移动，光线柔和均匀，整体呈现流畅自然的舞蹈动作序列，氛围欢快活力。','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-01/1788267279328.jpg','blurry, low quality, flickering, jittery motion, morphing objects, distorted face, extra limbs, deformed hands, watermark, text, logo',5,1280,720,121,24,24,766696430,'queued',0,NULL,'5.0','1280x704',NULL,NULL,'2026-09-01 20:55:14','2026-09-02 19:47:12',0),(37,2,'task_8tlMkFOck2aZ3a73aWWBbODWiQJYK00V','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2ZiNDdiNzVlZmYyOTQxNmFhM2YzZjhhYTQyNzRiMzk3','图中女子身着修身晚礼服，在昏暗奢华的舞厅中央翩翩起舞；她随着音乐节奏缓缓扭动腰肢，指尖轻抚过发丝，眼神迷离自信；裙摆随旋转动作优雅翻飞，发丝在空中划出流畅弧线；灯光聚焦在她身上形成轮廓光，背景人群模糊虚化；镜头保持中景固定机位，缓慢推近捕捉她的面部表情与肢体线条，展现性感而不失优雅的舞蹈韵律。','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-02/1788349995592.jpg','distorted face, extra limbs, bad anatomy, blurry, low quality, watermark, text, logo, flickering, jittery motion, morphing objects, deformed hands, stiff movement, unnatural motion',5,1152,768,121,24,24,1421473177,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/video_fb47b75eff29416aa3f3f8aa4274b397.mp4','5.0','1088x832',NULL,NULL,'2026-09-02 19:53:46','2026-09-02 19:55:32',0),(38,2,'task_Orddz2U9ghrhOVVRM3B8qtztXB4zJMn4','video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvX2QwZTdmZjY5NDc3ODRmMjc5ZGNiYTVmNDQ1NGQ4Nzhi','一只毛茸茸的小猫站在金色的沙滩上，面朝大海，夕阳的暖光洒在它柔软的毛发上，泛起柔和的光晕；轻盈的海浪在远处缓缓拍打着岸边，溅起细碎的白色泡沫，海风轻拂，小猫的胡须微微颤动，尾巴轻轻摆动；天空呈现粉橙渐变的晚霞色调，几只海鸥在远处低飞掠过海面；画面采用全景构图，前景是小猫的特写，背景是辽阔的海平面与渐变的天空，整体氛围温暖治愈、宁静浪漫；电影感光影，自然光线柔焦，色彩饱和而温馨，细腻写实风格，8K高清画质。','text2vid',NULL,'blurry, low quality, deformed, watermark, text, signature, bad anatomy, extra limbs, distorted face, ugly, grainy, oversaturated, unnatural colors',5,1280,720,NULL,NULL,24,20792556,'queued',0,NULL,'5.0','1280x704',NULL,NULL,'2026-09-10 07:08:52','2026-09-10 07:08:53',0),(39,37,NULL,'ce09e309-3387-4ead-b18b-f217c88cff0e','一只橘白相间的可爱小猫在金色沙滩上漫步，身后是碧蓝的大海和轻柔拍打岸边的浪花；夕阳的暖光洒在小猫蓬松柔软的毛发上，勾勒出迷人的轮廓光；镜头采用低角度平视拍摄，营造亲密感，小猫时不时停下脚步好奇地望向远方海平面，尾巴轻轻摇摆；画面氛围温暖治愈，充满夏日海边的悠闲与宁静，细节丰富，毛发质感细腻可见，沙滩上的小贝壳和湿润的反光清晰呈现','text2vid',NULL,'blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs',5,1280,720,81,24,24,3850380927,'completed',100,'https://dynamic.senseaudio.cn/video/36f19820-4ac9-405b-920f-0fec21665913',NULL,NULL,NULL,NULL,'2026-09-10 07:09:43','2026-09-10 07:32:02',0),(40,37,NULL,NULL,'图中人物开始跳起充满活力的热舞，身体随着节奏律动，动作流畅有力；手臂舒展挥舞，配合步伐变换做出经典的街舞旋转与定格姿势；发丝随动作飞扬，衣摆自然飘动；面部表情自信洋溢，眼神有神；背景为动感舞台灯光，霓虹色彩交替闪烁，丁达尔光效穿透烟雾；镜头保持中景全景为主，配合轻微推拉增强节奏感；整体氛围热烈奔放，高饱和度色彩，电影感动态视觉效果。','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, deformed hands, inconsistent style, bad anatomy',5,1280,720,121,24,24,3674765559,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"图片格式不支持\",\"message\":\"图片格式不支持\",\"ref_code\":400000}',NULL,'2026-09-10 07:31:15','2026-09-10 07:31:17',0),(41,37,NULL,NULL,'一只毛茸茸的橘色小猫咪在阳光充足的客厅里活动，它先是慵懒地伸了个大大的懒腰，前爪向前伸展，背部拱起，然后轻巧地跳下窗台；接着发现一只蝴蝶飞过，立刻进入捕猎模式，四肢交替快速移动追逐蝴蝶，尾巴高高竖起保持平衡；镜头从平视角度跟随猫咪的活泼动作，背景是温暖的室内环境，柔和的午后阳光透过窗户洒入，在地面形成斑驳光影，营造出温馨治愈的氛围，4K超清画质，写实风格。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, distorted face, deformed paws, extra limbs, watermark, text, logo',5,1280,720,81,24,24,3653071434,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:38:43','2026-09-10 07:38:45',0),(42,37,NULL,NULL,'一只毛茸茸的橘色小猫趴在柔软的白色地毯上，阳光透过窗纱洒下温暖的光斑；小猫慵懒地打了个哈欠，伸了个懒腰，前爪向前伸展，胡须微微颤动；它转过头好奇地看向镜头，圆溜溜的大眼睛闪烁着光芒；背景是温馨的客厅一角，绿植在微风中轻轻摇曳，光影柔和细腻，电影感写实风格，浅景深突出主体，画面充满治愈氛围。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, deformed, bad anatomy',5,1280,720,81,24,24,750489501,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:41:27','2026-09-10 07:41:29',0),(43,37,NULL,NULL,'一只毛茸茸的橘白相间小奶猫在阳光洒落的木地板上慵懒地打滚，阳光透过白色纱帘形成柔和的丁达尔光柱，猫咪眯着眼睛发出呼噜声，尾巴轻轻摇摆；它忽然被一片飘落的树叶吸引，竖起耳朵好奇地凑近嗅闻，然后伸出粉嫩的小爪子试图抓住树叶，动作笨拙又可爱；镜头采用平视近景拍摄，背景是温馨的日式房间，有绿植和藤编猫窝，整体氛围治愈温暖，高清晰度，自然光柔和，胶片质感','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, deformed, watermark, text, signature, bad anatomy, distorted face, extra limbs, jittery motion, morphing objects, sudden scene change',5,1280,720,81,24,24,1186566131,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:42:10','2026-09-10 07:42:12',0),(44,37,NULL,NULL,'镜头从侧面近景拍摄一只毛茸茸的小猫，清晨阳光透过窗户洒在它柔软的皮毛上；小猫先是慵懒地伸了个长长的懒腰，前爪向前伸展，背部拱起，尾巴轻轻摆动；然后它低下头用舌头舔舐前爪梳理毛发，耳朵随着声音微微转动；接着它注意到光斑中的尘埃，好奇地抬起爪子轻轻拍打；最后它轻巧地站起身，迈开步子走向镜头，尾巴高高竖起，步伐轻盈可爱；暖色调自然光，浅景深虚化背景，电影感写实风格。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, deformed paws, bad anatomy, inconsistent fur texture',5,1280,720,121,24,24,1734862282,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:43:52','2026-09-10 07:43:54',0),(45,37,NULL,NULL,'一只毛茸茸的橘白相间小猫在阳光明媚的客厅里活泼地玩耍；小猫先是趴在柔软的毛毯上伸了一个长长的懒腰，前爪向前伸展，身体弓起，粉嫩的肉垫清晰可见；随后它敏锐地注意到地面上飘落的阳光光斑，耳朵微微转动，尾巴高高翘起轻轻摇晃；突然它后腿发力跃起，在空中做出灵活的扑抓动作，毛皮在阳光下泛着温暖的光泽；落地后它轻巧地转身，歪着头看向镜头，眼睛清澈明亮，嘴巴微微张开发出无声的喵叫；背景是温馨的居家环境，窗帘随风轻摆，阳光透过玻璃形成柔和的光晕，整体画面温暖治愈，电影感写实风格。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, bad anatomy, deformed, pixelated',5,1280,720,121,24,24,662766897,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:45:08','2026-09-10 07:45:10',0),(46,37,NULL,NULL,'一只毛茸茸的小橘猫蜷缩在阳光明媚的窗台上，午后暖阳透过白色纱帘洒下柔和的丁达尔光线，小猫半眯着眼睛打盹，胡须微微颤动，爪子偶尔轻轻抓挠垫子，阳光在它蓬松的毛发上泛起金色的光晕；镜头采用中景俯拍，浅景深虚化背景花园绿植，画面充满治愈温暖的氛围，电影感写实风格，8K超高清画质。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, deformed, watermark, text, signature, bad anatomy, distorted face, extra limbs, harsh shadows, cluttered background',5,1280,720,121,24,24,1239548817,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:46:53','2026-09-10 07:46:54',0),(47,37,NULL,NULL,'一只毛茸茸的小猫蜷缩在温暖的阳光窗台上，金色的阳光洒在它柔软的毛发上，形成漂亮的光晕效果。小猫好奇地抬起头，眼睛明亮清澈，瞳孔微微收缩，鼻子轻轻嗅着空气中的气息，尾巴悠闲地左右摆动。背景是柔和虚化的室内环境，透出温馨舒适的氛围。镜头缓慢推进，从全景逐渐转为面部特写，捕捉小猫每一个细微的表情变化。画面色调温暖，采用自然光照明，呈现电影感的写实风格，色彩柔和自然，细节清晰可见。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, bad anatomy, deformed features, overexposed, underexposed',5,1280,720,121,24,24,780298442,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:48:10','2026-09-10 07:48:11',0),(48,37,NULL,NULL,'一只毛茸茸的橘色小猫咪蜷缩在柔软的米色针织毯子上，阳光透过白色纱帘洒在地板上形成斑驳的光斑。猫咪的眼睛微微眯起，呈现出明亮的琥珀色，瞳孔在柔和的光线下收缩成细长的竖线。它的一只前爪轻轻抬起，像是在伸懒腰，粉嫩的肉垫清晰可见。背景是温馨的客厅角落，旁边放着一个精致的陶瓷食盆和几根散落的逗猫棒。温暖的午后光线从侧面照射，营造出丁达尔效应的光束，整体色调偏向暖黄色和奶油色系，画面充满宁静治愈的氛围，细节丰富，毛发丝丝分明。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, distorted face, extra limbs, bad anatomy, watermark, text, deformed, oversaturated, harsh shadows, cluttered background',10,1280,720,121,24,24,1464103804,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:48:51','2026-09-10 07:48:53',0),(49,37,NULL,NULL,'一只毛茸茸的小猫趴在柔软的窗台上，阳光透过纱帘洒在它柔软的皮毛上，泛着温暖的金光。它的眼睛睁得圆圆的，透着好奇与灵动，微微晃动的小尾巴偶尔扫过木台边缘。微风轻拂，几缕毛发随光线轻轻飘动，空气中漂浮着细小的尘埃粒子。小猫轻轻打了个哈欠，露出粉嫩的小舌头，耳朵微微抖动，整个画面洋溢着温馨、治愈的氛围。浅景深虚化背景，突出小猫的细腻质感，自然光影柔和过渡，温暖治愈的色调。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, distorted face, extra limbs, deformed, watermark, text, logo, harsh shadows, overexposed, underexposed, flickering, jittery motion',10,1280,720,121,24,24,2271554937,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:52:03','2026-09-10 07:52:05',0),(50,37,NULL,NULL,'一只毛茸茸的橘白相间小奶猫在阳光洒落的木地板上慵懒伸懒腰；镜头缓慢推进至中景，小猫先是用前爪向后伸展身体，尾巴轻轻摇摆，随后低头舔舐爪子整理毛发，耳朵随着周围环境细微转动；午后暖金色的丁达尔光线透过半开的窗帘斜射入室内，空气中漂浮着细小的尘埃粒子，背景是温馨的家居环境，绿植盆栽在柔和光晕中若隐若现；整体画面色调温暖柔和，充满治愈感与生活气息，电影感写实风格，细腻呈现猫咪绒毛的质感与灵动神态。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, distorted face, extra limbs, watermark, text, logo, harsh shadows, cluttered background',10,1280,720,121,24,24,1054825185,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:52:56','2026-09-10 07:52:58',0),(51,37,NULL,NULL,'一只圆眼幼猫在阳光斑驳的林间草地上轻盈行走，琥珀色瞳孔微微收缩，毛茸茸的尾巴高高竖起并随步伐小幅度摆动；前爪交替抬起落下时肉垫柔软地按压草地，耳尖偶尔抖动捕捉虫鸣；微风拂过颈侧绒毛泛起波浪状起伏，阳光穿透树冠形成丁达尔光柱洒落全身，毛发边缘泛着金棕色轮廓光；镜头采用低角度平视跟拍，焦点始终锁定猫咪面部与身体，背景植被柔和虚化；画面干净无杂物，整体呈现温馨治愈的日常氛围','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, morphing objects, sudden scene change, watermark, text, logo, distorted face, extra limbs, deformed character',10,1280,720,81,24,24,3062295219,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:53:29','2026-09-10 07:53:31',0),(52,37,NULL,NULL,'一只可爱的橘色小猫在阳光充足的客厅地板上慢慢苏醒，它伸了个懒腰，前爪向前伸展，身体弓起；随后轻盈地站起身，尾巴高高竖起微微摆动，好奇地用爪子拨弄地上的一颗玻璃弹珠；镜头从低角度特写它毛茸茸的脸庞，眼神清澈灵动，耳朵时不时抖动；午后柔和的自然光透过窗帘洒在它的绒毛上，形成温暖的光晕；背景是温馨的家居环境，浅木色地板，虚化的沙发与绿植；画面节奏舒缓，充满治愈感，写实摄影风格，8K高清画质。','text2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg','blurry, low quality, flickering, jittery motion, distorted face, deformed paws, extra limbs, watermark, text, logo, bad anatomy, unnatural movement',10,1280,720,NULL,NULL,24,4231573962,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: 商汤调用失败(POST /v1/video/create status=400): {\"code\":\"invalid\"}',NULL,'2026-09-10 07:55:57','2026-09-10 07:55:59',0),(53,37,NULL,NULL,'保持原图中人物的面部特征与服装细节不变；人物随着音乐节奏自然摆动身体，手臂优雅挥舞，脚步轻盈踏动，头发随动作微微飘动；背景保持静止稳定；镜头固定在中景角度，平稳记录完整舞蹈动作；光影柔和均匀，氛围活泼欢快，画面清晰细腻，电影级画质，高饱和度色彩，流畅自然的动画过渡效果。','img2vid','https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788999088304.jpg','blurry, low quality, distorted face, deformed hands, extra limbs, flickering, jittery motion, morphing objects, watermark, text, bad anatomy, inconsistent background',5,1280,720,121,24,24,128425436,'failed',0,NULL,NULL,NULL,'senseaudio 提交失败: name \'asyncio\' is not defined',NULL,'2026-09-10 08:11:54','2026-09-10 08:11:55',0),(54,46,'task_y0AkVBD1CxPprXXBaBmPYGYG4SPo4j8R','task_y0AkVBD1CxPprXXBaBmPYGYG4SPo4j8R','雪山上日出，云海翻涌，航拍缓慢推进','text2vid',NULL,NULL,5,1280,704,NULL,NULL,24,783406179,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_y0AkVBD1CxPprXXBaBmPYGYG4SPo4j8R.mp4','5','720P',NULL,NULL,'2026-09-17 21:30:01','2026-09-17 21:35:32',0),(55,46,NULL,NULL,'人物从首帧姿态自然转身走向窗边，镜头缓慢推进','keyframes','[\"https://picsum.photos/seed/kf1/1280/704\",\"https://picsum.photos/seed/kf2/1280/704\"]',NULL,5,1280,704,NULL,NULL,24,282985995,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）',NULL,'2026-09-17 21:35:40','2026-09-17 21:37:37',0),(56,46,'task_F0xaB98Y5UwugF44U2Ad5NSB9hz0mdqQ','task_F0xaB98Y5UwugF44U2Ad5NSB9hz0mdqQ','画面从首帧平滑过渡到尾帧，镜头缓慢推进','keyframes','[\"https://picsum.photos/seed/kf3/1280/704\",\"https://picsum.photos/seed/kf4/1280/704\"]',NULL,4,1280,704,NULL,NULL,24,3330506997,'in_progress',10,NULL,'4','720P',NULL,NULL,'2026-09-17 21:39:10','2026-09-17 21:42:17',0),(57,46,NULL,NULL,'a melancholic young man sitting on a dimly lit park bench, hunched shoulders, staring blankly into the distance, holding a cold coffee cup, rain dripping from his clothes, gray desaturated color palette, heavy shadows, overcast sky, urban decay in the background, expression of deep boredom and emptiness, cinematic lighting, 4k, highly detailed, realistic skin texture, lonely atmosphere','text2vid',NULL,'smiling, happy, bright colors, high energy, vibrant, clean, perfect lighting, cheerful, upbeat, cartoonish, anime, low quality, blurry, deformed, watermark, text, signature',5,960,720,NULL,NULL,24,4219105676,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）',NULL,'2026-09-23 20:34:01','2026-09-23 20:35:46',0),(58,46,NULL,NULL,'夜晚昏暗的卧室，人物猛然抬头直视镜头，眼神中充满惊恐与紧迫感；手中举着的手机屏幕发出剧烈闪烁的冷白光，光影在面部快速交替，制造出动态的明暗变化；镜头以极快的速度向前推近，从近景瞬间压缩至面部大特写，焦点牢牢锁定在人物瞪大的双眼和紧锁的眉宇之间；背景完全虚化为深黑色，仅保留屏幕光在头发和皮肤边缘形成的微弱轮廓光；整体画面色调阴冷压抑，高对比度光影，电影级惊悚氛围，2.35:1宽银幕画幅，4K超高清质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_a9BgGpscTC8I64O9HrN5yE0D1sJlZDWK/output_ca3cbc84d7cf4afdbf2bf261f40355c4.png\"]',NULL,7,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:25:38','2026-10-03 06:27:27',0),(59,46,NULL,NULL,'高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:25:38','2026-10-03 06:27:27',0),(60,46,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:27:33','2026-10-03 06:29:22',0),(61,46,NULL,NULL,'特写镜头聚焦于手指在机械键盘上快速敲击的特写画面，指尖精准触动键帽，键盘轴体发出轻微反馈；屏幕散发出冷调蓝紫色光晕，随着敲击节奏产生高频脉动与呼吸感，光影柔和地映在皮肤与金属边框上；镜头以缓慢平稳的速度围绕手部进行360度环绕移动，捕捉不同角度下的手指动态与屏幕反光变化；背景为深暗的科技感工作室，景深极浅，背景虚化柔和，突出前景的手部动作与键盘细节；画面具有电影级质感，色彩对比度高，细节锐利，营造出专注且充满科技氛围的视觉效果。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_3sk0bFG2RB4A17RcM7fPz0qy2sg9mEGZ/output_3e23a3dfe7874aa5a52876e10d183db8.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:27:33','2026-10-03 06:29:22',0),(62,46,'task_YehYbmpFmKbW4seXCWRonrHbY3G2td8q','task_YehYbmpFmKbW4seXCWRonrHbY3G2td8q','0-2秒：深邃的暗色背景中，三个半透明的悬浮全息屏幕依次从中心向外亮起，屏幕边缘闪烁着幽蓝色的科技光晕，屏幕内显示着动态的数据流与抽象的几何图形，伴随轻微的机械旋转展示内部内容；2-5秒：成千上万颗微小的金色光粒子在屏幕之间穿梭流动，形成灵动的粒子轨迹，光影交织出梦幻般的立体空间感，粒子碰撞时产生柔和的光爆效果；6-10秒：镜头保持水平缓慢横移，模拟视线扫过屏幕群的流畅视角，景深逐渐拉远展现屏幕阵列的全貌，整体氛围充满未来感与静谧的高级科技感，画面清晰锐利，色彩以深蓝与亮金为主。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_sKkqAaXlNHXJGQ9O13crQToGh0W7OSeH/output_5cec61476e9e40648d74b2de7e771457.png\"]',NULL,7,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_YehYbmpFmKbW4seXCWRonrHbY3G2td8q.mp4','7','720P','服务暂时不可用(429), 正在重试(3/5)...','creation','2026-10-03 06:29:28','2026-10-03 06:37:42',0),(63,46,NULL,NULL,'动态视觉序列，多张半透明幻灯片页面如同扑克牌般以高速度快速连续展开，层层叠叠铺满整个屏幕，边缘锐利，光影交错；画面底部，深色的客户剪影依次淡入出现，整齐排成横向队列，保持静止姿态，身份清晰；镜头从近景特写缓缓拉远至大远景，展现整体构图，背景为深蓝色科技风格，柔和顶光，高清晰度，电影感色调，流畅连贯的动画过渡。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_P7jiopbpfMmWr0Mk82NkqQ25e4rG9B44/output_d689e14192e54de7b46b2ce99d64f47d.png\"]',NULL,11,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:29:41','2026-10-03 06:31:28',0),(64,46,'task_5pzcfweiZ4xHQuuP2EAg3mpLJ6vDnbsC','task_5pzcfweiZ4xHQuuP2EAg3mpLJ6vDnbsC','中近景视角，一位时尚年轻博主正对着镜头进行充满活力的口型讲述，面部表情生动夸张，眼神直视前方，配合言语节奏做出富有感染力的手部手势，如比心或强调性挥动。背景为简约温馨的室内直播间，暖色调LED灯带营造柔和氛围，光影立体。随着讲述高潮，手机屏幕界面中红色的点赞爱心图标密集生成并不断向上漂浮，粒子特效梦幻且明亮，与人物动态形成虚实互动。镜头采用轻微手持晃动模拟真实直播质感，焦点始终锁定在人物面部与上半身，背景微虚化，色彩饱和度高，整体呈现高清、活泼、社交媒体的视觉风格。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_dQ3EkErXrRp0WFGWaBfgJaVfHj9yI9Sc/output_c93c6ce8c3ce458ca13fdf3f8bf20f9e.png\"]',NULL,10,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_5pzcfweiZ4xHQuuP2EAg3mpLJ6vDnbsC.mp4','10','720P','服务暂时不可用(503), 正在重试(1/5)...','creation','2026-10-03 06:31:46','2026-10-03 06:39:01',0),(65,46,'task_kwGsUqyz3stpxhbuYd8wgIDsHHezYbiZ','task_kwGsUqyz3stpxhbuYd8wgIDsHHezYbiZ','未来科技感场景，人物站在画面中央，指尖指向镜头下方，眼神坚定，表情自信；背景由多层全息电路板与发光线路构成，光效呈冷蓝色，数据流缓缓流动，节点周期性闪烁，营造稳定、秩序、数字化的氛围；机位保持正面平视，从全景缓慢推近至半身中景，人物头部与肩部始终居中，焦点锁定面部与手势，背景虚化但光效保持清晰；冷色调霓虹光影，轻微体积光与丁达尔效应，电影级科幻质感，节奏平稳，一镜到底。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_teRKhbfRsAHIwbt9HD37KgvYhBmYeuDu/output_1b37d05fb11e4aa1814a5360e5434f0a.png\"]',NULL,11,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_kwGsUqyz3stpxhbuYd8wgIDsHHezYbiZ.mp4','11','720P','服务暂时不可用(503), 正在重试(4/5)...','creation','2026-10-03 06:37:59','2026-10-03 06:47:10',0),(66,46,NULL,NULL,'夜晚昏暗的卧室，人物猛然抬头直视镜头，眼神中充满惊恐与紧迫感；手中举着的手机屏幕发出剧烈闪烁的冷白光，光影在面部快速交替，制造出动态的明暗变化；镜头以极快的速度向前推近，从近景瞬间压缩至面部大特写，焦点牢牢锁定在人物瞪大的双眼和紧锁的眉宇之间；背景完全虚化为深黑色，仅保留屏幕光在头发和皮肤边缘形成的微弱轮廓光；整体画面色调阴冷压抑，高对比度光影，电影级惊悚氛围，2.35:1宽银幕画幅，4K超高清质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_a9BgGpscTC8I64O9HrN5yE0D1sJlZDWK/output_ca3cbc84d7cf4afdbf2bf261f40355c4.png\"]',NULL,7,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:48:12','2026-10-03 06:49:59',0),(67,46,'task_EnIs2hw3f66Z19odXMDjiugqm6c6Mmyw','task_EnIs2hw3f66Z19odXMDjiugqm6c6Mmyw','夜晚昏暗的卧室，人物猛然抬头直视镜头，眼神中充满惊恐与紧迫感；手中举着的手机屏幕发出剧烈闪烁的冷白光，光影在面部快速交替，制造出动态的明暗变化；镜头以极快的速度向前推近，从近景瞬间压缩至面部大特写，焦点牢牢锁定在人物瞪大的双眼和紧锁的眉宇之间；背景完全虚化为深黑色，仅保留屏幕光在头发和皮肤边缘形成的微弱轮廓光；整体画面色调阴冷压抑，高对比度光影，电影级惊悚氛围，2.35:1宽银幕画幅，4K超高清质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_a9BgGpscTC8I64O9HrN5yE0D1sJlZDWK/output_ca3cbc84d7cf4afdbf2bf261f40355c4.png\"]',NULL,7,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_EnIs2hw3f66Z19odXMDjiugqm6c6Mmyw.mp4','7','720P','服务暂时不可用(429), 正在重试(3/5)...','creation','2026-10-03 06:51:37','2026-10-03 06:59:07',0),(68,46,NULL,NULL,'高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:58:47','2026-10-03 07:00:34',0),(69,46,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 06:59:12','2026-10-03 07:01:00',0),(70,46,NULL,NULL,'特写镜头聚焦于手指在机械键盘上快速敲击的特写画面，指尖精准触动键帽，键盘轴体发出轻微反馈；屏幕散发出冷调蓝紫色光晕，随着敲击节奏产生高频脉动与呼吸感，光影柔和地映在皮肤与金属边框上；镜头以缓慢平稳的速度围绕手部进行360度环绕移动，捕捉不同角度下的手指动态与屏幕反光变化；背景为深暗的科技感工作室，景深极浅，背景虚化柔和，突出前景的手部动作与键盘细节；画面具有电影级质感，色彩对比度高，细节锐利，营造出专注且充满科技氛围的视觉效果。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_3sk0bFG2RB4A17RcM7fPz0qy2sg9mEGZ/output_3e23a3dfe7874aa5a52876e10d183db8.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 07:00:40','2026-10-03 07:02:26',0),(71,46,NULL,NULL,'动态视觉序列，多张半透明幻灯片页面如同扑克牌般以高速度快速连续展开，层层叠叠铺满整个屏幕，边缘锐利，光影交错；画面底部，深色的客户剪影依次淡入出现，整齐排成横向队列，保持静止姿态，身份清晰；镜头从近景特写缓缓拉远至大远景，展现整体构图，背景为深蓝色科技风格，柔和顶光，高清晰度，电影感色调，流畅连贯的动画过渡。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_P7jiopbpfMmWr0Mk82NkqQ25e4rG9B44/output_d689e14192e54de7b46b2ce99d64f47d.png\"]',NULL,11,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 07:01:05','2026-10-03 07:02:52',0),(72,46,'task_CdaTCmtzJosiBPcWe0kgrXgSACuE5pLR','task_CdaTCmtzJosiBPcWe0kgrXgSACuE5pLR','高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_CdaTCmtzJosiBPcWe0kgrXgSACuE5pLR.mp4','4','720P',NULL,'creation','2026-10-03 07:13:49','2026-10-03 07:20:03',0),(73,46,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 07:13:50','2026-10-03 07:15:37',0),(74,46,'task_VtXURFFGatF8l6uYZJcgXo0quSrah2e7','task_VtXURFFGatF8l6uYZJcgXo0quSrah2e7','特写镜头聚焦于手指在机械键盘上快速敲击的特写画面，指尖精准触动键帽，键盘轴体发出轻微反馈；屏幕散发出冷调蓝紫色光晕，随着敲击节奏产生高频脉动与呼吸感，光影柔和地映在皮肤与金属边框上；镜头以缓慢平稳的速度围绕手部进行360度环绕移动，捕捉不同角度下的手指动态与屏幕反光变化；背景为深暗的科技感工作室，景深极浅，背景虚化柔和，突出前景的手部动作与键盘细节；画面具有电影级质感，色彩对比度高，细节锐利，营造出专注且充满科技氛围的视觉效果。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_3sk0bFG2RB4A17RcM7fPz0qy2sg9mEGZ/output_3e23a3dfe7874aa5a52876e10d183db8.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_VtXURFFGatF8l6uYZJcgXo0quSrah2e7.mp4','5','720P','服务暂时不可用(503), 正在重试(3/5)...','creation','2026-10-03 07:15:43','2026-10-03 07:22:01',0),(75,46,'task_LxM8QWQbyYERAS7PNl5DPkI5KusFnJ5B','task_LxM8QWQbyYERAS7PNl5DPkI5KusFnJ5B','动态视觉序列，多张半透明幻灯片页面如同扑克牌般以高速度快速连续展开，层层叠叠铺满整个屏幕，边缘锐利，光影交错；画面底部，深色的客户剪影依次淡入出现，整齐排成横向队列，保持静止姿态，身份清晰；镜头从近景特写缓缓拉远至大远景，展现整体构图，背景为深蓝色科技风格，柔和顶光，高清晰度，电影感色调，流畅连贯的动画过渡。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_P7jiopbpfMmWr0Mk82NkqQ25e4rG9B44/output_d689e14192e54de7b46b2ce99d64f47d.png\"]',NULL,11,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_LxM8QWQbyYERAS7PNl5DPkI5KusFnJ5B.mp4','11','720P',NULL,'creation','2026-10-03 07:20:09','2026-10-03 07:27:20',0),(76,37,NULL,'a7fcb31a-e35b-43e3-8355-0c21c0bf31c4','低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-03 07:38:27','2026-10-03 07:38:45',0),(77,37,NULL,'52656448-406f-4c49-82a8-9f24997f09a6','低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-03 07:40:41','2026-10-03 07:40:56',0),(78,46,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 07:47:19','2026-10-03 07:49:07',0),(79,37,NULL,'87fc026c-0036-446d-b44d-440ec00d848c','高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-03 07:54:42','2026-10-03 07:54:57',0),(80,2,NULL,NULL,'高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'Agnes API returned status 400: {\"code\":\"fail_to_fetch_task\",\"message\":\"{\\\"error\\\":{\\\"message\\\":\\\"litellm.BadRequestError: OpenAIException - {\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"Input should be \'ti2vid\', \'keyframes\' or \'multi_referen','creation','2026-10-03 07:56:00','2026-10-03 07:56:02',0),(81,2,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'Agnes API returned status 400: {\"code\":\"fail_to_fetch_task\",\"message\":\"{\\\"error\\\":{\\\"message\\\":\\\"litellm.BadRequestError: OpenAIException - {\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"Input should be \'ti2vid\', \'keyframes\' or \'multi_referen','creation','2026-10-03 07:56:00','2026-10-03 07:56:06',0),(82,46,'task_fwMEflQIwUgJiwyAcQrQr4AV2VQcQnI6','task_fwMEflQIwUgJiwyAcQrQr4AV2VQcQnI6','高对比度红色叉号元素以极具冲击力的动画形式从画面上方重重砸下，瞬间覆盖整个画面，形成强烈的视觉遮蔽效果；程序员角色保持动作定格状态，身体僵硬，面部表情凝固，展现程序崩溃或错误的瞬间；镜头伴随冲击产生轻微且急促的震动，画面边缘伴随红色光晕闪烁与粒子飞溅，整体色调以冷峻的科技灰背景衬托刺眼的警示红，营造紧张、故障与错误的赛博朋克氛围，8K高清晰度，电影级光影质感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_XHC9VaG3Aim852cexMUIo8Tyx6kRd138/output_c14be8ec34c24a00aeeccd1a2371ce59.png\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_fwMEflQIwUgJiwyAcQrQr4AV2VQcQnI6.mp4','4','720P','服务暂时不可用(429), 正在重试(3/5)...','creation','2026-10-03 08:06:39','2026-10-03 08:12:56',0),(83,46,NULL,NULL,'低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-03 08:06:39','2026-10-03 08:08:28',0),(84,46,'task_rLBwA2mZUxKJJgsxpPoeYVp1LH8K4riJ','task_rLBwA2mZUxKJJgsxpPoeYVp1LH8K4riJ','低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_rLBwA2mZUxKJJgsxpPoeYVp1LH8K4riJ.mp4','5','720P','服务暂时不可用(503), 正在重试(1/5)...','creation','2026-10-03 08:16:45','2026-10-03 08:22:51',0),(85,46,'task_CMqxsbvQOio3CKlcVG0nGHLP3y38fJX4','task_CMqxsbvQOio3CKlcVG0nGHLP3y38fJX4','低角度仰拍镜头缓缓上摇，画面从人物脚下逐渐升至面部特写，背景是繁华都市夜景，霓虹灯光与车流光轨在虚化背景中闪烁流动，色彩以冷蓝与暖橙对比为主；人物衣角及发丝被微风吹动，呈现自然飘动感，面部表情沉静且专注，眼神望向远方；光影采用侧逆光勾勒轮廓，体积光穿透城市薄雾，营造电影级质感；构图遵循三分法，焦点始终锁定在人物身上，背景适度虚化以突出主体，整体氛围孤独而充满希望，风格为高清晰度写实主义，色调偏电影胶片感。','img2vid','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_HRR1JYmCpqYUorDVoZf6zoNb0Dbx3zOi/output_3fda7f3b77164865861c69b4d69c1f0e.png\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'completed',100,'https://platform-outputs.agnes-ai.space/videos/agnes-video-2.5/task_CMqxsbvQOio3CKlcVG0nGHLP3y38fJX4.mp4','5','720P','服务暂时不可用(429), 正在重试(3/5)...','creation','2026-10-03 08:24:54','2026-10-03 08:32:45',0),(86,37,NULL,'7a302c5a-18eb-432a-8459-9dd826151a1d','中近景镜头捕捉演讲者面部，他神情严肃，坚定有力地摇头并挥动手臂，动作果断且富有张力；背景光影缓慢流动，色彩由暗转亮再回落，营造紧张转折氛围；镜头起始有轻微手持晃动感，随后迅速稳定，焦点始终锁定在演讲者面部；环境为室内演讲舞台，深蓝色调，侧逆光突出轮廓，画面电影感强，高清晰度，情绪凝重。','img2vid','[\"https://dynamic.senseaudio.cn/image/4cdcb95b-7343-4331-87c6-18db27d1a16e\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:49:22','2026-10-07 09:49:37',0),(87,37,NULL,'18e97a8b-8213-4a2c-868d-53d166c0f92c','写实主义风格，4K 高清电影质感。镜头缓缓推近一名白领的面部特写，他面色凝重，眼神中流露不安，视线紧张地瞥向左侧；旁边同事手指在键盘上快速敲击，机械键盘的轻微振动与屏幕发出的冷白光微微闪烁，映照在两人面部；背景虚化，隐约可见模糊的人影在办公区走动。采用浅景深，侧光照明，冷暖色调对比，营造出压抑且充满悬疑感的职场氛围，画面稳定，细节丰富，光影自然过渡。','img2vid','[\"https://dynamic.senseaudio.cn/image/0a112adb-c304-4686-8c1b-a63ed825401a\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:49:41','2026-10-07 09:49:55',0),(88,37,NULL,'b3cb4bd7-39d7-4478-b6be-799434ae114a','镜头从空桌缓慢横移到远处忙碌的员工，纸箱上灰尘微粒在光束中飘浮，画面安静肃穆。','img2vid','[\"https://dynamic.senseaudio.cn/image/d30b77d4-fc02-4c7f-9693-d3794951d544\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'completed',100,'https://dynamic.senseaudio.cn/video/5ac9ed6f-417b-4271-91c4-6c6e341f5f05',NULL,NULL,NULL,'creation','2026-10-07 09:51:13','2026-10-07 09:53:23',0),(89,37,NULL,'5b517df4-1c48-4cb2-bc5e-5ee006ffba94','深夜办公室，冷色调蓝光从电脑屏幕投射在设计师疲惫的脸上，特写镜头捕捉他/她痛苦地揉捏眼珠，随后缓慢伸懒腰，手臂舒展，动作充满疲惫感；屏幕上UI设计稿反复闪烁修改，光标快速跳动，反映焦虑状态；镜头缓慢向后拉远并略微升高，逐渐揭示桌面杂乱无章的现实，堆满揉皱的草图纸张、废弃的马克笔、咖啡杯和凌乱线缆，背景昏暗，强调孤立与高压氛围，写实主义风格，电影级景深。','img2vid','[\"https://dynamic.senseaudio.cn/image/cb96eaee-15a6-4ca6-a482-11b5a1e34c25\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:51:20','2026-10-07 09:51:34',0),(90,37,NULL,'7bdc4f79-8311-40a0-bcf6-0191b2bec82e','中近景镜头捕捉一位年轻设计师在明亮的工作台前，面前多台显示器上UI设计稿与3D模型快速刷新、滚动生成，屏幕光影映照在设计师专注的面庞上；设计师嘴角微微上扬，自信地点头微笑，眼神中透着满足与肯定；随着情绪推进，背景环境光线逐渐由柔和过渡至明亮通透，自然光与屏幕光交织，营造出高效、现代的科技感氛围；镜头缓慢轻微上扬，模拟仰视视角，强化空间感与积极向上的视觉张力，整体色调清新明亮，细节锐利，电影级画质。','img2vid','[\"https://dynamic.senseaudio.cn/image/79e5dd90-2c43-4976-9eea-54029b2f6245\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:53:04','2026-10-07 09:53:18',0),(91,37,NULL,'e48ff653-50c6-4ae0-99c0-5569faa3d36f','现代简约明亮的办公室内，身穿深色西装的老板与穿着休闲职业装的设计师面对面站立，两人动作自然流畅地紧握双手，随后老板有力地竖起大拇指，设计师面部肌肉放松，露出惊喜且真诚的灿烂笑容。背景是落地窗，午后黄金时段的阳光透过玻璃洒入室内，形成清晰的丁达尔光束，照亮空气中的微尘。镜头以人物为圆心进行缓慢的水平环绕拍摄，始终保持中近景构图，浅景深效果虚化背景办公设施，焦点牢牢锁定在两人互动的面部表情与手部细节上。整体色调温暖明快，光影柔和，充满职业成就感与温馨积极的商务氛围，电影级写实画质。','img2vid','[\"https://dynamic.senseaudio.cn/image/1800a799-055c-4f74-ba39-1fa86df29c54\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:55:10','2026-10-07 09:55:25',0),(92,37,NULL,'5e27695d-91ca-4a20-adb3-fbca68a72c20','镜头从分界线缓慢向两侧拉开，左侧画面逐渐变暗，右侧画面逐渐变亮，对比感不断强化。','img2vid','[\"https://dynamic.senseaudio.cn/image/6803149f-a58a-4e87-9eaf-beedb19d474d\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:55:13','2026-10-07 09:55:27',0),(93,37,NULL,'aa8982a2-dd31-4646-80d6-edf34399a59e','演讲者微笑点头，手指向镜头下方示意，身体微微前倾，镜头缓慢推近，营造互动感。','img2vid','[\"https://dynamic.senseaudio.cn/image/1614da6a-3b94-4963-beb6-9b797a71221e\"]',NULL,8,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'内容审核未通过，请修改提示词或参考图片后重试','creation','2026-10-07 09:56:00','2026-10-07 09:56:15',0),(94,46,NULL,NULL,'中近景镜头捕捉演讲者面部，他神情严肃，坚定有力地摇头并挥动手臂，动作果断且富有张力；背景光影缓慢流动，色彩由暗转亮再回落，营造紧张转折氛围；镜头起始有轻微手持晃动感，随后迅速稳定，焦点始终锁定在演讲者面部；环境为室内演讲舞台，深蓝色调，侧逆光突出轮廓，画面电影感强，高清晰度，情绪凝重。','img2vid','[\"https://dynamic.senseaudio.cn/image/4cdcb95b-7343-4331-87c6-18db27d1a16e\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 09:56:33','2026-10-07 09:58:24',0),(95,46,NULL,NULL,'写实主义风格，4K 高清电影质感。镜头缓缓推近一名白领的面部特写，他面色凝重，眼神中流露不安，视线紧张地瞥向左侧；旁边同事手指在键盘上快速敲击，机械键盘的轻微振动与屏幕发出的冷白光微微闪烁，映照在两人面部；背景虚化，隐约可见模糊的人影在办公区走动。采用浅景深，侧光照明，冷暖色调对比，营造出压抑且充满悬疑感的职场氛围，画面稳定，细节丰富，光影自然过渡。','img2vid','[\"https://dynamic.senseaudio.cn/image/0a112adb-c304-4686-8c1b-a63ed825401a\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 09:56:33','2026-10-07 09:58:25',0),(96,46,NULL,NULL,'深夜办公室，冷色调蓝光从电脑屏幕投射在设计师疲惫的脸上，特写镜头捕捉他/她痛苦地揉捏眼珠，随后缓慢伸懒腰，手臂舒展，动作充满疲惫感；屏幕上UI设计稿反复闪烁修改，光标快速跳动，反映焦虑状态；镜头缓慢向后拉远并略微升高，逐渐揭示桌面杂乱无章的现实，堆满揉皱的草图纸张、废弃的马克笔、咖啡杯和凌乱线缆，背景昏暗，强调孤立与高压氛围，写实主义风格，电影级景深。','img2vid','[\"https://dynamic.senseaudio.cn/image/cb96eaee-15a6-4ca6-a482-11b5a1e34c25\"]',NULL,5,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 09:58:30','2026-10-07 10:00:21',0),(97,46,NULL,NULL,'中近景镜头捕捉一位年轻设计师在明亮的工作台前，面前多台显示器上UI设计稿与3D模型快速刷新、滚动生成，屏幕光影映照在设计师专注的面庞上；设计师嘴角微微上扬，自信地点头微笑，眼神中透着满足与肯定；随着情绪推进，背景环境光线逐渐由柔和过渡至明亮通透，自然光与屏幕光交织，营造出高效、现代的科技感氛围；镜头缓慢轻微上扬，模拟仰视视角，强化空间感与积极向上的视觉张力，整体色调清新明亮，细节锐利，电影级画质。','img2vid','[\"https://dynamic.senseaudio.cn/image/79e5dd90-2c43-4976-9eea-54029b2f6245\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 09:58:31','2026-10-07 10:00:20',0),(98,46,NULL,NULL,'镜头从分界线缓慢向两侧拉开，左侧画面逐渐变暗，右侧画面逐渐变亮，对比感不断强化。','img2vid','[\"https://dynamic.senseaudio.cn/image/6803149f-a58a-4e87-9eaf-beedb19d474d\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 10:00:25','2026-10-07 10:02:15',0),(99,46,NULL,NULL,'现代简约明亮的办公室内，身穿深色西装的老板与穿着休闲职业装的设计师面对面站立，两人动作自然流畅地紧握双手，随后老板有力地竖起大拇指，设计师面部肌肉放松，露出惊喜且真诚的灿烂笑容。背景是落地窗，午后黄金时段的阳光透过玻璃洒入室内，形成清晰的丁达尔光束，照亮空气中的微尘。镜头以人物为圆心进行缓慢的水平环绕拍摄，始终保持中近景构图，浅景深效果虚化背景办公设施，焦点牢牢锁定在两人互动的面部表情与手部细节上。整体色调温暖明快，光影柔和，充满职业成就感与温馨积极的商务氛围，电影级写实画质。','img2vid','[\"https://dynamic.senseaudio.cn/image/1800a799-055c-4f74-ba39-1fa86df29c54\"]',NULL,4,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 10:00:26','2026-10-07 10:02:16',0),(100,46,NULL,NULL,'演讲者微笑点头，手指向镜头下方示意，身体微微前倾，镜头缓慢推近，营造互动感。','img2vid','[\"https://dynamic.senseaudio.cn/image/1614da6a-3b94-4963-beb6-9b797a71221e\"]',NULL,8,720,1280,NULL,NULL,24,NULL,'failed',0,NULL,NULL,NULL,'视频生成服务暂时不可用，请稍后再试（已重试5次）','creation','2026-10-07 10:02:21','2026-10-07 10:04:10',0),(101,37,NULL,'a810ff2f-54ec-4b15-aa4a-0d3efb3f7aec','国漫仙侠动态漫画，电影级打斗分镜。场景设定在云海之上的天宫废墟，背景为暗云笼罩的高对比环境。画面主体为两名仙家激烈斗法：左侧青衣剑仙脚踏七星，身缠银蓝剑炁粒子，身后展开太虚剑阵，万千飞剑悬浮并伴随星尘拖尾；右侧玄袍雷尊掌心托举紫金雷球，压缩的雷浆中电弧如龙蛇游走，空间产生可见涟漪。两人同时出手，飞剑洪流与神雷洪流剧烈对撞，中心形成太极状能量核，金蓝与紫红粒子爆裂，体积光穿透乌云，冲击波掀翻瓦砾，火星、灵尘、碎玉及符箓纸片在慢动作中飞散。视觉风格融合国漫赛璐璐与厚涂技法，运用广角低角度仰拍与对角线构图，结合环绕运镜与急速推近，营造史诗感。色彩以青蓝银白对抗紫金赤红，强调高对比度与HDR质感，细节包含GPU粒子、等离子电弧、辉光、镜头光晕、色散及空间裂纹等特效，8K超精细画质，高帧率动态表现。','text2vid',NULL,'低清，模糊，崩坏，多手多指，五官扭曲，塑料感，廉价3D，粒子像贴图，无光影，过曝，死黑，静态，PPT，帧重复，水印，文字，logo，画面脏乱，粒子糊成一片。',15,1280,720,441,30,24,1205445419,'completed',100,'https://dynamic.senseaudio.cn/video/737f9012-d125-4dd2-9d9d-33f42f74b954',NULL,NULL,NULL,NULL,'2026-10-07 18:29:03','2026-10-07 18:35:00',0),(102,37,NULL,'d50912a6-64e2-4dda-b446-998f83a0b3b0','电影级仙侠法术斗法场景，青云山巅云海翻涌，古松受气压压弯。人物卡A与人物卡B进行纯法术对决，无法宝。法术碰撞先改变环境再产生能量，最后爆开粒子。粒子分三层：底层体积雾厚重、被气压推动，中层能量流有方向、湍流、颜色渐变，上层实体碎屑包括冰晶、碎石、焦屑、金粉、符箓碎纸。UE5 Lumen全局光照，路径追踪，体积散射，次表面散射，HDR 8K电影级调色。低饱和暗部，高纯度能量色。运镜为呼吸式急推骤停，环绕后拉，极远景切微距，物理运动模糊，细节丰富，光影震撼。','multi2vid','[\"https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-10-07/1791373363440.png\",\"https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-10-07/1791373367526.jpeg\"]','对波，光柱，彩虹色，速度线，集中线，残影，廉价发光，塑料感，粒子像贴图，喊招式，PPT，脸崩，换装，水印，文字，logo，beam clash, light pillar, speed lines, cheap glow, particle stickers, plastic 3D, inconsistent character。',15,1280,720,441,30,24,1827533940,'completed',100,'https://dynamic.senseaudio.cn/video/fc8bfcbd-473a-40ae-99b2-2d185774a0d1',NULL,NULL,NULL,NULL,'2026-10-07 19:43:31','2026-10-07 19:50:00',0);
/*!40000 ALTER TABLE `ai_video_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_viral_template`
--

DROP TABLE IF EXISTS `ai_viral_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_viral_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT,
  `source_url` varchar(500) DEFAULT NULL COMMENT '爆款来源链接（或上传文件媒体路径）',
  `source_video_url` varchar(500) DEFAULT NULL COMMENT '下载后转储的视频地址',
  `title` varchar(200) DEFAULT NULL COMMENT '爆款标题',
  `asr_text` text COMMENT '转写全文',
  `structure_json` text COMMENT '结构模板：钩子/分镜骨架/节奏/视觉风格',
  `hook_type` varchar(50) DEFAULT NULL COMMENT '钩子类型：悬念/冲突/反常识/福利/身份共鸣/结果前置',
  `duration_sec` int DEFAULT NULL COMMENT '原片时长（秒）',
  `shot_count` int DEFAULT NULL COMMENT '拆解出的分镜数',
  `status` varchar(20) DEFAULT 'analyzing' COMMENT 'analyzing/ready/failed',
  `error_msg` text,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` smallint DEFAULT '0',
  PRIMARY KEY (`template_id`),
  KEY `idx_hook_type` (`hook_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作-爆款结构模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_viral_template`
--

LOCK TABLES `ai_viral_template` WRITE;
/*!40000 ALTER TABLE `ai_viral_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_viral_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_voice_profile`
--

DROP TABLE IF EXISTS `ai_voice_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_voice_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `name` varchar(64) NOT NULL COMMENT '用户起的别名',
  `vendor` varchar(32) NOT NULL COMMENT '供应商 siliconflow / cosyvoice / ...',
  `vendor_voice_id` varchar(256) NOT NULL COMMENT '上游音色 uri, 如 speech:my-name:xxx:xxx',
  `vendor_model` varchar(128) DEFAULT NULL COMMENT '上游模型, 如 IndexTeam/IndexTTS-2',
  `sample_url` varchar(512) DEFAULT NULL COMMENT 'OSS 样本地址',
  `sample_text` varchar(1024) DEFAULT NULL COMMENT '样本朗读文本(用于一致性校验)',
  `duration_ms` int DEFAULT NULL COMMENT '样本时长(毫秒)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=上传中 1=训练中 2=可用 3=失败',
  `consent_signed` tinyint NOT NULL DEFAULT '0' COMMENT '合规授权签署(强制=1)',
  `consent_ip` varchar(64) DEFAULT NULL COMMENT '签署时 IP',
  `consent_at` datetime DEFAULT NULL COMMENT '签署时间',
  `error_msg` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`,`del_flag`),
  KEY `idx_vendor_voice` (`vendor`,`vendor_voice_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闊宠壊搴';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_voice_profile`
--

LOCK TABLES `ai_voice_profile` WRITE;
/*!40000 ALTER TABLE `ai_voice_profile` DISABLE KEYS */;
INSERT INTO `ai_voice_profile` VALUES (1,1,'xiaoli','siliconflow','speech:xiaoli:d9ji5ks50mis73e3bijg:gcbakiyvxhejzzzbdoym','IndexTeam/IndexTTS-2',NULL,'我是帅哥现在克隆音色',10000,2,1,NULL,'2026-10-03 00:24:58',NULL,'2026-10-03 00:24:59','2026-10-03 00:24:59',0,NULL,'2026-10-03 00:24:59','2026-10-03 00:24:59');
/*!40000 ALTER TABLE `ai_voice_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `express_company_dict`
--

DROP TABLE IF EXISTS `express_company_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `express_company_dict` (
  `code` varchar(20) NOT NULL,
  `name` varchar(30) NOT NULL,
  `sort` int DEFAULT '0',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='快递公司字典';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `express_company_dict`
--

LOCK TABLES `express_company_dict` WRITE;
/*!40000 ALTER TABLE `express_company_dict` DISABLE KEYS */;
INSERT INTO `express_company_dict` VALUES ('EMS','中国邮政',6),('JD','京东物流',5),('SF','顺丰速运',1),('YD','韵达快递',4),('YTO','圆通速递',3),('ZTO','中通快递',2);
/*!40000 ALTER TABLE `express_company_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feed`
--

DROP TABLE IF EXISTS `feed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed` (
  `feed_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '发布者ID',
  `content` varchar(2000) DEFAULT '' COMMENT '文字内容',
  `images` text COMMENT 'JSON数组,最多9张',
  `related_product_id` bigint DEFAULT NULL COMMENT '关联商品ID',
  `visibility` varchar(20) DEFAULT 'public' COMMENT 'public/friends/self',
  `like_count` int DEFAULT '0',
  `comment_count` int DEFAULT '0',
  `forward_count` int DEFAULT '0',
  `hot_score` decimal(10,2) DEFAULT '0.00' COMMENT '热度分(推荐算法用)',
  `status` varchar(20) DEFAULT 'normal' COMMENT 'normal/deleted',
  `audit_status` varchar(20) DEFAULT 'approved' COMMENT 'pending/approved/rejected',
  `audit_reason` varchar(500) DEFAULT NULL COMMENT '审核拒绝原因',
  `original_feed_id` bigint DEFAULT NULL COMMENT '来源动态ID(转发时用)',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`feed_id`),
  KEY `idx_user_time` (`user_id`,`publish_time`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_audit` (`audit_status`,`publish_time`),
  KEY `idx_feed_tenant` (`tenant_id`,`publish_time`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed`
--

LOCK TABLES `feed` WRITE;
/*!40000 ALTER TABLE `feed` DISABLE KEYS */;
INSERT INTO `feed` VALUES (5,101,'出一些闲置好物，都是之前囤的，质量很好～ 有感兴趣的姐妹可以来看 👗👠','[\"https://picsum.photos/400/400?random=1\",\"https://picsum.photos/400/400?random=2\",\"https://picsum.photos/400/400?random=3\"]',NULL,'public',23,5,0,38.00,'normal','approved',NULL,NULL,'2026-06-10 14:30:00','2026-08-31 16:07:11','2026-06-10 14:30:00',1),(6,102,'新到一批二手数码好物！iPhone 14 Pro 256G 国行，成色新，价格美丽～ 支持当面验机 💻📱','[\"https://picsum.photos/400/400?random=4\",\"https://picsum.photos/400/400?random=5\"]',NULL,'public',45,12,0,0.00,'normal','approved',NULL,NULL,'2026-06-10 16:15:00','2026-10-30 09:23:28','2026-06-10 16:15:00',1),(7,101,'今天天气太好了，周末来场说走就走的野餐 🌸🧺 你们周末都去哪里玩呀？','[\"https://picsum.photos/400/400?random=6\",\"https://picsum.photos/400/400?random=7\",\"https://picsum.photos/400/400?random=8\",\"https://pics\r\n    um.photos/400/400?random=9\"]',NULL,'public',70,18,0,124.00,'normal','approved',NULL,NULL,'2026-06-11 10:00:00','2026-06-12 15:39:23','2026-06-11 10:00:00',1),(8,102,'出一台成色很好的 MacBook Pro M2，去年买的，用了一两次就吃灰了。带原包装和发票，支持闲鱼担保交易 💪','[\"https://picsum.photos/400/400?random=10\",\"https://picsum.photos/400/400?random=11\",\"https://picsum.photos/400/400?random=12\"]',NULL,'public',39,9,0,0.00,'normal','approved',NULL,NULL,'2026-06-11 13:20:00','2026-06-12 14:45:32','2026-06-11 13:20:00',1),(9,101,'整理衣柜出了几件几乎全新的衣服，S码，尺码合适的姐妹别错过！','[\"https://picsum.photos/400/400?random=13\",\"https://picsum.photos/400/400?random=14\",\"https://picsum.photos/400/400?random=15\",\"https://p\r\n    icsum.photos/400/400?random=16\",\"https://picsum.photos/400/400?random=17\"]',NULL,'public',16,3,0,0.00,'normal','approved',NULL,NULL,'2026-06-11 18:45:00','2026-06-11 18:45:00','2026-06-11 18:45:00',1),(10,102,'搬家清书架，出一批技术和设计类书籍，几乎全新📚 需要的私聊，打包更优惠～','[\"https://picsum.photos/400/400?random=18\"]',NULL,'public',12,2,0,18.00,'normal','approved',NULL,NULL,'2026-06-12 08:30:00','2026-06-12 16:35:51','2026-06-12 08:30:00',1),(11,101,'发现一个宝藏二手好物！在咱们平台淘到了超便宜的相机 📷 成色几乎全新，太开心了！','[\"https://picsum.photos/400/400?random=19\",\"https://picsum.photos/400/400?random=20\"]',NULL,'public',54,17,0,105.00,'normal','approved',NULL,NULL,'2026-06-12 09:15:00','2026-08-28 10:03:10','2026-06-12 09:15:00',1),(12,102,'搬家出健身装备：哑铃一对、瑜伽垫、跑步机，几乎全新！同城自提优先 🏋','[\"https://picsum.photos/400/400?random=21\",\"https://picsum.photos/400/400?random=22\"]',NULL,'public',6,3,0,15.00,'normal','approved',NULL,NULL,'2026-06-12 10:00:00','2026-06-12 18:51:52','2026-06-12 10:00:00',1),(23,101,'崩铁好物','[]',NULL,'public',0,0,0,0.00,'normal','approved',NULL,NULL,'2026-06-12 17:32:09','2026-08-28 13:27:14','2026-06-12 17:32:09',1),(26,101,'搬家清仓～这辆公路车骑了半年，车况很好，有喜欢骑行的邻居吗？','[\"https://picsum.photos/seed/f101/800/600\"]',47,'public',12,3,1,8.50,'normal','approved',NULL,NULL,'2026-10-22 17:02:24','2026-10-27 17:02:24','2026-10-22 17:02:24',1),(27,101,'周末在小区门口摆摊出了几件闲置，邻居们都很好说话，二手真香。','[\"https://picsum.photos/seed/f102/800/600\"]',NULL,'public',25,6,2,15.20,'normal','approved',NULL,NULL,'2026-10-24 17:02:24','2026-10-27 17:02:24','2026-10-24 17:02:24',1),(28,101,'求推荐附近靠谱的搬家公司，下周六要用，谢谢各位邻居！',NULL,NULL,'public',5,8,0,4.10,'normal','approved',NULL,NULL,'2026-10-26 17:02:24','2026-10-27 17:02:24','2026-10-26 17:02:24',1),(29,101,'这条动态还在审核中，用来演示内容审核流程。',NULL,NULL,'public',0,0,0,0.00,'normal','pending',NULL,NULL,'2026-10-27 15:02:24','2026-10-27 17:02:24','2026-10-27 15:02:24',1),(30,101,'这条动态已被驳回，用来演示审核结果展示。',NULL,NULL,'public',0,0,0,0.00,'normal','rejected','含外部引流链接，已驳回',NULL,'2026-10-27 11:02:24','2026-10-27 17:02:24','2026-10-27 11:02:24',1),(31,102,'新到一批数码好物，都是自用闲置，支持同城面交验货。','[\"https://picsum.photos/seed/f106/800/600\"]',NULL,'public',31,9,3,21.70,'normal','approved',NULL,NULL,'2026-10-23 17:02:24','2026-10-27 17:02:24','2026-10-23 17:02:24',1);
/*!40000 ALTER TABLE `feed` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feed_comment`
--

DROP TABLE IF EXISTS `feed_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `feed_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `reply_to_id` bigint DEFAULT NULL COMMENT '回复的目标评论ID(NULL=一级)',
  `reply_to_user_id` bigint DEFAULT NULL COMMENT '被回复用户ID',
  `content` varchar(500) NOT NULL,
  `status` varchar(20) DEFAULT 'normal',
  `create_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`comment_id`),
  KEY `idx_feed_id` (`feed_id`),
  KEY `idx_parent_id` (`reply_to_id`),
  KEY `idx_feed_comment_tenant` (`tenant_id`,`feed_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态评论';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed_comment`
--

LOCK TABLES `feed_comment` WRITE;
/*!40000 ALTER TABLE `feed_comment` DISABLE KEYS */;
INSERT INTO `feed_comment` VALUES (1,11,101,NULL,NULL,'多钱','normal','2026-06-12 17:59:17',1),(2,11,101,NULL,NULL,'？','normal','2026-06-12 17:59:26',1),(3,12,101,NULL,NULL,'你好','normal','2026-06-12 18:40:40',1),(4,12,101,3,NULL,'你好','normal','2026-06-12 18:51:35',1),(5,11,101,NULL,NULL,'666','normal','2026-06-12 18:57:23',1);
/*!40000 ALTER TABLE `feed_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feed_comment_like`
--

DROP TABLE IF EXISTS `feed_comment_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed_comment_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmt_user` (`comment_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论点赞';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed_comment_like`
--

LOCK TABLES `feed_comment_like` WRITE;
/*!40000 ALTER TABLE `feed_comment_like` DISABLE KEYS */;
/*!40000 ALTER TABLE `feed_comment_like` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feed_like`
--

DROP TABLE IF EXISTS `feed_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `feed_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feed_user` (`feed_id`,`user_id`),
  KEY `idx_feed` (`feed_id`),
  KEY `idx_feed_like_tenant` (`tenant_id`,`feed_id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态点赞';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed_like`
--

LOCK TABLES `feed_like` WRITE;
/*!40000 ALTER TABLE `feed_like` DISABLE KEYS */;
INSERT INTO `feed_like` VALUES (12,8,101,'2026-06-12 12:59:39',1),(13,9,101,'2026-06-12 12:59:46',1);
/*!40000 ALTER TABLE `feed_like` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feed_product_ref`
--

DROP TABLE IF EXISTS `feed_product_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed_product_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `feed_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sort` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_feed_id` (`feed_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态关联商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed_product_ref`
--

LOCK TABLES `feed_product_ref` WRITE;
/*!40000 ALTER TABLE `feed_product_ref` DISABLE KEYS */;
/*!40000 ALTER TABLE `feed_product_ref` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `funnel_event`
--

DROP TABLE IF EXISTS `funnel_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `funnel_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛/绀惧尯ID',
  `event_type` varchar(32) NOT NULL COMMENT '10 绫绘紡鏂椾簨浠朵箣涓',
  `user_id` bigint DEFAULT NULL COMMENT '鍖垮悕鏇濆厜涓虹┖',
  `role` varchar(16) DEFAULT NULL COMMENT 'buyer/seller/visitor',
  `product_id` bigint DEFAULT NULL,
  `category` varchar(20) DEFAULT NULL COMMENT 'product.cat_l1 蹇?収',
  `ai_assisted` tinyint NOT NULL DEFAULT '0' COMMENT '1=AI 杈呭姪浜х敓',
  `scene` varchar(32) DEFAULT NULL COMMENT 'home_recommend/nearby/search/detail',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '璁㈠崟/閫??/璇勪环/浼氳瘽ID',
  `event_key` varchar(96) DEFAULT NULL COMMENT '骞傜瓑閿?紝鏈嶅姟绔?簨浠跺繀濉',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_event_key` (`event_key`),
  KEY `idx_tenant_type_time` (`tenant_id`,`event_type`,`create_time`),
  KEY `idx_product_time` (`product_id`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏍稿績婕忔枟浜嬩欢';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `funnel_event`
--

LOCK TABLES `funnel_event` WRITE;
/*!40000 ALTER TABLE `funnel_event` DISABLE KEYS */;
INSERT INTO `funnel_event` VALUES (1,1,'order_create',1,'buyer',2,'home',0,NULL,'31','order_create:31','2026-11-03 16:31:36'),(2,1,'pay_success',1,'buyer',2,'home',0,NULL,'31','pay_success:31','2026-11-03 16:32:03'),(3,1,'order_create',1,'buyer',3,'trendy',0,NULL,'32','order_create:32','2026-11-03 16:33:24'),(4,1,'ship',102,'seller',2,'home',0,NULL,'31','ship:31','2026-11-03 16:36:40'),(5,1,'confirm_receipt',1,'buyer',2,'home',0,NULL,'31','confirm_receipt:31','2026-11-03 16:36:51');
/*!40000 ALTER TABLE `funnel_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_datasource_conf`
--

DROP TABLE IF EXISTS `gen_datasource_conf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_datasource_conf` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '别名',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'jdbcurl',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `ds_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库类型',
  `conf_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置类型(0：主机模式，1：JDBC)',
  `ds_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  `instance` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实例',
  `port` int DEFAULT NULL COMMENT '端口',
  `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主机',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_datasource_conf`
--

LOCK TABLES `gen_datasource_conf` WRITE;
/*!40000 ALTER TABLE `gen_datasource_conf` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_datasource_conf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_field_type`
--

DROP TABLE IF EXISTS `gen_field_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_field_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `column_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段类型',
  `attr_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性类型',
  `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性包名',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `column_type` (`column_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字段类型管理';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_field_type`
--

LOCK TABLES `gen_field_type` WRITE;
/*!40000 ALTER TABLE `gen_field_type` DISABLE KEYS */;
INSERT INTO `gen_field_type` VALUES (1,'datetime','LocalDateTime','java.time.LocalDateTime','2023-02-06 08:45:10',NULL,NULL,NULL,'0'),(2,'date','LocalDate','java.time.LocalDate','2023-02-06 08:45:10',NULL,NULL,NULL,'0'),(3,'tinyint','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(4,'smallint','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(5,'mediumint','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(6,'int','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(7,'integer','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(8,'bigint','Long',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(9,'float','Float',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(10,'double','Double',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(11,'decimal','BigDecimal','java.math.BigDecimal','2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(12,'bit','Boolean',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(13,'char','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(14,'varchar','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(15,'tinytext','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(16,'text','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(17,'mediumtext','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(18,'longtext','String',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(19,'timestamp','LocalDateTime','java.time.LocalDateTime','2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(20,'NUMBER','Integer',NULL,'2023-02-06 08:45:11',NULL,NULL,NULL,'0'),(21,'BINARY_INTEGER','Integer',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(22,'BINARY_FLOAT','Float',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(23,'BINARY_DOUBLE','Double',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(24,'VARCHAR2','String',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(25,'NVARCHAR','String',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(26,'NVARCHAR2','String',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(27,'CLOB','String',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(28,'int8','Long',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(29,'int4','Integer',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(30,'int2','Integer',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(31,'numeric','BigDecimal','java.math.BigDecimal','2023-02-06 08:45:12',NULL,NULL,NULL,'0'),(32,'json','String',NULL,'2023-02-06 08:45:12',NULL,NULL,NULL,'0');
/*!40000 ALTER TABLE `gen_field_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_group`
--

DROP TABLE IF EXISTS `gen_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组名称',
  `group_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组描述',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模板分组';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_group`
--

LOCK TABLES `gen_group` WRITE;
/*!40000 ALTER TABLE `gen_group` DISABLE KEYS */;
INSERT INTO `gen_group` VALUES (1,'单表CRUD','单表CRUD','admin','admin','2023-11-07 18:04:33','2023-11-10 11:20:51'),(2,'主子表CRUD','主子表CRUD','admin','admin','2023-11-08 09:28:19','2023-11-10 11:20:55'),(3,'树表CRUD','树表CRUD','admin','admin','2023-11-08 09:38:54','2023-11-10 14:17:12');
/*!40000 ALTER TABLE `gen_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ds_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据源别名',
  `db_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据源类型',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_group_id` bigint NOT NULL DEFAULT '1' COMMENT '使用的模板分组ID',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_template`
--

DROP TABLE IF EXISTS `gen_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `generator_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板路径',
  `template_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板描述',
  `template_code` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板代码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标记',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ' ' COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成业务模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_template`
--

LOCK TABLES `gen_template` WRITE;
/*!40000 ALTER TABLE `gen_template` DISABLE KEYS */;
INSERT INTO `gen_template` VALUES (1,'Controller','${backendPath}/src/main/java/${packagePath}/controller/${ClassName}Controller.java','Controller','package ${packageName}.controller;\r\n\r\nimport java.util.List;\r\nimport jakarta.servlet.http.HttpServletResponse;\r\n\r\nimport com.twelvet.framework.jdbc.web.page.TableDataInfo;\r\nimport cn.dev33.satoken.annotation.SaCheckPermission;\r\nimport io.swagger.v3.oas.annotations.tags.Tag;\r\nimport io.swagger.v3.oas.annotations.Operation;\r\nimport com.twelvet.framework.log.annotation.Log;\r\nimport com.twelvet.framework.log.enums.BusinessType;\r\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\r\nimport ${packageName}.service.I${ClassName}Service;\r\nimport com.twelvet.framework.core.application.controller.TWTController;\r\nimport com.twelvet.framework.core.application.domain.AjaxResult;\r\nimport com.twelvet.framework.core.application.domain.JsonResult;\r\nimport com.pig4cloud.plugin.excel.annotation.ResponseExcel;\r\nimport org.springframework.beans.factory.annotation.Autowired;\r\nimport org.springframework.web.bind.annotation.*;\r\n#if($table.crud || $table.sub)\r\nimport com.twelvet.framework.jdbc.web.utils.PageUtils;\r\n#elseif($table.tree)\r\n#end\r\n\r\n/**\r\n * ${functionName}Controller\r\n *\r\n * @author ${author}\r\n * @date ${datetime}\r\n */\r\n@Tag(description = \"${ClassName}Controller\", name = \"${functionName}\")\r\n@RestController\r\n@RequestMapping(\"/${businessName}\")\r\npublic class ${ClassName}Controller extends TWTController\r\n{\r\n    @Autowired\r\n    private I${ClassName}Service ${className}Service;\r\n\r\n    /**\r\n     * 查询${functionName}分页\r\n     */\r\n    @Operation(summary = \"查询${functionName}分页\")\r\n    @SaCheckPermission(\"${permissionPrefix}:list\")\r\n    @GetMapping(\"/pageQuery\")\r\n    #if($table.crud || $table.sub)\r\n    public JsonResult<TableDataInfo<${ClassName}>> pageQuery(${ClassName} ${className})\r\n    {\r\n        PageUtils.startPage();\r\n        List<${ClassName}> list = ${className}Service.select${ClassName}List(${className});\r\n        return JsonResult.success(PageUtils.getDataTable(list));\r\n    }\r\n    #elseif($table.tree)\r\n        public JsonResult<${ClassName}>> list(${ClassName} ${className})\r\n        {\r\n            List<${ClassName}> list = ${className}Service.select${ClassName}List(${className});\r\n            return JsonResult.success(list);\r\n        }\r\n    #end\r\n\r\n    /**\r\n     * 导出${functionName}列表\r\n     */\r\n    @ResponseExcel(name = \"${functionName}\")\r\n    @Operation(summary = \"导出${functionName}列表\")\r\n    @SaCheckPermission(\"${permissionPrefix}:export\")\r\n    @Log(service = \"${functionName}\", businessType = BusinessType.EXPORT)\r\n    @PostMapping(\"/export\")\r\n    public List<${ClassName}> export(${ClassName} ${className})\r\n    {\r\n        return ${className}Service.select${ClassName}List(${className});\r\n    }\r\n\r\n    /**\r\n     * 获取${functionName}详细信息\r\n     */\r\n    @Operation(summary = \"获取${functionName}详细信息\")\r\n    @SaCheckPermission(\"${permissionPrefix}:query\")\r\n    @GetMapping(value = \"/{${pkColumn.javaField}}\")\r\n    public JsonResult<${ClassName}> getInfo(@PathVariable(\"${pkColumn.javaField}\") ${pkColumn.javaType} ${pkColumn.javaField})\r\n    {\r\n        return JsonResult.success(${className}Service.select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaField}));\r\n    }\r\n\r\n    /**\r\n     * 新增${functionName}\r\n     */\r\n    @Operation(summary = \"新增${functionName}\")\r\n    @SaCheckPermission(\"${permissionPrefix}:add\")\r\n    @Log(service = \"${functionName}\", businessType = BusinessType.INSERT)\r\n    @PostMapping\r\n    public JsonResult add(@RequestBody ${ClassName} ${className})\r\n    {\r\n        return json(${className}Service.insert${ClassName}(${className}));\r\n    }\r\n\r\n    /**\r\n     * 修改${functionName}\r\n     */\r\n    @Operation(summary = \"修改${functionName}\")\r\n    @SaCheckPermission(\"${permissionPrefix}:edit\")\r\n    @Log(service = \"${functionName}\", businessType = BusinessType.UPDATE)\r\n    @PutMapping\r\n    public JsonResult edit(@RequestBody ${ClassName} ${className})\r\n    {\r\n        return json(${className}Service.update${ClassName}(${className}));\r\n    }\r\n\r\n    /**\r\n     * 删除${functionName}\r\n     */\r\n    @Operation(summary = \"删除${functionName}\")\r\n    @SaCheckPermission(\"${permissionPrefix}:remove\")\r\n    @Log(service = \"${functionName}\", businessType = BusinessType.DELETE)\r\n    @DeleteMapping(\"/{${pkColumn.javaField}s}\")\r\n    public JsonResult remove(@PathVariable ${pkColumn.javaType}[] ${pkColumn.javaField}s)\r\n    {\r\n        return json(${className}Service.delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaField}s));\r\n    }\r\n}','2023-11-07 17:39:44','2026-10-21 14:16:38','0','admin','admin'),(2,'Service','${backendPath}/src/main/java/${packagePath}/service/${ClassName}Service.java','Service','package ${packageName}.service;\n\nimport java.util.List;\n\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\n\n/**\n * ${functionName}Service接口\n *\n * @author ${author}\n * @date ${datetime}\n */\npublic interface I${ClassName}Service {\n    /**\n     * 查询${functionName}\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return ${functionName}\n     */\n    public ${ClassName} select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n\n    /**\n     * 查询${functionName}列表\n     *\n     * @param ${className} ${functionName}\n     * @return ${functionName}集合\n     */\n    public List<${ClassName}> select${ClassName}List(${ClassName} ${className});\n\n    /**\n     * 新增${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int insert${ClassName}(${ClassName} ${className});\n\n    /**\n     * 修改${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int update${ClassName}(${ClassName} ${className});\n\n    /**\n     * 批量删除${functionName}\n     *\n     * @param ${pkColumn.javaField}s 需要删除的${functionName}主键集合\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaType}[] ${pkColumn.javaField}s);\n\n    /**\n     * 删除${functionName}信息\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n}','2023-11-08 10:38:40','2026-10-21 14:16:38','0','admin','admin'),(3,'Mapper','${backendPath}/src/main/java/${packagePath}/mapper/${ClassName}Mapper.java','Mapper','package ${packageName}.mapper;\n\nimport java.util.List;\n\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\n#if($table.sub)\nimport com.twelvet.${moduleName}.api.domain.${subClassName};\n#end\n\n/**\n * ${functionName}Mapper接口\n *\n * @author ${author}\n * @date ${datetime}\n */\npublic interface ${ClassName}Mapper {\n\n    /**\n     * 查询${functionName}\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return ${functionName}\n     */\n    public ${ClassName} select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n\n    /**\n     * 查询${functionName}列表\n     *\n     * @param ${className} ${functionName}\n     * @return ${functionName}集合\n     */\n    public List<${ClassName}> select${ClassName}List(${ClassName} ${className});\n\n    /**\n     * 新增${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int insert${ClassName}(${ClassName} ${className});\n\n    /**\n     * 修改${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int update${ClassName}(${ClassName} ${className});\n\n    /**\n     * 删除${functionName}\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n\n    /**\n     * 批量删除${functionName}\n     *\n     * @param ${pkColumn.javaField}s 需要删除的数据主键集合\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaType}[] ${pkColumn.javaField}s);\n#if($table.sub)\n\n    /**\n     * 批量删除${subTable.functionName}\n     *\n     * @param ${pkColumn.javaField}s 需要删除的数据主键集合\n     * @return 结果\n     */\n    public int delete${subClassName}By${subTableFkClassName}s(${pkColumn.javaType}[] ${pkColumn.javaField}s);\n\n    /**\n     * 批量新增${subTable.functionName}\n     *\n     * @param ${subclassName}List ${subTable.functionName}列表\n     * @return 结果\n     */\n    public int batch${subClassName}(List<${subClassName}> ${subclassName}List);\n\n\n    /**\n     * 通过${functionName}主键删除${subTable.functionName}信息\n     *\n     * @param ${pkColumn.javaField} ${functionName}ID\n     * @return 结果\n     */\n    public int delete${subClassName}By${subTableFkClassName}(${pkColumn.javaType} ${pkColumn.javaField});\n#end\n}','2023-11-09 14:27:08','2026-10-21 14:16:38','0','admin','admin'),(4,'Service','${backendPath}/src/main/java/${packagePath}/service/${ClassName}Service.java','Service','package ${packageName}.service;\n\nimport java.util.List;\n\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\n\n/**\n * ${functionName}Service接口\n *\n * @author ${author}\n * @date ${datetime}\n */\npublic interface I${ClassName}Service {\n    /**\n     * 查询${functionName}\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return ${functionName}\n     */\n    public ${ClassName} select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n\n    /**\n     * 查询${functionName}列表\n     *\n     * @param ${className} ${functionName}\n     * @return ${functionName}集合\n     */\n    public List<${ClassName}> select${ClassName}List(${ClassName} ${className});\n\n    /**\n     * 新增${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int insert${ClassName}(${ClassName} ${className});\n\n    /**\n     * 修改${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n    public int update${ClassName}(${ClassName} ${className});\n\n    /**\n     * 批量删除${functionName}\n     *\n     * @param ${pkColumn.javaField}s 需要删除的${functionName}主键集合\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaType}[] ${pkColumn.javaField}s);\n\n    /**\n     * 删除${functionName}信息\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return 结果\n     */\n    public int delete${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField});\n}','2023-11-09 14:27:38','2026-10-21 14:16:38','0','admin','admin'),(5,'ServiceImpl','${backendPath}/src/main/java/${packagePath}/service/impl/${ClassName}ServiceImpl.java','ServiceImpl','package ${packageName}.service.impl;\n\nimport java.util.List;\n#foreach ($column in $columns)\n#if($column.javaField == \'createTime\' || $column.javaField == \'updateTime\')\nimport com.twelvet.framework.utils.DateUtils;\n#break\n#end\n#end\nimport org.springframework.beans.factory.annotation.Autowired;\nimport com.twelvet.framework.security.utils.SecurityUtils;\nimport org.springframework.stereotype.Service;\n#if($table.sub)\nimport java.util.ArrayList;\nimport com.twelvet.framework.core.utils.StringUtils;\nimport org.springframework.transaction.annotation.Transactional;\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\n#end\nimport ${packageName}.mapper.${ClassName}Mapper;\nimport com.twelvet.${moduleName}.api.domain.${ClassName};\nimport ${packageName}.service.I${ClassName}Service;\n\n/**\n * ${functionName}Service业务层处理\n *\n * @author ${author}\n * @date ${datetime}\n */\n@Service\npublic class ${ClassName}ServiceImpl implements I${ClassName}Service {\n\n    @Autowired\n    private ${ClassName}Mapper ${className}Mapper;\n\n    /**\n     * 查询${functionName}\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return ${functionName}\n     */\n    @Override\n    public ${ClassName} select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField})\n    {\n        return ${className}Mapper.select${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaField});\n    }\n\n    /**\n     * 查询${functionName}列表\n     *\n     * @param ${className} ${functionName}\n     * @return ${functionName}\n     */\n    @Override\n    public List<${ClassName}> select${ClassName}List(${ClassName} ${className})\n    {\n        return ${className}Mapper.select${ClassName}List(${className});\n    }\n\n    /**\n     * 新增${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n#if($table.sub)\n    @Transactional\n#end\n    @Override\n    public int insert${ClassName}(${ClassName} ${className})\n    {\n#foreach ($column in $columns)\n#if($column.javaField == \'createTime\')\n        ${className}.setCreateTime(DateUtils.getNowDate());\n        String loginUsername = SecurityUtils.getUsername();\n        ${className}.setCreateBy(loginUsername);\n        ${className}.setUpdateBy(loginUsername);\n#end\n#end\n#if($table.sub)\n        int rows = ${className}Mapper.insert${ClassName}(${className});\n        insert${subClassName}(${className});\n        return rows;\n#else\n        return ${className}Mapper.insert${ClassName}(${className});\n#end\n    }\n\n    /**\n     * 修改${functionName}\n     *\n     * @param ${className} ${functionName}\n     * @return 结果\n     */\n#if($table.sub)\n    @Transactional\n#end\n    @Override\n    public int update${ClassName}(${ClassName} ${className})\n    {\n#foreach ($column in $columns)\n#if($column.javaField == \'updateTime\')\n        ${className}.setUpdateTime(DateUtils.getNowDate());\n        String loginUsername = SecurityUtils.getUsername();\n        ${className}.setCreateBy(loginUsername);\n        ${className}.setUpdateBy(loginUsername);\n#end\n#end\n#if($table.sub)\n        ${className}Mapper.delete${subClassName}By${subTableFkClassName}(${className}.get${pkColumn.capJavaField}());\n        insert${subClassName}(${className});\n#end\n        return ${className}Mapper.update${ClassName}(${className});\n    }\n\n    /**\n     * 批量删除${functionName}\n     *\n     * @param ${pkColumn.javaField}s 需要删除的${functionName}主键\n     * @return 结果\n     */\n#if($table.sub)\n    @Transactional\n#end\n    @Override\n    public int delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaType}[] ${pkColumn.javaField}s)\n    {\n#if($table.sub)\n        ${className}Mapper.delete${subClassName}By${subTableFkClassName}s(${pkColumn.javaField}s);\n#end\n        return ${className}Mapper.delete${ClassName}By${pkColumn.capJavaField}s(${pkColumn.javaField}s);\n    }\n\n    /**\n     * 删除${functionName}信息\n     *\n     * @param ${pkColumn.javaField} ${functionName}主键\n     * @return 结果\n     */\n#if($table.sub)\n    @Transactional\n#end\n    @Override\n    public int delete${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaType} ${pkColumn.javaField})\n    {\n#if($table.sub)\n        ${className}Mapper.delete${subClassName}By${subTableFkClassName}(${pkColumn.javaField});\n#end\n        return ${className}Mapper.delete${ClassName}By${pkColumn.capJavaField}(${pkColumn.javaField});\n    }\n#if($table.sub)\n\n    /**\n     * 新增${subTable.functionName}信息\n     *\n     * @param ${className} ${functionName}对象\n     */\n    public void insert${subClassName}(${ClassName} ${className})\n    {\n        List<${subClassName}> ${subclassName}List = ${className}.get${subClassName}List();\n        ${pkColumn.javaType} ${pkColumn.javaField} = ${className}.get${pkColumn.capJavaField}();\n        if (StringUtils.isNotNull(${subclassName}List))\n        {\n            List<${subClassName}> list = new ArrayList<${subClassName}>();\n            for (${subClassName} ${subclassName} : ${subclassName}List)\n            {\n                ${subclassName}.set${subTableFkClassName}(${pkColumn.javaField});\n                list.add(${subclassName});\n            }\n            if (list.size() > 0)\n            {\n                ${className}Mapper.batch${subClassName}(list);\n            }\n        }\n    }\n#end\n}','2023-11-09 14:28:36','2026-10-21 14:16:38','0','admin','admin'),(6,'Mapper.xml','${backendPath}/src/main/resources/mapper/${moduleName}/${ClassName}Mapper.xml','Mapper.xml','<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<!DOCTYPE mapper\nPUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"${packageName}.mapper.${ClassName}Mapper\">\n    \n    <resultMap type=\"${ClassName}\" id=\"${ClassName}Result\">\n#foreach ($column in $columns)\n        <result property=\"${column.javaField}\"    column=\"${column.columnName}\"    />\n#end\n    </resultMap>\n#if($table.sub)\n\n    <resultMap id=\"${ClassName}${subClassName}Result\" type=\"${ClassName}\" extends=\"${ClassName}Result\">\n        <collection property=\"${subclassName}List\" notNullColumn=\"sub_${subTable.pkColumn.columnName}\" javaType=\"java.util.List\" resultMap=\"${subClassName}Result\" />\n    </resultMap>\n\n    <resultMap type=\"${subClassName}\" id=\"${subClassName}Result\">\n#foreach ($column in $subTable.columns)\n        <result property=\"${column.javaField}\"    column=\"sub_${column.columnName}\"    />\n#end\n    </resultMap>\n#end\n\n    <sql id=\"select${ClassName}Vo\">\n        select#foreach($column in $columns) $column.columnName#if($foreach.count != $columns.size()),#end#end from ${tableName}\n    </sql>\n\n    <select id=\"select${ClassName}List\" parameterType=\"${ClassName}\" resultMap=\"${ClassName}Result\">\n        <include refid=\"select${ClassName}Vo\"/>\n        <where>  \n#foreach($column in $columns)\n#set($queryType=$column.queryType)\n#set($javaField=$column.javaField)\n#set($javaType=$column.javaType)\n#set($columnName=$column.columnName)\n#set($AttrName=$column.javaField.substring(0,1).toUpperCase() + ${column.javaField.substring(1)})\n#if($column.query)\n#if($column.queryType == \"EQ\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName = #{$javaField}</if>\n#elseif($queryType == \"NE\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName != #{$javaField}</if>\n#elseif($queryType == \"GT\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName &gt; #{$javaField}</if>\n#elseif($queryType == \"GTE\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName &gt;= #{$javaField}</if>\n#elseif($queryType == \"LT\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName &lt; #{$javaField}</if>\n#elseif($queryType == \"LTE\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName &lt;= #{$javaField}</if>\n#elseif($queryType == \"LIKE\")\n            <if test=\"$javaField != null #if($javaType == \'String\' ) and $javaField.trim() != \'\'#end\"> and $columnName like concat(\'%\', #{$javaField}, \'%\')</if>\n#elseif($queryType == \"BETWEEN\")\n            <if test=\"params.begin$AttrName != null and params.begin$AttrName != \'\' and params.end$AttrName != null and params.end$AttrName != \'\'\"> and $columnName between #{params.begin$AttrName} and #{params.end$AttrName}</if>\n#end\n#end\n#end\n        </where>\n    </select>\n    \n    <select id=\"select${ClassName}By${pkColumn.capJavaField}\" parameterType=\"${pkColumn.javaType}\" resultMap=\"#if($table.sub)${ClassName}${subClassName}Result#else${ClassName}Result#end\">\n#if($table.crud || $table.tree)\n        <include refid=\"select${ClassName}Vo\"/>\n        where ${pkColumn.columnName} = #{${pkColumn.javaField}}\n#elseif($table.sub)\n        select#foreach($column in $columns) a.$column.columnName#if($foreach.count != $columns.size()),#end#end,\n           #foreach($column in $subTable.columns) b.$column.columnName as sub_$column.columnName#if($foreach.count != $subTable.columns.size()),#end#end\n\n        from ${tableName} a\n        left join ${subTableName} b on b.${subTableFkName} = a.${pkColumn.columnName}\n        where a.${pkColumn.columnName} = #{${pkColumn.javaField}}\n#end\n    </select>\n        \n    <insert id=\"insert${ClassName}\" parameterType=\"${ClassName}\"#if($pkColumn.increment) useGeneratedKeys=\"true\" keyProperty=\"$pkColumn.javaField\"#end>\n        insert into ${tableName}\n        <trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">\n#foreach($column in $columns)\n#if($column.columnName != $pkColumn.columnName || !$pkColumn.increment)\n            <if test=\"$column.javaField != null#if($column.javaType == \'String\' && $column.required) and $column.javaField != \'\'#end\">$column.columnName,</if>\n#end\n#end\n         </trim>\n        <trim prefix=\"values (\" suffix=\")\" suffixOverrides=\",\">\n#foreach($column in $columns)\n#if($column.columnName != $pkColumn.columnName || !$pkColumn.increment)\n            <if test=\"$column.javaField != null#if($column.javaType == \'String\' && $column.required) and $column.javaField != \'\'#end\">#{$column.javaField},</if>\n#end\n#end\n         </trim>\n    </insert>\n\n    <update id=\"update${ClassName}\" parameterType=\"${ClassName}\">\n        update ${tableName}\n        <trim prefix=\"SET\" suffixOverrides=\",\">\n#foreach($column in $columns)\n#if($column.columnName != $pkColumn.columnName)\n            <if test=\"$column.javaField != null#if($column.javaType == \'String\' && $column.required) and $column.javaField != \'\'#end\">$column.columnName = #{$column.javaField},</if>\n#end\n#end\n        </trim>\n        where ${pkColumn.columnName} = #{${pkColumn.javaField}}\n    </update>\n\n    <delete id=\"delete${ClassName}By${pkColumn.capJavaField}\" parameterType=\"${pkColumn.javaType}\">\n        delete from ${tableName} where ${pkColumn.columnName} = #{${pkColumn.javaField}}\n    </delete>\n\n    <delete id=\"delete${ClassName}By${pkColumn.capJavaField}s\" parameterType=\"String\">\n        delete from ${tableName} where ${pkColumn.columnName} in \n        <foreach item=\"${pkColumn.javaField}\" collection=\"array\" open=\"(\" separator=\",\" close=\")\">\n            #{${pkColumn.javaField}}\n        </foreach>\n    </delete>\n#if($table.sub)\n    \n    <delete id=\"delete${subClassName}By${subTableFkClassName}s\" parameterType=\"String\">\n        delete from ${subTableName} where ${subTableFkName} in \n        <foreach item=\"${subTableFkclassName}\" collection=\"array\" open=\"(\" separator=\",\" close=\")\">\n            #{${subTableFkclassName}}\n        </foreach>\n    </delete>\n\n    <delete id=\"delete${subClassName}By${subTableFkClassName}\" parameterType=\"${pkColumn.javaType}\">\n        delete from ${subTableName} where ${subTableFkName} = #{${subTableFkclassName}}\n    </delete>\n\n    <insert id=\"batch${subClassName}\">\n        insert into ${subTableName}(#foreach($column in $subTable.columns) $column.columnName#if($foreach.count != $subTable.columns.size()),#end#end) values\n		<foreach item=\"item\" index=\"index\" collection=\"list\" separator=\",\">\n            (#foreach($column in $subTable.columns) #{item.$column.javaField}#if($foreach.count != $subTable.columns.size()),#end#end)\n        </foreach>\n    </insert>\n#end\n</mapper>','2023-11-09 14:29:42','2023-11-10 11:11:53','0','admin','admin'),(7,'SQL','${backendPath}/${className}.sql','SQL','-- 菜单 SQL\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}\', \'${parentMenuId}\', \'1\', \'${businessName}\', \'${moduleName}/${businessName}/index\', 1, \'C\', \'0\', \'0\', \'${permissionPrefix}:list\', \'#\', \'admin\', \'2018-03-01\', \'twelvet\', \'2018-03-01\', \'${functionName}菜单\');\n\n-- 按钮父菜单ID\nSELECT @parentId := LAST_INSERT_ID();\n\n-- 按钮 SQL\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}查询\', @parentId, \'1\',  \'#\', \'\', 1, \'F\', \'0\', \'0\', \'${permissionPrefix}:query\',        \'#\', \'admin\', \'2018-03-01\', \'admin\', \'2018-03-01\', \'\');\n\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}新增\', @parentId, \'2\',  \'#\', \'\', 1, \'F\', \'0\', \'0\', \'${permissionPrefix}:add\',          \'#\', \'admin\', \'2018-03-01\', \'admin\', \'2018-03-01\', \'\');\n\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}修改\', @parentId, \'3\',  \'#\', \'\', 1, \'F\', \'0\', \'0\', \'${permissionPrefix}:edit\',         \'#\', \'admin\', \'2018-03-01\', \'admin\', \'2018-03-01\', \'\');\n\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}删除\', @parentId, \'4\',  \'#\', \'\', 1, \'F\', \'0\', \'0\', \'${permissionPrefix}:remove\',       \'#\', \'admin\', \'2018-03-01\', \'admin\', \'2018-03-01\', \'\');\n\ninsert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)\nvalues(\'${functionName}导出\', @parentId, \'5\',  \'#\', \'\', 1, \'F\', \'0\', \'0\', \'${permissionPrefix}:export\',       \'#\', \'admin\', \'2018-03-01\', \'admin\', \'2018-03-01\', \'\');\n','2023-11-09 14:33:56','2023-11-10 11:13:07','0','admin','admin'),(8,'实体','${backendPath}/src/main/java/${packagePath}/domain/${ClassName}.java','实体','package ${packageName}.domain;\n\n#foreach ($import in $importList)\nimport ${import};\n#end\nimport cn.idev.excel.annotation.ExcelProperty;\nimport com.twelvet.framework.core.application.domain.BaseEntity;\nimport org.apache.commons.lang3.builder.ToStringBuilder;\nimport org.apache.commons.lang3.builder.ToStringStyle;\n\nimport io.swagger.v3.oas.annotations.media.Schema;\n#if($table.crud || $table.sub)\n#elseif($table.tree)\nimport com.twelvet.framework.jdbc.web.domain.TreeEntity;\n#end\n\n/**\n * ${functionName}对象 ${tableName}\n *\n * @author ${author}\n * @date ${datetime}\n */\n#if($table.crud || $table.sub)\n#set($Entity=\"BaseEntity\")\n#elseif($table.tree)\n#set($Entity=\"TreeEntity\")\n#end\n@Schema(description =\"${functionName}对象\")\npublic class ${ClassName} extends ${Entity} {\n\n    private static final long serialVersionUID = 1L;\n\n#foreach ($column in $columns)\n    #if(!$table.isSuperColumn($column.javaField))\n    /** $column.columnComment */\n    @Schema(description = \"$column.columnComment\")\n        #if($column.list)\n            #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n            #if($parentheseIndex != -1)\n                #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n            #else\n                #set($comment=$column.columnComment)\n            #end\n            #if($parentheseIndex != -1)\n    @ExcelProperty(value =\"${comment}($column.readConverterExp())\")\n            #elseif($column.javaType == \'Date\')\n    @JsonFormat(pattern = \"yyyy-MM-dd\")\n    @ExcelProperty(value =\"${comment}\", width = 30, dateFormat = \"yyyy-MM-dd\")\n            #else\n    @ExcelProperty(value =\"${comment}\")\n            #end\n        #end\n    private $column.javaType $column.javaField;\n\n    #end\n#end\n#if($table.sub)\n    /** $table.subTable.functionName信息 */\n    private List<${subClassName}> ${subclassName}List;\n\n#end\n#foreach ($column in $columns)\n#if(!$table.isSuperColumn($column.javaField))\n#if($column.javaField.length() > 2 && $column.javaField.substring(1,2).matches(\"[A-Z]\"))\n#set($AttrName=$column.javaField)\n#else\n#set($AttrName=$column.javaField.substring(0,1).toUpperCase() + ${column.javaField.substring(1)})\n#end\n    public void set${AttrName}($column.javaType $column.javaField)\n    {\n        this.$column.javaField = $column.javaField;\n    }\n\n    public $column.javaType get${AttrName}()\n    {\n        return $column.javaField;\n    }\n#end\n#end\n\n#if($table.sub)\n    public List<${subClassName}> get${subClassName}List()\n    {\n        return ${subclassName}List;\n    }\n\n    public void set${subClassName}List(List<${subClassName}> ${subclassName}List)\n    {\n        this.${subclassName}List = ${subclassName}List;\n    }\n\n#end\n    @Override\n    public String toString() {\n        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)\n#foreach ($column in $columns)\n#if($column.javaField.length() > 2 && $column.javaField.substring(1,2).matches(\"[A-Z]\"))\n#set($AttrName=$column.javaField)\n#else\n#set($AttrName=$column.javaField.substring(0,1).toUpperCase() + ${column.javaField.substring(1)})\n#end\n            .append(\"${column.javaField}\", get${AttrName}())\n#end\n#if($table.sub)\n            .append(\"${subclassName}List\", get${subClassName}List())\n#end\n            .toString();\n    }\n}\n','2023-11-09 14:52:04','2026-10-21 14:16:38','0','admin','admin'),(9,'React页面','${frontendPath}/react/pages/${moduleName}/${businessName}/index.tsx','React页面','import React, {useState, useRef} from \'react\'\nimport { useIntl } from \'@umijs/max\'\nimport {PageContainer, ProTable} from \'@ant-design/pro-components\'\nimport type {ActionType, ProColumns} from \'@ant-design/pro-components\'\nimport {\n    DeleteOutlined,\n    FundProjectionScreenOutlined,\n    PlusOutlined,\n    EditOutlined,\n    CloseOutlined\n} from \'@ant-design/icons\'\nimport {Popconfirm, Button, message, Modal, Form, Input, Space, Divider} from \'antd\'\nimport {FormInstance} from \'antd/lib/form\'\n#foreach($column in $columns)\n    #if($column.htmlType == \"select\")\n    import DictionariesSelect from \'@/components/TwelveT/Dictionaries/DictionariesSelect/Index\'\n        #break\n    #end\n#end\n#foreach($column in $columns)\n    #if($column.htmlType == \"radio\")\n    import DictionariesRadio from \'@/components/TwelveT/Dictionaries/DictionariesRadio/Index\'\n        #break\n    #end\n#end\n#foreach($column in $columns)\n    #if($column.htmlType == \"checkbox\")\n    import DictionariesCheckbox from \'@/components/TwelveT/Dictionaries/DictionariesCheckbox/Index\'\n        #break\n    #end\n#end\nimport {\n    pageQuery${BusinessName},\n    get${BusinessName},\n    del${BusinessName},\n    add${BusinessName},\n    update${BusinessName},\n    export${BusinessName}\n} from \'./service\'\nimport {system} from \'@/utils/twelvet\'\nimport {isArray} from \'lodash\'\n#set($importTwT = false)\n#foreach($column in $columns)\n    #if($column.htmlType == \"imageUpload\" || $column.htmlType == \"fileUpload\")\n        #set($importTwT = true)\n    import UploadTWT from \'@/components/TwelveT/Upload\'\n        #break\n    #end\n#end\n#if($importTwT)\nimport TWT, {proTableConfigs} from \'@/setting\'\n#else\nimport {proTableConfigs} from \'@/setting\'\n#end\n\n#foreach($column in $columns)\n    #if($column.htmlType == \"datetime\")\n    import DatePickerTWT from \'@/components/TwelveT/DatePicker/index\'\n    import moment from \'moment\'\n        #break\n    #end\n#end\n\n#if($table.sub)\nimport {EditableProTable} from \'@ant-design/pro-components\'\n#end\n/**\n * ${functionName}模块\n */\nconst ${BusinessName}: React.FC = () => {\n  \n    const {formatMessage} = useIntl()\n\n    const [state] = useState<{\n        pageSize: number\n    }>({\n        pageSize: 10,\n    });\n\n    // 显示Modal\n    const [modal, setModal] = useState<{ title: string, visible: boolean }>({title: ``, visible: false})\n\n    // 是否执行Modal数据操作中\n    const [loadingModal, setLoadingModal] = useState<boolean>(false)\n\n    const acForm = useRef<ActionType>()\n\n    const formRef = useRef<FormInstance>()\n\n    const [form] = Form.useForm()\n\n    const formItemLayout = {\n        labelCol: {\n            xs: {span: 4},\n            sm: {span: 4},\n        },\n        wrapperCol: {\n            xs: {span: 18},\n            sm: {span: 18},\n        },\n    }\n\n    #if($table.sub)\n\n        const [tableForm] = Form.useForm<FormInstance>()\n        const [dataSource, setDataSource] = useState<[]>([])\n\n        const columnsEdit: ProColumns = [\n            #foreach($column in $subTable.columns)\n                #set($javaField=$column.javaField)\n                #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n                #if($parentheseIndex != -1)\n                    #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n                #else\n                    #set($comment=$column.columnComment)\n                #end\n                #if($column.pk)\n                #else\n                    {\n                        title: \'${comment}\', ellipsis: true, width: 100, valueType: \"text\", dataIndex: \'${javaField}\',\n                    },\n                #end\n            #end\n            {\n                title: \'操作\', fixed: \'right\', width: 80, valueType: \"option\", dataIndex: \'operation\'\n            },\n        ]\n    #end\n\n    /**\n     * 新增${functionName}数据\n     * @param row row\n     */\n    const refPost = async () => {\n        setModal({title: formatMessage({id: \'system.add\'}), visible: true})\n    }\n\n    /**\n     * 获取修改${functionName}信息\n     * @param row row\n     */\n    const refPut = async (row: { [key: string]: any }) => {\n        try {\n            const {code, msg, data} = await get${BusinessName}(row.${pkColumn.javaField})\n            if (code !== 200) {\n                return message.error(msg)\n            }\n\n            #if($table.sub)\n                setDataSource(data.${subclassName}List)\n            #end\n\n            // 赋值表单数据\n            form.setFieldsValue(data)\n\n            // 设置Modal状态\n            setModal({title: formatMessage({id: \'system.update\'}), visible: true})\n\n        } catch (e) {\n            system.error(e)\n        }\n    }\n\n    /**\n     * 移除${functionName}数据\n     * @param row ${pkColumn.javaField}\n     */\n    const refRemove = async (${pkColumn.javaField}: (string | number)[] | string | undefined) => {\n        try {\n            if (!${pkColumn.javaField}) {\n                return true\n            }\n\n            let params\n            if (isArray(${pkColumn.javaField})) {\n                params = ${pkColumn.javaField}.join(\",\")\n            } else {\n                params = ${pkColumn.javaField}\n            }\n\n            const {code, msg} = await del${BusinessName}(params)\n\n            if (code !== 200) {\n                return message.error(msg)\n            }\n\n            message.success(msg)\n\n            acForm?.current?.reload()\n\n        } catch (e) {\n            system.error(e)\n        }\n\n    }\n\n    /**\n     * 取消Modal的显示\n     */\n    const handleCancel = () => {\n        setModal({title: \"\", visible: false})\n\n        form.resetFields()\n\n    }\n\n    /**\n     * 保存${functionName}数据\n     */\n    const onSave = () => {\n        form\n            .validateFields()\n            .then(\n                async (fields) => {\n                    try {\n                        // 开启加载中\n                        setLoadingModal(true)\n\n                        #if($table.sub)\n                            fields.${subclassName}List = dataSource\n                        #end\n\n                        // ID为0则insert，否则将update\n                        const {\n                            code,\n                            msg\n                        } = fields.${pkColumn.javaField} === 0 ? await add${BusinessName}(fields) : await update${BusinessName}(fields)\n                        if (code !== 200) {\n                            return message.error(msg)\n                        }\n\n                        message.success(msg)\n\n                        if (acForm.current) {\n                            acForm.current.reload()\n                        }\n\n                        // 关闭模态框\n                        handleCancel()\n                    } catch (e) {\n                        system.error(e)\n                    } finally {\n                        setLoadingModal(false)\n                    }\n                }).catch(e => {\n            system.error(e)\n        })\n    }\n\n    // Form参数\n    const columns: ProColumns<any>[] = [\n        #foreach($column in $columns)\n            #set($javaField=$column.javaField)\n            #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n            #if($parentheseIndex != -1)\n                #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n            #else\n                #set($comment=$column.columnComment)\n            #end\n            #if($column.pk)\n            #elseif($column.list && $column.htmlType == \"datetime\")\n                {\n                    title: \'${comment}\', ellipsis: true, width: 200, valueType: \"dateTime\", dataIndex: \'${javaField}\',\n                },\n            #elseif($column.list && \"\" != $javaField)\n                {\n                    title: \'${comment}\', ellipsis: true, width: 200, valueType: \"text\", dataIndex: \'${javaField}\',\n                },\n            #end\n        #end\n        {\n            title: \'操作\',\n            fixed: \'right\',\n            width: 320,\n            valueType: \"option\",\n            dataIndex: \'operation\',\n            render: (_, row) => {\n                return (\n                    <>\n                        <a onClick={() => refPut(row)}>\n                            <Space>\n                                <EditOutlined/>\n                                {useIntl().formatMessage({id: \'system.update\'})}\n                            </Space>\n                        </a>\n\n                        <Divider type=\"vertical\"/>\n\n                        <Popconfirm\n                            onConfirm={() => refRemove(row.${pkColumn.javaField})}\n                            title=\"确定删除吗\"\n                        >\n                            <a href=\'#\'>\n                                <Space>\n                                    <CloseOutlined/>\n                                    {useIntl().formatMessage({id: \'system.delete\'})}\n                                </Space>\n                            </a>\n                        </Popconfirm>\n                    </>\n                )\n            }\n        },\n    ]\n\n    return (\n        <PageContainer>\n            <ProTable\n                {...proTableConfigs}\n                pagination={{\n                    // 是否允许每页大小更改\n                    showSizeChanger: true,\n                    // 每页显示条数\n                    defaultPageSize: state.pageSize,\n                }}\n                actionRef={acForm}\n                formRef={formRef}\n                rowKey=\"${pkColumn.javaField}\"\n                columns={columns}\n                request={async (params) => {\n                    const {data} = await pageQuery${BusinessName}(params);\n                    const {records, total} = data;\n                    return Promise.resolve({\n                        data: records,\n                        success: true,\n                        total,\n                    });\n                }}\n                rowSelection={{}}\n                toolBarRender={(action, {selectedRowKeys}) => [\n                    <Button key=\'add\' type=\"default\" onClick={refPost}>\n                        <PlusOutlined/>\n                        {useIntl().formatMessage({id: \'system.add\'})}\n                    </Button>,\n                    <Popconfirm\n                        key=\'batchDelete\'\n                        disabled={!(selectedRowKeys && selectedRowKeys.length > 0)}\n                        onConfirm={() => refRemove(selectedRowKeys)}\n                        title=\"是否删除选中数据\"\n                    >\n                        <Button\n                            disabled={!(selectedRowKeys && selectedRowKeys.length > 0)}\n                            type=\"primary\" danger\n                        >\n                            <DeleteOutlined/>\n                            {useIntl().formatMessage({id: \'system.delete.batch\'})}\n                        </Button>\n                    </Popconfirm>,\n                    <Popconfirm\n                        key=\'export\'\n                        title=\"是否导出数据\"\n                        onConfirm={() => {\n                            export${BusinessName}({\n                                ...formRef.current?.getFieldsValue()\n                            })\n                        }}\n                    >\n                        <Button type=\"default\">\n                            <FundProjectionScreenOutlined/>\n                            {useIntl().formatMessage({id: \'system.export\'})}\n                        </Button>\n                    </Popconfirm>\n                ]}\n\n            />\n\n            <Modal\n                title={`${modal.title}${functionName}`}\n                open={modal.visible}\n                okText={`${modal.title}`}\n                confirmLoading={loadingModal}\n                onOk={onSave}\n                onCancel={handleCancel}\n                // 销毁组件，要求重新装载\n                destroyOnClose\n            >\n\n                <Form\n                    form={form}\n                >\n                    <Form.Item\n                        hidden\n                        {...formItemLayout}\n                        label=\"主键\"\n                        name=\"${pkColumn.javaField}\"\n                        initialValue={0}\n                    >\n                        <Input/>\n                    </Form.Item>\n                    #foreach($column in $columns)\n                        #set($field=$column.javaField)\n                        #if($column.insert && !$column.pk)\n                            #if(($column.usableColumn) || (!$column.superColumn))\n                                #if($column.isRequired)\n                                    #set($isRequired=true)\n                                #else\n                                    #set($isRequired=false)\n                                #end\n\n                                #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n                                #if($parentheseIndex != -1)\n                                    #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n                                #else\n                                    #set($comment=$column.columnComment)\n                                #end\n                                #set($dictType=$column.dictType)\n                                #if($column.htmlType == \"input\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Input/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"imageUpload\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'请上传${comment}\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <UploadTWT\n                                            name=\'file\'\n                                            maxCount={1}\n                                            // 开启图片剪裁\n                                            imgCrop={true}\n                                            action={TWT.uploadUrl}\n                                        />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"fileUpload\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'请上传${comment}\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <UploadTWT\n                                            name=\'file\'\n                                            maxCount={1}\n                                            listType={\'picture\'}\n                                            action={TWT.uploadUrl}\n                                            title={<Button> 上传文件</Button>}\n                                        />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"editor\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Upload\n                                            name=\'file\'\n                                            headers={{\n                                                Authorization: `Bearer ${token.access_token}`\n                                            }}\n                                            maxCount={1}\n                                        >\n                                            <Button>上传文件</Button>\n                                        </Upload>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"select\" && $dictType)\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesSelect type=\'${dictType}\'/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"checkbox\" && $dictType)\n                                    <Form.Item\n                                        // 格式数组\n                                        getValueFromEvent={(value) => {\n                                            return value.join(\',\')\n                                        }}\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesCheckbox type=\'${dictType}\'/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"radio\" && $dictType)\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesRadio type=\'${dictType}\'/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"datetime\")\n                                    <Form.Item\n                                        // 格式化时间\n                                        getValueFromEvent={(date) => {\n                                            return moment(date).valueOf()\n                                        }}\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DatePickerTWT showTime/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"textarea\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Input.TextArea/>\n                                    </Form.Item>\n                                #end\n                            #end\n                        #end\n                    #end\n\n                </Form>\n\n                #if($table.sub)\n                    <Divider>${subTable.functionName}信息</Divider>\n\n                    <EditableProTable\n                        scroll={{x: \'x-content\'}}\n                        columns={columnsEdit}\n                        rowKey=\"id\"\n                        recordCreatorProps={{\n                            newRecordType: \'dataSource\',\n                            record: () => ({\n                                id: Date.now(),\n                            }),\n                        }}\n                        editable={{\n                            form: tableForm,\n                            type: \'multiple\',\n                            actionRender: (row, config, defaultDoms) => {\n                                return [defaultDoms.delete];\n                            },\n                            onValuesChange: (record, recordList: any) => {\n                                setDataSource(recordList);\n                            },\n                        }}\n                    />\n                #end\n\n            </Modal>\n        </PageContainer>\n    )\n\n}\n\nexport default ${BusinessName}\n','2023-11-09 15:01:11','2024-04-03 09:36:34','0','admin','admin'),(10,'React 请求API','${frontendPath}/react/pages/${moduleName}/${businessName}/service.ts','React 请求API','import {request} from \'@umijs/max\'\nimport {download} from \'@/utils/twelvet\'\n\n// 请求的控制器名称\nconst controller = \"/${moduleName}/${businessName}\";\n\n/**\n * 查询${functionName}列表\n * @param query 查询参数\n */\nexport async function pageQuery${BusinessName}(query: { [key: string]: any }) {\n    return request(`${controller}/pageQuery`, {\n        method: `get`,\n        params: query\n    })\n}\n\n/**\n * 查询${functionName}详细\n * @param 主键\n */\nexport async function get${BusinessName}(${pkColumn.javaField}: string | number) {\n    return request(`${controller}/${${pkColumn.javaField}}`, {\n        method: `get`\n    })\n}\n\n/**\n * 新增${functionName}\n * @param data 数据参数\n */\nexport async function add${BusinessName}(data: { [key: string]: any }) {\n    return request(`${controller}`, {\n        method: `post`,\n        data: data\n    })\n}\n\n/**\n * 修改${functionName}\n * @param data 数据参数\n */\nexport async function update${BusinessName}(data: { [key: string]: any }) {\n    return request(`${controller}`, {\n        method: `put`,\n        data: data\n    })\n}\n\n/**\n * 删除${functionName}\n * @param 主键\n */\nexport async function del${BusinessName}(${pkColumn.javaField}: string | number) {\n    return request(`${controller}/${${pkColumn.javaField}}`, {\n        method: `delete`\n    })\n}\n\n/**\n * 导出数据\n * @param params\n */\nexport async function export${BusinessName}(params?: { [key: string]: any }) {\n    return download(`${controller}/export`, params);\n}','2023-11-09 15:01:40','2023-11-09 15:02:09','0','admin','admin'),(11,'index-tree','${frontendPath}/react/pages/${moduleName}/${businessName}/index.tsx','树形模板','import React, {useState, useRef} from \'react\'\nimport { useIntl } from \'@umijs/max\'\nimport {PageContainer, ProTable} from \'@ant-design/pro-components\'\nimport type {ActionType, ProColumns} from \'@ant-design/pro-components\'\nimport {\n    DeleteOutlined,\n    FundProjectionScreenOutlined,\n    PlusOutlined,\n    EditOutlined,\n    CloseOutlined\n} from \'@ant-design/icons\'\nimport {Popconfirm, Button, message, Modal, Form, Input, Space, Divider, TreeSelect} from \'antd\'\nimport {FormInstance} from \'antd/lib/form\'\n#foreach($column in $columns)\n    #if($column.htmlType == \"select\")\n    import DictionariesSelect from \'@/components/TwelveT/Dictionaries/DictionariesSelect/Index\'\n        #break\n    #end\n#end\n#foreach($column in $columns)\n    #if($column.htmlType == \"radio\")\n    import DictionariesRadio from \'@/components/TwelveT/Dictionaries/DictionariesRadio/Index\'\n        #break\n    #end\n#end\n#foreach($column in $columns)\n    #if($column.htmlType == \"checkbox\")\n    import DictionariesCheckbox from \'@/components/TwelveT/Dictionaries/DictionariesCheckbox/Index\'\n        #break\n    #end\n#end\nimport {\n    pageQuery${BusinessName},\n    get${BusinessName},\n    del${BusinessName},\n    add${BusinessName},\n    update${BusinessName},\n    export${BusinessName}\n} from \'./service\'\nimport {makeTree, system} from \'@/utils/twelvet\'\nimport {isArray} from \'lodash\'\n#set($importTwT = false)\n#foreach($column in $columns)\n    #if($column.htmlType == \"imageUpload\" || $column.htmlType == \"fileUpload\")\n        #set($importTwT = true)\n    import UploadTWT from \'@/components/TwelveT/Upload\'\n        #break\n    #end\n#end\n#if($importTwT)\nimport TWT, {proTableConfigs} from \'@/setting\'\n#else\nimport {proTableConfigs} from \'@/setting\'\n#end\n#foreach($column in $columns)\n    #if($column.htmlType == \"datetime\")\n    import DatePickerTWT from \'@/components/TwelveT/DatePicker/index\'\n    import moment from \'moment\'\n        #break\n    #end\n#end\n#if($table.sub)\nimport {EditableProTable} from \'@ant-design/pro-components\'\n#end\n\n/**\n * ${functionName}模块\n */\nconst ${BusinessName}: React.FC = () => {\n\n    const {formatMessage} = useIntl()\n\n    const [state] = useState<{\n        pageSize: number\n    }>({\n        pageSize: 10,\n    });\n\n    // 显示Modal\n    const [modal, setModal] = useState<{ title: string, visible: boolean }>({title: ``, visible: false})\n\n    // 是否执行Modal数据操作中\n    const [loadingModal, setLoadingModal] = useState<boolean>(false)\n\n    const acForm = useRef<ActionType>()\n\n    const formRef = useRef<FormInstance>()\n\n    const [form] = Form.useForm()\n\n    const formItemLayout = {\n        labelCol: {\n            xs: {span: 4},\n            sm: {span: 4},\n        },\n        wrapperCol: {\n            xs: {span: 18},\n            sm: {span: 18},\n        },\n    }\n\n    const [${businessName}Options, set${BusinessName}Options] = useState<any[]>()\n\n    #if($table.sub)\n\n        const [tableForm] = Form.useForm<FormInstance>()\n        const [dataSource, setDataSource] = useState<[]>([])\n\n        const columnsEdit: ProColumns = [\n            #foreach($column in $subTable.columns)\n                #set($javaField=$column.javaField)\n                #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n                #if($parentheseIndex != -1)\n                    #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n                #else\n                    #set($comment=$column.columnComment)\n                #end\n                #if($column.pk)\n                #else\n                    {\n                        title: \'${comment}\', ellipsis: true, width: 100, valueType: \"text\", dataIndex: \'${javaField}\',\n                    },\n                #end\n            #end\n            {\n                title: \'操作\', fixed: \'right\', width: 80, valueType: \"option\", dataIndex: \'operation\'\n            },\n        ]\n    #end\n\n    /** 查询下拉树结构 */\n    const getTreeselect = async () => {\n\n        try {\n            const {code, msg, data} = await list${BusinessName}({})\n            if (code !== 200) {\n                return message.error(msg)\n            }\n\n            const tree = [{\n                key: 0,\n                title: \'顶级节点\',\n                value: 0,\n                children: makeTree({\n                    dataSource: data.records,\n                    id: `${treeCode}`,\n                    parentId: \'${treeParentCode}\',\n                    rootId: false,\n                    enhance: {\n                        key: `${treeCode}`,\n                        title: `${treeCode}`,\n                        value: `${treeCode}`\n                    }\n                })\n            }]\n            set${BusinessName}Options(tree)\n        } catch (e) {\n            system.error(e)\n        }\n    }\n\n    /**\n     * 新增${functionName}数据\n     * @param row row\n     */\n    const refPost = async () => {\n        setModal({title: formatMessage({id: \'system.add\'}), visible: true})\n        getTreeselect()\n    }\n\n    /**\n     * 获取修改${functionName}信息\n     * @param row row\n     */\n    const refPut = async (row: { [key: string]: any }) => {\n        try {\n            const {code, msg, data} = await get${BusinessName}(row.${pkColumn.javaField})\n            if (code !== 200) {\n                return message.error(msg)\n            }\n\n            #if($table.sub)\n                setDataSource(data.${subclassName}List)\n            #end\n\n            // 赋值表单数据\n            form.setFieldsValue(data)\n\n            getTreeselect()\n\n            // 设置Modal状态\n            setModal({title: formatMessage({id: \'system.update\'}), visible: true})\n\n        } catch (e) {\n            system.error(e)\n        }\n    }\n\n    /**\n     * 移除${functionName}数据\n     * @param row ${pkColumn.javaField}\n     */\n    const refRemove = async (${pkColumn.javaField}: (string | number)[] | string | undefined) => {\n        try {\n            if (!${pkColumn.javaField}) {\n                return true\n            }\n\n            let params\n            if (isArray(${pkColumn.javaField})) {\n                params = ${pkColumn.javaField}.join(\",\")\n            } else {\n                params = ${pkColumn.javaField}\n            }\n\n            const {code, msg} = await del${BusinessName}(params)\n\n            if (code !== 200) {\n                return message.error(msg)\n            }\n\n            message.success(msg)\n\n            acForm?.current?.reload()\n\n        } catch (e) {\n            system.error(e)\n        }\n\n    }\n\n    /**\n     * 取消Modal的显示\n     */\n    const handleCancel = () => {\n        setModal({title: \"\", visible: false})\n\n        form.resetFields()\n\n    }\n\n    /**\n     * 保存${functionName}数据\n     */\n    const onSave = () => {\n        form\n            .validateFields()\n            .then(\n                async (fields) => {\n                    try {\n                        // 开启加载中\n                        setLoadingModal(true)\n\n                        #if($table.sub)\n                            fields.${subclassName}List = dataSource\n                        #end\n\n                        // ID为0则insert，否则将update\n                        const {\n                            code,\n                            msg\n                        } = fields.${pkColumn.javaField} === 0 ? await add${BusinessName}(fields) : await update${BusinessName}(fields)\n                        if (code !== 200) {\n                            return message.error(msg)\n                        }\n\n                        message.success(msg)\n\n                        if (acForm.current) {\n                            acForm.current.reload()\n                        }\n\n                        // 关闭模态框\n                        handleCancel()\n                    } catch (e) {\n                        system.error(e)\n                    } finally {\n                        setLoadingModal(false)\n                    }\n                }).catch(e => {\n            system.error(e)\n        })\n    }\n\n    // Form参数\n    const columns: ProColumns<any>[] = [\n        #foreach($column in $columns)\n            #set($javaField=$column.javaField)\n            #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n            #if($parentheseIndex != -1)\n                #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n            #else\n                #set($comment=$column.columnComment)\n            #end\n            #if($column.pk)\n            #elseif($column.list && $column.htmlType == \"datetime\")\n                {\n                    title: \'${comment}\', ellipsis: true, width: 200, valueType: \"dateTime\", dataIndex: \'${javaField}\',\n                },\n            #elseif($column.list && \"\" != $javaField)\n                {\n                    title: \'${comment}\', ellipsis: true, width: 200, valueType: \"text\", dataIndex: \'${javaField}\',\n                },\n            #end\n        #end\n        {\n            title: \'操作\',\n            fixed: \'right\',\n            width: 320,\n            valueType: \"option\",\n            dataIndex: \'operation\',\n            render: (_, row) => {\n                return (\n                    <>\n                        <a onClick={() => refPut(row)}>\n                            <Space>\n                                <EditOutlined/>\n                                {useIntl().formatMessage({id: \'system.update\'})}\n                            </Space>\n                        </a>\n\n                        <Divider type=\"vertical\"/>\n\n                        <Popconfirm\n                            onConfirm={() => refRemove(row.${pkColumn.javaField})}\n                            title=\"确定删除吗\"\n                        >\n                            <a href=\'#\'>\n                                <Space>\n                                    <CloseOutlined/>\n                                    {useIntl().formatMessage({id: \'system.delete\'})}\n                                </Space>\n                            </a>\n                        </Popconfirm>\n                    </>\n                )\n            }\n        },\n    ]\n\n    return (\n        <PageContainer>\n            <ProTable\n                {...proTableConfigs}\n                pagination={{\n                    // 是否允许每页大小更改\n                    showSizeChanger: true,\n                    // 每页显示条数\n                    defaultPageSize: state.pageSize,\n                }}\n                actionRef={acForm}\n                formRef={formRef}\n                rowKey=\"${pkColumn.javaField}\"\n                columns={columns}\n                request={async (params) => {\n                    const {data} = await list${BusinessName}(params);\n                    const {records, total} = data;\n                    return Promise.resolve({\n                        data: records,\n                        success: true,\n                        total,\n                    });\n                }}\n                rowSelection={{}}\n                toolBarRender={(action, {selectedRowKeys}) => [\n                    <Button key=\'add\' type=\"default\" onClick={refPost}>\n                        <PlusOutlined/>\n                        新增\n                    </Button>,\n                    <Popconfirm\n                        key=\'batchDelete\'\n                        disabled={!(selectedRowKeys && selectedRowKeys.length > 0)}\n                        onConfirm={() => refRemove(selectedRowKeys)}\n                        title=\"是否删除选中数据\"\n                    >\n                        <Button\n                            disabled={!(selectedRowKeys && selectedRowKeys.length > 0)}\n                            type=\"primary\" danger\n                        >\n                            <DeleteOutlined/>\n                            {useIntl().formatMessage({id: \'system.delete.batch\'})}\n                        </Button>\n                    </Popconfirm>,\n                    <Popconfirm\n                        key=\'export\'\n                        title=\"是否导出数据\"\n                        onConfirm={() => {\n                            export${BusinessName}({\n                                ...formRef.current?.getFieldsValue()\n                            })\n                        }}\n                    >\n                        <Button type=\"default\">\n                            <FundProjectionScreenOutlined/>\n                            {useIntl().formatMessage({id: \'system.export\'})}\n                        </Button>\n                    </Popconfirm>\n                ]}\n\n            />\n\n            <Modal\n                title={`${modal.title}${functionName}`}\n                open={modal.visible}\n                okText={`${modal.title}`}\n                confirmLoading={loadingModal}\n                onOk={onSave}\n                onCancel={handleCancel}\n                // 销毁组件，要求重新装载\n                destroyOnClose\n            >\n\n                <Form\n                    form={form}\n                >\n                    <Form.Item\n                        hidden\n                        {...formItemLayout}\n                        label=\"主键\"\n                        name=\"${pkColumn.javaField}\"\n                        initialValue={0}\n                    >\n                        <Input/>\n                    </Form.Item>\n                    #foreach($column in $columns)\n                        #set($field=$column.javaField)\n                        #if($column.insert && !$column.pk)\n                            #if(($column.usableColumn) || (!$column.superColumn))\n                                #if($column.isRequired)\n                                    #set($isRequired=true)\n                                #else\n                                    #set($isRequired=false)\n                                #end\n\n                                #set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n                                #if($parentheseIndex != -1)\n                                    #set($comment=$column.columnComment.substring(0, $parentheseIndex))\n                                #else\n                                    #set($comment=$column.columnComment)\n                                #end\n                                #set($dictType=$column.dictType)\n                                #if(\"\" != $treeParentCode && $column.javaField == $treeParentCode)\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <TreeSelect\n                                            placeholder={\"请选择${comment}\"}\n                                            showSearch\n                                            treeLine\n                                            treeNodeFilterProp=\"title\"\n                                            treeData={${businessName}Options}\n                                        />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"input\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Input placeholder={\"请输入${comment}\"}/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"imageUpload\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'请上传${comment}\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <UploadTWT\n                                            name=\'file\'\n                                            maxCount={1}\n                                            // 开启图片剪裁\n                                            imgCrop={true}\n                                            action={TWT.uploadUrl}\n                                        />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"fileUpload\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'请上传${comment}\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <UploadTWT\n                                            name=\'file\'\n                                            maxCount={1}\n                                            listType={\'picture\'}\n                                            action={TWT.uploadUrl}\n                                            title={<Button> 上传文件</Button>}\n                                        />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"editor\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Upload\n                                            name=\'file\'\n                                            headers={{\n                                                Authorization: `Bearer ${token.access_token}`\n                                            }}\n                                            maxCount={1}\n                                        >\n                                            <Button>上传文件</Button>\n                                        </Upload>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"select\" && $dictType)\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesSelect type=\'${dictType}\' />\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"checkbox\" && $dictType)\n                                    <Form.Item\n                                        // 格式数组\n                                        getValueFromEvent={(value) => {\n                                            return value.join(\',\')\n                                        }}\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesCheckbox type=\'${dictType}\'/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"radio\" && $dictType)\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        name=\"${field}\"\n                                    >\n                                        <DictionariesRadio type=\'${dictType}\'/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"datetime\")\n                                    <Form.Item\n                                        // 格式化时间\n                                        getValueFromEvent={(date) => {\n                                            return moment(date).valueOf()\n                                        }}\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <DatePickerTWT showTime/>\n                                    </Form.Item>\n                                #elseif($column.htmlType == \"textarea\")\n                                    <Form.Item\n                                        {...formItemLayout}\n                                        label=\"${comment}\"\n                                        rules={[{required: ${isRequired}, message: \'${comment}不能为空\'}]}\n                                        name=\"${field}\"\n                                    >\n                                        <Input.TextArea/>\n                                    </Form.Item>\n                                #end\n                            #end\n                        #end\n                    #end\n\n                </Form>\n\n            </Modal>\n        </PageContainer>\n    )\n\n}\n\nexport default ${BusinessName}\n','2023-11-10 11:14:20','2024-04-03 09:38:18','0','admin','admin'),(12,'sub-domain','${backendPath}/src/main/java/${packagePath}/domain/${subClassName}.java','链表实体','package ${packageName}.domain;\n\n    #foreach ($import in $subImportList)\n    import ${import};\n    #end\nimport com.twelvet.framework.core.annotation.Excel;\nimport com.twelvet.framework.jdbc.web.domain.BaseEntity;\n\n/**\n * ${subTable.functionName}对象 ${subTableName}\n *\n * @author ${author}\n * @date ${datetime}\n */\npublic class ${subClassName} extends BaseEntity\n{\n    private static final long serialVersionUID = 1L;\n\n#foreach ($column in $subTable.columns)\n#if(!$table.isSuperColumn($column.javaField))\n    /** $column.columnComment */\n#if($column.list)\n#set($parentheseIndex=$column.columnComment.indexOf(\"（\"))\n#if($parentheseIndex != -1)\n#set($comment=$column.columnComment.substring(0, $parentheseIndex))\n#else\n#set($comment=$column.columnComment)\n#end\n#if($parentheseIndex != -1)\n    @ExcelProperty(value =\"${comment}($column.readConverterExp())\")\n#elseif($column.javaType == \'Date\')\n    @JsonFormat(pattern = \"yyyy-MM-dd\")\n    @ExcelProperty(value =\"${comment}\", width = 30, dateFormat = \"yyyy-MM-dd\")\n#else\n    @ExcelProperty(value =\"${comment}\")\n#end\n#end\n    private $column.javaType $column.javaField;\n\n#end\n#end\n#foreach ($column in $subTable.columns)\n#if(!$table.isSuperColumn($column.javaField))\n#if($column.javaField.length() > 2 && $column.javaField.substring(1,2).matches(\"[A-Z]\"))\n#set($AttrName=$column.javaField)\n#else\n#set($AttrName=$column.javaField.substring(0,1).toUpperCase() + ${column.javaField.substring(1)})\n#end\n    public void set${AttrName}($column.javaType $column.javaField)\n    {\n        this.$column.javaField = $column.javaField;\n    }\n\n    public $column.javaType get${AttrName}()\n    {\n        return $column.javaField;\n    }\n#end\n#end\n\n    @Override\n    public String toString() {\n        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)\n#foreach ($column in $subTable.columns)\n#if($column.javaField.length() > 2 && $column.javaField.substring(1,2).matches(\"[A-Z]\"))\n#set($AttrName=$column.javaField)\n#else\n#set($AttrName=$column.javaField.substring(0,1).toUpperCase() + ${column.javaField.substring(1)})\n#end\n            .append(\"${column.javaField}\", get${AttrName}())\n#end\n            .toString();\n    }\n}\n','2023-11-10 11:14:47','2026-10-21 14:16:38','0','admin','admin');
/*!40000 ALTER TABLE `gen_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_template_group`
--

DROP TABLE IF EXISTS `gen_template_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_template_group` (
  `group_id` bigint NOT NULL COMMENT '分组id',
  `template_id` bigint NOT NULL COMMENT '模板id',
  PRIMARY KEY (`group_id`,`template_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模板分组关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_template_group`
--

LOCK TABLES `gen_template_group` WRITE;
/*!40000 ALTER TABLE `gen_template_group` DISABLE KEYS */;
INSERT INTO `gen_template_group` VALUES (1,1),(1,2),(1,3),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(2,1),(2,2),(2,3),(2,5),(2,6),(2,7),(2,8),(2,9),(2,10),(2,12),(3,1),(3,2),(3,3),(3,5),(3,6),(3,7),(3,8),(3,10),(3,11);
/*!40000 ALTER TABLE `gen_template_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `i18n`
--

DROP TABLE IF EXISTS `i18n`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `i18n` (
  `i18n_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '唯一Code',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '语言类型：zh_CN,en_US...',
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '翻译值',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`i18n_id`) USING BTREE,
  UNIQUE KEY `un_code_type` (`code`,`type`) USING BTREE COMMENT 'Code翻译唯一'
) ENGINE=InnoDB AUTO_INCREMENT=209 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='国际化表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `i18n`
--

LOCK TABLES `i18n` WRITE;
/*!40000 ALTER TABLE `i18n` DISABLE KEYS */;
INSERT INTO `i18n` VALUES (1,'system.menu.index','zh_CN','欢迎页','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','欢迎页'),(2,'system.menu.system','zh_CN','系统管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','系统管理'),(3,'system.menu.monitor','zh_CN','系统监控','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','系统监控'),(4,'system.menu.human','zh_CN','人力管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','人力管理'),(5,'system.menu.log','zh_CN','日志管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志管理'),(6,'system.menu.tool','zh_CN','工具箱','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','工具箱'),(7,'system.menu.system.menu','zh_CN','菜单管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单管理'),(8,'system.menu.system.dictionaries','zh_CN','字典管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典管理'),(9,'system.menu.system.client','zh_CN','Oauth2终端','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','Oauth2终端'),(10,'system.menu.system.dfs','zh_CN','文件管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','文件管理'),(11,'system.menu.system.token','zh_CN','令牌管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','令牌管理'),(12,'system.menu.system.i18n','zh_CN','国际化','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化'),(13,'system.menu.monitor.redis','zh_CN','缓存监控','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','缓存监控'),(14,'system.menu.monitor.job','zh_CN','定时任务','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','定时任务'),(15,'system.menu.human.team','zh_CN','职员管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','职员管理'),(16,'system.menu.human.role','zh_CN','角色管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色管理'),(17,'system.menu.human.dept','zh_CN','部门管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门管理'),(18,'system.menu.human.post','zh_CN','岗位管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位管理'),(19,'system.menu.log.operation','zh_CN','操作日志','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作日志'),(20,'system.menu.log.login','zh_CN','登录日志','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录日志'),(21,'system.menu.log.job','zh_CN','定时任务日志','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','定时任务日志'),(22,'system.menu.tool.gen','zh_CN','开发平台','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','开发平台'),(23,'system.menu.tool.gen.ds','zh_CN','数据源','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源'),(24,'system.menu.tool.gen.code','zh_CN','代码生成','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','代码生成'),(25,'system.menu.tool.gen.metadata','zh_CN','元数据管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','元数据管理'),(26,'system.menu.tool.gen.metadata.type','zh_CN','字段管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段管理'),(27,'system.menu.tool.gen.metadata.template','zh_CN','模板管理','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理'),(28,'system.menu.tool.gen.metadata.group','zh_CN','模板分组','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组'),(29,'system.menu.human.team.query','zh_CN','用户查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户查询'),(30,'system.menu.human.team.insert','zh_CN','用户新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户新增'),(31,'system.menu.human.team.update','zh_CN','用户修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户修改'),(32,'system.menu.human.team.remove','zh_CN','用户删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户删除'),(33,'system.menu.human.team.export','zh_CN','用户导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户导出'),(34,'system.menu.human.team.import','zh_CN','用户导入','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户导入'),(35,'system.menu.human.team.resetPwd','zh_CN','重置密码','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','重置密码'),(36,'system.menu.human.role.query','zh_CN','角色查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色查询'),(37,'system.menu.human.role.insert','zh_CN','角色新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色新增'),(38,'system.menu.human.role.update','zh_CN','角色修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色修改'),(39,'system.menu.human.role.remove','zh_CN','角色删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色删除'),(40,'system.menu.human.role.export','zh_CN','角色导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色导出'),(41,'system.menu.system.menu.query','zh_CN','菜单查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单查询'),(42,'system.menu.system.menu.insert','zh_CN','菜单新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单新增'),(43,'system.menu.system.menu.update','zh_CN','菜单修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单修改'),(44,'system.menu.system.menu.remove','zh_CN','菜单删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单删除'),(45,'system.menu.human.dept.query','zh_CN','部门查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门查询'),(46,'system.menu.human.dept.insert','zh_CN','部门新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门新增'),(47,'system.menu.human.dept.update','zh_CN','部门修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门修改'),(48,'system.menu.human.dept.remove','zh_CN','部门删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门删除'),(49,'system.menu.human.post.query','zh_CN','岗位查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位查询'),(50,'system.menu.human.post.insert','zh_CN','岗位新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位新增'),(51,'system.menu.human.post.update','zh_CN','岗位修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位修改'),(52,'system.menu.human.post.remove','zh_CN','岗位删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位删除'),(53,'system.menu.human.post.export','zh_CN','岗位导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位导出'),(54,'system.menu.system.dictionaries.query','zh_CN','字典查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典查询'),(55,'system.menu.system.dictionaries.insert','zh_CN','字典新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典新增'),(56,'system.menu.system.dictionaries.update','zh_CN','字典修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典修改'),(57,'system.menu.system.dictionaries.remove','zh_CN','字典删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典删除'),(58,'system.menu.system.dictionaries.export','zh_CN','字典导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典导出'),(59,'system.menu.system.client.query','zh_CN','终端查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端查询'),(60,'system.menu.system.client.insert','zh_CN','终端新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端新增'),(61,'system.menu.system.client.update','zh_CN','终端修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端修改'),(62,'system.menu.system.client.remove','zh_CN','终端删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端删除'),(63,'system.menu.monitor.job.query','zh_CN','任务查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务查询'),(64,'system.menu.monitor.job.insert','zh_CN','任务新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务新增'),(65,'system.menu.monitor.job.update','zh_CN','任务修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务修改'),(66,'system.menu.monitor.job.remove','zh_CN','任务删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务删除'),(67,'system.menu.log.operation.query','zh_CN','操作查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作查询'),(68,'system.menu.log.operation.remove','zh_CN','操作删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作删除'),(69,'system.menu.log.operation.export','zh_CN','日志导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(70,'system.menu.log.login.query','zh_CN','登录查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录查询'),(71,'system.menu.log.login.remove','zh_CN','登录删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录删除'),(72,'system.menu.log.login.export','zh_CN','日志导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(73,'system.menu.log.job.query','zh_CN','操作查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作查询'),(74,'system.menu.log.job.remove','zh_CN','操作删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作删除'),(75,'system.menu.log.job.export','zh_CN','日志导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(76,'system.menu.system.dfs.remove','zh_CN','DFS文件删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','DFS文件删除'),(77,'system.menu.tool.gen.code.query','zh_CN','生成查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成查询'),(78,'system.menu.tool.gen.code.edit','zh_CN','生成修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成修改'),(79,'system.menu.tool.gen.code.import','zh_CN','导入代码','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','导入代码'),(80,'system.menu.tool.gen.code.remove','zh_CN','生成删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成删除'),(81,'system.menu.tool.gen.code.preview','zh_CN','预览代码','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','预览代码'),(82,'system.menu.tool.gen.code.code','zh_CN','生成代码','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成代码'),(83,'system.menu.system.token.remove','zh_CN','强退用户','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','强退用户'),(84,'system.menu.tool.gen.ds.query','zh_CN','数据源查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源查询'),(85,'system.menu.tool.gen.ds.add','zh_CN','数据源新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源新增'),(86,'system.menu.tool.gen.ds.edit','zh_CN','数据源修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源修改'),(87,'system.menu.tool.gen.ds.remove','zh_CN','数据源删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源删除'),(88,'system.menu.tool.gen.metadata.type.query','zh_CN','字段类型管理查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理查询'),(89,'system.menu.tool.gen.metadata.type.add','zh_CN','字段类型管理新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理新增'),(90,'system.menu.tool.gen.metadata.type.edit','zh_CN','字段类型管理修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理修改'),(91,'system.menu.tool.gen.metadata.type.remove','zh_CN','字段类型管理删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理删除'),(92,'system.menu.tool.gen.metadata.group.query','zh_CN','模板分组查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组查询'),(93,'system.menu.tool.gen.metadata.group.add','zh_CN','模板分组新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组新增'),(94,'system.menu.tool.gen.metadata.group.edit','zh_CN','模板分组修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组修改'),(95,'system.menu.tool.gen.metadata.group.remove','zh_CN','模板分组删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组删除'),(96,'system.menu.tool.gen.metadata.template.query','zh_CN','模板管理查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理查询'),(97,'system.menu.tool.gen.metadata.template.add','zh_CN','模板管理新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理新增'),(98,'system.menu.tool.gen.metadata.template.edit','zh_CN','模板管理修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理修改'),(99,'system.menu.tool.gen.metadata.template.remove','zh_CN','模板管理删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理删除'),(100,'system.menu.system.i18n.query','zh_CN','国际化查询','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化查询'),(101,'system.menu.system.i18n.add','zh_CN','国际化新增','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化新增'),(102,'system.menu.system.i18n.edit','zh_CN','国际化修改','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化修改'),(103,'system.menu.system.i18n.remove','zh_CN','国际化删除','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化删除'),(104,'system.menu.system.i18n.export','zh_CN','国际化导出','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化导出'),(105,'system.menu.index','en_US','Welcome Page','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','欢迎页'),(106,'system.menu.system','en_US','System Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','系统管理'),(107,'system.menu.monitor','en_US','System Monitoring','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','系统监控'),(108,'system.menu.human','en_US','Human Resources Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','人力管理'),(109,'system.menu.log','en_US','Log Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志管理'),(110,'system.menu.tool','en_US','Toolbox','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','工具箱'),(111,'system.menu.system.menu','en_US','Menu Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单管理'),(112,'system.menu.system.dictionaries','en_US','Dictionary Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典管理'),(113,'system.menu.system.client','en_US','Oauth2 Terminal','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','Oauth2终端'),(114,'system.menu.system.dfs','en_US','File Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','文件管理'),(115,'system.menu.system.token','en_US','Token Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','令牌管理'),(116,'system.menu.system.i18n','en_US','Internationalization','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化'),(117,'system.menu.monitor.redis','en_US','Cache Monitoring','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','缓存监控'),(118,'system.menu.monitor.job','en_US','Scheduled Tasks','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','定时任务'),(119,'system.menu.human.team','en_US','Staff Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','职员管理'),(120,'system.menu.human.role','en_US','Role Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色管理'),(121,'system.menu.human.dept','en_US','Department Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门管理'),(122,'system.menu.human.post','en_US','Position Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位管理'),(123,'system.menu.log.operation','en_US','Operation Logs','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作日志'),(124,'system.menu.log.login','en_US','Login Logs','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录日志'),(125,'system.menu.log.job','en_US','Scheduled Task Logs','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','定时任务日志'),(126,'system.menu.tool.gen','en_US','Development Platform','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','开发平台'),(127,'system.menu.tool.gen.ds','en_US','Data Sources','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源'),(128,'system.menu.tool.gen.code','en_US','Code Generation','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','代码生成'),(129,'system.menu.tool.gen.metadata','en_US','Metadata Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','元数据管理'),(130,'system.menu.tool.gen.metadata.type','en_US','Field Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段管理'),(131,'system.menu.tool.gen.metadata.template','en_US','Template Management','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理'),(132,'system.menu.tool.gen.metadata.group','en_US','Template Groups','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组'),(133,'system.menu.human.team.query','en_US','User Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户查询'),(134,'system.menu.human.team.insert','en_US','User Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户新增'),(135,'system.menu.human.team.update','en_US','User Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户修改'),(136,'system.menu.human.team.remove','en_US','User Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户删除'),(137,'system.menu.human.team.export','en_US','User Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户导出'),(138,'system.menu.human.team.import','en_US','User Import','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','用户导入'),(139,'system.menu.human.team.resetPwd','en_US','Reset Password','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','重置密码'),(140,'system.menu.human.role.query','en_US','Role Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色查询'),(141,'system.menu.human.role.insert','en_US','Role Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色新增'),(142,'system.menu.human.role.update','en_US','Role Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色修改'),(143,'system.menu.human.role.remove','en_US','Role Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色删除'),(144,'system.menu.human.role.export','en_US','Role Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','角色导出'),(145,'system.menu.system.menu.query','en_US','Menu Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单查询'),(146,'system.menu.system.menu.insert','en_US','Menu Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单新增'),(147,'system.menu.system.menu.update','en_US','Menu Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单修改'),(148,'system.menu.system.menu.remove','en_US','Menu Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','菜单删除'),(149,'system.menu.human.dept.query','en_US','Department Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门查询'),(150,'system.menu.human.dept.insert','en_US','Department Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门新增'),(151,'system.menu.human.dept.update','en_US','Department Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门修改'),(152,'system.menu.human.dept.remove','en_US','Department Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','部门删除'),(153,'system.menu.human.post.query','en_US','Position Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位查询'),(154,'system.menu.human.post.insert','en_US','Position Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位新增'),(155,'system.menu.human.post.update','en_US','Position Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位修改'),(156,'system.menu.human.post.remove','en_US','Position Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位删除'),(157,'system.menu.human.post.export','en_US','Position Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','岗位导出'),(158,'system.menu.system.dictionaries.query','en_US','Dictionary Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典查询'),(159,'system.menu.system.dictionaries.insert','en_US','Dictionary Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典新增'),(160,'system.menu.system.dictionaries.update','en_US','Dictionary Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典修改'),(161,'system.menu.system.dictionaries.remove','en_US','Dictionary Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典删除'),(162,'system.menu.system.dictionaries.export','en_US','Dictionary Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字典导出'),(163,'system.menu.system.client.query','en_US','Terminal Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端查询'),(164,'system.menu.system.client.insert','en_US','Terminal Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端新增'),(165,'system.menu.system.client.update','en_US','Terminal Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端修改'),(166,'system.menu.system.client.remove','en_US','Terminal Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','终端删除'),(167,'system.menu.monitor.job.query','en_US','Task Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务查询'),(168,'system.menu.monitor.job.insert','en_US','Task Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务新增'),(169,'system.menu.monitor.job.update','en_US','Task Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务修改'),(170,'system.menu.monitor.job.remove','en_US','Task Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','任务删除'),(171,'system.menu.log.operation.query','en_US','Operation Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作查询'),(172,'system.menu.log.operation.remove','en_US','Operation Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作删除'),(173,'system.menu.log.operation.export','en_US','Log Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(174,'system.menu.log.login.query','en_US','Login Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录查询'),(175,'system.menu.log.login.remove','en_US','Login Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','登录删除'),(176,'system.menu.log.login.export','en_US','Log Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(177,'system.menu.log.job.query','en_US','Operation Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作查询'),(178,'system.menu.log.job.remove','en_US','Operation Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','操作删除'),(179,'system.menu.log.job.export','en_US','Log Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','日志导出'),(180,'system.menu.system.dfs.remove','en_US','DFS File Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','DFS文件删除'),(181,'system.menu.tool.gen.code.query','en_US','Generation Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成查询'),(182,'system.menu.tool.gen.code.edit','en_US','Generation Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成修改'),(183,'system.menu.tool.gen.code.import','en_US','Code Import','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','导入代码'),(184,'system.menu.tool.gen.code.remove','en_US','Generation Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成删除'),(185,'system.menu.tool.gen.code.preview','en_US','Code Preview','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','预览代码'),(186,'system.menu.tool.gen.code.code','en_US','Code Generation','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','生成代码'),(187,'system.menu.system.token.remove','en_US','Force Logout User','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','强退用户'),(188,'system.menu.tool.gen.ds.query','en_US','Data Source Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源查询'),(189,'system.menu.tool.gen.ds.add','en_US','Data Source Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源新增'),(190,'system.menu.tool.gen.ds.edit','en_US','Data Source Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源修改'),(191,'system.menu.tool.gen.ds.remove','en_US','Data Source Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','数据源删除'),(192,'system.menu.tool.gen.metadata.type.query','en_US','Field Type Management Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理查询'),(193,'system.menu.tool.gen.metadata.type.add','en_US','Field Type Management Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理新增'),(194,'system.menu.tool.gen.metadata.type.edit','en_US','Field Type Management Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理修改'),(195,'system.menu.tool.gen.metadata.type.remove','en_US','Field Type Management Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','字段类型管理删除'),(196,'system.menu.tool.gen.metadata.group.query','en_US','Template Group Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组查询'),(197,'system.menu.tool.gen.metadata.group.add','en_US','Template Group Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组新增'),(198,'system.menu.tool.gen.metadata.group.edit','en_US','Template Group Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组修改'),(199,'system.menu.tool.gen.metadata.group.remove','en_US','Template Group Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板分组删除'),(200,'system.menu.tool.gen.metadata.template.query','en_US','Template Management Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理查询'),(201,'system.menu.tool.gen.metadata.template.add','en_US','Template Management Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理新增'),(202,'system.menu.tool.gen.metadata.template.edit','en_US','Template Management Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理修改'),(203,'system.menu.tool.gen.metadata.template.remove','en_US','Template Management Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','模板管理删除'),(204,'system.menu.system.i18n.query','en_US','Internationalization Query','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化查询'),(205,'system.menu.system.i18n.add','en_US','Internationalization Addition','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化新增'),(206,'system.menu.system.i18n.edit','en_US','Internationalization Modification','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化修改'),(207,'system.menu.system.i18n.remove','en_US','Internationalization Deletion','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化删除'),(208,'system.menu.system.i18n.export','en_US','Internationalization Export','2024-03-31 11:32:29','admin','2024-03-31 11:32:29','国际化导出');
/*!40000 ALTER TABLE `i18n` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `im_block`
--

DROP TABLE IF EXISTS `im_block`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_block` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_user_id` bigint NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拉黑';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `im_block`
--

LOCK TABLES `im_block` WRITE;
/*!40000 ALTER TABLE `im_block` DISABLE KEYS */;
/*!40000 ALTER TABLE `im_block` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `im_conversation`
--

DROP TABLE IF EXISTS `im_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_conversation` (
  `conv_id` bigint NOT NULL AUTO_INCREMENT,
  `user_a_id` bigint NOT NULL,
  `user_b_id` bigint NOT NULL,
  `last_msg_id` bigint DEFAULT NULL,
  `last_msg_time` datetime DEFAULT NULL,
  `related_product_id` bigint DEFAULT NULL,
  `pinned_by_a` tinyint(1) DEFAULT '0',
  `pinned_by_b` tinyint(1) DEFAULT '0',
  `muted_by_a` tinyint(1) DEFAULT '0',
  `muted_by_b` tinyint(1) DEFAULT '0',
  `deleted_by_a` tinyint(1) DEFAULT '0',
  `deleted_by_b` tinyint(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`conv_id`),
  UNIQUE KEY `uk_tenant_ab` (`tenant_id`,`user_a_id`,`user_b_id`),
  KEY `idx_last_msg_time` (`last_msg_time`),
  KEY `idx_im_conv_tenant` (`tenant_id`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM会话';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `im_conversation`
--

LOCK TABLES `im_conversation` WRITE;
/*!40000 ALTER TABLE `im_conversation` DISABLE KEYS */;
INSERT INTO `im_conversation` VALUES (1,101,102,8,'2026-08-04 17:43:09',1,0,0,0,0,0,0,NULL,'2026-08-04 17:43:09',1),(18,123,125,62,'2026-09-06 10:38:29',NULL,0,0,0,0,0,0,'2026-09-06 10:38:29','2026-09-06 10:38:29',2),(19,101,104,66,'2026-10-25 20:02:24',NULL,0,0,0,0,0,0,'2026-10-25 17:02:24','2026-10-27 17:02:24',1),(20,102,104,68,'2026-10-26 18:02:24',NULL,0,0,0,0,0,0,'2026-10-26 17:02:24','2026-10-27 17:02:24',1);
/*!40000 ALTER TABLE `im_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `im_message`
--

DROP TABLE IF EXISTS `im_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_message` (
  `msg_id` bigint NOT NULL AUTO_INCREMENT,
  `conv_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'text/image/bargain/system/product_card',
  `content` text,
  `image_url` varchar(500) DEFAULT NULL,
  `bargain_id` bigint DEFAULT NULL,
  `product_card_id` bigint DEFAULT NULL,
  `system_card_json` text,
  `status` varchar(20) DEFAULT 'sent' COMMENT 'sending/sent/delivered/read/failed',
  `recalled` tinyint(1) DEFAULT '0',
  `create_time` datetime NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`msg_id`),
  KEY `idx_conv_time` (`conv_id`,`create_time`),
  KEY `idx_im_msg_tenant` (`tenant_id`,`conv_id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `im_message`
--

LOCK TABLES `im_message` WRITE;
/*!40000 ALTER TABLE `im_message` DISABLE KEYS */;
INSERT INTO `im_message` VALUES (1,1,101,102,'text','老板你好,这个iPhone还在吗?',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 09:02:11',1),(2,1,102,101,'text','在的,暗紫色256G,99新,电池88%。随时可以看机。',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 09:17:11',1),(3,1,101,102,'text','电池88%啊…价格还能少吗?',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 09:32:11',1),(4,1,102,101,'image','',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 10:02:11',1),(5,1,101,102,'text','求求你',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 12:59:16',1),(6,1,102,101,'text','这个价格真的很便宜了',NULL,NULL,NULL,NULL,'sent',0,'2026-06-08 13:00:32',1),(7,1,101,102,'text','你好',NULL,NULL,NULL,NULL,'sent',0,'2026-06-28 16:02:37',1),(8,1,101,102,'text','滚',NULL,NULL,NULL,NULL,'sent',0,'2026-08-04 17:43:09',1),(62,18,123,125,'system','9月5日thu社区日报｜今天很安静\n一句话总览 9月5日的thucommunity今天格外安静，没什么新动静，适合好好休息。 二手好物 今天没有新的闲置好物上架，有想出售的居民可以随时发帖，说不定…\n— 打开「AI 应用 → 社区日报」查看全文',NULL,NULL,NULL,NULL,'sent',0,'2026-09-06 10:38:29',2),(64,19,101,104,'text','你好，我买的订单一直显示待发货，能帮忙看下吗？',NULL,NULL,NULL,NULL,'sent',0,'2026-10-25 17:02:24',1),(65,19,104,101,'text','您好，刚帮您联系卖家了，今天下午会发出，请留意物流。',NULL,NULL,NULL,NULL,'sent',0,'2026-10-25 19:02:24',1),(66,19,101,104,'text','好的，谢谢！',NULL,NULL,NULL,NULL,'sent',0,'2026-10-25 20:02:24',1),(67,20,102,104,'text','我这边有两笔订单买家一直没付款，会自动关闭吗？',NULL,NULL,NULL,NULL,'sent',0,'2026-10-26 17:02:24',1),(68,20,104,102,'text','会的，30 分钟未支付系统会自动关闭订单，不影响您的店铺。',NULL,NULL,NULL,NULL,'sent',0,'2026-10-26 18:02:24',1);
/*!40000 ALTER TABLE `im_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `im_notice`
--

DROP TABLE IF EXISTS `im_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_notice` (
  `notice_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'order/platform/interact',
  `title` varchar(200) NOT NULL,
  `content` text,
  `ref_type` varchar(20) DEFAULT NULL,
  `ref_id` bigint DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`notice_id`),
  KEY `idx_user_read` (`user_id`,`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知中心';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `im_notice`
--

LOCK TABLES `im_notice` WRITE;
/*!40000 ALTER TABLE `im_notice` DISABLE KEYS */;
INSERT INTO `im_notice` VALUES (12,101,'system','退款成功','您的退款 ¥1107.00 已原路退回',NULL,NULL,0,'2026-10-30 18:31:55'),(13,101,'system','退款成功','您的退款 ¥2499.00 已原路退回',NULL,NULL,0,'2026-11-02 09:29:30'),(14,101,'system','退款成功','您的退款 ¥1.00 已原路退回',NULL,NULL,0,'2026-11-02 09:30:35'),(15,1,'order','支付成功','订单已支付，等待卖家发货',NULL,NULL,0,'2026-11-03 16:32:03'),(16,102,'order','买家已付款','请尽快发货',NULL,NULL,0,'2026-11-03 16:32:03'),(17,1,'order','支付时订单已关闭','您支付的款项将原路退回',NULL,NULL,0,'2026-11-03 16:34:06'),(18,1,'order','卖家已发货','物流公司：SF，单号：SF999',NULL,NULL,0,'2026-11-03 16:36:40'),(19,102,'order','买家已确认收货','订单已完成',NULL,NULL,0,'2026-11-03 16:36:51');
/*!40000 ALTER TABLE `im_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `im_unread`
--

DROP TABLE IF EXISTS `im_unread`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `im_unread` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `conv_id` bigint NOT NULL,
  `unread_count` int DEFAULT '0',
  `notice_unread` int DEFAULT '0',
  `last_read_msg_id` bigint NOT NULL DEFAULT '0' COMMENT '已读位点(最后已读 msg_id)，未读=会话内 msg_id 大于位点的他人消息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_conv` (`user_id`,`conv_id`)
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM未读';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `im_unread`
--

LOCK TABLES `im_unread` WRITE;
/*!40000 ALTER TABLE `im_unread` DISABLE KEYS */;
INSERT INTO `im_unread` VALUES (1,101,1,0,0,8),(8,102,1,0,0,8),(73,125,18,1,0,0),(74,123,18,0,0,62),(77,101,19,1,0,0),(78,104,19,0,0,0),(79,102,20,0,0,0),(80,104,20,1,0,0);
/*!40000 ALTER TABLE `im_unread` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `title` varchar(60) NOT NULL,
  `cover` varchar(500) DEFAULT '',
  `images` text COMMENT 'JSON数组',
  `price` decimal(10,2) NOT NULL,
  `original_price` decimal(10,2) DEFAULT NULL,
  `condition` varchar(20) DEFAULT '90' COMMENT 'brand_new/95/90/80/70_lower',
  `cat_l1` varchar(20) NOT NULL,
  `cat_l2` varchar(20) DEFAULT NULL,
  `shipping_method` varchar(20) DEFAULT 'express' COMMENT 'express/self_pickup/free',
  `shipping_fee` decimal(10,2) DEFAULT '0.00',
  `stock` int DEFAULT '1',
  `status` varchar(20) DEFAULT 'draft' COMMENT 'draft/auditing/onsale/locked/sold/offshelf/rejected',
  `description` text,
  `location` varchar(100) DEFAULT NULL,
  `city` varchar(20) DEFAULT NULL,
  `lat` decimal(10,6) DEFAULT NULL,
  `lng` decimal(10,6) DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `want_count` int DEFAULT '0',
  `chat_count` int DEFAULT '0',
  `hot_score` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '热度分(view+want*3+chat*2)/gravity衰减',
  `publish_time` datetime DEFAULT NULL,
  `offshelf_time` datetime DEFAULT NULL,
  `is_pinned` tinyint(1) DEFAULT '0',
  `audit_reject_reason` varchar(500) DEFAULT NULL,
  `purchase_date` varchar(10) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  PRIMARY KEY (`product_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_cat_l1` (`cat_l1`),
  KEY `idx_city` (`city`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_product_tenant` (`tenant_id`,`publish_time`),
  KEY `idx_hot_score` (`hot_score`),
  FULLTEXT KEY `ft_product_search` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,102,'iPhone 14 Pro 256G 暗紫色 99新','https://picsum.photos/seed/p1/600/600','[\"https://picsum.photos/seed/p1/600/600\",\"https://picsum.photos/seed/p1b/600/600\",\"https://picsum.photos/seed/p1c/600/600\"]',5999.00,8899.00,'99','digital','digital-phone','express',12.00,1,'onsale','自用8个月,无划痕无拆修,带原装盒+数据线,电池健康88%。支持线下看机验货。',NULL,'深圳',NULL,NULL,330,18,7,0.0000,'2026-06-05 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','','2026-10-30 18:31:55',NULL,1),(2,102,'宜家KALLAX卡莱克搁架单元4x2','https://picsum.photos/seed/p2/600/600','[\"https://picsum.photos/seed/p2/600/600\",\"https://picsum.photos/seed/p2b/600/600\"]',299.00,599.00,'90','home','home-furniture','self_pickup',0.00,1,'sold','使用1年,白色,表面无划痕,可拆装。建议福田区自提,太大不方便快递。',NULL,'深圳',NULL,NULL,156,12,5,0.0000,'2026-06-03 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','','2026-11-03 16:36:51',NULL,1),(3,102,'乐高机械组42143法拉利Daytona SP3 全新未拆','https://picsum.photos/seed/p3/600/600','[\"https://picsum.photos/seed/p3/600/600\",\"https://picsum.photos/seed/p3b/600/600\",\"https://picsum.photos/seed/p3c/600/600\"]',2499.00,3499.00,'brand_new','trendy','trendy-lego','express',0.00,1,'locked','朋友送的,家里没有地方拼,全新未拆封,盒子轻微压痕。3778颗粒,收藏级。',NULL,'深圳',NULL,NULL,245,22,9,0.0000,'2026-06-06 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','','2026-11-03 16:33:24',NULL,1),(4,102,'任天堂Switch OLED 红蓝 95新','https://picsum.photos/seed/p4/600/600','[\"https://picsum.photos/seed/p4/600/600\",\"https://picsum.photos/seed/p4b/600/600\"]',1499.00,2599.00,'95','digital','digital-tablet','express',12.00,1,'onsale','OLED版,购入半年,无划痕,配件齐全(底座+Joy-Con+原充)。送塞尔达卡带。',NULL,'深圳',NULL,NULL,422,56,15,0.0000,'2026-06-07 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(5,102,'戴森V12 Detect Slim无线吸尘器','https://picsum.photos/seed/p5/600/600','[\"https://picsum.photos/seed/p5/600/600\",\"https://picsum.photos/seed/p5b/600/600\"]',1899.00,3490.00,'90','home','home-appliance','express',15.00,1,'onsale','9成新,使用半年,可拆卸水洗过滤网,送激光探测头。功能完全正常。',NULL,'深圳',NULL,NULL,89,5,2,0.0000,'2026-06-04 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','','2026-11-02 09:30:35',NULL,1),(6,102,'Apple AirPods Pro 2 USB-C','https://picsum.photos/seed/p6/600/600','[\"https://picsum.photos/seed/p6/600/600\",\"https://picsum.photos/seed/p6b/600/600\"]',1099.00,1899.00,'95','digital','digital-accessory','express',8.00,1,'onsale','国行,2024年购入,仅拆封试戴,全新成色,送硅胶保护套。',NULL,'深圳',NULL,NULL,573,46,18,0.0000,'2026-06-07 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(7,102,'MacBook Air M2 星光色 8+256','https://picsum.photos/seed/p7/600/600','[\"https://picsum.photos/seed/p7/600/600\",\"https://picsum.photos/seed/p7b/600/600\",\"https://picsum.photos/seed/p7c/600/600\"]',6999.00,9499.00,'95','digital','digital-laptop','express',0.00,1,'onsale','2023款,循环次数76次,屏幕无划痕,键盘无油光,原装充电器+数据线。',NULL,'深圳',NULL,NULL,198,14,6,0.0000,'2026-06-02 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(8,102,'索尼WH-1000XM5 头戴降噪耳机 黑色','https://picsum.photos/seed/p8/600/600','[\"https://picsum.photos/seed/p8/600/600\",\"https://picsum.photos/seed/p8b/600/600\"]',1599.00,2799.00,'95','digital','digital-accessory','express',0.00,1,'onsale','自用3个月,音质出彩,降噪顶级。箱说全,带飞机转换头。',NULL,'深圳',NULL,NULL,346,28,11,0.0000,'2026-06-05 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(9,102,'大疆Mini 3 无人机 带屏遥控器版','https://picsum.photos/seed/p9/600/600','[\"https://picsum.photos/seed/p9/600/600\",\"https://picsum.photos/seed/p9b/600/600\",\"https://picsum.photos/seed/p9c/600/600\"]',2499.00,4298.00,'90','sport','sport-outdoor','express',15.00,1,'onsale','飞行累计8小时,无炸机无维修,含畅飞套装。送128G内存卡。',NULL,'深圳',NULL,NULL,178,13,7,0.0000,'2026-06-05 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(10,102,'三宅一生BAOBAO 6x6 银色','https://picsum.photos/seed/p10/600/600','[\"https://picsum.photos/seed/p10/600/600\",\"https://picsum.photos/seed/p10b/600/600\"]',899.00,1899.00,'95','fashion','fashion-bag','express',10.00,1,'onsale','日本购入,背过3次,无明显使用痕迹。带防尘袋+小票。',NULL,'深圳',NULL,NULL,204,9,4,0.0000,'2026-06-01 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(11,102,'Kindle Oasis 10代 8G','https://picsum.photos/seed/p11/600/600','[\"https://picsum.photos/seed/p11/600/600\",\"https://picsum.photos/seed/p11b/600/600\"]',899.00,2399.00,'90','digital','digital-tablet','express',8.00,1,'onsale','实体翻页键,冷暖光阅读灯,电池续航1周+,贴了磨砂膜。',NULL,'深圳',NULL,NULL,134,7,3,0.0000,'2026-06-03 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(12,102,'巴拉巴拉男童羽绒服 120码 白鸭绒','https://picsum.photos/seed/p12/600/600','[\"https://picsum.photos/seed/p12/600/600\",\"https://picsum.photos/seed/p12b/600/600\"]',39.00,299.00,'90','baby','baby-clothes','express',8.00,1,'onsale','孩子穿了一季就小了,洗过2次,无污渍,充绒量充足。适合4-5岁。',NULL,'深圳',NULL,NULL,68,3,1,0.0000,'2026-05-31 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(13,102,'iPad mini 6 紫色 64G WiFi版','https://picsum.photos/seed/p13/600/600','[\"https://picsum.photos/seed/p13/600/600\",\"https://picsum.photos/seed/p13b/600/600\",\"https://picsum.photos/seed/p13c/600/600\"]',2999.00,3799.00,'95','digital','digital-tablet','express',0.00,1,'onsale','2022款,A15芯片,支持Apple Pencil 2代。屏幕完美,电池87%。',NULL,'深圳',NULL,NULL,198,17,5,0.0000,'2026-06-06 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(14,102,'始祖鸟Atom LT 男士羽绒夹克 M码 黑','https://picsum.photos/seed/p14/600/600','[\"https://picsum.photos/seed/p14/600/600\",\"https://picsum.photos/seed/p14b/600/600\"]',3499.00,4500.00,'95','fashion','fashion-men','express',12.00,1,'onsale','2024款,C棉保暖,户外/日常都合适。就试穿,吊牌已剪。',NULL,'深圳',NULL,NULL,167,11,6,0.0000,'2026-06-02 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(15,102,'GoPro Hero 11 Black 运动相机','https://picsum.photos/seed/p15/600/600','[\"https://picsum.photos/seed/p15/600/600\",\"https://picsum.photos/seed/p15b/600/600\"]',1799.00,3198.00,'95','digital','digital-accessory','express',10.00,1,'onsale','用于拍摄了3次旅行,含防水壳+自拍杆+三块电池。',NULL,'深圳',NULL,NULL,145,8,3,0.0000,'2026-06-04 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(16,102,'JBL Charge 5 蓝牙音箱 蓝色','https://picsum.photos/seed/p16/600/600','[\"https://picsum.photos/seed/p16/600/600\",\"https://picsum.photos/seed/p16b/600/600\"]',899.00,1499.00,'90','digital','digital-accessory','express',10.00,1,'onsale','IP67防水,20小时续航,低音出色。表面无磕碰,1年使用。',NULL,'深圳',NULL,NULL,156,9,4,0.0000,'2026-06-05 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(17,102,'索尼A7M4全画幅微单相机 单机身','https://picsum.photos/seed/p17/600/600','[\"https://picsum.photos/seed/p17/600/600\",\"https://picsum.photos/seed/p17b/600/600\",\"https://picsum.photos/seed/p17c/600/600\"]',12999.00,16999.00,'90','digital','digital-laptop','express',20.00,1,'onsale','快门数约8000,3300万像素,4K60p视频。原厂电池*2+充电座。',NULL,'深圳',NULL,NULL,312,21,9,0.0000,'2026-06-03 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(18,102,'雷蛇黑寡妇V4 Pro 机械键盘 绿轴','https://picsum.photos/seed/p18/600/600','[\"https://picsum.photos/seed/p18/600/600\",\"https://picsum.photos/seed/p18b/600/600\"]',899.00,1499.00,'95','digital','digital-accessory','express',12.00,1,'onsale','雷蛇绿轴,RGB灯效,带掌托。购入半年,包装配件都在。',NULL,'深圳',NULL,NULL,100,7,2,0.0000,'2026-06-07 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(19,102,'罗技MX Master 3S 无线鼠标 石墨黑','https://picsum.photos/seed/p19/600/600','[\"https://picsum.photos/seed/p19/600/600\",\"https://picsum.photos/seed/p19b/600/600\"]',499.00,899.00,'95','digital','digital-accessory','express',8.00,1,'onsale','静音按键,8000DPI,USB-C充电。仅使用2个月,新机感觉。',NULL,'深圳',NULL,NULL,234,19,7,0.0000,'2026-06-06 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(20,102,'戴尔S2722QC 27寸4K显示器','https://picsum.photos/seed/p20/600/600','[\"https://picsum.photos/seed/p20/600/600\",\"https://picsum.photos/seed/p20b/600/600\"]',1999.00,3299.00,'95','digital','digital-laptop','express',30.00,1,'onsale','4K IPS面板,Type-C 65W供电,无坏点,支架升降旋转。',NULL,'深圳',NULL,NULL,87,7,3,0.0000,'2026-06-02 11:02:11',NULL,0,NULL,NULL,'','2026-06-08 11:02:11','',NULL,NULL,1),(21,101,'iPad Air 4 64G WiFi 版 95新','https://picsum.photos/seed/p21/600/600','[\"https://picsum.photos/seed/p21/600/600\"]',1899.00,4399.00,'90','digital','digital-tablet','express',0.00,1,'onsale','自用一年，屏幕无划痕，附原装充电器与保护壳。',NULL,'上海',NULL,NULL,0,1,0,0.0000,'2026-08-07 17:35:19',NULL,0,NULL,'2025年6月','','2026-08-04 17:01:32','','2026-08-04 17:01:32',NULL,1),(22,102,'小米13 8+256G 远山蓝','https://picsum.photos/seed/p22/600/600','[\"https://picsum.photos/seed/p22/600/600\"]',1299.00,3999.00,'new','digital','digital-phone','express',0.00,NULL,'auditing','自用一年，无磕碰，功能正常，附原装充电器。',NULL,'深圳',NULL,NULL,0,0,0,0.0000,'2026-08-28 17:10:26',NULL,0,NULL,NULL,'','2026-08-28 17:10:26','','2026-08-28 17:10:26',NULL,1),(23,102,'考研英语真题解析（2024版）',NULL,NULL,39.00,68.00,NULL,'books','books-education',NULL,NULL,1,'auditing','做过一半，笔记已擦除，适合二刷。',NULL,NULL,NULL,NULL,0,0,0,0.0000,'2026-08-28 21:48:44',NULL,0,NULL,NULL,'','2026-08-28 21:48:44','','2026-08-28 21:48:44',NULL,1),(26,102,'iPhone 12 128G 白色 9成新','https://platform-outputs.agnes-ai.space/images/t2i/task_IH22K8lLnZedw1R9JkHh8UsCmh2ayY3B/output_71fd95597c17438c87b6cb3f1025cb46.png','[\"https://platform-outputs.agnes-ai.space/images/t2i/task_IH22K8lLnZedw1R9JkHh8UsCmh2ayY3B/output_71fd95597c17438c87b6cb3f1025cb46.png\"]',1599.00,4599.00,'90','digital','digital-phone','express',5.00,1,'onsale','出一台二手苹果手机，整体成色约9成新，外观保持得不错，没有明显的磕碰或划痕，日常使用痕迹较少。功能一切正常，系统运行流畅，屏幕显示清晰，触控灵敏，电池状态稳定，续航表现符合正常使用水平。机器无拆修，所有功能按键均可正常使用。因为是日常自用换下来的，所以对这台机器比较熟悉，没有隐瞒问题。适合想要入手电梯性价比苹果手机的朋友，成色真实可靠，诚意出售，欢迎感兴趣的朋友联系了解。',NULL,'上海',NULL,NULL,1,0,0,0.0000,'2026-09-12 07:34:28',NULL,0,NULL,'2024年6月','','2026-09-06 17:11:35','','2026-09-06 17:11:35',NULL,1),(41,102,'iPhone 13 128G 蓝色 95新（已售）','https://picsum.photos/seed/p101/600/600','[\"https://picsum.photos/seed/p101/600/600\"]',5999.00,8899.00,'99','digital','digital-phone','express',12.00,0,'sold','自用8个月,无划痕无拆修,带原装盒+数据线,电池健康88%。支持线下看机验货。',NULL,'深圳',NULL,NULL,156,12,7,18.6000,'2026-09-17 17:02:24','2026-10-22 17:02:24',0,NULL,NULL,'','2026-09-17 17:02:24','','2026-10-27 17:02:24',NULL,1),(42,102,'任天堂Switch OLED 白色（已售）','https://picsum.photos/seed/p102/600/600','[\"https://picsum.photos/seed/p102/600/600\"]',1499.00,2599.00,'95','digital','digital-tablet','express',12.00,0,'sold','OLED版,购入半年,无划痕,配件齐全(底座+Joy-Con+原充)。送塞尔达卡带。',NULL,'深圳',NULL,NULL,98,9,4,12.3000,'2026-09-27 17:02:24','2026-10-25 17:02:24',0,NULL,NULL,'','2026-09-27 17:02:24','','2026-10-27 17:02:24',NULL,1),(43,102,'Lemaire 牛角包 焦糖色（已下架）','https://picsum.photos/seed/p103/600/600','[\"https://picsum.photos/seed/p103/600/600\"]',899.00,1899.00,'95','fashion','fashion-bag','express',10.00,0,'offshelf','日本购入,背过3次,无明显使用痕迹。带防尘袋+小票。',NULL,'深圳',NULL,NULL,64,5,2,6.8000,'2026-10-02 17:02:24','2026-10-26 17:02:24',0,NULL,NULL,'','2026-10-02 17:02:24','','2026-10-27 17:02:24',NULL,1),(44,102,'加拿大鹅羽绒服 M码（待整改）','https://picsum.photos/seed/p104/600/600','[\"https://picsum.photos/seed/p104/600/600\"]',3499.00,4500.00,'95','fashion','fashion-men','express',12.00,1,'rejected','2024款,C棉保暖,户外/日常都合适。就试穿,吊牌已剪。',NULL,'深圳',NULL,NULL,167,11,6,0.0000,'2026-10-25 17:02:24',NULL,0,'图片与描述不符，请补充实拍图后再提交',NULL,'','2026-10-25 17:02:24','','2026-10-27 17:02:24',NULL,1),(45,102,'Kindle Paperwhite 5 8G（草稿）','https://picsum.photos/seed/p105/600/600','[\"https://picsum.photos/seed/p105/600/600\"]',899.00,2399.00,'90','digital','digital-tablet','express',8.00,1,'draft','实体翻页键,冷暖光阅读灯,电池续航1周+,贴了磨砂膜。',NULL,'深圳',NULL,NULL,0,0,0,0.0000,NULL,NULL,0,NULL,NULL,'','2026-10-26 17:02:24','','2026-10-27 17:02:24',NULL,1),(46,102,'索尼 A7M4 单机身 快门8000','https://picsum.photos/seed/p106/600/600','[\"https://picsum.photos/seed/p106/600/600\"]',12999.00,16999.00,'90','digital','digital-laptop','express',20.00,1,'onsale','快门数约8000,3300万像素,4K60p视频。原厂电池*2+充电座。',NULL,'深圳',NULL,NULL,312,21,9,0.0000,'2026-10-21 17:02:24',NULL,0,NULL,NULL,'','2026-10-21 17:02:24','','2026-10-27 17:02:24',NULL,1),(47,102,'捷安特 TCR 公路车 S码','https://picsum.photos/seed/p107/600/600','[\"https://picsum.photos/seed/p107/600/600\"]',2499.00,4298.00,'90','sport','sport-outdoor','express',15.00,1,'onsale','飞行累计8小时,无炸机无维修,含畅飞套装。送128G内存卡。',NULL,'深圳',NULL,NULL,178,13,7,0.0000,'2026-10-23 17:02:24',NULL,0,NULL,NULL,'','2026-10-23 17:02:24','','2026-10-27 17:02:24',NULL,1);
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_audit_log`
--

DROP TABLE IF EXISTS `product_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_audit_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `auditor_id` bigint NOT NULL,
  `action` varchar(20) NOT NULL COMMENT 'pass/reject',
  `reason_code` varchar(50) DEFAULT NULL,
  `custom_reason` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品审核日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_audit_log`
--

LOCK TABLES `product_audit_log` WRITE;
/*!40000 ALTER TABLE `product_audit_log` DISABLE KEYS */;
INSERT INTO `product_audit_log` VALUES (1,1,103,'pass','auto',NULL,'2026-06-05 11:02:11'),(2,3,103,'pass','auto',NULL,'2026-06-06 11:02:11'),(3,21,103,'pass','manual',NULL,'2026-08-07 17:35:19'),(4,26,103,'pass','manual',NULL,'2026-09-12 07:34:28');
/*!40000 ALTER TABLE `product_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_category_dict`
--

DROP TABLE IF EXISTS `product_category_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category_dict` (
  `code` varchar(20) NOT NULL,
  `parent_code` varchar(20) DEFAULT NULL,
  `level` tinyint(1) NOT NULL DEFAULT '1',
  `name` varchar(30) NOT NULL,
  `sort` int DEFAULT '0',
  PRIMARY KEY (`code`),
  KEY `idx_parent` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类字典';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_category_dict`
--

LOCK TABLES `product_category_dict` WRITE;
/*!40000 ALTER TABLE `product_category_dict` DISABLE KEYS */;
INSERT INTO `product_category_dict` VALUES ('baby',NULL,1,'母婴',3),('baby-clothes','baby',2,'童装',3),('baby-feeding','baby',2,'喂养',4),('baby-stroller','baby',2,'推车',2),('baby-toy','baby',2,'玩具',1),('books',NULL,1,'书籍',5),('books-business','books',2,'商业',4),('books-child','books',2,'儿童',3),('books-education','books',2,'教辅',2),('books-novel','books',2,'小说',1),('digital',NULL,1,'数码',1),('digital-accessory','digital',2,'配件',5),('digital-laptop','digital',2,'笔记本',3),('digital-phone','digital',2,'手机',1),('digital-tablet','digital',2,'平板',2),('digital-wearable','digital',2,'穿戴设备',4),('fashion',NULL,1,'服装',4),('fashion-bag','fashion',2,'包袋',3),('fashion-men','fashion',2,'男装',1),('fashion-shoe','fashion',2,'鞋靴',4),('fashion-women','fashion',2,'女装',2),('home',NULL,1,'家居',2),('home-appliance','home',2,'家电',2),('home-decor','home',2,'装饰',4),('home-furniture','home',2,'家具',1),('home-kitchen','home',2,'厨房',3),('sport',NULL,1,'运动',7),('sport-bike','sport',2,'骑行',3),('sport-fitness','sport',2,'健身',2),('sport-outdoor','sport',2,'户外',1),('trendy',NULL,1,'潮玩',6),('trendy-card','trendy',2,'收藏卡',3),('trendy-figure','trendy',2,'手办',2),('trendy-lego','trendy',2,'乐高',1);
/*!40000 ALTER TABLE `product_category_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_draft`
--

DROP TABLE IF EXISTS `product_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_draft` (
  `draft_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `snapshot` text COMMENT 'JSON',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`draft_id`),
  KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_draft`
--

LOCK TABLES `product_draft` WRITE;
/*!40000 ALTER TABLE `product_draft` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_favorite`
--

DROP TABLE IF EXISTS `product_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_favorite` (
  `fav_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `original_price` decimal(10,2) DEFAULT NULL,
  `fav_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`fav_id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_favorite_tenant` (`tenant_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品收藏';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_favorite`
--

LOCK TABLES `product_favorite` WRITE;
/*!40000 ALTER TABLE `product_favorite` DISABLE KEYS */;
INSERT INTO `product_favorite` VALUES (1,101,1,8899.00,'2026-06-07 11:02:11',1),(2,101,3,3499.00,'2026-06-07 23:02:11',1),(4,101,18,NULL,'2026-06-11 16:35:52',1),(9,101,6,NULL,'2026-08-28 10:03:34',1),(16,101,13,NULL,'2026-08-28 12:24:18',1),(31,101,19,NULL,'2026-08-28 21:48:07',1),(32,125,19,NULL,'2026-08-29 14:19:24',2),(33,102,1,5999.00,'2026-10-25 17:02:24',1),(34,102,7,6999.00,'2026-10-25 17:02:24',1);
/*!40000 ALTER TABLE `product_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_pin`
--

DROP TABLE IF EXISTS `product_pin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_pin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seller_product` (`seller_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品置顶';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_pin`
--

LOCK TABLES `product_pin` WRITE;
/*!40000 ALTER TABLE `product_pin` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_pin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_stats_daily`
--

DROP TABLE IF EXISTS `product_stats_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_stats_daily` (
  `product_id` bigint NOT NULL,
  `date` date NOT NULL,
  `view_count` int DEFAULT '0',
  `want_count` int DEFAULT '0',
  `chat_count` int DEFAULT '0',
  PRIMARY KEY (`product_id`,`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品日统计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_stats_daily`
--

LOCK TABLES `product_stats_daily` WRITE;
/*!40000 ALTER TABLE `product_stats_daily` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_stats_daily` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_view_log`
--

DROP TABLE IF EXISTS `product_view_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `view_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`view_time`),
  KEY `idx_product_time` (`product_id`,`view_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品浏览记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_view_log`
--

LOCK TABLES `product_view_log` WRITE;
/*!40000 ALTER TABLE `product_view_log` DISABLE KEYS */;
INSERT INTO `product_view_log` VALUES (1,1,101,'2026-06-08 09:02:11'),(2,3,101,'2026-06-08 10:02:11'),(3,6,101,'2026-06-08 10:32:11');
/*!40000 ALTER TABLE `product_view_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_blob_triggers`
--

LOCK TABLES `qrtz_blob_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_blob_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_blob_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_calendars` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_calendars`
--

LOCK TABLES `qrtz_calendars` WRITE;
/*!40000 ALTER TABLE `qrtz_calendars` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_cron_triggers`
--

LOCK TABLES `qrtz_cron_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_cron_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_cron_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) NOT NULL COMMENT '状态',
  `job_name` varchar(200) DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_fired_triggers`
--

LOCK TABLES `qrtz_fired_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_fired_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_fired_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_job_details` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) NOT NULL COMMENT '任务组名',
  `description` varchar(250) DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_job_details`
--

LOCK TABLES `qrtz_job_details` WRITE;
/*!40000 ALTER TABLE `qrtz_job_details` DISABLE KEYS */;
INSERT INTO `qrtz_job_details` VALUES ('TWTScheduler','TASK_CLASS_NAME1','DEFAULT',NULL,'com.twelvet.server.job.util.QuartzJobExecution','0','0','0','0',0xACED0005737200156F72672E71756172747A2E4A6F62446174614D61709FB083E8BFA9B0CB020000787200266F72672E71756172747A2E7574696C732E537472696E674B65794469727479466C61674D61708208E8C3FBC55D280200015A0013616C6C6F77735472616E7369656E74446174617872001D6F72672E71756172747A2E7574696C732E4469727479466C61674D617013E62EAD28760ACE0200025A000564697274794C00036D617074000F4C6A6176612F7574696C2F4D61703B787001737200116A6176612E7574696C2E486173684D61700507DAC1C31660D103000246000A6C6F6164466163746F724900097468726573686F6C6478703F4000000000000C7708000000100000000174000F5441534B5F50524F5045525449455373720021636F6D2E7477656C7665742E6170692E6A6F622E646F6D61696E2E5379734A6F62000000000000000102000849000A636F6E63757272656E7449000D6D697366697265506F6C6963794900067374617475734C000E63726F6E45787072657373696F6E7400124C6A6176612F6C616E672F537472696E673B4C000C696E766F6B6554617267657471007E00094C00086A6F6247726F757071007E00094C00056A6F6249647400104C6A6176612F6C616E672F4C6F6E673B4C00076A6F624E616D6571007E000978720038636F6D2E7477656C7665742E6672616D65776F726B2E636F72652E6170706C69636174696F6E2E646F6D61696E2E42617365456E7469747900000000000000010200094C0009626567696E54696D6571007E00094C0008637265617465427971007E00094C000A63726561746554696D657400104C6A6176612F7574696C2F446174653B4C0007656E6454696D6571007E00094C0006706172616D7371007E00034C000672656D61726B71007E00094C000B73656172636856616C756571007E00094C0008757064617465427971007E00094C000A75706461746554696D6571007E000C78707074000561646D696E7372000E6A6176612E7574696C2E44617465686A81014B59741903000078707708000001622CDE29E078707074000070707000000001000000030000000074000E30202A2F3135202A202A202A203F7400137477745461736B2E7477744E6F506172616D7374000744454641554C547372000E6A6176612E6C616E672E4C6F6E673B8BE490CC8F23DF0200014A000576616C7565787200106A6176612E6C616E672E4E756D62657286AC951D0B94E08B02000078700000000000000001740018E7B3BBE7BB9FE9BB98E8AEA4EFBC88E697A0E58F82EFBC897800),('TWTScheduler','TASK_CLASS_NAME106','DEFAULT',NULL,'com.twelvet.server.job.util.QuartzDisallowConcurrentExecution','0','1','0','0',0xACED0005737200156F72672E71756172747A2E4A6F62446174614D61709FB083E8BFA9B0CB020000787200266F72672E71756172747A2E7574696C732E537472696E674B65794469727479466C61674D61708208E8C3FBC55D280200015A0013616C6C6F77735472616E7369656E74446174617872001D6F72672E71756172747A2E7574696C732E4469727479466C61674D617013E62EAD28760ACE0200025A000564697274794C00036D617074000F4C6A6176612F7574696C2F4D61703B787001737200116A6176612E7574696C2E486173684D61700507DAC1C31660D103000246000A6C6F6164466163746F724900097468726573686F6C6478703F4000000000000C7708000000100000000174000F5441534B5F50524F5045525449455373720021636F6D2E7477656C7665742E6170692E6A6F622E646F6D61696E2E5379734A6F62000000000000000102000849000A636F6E63757272656E7449000D6D697366697265506F6C6963794900067374617475734C000E63726F6E45787072657373696F6E7400124C6A6176612F6C616E672F537472696E673B4C000C696E766F6B6554617267657471007E00094C00086A6F6247726F757071007E00094C00056A6F6249647400104C6A6176612F6C616E672F4C6F6E673B4C00076A6F624E616D6571007E000978720038636F6D2E7477656C7665742E6672616D65776F726B2E636F72652E6170706C69636174696F6E2E646F6D61696E2E42617365456E7469747900000000000000010200094C0009626567696E54696D6571007E00094C0008637265617465427971007E00094C000A63726561746554696D657400104C6A6176612F7574696C2F446174653B4C0007656E6454696D6571007E00094C0006706172616D7371007E00034C000672656D61726B71007E00094C000B73656172636856616C756571007E00094C0008757064617465427971007E00094C000A75706461746554696D6571007E000C78707074000561646D696E7372000E6A6176612E7574696C2E44617465686A81014B59741903000078707708000001764D4CF5D078707074000070707000000000000000010000000074000D302F35202A202A202A202A203F7400187477745461736B2E747774506172616D732827747774272974000744454641554C547372000E6A6176612E6C616E672E4C6F6E673B8BE490CC8F23DF0200014A000576616C7565787200106A6176612E6C616E672E4E756D62657286AC951D0B94E08B0200007870000000000000006A740018E7B3BBE7BB9FE9BB98E8AEA4EFBC88E69C89E58F82EFBC897800);
/*!40000 ALTER TABLE `qrtz_job_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_locks` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_locks`
--

LOCK TABLES `qrtz_locks` WRITE;
/*!40000 ALTER TABLE `qrtz_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_paused_trigger_grps`
--

LOCK TABLES `qrtz_paused_trigger_grps` WRITE;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_scheduler_state`
--

LOCK TABLES `qrtz_scheduler_state` WRITE;
/*!40000 ALTER TABLE `qrtz_scheduler_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_scheduler_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simple_triggers`
--

LOCK TABLES `qrtz_simple_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simple_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simple_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simprop_triggers`
--

LOCK TABLES `qrtz_simprop_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_triggers`
--

LOCK TABLES `qrtz_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `recommend_impression_log`
--

DROP TABLE IF EXISTS `recommend_impression_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recommend_impression_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID(单租户模式可空)',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID(匿名曝光为空)',
  `product_id` bigint NOT NULL,
  `scene` varchar(30) NOT NULL DEFAULT 'home_recommend' COMMENT '曝光场景(home_recommend/detail_similar)',
  `position` int NOT NULL DEFAULT '0' COMMENT '曝光位次(从1起)',
  `bucket` tinyint NOT NULL DEFAULT '0' COMMENT 'A/B 分组(0=A/1=B)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_product_time` (`product_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=469 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐曝光流水(CTR统计)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `recommend_impression_log`
--

LOCK TABLES `recommend_impression_log` WRITE;
/*!40000 ALTER TABLE `recommend_impression_log` DISABLE KEYS */;
INSERT INTO `recommend_impression_log` VALUES (1,1,101,4,'home_recommend',1,0,'2026-09-07 22:51:51'),(2,1,101,13,'home_recommend',2,0,'2026-09-07 22:51:51'),(3,1,101,1,'home_recommend',3,0,'2026-09-07 22:51:51'),(4,1,101,16,'home_recommend',4,0,'2026-09-07 22:51:51'),(5,1,101,7,'home_recommend',5,0,'2026-09-07 22:51:51'),(6,1,101,10,'home_recommend',6,0,'2026-09-07 22:51:51'),(7,1,101,4,'home_recommend',1,0,'2026-09-09 23:48:57'),(8,1,101,13,'home_recommend',2,0,'2026-09-09 23:48:57'),(9,1,101,1,'home_recommend',3,0,'2026-09-09 23:48:57'),(10,1,101,16,'home_recommend',4,0,'2026-09-09 23:48:57'),(11,1,101,7,'home_recommend',5,0,'2026-09-09 23:48:57'),(12,1,101,10,'home_recommend',6,0,'2026-09-09 23:48:57'),(13,1,101,4,'home_recommend',1,0,'2026-09-10 00:08:51'),(14,1,101,13,'home_recommend',2,0,'2026-09-10 00:08:51'),(15,1,101,1,'home_recommend',3,0,'2026-09-10 00:08:51'),(16,1,101,16,'home_recommend',4,0,'2026-09-10 00:08:51'),(17,1,101,7,'home_recommend',5,0,'2026-09-10 00:08:51'),(18,1,101,10,'home_recommend',6,0,'2026-09-10 00:08:51'),(19,1,101,4,'home_recommend',1,0,'2026-09-10 00:10:34'),(20,1,101,13,'home_recommend',2,0,'2026-09-10 00:10:34'),(21,1,101,1,'home_recommend',3,0,'2026-09-10 00:10:34'),(22,1,101,16,'home_recommend',4,0,'2026-09-10 00:10:34'),(23,1,101,7,'home_recommend',5,0,'2026-09-10 00:10:34'),(24,1,101,10,'home_recommend',6,0,'2026-09-10 00:10:34'),(25,1,101,4,'home_recommend',1,0,'2026-09-10 00:14:38'),(26,1,101,13,'home_recommend',2,0,'2026-09-10 00:14:38'),(27,1,101,1,'home_recommend',3,0,'2026-09-10 00:14:38'),(28,1,101,16,'home_recommend',4,0,'2026-09-10 00:14:38'),(29,1,101,7,'home_recommend',5,0,'2026-09-10 00:14:38'),(30,1,101,10,'home_recommend',6,0,'2026-09-10 00:14:38'),(31,1,101,4,'home_recommend',1,0,'2026-09-10 06:46:39'),(32,1,101,13,'home_recommend',2,0,'2026-09-10 06:46:39'),(33,1,101,1,'home_recommend',3,0,'2026-09-10 06:46:39'),(34,1,101,16,'home_recommend',4,0,'2026-09-10 06:46:39'),(35,1,101,7,'home_recommend',5,0,'2026-09-10 06:46:39'),(36,1,101,10,'home_recommend',6,0,'2026-09-10 06:46:39'),(37,1,101,4,'home_recommend',1,0,'2026-09-10 22:54:07'),(38,1,101,13,'home_recommend',2,0,'2026-09-10 22:54:07'),(39,1,101,1,'home_recommend',3,0,'2026-09-10 22:54:07'),(40,1,101,16,'home_recommend',4,0,'2026-09-10 22:54:07'),(41,1,101,7,'home_recommend',5,0,'2026-09-10 22:54:07'),(42,1,101,10,'home_recommend',6,0,'2026-09-10 22:54:07'),(43,1,101,4,'home_recommend',1,0,'2026-09-10 22:54:20'),(44,1,101,13,'home_recommend',2,0,'2026-09-10 22:54:20'),(45,1,101,1,'home_recommend',3,0,'2026-09-10 22:54:20'),(46,1,101,16,'home_recommend',4,0,'2026-09-10 22:54:20'),(47,1,101,7,'home_recommend',5,0,'2026-09-10 22:54:20'),(48,1,101,10,'home_recommend',6,0,'2026-09-10 22:54:20'),(49,NULL,NULL,21,'home_recommend',1,0,'2026-09-10 22:55:52'),(50,NULL,NULL,4,'home_recommend',2,0,'2026-09-10 22:55:52'),(51,1,101,4,'home_recommend',1,0,'2026-09-10 23:07:59'),(52,1,101,13,'home_recommend',2,0,'2026-09-10 23:07:59'),(53,1,101,1,'home_recommend',3,0,'2026-09-10 23:07:59'),(54,1,101,16,'home_recommend',4,0,'2026-09-10 23:07:59'),(55,1,101,7,'home_recommend',5,0,'2026-09-10 23:07:59'),(56,1,101,10,'home_recommend',6,0,'2026-09-10 23:07:59'),(57,1,101,4,'home_recommend',1,0,'2026-09-10 23:09:40'),(58,1,101,13,'home_recommend',2,0,'2026-09-10 23:09:40'),(59,1,101,1,'home_recommend',3,0,'2026-09-10 23:09:40'),(60,1,101,16,'home_recommend',4,0,'2026-09-10 23:09:40'),(61,1,101,7,'home_recommend',5,0,'2026-09-10 23:09:40'),(62,1,101,10,'home_recommend',6,0,'2026-09-10 23:09:40'),(63,NULL,NULL,21,'home_recommend',1,0,'2026-09-10 23:12:22'),(64,NULL,NULL,4,'home_recommend',2,0,'2026-09-10 23:12:22'),(65,NULL,NULL,6,'home_recommend',3,0,'2026-09-10 23:12:22'),(66,1,101,4,'home_recommend',1,0,'2026-09-11 20:46:49'),(67,1,101,13,'home_recommend',2,0,'2026-09-11 20:46:49'),(68,1,101,1,'home_recommend',3,0,'2026-09-11 20:46:49'),(69,1,101,16,'home_recommend',4,0,'2026-09-11 20:46:49'),(70,1,101,7,'home_recommend',5,0,'2026-09-11 20:46:49'),(71,1,101,10,'home_recommend',6,0,'2026-09-11 20:46:49'),(72,1,101,4,'home_recommend',1,0,'2026-09-11 20:56:55'),(73,1,101,13,'home_recommend',2,0,'2026-09-11 20:56:55'),(74,1,101,1,'home_recommend',3,0,'2026-09-11 20:56:55'),(75,1,101,16,'home_recommend',4,0,'2026-09-11 20:56:55'),(76,1,101,7,'home_recommend',5,0,'2026-09-11 20:56:55'),(77,1,101,10,'home_recommend',6,0,'2026-09-11 20:56:55'),(78,1,101,4,'home_recommend',1,0,'2026-09-11 20:57:04'),(79,1,101,13,'home_recommend',2,0,'2026-09-11 20:57:04'),(80,1,101,1,'home_recommend',3,0,'2026-09-11 20:57:04'),(81,1,101,16,'home_recommend',4,0,'2026-09-11 20:57:04'),(82,1,101,7,'home_recommend',5,0,'2026-09-11 20:57:04'),(83,1,101,10,'home_recommend',6,0,'2026-09-11 20:57:04'),(84,1,101,4,'home_recommend',1,0,'2026-09-11 23:09:30'),(85,1,101,13,'home_recommend',2,0,'2026-09-11 23:09:30'),(86,1,101,1,'home_recommend',3,0,'2026-09-11 23:09:30'),(87,1,101,16,'home_recommend',4,0,'2026-09-11 23:09:30'),(88,1,101,7,'home_recommend',5,0,'2026-09-11 23:09:30'),(89,1,101,10,'home_recommend',6,0,'2026-09-11 23:09:30'),(90,1,101,4,'home_recommend',1,0,'2026-09-12 07:26:46'),(91,1,101,13,'home_recommend',2,0,'2026-09-12 07:26:46'),(92,1,101,1,'home_recommend',3,0,'2026-09-12 07:26:46'),(93,1,101,16,'home_recommend',4,0,'2026-09-12 07:26:46'),(94,1,101,7,'home_recommend',5,0,'2026-09-12 07:26:46'),(95,1,101,10,'home_recommend',6,0,'2026-09-12 07:26:46'),(96,1,101,4,'home_recommend',1,0,'2026-09-15 20:28:54'),(97,1,101,13,'home_recommend',2,0,'2026-09-15 20:28:54'),(98,1,101,1,'home_recommend',3,0,'2026-09-15 20:28:54'),(99,1,101,16,'home_recommend',4,0,'2026-09-15 20:28:54'),(100,1,101,7,'home_recommend',5,0,'2026-09-15 20:28:54'),(101,1,101,10,'home_recommend',6,0,'2026-09-15 20:28:54'),(102,1,101,4,'home_recommend',1,0,'2026-09-19 06:52:41'),(103,1,101,13,'home_recommend',2,0,'2026-09-19 06:52:41'),(104,1,101,1,'home_recommend',3,0,'2026-09-19 06:52:41'),(105,1,101,16,'home_recommend',4,0,'2026-09-19 06:52:41'),(106,1,101,7,'home_recommend',5,0,'2026-09-19 06:52:41'),(107,1,101,10,'home_recommend',6,0,'2026-09-19 06:52:41'),(108,1,101,4,'home_recommend',1,0,'2026-09-22 23:19:14'),(109,1,101,13,'home_recommend',2,0,'2026-09-22 23:19:14'),(110,1,101,1,'home_recommend',3,0,'2026-09-22 23:19:14'),(111,1,101,16,'home_recommend',4,0,'2026-09-22 23:19:14'),(112,1,101,7,'home_recommend',5,0,'2026-09-22 23:19:14'),(113,1,101,10,'home_recommend',6,0,'2026-09-22 23:19:14'),(114,1,101,4,'home_recommend',1,0,'2026-09-23 12:56:35'),(115,1,101,13,'home_recommend',2,0,'2026-09-23 12:56:35'),(116,1,101,1,'home_recommend',3,0,'2026-09-23 12:56:35'),(117,1,101,16,'home_recommend',4,0,'2026-09-23 12:56:35'),(118,1,101,7,'home_recommend',5,0,'2026-09-23 12:56:35'),(119,1,101,10,'home_recommend',6,0,'2026-09-23 12:56:35'),(120,1,101,4,'home_recommend',1,0,'2026-09-23 13:55:04'),(121,1,101,13,'home_recommend',2,0,'2026-09-23 13:55:04'),(122,1,101,1,'home_recommend',3,0,'2026-09-23 13:55:04'),(123,1,101,16,'home_recommend',4,0,'2026-09-23 13:55:04'),(124,1,101,7,'home_recommend',5,0,'2026-09-23 13:55:04'),(125,1,101,10,'home_recommend',6,0,'2026-09-23 13:55:04'),(126,1,101,4,'home_recommend',1,0,'2026-09-23 13:55:33'),(127,1,101,13,'home_recommend',2,0,'2026-09-23 13:55:33'),(128,1,101,1,'home_recommend',3,0,'2026-09-23 13:55:33'),(129,1,101,16,'home_recommend',4,0,'2026-09-23 13:55:33'),(130,1,101,7,'home_recommend',5,0,'2026-09-23 13:55:33'),(131,1,101,10,'home_recommend',6,0,'2026-09-23 13:55:33'),(132,1,101,4,'home_recommend',1,0,'2026-09-23 16:35:01'),(133,1,101,13,'home_recommend',2,0,'2026-09-23 16:35:01'),(134,1,101,1,'home_recommend',3,0,'2026-09-23 16:35:01'),(135,1,101,16,'home_recommend',4,0,'2026-09-23 16:35:01'),(136,1,101,7,'home_recommend',5,0,'2026-09-23 16:35:01'),(137,1,101,10,'home_recommend',6,0,'2026-09-23 16:35:01'),(138,1,101,4,'home_recommend',1,0,'2026-09-23 16:35:31'),(139,1,101,13,'home_recommend',2,0,'2026-09-23 16:35:31'),(140,1,101,1,'home_recommend',3,0,'2026-09-23 16:35:31'),(141,1,101,16,'home_recommend',4,0,'2026-09-23 16:35:31'),(142,1,101,7,'home_recommend',5,0,'2026-09-23 16:35:31'),(143,1,101,10,'home_recommend',6,0,'2026-09-23 16:35:31'),(144,1,101,4,'home_recommend',1,0,'2026-09-23 16:39:21'),(145,1,101,13,'home_recommend',2,0,'2026-09-23 16:39:21'),(146,1,101,1,'home_recommend',3,0,'2026-09-23 16:39:21'),(147,1,101,16,'home_recommend',4,0,'2026-09-23 16:39:21'),(148,1,101,7,'home_recommend',5,0,'2026-09-23 16:39:21'),(149,1,101,10,'home_recommend',6,0,'2026-09-23 16:39:21'),(150,1,101,4,'home_recommend',1,0,'2026-09-23 16:40:00'),(151,1,101,13,'home_recommend',2,0,'2026-09-23 16:40:00'),(152,1,101,1,'home_recommend',3,0,'2026-09-23 16:40:00'),(153,1,101,16,'home_recommend',4,0,'2026-09-23 16:40:00'),(154,1,101,7,'home_recommend',5,0,'2026-09-23 16:40:00'),(155,1,101,10,'home_recommend',6,0,'2026-09-23 16:40:00'),(156,1,101,4,'home_recommend',1,0,'2026-09-23 16:41:03'),(157,1,101,13,'home_recommend',2,0,'2026-09-23 16:41:03'),(158,1,101,1,'home_recommend',3,0,'2026-09-23 16:41:03'),(159,1,101,16,'home_recommend',4,0,'2026-09-23 16:41:03'),(160,1,101,7,'home_recommend',5,0,'2026-09-23 16:41:03'),(161,1,101,10,'home_recommend',6,0,'2026-09-23 16:41:03'),(162,1,101,4,'home_recommend',1,0,'2026-09-23 16:45:51'),(163,1,101,13,'home_recommend',2,0,'2026-09-23 16:45:51'),(164,1,101,1,'home_recommend',3,0,'2026-09-23 16:45:51'),(165,1,101,16,'home_recommend',4,0,'2026-09-23 16:45:51'),(166,1,101,7,'home_recommend',5,0,'2026-09-23 16:45:51'),(167,1,101,10,'home_recommend',6,0,'2026-09-23 16:45:51'),(168,1,101,4,'home_recommend',1,0,'2026-09-23 16:49:18'),(169,1,101,13,'home_recommend',2,0,'2026-09-23 16:49:18'),(170,1,101,1,'home_recommend',3,0,'2026-09-23 16:49:18'),(171,1,101,16,'home_recommend',4,0,'2026-09-23 16:49:18'),(172,1,101,7,'home_recommend',5,0,'2026-09-23 16:49:18'),(173,1,101,10,'home_recommend',6,0,'2026-09-23 16:49:18'),(174,1,101,4,'home_recommend',1,0,'2026-09-23 16:51:22'),(175,1,101,13,'home_recommend',2,0,'2026-09-23 16:51:22'),(176,1,101,1,'home_recommend',3,0,'2026-09-23 16:51:22'),(177,1,101,16,'home_recommend',4,0,'2026-09-23 16:51:22'),(178,1,101,7,'home_recommend',5,0,'2026-09-23 16:51:22'),(179,1,101,10,'home_recommend',6,0,'2026-09-23 16:51:22'),(180,1,101,4,'home_recommend',1,0,'2026-09-23 16:54:06'),(181,1,101,13,'home_recommend',2,0,'2026-09-23 16:54:06'),(182,1,101,1,'home_recommend',3,0,'2026-09-23 16:54:06'),(183,1,101,16,'home_recommend',4,0,'2026-09-23 16:54:06'),(184,1,101,7,'home_recommend',5,0,'2026-09-23 16:54:06'),(185,1,101,10,'home_recommend',6,0,'2026-09-23 16:54:06'),(186,1,101,4,'home_recommend',1,0,'2026-09-23 17:02:25'),(187,1,101,13,'home_recommend',2,0,'2026-09-23 17:02:25'),(188,1,101,1,'home_recommend',3,0,'2026-09-23 17:02:25'),(189,1,101,16,'home_recommend',4,0,'2026-09-23 17:02:25'),(190,1,101,7,'home_recommend',5,0,'2026-09-23 17:02:25'),(191,1,101,10,'home_recommend',6,0,'2026-09-23 17:02:25'),(192,1,101,4,'home_recommend',1,0,'2026-09-23 20:28:38'),(193,1,101,13,'home_recommend',2,0,'2026-09-23 20:28:38'),(194,1,101,1,'home_recommend',3,0,'2026-09-23 20:28:38'),(195,1,101,16,'home_recommend',4,0,'2026-09-23 20:28:38'),(196,1,101,7,'home_recommend',5,0,'2026-09-23 20:28:38'),(197,1,101,10,'home_recommend',6,0,'2026-09-23 20:28:38'),(198,1,101,4,'home_recommend',1,0,'2026-09-23 20:35:26'),(199,1,101,13,'home_recommend',2,0,'2026-09-23 20:35:26'),(200,1,101,1,'home_recommend',3,0,'2026-09-23 20:35:26'),(201,1,101,16,'home_recommend',4,0,'2026-09-23 20:35:26'),(202,1,101,7,'home_recommend',5,0,'2026-09-23 20:35:26'),(203,1,101,10,'home_recommend',6,0,'2026-09-23 20:35:26'),(204,1,101,4,'home_recommend',1,0,'2026-09-24 11:18:55'),(205,1,101,13,'home_recommend',2,0,'2026-09-24 11:18:55'),(206,1,101,1,'home_recommend',3,0,'2026-09-24 11:18:55'),(207,1,101,16,'home_recommend',4,0,'2026-09-24 11:18:55'),(208,1,101,7,'home_recommend',5,0,'2026-09-24 11:18:55'),(209,1,101,10,'home_recommend',6,0,'2026-09-24 11:18:55'),(210,1,101,4,'home_recommend',1,0,'2026-09-24 11:40:43'),(211,1,101,13,'home_recommend',2,0,'2026-09-24 11:40:43'),(212,1,101,1,'home_recommend',3,0,'2026-09-24 11:40:43'),(213,1,101,16,'home_recommend',4,0,'2026-09-24 11:40:43'),(214,1,101,7,'home_recommend',5,0,'2026-09-24 11:40:43'),(215,1,101,10,'home_recommend',6,0,'2026-09-24 11:40:43'),(216,1,101,4,'home_recommend',1,0,'2026-09-25 08:36:44'),(217,1,101,13,'home_recommend',2,0,'2026-09-25 08:36:44'),(218,1,101,1,'home_recommend',3,0,'2026-09-25 08:36:44'),(219,1,101,16,'home_recommend',4,0,'2026-09-25 08:36:44'),(220,1,101,7,'home_recommend',5,0,'2026-09-25 08:36:44'),(221,1,101,10,'home_recommend',6,0,'2026-09-25 08:36:44'),(222,1,101,4,'home_recommend',1,0,'2026-09-25 08:36:53'),(223,1,101,13,'home_recommend',2,0,'2026-09-25 08:36:53'),(224,1,101,1,'home_recommend',3,0,'2026-09-25 08:36:53'),(225,1,101,16,'home_recommend',4,0,'2026-09-25 08:36:53'),(226,1,101,7,'home_recommend',5,0,'2026-09-25 08:36:53'),(227,1,101,10,'home_recommend',6,0,'2026-09-25 08:36:53'),(228,1,101,4,'home_recommend',1,0,'2026-09-25 17:50:48'),(229,1,101,13,'home_recommend',2,0,'2026-09-25 17:50:48'),(230,1,101,1,'home_recommend',3,0,'2026-09-25 17:50:48'),(231,1,101,16,'home_recommend',4,0,'2026-09-25 17:50:48'),(232,1,101,7,'home_recommend',5,0,'2026-09-25 17:50:48'),(233,1,101,10,'home_recommend',6,0,'2026-09-25 17:50:48'),(234,1,101,4,'home_recommend',1,0,'2026-09-25 17:51:08'),(235,1,101,13,'home_recommend',2,0,'2026-09-25 17:51:08'),(236,1,101,1,'home_recommend',3,0,'2026-09-25 17:51:08'),(237,1,101,16,'home_recommend',4,0,'2026-09-25 17:51:08'),(238,1,101,7,'home_recommend',5,0,'2026-09-25 17:51:08'),(239,1,101,10,'home_recommend',6,0,'2026-09-25 17:51:08'),(240,1,101,4,'home_recommend',1,0,'2026-09-25 17:51:55'),(241,1,101,13,'home_recommend',2,0,'2026-09-25 17:51:55'),(242,1,101,1,'home_recommend',3,0,'2026-09-25 17:51:55'),(243,1,101,16,'home_recommend',4,0,'2026-09-25 17:51:55'),(244,1,101,7,'home_recommend',5,0,'2026-09-25 17:51:55'),(245,1,101,10,'home_recommend',6,0,'2026-09-25 17:51:55'),(246,1,101,4,'home_recommend',1,0,'2026-09-25 17:52:25'),(247,1,101,13,'home_recommend',2,0,'2026-09-25 17:52:25'),(248,1,101,1,'home_recommend',3,0,'2026-09-25 17:52:25'),(249,1,101,16,'home_recommend',4,0,'2026-09-25 17:52:25'),(250,1,101,7,'home_recommend',5,0,'2026-09-25 17:52:25'),(251,1,101,10,'home_recommend',6,0,'2026-09-25 17:52:25'),(252,1,101,4,'home_recommend',1,0,'2026-09-25 17:52:51'),(253,1,101,13,'home_recommend',2,0,'2026-09-25 17:52:51'),(254,1,101,1,'home_recommend',3,0,'2026-09-25 17:52:51'),(255,1,101,16,'home_recommend',4,0,'2026-09-25 17:52:51'),(256,1,101,7,'home_recommend',5,0,'2026-09-25 17:52:51'),(257,1,101,10,'home_recommend',6,0,'2026-09-25 17:52:51'),(258,1,101,4,'home_recommend',1,0,'2026-09-25 17:52:55'),(259,1,101,13,'home_recommend',2,0,'2026-09-25 17:52:55'),(260,1,101,1,'home_recommend',3,0,'2026-09-25 17:52:55'),(261,1,101,16,'home_recommend',4,0,'2026-09-25 17:52:55'),(262,1,101,7,'home_recommend',5,0,'2026-09-25 17:52:55'),(263,1,101,10,'home_recommend',6,0,'2026-09-25 17:52:55'),(264,1,101,4,'home_recommend',1,0,'2026-09-25 18:06:27'),(265,1,101,13,'home_recommend',2,0,'2026-09-25 18:06:27'),(266,1,101,1,'home_recommend',3,0,'2026-09-25 18:06:27'),(267,1,101,16,'home_recommend',4,0,'2026-09-25 18:06:27'),(268,1,101,7,'home_recommend',5,0,'2026-09-25 18:06:27'),(269,1,101,10,'home_recommend',6,0,'2026-09-25 18:06:27'),(270,1,101,4,'home_recommend',1,0,'2026-09-25 18:15:51'),(271,1,101,13,'home_recommend',2,0,'2026-09-25 18:15:51'),(272,1,101,1,'home_recommend',3,0,'2026-09-25 18:15:51'),(273,1,101,16,'home_recommend',4,0,'2026-09-25 18:15:51'),(274,1,101,7,'home_recommend',5,0,'2026-09-25 18:15:51'),(275,1,101,10,'home_recommend',6,0,'2026-09-25 18:15:51'),(276,1,101,4,'home_recommend',1,0,'2026-09-25 18:16:01'),(277,1,101,13,'home_recommend',2,0,'2026-09-25 18:16:01'),(278,1,101,1,'home_recommend',3,0,'2026-09-25 18:16:01'),(279,1,101,16,'home_recommend',4,0,'2026-09-25 18:16:01'),(280,1,101,7,'home_recommend',5,0,'2026-09-25 18:16:01'),(281,1,101,10,'home_recommend',6,0,'2026-09-25 18:16:01'),(282,1,101,4,'home_recommend',1,0,'2026-09-25 18:19:08'),(283,1,101,13,'home_recommend',2,0,'2026-09-25 18:19:08'),(284,1,101,1,'home_recommend',3,0,'2026-09-25 18:19:08'),(285,1,101,16,'home_recommend',4,0,'2026-09-25 18:19:08'),(286,1,101,7,'home_recommend',5,0,'2026-09-25 18:19:08'),(287,1,101,10,'home_recommend',6,0,'2026-09-25 18:19:08'),(288,1,101,4,'home_recommend',1,0,'2026-09-25 18:19:20'),(289,1,101,13,'home_recommend',2,0,'2026-09-25 18:19:20'),(290,1,101,1,'home_recommend',3,0,'2026-09-25 18:19:20'),(291,1,101,16,'home_recommend',4,0,'2026-09-25 18:19:20'),(292,1,101,7,'home_recommend',5,0,'2026-09-25 18:19:20'),(293,1,101,10,'home_recommend',6,0,'2026-09-25 18:19:20'),(294,1,101,4,'home_recommend',1,0,'2026-09-25 18:49:56'),(295,1,101,13,'home_recommend',2,0,'2026-09-25 18:49:56'),(296,1,101,1,'home_recommend',3,0,'2026-09-25 18:49:56'),(297,1,101,16,'home_recommend',4,0,'2026-09-25 18:49:56'),(298,1,101,7,'home_recommend',5,0,'2026-09-25 18:49:56'),(299,1,101,10,'home_recommend',6,0,'2026-09-25 18:49:56'),(300,1,101,4,'home_recommend',1,0,'2026-09-25 19:22:04'),(301,1,101,13,'home_recommend',2,0,'2026-09-25 19:22:04'),(302,1,101,1,'home_recommend',3,0,'2026-09-25 19:22:04'),(303,1,101,16,'home_recommend',4,0,'2026-09-25 19:22:04'),(304,1,101,7,'home_recommend',5,0,'2026-09-25 19:22:04'),(305,1,101,10,'home_recommend',6,0,'2026-09-25 19:22:04'),(306,1,101,4,'home_recommend',1,0,'2026-09-25 19:22:14'),(307,1,101,13,'home_recommend',2,0,'2026-09-25 19:22:14'),(308,1,101,1,'home_recommend',3,0,'2026-09-25 19:22:14'),(309,1,101,16,'home_recommend',4,0,'2026-09-25 19:22:14'),(310,1,101,7,'home_recommend',5,0,'2026-09-25 19:22:14'),(311,1,101,10,'home_recommend',6,0,'2026-09-25 19:22:14'),(312,1,101,4,'home_recommend',1,0,'2026-09-25 19:22:22'),(313,1,101,13,'home_recommend',2,0,'2026-09-25 19:22:22'),(314,1,101,1,'home_recommend',3,0,'2026-09-25 19:22:22'),(315,1,101,16,'home_recommend',4,0,'2026-09-25 19:22:22'),(316,1,101,7,'home_recommend',5,0,'2026-09-25 19:22:22'),(317,1,101,10,'home_recommend',6,0,'2026-09-25 19:22:22'),(318,1,101,4,'home_recommend',1,0,'2026-09-25 23:26:00'),(319,1,101,13,'home_recommend',2,0,'2026-09-25 23:26:00'),(320,1,101,1,'home_recommend',3,0,'2026-09-25 23:26:00'),(321,1,101,16,'home_recommend',4,0,'2026-09-25 23:26:00'),(322,1,101,7,'home_recommend',5,0,'2026-09-25 23:26:00'),(323,1,101,10,'home_recommend',6,0,'2026-09-25 23:26:00'),(324,1,101,4,'home_recommend',1,0,'2026-09-26 19:11:09'),(325,1,101,13,'home_recommend',2,0,'2026-09-26 19:11:09'),(326,1,101,1,'home_recommend',3,0,'2026-09-26 19:11:09'),(327,1,101,16,'home_recommend',4,0,'2026-09-26 19:11:09'),(328,1,101,7,'home_recommend',5,0,'2026-09-26 19:11:09'),(329,1,101,10,'home_recommend',6,0,'2026-09-26 19:11:09'),(330,1,101,4,'home_recommend',1,0,'2026-09-26 19:15:05'),(331,1,101,13,'home_recommend',2,0,'2026-09-26 19:15:05'),(332,1,101,1,'home_recommend',3,0,'2026-09-26 19:15:05'),(333,1,101,16,'home_recommend',4,0,'2026-09-26 19:15:05'),(334,1,101,7,'home_recommend',5,0,'2026-09-26 19:15:05'),(335,1,101,10,'home_recommend',6,0,'2026-09-26 19:15:05'),(336,1,101,4,'home_recommend',1,0,'2026-09-26 19:15:42'),(337,1,101,13,'home_recommend',2,0,'2026-09-26 19:15:42'),(338,1,101,1,'home_recommend',3,0,'2026-09-26 19:15:42'),(339,1,101,16,'home_recommend',4,0,'2026-09-26 19:15:42'),(340,1,101,7,'home_recommend',5,0,'2026-09-26 19:15:42'),(341,1,101,10,'home_recommend',6,0,'2026-09-26 19:15:42'),(342,1,101,4,'home_recommend',1,0,'2026-09-26 19:15:51'),(343,1,101,13,'home_recommend',2,0,'2026-09-26 19:15:51'),(344,1,101,1,'home_recommend',3,0,'2026-09-26 19:15:51'),(345,1,101,16,'home_recommend',4,0,'2026-09-26 19:15:51'),(346,1,101,7,'home_recommend',5,0,'2026-09-26 19:15:51'),(347,1,101,10,'home_recommend',6,0,'2026-09-26 19:15:51'),(348,1,101,4,'home_recommend',1,0,'2026-09-26 19:17:00'),(349,1,101,13,'home_recommend',2,0,'2026-09-26 19:17:00'),(350,1,101,1,'home_recommend',3,0,'2026-09-26 19:17:00'),(351,1,101,16,'home_recommend',4,0,'2026-09-26 19:17:00'),(352,1,101,7,'home_recommend',5,0,'2026-09-26 19:17:00'),(353,1,101,10,'home_recommend',6,0,'2026-09-26 19:17:00'),(354,1,101,4,'home_recommend',1,0,'2026-09-26 19:18:00'),(355,1,101,13,'home_recommend',2,0,'2026-09-26 19:18:00'),(356,1,101,1,'home_recommend',3,0,'2026-09-26 19:18:00'),(357,1,101,16,'home_recommend',4,0,'2026-09-26 19:18:00'),(358,1,101,7,'home_recommend',5,0,'2026-09-26 19:18:00'),(359,1,101,10,'home_recommend',6,0,'2026-09-26 19:18:00'),(360,1,101,4,'home_recommend',1,0,'2026-09-26 19:46:45'),(361,1,101,13,'home_recommend',2,0,'2026-09-26 19:46:45'),(362,1,101,1,'home_recommend',3,0,'2026-09-26 19:46:45'),(363,1,101,16,'home_recommend',4,0,'2026-09-26 19:46:45'),(364,1,101,7,'home_recommend',5,0,'2026-09-26 19:46:45'),(365,1,101,10,'home_recommend',6,0,'2026-09-26 19:46:45'),(366,1,101,4,'home_recommend',1,0,'2026-09-30 15:28:15'),(367,1,101,13,'home_recommend',2,0,'2026-09-30 15:28:15'),(368,1,101,1,'home_recommend',3,0,'2026-09-30 15:28:15'),(369,1,101,16,'home_recommend',4,0,'2026-09-30 15:28:15'),(370,1,101,7,'home_recommend',5,0,'2026-09-30 15:28:15'),(371,1,101,10,'home_recommend',6,0,'2026-09-30 15:28:15'),(372,1,101,4,'home_recommend',1,0,'2026-09-30 15:36:46'),(373,1,101,13,'home_recommend',2,0,'2026-09-30 15:36:46'),(374,1,101,1,'home_recommend',3,0,'2026-09-30 15:36:46'),(375,1,101,16,'home_recommend',4,0,'2026-09-30 15:36:46'),(376,1,101,7,'home_recommend',5,0,'2026-09-30 15:36:46'),(377,1,101,10,'home_recommend',6,0,'2026-09-30 15:36:46'),(378,1,101,4,'home_recommend',1,0,'2026-09-30 15:37:03'),(379,1,101,13,'home_recommend',2,0,'2026-09-30 15:37:03'),(380,1,101,1,'home_recommend',3,0,'2026-09-30 15:37:03'),(381,1,101,16,'home_recommend',4,0,'2026-09-30 15:37:03'),(382,1,101,7,'home_recommend',5,0,'2026-09-30 15:37:03'),(383,1,101,10,'home_recommend',6,0,'2026-09-30 15:37:03'),(384,1,101,4,'home_recommend',1,0,'2026-10-02 22:13:08'),(385,1,101,13,'home_recommend',2,0,'2026-10-02 22:13:08'),(386,1,101,1,'home_recommend',3,0,'2026-10-02 22:13:08'),(387,1,101,16,'home_recommend',4,0,'2026-10-02 22:13:08'),(388,1,101,7,'home_recommend',5,0,'2026-10-02 22:13:08'),(389,1,101,10,'home_recommend',6,0,'2026-10-02 22:13:08'),(390,1,101,4,'home_recommend',1,0,'2026-10-02 22:20:09'),(391,1,101,13,'home_recommend',2,0,'2026-10-02 22:20:09'),(392,1,101,1,'home_recommend',3,0,'2026-10-02 22:20:09'),(393,1,101,16,'home_recommend',4,0,'2026-10-02 22:20:09'),(394,1,101,7,'home_recommend',5,0,'2026-10-02 22:20:09'),(395,1,101,10,'home_recommend',6,0,'2026-10-02 22:20:09'),(396,1,101,4,'home_recommend',1,0,'2026-10-07 08:57:37'),(397,1,101,13,'home_recommend',2,0,'2026-10-07 08:57:37'),(398,1,101,1,'home_recommend',3,0,'2026-10-07 08:57:37'),(399,1,101,16,'home_recommend',4,0,'2026-10-07 08:57:37'),(400,1,101,7,'home_recommend',5,0,'2026-10-07 08:57:37'),(401,1,101,10,'home_recommend',6,0,'2026-10-07 08:57:37'),(402,1,101,4,'home_recommend',1,0,'2026-10-07 09:20:24'),(403,1,101,13,'home_recommend',2,0,'2026-10-07 09:20:24'),(404,1,101,1,'home_recommend',3,0,'2026-10-07 09:20:24'),(405,1,101,16,'home_recommend',4,0,'2026-10-07 09:20:24'),(406,1,101,7,'home_recommend',5,0,'2026-10-07 09:20:24'),(407,1,101,10,'home_recommend',6,0,'2026-10-07 09:20:24'),(408,1,101,4,'home_recommend',1,0,'2026-10-07 18:16:11'),(409,1,101,13,'home_recommend',2,0,'2026-10-07 18:16:11'),(410,1,101,1,'home_recommend',3,0,'2026-10-07 18:16:11'),(411,1,101,16,'home_recommend',4,0,'2026-10-07 18:16:11'),(412,1,101,7,'home_recommend',5,0,'2026-10-07 18:16:11'),(413,1,101,10,'home_recommend',6,0,'2026-10-07 18:16:11'),(414,1,101,4,'home_recommend',1,0,'2026-10-21 14:54:00'),(415,1,101,6,'home_recommend',2,0,'2026-10-21 14:54:00'),(416,1,101,4,'home_recommend',1,0,'2026-10-21 14:59:38'),(417,NULL,123,47,'home_recommend',1,0,'2026-10-27 17:05:55'),(418,NULL,123,46,'home_recommend',2,0,'2026-10-27 17:05:55'),(419,NULL,123,26,'home_recommend',3,0,'2026-10-27 17:05:55'),(420,NULL,123,4,'home_recommend',4,0,'2026-10-27 17:05:55'),(421,NULL,123,6,'home_recommend',5,0,'2026-10-27 17:05:55'),(422,NULL,123,18,'home_recommend',6,0,'2026-10-27 17:05:55'),(423,NULL,123,3,'home_recommend',7,0,'2026-10-27 17:05:55'),(424,NULL,123,9,'home_recommend',8,0,'2026-10-27 17:05:55'),(425,NULL,123,5,'home_recommend',9,0,'2026-10-27 17:05:55'),(426,NULL,123,2,'home_recommend',10,0,'2026-10-27 17:05:55'),(427,NULL,123,14,'home_recommend',11,0,'2026-10-27 17:05:55'),(428,NULL,123,10,'home_recommend',12,0,'2026-10-27 17:05:55'),(429,NULL,123,12,'home_recommend',13,0,'2026-10-27 17:05:55'),(430,NULL,101,46,'home_recommend',1,0,'2026-10-27 17:06:28'),(431,NULL,101,26,'home_recommend',2,0,'2026-10-27 17:06:28'),(432,NULL,101,4,'home_recommend',3,0,'2026-10-27 17:06:28'),(433,NULL,101,6,'home_recommend',4,0,'2026-10-27 17:06:28'),(434,NULL,101,18,'home_recommend',5,0,'2026-10-27 17:06:28'),(435,NULL,101,5,'home_recommend',6,0,'2026-10-27 17:06:28'),(436,NULL,101,2,'home_recommend',7,0,'2026-10-27 17:06:28'),(437,NULL,101,47,'home_recommend',8,0,'2026-10-27 17:06:28'),(438,NULL,101,3,'home_recommend',9,0,'2026-10-27 17:06:28'),(439,NULL,101,9,'home_recommend',10,0,'2026-10-27 17:06:28'),(440,NULL,101,14,'home_recommend',11,0,'2026-10-27 17:06:28'),(441,NULL,101,10,'home_recommend',12,0,'2026-10-27 17:06:28'),(442,NULL,101,12,'home_recommend',13,0,'2026-10-27 17:06:28'),(443,1,NULL,47,'home_recommend',1,0,'2026-10-29 14:19:50'),(444,1,NULL,46,'home_recommend',2,0,'2026-10-29 14:19:50'),(445,1,NULL,26,'home_recommend',3,0,'2026-10-29 14:19:50'),(446,1,NULL,4,'home_recommend',4,0,'2026-10-29 14:19:50'),(447,1,NULL,6,'home_recommend',5,0,'2026-10-29 14:19:50'),(448,1,NULL,18,'home_recommend',6,0,'2026-10-29 14:19:50'),(449,1,NULL,3,'home_recommend',7,0,'2026-10-29 14:19:50'),(450,1,NULL,9,'home_recommend',8,0,'2026-10-29 14:19:50'),(451,1,NULL,5,'home_recommend',9,0,'2026-10-29 14:19:50'),(452,1,NULL,2,'home_recommend',10,0,'2026-10-29 14:19:50'),(453,1,NULL,14,'home_recommend',11,0,'2026-10-29 14:19:50'),(454,1,NULL,10,'home_recommend',12,0,'2026-10-29 14:19:50'),(455,1,NULL,12,'home_recommend',13,0,'2026-10-29 14:19:50'),(456,1,101,46,'home_recommend',1,0,'2026-10-29 14:20:10'),(457,1,101,26,'home_recommend',2,0,'2026-10-29 14:20:10'),(458,1,101,4,'home_recommend',3,0,'2026-10-29 14:20:10'),(459,1,101,6,'home_recommend',4,0,'2026-10-29 14:20:10'),(460,1,101,18,'home_recommend',5,0,'2026-10-29 14:20:10'),(461,1,101,5,'home_recommend',6,0,'2026-10-29 14:20:10'),(462,1,101,2,'home_recommend',7,0,'2026-10-29 14:20:10'),(463,1,101,47,'home_recommend',8,0,'2026-10-29 14:20:10'),(464,1,101,3,'home_recommend',9,0,'2026-10-29 14:20:10'),(465,1,101,9,'home_recommend',10,0,'2026-10-29 14:20:10'),(466,1,101,14,'home_recommend',11,0,'2026-10-29 14:20:10'),(467,1,101,10,'home_recommend',12,0,'2026-10-29 14:20:10'),(468,1,101,12,'home_recommend',13,0,'2026-10-29 14:20:10');
/*!40000 ALTER TABLE `recommend_impression_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `report`
--

DROP TABLE IF EXISTS `report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint NOT NULL,
  `target_type` varchar(20) NOT NULL COMMENT 'product/feed/user',
  `target_id` bigint NOT NULL,
  `reason` varchar(500) NOT NULL,
  `evidence_images` text,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending/done',
  `action` varchar(20) DEFAULT NULL COMMENT 'dismiss/warn/remove_product/remove_feed/ban_user',
  `handle_note` varchar(500) DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `handle_time` datetime DEFAULT NULL,
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_reporter_target` (`reporter_id`,`target_type`,`target_id`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_status_time` (`status`,`create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report`
--

LOCK TABLES `report` WRITE;
/*!40000 ALTER TABLE `report` DISABLE KEYS */;
/*!40000 ALTER TABLE `report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `search_keyword_log`
--

DROP TABLE IF EXISTS `search_keyword_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `search_keyword_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID(单租户模式可空)',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID(匿名搜索为空)',
  `keyword` varchar(60) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索关键词流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `search_keyword_log`
--

LOCK TABLES `search_keyword_log` WRITE;
/*!40000 ALTER TABLE `search_keyword_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `search_keyword_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seller_account`
--

DROP TABLE IF EXISTS `seller_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seller_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL COMMENT '卖家 sys_user.user_id',
  `tenant_id` bigint NOT NULL COMMENT '租户ID(sys_tenant.id)，与订单对齐',
  `account_name` varchar(64) DEFAULT NULL COMMENT '实名姓名',
  `id_card_no` varchar(255) DEFAULT NULL COMMENT '证件号密文(AES/GCM)',
  `bank_card_no` varchar(255) DEFAULT NULL COMMENT '银行卡号密文(AES/GCM)',
  `bank_name` varchar(64) DEFAULT NULL COMMENT '开户行',
  `kyc_status` varchar(16) NOT NULL DEFAULT 'unverified' COMMENT 'unverified/pending/verified/rejected',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额（账本结果，非资金池）',
  `frozen` tinyint NOT NULL DEFAULT '0' COMMENT '1=冻结（负余额/对账差异），禁止提现与新打款',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `unfreeze_reason` varchar(255) DEFAULT NULL COMMENT '人工解冻原因',
  `unfreeze_by` bigint DEFAULT NULL COMMENT '解冻操作人',
  `unfreeze_time` datetime DEFAULT NULL COMMENT '解冻时间',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_seller_tenant` (`seller_id`,`tenant_id`),
  KEY `idx_kyc_status` (`kyc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卖家收款账户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seller_account`
--

LOCK TABLES `seller_account` WRITE;
/*!40000 ALTER TABLE `seller_account` DISABLE KEYS */;
/*!40000 ALTER TABLE `seller_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seller_balance_flow`
--

DROP TABLE IF EXISTS `seller_balance_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seller_balance_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `refund_id` bigint DEFAULT NULL,
  `withdraw_id` bigint DEFAULT NULL,
  `type` varchar(16) NOT NULL COMMENT 'settle_in/clawback/withdraw_out/adjust',
  `amount` decimal(12,2) NOT NULL COMMENT '正数入账，负数扣减',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额（对账用）',
  `biz_key` varchar(64) NOT NULL COMMENT '幂等键 settle:{id}/clawback:{refundId}/withdraw:{id}/withdraw_reject:{id}',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_account_time` (`account_id`,`create_time`),
  KEY `idx_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卖家余额账本（只追加）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seller_balance_flow`
--

LOCK TABLES `seller_balance_flow` WRITE;
/*!40000 ALTER TABLE `seller_balance_flow` DISABLE KEYS */;
/*!40000 ALTER TABLE `seller_balance_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sensitive_word`
--

DROP TABLE IF EXISTS `sensitive_word`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(100) NOT NULL,
  `level` tinyint(1) NOT NULL DEFAULT '3' COMMENT '1-5',
  `scene` varchar(50) NOT NULL DEFAULT 'all' COMMENT 'all/im/feed/product',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scene` (`scene`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词词库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sensitive_word`
--

LOCK TABLES `sensitive_word` WRITE;
/*!40000 ALTER TABLE `sensitive_word` DISABLE KEYS */;
INSERT INTO `sensitive_word` VALUES (1,'wechat',4,'im',NULL),(2,'微信',4,'im',NULL),(3,'QQ',3,'all',NULL),(4,'加微',5,'im',NULL),(5,'加我',3,'im',NULL),(6,'私聊',3,'im',NULL),(7,'转账',5,'im',NULL),(8,'支付宝账号',5,'im',NULL),(9,'银行卡',4,'im',NULL),(10,'1[3-9]\\d{9}',5,'all',NULL);
/*!40000 ALTER TABLE `sensitive_word` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `settlement_order`
--

DROP TABLE IF EXISTS `settlement_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_order` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '商品订单ID',
  `refund_id` bigint DEFAULT NULL COMMENT '关联退款单（closed/clawback 时写）',
  `seller_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `gross_amount` decimal(12,2) NOT NULL COMMENT '订单实付金额',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '平台佣金',
  `net_amount` decimal(12,2) NOT NULL COMMENT '应结给卖家=gross-commission',
  `status` varchar(20) NOT NULL DEFAULT 'holding' COMMENT 'holding/pending_settle/settled/withdrawn/closed/clawback',
  `done_at` datetime DEFAULT NULL COMMENT '订单完成时间（扫描时从 trade_order 派生）',
  `hold_until` datetime DEFAULT NULL COMMENT 'done_at + hold-days',
  `settled_at` datetime DEFAULT NULL,
  `channel` varchar(16) DEFAULT NULL COMMENT 'mock/alipay/wechat',
  `channel_trans_no` varchar(64) DEFAULT NULL COMMENT '渠道结算流水号',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '打款失败次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  UNIQUE KEY `uk_channel_trans` (`channel`,`channel_trans_no`),
  KEY `idx_status_hold` (`status`,`hold_until`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算单（一单一结算）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `settlement_order`
--

LOCK TABLES `settlement_order` WRITE;
/*!40000 ALTER TABLE `settlement_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `settlement_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `settlement_recon_diff`
--

DROP TABLE IF EXISTS `settlement_recon_diff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_recon_diff` (
  `diff_id` bigint NOT NULL AUTO_INCREMENT,
  `biz_date` date NOT NULL COMMENT '对账业务日',
  `type` varchar(24) NOT NULL COMMENT 'missing_settlement/phantom_settlement/amount_mismatch/status_mismatch/duplicate_settlement/ledger_mismatch',
  `biz_key` varchar(96) NOT NULL COMMENT '本类型内幂等键',
  `order_id` bigint DEFAULT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `seller_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `channel_amount` decimal(12,2) DEFAULT NULL,
  `platform_amount` decimal(12,2) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'open' COMMENT 'open/resolved',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_note` varchar(255) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`diff_id`),
  UNIQUE KEY `uk_diff` (`biz_date`,`type`,`biz_key`),
  KEY `idx_status_date` (`status`,`biz_date`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算对账差异队列';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `settlement_recon_diff`
--

LOCK TABLES `settlement_recon_diff` WRITE;
/*!40000 ALTER TABLE `settlement_recon_diff` DISABLE KEYS */;
/*!40000 ALTER TABLE `settlement_recon_diff` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (100,0,'0','邻里二手',1,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:36'),(101,100,'0,100','深圳总公司',1,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:28'),(102,100,'0,100','长沙分公司',2,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:36'),(103,101,'0,100,101','研发部门',1,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:00'),(104,101,'0,100,101','市场部门',2,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:04'),(105,101,'0,100,101','测试部门',3,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:07'),(106,101,'0,100,101','财务部门',4,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:24'),(107,101,'0,100,101','运维部门',5,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:28'),(108,102,'0,100,102','市场部门',1,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:33'),(109,102,'0,100,102','财务部门',2,'','','','0','0','admin','2018-03-16 11:33:00','demo','2025-07-21 18:41:36'),(110,101,'0,100,101','客服中心',6,'小李','13800001004','cs@weiwei.com','0','0','admin','2026-10-27 16:53:07','',NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dfs`
--

DROP TABLE IF EXISTS `sys_dfs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dfs` (
  `file_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名称',
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件原名称',
  `space_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件分组',
  `path` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件路径',
  `size` int NOT NULL COMMENT '文件大小',
  `type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件类型',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`file_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='分布式文件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dfs`
--

LOCK TABLES `sys_dfs` WRITE;
/*!40000 ALTER TABLE `sys_dfs` DISABLE KEYS */;
INSERT INTO `sys_dfs` VALUES (1,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-06-27/1782530904870.jpg',329547,'jpg','2026-06-27 11:28:28'),(2,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-02/1782972931972.png',36111,'png','2026-07-02 14:15:32'),(3,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-02/1782973288668.png',36111,'png','2026-07-02 14:21:28'),(4,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-03/1783046533161.png',36111,'png','2026-07-03 10:42:13'),(5,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-03/1783047198555.png',36111,'png','2026-07-03 10:53:18'),(6,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-03/1783047731567.png',36111,'png','2026-07-03 11:02:11'),(7,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-03/1783048321946.png',36111,'png','2026-07-03 11:12:02'),(8,'downloaded-image.png','downloaded-image.png',NULL,'/2026-07-03/1783051033926.png',36111,'png','2026-07-03 11:57:14'),(9,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-07-11/1783726304917.jpg',124666,'jpg','2026-07-11 07:31:46'),(10,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-04/1785834069361.jpg',124666,'jpg','2026-08-04 17:01:10'),(11,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-06/1786000110147.jpg',329547,'jpg','2026-08-06 15:08:31'),(12,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-06/1786000998513.jpg',329547,'jpg','2026-08-06 15:23:18'),(13,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-06/1786001771373.jpg',329547,'jpg','2026-08-06 15:36:12'),(14,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-06/1786002131363.jpg',329547,'jpg','2026-08-06 15:42:11'),(15,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-06/1786003270533.jpg',329547,'jpg','2026-08-06 16:01:11'),(16,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-06/1786003770910.jpg',124666,'jpg','2026-08-06 16:09:31'),(17,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-06/1786003900781.jpg',124666,'jpg','2026-08-06 16:11:41'),(18,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-06/1786004600710.jpg',124666,'jpg','2026-08-06 16:23:21'),(19,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-06/1786006928353.jpg',124666,'jpg','2026-08-06 17:02:08'),(20,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-06/1786009562911.jpg',124666,'jpg','2026-08-06 17:46:03'),(21,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-10/1786329450782.jpg',329547,'jpg','2026-08-10 10:37:31'),(22,'2.png','2.png',NULL,'/2026-08-10/1786330715467.png',63083,'png','2026-08-10 10:58:35'),(23,'1c4b06247425af7731aa27acee99e3b.jpg','1c4b06247425af7731aa27acee99e3b.jpg',NULL,'/2026-08-11/1786418405031.jpg',329547,'jpg','2026-08-11 11:20:05'),(24,'v2-d021024a102298f45f299544faf32735_1440w.jpg','v2-d021024a102298f45f299544faf32735_1440w.jpg',NULL,'/2026-08-11/1786420015755.jpg',124666,'jpg','2026-08-11 11:46:56'),(25,'OIP-C.webp','OIP-C.webp',NULL,'/2026-08-11/1786420527155.webp',19004,'webp','2026-08-11 11:55:27'),(26,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-01/1788266947088.jpg',5164832,'jpg','2026-09-01 20:49:07'),(27,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-01/1788267279328.jpg',5164832,'jpg','2026-09-01 20:54:39'),(28,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-02/1788349995592.jpg',5164832,'jpg','2026-09-02 19:53:16'),(29,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-10/1788994319303.jpg',5164832,'jpg','2026-09-10 06:51:59'),(30,'wings zenless_zone_zero.jpg','wings zenless_zone_zero.jpg',NULL,'/2026-09-10/1788996640327.jpg',7400058,'jpg','2026-09-10 07:30:40'),(31,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-10/1788999088304.jpg',5164832,'jpg','2026-09-10 08:11:28'),(32,'zenless_zone_zero.jpg','zenless_zone_zero.jpg',NULL,'/2026-09-11/1789139434533.jpg',5164832,'jpg','2026-09-11 23:10:35'),(33,'weiwitest.txt','weiwitest.txt',NULL,'/2026-09-16/1789572181564.txt',18,'txt','2026-09-16 23:23:02'),(34,'oss_probe_upload.png','oss_probe_upload.png',NULL,'/2026-09-23/1790133296085.png',70,'png','2026-09-23 11:14:56'),(35,'probe2.png','probe2.png',NULL,'/2026-09-23/1790133748352.png',70,'png','2026-09-23 11:22:28'),(36,'Screenshot_2026-09-16-13-36-34-93_7d4fe468b8a55b452fe6b82122d666b2.jpg','Screenshot_2026-09-16-13-36-34-93_7d4fe468b8a55b452fe6b82122d666b2.jpg',NULL,'/2026-09-23/1790153188463.jpg',2102971,'jpg','2026-09-23 16:46:32'),(37,'Snipaste_2026-10-07_19-40-28.png','Snipaste_2026-10-07_19-40-28.png',NULL,'/2026-10-07/1791373358617.png',957743,'png','2026-10-07 19:42:41'),(38,'Snipaste_2026-10-07_19-40-28.png','Snipaste_2026-10-07_19-40-28.png',NULL,'/2026-10-07/1791373363440.png',957743,'png','2026-10-07 19:42:44'),(39,'202504221745251995534699.jpeg','202504221745251995534699.jpeg',NULL,'/2026-10-07/1791373367526.jpeg',218579,'jpeg','2026-10-07 19:42:47');
/*!40000 ALTER TABLE `sys_dfs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=134 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'男','0','sys_user_sex','','','Y',0,'admin','2023-03-05 23:41:03','',NULL,'性别男'),(2,2,'女','1','sys_user_sex','','','N',0,'admin','2023-03-05 23:41:03','',NULL,'性别女'),(3,3,'未知','2','sys_user_sex','','','N',0,'admin','2023-03-05 23:41:03','',NULL,'性别未知'),(4,1,'显示','0','sys_show_hide','','primary','Y',0,'admin','2023-03-05 23:41:03','',NULL,'显示菜单'),(5,2,'隐藏','1','sys_show_hide','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'隐藏菜单'),(6,1,'正常','0','sys_normal_disable','','primary','Y',0,'admin','2023-03-05 23:41:03','',NULL,'正常状态'),(7,2,'停用','1','sys_normal_disable','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'停用状态'),(8,1,'正常','0','sys_job_status','','primary','Y',0,'admin','2023-03-05 23:41:03','',NULL,'正常状态'),(9,2,'暂停','1','sys_job_status','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'停用状态'),(10,1,'默认','DEFAULT','sys_job_group','','','Y',0,'admin','2023-03-05 23:41:03','',NULL,'默认分组'),(11,2,'系统','SYSTEM','sys_job_group','','','N',0,'admin','2023-03-05 23:41:03','',NULL,'系统分组'),(12,1,'是','Y','sys_yes_no','','primary','Y',0,'admin','2023-03-05 23:41:03','',NULL,'系统默认是'),(13,2,'否','N','sys_yes_no','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'系统默认否'),(14,1,'通知','1','sys_notice_type','','warning','Y',0,'admin','2023-03-05 23:41:03','',NULL,'通知'),(15,2,'公告','2','sys_notice_type','','success','N',0,'admin','2023-03-05 23:41:03','',NULL,'公告'),(16,1,'正常','0','sys_notice_status','','primary','Y',0,'admin','2023-03-05 23:41:03','',NULL,'正常状态'),(17,2,'关闭','1','sys_notice_status','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'关闭状态'),(18,99,'其他','0','sys_oper_type','','info','N',0,'admin','2023-03-05 23:41:03','',NULL,'其他操作'),(19,1,'新增','1','sys_oper_type','','info','N',0,'admin','2023-03-05 23:41:03','',NULL,'新增操作'),(20,2,'修改','2','sys_oper_type','','info','N',0,'admin','2023-03-05 23:41:03','',NULL,'修改操作'),(21,3,'删除','3','sys_oper_type','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'删除操作'),(22,4,'授权','4','sys_oper_type','','primary','N',0,'admin','2023-03-05 23:41:03','',NULL,'授权操作'),(23,5,'导出','5','sys_oper_type','','warning','N',0,'admin','2023-03-05 23:41:03','',NULL,'导出操作'),(24,6,'导入','6','sys_oper_type','','warning','N',0,'admin','2023-03-05 23:41:03','',NULL,'导入操作'),(25,7,'强退','7','sys_oper_type','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'强退操作'),(26,8,'生成代码','8','sys_oper_type','','warning','N',0,'admin','2023-03-05 23:41:03','',NULL,'生成操作'),(27,9,'清空数据','9','sys_oper_type','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'清空操作'),(28,1,'成功','0','sys_common_status','','primary','N',0,'admin','2023-03-05 23:41:03','',NULL,'正常状态'),(29,2,'失败','1','sys_common_status','','danger','N',0,'admin','2023-03-05 23:41:03','',NULL,'停用状态'),(30,1,'密码模式','password','sys_oauth_client_details',NULL,NULL,'N',0,'admin','2021-01-12 22:09:15','','2021-01-12 22:09:27',NULL),(31,2,'刷新模式','refresh_token','sys_oauth_client_details',NULL,NULL,'N',0,'admin','2021-01-12 22:09:15','','2021-01-12 22:09:27',NULL),(32,3,'手机号码模式','phone_password','sys_oauth_client_details',NULL,NULL,'N',0,'admin','2021-01-12 22:09:15','','2021-01-12 22:09:27',NULL),(33,4,'授权码模式','authorization_code','sys_oauth_client_details',NULL,NULL,'N',0,'admin','2021-01-12 22:28:56','',NULL,NULL),(110,1,'mysql','mysql','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:50:48','',NULL,'mysql'),(111,2,'oracle','oracle','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:50:59','',NULL,'oracle'),(112,3,'db2','db2','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:51:09','',NULL,'db2'),(113,4,'mssql','mssql','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:51:19','',NULL,'mssql'),(114,5,'pg','pg','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:51:28','',NULL,'pg'),(115,6,'达梦','dm','ds_type',NULL,NULL,'N',0,'admin','2023-11-04 18:51:38','admin','2023-11-16 13:57:15','达梦'),(116,1,'主机','0','ds_config_type',NULL,NULL,'N',0,'admin','2023-11-04 19:04:15','',NULL,'主机'),(117,1,'JDBC','1','ds_config_type',NULL,NULL,'N',0,'admin','2023-11-04 19:04:27','',NULL,'JDBC'),(118,7,'瀚高','highgo','ds_type',NULL,NULL,'N',0,'admin','2023-11-16 13:58:38','admin','2023-11-16 13:58:42','瀚高数据库'),(119,1,'简体中文','zh_CN','i18n',NULL,NULL,'N',0,'admin','2024-03-26 21:16:31','',NULL,NULL),(120,2,'English US','en_US','i18n',NULL,NULL,'N',0,'admin','2024-03-26 21:17:03','',NULL,NULL),(121,1,'OpenAI','openai','ai_model_provider',NULL,NULL,'N',0,'admin','2026-06-25 12:50:20','',NULL,NULL),(122,2,'DeepSeek','deepseek','ai_model_provider',NULL,NULL,'N',0,'admin','2026-06-25 12:50:20','',NULL,NULL),(123,3,'智谱AI','zhipu','ai_model_provider',NULL,NULL,'N',0,'admin','2026-06-25 12:50:20','',NULL,NULL),(124,4,'Ollama','ollama','ai_model_provider',NULL,NULL,'N',0,'admin','2026-06-25 12:50:20','',NULL,NULL),(125,5,'Agnes AI','agnes','ai_model_provider',NULL,NULL,'N',0,'admin','2026-06-25 12:50:20','',NULL,NULL),(126,1,'大语言模型','LLM','ai_model_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(127,2,'嵌入模型','EMBEDDING','ai_model_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(128,3,'图像生成','IMAGE','ai_model_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(129,4,'视频生成','VIDEO','ai_model_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(130,1,'手动输入','INPUT','rag_source_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(131,2,'文件上传','UPLOAD','rag_source_type',NULL,NULL,'N',0,'admin','2026-06-25 12:50:21','',NULL,NULL),(132,6,'SenseAudio','senseaudio','ai_model_provider',NULL,NULL,'N',0,'admin','2026-09-19 07:22:20','',NULL,'商汤 SenseAudio 平台'),(133,7,'SenseNova','sensenova','ai_model_provider',NULL,NULL,'N',0,'admin','2026-09-19 07:22:20','',NULL,'商汤日日新 platform.sensenova.cn');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE KEY `dict_type` (`dict_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'用户性别','sys_user_sex',0,'admin','2023-03-05 23:40:55','',NULL,'用户性别列表'),(2,'菜单状态','sys_show_hide',0,'admin','2023-03-05 23:40:55','',NULL,'菜单状态列表'),(3,'系统开关','sys_normal_disable',0,'admin','2023-03-05 23:40:55','',NULL,'系统开关列表'),(4,'任务状态','sys_job_status',0,'admin','2023-03-05 23:40:55','',NULL,'任务状态列表'),(5,'任务分组','sys_job_group',0,'admin','2023-03-05 23:40:55','',NULL,'任务分组列表'),(6,'系统是否','sys_yes_no',0,'admin','2023-03-05 23:40:55','',NULL,'系统是否列表'),(7,'通知类型','sys_notice_type',0,'admin','2023-03-05 23:40:55','',NULL,'通知类型列表'),(8,'通知状态','sys_notice_status',0,'admin','2023-03-05 23:40:55','',NULL,'通知状态列表'),(9,'操作类型','sys_oper_type',0,'admin','2023-03-05 23:40:55','',NULL,'操作类型列表'),(10,'系统状态','sys_common_status',0,'admin','2023-03-05 23:40:55','',NULL,'登录状态列表'),(11,'OAuth2终端','sys_oauth_client_details',0,'admin','2023-03-05 11:33:00','','2023-03-05 22:07:34','OAuth2终端授权模式'),(104,'代码生成器支持的数据库类型','ds_type',0,'admin','2023-11-04 18:50:11','admin','2023-11-04 18:50:22','代码生成器支持的数据库类型'),(105,'数据库链接类型','ds_config_type',0,'admin','2023-11-04 19:03:57','',NULL,'数据库链接类型'),(106,'国际化','i18n',0,'admin','2024-03-26 21:15:49','',NULL,'国际化i18n'),(107,'AI模型供应商','ai_model_provider',0,'admin','2026-06-25 12:50:20','',NULL,'AI模型供应商列表'),(108,'AI模型类型','ai_model_type',0,'admin','2026-06-25 12:50:20','',NULL,'AI模型类型列表'),(109,'AI文档来源类型','rag_source_type',0,'admin','2026-06-25 12:50:21','',NULL,'AI文档来源类型');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` tinyint(1) DEFAULT NULL COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` tinyint(1) DEFAULT NULL COMMENT '是否并发执行（1允许 0禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=131 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'系统默认（无参）','DEFAULT','twtTask.twtNoParams','0 */15 * * * ?',3,1,'1','admin','2018-03-16 11:33:00','admin','2021-08-06 23:02:47',''),(106,'系统默认（有参）','DEFAULT','twtTask.twtParams(\'twt\')','0/5 * * * * ?',1,1,'1','admin','2020-12-10 23:37:38','admin','2021-08-06 23:02:45',''),(107,'互助任务超时未接单扫描','DEFAULT','runnerTask.scanTimeoutPending','0 */5 * * * ?',3,1,'1','admin','2026-09-04 23:37:36','admin','2026-09-04 23:37:36','每5分钟扫超时未接单任务，自动取消并原路退款(失败由退款对账任务兜底)'),(108,'互助任务超时未确认自动确认','DEFAULT','runnerTask.scanAutoConfirm','0 0 * * * ?',3,1,'1','admin','2026-09-04 23:37:36','admin','2026-09-04 23:37:36','每小时扫一次 pending_confirm 超 3 天的任务，自动确认完成'),(109,'AI社区日报生成','DEFAULT','aiDailyReport.generateAll','0 0 7 * * ?',3,1,'1','admin','2026-09-06 10:14:04','admin','2026-09-06 10:14:04','每天07:00为每个启用租户生成社区日报并IM推送'),(110,'推荐-用户兴趣画像刷新','DEFAULT','productRecommend.refreshInterestStat','0 */30 * * * ?',3,1,'1','admin','2026-09-06 19:53:59','admin','2026-09-06 19:53:59','每30分钟逐租户重建用户兴趣画像(浏览1/收藏3/下单5,半衰期14天)'),(111,'推荐-商品热度分刷新','DEFAULT','productRecommend.refreshHotScore','0 0 * * * ?',3,1,'1','admin','2026-09-06 19:53:59','admin','2026-09-06 19:53:59','每小时按 gravity 公式全量刷新商品热度分'),(112,'推荐-商品向量索引同步','DEFAULT','productRecommend.syncVectors','0 40 * * * ?',3,1,'1','admin','2026-09-06 19:53:59','admin','2026-09-06 19:53:59','每小时增量同步商品向量索引(标题+类目+描述, bge-m3; 无变更时仅一次轻量 SELECT)'),(113,'推荐-收藏降价提醒扫描','DEFAULT','productRecommend.scanPriceDrop','0 20 * * * ?',3,1,'1','admin','2026-09-06 20:24:23','admin','2026-09-06 20:24:23','每小时对比收藏价格快照与现价，降价则 IM 通知收藏者并刷新快照(幂等,不重复提醒)'),(114,'推荐-商品日统计聚合','DEFAULT','productRecommend.refreshDailyStats','0 0 5 * * ?',3,1,'1','admin','2026-09-06 20:50:53','admin','2026-09-06 20:50:53','每日05:00把昨日浏览明细聚合进 product_stats_daily(幂等覆盖)'),(115,'推荐-日志过期清理','DEFAULT','productRecommend.purgeExpiredLogs','0 50 3 * * ?',3,1,'1','admin','2026-09-06 20:50:53','admin','2026-09-06 20:50:53','每日03:50清理90天前的浏览/曝光/搜索词/负反馈流水'),(116,'推荐-好物日报推送','DEFAULT','productRecommend.pushDailyPicks','0 30 7 * * ?',3,1,'1','admin','2026-09-06 20:50:53','admin','2026-09-06 20:50:53','每日07:30按用户兴趣挑3件好物 IM 推送(仅推有画像用户)'),(117,'AI初审-商品','DEFAULT','productAiModeration.auditProducts','0 */10 * * * ?',3,1,'1','admin','2026-09-07 00:09:33','admin','2026-09-07 00:09:33','每10分钟扫描待审商品调AI初审：low自动通过/high自动驳回+IM通知/medium标注留人工(见 docs/AI初审与发布助手设计.md)'),(118,'AI初审-动态','DEFAULT','productAiModeration.auditFeeds','0 5/10 * * * ?',3,1,'1','admin','2026-09-07 00:09:33','admin','2026-09-07 00:09:33','每10分钟(错峰5分)扫描待审动态调AI初审：low自动通过/high自动驳回+IM通知/medium标注留人工'),(119,'交易-超时未支付关单','DEFAULT','tradeTimeout.cancelExpiredUnpaid','0 */5 * * * ?',3,1,'1','admin','2026-10-21 14:15:44','admin','2026-10-21 14:15:44','每5分钟扫描下单超30分钟未支付订单，原子关单并回补库存(仅status=unpaid命中,rows=1才回补,防与买家取消并发双倍回补)'),(120,'交易-发货超时自动确认收货','DEFAULT','tradeTimeout.autoConfirmReceipt','0 */5 * * * ?',3,1,'1','admin','2026-10-21 14:15:44','admin','2026-10-21 14:15:44','每5分钟扫描发货超7天未确认的订单，自动确认收货并标记商品已售(仅status=shipped命中,rows=1才标记)'),(121,'换物物品AI初审扫描','DEFAULT','exchangeAiModeration.auditItems','0 */10 * * * ?',3,1,'1','admin','2026-10-27 16:53:01','',NULL,'每10分钟扫一次auditing换物物品走AI初审三分流（low过/high驳/medium留人工）'),(122,'换物意向超时扫描','DEFAULT','exchangeTask.scanTimeoutOffer','0 */5 * * * ?',3,1,'1','admin','2026-10-27 16:53:01','',NULL,'每5分钟扫一次pending意向，72h未处理自动过期'),(123,'换物订单超时扫描','DEFAULT','exchangeTask.scanTimeoutOrder','0 0 * * * ?',3,1,'1','admin','2026-10-27 16:53:01','',NULL,'每小时扫一次订单：建单7天未双交付取消；双交付后3天未双确认自动完成'),(124,'换物向量索引增量同步','DEFAULT','exchangeMatchTask.syncVectors','0 */30 * * * ?',3,1,'1','admin','2026-10-27 16:53:01','',NULL,'每30分钟调 ai-service /exchange-match/reindex 增量同步双索引（镜像 productRecommend.syncVectors）'),(125,'换物反向匹配每日推送','DEFAULT','exchangeTask.scanMatchPush','0 0 10 * * ?',3,1,'1','admin','2026-10-27 16:53:01','',NULL,'每日10点反向匹配扫描，快照diff去重后 IM 推送\"有人想换你的物品\"（镜像 productRecommend.scanPriceDrop）'),(126,'退款-超时扫描','DEFAULT','tradeRefundScan.scan','0 0/30 * * * ?',3,1,'1','admin','2026-10-30 16:07:17','',NULL,'pending_seller 48h→mediation；seller_agreed 7d→closed；buyer_returned 7d→mediation'),(127,'结算-售后期扫描','DEFAULT','settlementScan.scan','0 50 1 * * ?',3,1,'1','admin','2026-11-02 16:03:37','',NULL,'holding→pending_settle：hold_until 到期且订单 done、无进行中退款、卖家已实名'),(128,'结算-每日打款批次','DEFAULT','settlementPayout.payout','0 0 2 * * ?',3,1,'1','admin','2026-11-02 16:03:37','',NULL,'pending_settle→settled：MockPayoutGateway，冻结/未实名/失败超限跳过'),(129,'结算-每日对账','DEFAULT','settlementRecon.reconcile','0 30 2 * * ?',3,1,'1','admin','2026-11-02 16:03:37','',NULL,'六类差异入队并冻结相关卖家'),(130,'互助-退款对账补偿','DEFAULT','runnerTask.reconcileStalePaidRefunds','0 0/30 * * * ?',3,1,'1','admin','2026-11-03 16:17:05','',NULL,'每30分钟扫cancelled且pay_status=paid的滞留任务，兜底重试退款(幂等: refund非paid拒绝+仅paid可翻)');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '异常信息',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
INSERT INTO `sys_job_log` VALUES (1,'AI社区日报生成','DEFAULT','aiDailyReport.generateAll','AI社区日报生成 总共耗时：21毫秒','0','','2026-09-06 10:40:08');
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_info`
--

DROP TABLE IF EXISTS `sys_login_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_info` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录IP地址',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '登录状态（0登录成功 1登录失败 2成功退出）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '提示信息',
  `access_time` datetime DEFAULT NULL COMMENT '访问时间',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  PRIMARY KEY (`info_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=456 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_info`
--

LOCK TABLES `sys_login_info` WRITE;
/*!40000 ALTER TABLE `sys_login_info` DISABLE KEYS */;
INSERT INTO `sys_login_info` VALUES (1,'admin','127.0.0.1','0','登录成功','2026-06-05 16:28:33',101),(2,'admin','127.0.0.1','0','退出登录','2026-06-05 16:29:47',101),(3,'demo','127.0.0.1','0','登录成功','2026-06-05 16:30:38',103),(4,'admin','127.0.0.1','0','登录成功','2026-06-05 16:30:45',101),(5,'admin','127.0.0.1','0','退出登录','2026-06-05 16:30:48',101),(6,'demo','127.0.0.1','0','登录成功','2026-06-05 16:30:53',103),(7,'admin','127.0.0.1','0','登录成功','2026-06-05 16:37:47',101),(8,'admin','127.0.0.1','0','退出登录','2026-06-05 17:02:55',101),(9,'admin','127.0.0.1','0','登录成功','2026-06-05 17:02:59',101),(10,'admin','127.0.0.1','0','登录成功','2026-06-05 17:41:07',101),(11,'user_demo','127.0.0.1','0','登录成功','2026-06-08 13:04:08',NULL),(12,'user_demo','127.0.0.1','0','退出登录','2026-06-08 13:04:12',NULL),(13,'user_demo','127.0.0.1','0','登录成功','2026-06-08 13:04:42',NULL),(14,'user_demo','127.0.0.1','0','退出登录','2026-06-08 13:07:42',102),(15,'user_demo','127.0.0.1','0','登录成功','2026-06-08 13:08:51',102),(16,'cs_demo','127.0.0.1','0','登录成功','2026-06-08 13:11:11',106),(17,'ops_demo','127.0.0.1','0','登录成功','2026-06-08 13:22:49',105),(18,'user_demo','127.0.0.1','0','登录成功','2026-06-08 13:22:57',102),(19,'user_demo','127.0.0.1','0','登录成功','2026-06-08 15:14:30',102),(20,'user_demo','127.0.0.1','0','退出登录','2026-06-08 15:21:16',102),(21,'user_demo','127.0.0.1','0','登录成功','2026-06-08 15:21:18',102),(22,'user_demo','127.0.0.1','0','退出登录','2026-06-08 15:26:45',102),(23,'admin','127.0.0.1','0','登录成功','2026-06-08 15:26:50',101),(24,'admin','127.0.0.1','0','退出登录','2026-06-08 15:27:35',101),(25,'user_demo','127.0.0.1','0','登录成功','2026-06-08 15:27:37',102),(26,'user_demo','127.0.0.1','0','退出登录','2026-06-08 15:31:35',102),(27,'admin','127.0.0.1','0','登录成功','2026-06-08 15:31:41',101),(28,'admin','127.0.0.1','0','退出登录','2026-06-08 15:54:03',101),(29,'user_demo','127.0.0.1','0','登录成功','2026-06-08 15:54:05',102),(30,'user_demo','127.0.0.1','0','登录成功','2026-06-09 11:53:30',102),(31,'user_demo','127.0.0.1','0','退出登录','2026-06-09 11:53:42',102),(32,'admin','127.0.0.1','0','登录成功','2026-06-09 11:53:48',101),(33,'admin','127.0.0.1','0','退出登录','2026-06-09 11:57:48',101),(34,'user_demo','127.0.0.1','0','登录成功','2026-06-09 11:57:49',102),(35,'user_demo','127.0.0.1','0','登录成功','2026-06-09 12:00:30',102),(36,'user_demo','127.0.0.1','0','退出登录','2026-06-09 12:00:37',102),(37,'user_demo','127.0.0.1','0','登录成功','2026-06-09 12:00:38',102),(38,'user_demo','127.0.0.1','0','退出登录','2026-06-09 12:00:45',102),(39,'user_demo','127.0.0.1','0','登录成功','2026-06-09 12:00:48',102),(40,'user_demo','127.0.0.1','0','退出登录','2026-06-09 13:36:22',102),(41,'admin','127.0.0.1','0','登录成功','2026-06-09 13:36:29',101),(42,'admin','127.0.0.1','0','退出登录','2026-06-09 13:40:35',101),(43,'user_demo','127.0.0.1','0','登录成功','2026-06-09 13:40:37',102),(44,'user_demo','127.0.0.1','0','退出登录','2026-06-09 13:40:54',102),(45,'ops_demo','127.0.0.1','0','登录成功','2026-06-09 13:40:55',105),(46,'ops_demo','127.0.0.1','0','退出登录','2026-06-09 13:40:58',105),(47,'cs_demo','127.0.0.1','0','登录成功','2026-06-09 13:41:00',106),(48,'cs_demo','127.0.0.1','0','退出登录','2026-06-09 13:44:06',106),(49,'admin','127.0.0.1','0','登录成功','2026-06-09 13:44:07',101),(50,'admin','127.0.0.1','0','退出登录','2026-06-09 13:44:41',101),(51,'ops_demo','127.0.0.1','0','登录成功','2026-06-09 13:44:43',105),(52,'ops_demo','127.0.0.1','0','退出登录','2026-06-09 13:47:00',105),(53,'cs_demo','127.0.0.1','0','登录成功','2026-06-09 13:48:06',106),(54,'cs_demo','127.0.0.1','0','退出登录','2026-06-09 13:48:53',106),(55,'admin','127.0.0.1','0','登录成功','2026-06-09 13:48:54',101),(56,'admin','127.0.0.1','0','退出登录','2026-06-09 14:07:43',101),(57,'user_demo','127.0.0.1','0','登录成功','2026-06-09 14:07:44',102),(58,'user_demo','127.0.0.1','0','退出登录','2026-06-09 14:07:52',102),(59,'ops_demo','127.0.0.1','0','登录成功','2026-06-09 14:07:54',105),(60,'ops_demo','127.0.0.1','0','退出登录','2026-06-09 14:08:59',105),(61,'user_demo','127.0.0.1','0','登录成功','2026-06-09 14:09:00',102),(62,'user_demo','127.0.0.1','0','登录成功','2026-06-09 14:19:02',102),(63,'user_demo','127.0.0.1','0','退出登录','2026-06-09 14:19:24',102),(64,'cs_demo','127.0.0.1','0','登录成功','2026-06-09 14:19:25',106),(65,'user_demo','127.0.0.1','0','登录成功','2026-06-10 13:45:27',102),(66,'user_demo','127.0.0.1','0','退出登录','2026-06-10 14:36:38',102),(67,'admin','127.0.0.1','0','登录成功','2026-06-10 14:36:44',101),(68,'admin','127.0.0.1','0','退出登录','2026-06-10 14:37:06',101),(69,'cs_demo','127.0.0.1','0','登录成功','2026-06-10 14:37:07',106),(70,'cs_demo','127.0.0.1','0','退出登录','2026-06-10 14:37:12',106),(71,'admin','127.0.0.1','0','登录成功','2026-06-10 14:37:14',101),(72,'admin','127.0.0.1','0','退出登录','2026-06-11 14:22:45',101),(73,'user_demo','127.0.0.1','0','登录成功','2026-06-11 14:22:46',102),(74,'user_demo','127.0.0.1','0','退出登录','2026-06-11 14:23:55',102),(75,'admin','127.0.0.1','0','登录成功','2026-06-11 14:23:56',101),(76,'admin','127.0.0.1','0','退出登录','2026-06-11 14:25:57',101),(77,'user_demo','127.0.0.1','0','登录成功','2026-06-11 14:25:58',102),(78,'user_demo','127.0.0.1','0','退出登录','2026-06-11 14:26:06',102),(79,'admin','127.0.0.1','0','登录成功','2026-06-11 14:26:07',101),(80,'admin','127.0.0.1','0','退出登录','2026-06-11 14:26:37',101),(81,'user_demo','127.0.0.1','0','登录成功','2026-06-11 14:26:39',102),(82,'user_demo','127.0.0.1','0','退出登录','2026-06-11 14:27:09',102),(83,'admin','127.0.0.1','0','登录成功','2026-06-11 14:27:10',101),(84,'admin','127.0.0.1','0','退出登录','2026-06-11 14:28:03',101),(85,'user_demo','127.0.0.1','0','登录成功','2026-06-11 14:28:04',102),(86,'user_demo','127.0.0.1','0','退出登录','2026-06-11 14:28:07',102),(87,'admin','127.0.0.1','0','登录成功','2026-06-11 14:28:09',101),(88,'admin','127.0.0.1','0','退出登录','2026-06-11 14:28:30',101),(89,'ops_demo','127.0.0.1','0','登录成功','2026-06-11 14:28:31',105),(90,'ops_demo','127.0.0.1','0','退出登录','2026-06-11 14:28:34',105),(91,'admin','127.0.0.1','0','登录成功','2026-06-11 14:28:35',101),(92,'admin','127.0.0.1','0','退出登录','2026-06-11 14:29:00',101),(93,'ops_demo','127.0.0.1','0','登录成功','2026-06-11 14:29:02',105),(94,'ops_demo','127.0.0.1','0','退出登录','2026-06-11 14:31:16',105),(95,'admin','127.0.0.1','0','登录成功','2026-06-11 14:31:17',101),(96,'admin','127.0.0.1','0','退出登录','2026-06-11 16:01:07',101),(97,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:01:09',102),(98,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:01:15',102),(99,'admin','127.0.0.1','0','登录成功','2026-06-11 16:01:16',101),(100,'admin','127.0.0.1','0','退出登录','2026-06-11 16:01:47',101),(101,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:01:48',102),(102,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:02:00',102),(103,'admin','127.0.0.1','0','登录成功','2026-06-11 16:02:01',101),(104,'admin','127.0.0.1','0','退出登录','2026-06-11 16:02:26',101),(105,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:02:27',102),(106,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:02:39',102),(107,'admin','127.0.0.1','0','登录成功','2026-06-11 16:02:41',101),(108,'admin','127.0.0.1','0','退出登录','2026-06-11 16:11:29',101),(109,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:11:30',102),(110,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:11:50',102),(111,'ops_demo','127.0.0.1','0','登录成功','2026-06-11 16:11:52',105),(112,'ops_demo','127.0.0.1','0','退出登录','2026-06-11 16:11:56',105),(113,'cs_demo','127.0.0.1','0','登录成功','2026-06-11 16:11:57',106),(114,'cs_demo','127.0.0.1','0','退出登录','2026-06-11 16:18:11',106),(115,'admin','127.0.0.1','0','登录成功','2026-06-11 16:18:12',101),(116,'admin','127.0.0.1','0','退出登录','2026-06-11 16:30:59',101),(117,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:31:02',102),(118,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:39:58',102),(119,'ops_demo','127.0.0.1','0','登录成功','2026-06-11 16:39:59',105),(120,'ops_demo','127.0.0.1','0','退出登录','2026-06-11 16:42:33',105),(121,'cs_demo','127.0.0.1','0','登录成功','2026-06-11 16:42:34',106),(122,'cs_demo','127.0.0.1','0','退出登录','2026-06-11 16:44:10',106),(123,'admin','127.0.0.1','0','登录成功','2026-06-11 16:44:11',101),(124,'admin','127.0.0.1','0','退出登录','2026-06-11 16:44:23',101),(125,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:44:25',102),(126,'user_demo','127.0.0.1','0','退出登录','2026-06-11 16:59:06',102),(127,'admin','127.0.0.1','0','登录成功','2026-06-11 16:59:08',101),(128,'admin','127.0.0.1','0','退出登录','2026-06-11 16:59:37',101),(129,'user_demo','127.0.0.1','0','登录成功','2026-06-11 16:59:38',102),(130,'user_demo','127.0.0.1','0','退出登录','2026-06-12 11:31:52',102),(131,'ops_demo','127.0.0.1','0','登录成功','2026-06-12 11:31:54',105),(132,'ops_demo','127.0.0.1','0','退出登录','2026-06-12 11:34:24',105),(133,'user_demo','127.0.0.1','0','登录成功','2026-06-12 11:34:25',102),(134,'user_demo','127.0.0.1','0','退出登录','2026-06-12 14:52:09',102),(135,'ops_demo','127.0.0.1','0','登录成功','2026-06-12 14:52:11',105),(136,'ops_demo','127.0.0.1','0','退出登录','2026-06-12 14:52:28',105),(137,'user_demo','127.0.0.1','0','登录成功','2026-06-12 15:03:23',102),(138,'user_demo','127.0.0.1','0','登录成功','2026-06-12 15:40:07',102),(139,'user_demo','127.0.0.1','0','退出登录','2026-06-12 17:32:24',102),(140,'ops_demo','127.0.0.1','0','登录成功','2026-06-12 17:32:27',105),(141,'ops_demo','127.0.0.1','0','退出登录','2026-06-12 17:32:35',105),(142,'user_demo','127.0.0.1','0','登录成功','2026-06-12 17:32:36',102),(143,'user_demo','127.0.0.1','0','登录成功','2026-06-16 16:49:11',102),(144,'user_demo','127.0.0.1','0','登录成功','2026-06-25 11:37:20',102),(145,'user_demo','127.0.0.1','0','退出登录','2026-06-25 11:37:30',102),(146,'admin','127.0.0.1','0','登录成功','2026-06-25 11:37:31',101),(147,'admin','127.0.0.1','0','退出登录','2026-06-25 12:53:48',101),(148,'admin','127.0.0.1','0','登录成功','2026-06-25 12:53:49',101),(149,'admin','127.0.0.1','0','登录成功','2026-06-27 09:31:23',101),(150,'admin','127.0.0.1','0','登录成功','2026-06-27 12:08:38',101),(151,'admin','127.0.0.1','1','账号密码不正确','2026-06-27 15:01:32',101),(152,'admin','127.0.0.1','0','退出登录','2026-06-27 15:18:06',101),(153,'user_demo','127.0.0.1','0','登录成功','2026-06-27 15:18:11',102),(154,'user_demo','127.0.0.1','0','退出登录','2026-06-27 15:30:26',102),(155,'admin','127.0.0.1','0','登录成功','2026-06-27 15:30:27',101),(156,'admin','127.0.0.1','0','登录成功','2026-06-28 15:32:29',101),(157,'admin','127.0.0.1','0','退出登录','2026-06-28 15:56:48',101),(158,'user_demo','127.0.0.1','0','登录成功','2026-06-28 15:56:50',102),(159,'user_demo','127.0.0.1','0','退出登录','2026-06-28 16:02:44',102),(160,'user_demo','127.0.0.1','0','登录成功','2026-06-28 16:02:51',102),(161,'user_demo','127.0.0.1','0','退出登录','2026-06-28 16:15:29',102),(162,'admin','127.0.0.1','0','登录成功','2026-06-28 16:15:31',101),(163,'admin','127.0.0.1','0','登录成功','2026-07-02 10:10:21',101),(164,'admin','127.0.0.1','0','登录成功','2026-07-02 14:12:24',101),(165,'user_demo','127.0.0.1','0','登录成功','2026-07-03 10:36:44',102),(166,'user_demo','127.0.0.1','0','退出登录','2026-07-03 10:36:51',102),(167,'admin','127.0.0.1','0','登录成功','2026-07-03 10:36:55',101),(168,'admin','127.0.0.1','0','登录成功','2026-07-11 07:28:55',101),(169,'user_demo','127.0.0.1','0','登录成功','2026-08-04 11:53:23',102),(170,'user_demo','127.0.0.1','0','登录成功','2026-08-04 16:16:08',102),(171,'admin','127.0.0.1','0','登录成功','2026-08-06 14:27:44',101),(172,'admin','127.0.0.1','0','登录成功','2026-08-06 14:38:29',101),(173,'admin','127.0.0.1','0','退出登录','2026-08-06 14:49:21',101),(174,'admin','127.0.0.1','0','登录成功','2026-08-06 14:55:12',101),(175,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:09:15',102),(176,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:09:18',102),(177,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:09:20',102),(178,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:09:27',102),(179,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:09:32',102),(180,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:13:42',102),(181,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:24:53',102),(182,'user_demo','127.0.0.1','0','退出登录','2026-08-07 17:25:29',102),(183,'ops_demo','127.0.0.1','0','登录成功','2026-08-07 17:25:31',105),(184,'ops_demo','127.0.0.1','0','退出登录','2026-08-07 17:29:09',105),(185,'admin','127.0.0.1','0','登录成功','2026-08-07 17:29:10',101),(186,'admin','127.0.0.1','0','退出登录','2026-08-07 17:35:10',101),(187,'ops_demo','127.0.0.1','0','登录成功','2026-08-07 17:35:12',105),(188,'ops_demo','127.0.0.1','0','退出登录','2026-08-07 17:35:22',105),(189,'user_demo','127.0.0.1','0','登录成功','2026-08-07 17:35:23',102),(190,'user_demo','127.0.0.1','0','退出登录','2026-08-07 17:35:32',102),(191,'admin','127.0.0.1','0','登录成功','2026-08-07 17:35:33',101),(192,'admin','127.0.0.1','0','登录成功','2026-08-10 09:42:59',101),(193,'admin','127.0.0.1','0','退出登录','2026-08-10 09:46:16',101),(194,'user_demo','127.0.0.1','0','登录成功','2026-08-10 09:46:18',102),(195,'user_demo','127.0.0.1','0','退出登录','2026-08-10 09:46:35',102),(196,'admin','127.0.0.1','0','登录成功','2026-08-10 09:46:36',101),(197,'admin','127.0.0.1','1','账号密码不正确','2026-08-10 09:47:52',101),(198,'admin','127.0.0.1','0','登录成功','2026-08-10 09:48:02',101),(199,'admin','127.0.0.1','1','账号密码不正确','2026-08-10 09:48:02',101),(200,'admin','127.0.0.1','1','账号密码不正确','2026-08-10 09:48:02',101),(201,'admin','127.0.0.1','0','登录成功','2026-08-10 10:03:46',101),(202,'admin','127.0.0.1','0','登录成功','2026-08-10 10:03:57',101),(203,'admin','127.0.0.1','1','账号密码不正确','2026-08-10 10:27:26',101),(204,'admin','127.0.0.1','0','登录成功','2026-08-10 10:27:32',101),(205,'admin','127.0.0.1','0','退出登录','2026-08-10 14:33:59',101),(206,'admin','127.0.0.1','0','登录成功','2026-08-10 14:34:00',101),(207,'admin','127.0.0.1','1','账号密码不正确','2026-08-10 15:25:48',101),(208,'admin','127.0.0.1','0','登录成功','2026-08-10 15:27:03',101),(209,'admin','127.0.0.1','0','退出登录','2026-08-10 15:35:51',101),(210,'ops_demo','127.0.0.1','0','登录成功','2026-08-10 15:36:37',105),(211,'admin','127.0.0.1','0','登录成功','2026-08-10 15:38:34',101),(212,'admin','127.0.0.1','0','登录成功','2026-08-10 15:44:44',101),(213,'admin','127.0.0.1','0','登录成功','2026-08-12 10:28:31',101),(214,'user_demo','127.0.0.1','0','登录成功','2026-08-28 10:02:28',102),(215,'user_demo','127.0.0.1','0','退出登录','2026-08-28 11:10:41',102),(216,'user_demo','127.0.0.1','0','登录成功','2026-08-28 11:10:45',102),(217,'user_demo','127.0.0.1','0','登录成功','2026-08-28 11:18:36',102),(218,'admin','127.0.0.1','0','登录成功','2026-08-29 07:06:13',101),(219,'admin','127.0.0.1','0','登录成功','2026-09-06 07:33:21',101),(220,'admin','127.0.0.1','1','账号密码不正确','2026-09-06 07:33:21',101),(221,'admin','127.0.0.1','0','登录成功','2026-09-06 07:49:19',101),(222,'admin','127.0.0.1','0','登录成功','2026-09-06 07:49:25',101),(223,'demo','127.0.0.1','0','登录成功','2026-09-06 07:49:25',103),(224,'admin','127.0.0.1','0','登录成功','2026-09-06 07:49:34',101),(225,'demo','127.0.0.1','0','登录成功','2026-09-06 07:49:34',103),(226,'admin','127.0.0.1','0','登录成功','2026-09-06 07:50:24',101),(227,'demo','127.0.0.1','0','登录成功','2026-09-06 07:50:24',103),(228,'admin','127.0.0.1','0','登录成功','2026-09-06 07:52:09',101),(229,'demo','127.0.0.1','0','登录成功','2026-09-06 07:52:09',103),(230,'admin','127.0.0.1','0','登录成功','2026-09-06 07:53:38',101),(231,'admin','127.0.0.1','0','登录成功','2026-09-06 07:59:00',101),(232,'admin','127.0.0.1','0','登录成功','2026-09-06 08:03:05',101),(233,'admin','127.0.0.1','0','登录成功','2026-09-06 08:04:29',101),(234,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 08:04:31',NULL),(235,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 08:04:54',NULL),(236,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 08:06:10',NULL),(237,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 08:06:26',NULL),(238,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 08:06:44',NULL),(239,'admin','127.0.0.1','0','登录成功','2026-09-06 09:42:43',101),(240,'admin','127.0.0.1','1','账号密码不正确','2026-09-06 10:31:24',101),(241,'admin','127.0.0.1','0','登录成功','2026-09-06 10:31:48',101),(242,'admin','127.0.0.1','0','登录成功','2026-09-06 10:35:31',101),(243,'admin','127.0.0.1','0','登录成功','2026-09-06 10:36:22',101),(244,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 10:37:37',NULL),(245,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 10:37:44',NULL),(246,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 10:38:21',NULL),(247,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 10:38:43',NULL),(248,'admin','127.0.0.1','0','登录成功','2026-09-06 12:35:00',101),(249,'admin','127.0.0.1','0','登录成功','2026-09-06 12:38:04',101),(250,'admin','127.0.0.1','0','登录成功','2026-09-06 12:43:49',101),(251,'admin','127.0.0.1','0','登录成功','2026-09-06 12:45:55',101),(252,'admin','127.0.0.1','0','登录成功','2026-09-06 12:46:13',101),(253,'admin','127.0.0.1','0','登录成功','2026-09-06 12:47:36',101),(254,'admin','127.0.0.1','0','登录成功','2026-09-06 12:47:53',101),(255,'admin','127.0.0.1','0','登录成功','2026-09-06 12:48:41',101),(256,'thuadmin','127.0.0.1','0','登录成功','2026-09-06 12:49:10',NULL),(257,'admin','127.0.0.1','0','登录成功','2026-09-06 13:19:38',101),(258,'admin','127.0.0.1','0','登录成功','2026-09-06 17:00:21',101),(259,'admin','127.0.0.1','0','登录成功','2026-09-06 17:09:29',101),(260,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-06 17:26:00',102),(261,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-06 17:27:08',102),(262,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-06 17:27:16',102),(263,'admin','127.0.0.1','0','登录成功','2026-09-06 17:27:20',101),(264,'admin','127.0.0.1','0','退出登录','2026-09-06 18:05:44',101),(265,'admin','127.0.0.1','0','登录成功','2026-09-06 18:05:46',101),(266,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-07 08:42:03',102),(267,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-07 08:44:17',102),(268,'admin','127.0.0.1','0','登录成功','2026-09-07 08:44:35',101),(269,'admin','127.0.0.1','0','登录成功','2026-09-07 08:48:25',101),(270,'admin','127.0.0.1','0','登录成功','2026-09-07 21:06:11',101),(271,'admin','127.0.0.1','0','登录成功','2026-09-07 21:14:50',101),(272,'admin','127.0.0.1','0','登录成功','2026-09-07 21:15:44',101),(273,'admin','127.0.0.1','0','登录成功','2026-09-07 21:17:51',101),(274,'admin','127.0.0.1','0','登录成功','2026-09-07 21:18:52',101),(275,'admin','127.0.0.1','0','登录成功','2026-09-09 23:48:57',101),(276,'admin','127.0.0.1','0','登录成功','2026-09-09 23:52:24',101),(277,'admin','127.0.0.1','0','登录成功','2026-09-09 23:57:09',101),(278,'admin','127.0.0.1','0','登录成功','2026-09-09 23:59:44',101),(279,'admin','127.0.0.1','0','登录成功','2026-09-09 23:59:51',101),(280,'admin','127.0.0.1','0','登录成功','2026-09-10 00:03:47',101),(281,'admin','127.0.0.1','0','登录成功','2026-09-10 00:08:50',101),(282,'admin','127.0.0.1','0','登录成功','2026-09-10 00:10:34',101),(283,'admin','127.0.0.1','0','登录成功','2026-09-10 00:13:37',101),(284,'admin','127.0.0.1','0','退出登录','2026-09-10 00:14:36',101),(285,'admin','127.0.0.1','0','登录成功','2026-09-10 00:14:38',101),(286,'admin','127.0.0.1','0','登录成功','2026-09-10 06:48:54',101),(287,'admin','127.0.0.1','0','登录成功','2026-09-10 22:54:06',101),(288,'admin','127.0.0.1','0','退出登录','2026-09-10 22:54:18',101),(289,'admin','127.0.0.1','0','登录成功','2026-09-10 22:54:20',101),(290,'admin','127.0.0.1','0','登录成功','2026-09-11 23:09:29',101),(291,'admin','127.0.0.1','1','账号密码不正确','2026-09-12 10:06:21',101),(292,'admin','127.0.0.1','0','登录成功','2026-09-12 10:06:22',101),(293,'admin','127.0.0.1','0','登录成功','2026-09-12 10:37:06',101),(294,'admin','127.0.0.1','0','登录成功','2026-09-12 10:37:42',101),(295,'admin','127.0.0.1','0','登录成功','2026-09-12 10:38:28',101),(296,'admin','127.0.0.1','0','登录成功','2026-09-12 10:41:24',101),(297,'admin','127.0.0.1','0','登录成功','2026-09-12 10:44:32',101),(298,'admin','127.0.0.1','0','登录成功','2026-09-12 10:45:02',101),(299,'admin','127.0.0.1','0','登录成功','2026-09-12 10:50:07',101),(300,'admin','127.0.0.1','0','登录成功','2026-09-12 10:52:49',101),(301,'admin','127.0.0.1','0','登录成功','2026-09-12 10:53:08',101),(302,'admin','127.0.0.1','0','登录成功','2026-09-15 20:23:22',101),(303,'admin','127.0.0.1','0','登录成功','2026-09-15 20:25:26',101),(304,'admin','127.0.0.1','0','登录成功','2026-09-15 20:28:54',101),(305,'admin','127.0.0.1','0','登录成功','2026-09-15 20:35:34',101),(306,'admin','127.0.0.1','0','登录成功','2026-09-16 23:07:49',101),(307,'admin','127.0.0.1','0','登录成功','2026-09-16 23:23:00',101),(308,'admin','127.0.0.1','0','登录成功','2026-09-19 06:52:41',101),(309,'admin','127.0.0.1','0','登录成功','2026-09-19 06:55:46',101),(310,'admin','127.0.0.1','0','登录成功','2026-09-19 06:56:20',101),(311,'admin','127.0.0.1','0','登录成功','2026-09-19 06:56:42',101),(312,'admin','127.0.0.1','0','登录成功','2026-09-19 06:59:13',101),(313,'admin','127.0.0.1','0','登录成功','2026-09-19 06:59:46',101),(314,'admin','127.0.0.1','0','登录成功','2026-09-19 07:22:27',101),(315,'admin','127.0.0.1','0','登录成功','2026-09-19 07:32:12',101),(316,'admin','127.0.0.1','0','登录成功','2026-09-19 07:48:32',101),(317,'admin','1.199.37.227','1','账号密码不正确','2026-09-22 20:07:07',101),(318,'admin','1.199.37.227','0','登录成功','2026-09-22 20:10:19',101),(319,'admin','1.199.37.227','0','登录成功','2026-09-22 20:10:40',101),(320,'admin','1.199.37.227','0','登录成功','2026-09-22 20:12:27',101),(321,'admin','1.199.37.227','0','登录成功','2026-09-22 20:13:05',101),(322,'admin','1.199.37.227','0','登录成功','2026-09-22 20:17:30',101),(323,'admin','1.199.37.227','0','登录成功','2026-09-22 20:20:29',101),(324,'admin','1.199.37.227','0','登录成功','2026-09-22 23:12:23',101),(325,'admin','1.199.37.227','0','登录成功','2026-09-22 23:12:23',101),(326,'admin','1.199.37.227','0','登录成功','2026-09-22 23:16:54',101),(327,'admin','1.199.37.227','0','登录成功','2026-09-22 23:18:19',101),(328,'admin','223.88.55.36','0','登录成功','2026-09-22 23:19:13',101),(329,'admin','1.199.37.227','0','登录成功','2026-09-23 11:14:04',101),(330,'admin','127.0.0.1','0','登录成功','2026-09-23 11:14:55',101),(331,'admin','1.199.37.227','0','登录成功','2026-09-23 11:16:48',101),(332,'admin','127.0.0.1','0','登录成功','2026-09-23 11:22:28',101),(333,'admin','1.199.37.227','0','登录成功','2026-09-23 12:56:35',101),(334,'admin','127.0.0.1','0','登录成功','2026-09-23 13:52:00',101),(335,'admin','127.0.0.1','0','登录成功','2026-09-23 13:52:18',101),(336,'admin','127.0.0.1','0','登录成功','2026-09-23 13:52:32',101),(337,'admin','127.0.0.1','0','登录成功','2026-09-23 13:53:07',101),(338,'admin','1.199.37.227','0','登录成功','2026-09-23 13:55:03',101),(339,'admin','127.0.0.1','0','登录成功','2026-09-23 13:56:06',101),(340,'seller_demo','223.88.55.36','0','登录成功','2026-09-23 16:33:31',104),(341,'user_demo','182.118.233.111','1','账号密码不正确','2026-09-23 16:38:29',102),(342,'admin','182.118.233.111','0','登录成功','2026-09-23 16:39:20',101),(343,'admin','223.88.55.36','0','登录成功','2026-09-23 18:16:24',101),(344,'admin','127.0.0.1','0','登录成功','2026-09-25 08:25:50',101),(345,'admin','127.0.0.1','0','登录成功','2026-09-25 08:36:40',101),(346,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-25 18:04:56',102),(347,'user_demo','127.0.0.1','1','账号密码不正确','2026-09-25 18:05:14',102),(348,'admin','127.0.0.1','0','登录成功','2026-09-25 18:05:22',101),(349,'admin','127.0.0.1','0','登录成功','2026-09-25 18:05:52',101),(350,'admin','127.0.0.1','0','登录成功','2026-09-26 19:10:23',101),(351,'admin','127.0.0.1','0','登录成功','2026-09-26 19:10:36',101),(352,'admin','127.0.0.1','0','登录成功','2026-09-26 19:15:00',101),(353,'admin','127.0.0.1','0','登录成功','2026-09-26 19:23:05',101),(354,'admin','127.0.0.1','0','登录成功','2026-09-26 19:27:30',101),(355,'admin','123.52.10.182','0','登录成功','2026-09-30 15:28:14',101),(356,'admin','127.0.0.1','0','登录成功','2026-10-02 22:13:04',101),(357,'admin','127.0.0.1','0','登录成功','2026-10-07 08:57:34',101),(358,'admin','127.0.0.1','0','登录成功','2026-10-07 09:20:20',101),(359,'admin','223.88.55.136','0','登录成功','2026-10-07 18:16:10',101),(360,'admin','127.0.0.1','0','登录成功','2026-10-21 14:43:41',101),(361,'admin','127.0.0.1','0','登录成功','2026-10-21 14:43:44',101),(362,'admin','127.0.0.1','0','登录成功','2026-10-21 14:45:23',101),(363,'admin','127.0.0.1','0','登录成功','2026-10-21 14:45:26',101),(364,'admin','127.0.0.1','0','登录成功','2026-10-21 14:53:54',101),(365,'admin','127.0.0.1','0','登录成功','2026-10-21 14:54:00',101),(366,'admin','127.0.0.1','0','登录成功','2026-10-21 14:59:37',101),(367,'admin','127.0.0.1','0','登录成功','2026-10-27 17:03:31',101),(368,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:31',104),(369,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:32',110),(370,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:32',NULL),(371,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:32',NULL),(372,'demo','127.0.0.1','0','登录成功','2026-10-27 17:03:32',NULL),(373,'alice','127.0.0.1','0','登录成功','2026-10-27 17:03:32',NULL),(374,'thuadmin','127.0.0.1','0','登录成功','2026-10-27 17:03:32',NULL),(375,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:45',NULL),(376,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:46',NULL),(377,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:46',104),(378,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:03:54',NULL),(379,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:14',NULL),(380,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:14',NULL),(381,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:22',NULL),(382,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:28',104),(383,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:28',110),(384,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:04:36',104),(385,'admin','127.0.0.1','0','登录成功','2026-10-27 17:04:44',101),(386,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:05:43',104),(387,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:05:43',110),(388,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:05:55',104),(389,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:05:55',NULL),(390,'alice','127.0.0.1','0','登录成功','2026-10-27 17:05:55',NULL),(391,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:06:16',NULL),(392,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:06:16',NULL),(393,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:06:27',NULL),(394,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:06:27',NULL),(395,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:06:50',NULL),(396,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:07:10',NULL),(397,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:07:32',NULL),(398,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:19:21',NULL),(399,'seller_demo','127.0.0.1','0','登录成功','2026-10-27 17:19:21',NULL),(400,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:19:21',104),(401,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:19:21',110),(402,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:19:29',NULL),(403,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:23:40',104),(404,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:23:40',110),(405,'admin','127.0.0.1','0','登录成功','2026-10-27 17:23:40',101),(406,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:25:41',104),(407,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:25:41',110),(408,'admin','127.0.0.1','0','登录成功','2026-10-27 17:25:41',101),(409,'perm_probe','127.0.0.1','0','登录成功','2026-10-27 17:37:18',NULL),(410,'perm_probe','127.0.0.1','0','登录成功','2026-10-27 17:37:33',NULL),(411,'admin','127.0.0.1','0','登录成功','2026-10-27 17:37:34',101),(412,'ops_demo','127.0.0.1','0','登录成功','2026-10-27 17:37:34',104),(413,'cs_demo','127.0.0.1','0','登录成功','2026-10-27 17:37:34',110),(414,'user_demo','127.0.0.1','0','登录成功','2026-10-27 17:43:42',NULL),(415,'alice','127.0.0.1','0','登录成功','2026-10-27 17:44:05',NULL),(416,'admin','127.0.0.1','0','登录成功','2026-10-29 14:16:31',101),(417,'admin','127.0.0.1','0','登录成功','2026-10-29 14:16:43',101),(418,'user_demo','127.0.0.1','0','登录成功','2026-10-29 14:16:44',NULL),(419,'seller_demo','127.0.0.1','0','登录成功','2026-10-29 14:16:44',NULL),(420,'ops_demo','127.0.0.1','0','登录成功','2026-10-29 14:16:44',104),(421,'cs_demo','127.0.0.1','0','登录成功','2026-10-29 14:16:44',110),(422,'demo','127.0.0.1','0','登录成功','2026-10-29 14:16:44',NULL),(423,'alice','127.0.0.1','0','登录成功','2026-10-29 14:16:44',NULL),(424,'thuadmin','127.0.0.1','0','登录成功','2026-10-29 14:16:44',NULL),(425,'user_demo','127.0.0.1','0','登录成功','2026-10-29 14:20:10',NULL),(426,'user_demo','127.0.0.1','0','退出登录','2026-10-29 14:20:31',NULL),(427,'ops_demo','127.0.0.1','0','登录成功','2026-10-29 14:20:33',104),(428,'admin','127.0.0.1','0','登录成功','2026-10-30 09:12:12',101),(429,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 09:12:28',104),(430,'cs_demo','127.0.0.1','0','登录成功','2026-10-30 09:12:28',110),(431,'admin','127.0.0.1','0','登录成功','2026-10-30 09:12:28',101),(432,'user_demo','127.0.0.1','0','登录成功','2026-10-30 09:20:44',NULL),(433,'user_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:17',NULL),(434,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:17',104),(435,'alice','127.0.0.1','0','登录成功','2026-10-30 09:21:17',NULL),(436,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:21',104),(437,'user_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:27',NULL),(438,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:27',104),(439,'alice','127.0.0.1','0','登录成功','2026-10-30 09:21:27',NULL),(440,'seller_demo','127.0.0.1','0','登录成功','2026-10-30 09:21:27',NULL),(441,'alice','127.0.0.1','0','登录成功','2026-10-30 09:23:55',NULL),(442,'user_demo','127.0.0.1','0','登录成功','2026-10-30 09:24:20',NULL),(443,'seller_demo','127.0.0.1','0','登录成功','2026-10-30 09:24:20',NULL),(444,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 09:24:43',104),(445,'seller_demo','127.0.0.1','1','账号已被冻结','2026-10-30 09:24:51',NULL),(446,'user_demo','127.0.0.1','0','登录成功','2026-10-30 11:35:10',NULL),(447,'ops_demo','127.0.0.1','0','登录成功','2026-10-30 11:35:10',104),(448,'user_demo','127.0.0.1','0','登录成功','2026-10-30 18:28:22',NULL),(449,'user_demo','127.0.0.1','0','登录成功','2026-10-30 18:29:26',NULL),(450,'seller_demo','127.0.0.1','0','登录成功','2026-10-30 18:29:26',NULL),(451,'user_demo','127.0.0.1','0','登录成功','2026-11-02 09:29:16',NULL),(452,'seller_demo','127.0.0.1','0','登录成功','2026-11-02 09:29:16',NULL),(453,'admin','127.0.0.1','1','账号密码不正确','2026-11-03 16:28:29',101),(454,'admin','127.0.0.1','0','登录成功','2026-11-03 16:28:58',101),(455,'seller_demo','127.0.0.1','0','登录成功','2026-11-03 16:36:39',NULL);
/*!40000 ALTER TABLE `sys_login_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `is_frame` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '#' COMMENT '菜单图标',
  `menu_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '菜单范围（0=通用 1=平台专属，非default租户不可见/不可用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2752 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'system.menu.system',0,1,'/system',NULL,'1','M','0','0','','icon-system','1','admin','2019-03-16 11:33:00','admin','2024-03-30 19:50:45','系统管理目录'),(2,'system.menu.human',0,3,'/human',NULL,'1','M','0','0','','icon-human-resources','0','admin','2019-03-16 11:33:00','admin','2024-03-30 20:46:39','系统监控目录'),(3,'system.menu.tool',0,5,'/tool',NULL,'1','M','0','0','','icon-tool-box','1','admin','2019-03-16 11:33:00','admin','2024-03-30 20:46:58','系统工具目录'),(4,'system.menu.log',0,4,'/log','','1','M','0','0','','icon-log','1','admin','2019-03-16 11:33:00','admin','2024-03-30 20:46:46','日志管理菜单'),(100,'system.menu.human.team',2,1,'/human/staff','/human/staff/index','1','C','0','0','system:user:list','icon-team','0','admin','2019-03-16 11:33:00','admin','2024-03-31 09:34:38','用户管理菜单'),(101,'system.menu.human.role',2,2,'/human/role','/human/role/index','1','C','0','0','system:role:list','icon-role','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:34:44','角色管理菜单'),(102,'system.menu.system.menu',1,2,'/system/menu','/system/menu/index','1','C','0','0','system:menu:list','icon-menu','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:06:54','菜单管理菜单'),(103,'system.menu.human.dept',2,4,'/human/dept','/human/dept/index','1','C','0','0','system:dept:list','icon-dept','0','admin','2019-03-16 11:33:00','admin','2024-03-31 09:34:51','部门管理菜单'),(104,'system.menu.human.post',2,5,'/human/post','/human/post/index','1','C','0','0','system:post:list','icon-post','0','admin','2019-03-16 11:33:00','admin','2024-03-31 09:34:59','岗位管理菜单'),(105,'system.menu.system.dictionaries',1,3,'/system/dictionaries','/system/dictionaries/index','1','C','0','0','system:dictionaries:list','icon-dictionaries','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:07:02','字典管理菜单'),(107,'system.menu.system.client',1,4,'/system/client','/system/client/index','1','C','0','0','system:client:list','icon-client','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:07:10','终端设置菜单'),(110,'system.menu.monitor.job',1083,5,'/monitor/job','/monitor/job/index','1','C','0','0','monitor:job:list','icon-job','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:20:27','定时任务菜单'),(500,'system.menu.log.operation',4,1,'/log/operation','/log/operation/index','1','C','0','0','system:operlog:list','icon-log-operation','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:42:33','操作日志菜单'),(501,'system.menu.log.login',4,2,'/log/login','/log/login/index','1','C','0','0','system:logininfor:list','icon-log-login','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:42:42','登录日志菜单'),(1001,'system.menu.human.team.query',100,1,'','','1','F','1','0','system:user:query',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1002,'system.menu.human.team.insert',100,2,'','','1','F','1','0','system:user:insert',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1003,'system.menu.human.team.update',100,3,'','','1','F','1','0','system:user:update',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1004,'system.menu.human.team.remove',100,4,'','','1','F','1','0','system:user:remove',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1005,'system.menu.human.team.export',100,5,'','','1','F','1','0','system:user:export',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1006,'system.menu.human.team.import',100,6,'','','1','F','1','0','system:user:import',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1007,'system.menu.human.team.resetPwd',100,7,'','','1','F','1','0','system:user:resetPwd',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1008,'system.menu.human.role.query',101,1,'','','1','F','0','0','system:role:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1009,'system.menu.human.role.insert',101,2,'','','1','F','0','0','system:role:insert',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1010,'system.menu.human.role.update',101,3,'','','1','F','0','0','system:role:update',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1011,'system.menu.human.role.remove',101,4,'','','1','F','0','0','system:role:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1012,'system.menu.human.role.export',101,5,'','','1','F','0','0','system:role:export',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1013,'system.menu.system.menu.query',102,1,'','','1','F','0','0','system:menu:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1014,'system.menu.system.menu.insert',102,2,'','','1','F','0','0','system:menu:insert',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1015,'system.menu.system.menu.update',102,3,'','','1','F','0','0','system:menu:update',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1016,'system.menu.system.menu.remove',102,4,'','','1','F','0','0','system:menu:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1017,'system.menu.human.dept.query',103,1,'','','1','F','0','0','system:dept:query',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1018,'system.menu.human.dept.insert',103,2,'','','1','F','0','0','system:dept:insert',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1019,'system.menu.human.dept.update',103,3,'','','1','F','0','0','system:dept:update',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1020,'system.menu.human.dept.remove',103,4,'','','1','F','0','0','system:dept:remove',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1021,'system.menu.human.post.query',104,1,'','','1','F','0','0','system:post:query',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1022,'system.menu.human.post.insert',104,2,'','','1','F','0','0','system:post:insert',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1023,'system.menu.human.post.update',104,3,'','','1','F','0','0','system:post:update',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1024,'system.menu.human.post.remove',104,4,'','','1','F','0','0','system:post:remove',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1025,'system.menu.human.post.export',104,5,'','','1','F','0','0','system:post:export',NULL,'0','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1026,'system.menu.system.dictionaries.query',105,1,'#','','1','F','1','0','system:dict:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1027,'system.menu.system.dictionaries.insert',105,2,'#','','1','F','1','0','system:dict:insert',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1028,'system.menu.system.dictionaries.update',105,3,'#','','1','F','1','0','system:dict:update',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1029,'system.menu.system.dictionaries.remove',105,4,'#','','1','F','1','0','system:dict:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1030,'system.menu.system.dictionaries.export',105,5,'#','','1','F','1','0','system:dict:export',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1036,'system.menu.system.client.query',107,1,'#','','1','F','1','0','system:client:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1037,'system.menu.system.client.insert',107,2,'#','','1','F','1','0','system:client:insert',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1038,'system.menu.system.client.update',107,3,'#','','1','F','1','0','system:client:update',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1039,'system.menu.system.client.remove',107,4,'#','','1','F','1','0','system:client:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:21',''),(1040,'system.menu.log.operation.query',500,1,'#','','1','F','0','0','system:operlog:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1041,'system.menu.log.operation.remove',500,2,'#','','1','F','0','0','system:operlog:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1042,'system.menu.log.operation.export',500,4,'#','','1','F','0','0','system:operlog:export',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1043,'system.menu.log.login.query',501,1,'#','','1','F','0','0','system:logininfor:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1044,'system.menu.log.login.remove',501,2,'#','','1','F','0','0','system:logininfor:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1045,'system.menu.log.login.export',501,3,'#','','1','F','0','0','system:logininfor:export',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1051,'system.menu.monitor.job.query',110,1,'#','','1','F','1','0','monitor:job:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1052,'system.menu.monitor.job.insert',110,2,'#','','1','F','1','0','monitor:job:insert',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1053,'system.menu.monitor.job.update',110,3,'#','','1','F','1','0','monitor:job:update',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1054,'system.menu.monitor.job.remove',110,4,'#','','1','F','1','0','monitor:job:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1061,'system.menu.log.job',4,3,'/log/job','/log/job/index','1','C','0','0','system:operlog','icon-log-job','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:42:48',''),(1062,'system.menu.log.job.remove',1061,2,'#','','1','F','0','0','system:operlog:remove',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1063,'system.menu.log.job.export',1061,4,'#','','1','F','0','0','system:operlog:export',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1064,'system.menu.log.job.query',1061,1,'#','','1','F','0','0','system:operlog:query',NULL,'1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1071,'system.menu.system.dfs',1,6,'/system/dfs','/system/dfs/index','1','C','0','0','dfs:dfs:list','icon-DFS','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:07:24',''),(1072,'system.menu.tool.gen.code',1100,2,'/tool/code/gen','/tool/code/gen/index','1','C','0','0','gen:list','icon-gen-code','1','admin','2019-03-16 11:33:00','admin','2024-03-31 10:12:10',''),(1073,'system.menu.tool.gen.code.query',1072,1,'',NULL,'1','F','0','0','gen:query','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1074,'system.menu.tool.gen.code.edit',1072,2,'',NULL,'1','F','0','0','gen:edit','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1075,'system.menu.tool.gen.code.import',1072,3,'',NULL,'1','F','0','0','gen:import','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1076,'system.menu.tool.gen.code.remove',1072,4,'',NULL,'1','F','0','0','gen:remove','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1077,'system.menu.tool.gen.code.preview',1072,5,'',NULL,'1','F','0','0','gen:preview','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1078,'system.menu.tool.gen.code.code',1072,6,'',NULL,'1','F','0','0','gen:code','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1080,'system.menu.system.dfs.remove',1071,1,'',NULL,'1','F','0','0','dfs:dfs:remove','#','1','admin','2019-03-16 11:33:00','','2024-03-31 11:17:22',''),(1083,'system.menu.monitor',0,2,'/monitor',NULL,'1','M','0','0',NULL,'icon-monitor','1','admin','2019-03-16 11:33:00','admin','2024-03-30 20:46:21',''),(1084,'system.menu.monitor.redis',1083,1,'/monitor/redis','/monitor/redis/index','1','C','0','0','monitor:redis:query','icon-redis','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:20:00',''),(1092,'system.menu.system.token',1,7,'/system/token','/system/token/index','1','C','0','0','system:token:list','icon-token','1','admin','2019-03-16 11:33:00','admin','2024-03-31 09:07:31',''),(1093,'system.menu.system.token.remove',1092,1,'',NULL,'1','F','1','0','system:token:remove','#','1','admin','2019-03-16 11:33:00','admin','2024-03-31 11:17:22',''),(1094,'system.menu.tool.gen.ds',1100,1,'/tool/code/ds_conf','gen/code/dsConf/index','1','C','0','0','gen:dsConf:list','icon-gen-ds_conf','1','admin','2018-03-01 00:00:00','admin','2024-03-31 10:11:59','数据源菜单'),(1095,'system.menu.tool.gen.ds.query',1094,1,'#','','1','F','0','0','gen:dsConf:query','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1096,'system.menu.tool.gen.ds.add',1094,2,'#','','1','F','0','0','gen:dsConf:add','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1097,'system.menu.tool.gen.ds.edit',1094,3,'#','','1','F','0','0','gen:dsConf:edit','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1098,'system.menu.tool.gen.ds.remove',1094,4,'#','','1','F','0','0','gen:dsConf:remove','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1100,'system.menu.tool.gen',3,1,'/tool/code',NULL,'1','M','0','0',NULL,'gen','1','admin','2023-11-04 18:04:01','admin','2024-03-31 10:11:50',''),(1101,'system.menu.tool.gen.metadata.type',1114,1,'/tool/code/metadata/type','/Tool/Metadata/Code/Type','1','C','0','0','gen:FieldType:list','icon-gen-type','1','admin','2023-11-04 20:46:29','admin','2024-03-31 10:14:11',''),(1102,'system.menu.tool.gen.metadata.group',1114,3,'/tool/code/metadata/template_group','/tool/code/metadata/templateGroup/index','1','C','0','0','gen:group:list','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 10:14:33','模板分组菜单'),(1103,'system.menu.tool.gen.metadata.group.query',1102,1,'#','','1','F','0','0','gen:group:query','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1104,'system.menu.tool.gen.metadata.group.add',1102,2,'#','','1','F','0','0','gen:group:add','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1105,'system.menu.tool.gen.metadata.group.edit',1102,3,'#','','1','F','0','0','gen:group:edit','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1106,'system.menu.tool.gen.metadata.group.remove',1102,4,'#','','1','F','0','0','gen:group:remove','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1108,'system.menu.tool.gen.metadata.template',1114,2,'/tool/code/metadata/template','tool/code/metadata/template/index','1','C','0','0','gen:template:list','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 10:14:22','模板管理菜单'),(1109,'system.menu.tool.gen.metadata.template.query',1108,1,'#','','1','F','0','0','gen:template:query','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1110,'system.menu.tool.gen.metadata.template.add',1108,2,'#','','1','F','0','0','gen:template:add','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1111,'system.menu.tool.gen.metadata.template.edit',1108,3,'#','','1','F','0','0','gen:template:edit','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1112,'system.menu.tool.gen.metadata.template.remove',1108,4,'#','','1','F','0','0','gen:template:remove','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1114,'system.menu.tool.gen.metadata',1100,3,'/tool/code/metadata',NULL,'1','M','0','0','','icon-gen-metadata','1','admin','2023-11-06 17:29:08','admin','2024-03-31 10:12:17',''),(1115,'system.menu.tool.gen.metadata.type.query',1101,1,'#','','1','F','0','0','gen:FieldType:query','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1116,'system.menu.tool.gen.metadata.type.add',1101,2,'#','','1','F','0','0','gen:FieldType:add','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1117,'system.menu.tool.gen.metadata.type.edit',1101,3,'#','','1','F','0','0','gen:FieldType:edit','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1118,'system.menu.tool.gen.metadata.type.remove',1101,4,'#','','1','F','0','0','gen:FieldType:remove','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1119,'system.menu.system.i18n',1,8,'i18n','system/i18n/index','1','C','0','0','system:i18n:list','icon-i18n','1','admin','2018-03-01 00:00:00','admin','2024-03-31 09:07:53','国际化菜单'),(1120,'system.menu.system.i18n.query',1119,1,'#','','1','F','0','0','system:i18n:query','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1121,'system.menu.system.i18n.add',1119,2,'#','','1','F','0','0','system:i18n:add','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1122,'system.menu.system.i18n.edit',1119,3,'#','','1','F','0','0','system:i18n:edit','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1123,'system.menu.system.i18n.remove',1119,4,'#','','1','F','0','0','system:i18n:remove','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1124,'system.menu.system.i18n.export',1119,5,'#','','1','F','0','0','system:i18n:export','#','1','admin','2018-03-01 00:00:00','admin','2024-03-31 11:17:22',''),(1160,'system.menu.system.i18n.init',1119,6,'',NULL,'1','F','0','0','system:i18n:init','#','1','admin','2025-04-24 22:10:53','',NULL,''),(1161,'AI 中心',0,0,'/ai',NULL,'1','M','0','1',NULL,'icon-ai','0','admin','2026-06-08 15:49:28','admin','2026-09-06 06:39:58',''),(1162,'购物指南',2112,1,'/ai/shopping-guide','./AI/ShoppingGuide','1','C','0','0','product:shoppingGuide:list','icon-shop','0','admin','2026-06-08 15:50:53','admin','2026-09-06 06:39:25',''),(2000,'marketplace.menu.marketplace',0,2,'/marketplace',NULL,'1','M','0','0','','icon-shop','0','admin','2026-06-09 14:03:36','',NULL,'二手交易C端目录'),(2001,'marketplace.menu.home',2000,1,'/home','marketplace/Home','1','C','0','0','marketplace:home:list','icon-home','0','admin','2026-06-09 14:03:37','',NULL,'二手交易首页'),(2002,'marketplace.menu.product',2000,2,'/product',NULL,'1','M','0','0','','icon-shop','0','admin','2026-06-09 14:03:37','',NULL,'商品管理目录'),(2003,'marketplace.menu.product.publish',2002,1,'/product/publish','marketplace/ProductPublish','1','C','0','0','marketplace:product:publish','icon-plus','0','admin','2026-06-09 14:03:37','',NULL,'发布商品'),(2004,'marketplace.menu.product.my',2002,2,'/product/my','marketplace/ProductMyList','1','C','0','0','marketplace:product:my','icon-product','0','admin','2026-06-09 14:03:37','',NULL,'我的商品列表'),(2005,'marketplace.menu.feed',2000,3,'/feed/stream','marketplace/FeedStream','1','C','0','0','marketplace:feed:list','icon-feed','0','admin','2026-06-09 14:03:37','',NULL,'动态广场'),(2006,'marketplace.menu.im',2000,4,'/im/conversations','marketplace/ConversationList','1','C','0','0','marketplace:im:list','icon-message','0','admin','2026-06-09 14:03:37','',NULL,'即时通讯'),(2007,'marketplace.menu.user',2000,5,'/user',NULL,'1','M','0','0','','icon-user','0','admin','2026-06-09 14:03:37','',NULL,'个人中心目录'),(2008,'marketplace.menu.user.profile',2007,1,'/user/profile/me','marketplace/UserProfile','1','C','0','0','marketplace:user:profile','icon-user-info','0','admin','2026-06-09 14:03:37','',NULL,'个人主页'),(2009,'marketplace.menu.user.order',2007,2,'/user/orders','marketplace/OrderList','1','C','0','0','marketplace:trade:list','icon-order','0','admin','2026-06-09 14:03:37','',NULL,'我的订单'),(2010,'marketplace.menu.user.favorites',2007,3,'/user/favorites','marketplace/UserFavorites','1','C','0','0','marketplace:user:favorites','icon-star','0','admin','2026-06-09 14:03:37','',NULL,'我的收藏'),(2011,'marketplace.menu.user.follows',2007,4,'/user/follows','marketplace/UserFollows','1','C','0','0','marketplace:user:follows','icon-follow','0','admin','2026-06-09 14:03:37','',NULL,'我的关注'),(2012,'marketplace.menu.user.address',2007,5,'/user/address','marketplace/UserAddress','1','C','0','0','marketplace:user:address','icon-address','0','admin','2026-06-09 14:03:37','',NULL,'收货地址'),(2013,'marketplace.menu.user.settings',2007,6,'/user/settings','marketplace/UserSettings','1','C','0','0','marketplace:user:settings','icon-settings','0','admin','2026-06-09 14:03:37','',NULL,'账号设置'),(2100,'marketplace.menu.operations',0,7,'/admin/marketplace',NULL,'1','M','0','0','','icon-monitor','1','admin','2026-06-09 14:03:37','',NULL,'运营管理目录'),(2101,'marketplace.menu.operations.dashboard',2100,1,'/admin/marketplace/dashboard','marketplace/admin/Dashboard','1','C','0','0','marketplace:admin:dashboard','icon-dashboard','1','admin','2026-06-09 14:03:37','',NULL,'运营仪表盘'),(2102,'marketplace.menu.operations.productAudit',2100,2,'/admin/marketplace/product-audit','marketplace/admin/ProductAudit','1','C','0','0','marketplace:admin:productAudit','icon-shop','1','admin','2026-06-09 14:03:37','',NULL,'商品审核'),(2103,'marketplace.menu.operations.report',2100,3,'/admin/marketplace/report','marketplace/admin/Report','1','C','0','0','marketplace:admin:report','icon-alert','1','admin','2026-06-09 14:03:37','',NULL,'举报处理'),(2104,'marketplace.menu.operations.user',2100,4,'/admin/marketplace/user','marketplace/admin/User','1','C','0','0','marketplace:admin:user','icon-team','1','admin','2026-06-09 14:03:37','',NULL,'运营用户管理'),(2105,'marketplace.menu.operations.finance',2100,5,'/admin/marketplace/finance','marketplace/admin/Finance','1','C','0','0','marketplace:admin:finance','icon-money','1','admin','2026-06-09 14:03:37','',NULL,'资金对账'),(2106,'marketplace.menu.operations.feedAudit',2100,6,'/admin/marketplace/feed-audit','marketplace/admin/FeedAudit','1','C','0','0','marketplace:admin:feedAudit','icon-alert','1','admin','2026-06-12 14:52:03','',NULL,'动态审核(运营)'),(2107,'marketplace.menu.operations.review',2100,7,'/admin/marketplace/review','marketplace/admin/ReviewManage','1','C','0','0','marketplace:admin:review','icon-star','0','admin','2026-09-06 16:55:51','',NULL,'璇勪环绠＄悊'),(2110,'marketplace.menu.service',0,8,'/cs/marketplace',NULL,'1','M','0','0','','icon-team','1','admin','2026-06-09 14:03:37','',NULL,'客服工作台目录'),(2111,'marketplace.menu.service.disputes',2110,1,'/cs/marketplace/disputes','marketplace/cs/DisputeList','1','C','0','0','marketplace:cs:disputes','icon-order','1','admin','2026-06-09 14:03:37','',NULL,'纠纷列表'),(2112,'AI 应用',0,10,'/ai',NULL,'1','M','0','0','','icon-ai','0','admin','2026-06-25 12:50:21','admin','2026-09-06 06:25:10','AI知识库目录'),(2113,'模型管理',2112,1,'/ai/model','/ai/model/index','1','C','0','0','ai:model:list','icon-ai-model','1','admin','2026-06-25 12:50:21','',NULL,'模型管理菜单'),(2114,'知识库管理',2112,2,'/ai/knowledge','/ai/knowledge/index','1','C','0','0','ai:knowledge:list','icon-ai-knowledge','0','admin','2026-06-25 12:50:21','',NULL,'知识库管理菜单'),(2115,'文档管理',2112,3,'/ai/doc','/ai/doc/index','1','C','0','0','ai:doc:list','icon-ai-doc','0','admin','2026-06-25 12:50:21','',NULL,'文档管理菜单'),(2116,'AI 聊天',2112,4,'/ai/chat','/ai/chat/index','1','C','0','1','ai:chat:list','icon-ai-chat','0','admin','2026-06-25 12:50:21','admin','2026-09-06 06:37:31','AI聊天菜单'),(2117,'图片生成',2112,5,'/ai/image','/ai/image/index','1','C','0','0','ai:image:list','icon-ai-image','0','admin','2026-06-25 12:50:21','',NULL,'图片生成菜单'),(2118,'视频生成',2112,6,'/ai/video','/ai/video/index','1','C','0','0','ai:video:list','icon-ai-video','0','admin','2026-06-25 12:50:21','',NULL,'视频生成菜单'),(2119,'ai.menu.model.query',2113,1,'',NULL,'1','F','0','0','ai:model:query','#','1','',NULL,'',NULL,''),(2120,'ai.menu.model.add',2113,2,'',NULL,'1','F','0','0','ai:model:add','#','1','',NULL,'',NULL,''),(2121,'ai.menu.model.update',2113,3,'',NULL,'1','F','0','0','ai:model:update','#','1','',NULL,'',NULL,''),(2122,'ai.menu.model.remove',2113,4,'',NULL,'1','F','0','0','ai:model:remove','#','1','',NULL,'',NULL,''),(2123,'ai.menu.image.query',2117,1,'',NULL,'1','F','0','0','ai:image:query','#','0','',NULL,'',NULL,''),(2124,'ai.menu.image.generate',2117,2,'',NULL,'1','F','0','0','ai:image:generate','#','0','',NULL,'',NULL,''),(2125,'ai.menu.image.remove',2117,3,'',NULL,'1','F','0','0','ai:image:remove','#','0','',NULL,'',NULL,''),(2126,'ai.menu.video.query',2118,1,'',NULL,'1','F','0','0','ai:video:query','#','0','',NULL,'',NULL,''),(2127,'ai.menu.video.generate',2118,2,'',NULL,'1','F','0','0','ai:video:generate','#','0','',NULL,'',NULL,''),(2128,'ai.menu.video.remove',2118,3,'',NULL,'1','F','0','0','ai:video:remove','#','0','',NULL,'',NULL,''),(2200,'租户管理',1,7,'/system/tenant','system/tenant/index','1','C','0','0','system:tenant:list','icon-team','1','admin','2026-08-28 18:00:36','admin','2026-08-28 18:00:36','tenant management menu'),(2201,'租户查询',2200,1,'','','1','F','1','0','system:tenant:query','#','1','admin','2026-08-28 18:00:36','admin','2026-08-28 18:00:36',''),(2202,'租户续费',2200,2,'','','1','F','1','0','system:tenant:edit','#','1','admin','2026-08-28 18:00:36','admin','2026-08-28 18:00:36',''),(2300,'社区互助',0,3,'/runner',NULL,'1','M','0','0','','icon-run','0','admin','2026-09-05 00:12:45','',NULL,'互助服务C端目录'),(2301,'互助大厅',2300,1,'/runner/hall','Runner/Hall','1','C','0','0','runner:hall:list','icon-takeaway','0','admin','2026-09-05 00:12:45','',NULL,'互助大厅'),(2302,'我接的任务',2300,2,'/runner/hall/my-taken','Runner/MyTaken','1','C','0','0','runner:hall:my-taken','icon-task','0','admin','2026-09-05 00:12:45','',NULL,'我接的任务'),(2303,'我发布的',2300,3,'/runner/task/my-list','Runner/MyList','1','C','0','0','runner:task:my-list','icon-order','0','admin','2026-09-05 00:12:45','',NULL,'我发布的任务'),(2304,'发布任务',2300,4,'/runner/task/publish','Runner/Publish','1','C','1','0','runner:task:publish','icon-plus','0','admin','2026-09-05 00:12:45','',NULL,'发布任务（菜单隐藏，页面入口）'),(2305,'任务详情',2300,5,'/runner/task/detail','Runner/Detail','1','C','1','0','runner:task:detail','#','0','admin','2026-09-05 00:12:45','',NULL,'任务详情（菜单隐藏）'),(2310,'互助管理',0,4,'/admin/runner',NULL,'1','M','0','0','','icon-monitor','0','admin','2026-09-05 00:12:45','',NULL,'互助管理后台'),(2311,'任务管理',2310,1,'/admin/runner/task','Runner/admin/TaskPage','1','C','0','0','runner:task:list','icon-list','0','admin','2026-09-05 00:12:45','',NULL,'互助任务管理'),(2312,'数据看板',2310,2,'/admin/runner/stats','Runner/admin/Stats','1','C','0','0','runner:stat:view','icon-data-analysis','0','admin','2026-09-05 00:12:45','',NULL,'互助数据看板'),(2320,'接单',2301,1,'','','1','F','1','0','runner:hall:assign','#','0','admin','2026-09-05 00:12:45','',NULL,'接单按钮'),(2321,'开始服务',2302,1,'','','1','F','1','0','runner:hall:start','#','0','admin','2026-09-05 00:12:45','',NULL,'开始服务按钮'),(2322,'提交完成',2302,2,'','','1','F','1','0','runner:hall:finish','#','0','admin','2026-09-05 00:12:45','',NULL,'提交完成按钮'),(2323,'拒绝',2302,3,'','','1','F','1','0','runner:hall:reject','#','0','admin','2026-09-05 00:12:45','',NULL,'拒单按钮'),(2324,'取消任务',2303,1,'','','1','F','1','0','runner:task:cancel','#','0','admin','2026-09-05 00:12:45','',NULL,'取消任务按钮'),(2325,'确认完成',2303,2,'','','1','F','1','0','runner:task:confirm','#','0','admin','2026-09-05 00:12:45','',NULL,'确认完成按钮'),(2326,'申请退款',2303,3,'','','1','F','1','0','runner:task:refund','#','0','admin','2026-09-05 00:12:45','',NULL,'申请退款按钮'),(2401,'社区日报',2112,7,'/ai/report','/ai/report/index','1','C','0','0','ai:report:list','icon-ai','0','admin','2026-09-06 10:14:04','',NULL,'AI 社区日报（全员可看，管理员可手动生成）'),(2402,'日报立即生成',2401,1,'',NULL,'1','F','0','0','ai:report:generate','#','0','admin','2026-09-06 10:14:04','',NULL,'管理员手动触发当日日报生成/覆盖'),(2403,'社区管家',2112,8,'/ai/butler','/ai/butler/index','1','C','0','0','ai:butler:list','icon-ai','0','admin','2026-09-06 12:29:05','',NULL,'AI 社区管家（互助悬赏/社区近况/好物问答）'),(2500,'AI 音乐',2112,7,'/ai/music',NULL,'1','C','0','0','ai:music:list','icon-ai-music','0','admin','2026-09-10 00:13:03','',NULL,'AI music menu'),(2501,'我的作品',2500,1,'/ai/music/works','/ai/music/works','1','C','0','0','ai:music:song:list','#','0','admin','2026-09-10 00:13:03','',NULL,'works menu'),(2502,'我的音色',2500,2,'/ai/music/voices','/ai/music/voices','1','C','0','0','ai:music:voice:list','#','0','admin','2026-09-10 00:13:03','',NULL,'voices menu'),(2510,'AI 写词',2501,1,NULL,NULL,'1','F','0','0','ai:music:lyrics:draft','#','0','admin','2026-09-10 00:13:03','',NULL,'draft lyrics'),(2511,'文生歌',2501,2,NULL,NULL,'1','F','0','0','ai:music:song:submit','#','0','admin','2026-09-10 00:13:03','',NULL,'submit song'),(2512,'查询作品',2501,3,NULL,NULL,'1','F','0','0','ai:music:song:query','#','0','admin','2026-09-10 00:13:03','',NULL,'query song'),(2513,'删除作品',2501,4,NULL,NULL,'1','F','0','0','ai:music:song:remove','#','0','admin','2026-09-10 00:13:03','',NULL,'delete song'),(2520,'训练音色',2502,1,NULL,NULL,'1','F','0','0','ai:music:voice:train','#','0','admin','2026-09-10 00:13:03','',NULL,'train voice'),(2521,'查询音色',2502,2,NULL,NULL,'1','F','0','0','ai:music:voice:query','#','0','admin','2026-09-10 00:13:03','',NULL,'query voice'),(2522,'删除音色',2502,3,NULL,NULL,'1','F','0','0','ai:music:voice:remove','#','0','admin','2026-09-10 00:13:03','',NULL,'delete voice'),(2600,'AI 创作工作台',2112,8,'/ai/creation',NULL,'1','M','0','0','','icon-ai-video','0','admin','2026-10-02 22:14:56','',NULL,'AI creation workbench'),(2601,'创作项目',2600,1,'/ai/creation/index','ai/creation/index','1','C','0','0','ai:creation:list','#','0','admin','2026-10-02 22:14:56','',NULL,'creation projects'),(2602,'爆款模板库',2600,2,'/ai/creation/template','ai/creation/template','1','C','0','0','ai:creation:template','#','0','admin','2026-10-02 22:14:56','',NULL,'viral templates'),(2603,'人设管理',2600,3,'/ai/creation/persona','ai/creation/persona','1','C','0','0','ai:creation:persona','#','0','admin','2026-10-02 22:14:56','',NULL,'personas'),(2610,'新增作品',2601,1,NULL,NULL,'1','F','0','0','ai:creation:add','#','0','admin','2026-10-02 22:14:56','',NULL,'create project / decompose'),(2611,'查询作品',2601,2,NULL,NULL,'1','F','0','0','ai:creation:query','#','0','admin','2026-10-02 22:14:56','',NULL,'query project detail'),(2612,'编辑渲染',2601,3,NULL,NULL,'1','F','0','0','ai:creation:edit','#','0','admin','2026-10-02 22:14:56','',NULL,'edit storyboard / render / retry'),(2613,'删除作品',2601,4,NULL,NULL,'1','F','0','0','ai:creation:remove','#','0','admin','2026-10-02 22:14:56','',NULL,'remove project'),(2700,'交换广场',0,6,'/exchange',NULL,'1','M','0','0','','icon-order','0','admin','2026-10-27 16:53:01','',NULL,'以物换物广场目录'),(2701,'换物广场',2700,1,'/exchange/square','Exchange/Square','1','C','0','0','exchange:square:list','icon-takeaway','0','admin','2026-10-27 16:53:01','',NULL,'换物广场列表页'),(2702,'我的物品',2700,2,'/exchange/my-items','Exchange/MyItems','1','C','0','0','exchange:item:my-list','icon-task','0','admin','2026-10-27 16:53:01','',NULL,'我的换物物品'),(2703,'我的意向',2700,3,'/exchange/offers','Exchange/Offers','1','C','0','0','exchange:offer:list','icon-list','0','admin','2026-10-27 16:53:01','',NULL,'收到/发出的换物意向'),(2704,'我的交换',2700,4,'/exchange/my-orders','Exchange/MyOrders','1','C','0','0','exchange:order:my-list','icon-order','0','admin','2026-10-27 16:53:01','',NULL,'我的交换订单'),(2705,'发布物品',2700,5,'/exchange/publish','Exchange/Publish','1','C','1','0','exchange:item:publish','icon-plus','0','admin','2026-10-27 16:53:01','',NULL,'发布换物物品（隐藏菜单）'),(2706,'物品详情',2700,6,'/exchange/detail','Exchange/Detail','1','C','1','0','exchange:item:detail','#','0','admin','2026-10-27 16:53:01','',NULL,'换物物品详情（隐藏菜单）'),(2707,'订单详情',2700,7,'/exchange/order-detail','Exchange/OrderDetail','1','C','1','0','exchange:order:detail','#','0','admin','2026-10-27 16:53:01','',NULL,'交换订单详情（隐藏菜单）'),(2710,'交换管理',0,7,'/admin/exchange',NULL,'1','M','0','0','','icon-monitor','0','admin','2026-10-27 16:53:01','',NULL,'交换广场管理目录'),(2711,'物品审核',2710,1,'/admin/exchange/item-audit','Exchange/admin/ItemAudit','1','C','0','0','exchange:admin:item:list','icon-list','0','admin','2026-10-27 16:53:01','',NULL,'换物物品审核（AI初审medium兜底）'),(2712,'订单管理',2710,2,'/admin/exchange/order','Exchange/admin/OrderPage','1','C','0','0','exchange:admin:order:list','icon-order','0','admin','2026-10-27 16:53:01','',NULL,'交换订单管理/纠纷裁决'),(2713,'数据看板',2710,3,'/admin/exchange/stats','Exchange/admin/Stats','1','C','0','0','exchange:admin:stat:view','icon-data-analysis','0','admin','2026-10-27 16:53:01','',NULL,'交换广场数据看板'),(2720,'发起意向',2701,1,'',NULL,'1','F','1','0','exchange:offer:send','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2721,'接受意向',2703,1,'',NULL,'1','F','1','0','exchange:offer:accept','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2722,'婉拒意向',2703,2,'',NULL,'1','F','1','0','exchange:offer:reject','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2723,'下架物品',2702,1,'',NULL,'1','F','1','0','exchange:item:offshelf','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2724,'标记交付',2704,1,'',NULL,'1','F','1','0','exchange:order:deliver','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2725,'确认收货',2704,2,'',NULL,'1','F','1','0','exchange:order:confirm','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2726,'取消订单',2704,3,'',NULL,'1','F','1','0','exchange:order:cancel','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2727,'发起纠纷',2704,4,'',NULL,'1','F','1','0','exchange:order:dispute','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2728,'提交评价',2704,5,'',NULL,'1','F','1','0','exchange:order:review','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2729,'审核通过/驳回',2711,1,'',NULL,'1','F','1','0','exchange:admin:item:audit','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2730,'订单裁决',2712,1,'',NULL,'1','F','1','0','exchange:admin:order:force','#','0','admin','2026-10-27 16:53:01','',NULL,''),(2733,'任务导出',110,5,'',NULL,'1','F','1','0','monitor:job:export','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐代码要求的导出按钮权限'),(2734,'推荐效果',2100,6,'/admin/marketplace/recommend-stats','marketplace/admin/RecommendStats','1','C','0','0','marketplace:admin:recommend','icon-monitor','0','admin','2026-10-27 17:35:28','',NULL,'推荐曝光/点击/CTR 看板'),(2735,'文档新增',2115,1,'',NULL,'1','F','1','0','ai:doc:add','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:doc:add'),(2736,'文档查看',2115,2,'',NULL,'1','F','1','0','ai:doc:query','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:doc:query'),(2737,'文档修改',2115,3,'',NULL,'1','F','1','0','ai:doc:update','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:doc:update'),(2738,'文档删除',2115,4,'',NULL,'1','F','1','0','ai:doc:remove','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:doc:remove'),(2739,'知识库新增',2114,1,'',NULL,'1','F','1','0','ai:knowledge:add','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:knowledge:add'),(2740,'知识库查看',2114,2,'',NULL,'1','F','1','0','ai:knowledge:query','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:knowledge:query'),(2741,'知识库修改',2114,3,'',NULL,'1','F','1','0','ai:knowledge:update','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:knowledge:update'),(2742,'知识库删除',2114,4,'',NULL,'1','F','1','0','ai:knowledge:remove','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:knowledge:remove'),(2743,'AI 改词',2501,4,'',NULL,'1','F','1','0','ai:music:lyrics:revise','#','0','admin','2026-10-27 17:35:28','',NULL,'补齐 ai:music:lyrics:revise'),(2744,'退款管理',2100,8,'/admin/marketplace/refund','marketplace/admin/Refund','1','C','0','0','marketplace:admin:refund','icon-money','1','admin','2026-10-30 16:07:17','',NULL,'退款审核 / 客服裁决 / 手工收口'),(2745,'我的退款',2007,7,'/user/refunds','marketplace/RefundList','1','C','0','0','marketplace:refund:my','icon-money','0','admin','2026-10-30 16:07:17','',NULL,'买家退款申请列表'),(2746,'绀惧尯婕忔枟',2100,7,'/admin/marketplace/funnel','marketplace/admin/Funnel','1','C','0','0','marketplace:admin:funnel','icon-chart','0','admin','2026-11-02 14:08:06','',NULL,'鏍稿績婕忔枟浜嬩欢涓庤浆鍖栫湅鏉匡紙鎸夌ぞ鍖哄垏鐗囷級'),(2747,'实名审核',2100,9,'/admin/marketplace/kyc','marketplace/admin/Kyc','1','C','0','0','marketplace:admin:kyc','icon-user','1','admin','2026-11-02 16:03:37','',NULL,'卖家实名资料审核（证件/卡号脱敏展示）'),(2748,'结算单',2100,10,'/admin/marketplace/settlement','marketplace/admin/Settlement','1','C','0','0','marketplace:admin:settlement','icon-order','1','admin','2026-11-02 16:03:37','',NULL,'结算单查询（holding/pending_settle/settled/closed/clawback）'),(2749,'提现审核',2100,11,'/admin/marketplace/withdraw','marketplace/admin/Withdraw','1','C','0','0','marketplace:admin:withdraw','icon-money','1','admin','2026-11-02 16:03:37','',NULL,'卖家提现审核与线下打款凭证回填'),(2750,'结算对账',2100,12,'/admin/marketplace/recon','marketplace/admin/Recon','1','C','0','0','marketplace:admin:recon','icon-chart','1','admin','2026-11-02 16:03:37','',NULL,'每日对账差异入队、处理与解冻卖家'),(2751,'卖家中心',2007,8,'/user/settlement','marketplace/MySettlement','1','C','0','0','marketplace:seller:settlement','icon-money','0','admin','2026-11-02 16:03:37','',NULL,'卖家实名、余额、提现记录');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oauth_client_details`
--

DROP TABLE IF EXISTS `sys_oauth_client_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oauth_client_details` (
  `client_id` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端唯一标识',
  `resource_ids` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源ID标识',
  `client_secret` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端安全码',
  `scope` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端授权范围',
  `authorized_grant_types` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端授权类型',
  `web_server_redirect_uri` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '服务器回调地址',
  `authorities` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '访问资源所需权限',
  `access_token_validity` int DEFAULT NULL COMMENT '设定客户端的access_token的有效时间值（秒）',
  `refresh_token_validity` int DEFAULT NULL COMMENT '设定客户端的refresh_token的有效时间值（秒）',
  `additional_information` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '预留字段',
  `autoapprove` tinyint DEFAULT NULL COMMENT '是否登录时跳过授权（默认false）',
  PRIMARY KEY (`client_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='客户端配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oauth_client_details`
--

LOCK TABLES `sys_oauth_client_details` WRITE;
/*!40000 ALTER TABLE `sys_oauth_client_details` DISABLE KEYS */;
INSERT INTO `sys_oauth_client_details` VALUES ('weiwei',NULL,'123456','server','password,sms,refresh_token,authorization_code',NULL,NULL,3600,7200,NULL,NULL);
/*!40000 ALTER TABLE `sys_oauth_client_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `service` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` tinyint(1) DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '返回参数',
  `status` tinyint(1) DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`oper_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_operation_log`
--

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
INSERT INTO `sys_operation_log` VALUES (1,'用户头像',2,'com.twelvet.admin.controller.system.SysProfileController.avatar()','POST',1,'admin',NULL,'/system/user/profile/avatar','127.0.0.1','','FILE','{\"code\":500,\"msg\":\"发生未知错误\"}',1,NULL,'2026-06-05 17:07:36',101),(2,'个人信息',2,'com.twelvet.admin.controller.system.SysProfileController.updateProfile()','PUT',1,'admin',NULL,'/system/user/profile','127.0.0.1','','{\"params\":{},\"userId\":1,\"username\":\"admin\",\"nickName\":\"TwelveT\",\"email\":\"2332343543@qq.com\",\"phonenumber\":\"13593546788\",\"sex\":\"0\",\"admin\":true}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-05 17:08:06',101),(3,'个人信息',2,'com.twelvet.admin.controller.system.SysProfileController.updateProfile()','PUT',1,'admin',NULL,'/system/user/profile','127.0.0.1','','{\"params\":{},\"userId\":1,\"username\":\"admin\",\"nickName\":\"TwelveT\",\"email\":\"2332343543@qq.com\",\"phonenumber\":\"13593546788\",\"sex\":\"0\",\"admin\":true}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-05 17:08:11',101),(4,'个人信息',2,'com.twelvet.admin.controller.system.SysProfileController.updateProfile()','PUT',1,'admin',NULL,'/system/user/profile','127.0.0.1','','{\"params\":{},\"userId\":1,\"username\":\"admin\",\"nickName\":\"TwelveT\",\"email\":\"133454534565@163.com\",\"phonenumber\":\"13534566788\",\"sex\":\"0\",\"admin\":true}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-05 17:10:03',101),(5,'个人信息',2,'com.twelvet.admin.controller.system.SysProfileController.updateProfile()','PUT',1,'admin',NULL,'/system/user/profile','127.0.0.1','','{\"params\":{},\"userId\":1,\"username\":\"admin\",\"nickName\":\"TwelveT\",\"email\":\"133454534565@163.com\",\"phonenumber\":\"13534566788\",\"sex\":\"0\",\"admin\":true}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-05 17:10:13',101),(6,'用户头像',2,'com.twelvet.admin.controller.system.SysProfileController.avatar()','POST',1,'admin',NULL,'/system/user/profile/avatar','127.0.0.1','','FILE','{\"code\":500,\"msg\":\"发生未知错误\"}',1,NULL,'2026-06-05 17:10:49',101),(7,'菜单管理',1,'com.twelvet.system.server.controller.SysMenuController.insert()','POST',1,'admin',NULL,'/system/menu','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"menuId\":0,\"menuName\":\"AI\",\"parentId\":0,\"orderNum\":0,\"path\":\"/ai\",\"isFrame\":\"1\",\"menuType\":\"M\",\"visible\":\"0\",\"status\":\"0\",\"icon\":\"🌭\",\"routes\":[]}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-08 15:49:28',101),(8,'菜单管理',1,'com.twelvet.system.server.controller.SysMenuController.insert()','POST',1,'admin',NULL,'/system/menu','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"menuId\":0,\"menuName\":\"AI导购\",\"parentId\":1161,\"orderNum\":1,\"path\":\"/ai/shopping-guide\",\"component\":\"./AI/ShoppingGuide\",\"isFrame\":\"1\",\"menuType\":\"C\",\"visible\":\"0\",\"status\":\"0\",\"perms\":\"product:shoppingGuide:list\",\"icon\":\"🛬\",\"routes\":[]}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-08 15:50:53',101),(9,'角色管理',1,'com.twelvet.system.server.controller.SysRoleController.insert()','POST',1,'admin',NULL,'/system/role','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"roleId\":0,\"roleName\":\"运营\",\"roleKey\":\"operationa\",\"roleSort\":0,\"dataScope\":\"1\",\"status\":\"0\",\"flag\":false,\"menuIds\":[],\"deptIds\":[],\"admin\":false}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'role_sort\' doesn\'t have a default value\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-system\\twelvet-system-server\\target\\classes\\mapper\\system\\SysRoleMapper.xml]\r\n### The error may involve com.twelvet.system.server.mapper.SysRoleMapper.insertRole-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into sys_role(                    role_name,           role_key,                     data_scope,           status,                     create_by,          create_time         )values(                    ?,           ?,                     ?,           ?,                     ?,          sysdate()         )\r\n### Cause: java.sql.SQLException: Field \'role_sort\' doesn\'t have a default value\n; Field \'role_sort\' doesn\'t have a default value','2026-06-09 13:38:55',101),(10,'角色管理',1,'com.twelvet.system.server.controller.SysRoleController.insert()','POST',1,'admin',NULL,'/system/role','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"roleId\":0,\"roleName\":\"运营\",\"roleKey\":\"operationa\",\"roleSort\":0,\"dataScope\":\"1\",\"status\":\"0\",\"flag\":false,\"menuIds\":[],\"deptIds\":[],\"admin\":false}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'role_sort\' doesn\'t have a default value\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-system\\twelvet-system-server\\target\\classes\\mapper\\system\\SysRoleMapper.xml]\r\n### The error may involve com.twelvet.system.server.mapper.SysRoleMapper.insertRole-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into sys_role(                    role_name,           role_key,                     data_scope,           status,                     create_by,          create_time         )values(                    ?,           ?,                     ?,           ?,                     ?,          sysdate()         )\r\n### Cause: java.sql.SQLException: Field \'role_sort\' doesn\'t have a default value\n; Field \'role_sort\' doesn\'t have a default value','2026-06-09 13:39:03',101),(11,'角色管理',1,'com.twelvet.system.server.controller.SysRoleController.insert()','POST',1,'admin',NULL,'/system/role','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"roleId\":6,\"roleName\":\"运营\",\"roleKey\":\"operationa\",\"roleSort\":6,\"dataScope\":\"1\",\"status\":\"0\",\"flag\":false,\"menuIds\":[],\"deptIds\":[],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-09 13:39:39',101),(12,'角色管理',1,'com.twelvet.system.server.controller.SysRoleController.insert()','POST',1,'admin',NULL,'/system/role','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"roleId\":7,\"roleName\":\"客服\",\"roleKey\":\"customer_service\",\"roleSort\":7,\"dataScope\":\"1\",\"status\":\"0\",\"flag\":false,\"menuIds\":[],\"deptIds\":[],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-09 13:40:16',101),(13,'角色管理',2,'com.twelvet.system.server.controller.SysRoleController.update()','PUT',1,'admin',NULL,'/system/role','127.0.0.1','','{\"updateBy\":\"admin\",\"remark\":\"普通角色\",\"params\":{},\"roleId\":2,\"roleName\":\"普通角色\",\"roleKey\":\"common\",\"roleSort\":2,\"dataScope\":\"4\",\"status\":\"0\",\"flag\":false,\"menuIds\":[1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1017,1018,1019,1020,1021,1022,1023,1024,1025,1040,1041,1042,1043,1044,1045,1064,1062,1063,100,101,103,104,500,501,1061,2,4,1161,1162],\"deptIds\":[103],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-09 13:40:24',101),(14,'角色管理',2,'com.twelvet.system.server.controller.SysRoleController.update()','PUT',1,'admin',NULL,'/system/role','127.0.0.1','','{\"updateBy\":\"admin\",\"remark\":\"普通角色\",\"params\":{},\"roleId\":2,\"roleName\":\"普通角色\",\"roleKey\":\"common\",\"roleSort\":2,\"dataScope\":\"4\",\"status\":\"0\",\"flag\":false,\"menuIds\":[],\"deptIds\":[103],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-11 14:25:52',101),(15,'角色管理',2,'com.twelvet.system.server.controller.SysRoleController.update()','PUT',1,'admin',NULL,'/system/role','127.0.0.1','','{\"updateBy\":\"admin\",\"remark\":\"普通角色\",\"params\":{},\"roleId\":2,\"roleName\":\"普通角色\",\"roleKey\":\"common\",\"roleSort\":2,\"dataScope\":\"4\",\"status\":\"0\",\"flag\":false,\"menuIds\":[1161,1162,2000,2001,2002,2005,2006,2007,2003,2004,2008,2009,2010,2011,2012,2013],\"deptIds\":[103],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-11 14:26:31',101),(16,'角色管理',2,'com.twelvet.system.server.controller.SysRoleController.update()','PUT',1,'admin',NULL,'/system/role','127.0.0.1','','{\"updateBy\":\"admin\",\"remark\":\"运营管理角色(二手交易)\",\"params\":{},\"roleId\":3,\"roleName\":\"运营人员\",\"roleKey\":\"ops\",\"roleSort\":1,\"dataScope\":\"1\",\"status\":\"0\",\"flag\":false,\"menuIds\":[2100,2101,2102,2103,2104,2105],\"deptIds\":[],\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-11 14:28:51',101),(17,'菜单管理',3,'com.twelvet.system.server.controller.SysMenuController.remove()','DELETE',1,'admin',NULL,'/system/menu/1082','127.0.0.1','','{menuId=1082}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-11 16:18:51',101),(18,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'user_demo',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-06-12 17:32:02',102),(19,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女跳舞\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\r\n### The error may exist in URL [jar:file:/F:/apache-maven-3.9.9/mvn_repo/com/twelvet/twelvet-ai-server/3.5.4/twelvet-ai-server-3.5.4.jar!/mapper/AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,             size,             gen_type,                          response_format,                          status,                          create_by,             create_time )           values ( ?,             ?,             ?,                          ?,                          ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\n; bad SQL grammar []','2026-06-25 12:54:10',101),(20,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女跳舞\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"base64\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\r\n### The error may exist in URL [jar:file:/F:/apache-maven-3.9.9/mvn_repo/com/twelvet/twelvet-ai-server/3.5.4/twelvet-ai-server-3.5.4.jar!/mapper/AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,             size,             gen_type,                          response_format,                          status,                          create_by,             create_time )           values ( ?,             ?,             ?,                          ?,                          ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\n; bad SQL grammar []','2026-06-25 12:54:14',101),(21,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女跳舞\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,             size,             gen_type,                          response_format,                          status,                          create_by,             create_time )           values ( ?,             ?,             ?,                          ?,                          ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\n; bad SQL grammar []','2026-06-27 09:34:37',101),(22,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女跳舞\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,             size,             gen_type,                          response_format,                          status,                          create_by,             create_time )           values ( ?,             ?,             ?,                          ?,                          ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'size\' in \'field list\'\n; bad SQL grammar []','2026-06-27 09:34:59',101),(23,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女图片\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,                          size,             gen_type,                          response_format,                                                                              status,                          create_by,             create_time )           values ( ?,                          ?,             ?,                          ?,                                                                              ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\n; Field \'model_id\' doesn\'t have a default value','2026-06-27 09:47:09',101),(24,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女图片\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,                          size,             gen_type,                          response_format,                                                                              status,                          create_by,             create_time )           values ( ?,                          ?,             ?,                          ?,                                                                              ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\n; Field \'model_id\' doesn\'t have a default value','2026-06-27 09:47:13',101),(25,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"prompt\":\"美女图片\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"base64\",\"status\":\"pending\"}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiImageTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiImageTaskMapper.insertAiImageTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_image_task          ( prompt,                          size,             gen_type,                          response_format,                                                                              status,                          create_by,             create_time )           values ( ?,                          ?,             ?,                          ?,                                                                              ?,                          ?,             sysdate() )\r\n### Cause: java.sql.SQLException: Field \'model_id\' doesn\'t have a default value\n; Field \'model_id\' doesn\'t have a default value','2026-06-27 09:47:22',101),(26,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":1,\"modelId\":1,\"prompt\":\"美女图片\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/e3933023150e463fb89567c07de69d47.png\",\"status\":\"completed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":1,\"modelId\":1,\"prompt\":\"美女图片\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/e3933023150e463fb89567c07de69d47.png\",\"status\":\"completed\"}}',1,NULL,'2026-06-27 10:24:27',101),(27,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-06-27 10:58:25',101),(28,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-06-27 10:59:30',101),(29,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-06-27/1782530904870.jpg\"}',1,NULL,'2026-06-27 11:28:28',101),(30,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":2,\"modelId\":1,\"prompt\":\"穿上粉色裙子\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-06-27/1782530904870.jpg\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/i2i/fcd6e7af9cd141b8a523598d6a4e924e/output.png\",\"status\":\"completed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":2,\"modelId\":1,\"prompt\":\"穿上粉色裙子\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-06-27/1782530904870.jpg\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/i2i/fcd6e7af9cd141b8a523598d6a4e924e/output.png\",\"status\":\"completed\"}}',1,NULL,'2026-06-27 11:29:07',101),(31,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'gen_type\' in \'field list\'\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiVideoTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiVideoTaskMapper.insertAiVideoTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_video_task          ( model_id,                                       prompt,             gen_type,                          width,             height,             num_frames,             frame_rate,                                       status,             progress,                                                                 create_by,             create_time )           values ( ?,                                       ?,             ?,                          ?,             ?,             ?,             ?,                                       ?,             ?,                                                                 ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'gen_type\' in \'field list\'\n; bad SQL grammar []','2026-06-27 11:30:08',101),(32,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}',NULL,0,'\r\n### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'gen_type\' in \'field list\'\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiVideoTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiVideoTaskMapper.insertAiVideoTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into ai_video_task          ( model_id,                                       prompt,             gen_type,                          width,             height,             num_frames,             frame_rate,                                       status,             progress,                                                                 create_by,             create_time )           values ( ?,                                       ?,             ?,                          ?,             ?,             ?,             ?,                                       ?,             ?,                                                                 ?,             sysdate() )\r\n### Cause: java.sql.SQLSyntaxErrorException: Unknown column \'gen_type\' in \'field list\'\n; bad SQL grammar []','2026-06-27 11:30:29',101),(33,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":1,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: {\\\"code\\\":\\\"fail_to_fetch_task\\\",\\\"message\\\":\\\"{\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\\\\\\\\\"error\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"message\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Service busy (tasks: 1)\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"503\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"type\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"server_error\\\\\\\\\\\\\\\"}}\\\\\\\",\\\\\\\"type\\\\\\\":null,\\\\\\\"param\\\\\\\":null,\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\"}}\\\",\\\"data\\\":null}\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":1,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: {\\\"code\\\":\\\"fail_to_fetch_task\\\",\\\"message\\\":\\\"{\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\\\\\\\\\"error\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"message\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Service busy (tasks: 1)\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"503\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"type\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"server_error\\\\\\\\\\\\\\\"}}\\\\\\\",\\\\\\\"type\\\\\\\":null,\\\\\\\"param\\\\\\\":null,\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\"}}\\\",\\\"data\\\":null}\"}}',1,NULL,'2026-06-27 11:41:07',101),(34,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":2,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: {\\\"code\\\":\\\"fail_to_fetch_task\\\",\\\"message\\\":\\\"{\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\\\\\\\\\"error\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"message\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Service busy (tasks: 1)\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"503\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"type\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"server_error\\\\\\\\\\\\\\\"}}\\\\\\\",\\\\\\\"type\\\\\\\":null,\\\\\\\"param\\\\\\\":null,\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\"}}\\\",\\\"data\\\":null}\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":2,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: {\\\"code\\\":\\\"fail_to_fetch_task\\\",\\\"message\\\":\\\"{\\\\\\\"error\\\\\\\":{\\\\\\\"message\\\\\\\":\\\\\\\"litellm.ServiceUnavailableError: ServiceUnavailableError: OpenAIException - {\\\\\\\\\\\\\\\"error\\\\\\\\\\\\\\\":{\\\\\\\\\\\\\\\"message\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"Service busy (tasks: 1)\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"503\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"type\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"server_error\\\\\\\\\\\\\\\"}}\\\\\\\",\\\\\\\"type\\\\\\\":null,\\\\\\\"param\\\\\\\":null,\\\\\\\"code\\\\\\\":\\\\\\\"503\\\\\\\"}}\\\",\\\"data\\\":null}\"}}',1,NULL,'2026-06-27 11:46:59',101),(35,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":3,\"modelId\":2,\"agnesTaskId\":\"task_RyxE6gObm2pgrP5QokSpb464BcfspfCg\",\"agnesVideoId\":\"video_bGl0ZWxsbTpjdXN0b21fbGxtX3Byb3ZpZGVyOm9wZW5haTttb2RlbF9pZDphZ25lcy12aWRlby12Mi4wO3ZpZGVvX2lkOnZpZGVvXzBhODlhNzlhYjBmMmY2NWE4MGE1MGViMDM1MTJlMTk3NDE1YTA3NzYzYjNmOWVmYg==\",\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0,\"videoSeconds\":\"5.0\",\"videoSize\":\"1088x832\"}',NULL,0,'\r\n### Error updating database.  Cause: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation: Data truncation: Data too long for column \'agnes_video_id\' at row 1\r\n### The error may exist in file [F:\\code\\weiwei\\twelvet-fast\\twelvet-ai\\twelvet-ai-server\\target\\classes\\mapper\\AiVideoTaskMapper.xml]\r\n### The error may involve com.twelvet.ai.server.mapper.AiVideoTaskMapper.updateAiVideoTask-Inline\r\n### The error occurred while setting parameters\r\n### SQL: update ai_video_task          SET model_id = ?,             agnes_task_id = ?,             agnes_video_id = ?,             prompt = ?,             gen_type = ?,                          width = ?,             height = ?,             num_frames = ?,             frame_rate = ?,                                       status = ?,             progress = ?,                          video_seconds = ?,             video_size = ?,                          update_time = sysdate()          where task_id = ?\r\n### Cause: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation: Data truncation: Data too long for column \'agnes_video_id\' at row 1\n; Data truncation: Data too long for column \'agnes_video_id\' at row 1','2026-06-27 11:47:57',101),(36,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":4,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: error code: 502\\n\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":4,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0,\"errorMsg\":\"Agnes Video API error: error code: 502\\n\"}}',1,NULL,'2026-06-27 11:53:01',101),(37,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":5,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"failed\",\"progress\":0}','{\"code\":500,\"msg\":\"视频生成失败\"}',1,NULL,'2026-06-27 13:01:39',101),(38,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":6,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":6,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 14:30:43',101),(39,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":7,\"modelId\":2,\"prompt\":\"A cat walking on the beach at sunset\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":7,\"modelId\":2,\"prompt\":\"A cat walking on the beach at sunset\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 15:02:18',101),(40,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":8,\"modelId\":2,\"prompt\":\"A dog running in the park\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":8,\"modelId\":2,\"prompt\":\"A dog running in the park\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 15:06:24',101),(41,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":9,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":9,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 15:31:04',101),(42,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":10,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":10,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 16:15:28',101),(43,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":11,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":11,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 17:10:55',101),(44,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":12,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":12,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 17:11:50',101),(45,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":13,\"modelId\":2,\"prompt\":\"我要一个美女跳舞视频5s即可\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":13,\"modelId\":2,\"prompt\":\"我要一个美女跳舞视频5s即可\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 18:35:13',101),(46,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":14,\"modelId\":2,\"prompt\":\"我要一个美女跳舞视频\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":14,\"modelId\":2,\"prompt\":\"我要一个美女跳舞视频\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 19:01:29',101),(47,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":15,\"modelId\":2,\"prompt\":\"A butterfly flying among flowers, cinematic\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":15,\"modelId\":2,\"prompt\":\"A butterfly flying among flowers, cinematic\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 20:28:42',101),(48,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":16,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":16,\"modelId\":2,\"prompt\":\"一个金发碧眼美女穿着长裙在沙滩漫步\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-27 20:42:35',101),(49,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":3,\"modelId\":1,\"prompt\":\"一部专业相机的写实肖像摄影，一位28岁的亚洲美女，近距离特写，自然的皮肤纹理与细小的毛孔可见，淡淡的雀斑，清透真实的皮肤质感，柔和朦胧的窗边自然光，眼神有光，写实的眼球反光，极其细致的睫毛与发丝，平淡的写实色调，富士胶片模拟风格，背景虚化，景深效果，8k高分辨率，逼真得令人惊叹，杰作。\\n\\nNegative Prompt（负向词，必加）： 卡通，油画，3D渲染，光滑磨皮，塑料质感，过度美颜，假睫毛，浓妆艳抹，模糊，低画质。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/e9dd11e6d9a04716983ad91e6171bb57.png\",\"status\":\"completed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":3,\"modelId\":1,\"prompt\":\"一部专业相机的写实肖像摄影，一位28岁的亚洲美女，近距离特写，自然的皮肤纹理与细小的毛孔可见，淡淡的雀斑，清透真实的皮肤质感，柔和朦胧的窗边自然光，眼神有光，写实的眼球反光，极其细致的睫毛与发丝，平淡的写实色调，富士胶片模拟风格，背景虚化，景深效果，8k高分辨率，逼真得令人惊叹，杰作。\\n\\nNegative Prompt（负向词，必加）： 卡通，油画，3D渲染，光滑磨皮，塑料质感，过度美颜，假睫毛，浓妆艳抹，模糊，低画质。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/e9dd11e6d9a04716983ad91e6171bb57.png\",\"status\":\"completed\"}}',1,NULL,'2026-06-28 12:37:30',101),(50,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":17,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿。\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":17,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿。\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 12:43:02',101),(51,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":18,\"modelId\":2,\"prompt\":\"A cat walking on the beach\",\"genType\":\"text2vid\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":18,\"modelId\":2,\"prompt\":\"A cat walking on the beach\",\"genType\":\"text2vid\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 14:13:51',101),(52,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":19,\"modelId\":2,\"prompt\":\"Test video generation debug\",\"genType\":\"text2vid\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":19,\"modelId\":2,\"prompt\":\"Test video generation debug\",\"genType\":\"text2vid\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 14:26:59',101),(53,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":20,\"modelId\":2,\"prompt\":\"A red cat walking on the beach at sunset\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":20,\"modelId\":2,\"prompt\":\"A red cat walking on the beach at sunset\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":81,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 14:32:14',101),(54,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":15,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":15,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 14:54:13',101),(55,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":16,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":16,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 15:18:51',101),(56,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":17,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":17,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调，4k超高清，24fps电影帧率。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"negativePrompt\":\"变形扭曲的身体，多余的手指，面部模糊，人物闪烁，衣物穿模，僵硬漂浮的头发，过度曝光，卡通渲染，慢动作卡顿\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 15:20:46',101),(57,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":18,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":18,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 15:35:41',101),(58,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":19,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":19,\"modelId\":2,\"prompt\":\"电影级实拍视频，一位金发碧眼的白人美女，身穿一条轻薄的白色吊带长裙，赤脚漫步在黄昏时分的金色沙滩上。动态细节：温柔的海风吹拂着她的长发，发丝在空气中凌乱飘动；裙摆随风优雅地向后扬起，布料呈现真实的褶皱与垂坠感。环境交互：她的双脚踩在湿润的沙子上，每走一步都留下清晰的脚印，退去的海浪轻轻冲刷过她的脚背，产生细碎的白色泡沫。镜头语言：稳定器侧跟平移镜头，保持人物处于画面黄金分割点，中景景别。光影质感：逆光拍摄，金色的夕阳在她轮廓上镶上一层暖边，皮肤呈现真实通透的质感，手持微呼吸感，胶片色调\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 15:49:38',101),(59,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":20,\"modelId\":2,\"prompt\":\"3D卡通渲染（三渲二）风格，一位银白色长发的萝莉，穿着黑色哥特洛丽塔裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为海飞丝般的光滑高光，无锯齿。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":20,\"modelId\":2,\"prompt\":\"3D卡通渲染（三渲二）风格，一位银白色长发的萝莉，穿着黑色哥特洛丽塔裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为海飞丝般的光滑高光，无锯齿。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:20:25',101),(60,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":21,\"modelId\":2,\"prompt\":\"3D cel-shading animation style (MMD style), long silver-white hair Lolita girl, wearing black gothic dress, waltzing elegantly on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion.\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":21,\"modelId\":2,\"prompt\":\"3D cel-shading animation style (MMD style), long silver-white hair Lolita girl, wearing black gothic dress, waltzing elegantly on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion.\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:23:16',101),(61,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":22,\"modelId\":2,\"prompt\":\"3D动画风格，一位银白色长发的可爱少女，穿着华丽的黑色多层蕾丝裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为顺滑的亮丽高光，无锯齿。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":22,\"modelId\":2,\"prompt\":\"3D动画风格，一位银白色长发的可爱少女，穿着华丽的黑色多层蕾丝裙，在梦幻的星空舞台中央跳着优雅的华尔兹。动作细节：旋转时裙摆呈圆规状散开，蕾丝边有细微的物理飘动；双手做轻盈的芭蕾手势，双脚垫脚尖旋转。环境与光效：舞台地面有镜面反射，背景有流动的极光特效，角色身体边缘带有自发光（背光效果）。摄像机：围绕角色360度缓慢旋转运镜，全身景。质感：角色皮肤有陶瓷般的柔光质感，头发为顺滑的亮丽高光，无锯齿。\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:26:52',101),(62,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":23,\"modelId\":2,\"prompt\":\"3D anime style, a girl with long silver hair in a black dress, dancing waltz on a starry stage, 360 camera orbit.\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":23,\"modelId\":2,\"prompt\":\"3D anime style, a girl with long silver hair in a black dress, dancing waltz on a starry stage, 360 camera orbit.\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:29:47',101),(63,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":24,\"modelId\":2,\"prompt\":\"3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":24,\"modelId\":2,\"prompt\":\"3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:34:53',101),(64,'AI视频生成',3,'com.twelvet.ai.server.controller.AiVideoController.remove()','DELETE',1,'admin',NULL,'/ai/video/22','127.0.0.1','','{taskIds=22}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-06-28 16:35:00',101),(65,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":25,\"modelId\":2,\"prompt\":\"3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":25,\"modelId\":2,\"prompt\":\"3D animation style, a cute girl with long silver-white hair, wearing an elegant black multi-layered lace dress, waltzing gracefully on a dreamy starry stage. Skirt spinning out horizontally, lace physics enabled, tiptoe ballet turns. Stage floor with mirror reflection, flowing aurora background lights, rim lighting on character. Camera orbiting 360 degrees around the subject, full body shot. Smooth hair highlights, ceramic skin texture, high definition, 60fps smooth motion\",\"genType\":\"text2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-06-28 16:44:38',101),(66,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-02/1782972931972.png\"}',1,NULL,'2026-07-02 14:15:32',101),(67,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":26,\"modelId\":2,\"prompt\":\"让图片中的任务大喊咕咕嘎嘎\",\"genType\":\"img2vid\",\"width\":1152,\"height\":768,\"numFrames\":241,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":26,\"modelId\":2,\"prompt\":\"让图片中的任务大喊咕咕嘎嘎\",\"genType\":\"img2vid\",\"width\":1152,\"height\":768,\"numFrames\":241,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-07-02 14:16:22',101),(68,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-02/1782973288668.png\"}',1,NULL,'2026-07-02 14:21:28',101),(69,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":27,\"modelId\":2,\"prompt\":\"让图片中的人物大喊咕咕嘎嘎，人物一定要保持一致\",\"genType\":\"img2vid\",\"width\":1152,\"height\":768,\"numFrames\":241,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":27,\"modelId\":2,\"prompt\":\"让图片中的人物大喊咕咕嘎嘎，人物一定要保持一致\",\"genType\":\"img2vid\",\"width\":1152,\"height\":768,\"numFrames\":241,\"frameRate\":24,\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-07-02 14:22:42',101),(70,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-03/1783046533161.png\"}',1,NULL,'2026-07-03 10:42:13',101),(71,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":4,\"modelId\":1,\"prompt\":\"将图片中人物变得更加性感\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783046533161.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":4,\"modelId\":1,\"prompt\":\"将图片中人物变得更加性感\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783046533161.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}}',1,NULL,'2026-07-03 10:48:45',101),(72,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":5,\"modelId\":1,\"prompt\":\"将图片中人物变得更加性感\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/4c624a3a2f304c5f8f6fbaa2385df6d3.png\",\"status\":\"completed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":5,\"modelId\":1,\"prompt\":\"将图片中人物变得更加性感\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\",\"resultUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/4c624a3a2f304c5f8f6fbaa2385df6d3.png\",\"status\":\"completed\"}}',1,NULL,'2026-07-03 10:49:11',101),(73,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-03/1783047198555.png\"}',1,NULL,'2026-07-03 10:53:18',101),(74,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":6,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047198555.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":6,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047198555.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}}',1,NULL,'2026-07-03 11:00:16',101),(75,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-03/1783047731567.png\"}',1,NULL,'2026-07-03 11:02:11',101),(76,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":7,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047731567.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":7,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783047731567.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}}',1,NULL,'2026-07-03 11:08:13',101),(77,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-03/1783048321946.png\"}',1,NULL,'2026-07-03 11:12:02',101),(78,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":8,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":8,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}}',1,NULL,'2026-07-03 11:18:08',101),(79,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":9,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":9,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783048321946.png\",\"responseFormat\":\"url\",\"status\":\"failed\"}}',1,NULL,'2026-07-03 11:42:11',101),(80,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-03/1783051033926.png\"}',1,NULL,'2026-07-03 11:57:14',101),(81,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"taskId\":10,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783051033926.png\",\"responseFormat\":\"url\",\"status\":\"queued\",\"progress\":0}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"createBy\":\"admin\",\"params\":{},\"taskId\":10,\"modelId\":1,\"prompt\":\"将图片的角色变得更可爱穿上粉色小裙子，另外生成的图片要是对比图\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-03/1783051033926.png\",\"responseFormat\":\"url\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-07-03 11:57:27',101),(82,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-07-11/1783726304917.jpg\"}',1,NULL,'2026-07-11 07:31:46',101),(83,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图片中的任务更加精美一点\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-11/1783726304917.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":12,\"model_id\":1,\"prompt\":\"让图片中的任务更加精美一点\",\"status\":\"queued\"}}}',1,NULL,'2026-07-11 07:32:04',101),(84,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图片中的任务更加精美一点\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-07-11/1783726304917.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":13,\"model_id\":1,\"prompt\":\"让图片中的任务更加精美一点\",\"status\":\"queued\"}}}',1,NULL,'2026-07-11 07:36:21',101),(85,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'user_demo',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-04/1785834069361.jpg\"}',1,NULL,'2026-08-04 17:01:10',102),(86,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\"}',NULL,0,'500 Internal Server Error on POST request for \"http://localhost:8001/ai/image/generate\": \"Internal Server Error\"','2026-08-06 14:56:08',101),(87,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\"}',NULL,0,'500 Internal Server Error on POST request for \"http://localhost:8001/ai/image/generate\": \"Internal Server Error\"','2026-08-06 14:59:27',101),(88,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":22,\"model_id\":1,\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:06:17',101),(89,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786000110147.jpg\"}',1,NULL,'2026-08-06 15:08:31',101),(90,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"size\":\"1024x1024\",\"genType\":\"text2img\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":24,\"model_id\":1,\"prompt\":\"一只体型健硕的中华田园大黄狗，柔顺光滑的金黄色背毛，在阳光下泛着绸缎般的光泽，竖立的耳朵，机警而温柔的眼神，吐着舌头微笑。蹲坐在乡间土路上，背景是虚化的绿色稻田和远处的青山，夕阳西下，温暖的金色逆光勾勒出狗狗的轮廓，电影级布光，超高清画质，8k，写实摄影，佳能85mm镜头拍摄，浅景深，细节极其丰富，自然生动。\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:21:55',101),(91,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786000998513.jpg\"}',1,NULL,'2026-08-06 15:23:18',101),(92,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786000998513.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":25,\"model_id\":1,\"prompt\":\"让图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:24:09',101),(93,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786001771373.jpg\"}',1,NULL,'2026-08-06 15:36:12',101),(94,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786001771373.jpg\",\"responseFormat\":\"url\"}',NULL,0,'I/O error on POST request for \"http://localhost:8001/ai/image/generate\": Connection refused: no further information: localhost/127.0.0.1:8001','2026-08-06 15:36:13',101),(95,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786001771373.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":26,\"model_id\":1,\"prompt\":\"让图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:40:38',101),(96,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786002131363.jpg\"}',1,NULL,'2026-08-06 15:42:11',101),(97,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786002131363.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":27,\"model_id\":1,\"prompt\":\"让图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:42:13',101),(98,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"test\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://example.com/test.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":28,\"model_id\":1,\"prompt\":\"test\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:53:10',101),(99,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"verify fix\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://example.com/fix-test.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":29,\"model_id\":1,\"prompt\":\"verify fix\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:55:51',101),(100,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"verify fix v2\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://example.com/fix-test-v2.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":30,\"model_id\":1,\"prompt\":\"verify fix v2\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 15:59:35',101),(101,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786003270533.jpg\"}',1,NULL,'2026-08-06 16:01:11',101),(102,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://example.com/test.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":31,\"model_id\":1,\"prompt\":\"让图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 16:07:38',101),(103,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786003770910.jpg\"}',1,NULL,'2026-08-06 16:09:31',101),(104,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":32,\"model_id\":1,\"prompt\":\"将图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 16:09:56',101),(105,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":33,\"model_id\":1,\"prompt\":\"将图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 16:09:57',101),(106,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图中人物带上猫耳发卡\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786003770910.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":34,\"model_id\":1,\"prompt\":\"将图中人物带上猫耳发卡\",\"status\":\"queued\"}}}',1,NULL,'2026-08-06 16:10:14',101),(107,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786003900781.jpg\"}',1,NULL,'2026-08-06 16:11:41',101),(108,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中角色跳性感的舞蹈\",\"genType\":\"img2vid\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":30,\"model_id\":2,\"prompt\":\"让图中角色跳性感的舞蹈\",\"status\":\"queued\",\"progress\":0}}}',1,NULL,'2026-08-06 16:17:43',101),(109,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786004600710.jpg\"}',1,NULL,'2026-08-06 16:23:21',101),(110,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中角色跳性感的舞蹈\",\"genType\":\"img2vid\",\"inputImages\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786004600710.jpg\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":31,\"model_id\":2,\"prompt\":\"让图中角色跳性感的舞蹈\",\"status\":\"queued\",\"progress\":0}}}',1,NULL,'2026-08-06 16:23:22',101),(111,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786006928353.jpg\"}',1,NULL,'2026-08-06 17:02:08',101),(112,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中角色跳性感的舞蹈\",\"genType\":\"img2vid\",\"inputImages\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786006928353.jpg\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":33,\"model_id\":2,\"prompt\":\"让图中角色跳性感的舞蹈\",\"status\":\"queued\",\"progress\":0}}}',1,NULL,'2026-08-06 17:02:10',101),(113,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-06/1786009562911.jpg\"}',1,NULL,'2026-08-06 17:46:03',101),(114,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中角色跳性感的舞蹈\",\"genType\":\"img2vid\",\"inputImages\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-06/1786009562911.jpg\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":34,\"model_id\":2,\"prompt\":\"让图中角色跳性感的舞蹈\",\"status\":\"queued\",\"progress\":0}}}',1,NULL,'2026-08-06 17:46:07',101),(115,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-08-07 17:58:53',101),(116,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-08-07 17:59:10',101),(117,'菜单管理',2,'com.twelvet.system.server.controller.SysMenuController.update()','PUT',1,'admin',NULL,'/system/menu','127.0.0.1','','{\"updateBy\":\"admin\",\"params\":{},\"menuId\":1161,\"menuName\":\"ai.menu.ai\",\"parentId\":0,\"orderNum\":0,\"path\":\"/ai\",\"isFrame\":\"1\",\"menuType\":\"M\",\"visible\":\"0\",\"status\":\"0\",\"icon\":\"🚲\",\"routes\":[]}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-08-10 10:10:57',101),(118,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-08-10 10:30:15',101),(119,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-10/1786329450782.jpg\"}',1,NULL,'2026-08-10 10:37:31',101),(120,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图片人物变成朋克赛博风格\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":35,\"model_id\":1,\"prompt\":\"将图片人物变成朋克赛博风格\",\"status\":\"queued\"}}}',1,NULL,'2026-08-10 10:38:05',101),(121,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图片人物变成朋克赛博风格，并且要正脸\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":36,\"model_id\":1,\"prompt\":\"将图片人物变成朋克赛博风格，并且要正脸\",\"status\":\"queued\"}}}',1,NULL,'2026-08-10 10:41:29',101),(122,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"将图片人物变成朋克赛博风格，并且要正脸\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786329450782.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":37,\"model_id\":1,\"prompt\":\"将图片人物变成朋克赛博风格，并且要正脸\",\"status\":\"queued\"}}}',1,NULL,'2026-08-10 10:56:23',101),(123,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-10/1786330715467.png\"}',1,NULL,'2026-08-10 10:58:35',101),(124,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"美化一下，变成大帅哥，但是要保留原本的人物的特征\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-10/1786330715467.png\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"code\":200,\"msg\":\"success\",\"data\":{\"task_id\":38,\"model_id\":1,\"prompt\":\"美化一下，变成大帅哥，但是要保留原本的人物的特征\",\"status\":\"queued\"}}}',1,NULL,'2026-08-10 11:00:33',101),(125,'AI知识库管理',1,'com.twelvet.ai.server.controller.AiKnowledgeController.add()','POST',1,'admin',NULL,'/ai/knowledge','127.0.0.1','','{\"knowledgeId\":0,\"knowledgeName\":\"日常知识\",\"welcomeMsg\":\"帮助用户解答日常生活\",\"multiRound\":5,\"topK\":3,\"knowledgeSort\":1,\"score\":0.5,\"sliceSize\":3000}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"knowledgeId\":7,\"knowledgeName\":\"日常知识\",\"welcomeMsg\":\"帮助用户解答日常生活\",\"multiRound\":5,\"topK\":3,\"score\":0.5,\"sliceSize\":3000,\"knowledgeSort\":1,\"delFlag\":0,\"createTime\":\"2026-08-10 15:55:46\"}}',1,NULL,'2026-08-10 15:55:46',101),(126,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-11/1786418405031.jpg\"}',1,NULL,'2026-08-11 11:20:05',101),(127,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成中国水墨侠客风格\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg\",\"responseFormat\":\"url\"}',NULL,0,'I/O error on POST request for \"http://localhost:8001/ai/image/generate\": Connection refused: no further information: localhost/127.0.0.1:8001','2026-08-11 11:20:41',101),(128,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成中国水墨侠客风格\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":39,\"model_id\":1,\"prompt\":\"正脸改成中国水墨侠客风格\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:22:48',101),(129,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成中国水墨侠客风格\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786418405031.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":40,\"model_id\":1,\"prompt\":\"正脸改成中国水墨侠客风格\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:22:53',101),(130,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成中国水墨侠客风格\",\"size\":\"1024x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":41,\"model_id\":1,\"prompt\":\"正脸改成中国水墨侠客风格\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:24:06',101),(131,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成赛博朋克风格\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":42,\"model_id\":1,\"prompt\":\"正脸改成赛博朋克风格\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:26:18',101),(132,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成赛博朋克风格\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":43,\"model_id\":1,\"prompt\":\"正脸改成赛博朋克风格\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:26:55',101),(133,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"正脸改成赛博朋克风格\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://platform-outputs.agnes-ai.space/images/i2i/task_fij8sBZOpOA0D5yazYJcLm7AhJ2MPu2x/output.png\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":44,\"model_id\":1,\"prompt\":\"正面肖像，赛博朋克风格，未来都市夜景背景，霓虹灯光影交织，蓝色与洋红色调为主，人物面部带有机械义体植入细节，半透明发光元件，高科技科技感氛围，精致面部特征，强烈的明暗对比，电影级构图，8K超高清，细节丰富，未来主义美学\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:45:45',101),(134,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-11/1786420015755.jpg\"}',1,NULL,'2026-08-11 11:46:56',101),(135,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物粉色小裙子\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":45,\"model_id\":1,\"prompt\":\"一位优雅的女性角色，身穿精致的粉色小裙子，裙摆轻盈飘逸，采用柔和的粉色调，带有细腻的蕾丝或薄纱材质细节。裙子设计简约而甜美，可能带有蝴蝶结、荷叶边等装饰元素。整体画面呈现梦幻浪漫的氛围，光线柔和温暖，色彩以粉色为主调，搭配清新的背景环境，人物姿态优雅自然，展现出青春活力的气质。\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:47:30',101),(136,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物粉色小裙子显得甜美俏皮可爱\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420015755.jpg\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":46,\"model_id\":1,\"prompt\":\"一位年轻女性身穿甜美俏皮可爱的粉色小裙子，裙子采用轻盈的雪纺或棉质面料，带有精致的荷叶边装饰和蕾丝花边细节。裙摆蓬松灵动，呈现柔和的樱花粉色调，搭配小巧的蝴蝶结点缀。整体造型充满少女感与活泼气息，姿态轻盈俏皮，面带甜美微笑，眼神明亮灵动。画面采用柔和的自然光或梦幻柔光效果，色调温暖清新，背景可选用花丛、糖果色房间或童话般的小径，营造出浪漫可爱的氛围。构图聚焦人物全身或半身，突出裙子的飘逸质感与甜美气质。\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:49:20',101),(137,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-08-11/1786420527155.webp\"}',1,NULL,'2026-08-11 11:55:27',101),(138,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"让图中人物粉色小裙子显得甜美俏皮可爱\",\"size\":\"768x1024\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-08-11/1786420527155.webp\",\"responseFormat\":\"url\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":47,\"model_id\":1,\"prompt\":\"一位年轻可爱的女孩，身穿甜美俏皮的粉色小裙子，裙子采用轻盈的薄纱材质，带有精致的荷叶边装饰和蝴蝶结点缀，裙摆蓬松柔软，呈现出梦幻的少女感。整体造型清新活泼，配色以柔和的粉色为主调，搭配白色蕾丝细节。人物表情甜美灵动，眼神清澈明亮，散发青春活力的气息。背景为柔和的浅色调，光线温暖柔和，营造出浪漫温馨的氛围，构图注重展现裙子的飘逸质感和人物的可爱气质。\",\"status\":\"queued\"}}',1,NULL,'2026-08-11 11:55:43',101),(139,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"扣除图中人物，让人物比耶，注意不要让角色崩坏\",\"genType\":\"img2vid\",\"inputImages\":\"https://upload-bbs.miyoushe.com/upload/2026/06/30/291110358/3eea55124730ccd463bf99ba4a40645d_7897922056117889759.png?x-oss-process=image//resize,s_600/quality,q_80/auto-orient,0/interlace,1/format,png\",\"width\":1152,\"height\":768,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":35,\"model_id\":2,\"prompt\":\"一个年轻时尚的人物站在简约背景前，右手举起比出胜利的V字手势，表情自信开心，身体姿态自然放松，穿着现代休闲服装，光影柔和均匀，构图居中突出人物主体，皮肤质感细腻真实，手指细节清晰准确，不发胖变形，服装褶皱自然，整体氛围阳光积极充满活力，高细节高质量，摄影级别画质\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-08-13 10:11:48',101),(140,'用户管理',1,'com.twelvet.system.server.controller.SysUserController.insert()','POST',1,'admin',NULL,'/system/user','127.0.0.1','','{\"createBy\":\"admin\",\"params\":{},\"userId\":126,\"username\":\"carol\",\"nickName\":\"Carol回滚验证\",\"phonenumber\":\"13800001999\",\"password\":\"$2a$10$Hd.Ke3xkGGnwNB.BshKgDuy6cx81DPnJ45JYk.9sZqm8V8U5R6LIi\",\"status\":\"0\",\"admin\":false}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-08-29 07:06:14',101),(141,'AI模型管理',2,'com.twelvet.ai.server.controller.AiModelController.changeStatus()','PUT',1,'admin',NULL,'/ai/model/changeStatus','127.0.0.1','','{\"params\":{},\"modelId\":6,\"defaultFlag\":1}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-09-06 07:33:21',101),(142,'AI社区日报',0,'com.twelvet.ai.server.controller.AiReportController.generate()','POST',1,'admin',NULL,'/ai/report/generate','127.0.0.1','','',NULL,0,'当前无租户上下文，无法生成日报','2026-09-06 10:32:19',101),(143,'AI社区日报',0,'com.twelvet.ai.server.controller.AiReportController.generate()','POST',1,'admin',NULL,'/ai/report/generate','127.0.0.1','','','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"id\":1,\"tenantId\":1,\"reportDate\":\"2026-09-05\",\"title\":\"null社区日报 · 9月5日\",\"content\":\"## 一句话总览\\n9月5日：新上架 0 件、成交 0 单、新帖 0 条、跑腿完成 0 单。\\n\\n## 二手好物\\n今天很安静，快去发布闲置吧。\\n\\n## 邻里互助\\n今天没有跑腿单，大家有需要随时发布。\\n\\n## 社区动态\\n今天没有热帖，去广场发一条让大家认识你吧。\\n\",\"statsJson\":\"{\\\"newProducts\\\":0,\\\"doneOrders\\\":0,\\\"hotFeeds\\\":[],\\\"runnerDoneToday\\\":0,\\\"runnerNewReviews\\\":0,\\\"newFeeds\\\":0,\\\"runnerAvgRating\\\":0.0,\\\"topProducts\\\":[],\\\"doneAmount\\\":0.00,\\\"renewCount\\\":0,\\\"runnerDoneTotal\\\":4}\",\"source\":\"fallback\"}}',1,NULL,'2026-09-06 10:32:42',101),(144,'AI社区日报',0,'com.twelvet.ai.server.controller.AiReportController.generate()','POST',1,'admin',NULL,'/ai/report/generate','127.0.0.1','','','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"id\":1,\"tenantId\":1,\"reportDate\":\"2026-09-05\",\"title\":\"9月5日社区日报 | 今日风平浪静，邻居们都在忙啥呢？\",\"content\":\"## 一句话总览\\n\\n今天社区很安静，一切都在平稳运转中～\\n\\n## 二手好物\\n\\n今天没有新上架的闲置物品，看来大家都在整理自己的小窝。如果你家里有闲置想出手，欢迎挂到社区集市里！\\n\\n## 邻里互助\\n\\n今天没有新的跑腿完成单，不过社区累计已帮助居民完成 4 单，口碑一直在攒着呢～\\n\\n## 社区动态\\n\\n今天没有热帖，帖子区暂时安静。有什么想聊的、想分享的，欢迎大家发帖哦！\",\"statsJson\":\"{\\\"newProducts\\\":0,\\\"doneOrders\\\":0,\\\"hotFeeds\\\":[],\\\"runnerDoneToday\\\":0,\\\"runnerNewReviews\\\":0,\\\"newFeeds\\\":0,\\\"runnerAvgRating\\\":0.0,\\\"topProducts\\\":[],\\\"doneAmount\\\":0.00,\\\"renewCount\\\":0,\\\"runnerDoneTotal\\\":4}\",\"source\":\"llm\"}}',1,NULL,'2026-09-06 10:36:43',101),(145,'AI社区日报',0,'com.twelvet.ai.server.controller.AiReportController.generate()','POST',1,'admin',NULL,'/ai/report/generate','127.0.0.1','','','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"id\":1,\"tenantId\":1,\"reportDate\":\"2026-09-05\",\"title\":\"9月5日社区日报 | 今日风平浪静，邻居们都在忙啥呢？\",\"content\":\"## 一句话总览\\n\\n今天社区很安静，一切都在平稳运转中～\\n\\n## 二手好物\\n\\n今天没有新上架的闲置物品，看来大家都在整理自己的小窝。如果你家里有闲置想出手，欢迎挂到社区集市里！\\n\\n## 邻里互助\\n\\n今天没有新的跑腿完成单，不过社区累计已帮助居民完成 4 单，口碑一直在攒着呢～\\n\\n## 社区动态\\n\\n今天没有热帖，帖子区暂时安静。有什么想聊的、想分享的，欢迎大家发帖哦！\",\"statsJson\":\"{\\\"newProducts\\\":0,\\\"doneOrders\\\":0,\\\"hotFeeds\\\":[],\\\"runnerDoneToday\\\":0,\\\"runnerNewReviews\\\":0,\\\"newFeeds\\\":0,\\\"runnerAvgRating\\\":0.0,\\\"topProducts\\\":[],\\\"doneAmount\\\":0.00,\\\"renewCount\\\":0,\\\"runnerDoneTotal\\\":4}\",\"source\":\"llm\"}}',1,NULL,'2026-09-06 10:36:54',101),(146,'定时任务',2,'com.twelvet.quartz.server.controller.SysJobController.run()','PUT',1,'admin',NULL,'/job/cron/run','127.0.0.1','','{\"params\":{},\"jobId\":109,\"jobGroup\":\"DEFAULT\",\"misfirePolicy\":\"0\"}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-09-06 10:40:08',101),(147,'AI商品辅助',0,'com.twelvet.ai.server.controller.AiAssistController.productCopy()','POST',1,'admin',NULL,'/ai/assist/product-copy','127.0.0.1','','{\"productName\":\"苹果手机\",\"condition\":\"9成新\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"titles\":[\"苹果iPhone手机 9成新 功能完好 成色不错\",\"二手苹果手机 9成新 使用顺畅 性价比之选\",\"苹果智能手机 9成新 外观整洁 性能稳定\",\"成色9新iPhone苹果手机 正常使用痕迹少\",\"二手苹果iPhone 9成新 整体状态良好\"],\"description\":\"出一台二手苹果手机，整体成色约9成新，外观保持得不错，没有明显的磕碰或划痕，日常使用痕迹较少。功能一切正常，系统运行流畅，屏幕显示清晰，触控灵敏，电池状态稳定，续航表现符合正常使用水平。机器无拆修，所有功能按键均可正常使用。因为是日常自用换下来的，所以对这台机器比较熟悉，没有隐瞒问题。适合想要入手电梯性价比苹果手机的朋友，成色真实可靠，诚意出售，欢迎感兴趣的朋友联系了解。\",\"modelId\":6,\"modelName\":\"agnes-2.5-flash\"}}',1,NULL,'2026-09-06 17:10:28',101),(148,'AI商品辅助',0,'com.twelvet.ai.server.controller.AiAssistController.productImage()','POST',1,'admin',NULL,'/ai/assist/product-image','127.0.0.1','','{\"productName\":\"苹果iPhone手机 9成新 功能完好 成色不错\",\"category\":\"数码/手机\",\"condition\":\"9成新\",\"keywords\":\"出一台二手苹果手机，整体成色约9成新，外观保持得不错，没有明显的磕碰或划痕，日常使用痕迹较少。功能一切正常，系统运行流畅，屏幕显示清晰，触控灵敏，电池状态稳定，续航表现符合正常使用水平。机器无拆修，所有功能按键均可正常使用。因为是日常自用换下来的，所以对这台机器比较熟悉，没有隐瞒问题。适合想要入手电梯性价比苹果手机的朋友，成色真实可靠，诚意出售，欢迎感兴趣的朋友联系了解。\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"taskId\":52,\"imageUrl\":\"https://platform-outputs.agnes-ai.space/images/t2i/task_IH22K8lLnZedw1R9JkHh8UsCmh2ayY3B/output_71fd95597c17438c87b6cb3f1025cb46.png\",\"modelId\":1,\"modelName\":\"agnes-image-2.1-flash\"}}',1,NULL,'2026-09-06 17:10:49',101),(149,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-10/1788994319303.jpg\"}',1,NULL,'2026-09-10 06:51:59',101),(150,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":38,\"prompt\":\"让图中人物平躺在床上然后抬起双腿再岔开，小腿部分微微放下\",\"size\":\"2304x1728\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":53,\"model_id\":38,\"prompt\":\"一位人物平躺在柔软的平面上，身体放松，双臂自然放置于身体两侧。开始缓慢抬起双腿，双腿从床面缓缓升起，保持腿部线条流畅自然。随后双腿向两侧轻轻岔开，形成一个舒展的Y字形姿态。最后小腿部分微微下垂放松，膝盖保持柔和弯曲，整个动作缓慢优雅，呼吸感明显，展现出身体的柔韧与舒展感。采用中景构图，固定机位平视角度，柔和的室内漫射光，自然肤色质感，画面干净简洁的背景。\",\"status\":\"queued\"}}',1,NULL,'2026-09-10 06:53:47',101),(151,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":38,\"prompt\":\"让图中人物平躺在床上然后抬起双腿再岔开，小腿部分微微放下\",\"size\":\"2304x1728\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":54,\"model_id\":38,\"prompt\":\"一位人物平躺在床上，身体舒展放松，双腿缓慢抬起离床面约45度角，随后向两侧自然岔开至舒适幅度，小腿部分微微下垂放松。镜头采用中景固定机位，从正侧面或斜上方俯拍，完整呈现肢体动作轨迹。床品质感柔软，环境光线柔和温暖，氛围宁静居家。动作流畅连贯，无突兀跳跃，保持身体平衡与优雅姿态。\",\"status\":\"queued\"}}',1,NULL,'2026-09-10 06:55:48',101),(152,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":38,\"prompt\":\"让图中人物平躺在床上然后抬起双腿再岔开，小腿部分微微放下\",\"size\":\"2304x1728\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788994319303.jpg\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":57,\"model_id\":38,\"prompt\":\"一位成年女性平躺于白色床单的床上，身体放松伸展，双臂自然放置于身体两侧。她缓慢抬起双腿至垂直角度，随后双腿向两侧岔开形成V字姿态，膝盖保持轻微弯曲，小腿部分缓缓下降至约45度位置后停下。整个动作流畅连贯、速度适中，展现腿部柔韧性与控制力。卧室场景，柔和的自然光从侧窗洒入，营造温馨安静的氛围。固定机位，全身入镜，平视拍摄角度，确保动作全程清晰可见。\",\"status\":\"queued\"}}',1,NULL,'2026-09-10 07:02:03',101),(153,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-10/1788996640327.jpg\"}',1,NULL,'2026-09-10 07:30:40',101),(154,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"modelId\":37,\"prompt\":\"让图中人物跳一段热舞\",\"genType\":\"img2vid\",\"inputImages\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788996640327.jpg\",\"width\":1280,\"height\":720,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":40,\"model_id\":37,\"prompt\":\"图中人物开始跳起充满活力的热舞，身体随着节奏律动，动作流畅有力；手臂舒展挥舞，配合步伐变换做出经典的街舞旋转与定格姿势；发丝随动作飞扬，衣摆自然飘动；面部表情自信洋溢，眼神有神；背景为动感舞台灯光，霓虹色彩交替闪烁，丁达尔光效穿透烟雾；镜头保持中景全景为主，配合轻微推拉增强节奏感；整体氛围热烈奔放，高饱和度色彩，电影感动态视觉效果。\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-09-10 07:31:15',101),(155,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-10/1788999088304.jpg\"}',1,NULL,'2026-09-10 08:11:28',101),(156,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','127.0.0.1','','{\"params\":{},\"modelId\":37,\"prompt\":\"让图中人物跳舞\",\"genType\":\"img2vid\",\"inputImages\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-10/1788999088304.jpg\",\"width\":1280,\"height\":720,\"numFrames\":121,\"frameRate\":24}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":53,\"model_id\":37,\"prompt\":\"保持原图中人物的面部特征与服装细节不变；人物随着音乐节奏自然摆动身体，手臂优雅挥舞，脚步轻盈踏动，头发随动作微微飘动；背景保持静止稳定；镜头固定在中景角度，平稳记录完整舞蹈动作；光影柔和均匀，氛围活泼欢快，画面清晰细腻，电影级画质，高饱和度色彩，流畅自然的动画过渡效果。\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-09-10 08:11:54',101),(157,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-11/1789139434533.jpg\"}',1,NULL,'2026-09-11 23:10:35',101),(158,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":38,\"prompt\":\"将白袜换成性感黑丝脸上面带红晕\",\"size\":\"2304x1728\",\"genType\":\"img2img\",\"inputImage\":\"https://image123qwe.oss-cn-beijing.aliyuncs.com//2026-09-11/1789139434533.jpg\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":58,\"model_id\":38,\"prompt\":\"一位年轻女性，穿着黑色透肉丝袜，腿部线条修长优雅，面部带有自然的红润气色，双颊泛着淡淡羞涩的红晕。保留原始构图的相机角度与主体姿态，整体穿搭精致时尚。光线柔和均匀，突出丝袜的细腻质感与面部的健康血色感，画面干净高级。\",\"status\":\"queued\"}}',1,NULL,'2026-09-11 23:11:28',101),(159,'AI 音乐',1,'com.twelvet.ai.server.controller.AiMusicController.submit()','POST',1,'admin',NULL,'/ai/music/songs/submit','127.0.0.1','','{\"type\":\"text_to_song\",\"prompt\":\"夏夜海边久别重逢\",\"style\":\"温暖的中文民谣\",\"lyrics\":\"[Verse 1]\\n凌晨一点的海边\\n湿沙留着余温\\n浪头刚退去\\n他站在水线边\\n攥着那张旧船票\\n票根褪成了灰\\n海风把裙摆吹得响\\n像那年码头上的样子\\n[Verse 2]\\n她说船走了别等\\n他点头 没说话\\n后来船真的走了\\n站台也拆了\\n他把船票翻来覆去\\n名字看不清了\\n只有出发那一格\\n还留着她的笔迹\\n[Chorus]\\n浪退得再远\\n也带不走这片沙\\n他攥着那张旧船票\\n站在夜里\\n像还等着\\n一个不上船的人\\n\\n[Verse 3]\\n如今他学会折好\\n放进口袋里\\n湿沙早凉透了\\n余温只剩他自己\\n海风还在吹\\n吹得回忆猎猎响\\n他终于转过身\\n走了两步\\n[Outro]\\n走了两步\\n又停下\\n把船票拿出来\\n看了一眼\",\"gender\":\"female\",\"expectDuration\":60,\"title\":\"旧船票\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"vendorTaskId\":\"210bf467-e98e-4f4e-8f47-5a6a6683ce82\",\"id\":1,\"status\":1}}',1,NULL,'2026-09-12 10:38:05',101),(160,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE',NULL,0,'文件上传异常','2026-09-16 23:07:58',101),(161,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-16/1789572181564.txt\"}',1,NULL,'2026-09-16 23:23:02',101),(162,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":45,\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"2048x2048\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":60,\"model_id\":45,\"prompt\":\"竖屏9:16构图，日系清新漫剧风格，2D扁平上色，清晰锐利的黑色轮廓线，超高细节度。主体为22岁年轻女性角色标准立绘，呈现上半身至大腿中部的半身视角，自然站立姿态，表情温柔恬静。人物特征：浅棕色长卷发自然垂落，发丝纹理细腻，杏眼清澈，浅豆沙色唇妆，身穿米色质地柔软的针织长裙，针织纹理清晰可见。背景为纯净的浅淡纯色，无杂物干扰，突出主体。光照为柔和漫射光，无强烈硬影，整体色彩低饱和度，色调温暖清新，氛围宁静优雅。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:08:05',101),(163,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":45,\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":61,\"model_id\":45,\"prompt\":\"竖屏9:16构图，日系清新漫剧风格，平涂上色与清晰硬朗的轮廓线，超高细节。主体为22岁年轻女性，浅棕色长卷发自然垂落，杏眼清澈，浅豆沙色唇妆，身穿米色针织长裙，呈现温柔恬静的气质。角色采取自然站立姿态，呈现半身立绘视角。背景为纯净的浅色纯色平面，无杂物干扰。光照采用柔和的漫射光，色彩整体保持低饱和度与柔和质感，营造出干净、通透且舒适的视觉氛围，强调角色面部与服装的高清细节表现。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:08:47',101),(164,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":41,\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":62,\"model_id\":41,\"prompt\":\"竖屏9:16构图，日系清新漫剧风格半身立绘。主体为22岁年轻女性，拥有浅棕色长卷发，发丝柔顺且带有自然光泽，杏眼清澈温柔，浅豆沙色唇妆精致淡雅，身穿米色针织长裙，衣物材质呈现柔软细腻的纹理。角色保持自然放松的站姿，双手轻放身侧，气质温婉柔和。背景为纯净的浅色单色底，无杂乱装饰，突出人物主体。光照采用柔和漫射光，色彩整体低饱和、高明度，营造出清新、治愈、通透的氛围。画面具备清晰流畅的轮廓线，平涂上色技法，细节刻画精致，线条干净利落，无多余噪点。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:10:12',101),(165,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":38,\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"864x1536\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":63,\"model_id\":38,\"prompt\":\"一张竖屏9:16比例的女性角色标准立绘，采用日系清新漫剧画风。主体为一名22岁年轻女生，拥有浅棕色长卷发，发丝光泽自然，杏眼清澈，唇色为浅豆沙，身穿米色针织长裙，质地柔软蓬松。角色呈自然站姿，半身构图，神情温柔静谧。背景为纯色浅调，无杂乱元素。光照采用柔和漫射光，无强烈硬影，色彩整体呈现低饱和度的莫兰迪色系，视觉清新治愈。画面线条清晰流畅，平涂上色风格，细节极致丰富，轮廓线锐利，展现高级感的二次元美学。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:10:32',101),(166,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":41,\"prompt\":\"一只白色毛绒绒的海豹宝宝漂浮在平静海面上，柔和晨光，写实摄影风格\",\"size\":\"1024x1024\",\"genType\":\"text2img\"}',NULL,0,'I/O error on POST request for \"http://localhost:8001/ai/image/generate\": Connection refused: no further information: localhost/127.0.0.1:8001','2026-09-19 07:32:13',101),(167,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":41,\"prompt\":\"一只白色毛绒绒的海豹宝宝漂浮在平静海面上，柔和晨光，写实摄影风格\",\"size\":\"1024x1024\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":64,\"model_id\":41,\"prompt\":\"一只圆滚滚的白色小海豹幼崽，全身覆盖着极厚实、蓬松且柔软的纯白绒毛，质感细腻逼真，仿佛能感受到其温暖的触感。它惬意地漂浮在如镜面般平静的深蓝色海面上，身体大部分浸没在水中，仅露出圆润的背部和头部，姿态慵懒而安详。背景是清晨薄雾笼罩的极简海景，远处海天一色，过渡平滑。柔和的清晨金色阳光从侧前方低角度洒落，营造出温暖而宁静的氛围，光线在海豹的绒毛边缘勾勒出细腻的金边（轮廓光），并在清澈的海水表面形成柔和的波光粼粼倒影。采用85mm中焦镜头，平视视角，大光圈营造梦幻般的浅景深效果，背景虚化柔和。高分辨率，8k，极致写实摄影风格，展现生物特有的萌态与自然界静谧美感，色彩清新通透，光影层次丰富。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:33:00',101),(168,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":40,\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":65,\"model_id\":40,\"prompt\":\"日系清新漫剧风格，竖屏9:16构图。画面主体为一位22岁的年轻女性，呈现半身标准立绘姿态。她拥有浅棕色长卷发，发丝细腻柔顺，自然垂落；五官精致，杏眼清澈温柔，唇色为浅豆沙红，皮肤白皙通透。身穿米色针织长裙，材质纹理清晰可见，剪裁简约舒适，展现出温柔恬静的气质。人物保持自然站姿，四肢比例正常，面部表情平和。背景为纯净的浅色纯色背景，无多余杂乱装饰，突出人物主体。光影采用柔和漫射光，无强烈阴影，色彩整体低饱和度，色调清新柔和。绘画技法强调平涂上色与清晰轮廓线，线条流畅准确，细节超高精度，画面干净整洁，视觉重心明确。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:40:37',101),(169,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":40,\"prompt\":\"一只橘色小猫趴在窗台上的木质花盆旁，午后暖阳斜射，浅景深，写实摄影风格\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":66,\"model_id\":40,\"prompt\":\"一只毛茸茸的橘色虎斑猫慵懒地趴在窗台上，身体舒展，胡须清晰可见，眼神柔和地注视着前方。旁边是一个质朴的木质花盆，纹理自然，略显粗糙。午后温暖的金色阳光透过玻璃斜射进来，在猫身上形成柔和的高光，并在窗台投下细长的光影。采用浅景深效果，背景虚化，突出主体。写实摄影风格，高分辨率，细节丰富，氛围温馨宁静。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:48:42',101),(170,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"prompt\":\"女性主角标准立绘模板 竖屏9:16，日系清新漫剧画风，平涂上色，清晰轮廓线，超高细节 年轻女生，22岁，浅棕色长卷发，杏眼，浅豆沙唇色，米色针织长裙，温柔气质，半身立绘，纯色浅背景，自然站姿 柔和漫射光，色彩柔和低饱和 负面提示词：脸部变形，五官错位，肢体畸形，杂乱装饰，画面模糊，阴影过重\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":67,\"model_id\":45,\"prompt\":\"竖屏9:16构图，日系清新漫剧风格，平涂上色，线条清晰锐利，超高细节度。画面主体为22岁年轻女性半身立绘，浅棕色长卷发自然垂落，发丝细腻有光泽，杏眼温润有神，眼神柔和专注，浅豆沙色唇妆，米色针织长裙质感柔软贴身，体现温柔知性气质。自然站姿，双手自然下垂或微垂，肢体比例修长标准。背景为纯净浅色系（如淡米白或极浅灰），无杂乱元素，突出人物主体。光影采用柔和漫射光，无强烈高光与深重阴影，色彩低饱和度高纯度，整体氛围清新治愈，视觉层次分明，细节丰富。\",\"status\":\"queued\"}}',1,NULL,'2026-09-19 07:56:57',101),(171,'AI 音乐',1,'com.twelvet.ai.server.controller.AiMusicController.submit()','POST',1,'admin',NULL,'/ai/music/songs/submit','127.0.0.1','','{\"type\":\"text_to_song\",\"prompt\":\"青春热恋\",\"style\":\"温暖流行\",\"lyrics\":\"[Verse]\\n七月操场 蝉在叫\\n校服贴在背 汗没掉\\n汽水水珠 顺着指尖绕\\n\\n[Chorus]\\n你递来 一杯柠檬水\\n指尖碰上 心跳就乱了\",\"gender\":\"male\",\"expectDuration\":30,\"modelId\":34}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"vendorTaskId\":\"fc12cefd-8b2a-4b88-8336-9f06fb36cee5\",\"id\":2,\"status\":1}}',1,NULL,'2026-09-19 08:03:38',101),(172,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-23/1790133296085.png\"}',1,NULL,'2026-09-23 11:14:56',101),(173,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-23/1790133748352.png\"}',1,NULL,'2026-09-23 11:22:28',101),(174,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','1.199.37.227','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-09-23/1790153188463.jpg\"}',1,NULL,'2026-09-23 16:46:32',101),(175,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','1.199.37.227','','{\"params\":{},\"prompt\":\"让图中人物穿黑色丝袜\",\"size\":\"1472x2624\",\"genType\":\"img2img\",\"inputImage\":\"https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-09-23/1790153188463.jpg\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":68,\"model_id\":45,\"prompt\":\"让图中人物穿黑色丝袜\",\"status\":\"queued\"}}',1,NULL,'2026-09-23 16:49:58',101),(176,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','182.118.233.111','','{\"params\":{},\"modelId\":45,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"size\":\"1024x1024\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":69,\"model_id\":45,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"status\":\"queued\"}}',1,NULL,'2026-09-23 17:01:58',101),(177,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','182.118.233.111','','{\"params\":{},\"modelId\":40,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"size\":\"2048x2048\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":70,\"model_id\":40,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"status\":\"queued\"}}',1,NULL,'2026-09-23 17:07:43',101),(178,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','182.118.233.111','','{\"params\":{},\"modelId\":38,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"size\":\"1024x1024\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":71,\"model_id\":38,\"prompt\":\"生成一个活泼可爱的唐朝小公主的形象\",\"status\":\"queued\"}}',1,NULL,'2026-09-23 17:12:31',101),(179,'AI图片生成',3,'com.twelvet.ai.server.controller.AiImageController.remove()','DELETE',1,'admin',NULL,'/ai/image/64','223.88.55.36','','{taskIds=64}','{\"code\":200,\"msg\":\"操作成功\"}',1,NULL,'2026-09-23 20:30:21',101),(180,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','223.88.55.36','','{\"params\":{},\"modelId\":47,\"prompt\":\"男角色，表现的生活很没劲\",\"size\":\"2048x2048\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":72,\"model_id\":47,\"prompt\":\"A weary male figure in his 30s, slumped posture, dull unexpressive eyes, wearing faded, wrinkled gray office clothes, sitting in a dimly lit, cluttered apartment. The lighting is cold and flat, casting long, uninteresting shadows. The color palette is desaturated, dominated by grays, browns, and muted blues, conveying a sense of monotony and lack of vibrancy. The composition is centered but unengaging, with the character occupying the majority of the frame in a static, lifeless pose. The atmosphere is heavy with a sense of ennui and stagnation, emphasizing the mundane and tedious nature of his daily existence.\",\"status\":\"queued\"}}',1,NULL,'2026-09-23 20:31:58',101),(181,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','223.88.55.36','','{\"params\":{},\"modelId\":46,\"prompt\":\"男角色，表现的生活很没劲\",\"genType\":\"text2vid\",\"width\":960,\"height\":720}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":57,\"model_id\":46,\"prompt\":\"a melancholic young man sitting on a dimly lit park bench, hunched shoulders, staring blankly into the distance, holding a cold coffee cup, rain dripping from his clothes, gray desaturated color palette, heavy shadows, overcast sky, urban decay in the background, expression of deep boredom and emptiness, cinematic lighting, 4k, highly detailed, realistic skin texture, lonely atmosphere\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-09-23 20:34:01',101),(182,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','123.52.10.182','','{\"params\":{},\"modelId\":45,\"prompt\":\"绝世美女身穿浅黑色棉柔细带挂脖菱形围兜，露背设计，背后无系带，胯部浅黑色柔棉迷你裙，均有金色线条花纹点缀\",\"size\":\"1728x2304\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":73,\"model_id\":45,\"prompt\":\"A stunningly beautiful woman wearing a delicate light-black cotton-haltered top with thin straps and a diamond-patterned apron featuring intricate gold line accents; backless design without rear ties, paired with a matching mini skirt of the same soft cotton and gold detailing. Her pose is graceful and slightly turned, highlighting her elegant posture and the outfit\'s refined structure. Soft studio lighting creates gentle highlights on the fabric and skin, with a clean gradient background that emphasizes the subject. Hyper-realistic skin texture, natural makeup, and crisp fabric details with visible weave. Cinematic composition, shallow depth of field, fashion photography, ultra-detailed, 8k resolution, elegant and sophisticated atmosphere.\",\"status\":\"queued\"}}',1,NULL,'2026-09-30 15:29:01',101),(183,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','123.52.10.182','','{\"params\":{},\"modelId\":47,\"prompt\":\"绝世美女身穿浅黑色棉柔细带挂脖菱形围兜，露背设计，背后无系带，胯部浅黑色柔棉迷你裙，均有金色线条花纹点缀\",\"size\":\"1728x2304\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":74,\"model_id\":47,\"prompt\":\"A stunningly beautiful woman with delicate features and an elegant pose, wearing a light black soft-cotton halter bodysuit with thin straps and a distinctive diamond-shaped apron-style front panel. The garment features an open back design with no ties or fasteners, highlighting her shoulders and spine. She wears a matching light black soft-cotton mini-skirt with a similar texture. Both the top and skirt are embellished with intricate, subtle gold line patterns and floral embroidery that contrast beautifully against the matte fabric. The lighting is soft and cinematic, highlighting the sheen of the gold threads and the softness of the cotton. The background is a minimalist studio setting with a neutral tone to emphasize the outfit\'s details.\",\"status\":\"queued\"}}',1,NULL,'2026-09-30 15:30:29',101),(184,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','123.52.10.182','','{\"params\":{},\"modelId\":38,\"prompt\":\"绝世美女身穿浅黑色棉柔细带挂脖菱形围兜，露背设计，背后无系带，胯部浅黑色柔棉迷你裙，均有金色线条花纹点缀\",\"size\":\"864x1536\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":75,\"model_id\":38,\"prompt\":\"An extreme close-up and full-body cinematic shot of a stunningly beautiful woman with flawless skin and delicate features, exuding an air of modern elegance. She wears a sophisticated light-black cotton halter top with a diamond-patterned apron, featuring thin straps that cross at the neck. The back is designed with an open cut, lacking any ties, highlighting her elegant back and shoulder blades. Her lower body is adorned in a matching light-black soft cotton mini skirt, both garments intricately embroidered with thin, shimmering gold linear floral patterns that contrast beautifully against the dark fabric. The lighting is soft and diffused, creating a gentle glow on her skin while emphasizing the texture of the cotton and the metallic sheen of the gold accents. The composition focuses on the interplay of light and shadow, with a blurred neutral background to keep attention on the subject. High-resolution, 8k, detailed fabric texture, refined fashion photography, ethereal atmosphere.\",\"status\":\"queued\"}}',1,NULL,'2026-09-30 15:32:38',101),(185,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.savePersona()','POST',1,'admin',NULL,'/ai/creation/personas','127.0.0.1','','{\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"banned_words\":\"\",\"voice_id\":\"\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"look_image_urls\":[],\"voice_id\":\"\",\"banned_words\":\"\",\"status\":1,\"create_time\":\"2026-10-02 23:59:17\"}}',1,NULL,'2026-10-02 23:59:17',101),(186,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.createProject()','POST',1,'admin',NULL,'/ai/creation/projects','127.0.0.1','','{\"source_type\":\"original\",\"persona_id\":1,\"topic\":\"用ai视频描述打工人的一天\"}',NULL,0,'500 Internal Server Error on POST request for \"http://localhost:8001/ai/creation/projects\": \"Internal Server Error\"','2026-10-03 00:01:44',101),(187,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.createProject()','POST',1,'admin',NULL,'/ai/creation/projects','127.0.0.1','','{\"source_type\":\"original\",\"persona_id\":1,\"topic\":\"用ai视频描述打工人的一天\"}',NULL,0,'500 Internal Server Error on POST request for \"http://localhost:8001/ai/creation/projects\": \"Internal Server Error\"','2026-10-03 00:03:44',101),(188,'AI 音色库',1,'com.twelvet.ai.server.controller.AiVoiceProfileController.train()','POST',1,'admin',NULL,'/ai/music/voices/train','127.0.0.1','','FILE',NULL,0,'502 Bad Gateway on POST request for \"http://localhost:8001/ai/music/voices\": \"{\"detail\":{\"code\":\"MUSIC_VENDOR_ERROR\",\"message\":\"硅基流动上传失败(上游 400): {\\\"code\\\":20048,\\\"message\\\":\\\"Invalid custom name. only letters and digits and _ and - are supported. Should not exceed 64 chars\\\",\\\"data\\\":null}\"}}\"','2026-10-03 00:17:48',101),(189,'AI 音色库',1,'com.twelvet.ai.server.controller.AiVoiceProfileController.train()','POST',1,'admin',NULL,'/ai/music/voices/train','127.0.0.1','','FILE','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"vendor\":\"siliconflow\",\"id\":1,\"vendorVoiceId\":\"speech:xiaoli:d9ji5ks50mis73e3bijg:gcbakiyvxhejzzzbdoym\",\"status\":2}}',1,NULL,'2026-10-03 00:24:59',101),(190,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.savePersona()','POST',1,'admin',NULL,'/ai/creation/personas','127.0.0.1','','{\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"banned_words\":\"\",\"voice_id\":\"speech:xiaoli:d9ji5ks50mis73e3bijg:gcbakiyvxhejzzzbdoym\",\"persona_id\":1}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"look_image_urls\":[],\"voice_id\":\"speech:xiaoli:d9ji5ks50mis73e3bijg:gcbakiyvxhejzzzbdoym\",\"banned_words\":\"\",\"status\":1,\"create_time\":\"2026-10-02 23:59:17\"}}',1,NULL,'2026-10-03 00:25:28',101),(191,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.generateLook()','POST',1,'admin',NULL,'/ai/creation/personas/look','127.0.0.1','','{\"persona_id\":1,\"appearance_prompt\":\"热衷于科技分享的数字人\",\"count\":3}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 00:26:04',101),(192,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.createProject()','POST',1,'admin',NULL,'/ai/creation/projects','127.0.0.1','','{\"source_type\":\"original\",\"persona_id\":1,\"topic\":\"分析ai浪潮\",\"llm_model_id\":19}',NULL,0,'口播稿LLM 调用失败: 404 page not found','2026-10-03 00:37:42',101),(193,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 05:12:25',101),(194,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 05:19:06',101),(195,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 05:39:18',101),(196,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.savePersona()','POST',1,'admin',NULL,'/ai/creation/personas','127.0.0.1','','{\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"banned_words\":\"\",\"voice_id\":\"\",\"persona_id\":1}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"look_image_urls\":[],\"voice_id\":\"\",\"banned_words\":\"\",\"status\":1,\"create_time\":\"2026-10-02 23:59:17\"}}',1,NULL,'2026-10-03 05:58:23',101),(197,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 05:58:33',101),(198,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/1/retry','127.0.0.1','','1 1 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":1,\"stage\":\"image\"}}',1,NULL,'2026-10-03 06:13:47',101),(199,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/2/retry','127.0.0.1','','1 2 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":2,\"stage\":\"image\"}}',1,NULL,'2026-10-03 06:14:25',101),(200,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/3/retry','127.0.0.1','','1 3 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":3,\"stage\":\"image\"}}',1,NULL,'2026-10-03 06:14:48',101),(201,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/4/retry','127.0.0.1','','1 4 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":4,\"stage\":\"image\"}}',1,NULL,'2026-10-03 06:18:29',101),(202,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/5/retry','127.0.0.1','','1 5 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":5,\"stage\":\"image\"}}',1,NULL,'2026-10-03 06:22:07',101),(203,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 06:25:37',101),(204,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/1/retry','127.0.0.1','','1 1 {\"stage\":\"video\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":1,\"stage\":\"video\"}}',1,NULL,'2026-10-03 06:48:12',101),(205,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/1/retry','127.0.0.1','','1 1 {\"stage\":\"video\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":1,\"stage\":\"video\"}}',1,NULL,'2026-10-03 06:51:36',101),(206,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 06:58:47',101),(207,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 07:13:49',101),(208,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {\"video_model_id\":37}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 07:38:27',101),(209,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/3/retry','127.0.0.1','','1 3 {\"stage\":\"video\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":3,\"stage\":\"video\"}}',1,NULL,'2026-10-03 07:40:41',101),(210,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 07:47:18',101),(211,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}',NULL,0,'渲染进行中，请勿重复提交','2026-10-03 07:47:19',101),(212,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/2/retry','127.0.0.1','','1 2 {\"stage\":\"video\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":2,\"stage\":\"video\"}}',1,NULL,'2026-10-03 07:54:41',101),(213,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {\"video_model_id\":2}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 07:55:59',101),(214,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {\"video_model_id\":2}',NULL,0,'渲染进行中，请勿重复提交','2026-10-03 07:56:01',101),(215,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {\"video_model_id\":46}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:06:39',101),(216,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {\"video_model_id\":46}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:16:44',101),(217,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/3/retry','127.0.0.1','','1 3 {\"stage\":\"video\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":3,\"stage\":\"video\"}}',1,NULL,'2026-10-03 08:24:53',101),(218,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.retryShot()','POST',1,'admin',NULL,'/ai/creation/projects/1/shots/3/retry','127.0.0.1','','1 3 {\"stage\":\"image\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"shot_id\":3,\"stage\":\"image\"}}',1,NULL,'2026-10-03 08:25:19',101),(219,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:36:56',101),(220,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:38:59',101),(221,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:41:17',101),(222,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/1/render','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-03 08:44:42',101),(223,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.publishPackage()','POST',1,'admin',NULL,'/ai/creation/projects/1/publish-package','127.0.0.1','','1 {}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"title\":\"AI红利普通人怎么上车？3步实操法\",\"caption\":\"别再只看不练了！普通人上车的三步法：1.直接上手用；2.技能+AI放大价值；3.分享经验赚流量。淘汰你的不是AI，是会用AI的人。关注我，下期送三个免费神器，带你一起抓住这波红利！\",\"tags\":[\"AI干货\",\"职场逆袭\",\"效率工具\",\"普通人机会\",\"搞钱思维\",\"技能变现\",\"AI教程\",\"个人成长\"],\"ai_declaration\":\"本视频由 AI 辅助生成\"}}',1,NULL,'2026-10-03 08:47:07',101),(224,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.savePersona()','POST',1,'admin',NULL,'/ai/creation/personas','127.0.0.1','','{\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"banned_words\":\"\",\"voice_id\":\"female_0008_a\",\"persona_id\":1}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"name\":\"ai小李\",\"description\":\"ai分享\",\"style_fewshot\":\"\",\"look_image_urls\":[],\"voice_id\":\"female_0008_a\",\"banned_words\":\"\",\"status\":1,\"create_time\":\"2026-10-02 23:59:17\"}}',1,NULL,'2026-10-07 08:59:02',101),(225,'AI创作',1,'com.twelvet.ai.server.controller.AiCreationController.generateLook()','POST',1,'admin',NULL,'/ai/creation/personas/look','127.0.0.1','','{\"persona_id\":1,\"appearance_prompt\":\"一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间\",\"count\":3}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"status\":\"generating\"}}',1,NULL,'2026-10-07 09:02:24',101),(226,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":47,\"prompt\":\"一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间\",\"size\":\"1472x2624\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":99,\"model_id\":47,\"prompt\":\"一位28岁中国女程序员半身口播镜头，正对镜头直视观众，面带自然亲和的微笑，嘴唇微张似在生动讲解。她佩戴细框眼镜，梳着整洁的低马尾，身穿燕麦色针织开衫内搭纯白T恤，坐姿端正，双手做出自然专业的讲解手势。背景为现代居家办公桌，双显示器上虚化的代码界面、机械键盘及绿植细节丰富，暖色台灯与屏幕冷光形成冷暖交织的氛围，背景干净且深度虚化。采用50mm镜头，f/1.8大光圈浅景深效果，柔和正面补光，竖屏构图，顶部预留字幕空间，呈现出专业又亲切的口播博主风格，真实摄影质感，高清细节清晰锐利。\",\"status\":\"queued\"}}',1,NULL,'2026-10-07 09:05:54',101),(227,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','127.0.0.1','','{\"params\":{},\"modelId\":40,\"prompt\":\"一位28岁中国女程序员，半身口播镜头，正对镜头，直视观众，自然亲和微笑，嘴唇微张像正在讲解，戴细框眼镜，低马尾，燕麦色针织开衫内搭白T，坐姿端正，双手自然做讲解手势，现代居家办公桌，双显示器显示虚化代码，机械键盘，绿植，暖色台灯与屏幕冷光混合，背景干净虚化，口播博主风格，专业又亲切，柔和正面补光，50mm镜头，f/1.8，浅景深，真实摄影，高清细节，竖屏构图，上方留字幕空间\",\"size\":\"1600x2848\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":100,\"model_id\":40,\"prompt\":\"一位28岁中国女性程序员博主，半身特写，竖屏构图，上方预留字幕空间。她身穿燕麦色针织开衫内搭白色T恤，佩戴细框眼镜，扎着利落的低马尾，坐姿端正。面部表情自然亲和，嘴角微扬，嘴唇微张仿佛正在语重心长地讲解。双手位于画面下方，做出自然流畅的讲解手势。背景为现代居家办公桌，双显示器上显示着虚化的代码界面，桌案上摆放着机械键盘与生机盎然的绿植。光线采用暖色台灯与屏幕冷光混合，辅以柔和正面补光，营造专业且温馨的氛围。背景干净且深度虚化，使用50mm镜头，f/1.8大光圈产生浅景深效果，突出主体。风格为真实摄影，高清细节，口播博主质感。\",\"status\":\"queued\"}}',1,NULL,'2026-10-07 09:07:56',101),(228,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.setPersonaLook()','POST',1,'admin',NULL,'/ai/creation/personas/look/set','127.0.0.1','','{\"persona_id\":1,\"image_url\":\"https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c\",\"primary\":true}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"persona_id\":1,\"look_image_urls\":[\"https://dynamic.senseaudio.cn/image/08204c04-8101-4850-a7c3-c2028aa3660c\"]}}',1,NULL,'2026-10-07 09:43:43',101),(229,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/2/render','127.0.0.1','','2 {\"image_model_id\":38,\"video_model_id\":37}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":2,\"status\":\"generating\"}}',1,NULL,'2026-10-07 09:48:03',101),(230,'AI创作',2,'com.twelvet.ai.server.controller.AiCreationController.render()','POST',1,'admin',NULL,'/ai/creation/projects/2/render','127.0.0.1','','2 {\"image_model_id\":38,\"video_model_id\":46}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"project_id\":2,\"status\":\"generating\"}}',1,NULL,'2026-10-07 09:56:32',101),(231,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','223.88.55.136','','{\"params\":{},\"modelId\":40,\"prompt\":\"8K超高清，虚幻引擎5质感，PBR物理材质，精细发丝渲染，古风漫剧女主角人设图，东方古典审美，原创角色设计；\\n\\n女主身份：十八到二十二岁的清冷世家嫡女，像从旧书页里走出来的白月光；\\n\\n脸型五官：鹅蛋脸，眉眼细长，眼神安静疏离，鼻梁秀挺，唇色很淡，冷白皮，淡粉色眼妆，气质清雅克制；\\n\\n发型名称：流云垂鬟髻；\\n\\n发型结构：头顶做轻盈低髻，两侧垂鬟自然下落，后方长发半披，整体线条像流云一样柔和，不要厚重宫廷发髻；\\n\\n发饰搭配：白玉簪、细珍珠链、少量银色流苏，发饰集中在发髻上方和耳后，素雅清冷；\\n\\n发丝细节：额前少量空气感碎发，鬓边两缕细发贴近脸侧，发丝单根细节清晰，发尾顺滑垂落；\\n\\n服饰设定：月白色交领襦裙，浅银暗纹刺绣，轻薄白纱披帛，腰封简洁，衣料有细腻丝绸光泽；\\n\\n光线背景：柔和冷调侧光，米黄色宣纸古风背景，中式细边框，画面干净留白；\\n\\n画面为单人半身角色设定图，正面微侧角度，头发、发饰、服饰清晰可见，精致漫剧质感；\\n\\n无文字，无水印，无logo，不要现代首饰，不要浓妆，不要网红脸，不要夸张大眼\",\"size\":\"3456x2592\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":109,\"model_id\":40,\"prompt\":\"8K ultra-high resolution, Unreal Engine 5 visual quality, PBR physical material rendering, precise individual hair strand simulation. Character design sheet of a female protagonist in an ancient Chinese manga style, embodying Eastern classical aesthetics. An original character depicted as a cold, dignified daughter of a noble family, aged 18-22, resembling a \'moonlight\' figure from an old book. Oval face with fine, slender eyebrows and eyes, calm and detached gaze, straight and elegant nose, pale lips, cool white skin tone, and light pink eye makeup, projecting a restrained and pure temperament. Hairstyle is a \'Flowing Cloud Loose Bun\': a light low bun on top with loose side buns falling naturally, semi-covered long hair at the back, soft cloud-like lines, avoiding heavy palace styles. Accessories include a white jade hairpin, fine pearl chain, and minimal silver tassels placed at the bun top and behind the ears, elegant and cool. Hair details feature airy baby hairs on the forehead, two thin strands near the cheeks, and smooth falling ends. Outfit is a moon-white crossover collar ruqun with light silver dark-pattern embroidery, a thin white silk veil, a simple waistband, and delicate silk sheen. Lighting is soft cold-toned side light against a beige xuan paper background with a thin Chinese frame, clean composition with negative space. Single-person half-body character sheet, frontal slightly angled view, clearly showing hair, accessories, and clothing, refined manga texture.\",\"status\":\"queued\"}}',1,NULL,'2026-10-07 18:17:22',101),(232,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','223.88.55.136','','{\"params\":{},\"modelId\":40,\"prompt\":\"8K超高清，虚幻引擎5质感，PBR物理材质，精细发丝渲染，古风漫剧女主角人设图，东方古典审美，原创角色设计；\\n\\n女主身份：十八到二十二岁的清冷世家嫡女，像从旧书页里走出来的白月光；\\n\\n脸型五官：鹅蛋脸，眉眼细长，眼神安静疏离，鼻梁秀挺，唇色很淡，冷白皮，淡粉色眼妆，气质清雅克制；\\n\\n发型名称：流云垂鬟髻；\\n\\n发型结构：头顶做轻盈低髻，两侧垂鬟自然下落，后方长发半披，整体线条像流云一样柔和，不要厚重宫廷发髻；\\n\\n发饰搭配：白玉簪、细珍珠链、少量银色流苏，发饰集中在发髻上方和耳后，素雅清冷；\\n\\n发丝细节：额前少量空气感碎发，鬓边两缕细发贴近脸侧，发丝单根细节清晰，发尾顺滑垂落；\\n\\n服饰设定：月白色交领襦裙，浅银暗纹刺绣，轻薄白纱披帛，腰封简洁，衣料有细腻丝绸光泽；\\n\\n光线背景：柔和冷调侧光，米黄色宣纸古风背景，中式细边框，画面干净留白；\\n\\n画面为单人半身角色设定图，正面微侧角度，头发、发饰、服饰清晰可见，精致漫剧质感；\\n\\n无文字，无水印，无logo，不要现代首饰，不要浓妆，不要网红脸，不要夸张大眼\",\"size\":\"2048x2048\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":110,\"model_id\":40,\"prompt\":\"8K ultra-high resolution, Unreal Engine 5 aesthetic, PBR physically based rendering, intricate hair strand simulation. Character design sheet of an ancient Chinese drama female lead, embodying Eastern classical aesthetics. An 18-22 year old elite family heiress with a cool, detached \'white moonlight\' aura. Oval face, slender eyebrows and eyes, quiet and aloof gaze, prominent nose bridge, pale lips, cool fair skin with subtle pink eyeshadow, elegant and restrained temperament. Hairstyle: \'Flowing Cloud Drifting Bun\' (Liu Yun Chui Huan Ji), featuring a light low bun on top, side buns falling naturally, half-covered long hair at the back with soft, cloud-like lines, avoiding heavy palace styles. Accessories: white jade hairpin, delicate pearl chain, minimal silver tassels concentrated near the bun and behind ears, keeping the look simple and cold. Hair details: airy stray hairs on forehead, two thin strands framing the cheeks, distinct individual strands, smooth falling ends. Attire: Moon-white cross-collar Ruqun dress with faint silver embroidered patterns, thin white gauze shawl, simple waistband, fabric with delicate silk sheen. Lighting and background: Soft cold-toned side lighting, beige rice paper ancient style background with thin Chinese borders, clean composition with negative space. Single-person half-body character reference sheet, front slightly angled, showing hair, accessories, and costume clearly, high-quality comic drama texture.\",\"status\":\"queued\"}}',1,NULL,'2026-10-07 18:19:29',101),(233,'AI图片生成',1,'com.twelvet.ai.server.controller.AiImageController.generate()','POST',1,'admin',NULL,'/ai/image/generate','223.88.55.136','','{\"params\":{},\"modelId\":38,\"prompt\":\"8K超高清，虚幻引擎5质感，PBR物理材质，精细发丝渲染，古风漫剧女主角人设图，东方古典审美，原创角色设计；\\n\\n女主身份：十八到二十二岁的清冷世家嫡女，像从旧书页里走出来的白月光；\\n\\n脸型五官：鹅蛋脸，眉眼细长，眼神安静疏离，鼻梁秀挺，唇色很淡，冷白皮，淡粉色眼妆，气质清雅克制；\\n\\n发型名称：流云垂鬟髻；\\n\\n发型结构：头顶做轻盈低髻，两侧垂鬟自然下落，后方长发半披，整体线条像流云一样柔和，不要厚重宫廷发髻；\\n\\n发饰搭配：白玉簪、细珍珠链、少量银色流苏，发饰集中在发髻上方和耳后，素雅清冷；\\n\\n发丝细节：额前少量空气感碎发，鬓边两缕细发贴近脸侧，发丝单根细节清晰，发尾顺滑垂落；\\n\\n服饰设定：月白色交领襦裙，浅银暗纹刺绣，轻薄白纱披帛，腰封简洁，衣料有细腻丝绸光泽；\\n\\n光线背景：柔和冷调侧光，米黄色宣纸古风背景，中式细边框，画面干净留白；\\n\\n画面为单人半身角色设定图，正面微侧角度，头发、发饰、服饰清晰可见，精致漫剧质感；\\n\\n无文字，无水印，无logo，不要现代首饰，不要浓妆，不要网红脸，不要夸张大眼\",\"size\":\"1024x1024\",\"genType\":\"text2img\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":111,\"model_id\":38,\"prompt\":\"8K ultra-high definition, Unreal Engine 5 render quality, PBR physical materials, intricate single-hair rendering. Character design sheet for a heroine in an ancient Eastern fantasy drama, reflecting classical Oriental aesthetics. The subject is a cool, restrained noble daughter aged 18-22, appearing like a \'white moonlight\' character from old pages. She has an oval face, slender eyebrows and eyes, a calm and distant gaze, a delicate nose bridge, and pale lips with cool fair skin and light pink eye makeup. Her hairstyle is the \'Flowing Cloud Hanging Buns\' (Liuyun Chui Huan Ji): a light low bun on top with natural hanging buns on the sides, half-open long hair at the back with soft, cloud-like lines. Accessories are minimal and elegant, including a white jade hairpin, thin pearl chains, and silver tassels concentrated above the bun and behind the ears. Detailed hair strands show airiness with sparse bangs and side locks. She wears a moon-white cross-collared Ru skirt with light silver hidden embroidery, a thin white gauze shawl, and a simple waistband, featuring fine silk luster. Lighting is soft, cold-toned side light against a beige xuan paper background with a thin Chinese-style border. Single-person half-body character sheet, front slightly angled view, clean composition with ample white space, exquisite comic drama texture.\",\"status\":\"queued\"}}',1,NULL,'2026-10-07 18:20:37',101),(234,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','223.88.55.136','','{\"params\":{},\"modelId\":37,\"prompt\":\"一、总提示词（可直接复制）\\n国漫仙侠动态漫画，电影级打斗分镜。云海之上，天宫废墟，两名仙家斗法：青衣剑仙 vs 玄袍雷尊。青衣剑仙脚踏七星，掐剑诀，身后展开太虚剑阵，万千飞剑悬浮，剑刃缠绕银蓝剑炁粒子，粒子如星尘拖尾，符文碎片环绕。玄袍雷尊掌托九霄雷球，紫金雷浆压缩，电弧如龙蛇游走，空气扭曲，空间涟漪扩散。两人同时出手，飞剑洪流与神雷洪流对撞，中心形成太极状能量核，金蓝与紫红粒子爆裂，体积光穿透乌云，冲击波掀翻瓦砾，火星、灵尘、碎玉、符箓纸片慢动作飞散。粒子特效：GPU粒子、体积粒子、等离子电弧、能量拖尾、辉光、镜头光晕、色散、景深、运动模糊、空间裂纹、冲击波环、灵气光点、五行灵光。镜头：低角度仰拍、广角、对角线构图、环绕运镜、急速推近、特写穿插、残影、速度线、集中线。风格：国漫赛璐璐+厚涂，电影级光影，HDR，8K，超精细，高帧率，史诗感。色彩：青蓝银白 vs 紫金赤红，暗云背景，高对比。\\n\\n二、分镜提示词（按镜头生成更稳）\\n远景对峙：云海天宫废墟，青衣剑仙与玄袍雷尊隔空对峙，灵气粒子从地面上升，体积雾，低角度仰拍，广角，史诗构图。\\n\\n剑仙结印：特写青衣剑仙掐诀，瞳孔亮起，银蓝符文在指尖旋转，星尘粒子汇聚，剑炁拖尾，景深，辉光。\\n\\n雷尊聚雷：特写玄袍雷尊握雷，紫金雷浆从指缝溢出，电弧跳跃，空间涟漪，空气扭曲，等离子粒子。\\n\\n万剑 vs 雷龙：中景，万千飞剑如暴雨 vs 紫金雷龙咆哮，粒子拖尾拉长，运动模糊，速度线，残影。\\n\\n能量对撞：碰撞特写，太极状能量核，金蓝与紫红粒子爆裂，冲击波环，镜头光晕，色散，体积光。\\n\\n环绕冲击：环绕镜头，冲击波扩散，建筑碎片慢动作飞散，符箓纸片、玉屑、火星、灵尘飘舞。\\n\\n近身斗法：剑指对掌，空间裂纹，符文碎片炸开，电弧与剑炁交错，高速摄影，粒子湍流。\\n\\n大招法相：法相天地，太虚剑阵与九霄神雷同时降临，粒子海，天地变色，能量余烬如雪，史诗感。\",\"genType\":\"text2vid\",\"width\":1280,\"height\":720,\"numFrames\":441,\"frameRate\":30,\"negativePrompt\":\"低清，模糊，崩坏，多手多指，五官扭曲，塑料感，廉价3D，粒子像贴图，无光影，过曝，死黑，静态，PPT，帧重复，水印，文字，logo，画面脏乱，粒子糊成一片。\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":101,\"model_id\":37,\"prompt\":\"国漫仙侠动态漫画，电影级打斗分镜。场景设定在云海之上的天宫废墟，背景为暗云笼罩的高对比环境。画面主体为两名仙家激烈斗法：左侧青衣剑仙脚踏七星，身缠银蓝剑炁粒子，身后展开太虚剑阵，万千飞剑悬浮并伴随星尘拖尾；右侧玄袍雷尊掌心托举紫金雷球，压缩的雷浆中电弧如龙蛇游走，空间产生可见涟漪。两人同时出手，飞剑洪流与神雷洪流剧烈对撞，中心形成太极状能量核，金蓝与紫红粒子爆裂，体积光穿透乌云，冲击波掀翻瓦砾，火星、灵尘、碎玉及符箓纸片在慢动作中飞散。视觉风格融合国漫赛璐璐与厚涂技法，运用广角低角度仰拍与对角线构图，结合环绕运镜与急速推近，营造史诗感。色彩以青蓝银白对抗紫金赤红，强调高对比度与HDR质感，细节包含GPU粒子、等离子电弧、辉光、镜头光晕、色散及空间裂纹等特效，8K超精细画质，高帧率动态表现。\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-10-07 18:29:03',101),(235,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','223.88.55.136','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-10-07/1791373358617.png\"}',1,NULL,'2026-10-07 19:42:41',101),(236,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','223.88.55.136','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-10-07/1791373363440.png\"}',1,NULL,'2026-10-07 19:42:44',101),(237,'单文件上传',6,'com.twelvet.dfs.server.controller.DFSController.commonUpload()','POST',1,'admin',NULL,'/dfs/commonUpload','223.88.55.136','','FILE','{\"code\":200,\"msg\":\"上传成功\",\"data\":\"/2026-10-07/1791373367526.jpeg\"}',1,NULL,'2026-10-07 19:42:47',101),(238,'AI视频生成',1,'com.twelvet.ai.server.controller.AiVideoController.generate()','POST',1,'admin',NULL,'/ai/video/generate','223.88.55.136','','{\"params\":{},\"modelId\":37,\"prompt\":\"电影级仙侠法术斗法，严格遵守《诛仙》法术体系，不使用法宝，纯法术对决。参考《雾山五行》水墨张力与《爱死机》分镜节奏。严格锁定【人物卡A】与【人物卡B】的人物设定，不换脸、不换服装、不改变角色特征。\\n场景：青云山巅，云海翻涌，残破石台，古松被气压压弯，空气中灵气凝聚成可见的微小光粒缓慢上升。\\n打斗逻辑：法术碰撞时先改变环境，再产生能量，最后爆开粒子。冲击波先到，声音后到，粒子最后炸开。\\n粒子分三层：底层体积雾（慢、重、有厚度、被气压推动），中层能量流（有方向、有湍流、有惯性、有颜色渐变），上层实体碎屑（冰晶、碎石、焦屑、金粉、符箓碎纸，有重力、有碰撞、有二次弹射、有旋转反光）。\\n渲染：UE5 Lumen全局光照，路径追踪，体积散射，次表面散射，运动模糊物理正确，HDR，8K，电影级调色。暗部有细节，高光不爆。\\n色彩：低饱和暗部，高纯度能量色。不要彩虹色，不要五颜六色。\\n运镜：呼吸式运镜，急推骤停，环绕后拉，极远景切微距。快门角度180°，物理运动模糊。\",\"genType\":\"multi2vid\",\"inputImages\":\"[\\\"https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-10-07/1791373363440.png\\\",\\\"https://your-bucket.oss-cn-beijing.aliyuncs.com//2026-10-07/1791373367526.jpeg\\\"]\",\"width\":1280,\"height\":720,\"numFrames\":441,\"frameRate\":30,\"negativePrompt\":\"对波，光柱，彩虹色，速度线，集中线，残影，廉价发光，塑料感，粒子像贴图，喊招式，PPT，脸崩，换装，水印，文字，logo，beam clash, light pillar, speed lines, cheap glow, particle stickers, plastic 3D, inconsistent character。\"}','{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"task_id\":102,\"model_id\":37,\"prompt\":\"电影级仙侠法术斗法场景，青云山巅云海翻涌，古松受气压压弯。人物卡A与人物卡B进行纯法术对决，无法宝。法术碰撞先改变环境再产生能量，最后爆开粒子。粒子分三层：底层体积雾厚重、被气压推动，中层能量流有方向、湍流、颜色渐变，上层实体碎屑包括冰晶、碎石、焦屑、金粉、符箓碎纸。UE5 Lumen全局光照，路径追踪，体积散射，次表面散射，HDR 8K电影级调色。低饱和暗部，高纯度能量色。运镜为呼吸式急推骤停，环绕后拉，极远景切微距，物理运动模糊，细节丰富，光影震撼。\",\"status\":\"queued\",\"progress\":0}}',1,NULL,'2026-10-07 19:43:31',101),(239,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":1,\"action\":\"remove_product\",\"note\":\"图片与实际不符，已核实\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 09:22:47',104),(240,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":2,\"action\":\"remove_product\",\"note\":\"尝试下架已售商品\"}',NULL,0,'该商品存在进行中订单或已售，请走纠纷处理流程','2026-10-30 09:23:14',104),(241,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":3,\"action\":\"remove_feed\",\"note\":\"确认违规\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 09:23:28',104),(242,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":5,\"action\":\"ban_user\",\"note\":\"多次骚扰他人\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 09:24:43',104),(243,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":6,\"action\":\"ban_user\",\"note\":\"尝试封运营\"}',NULL,0,'运营账号不可通过举报处置停用','2026-10-30 09:25:27',104),(244,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":6,\"action\":\"warn\",\"note\":\"警告测试\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 09:27:32',104),(245,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":6,\"action\":\"warn\",\"note\":\"警告测试\"}',NULL,0,'该举报已被处理','2026-10-30 09:27:32',104),(246,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":9,\"action\":\"remove_product\",\"note\":\"图片与实际不符，已核实\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 11:35:22',104),(247,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":10,\"action\":\"remove_product\",\"note\":\"核实下架\"}',NULL,0,'该商品存在进行中订单或已售，请走纠纷处理流程','2026-10-30 11:35:31',104),(248,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":11,\"action\":\"ban_user\",\"note\":\"封号\"}','{\"code\":200,\"msg\":\"已处理\"}',1,NULL,'2026-10-30 11:35:46',104),(249,'举报管理',2,'com.twelvet.trade.server.controller.AdminMarketplaceController.handleReport()','POST',1,'ops_demo',NULL,'/admin/marketplace/report/handle','127.0.0.1','','{\"reportId\":13,\"action\":\"ban_user\",\"note\":\"封运营\"}',NULL,0,'运营账号不可通过举报处置停用','2026-10-30 11:36:02',104);
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','董事长',1,'0','admin','2018-03-16 11:33:00','admin','2020-12-31 16:49:55','董事长'),(2,'se','项目经理',2,'0','admin','2018-03-16 11:33:00','admin','2020-10-22 15:19:10','项目经理'),(3,'hr','人力资源',3,'0','admin','2018-03-16 11:33:00','admin','2020-10-22 15:19:18','人力资源'),(4,'staff','普通员工',4,'0','admin','2018-03-16 11:33:00','admin','2020-10-22 15:19:30','普通员工');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `status` tinyint(1) NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'管理员','admin',1,'1',0,'0','admin','2020-11-19 14:42:34','admin','2021-01-11 22:40:02','管理员'),(2,'普通角色','common',2,'4',0,'0','admin','2020-11-19 14:42:34','admin','2026-06-11 14:26:31','普通角色'),(3,'运营人员','ops',1,'1',0,'0','admin','2026-06-09 14:05:25','admin','2026-06-11 14:28:51','运营管理角色(二手交易)'),(4,'客服人员','cs',2,'1',0,'0','admin','2026-06-09 14:05:25','',NULL,'客服角色(二手交易纠纷)');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
INSERT INTO `sys_role_dept` VALUES (2,103),(4,103),(4,104),(4,105),(4,106),(4,107);
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,1),(1,2),(1,3),(1,4),(1,100),(1,101),(1,102),(1,103),(1,104),(1,105),(1,107),(1,110),(1,500),(1,501),(1,1001),(1,1002),(1,1003),(1,1004),(1,1005),(1,1006),(1,1007),(1,1008),(1,1009),(1,1010),(1,1011),(1,1012),(1,1013),(1,1014),(1,1015),(1,1016),(1,1017),(1,1018),(1,1019),(1,1020),(1,1021),(1,1022),(1,1023),(1,1024),(1,1025),(1,1026),(1,1027),(1,1028),(1,1029),(1,1030),(1,1036),(1,1037),(1,1038),(1,1039),(1,1040),(1,1041),(1,1042),(1,1043),(1,1044),(1,1045),(1,1051),(1,1052),(1,1053),(1,1054),(1,1061),(1,1062),(1,1063),(1,1064),(1,1071),(1,1072),(1,1073),(1,1074),(1,1075),(1,1076),(1,1077),(1,1078),(1,1080),(1,1083),(1,1084),(1,1092),(1,1093),(1,1094),(1,1095),(1,1096),(1,1097),(1,1098),(1,1100),(1,1101),(1,1102),(1,1103),(1,1104),(1,1105),(1,1106),(1,1108),(1,1109),(1,1110),(1,1111),(1,1112),(1,1114),(1,1115),(1,1116),(1,1117),(1,1118),(1,1119),(1,1120),(1,1121),(1,1122),(1,1123),(1,1124),(1,1160),(1,1161),(1,1162),(1,2000),(1,2001),(1,2002),(1,2003),(1,2004),(1,2005),(1,2006),(1,2007),(1,2008),(1,2009),(1,2010),(1,2011),(1,2012),(1,2013),(1,2100),(1,2101),(1,2102),(1,2103),(1,2104),(1,2105),(1,2106),(1,2110),(1,2111),(1,2112),(1,2113),(1,2114),(1,2115),(1,2116),(1,2117),(1,2118),(1,2119),(1,2120),(1,2121),(1,2122),(1,2123),(1,2124),(1,2125),(1,2126),(1,2127),(1,2128),(1,2200),(1,2201),(1,2202),(1,2300),(1,2301),(1,2302),(1,2303),(1,2304),(1,2305),(1,2310),(1,2311),(1,2312),(1,2320),(1,2321),(1,2322),(1,2323),(1,2324),(1,2325),(1,2326),(1,2401),(1,2402),(1,2403),(1,2500),(1,2501),(1,2502),(1,2510),(1,2511),(1,2512),(1,2513),(1,2520),(1,2521),(1,2522),(1,2600),(1,2601),(1,2602),(1,2603),(1,2610),(1,2611),(1,2612),(1,2613),(1,2700),(1,2701),(1,2702),(1,2703),(1,2704),(1,2705),(1,2706),(1,2707),(1,2710),(1,2711),(1,2712),(1,2713),(1,2720),(1,2721),(1,2722),(1,2723),(1,2724),(1,2725),(1,2726),(1,2727),(1,2728),(1,2729),(1,2730),(1,2734),(1,2744),(1,2745),(1,2746),(1,2747),(1,2748),(1,2749),(1,2750),(2,1161),(2,1162),(2,2000),(2,2001),(2,2002),(2,2003),(2,2004),(2,2005),(2,2006),(2,2007),(2,2008),(2,2009),(2,2010),(2,2011),(2,2012),(2,2013),(2,2300),(2,2301),(2,2302),(2,2303),(2,2304),(2,2305),(2,2320),(2,2321),(2,2322),(2,2323),(2,2324),(2,2325),(2,2326),(2,2401),(2,2403),(2,2700),(2,2701),(2,2702),(2,2703),(2,2704),(2,2705),(2,2706),(2,2707),(2,2720),(2,2721),(2,2722),(2,2723),(2,2724),(2,2725),(2,2726),(2,2727),(2,2728),(2,2745),(2,2751),(3,2100),(3,2101),(3,2102),(3,2103),(3,2104),(3,2105),(3,2106),(3,2107),(3,2310),(3,2311),(3,2312),(3,2401),(3,2403),(3,2710),(3,2711),(3,2712),(3,2713),(3,2729),(3,2730),(3,2734),(3,2744),(3,2746),(3,2747),(3,2748),(3,2749),(3,2750),(4,2110),(4,2111),(4,2401),(4,2403);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_tenant`
--

DROP TABLE IF EXISTS `sys_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'tenant code',
  `tenant_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'tenant name',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '0=on 1=off',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `plan_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'free' COMMENT '套餐编码：free/basic/pro',
  `expire_at` datetime DEFAULT NULL COMMENT '套餐到期时间，NULL=永不过期',
  `quota_product_max` int NOT NULL DEFAULT '100' COMMENT '商品数量上限，0=不限',
  `quota_ai_call_day` int NOT NULL DEFAULT '0' COMMENT '每日AI调用上限;0=不限',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_tenant_plan` (`plan_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_tenant`
--

LOCK TABLES `sys_tenant` WRITE;
/*!40000 ALTER TABLE `sys_tenant` DISABLE KEYS */;
INSERT INTO `sys_tenant` VALUES (1,'default','default-community','0','2026-08-27 19:22:54','pro','2028-09-27 17:09:55',0,0),(2,'thu','thu-community','0','2026-08-27 19:22:54','free','2027-10-27 23:59:59',100,50);
/*!40000 ALTER TABLE `sys_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_tenant_renew_order`
--

DROP TABLE IF EXISTS `sys_tenant_renew_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant_renew_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL COMMENT '租户编码',
  `plan_code` varchar(32) NOT NULL COMMENT '目标套餐 free/basic/pro',
  `months` int NOT NULL COMMENT '续费月数',
  `amount` decimal(10,2) NOT NULL COMMENT '应付金额(元)',
  `out_trade_no` varchar(64) NOT NULL COMMENT '商户支付单号(=trade_pay.out_trade_no)',
  `channel` varchar(20) NOT NULL DEFAULT 'alipay' COMMENT 'alipay/wechat',
  `status` varchar(20) NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/paid(paid=已支付且已续费)',
  `paid_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_tenant_code` (`tenant_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户套餐续费支付单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_tenant_renew_order`
--

LOCK TABLES `sys_tenant_renew_order` WRITE;
/*!40000 ALTER TABLE `sys_tenant_renew_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_tenant_renew_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `username` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '手机号码',
  `sex` tinyint(1) DEFAULT NULL COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码',
  `status` tinyint(1) DEFAULT NULL COMMENT '帐号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登陆IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登陆时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  `global_user_id` bigint DEFAULT NULL COMMENT '全局用户ID（跨租户同人锚点 = 首个身份的 user_id）',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `uk_sys_user_tenant_username` (`tenant_id`,`username`),
  UNIQUE KEY `uk_sys_user_tenant_phone` ((if(((`del_flag` <=> _utf8mb4'0') and (`phonenumber` <> _utf8mb4'')),`phonenumber`,NULL)),`tenant_id`),
  KEY `idx_sys_user_tenant` (`tenant_id`),
  KEY `idx_sys_user_global` (`global_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=128 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,101,'admin','平台管理员','00','admin@weiwei.com','13800000000',0,'/avatar.jpg','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','127.0.0.1','2018-03-16 11:33:00','admin','2021-02-11 11:33:00','admin','2026-06-05 17:10:13','管理员',1,1),(101,NULL,'user_demo','买家小美','02','buyer@weiwei.com','13800001001',0,'https://api.dicebear.com/7.x/avataaars/svg?seed=buyer','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','','2026-06-08 11:02:11','','2026-06-08 11:02:11','thuadmin','2026-08-29 07:01:28','Demo 普通用户(可买可卖)',1,101),(102,NULL,'seller_demo','卖家老张','02','seller@weiwei.com','13800001002',1,'https://api.dicebear.com/7.x/avataaars/svg?seed=seller','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','','2026-06-08 11:02:11','','2026-06-08 11:02:11','','2026-10-30 10:55:23','Demo 普通用户(主要在卖数码)',1,102),(103,104,'ops_demo','运营小张','00','ops@weiwei.com','13800001003',0,'https://api.dicebear.com/7.x/avataaars/svg?seed=ops','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','','2026-06-08 11:02:11','','2026-06-08 11:02:11','',NULL,'Demo运营',1,103),(104,110,'cs_demo','客服小李','01','cs@weiwei.com','13800001004',1,'https://api.dicebear.com/7.x/avataaars/svg?seed=cs','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','','2026-06-08 11:02:11','','2026-06-08 11:02:11','',NULL,'Demo客服',1,104),(108,NULL,'demo','演示账号','02','demo@weiwei.com','13800001005',2,'/avatar.jpg','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','',NULL,'admin','2020-11-19 14:06:45','admin','2022-03-21 18:03:54',NULL,1,108),(123,NULL,'alice','Alice','02','alice@thu.edu','13800002001',NULL,'','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','',NULL,'admin','2026-08-29 07:04:29','',NULL,NULL,2,123),(125,NULL,'thuadmin','清华管理员','00','thuadmin@thu.edu','13800002002',NULL,'','$2a$10$MBq3PvaIX9Yghhu.mm45wO8IC4WKXAUST9LvneCsQ71k/mrOdN6SO',0,'0','',NULL,'admin','2026-08-29 07:04:29','',NULL,NULL,2,125);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1),(1,2);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1),(101,2),(102,2),(103,3),(104,4),(108,2),(123,2),(125,1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_bargain`
--

DROP TABLE IF EXISTS `trade_bargain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_bargain` (
  `bargain_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `round` tinyint(1) DEFAULT '1',
  `current_price` decimal(10,2) NOT NULL,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending/agreed/rejected/expired',
  `exclusive_order_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`bargain_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_buyer_id` (`buyer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='议价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_bargain`
--

LOCK TABLES `trade_bargain` WRITE;
/*!40000 ALTER TABLE `trade_bargain` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_bargain` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_dispute`
--

DROP TABLE IF EXISTS `trade_dispute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_dispute` (
  `dispute_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `refund_id` bigint DEFAULT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'refund/return/no_received/fake/other',
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending/in_progress/closed',
  `priority` varchar(10) DEFAULT 'normal' COMMENT 'high/mid/normal',
  `claim_cs_id` bigint DEFAULT NULL,
  `claim_time` datetime DEFAULT NULL,
  `judge_decision` varchar(20) DEFAULT NULL COMMENT 'buyer/seller/partial',
  `judge_amount` decimal(10,2) DEFAULT NULL,
  `judge_reason` varchar(1000) DEFAULT NULL,
  `judge_at` datetime DEFAULT NULL,
  `judge_cs_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`dispute_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='纠纷工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_dispute`
--

LOCK TABLES `trade_dispute` WRITE;
/*!40000 ALTER TABLE `trade_dispute` DISABLE KEYS */;
INSERT INTO `trade_dispute` VALUES (1,2,NULL,101,102,'no_received',1511.00,'in_progress','mid',1,'2026-08-10 10:05:04',NULL,NULL,NULL,NULL,NULL,'2026-06-08 10:02:11','2026-08-10 10:05:04');
/*!40000 ALTER TABLE `trade_dispute` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_logistics`
--

DROP TABLE IF EXISTS `trade_logistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_logistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `company` varchar(30) DEFAULT NULL,
  `no` varchar(50) DEFAULT NULL,
  `latest_node` varchar(200) DEFAULT NULL,
  `trace_json` text,
  `cache_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_logistics_tenant` (`tenant_id`,`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_logistics`
--

LOCK TABLES `trade_logistics` WRITE;
/*!40000 ALTER TABLE `trade_logistics` DISABLE KEYS */;
INSERT INTO `trade_logistics` VALUES (1,2,'顺丰速运','SF1234567890','已到达深圳市','[{\"node\":\"已揽收\",\"time\":\"1天前\"},{\"node\":\"已到达深圳市\",\"time\":\"2小时前\"}]',NULL,1),(2,29,'SF','SF10001','已揽件','[{\"node\":\"已揽件\",\"time\":\"刚刚\"}]','2026-10-30 18:32:39',1),(3,31,'SF','SF999','已揽件','[{\"node\":\"已揽件\",\"time\":\"刚刚\"}]','2026-11-03 16:36:40',1);
/*!40000 ALTER TABLE `trade_logistics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_order`
--

DROP TABLE IF EXISTS `trade_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `product_snapshot` text COMMENT '下单时商品快照JSON',
  `bargain_id` bigint DEFAULT NULL,
  `delivery_type` varchar(20) DEFAULT 'express' COMMENT 'express/self_pickup',
  `address_snapshot` text COMMENT '地址JSON',
  `buyer_remark` varchar(200) DEFAULT NULL,
  `sku_price` decimal(10,2) NOT NULL,
  `freight` decimal(10,2) DEFAULT '0.00',
  `total_amount` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/unshipped/shipped/done/refunding/refunded/closed',
  `buyer_review_id` bigint DEFAULT NULL,
  `seller_review_id` bigint DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `shipped_at` datetime DEFAULT NULL,
  `done_at` datetime DEFAULT NULL,
  `auto_confirm_at` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_order_tenant` (`tenant_id`,`create_time`),
  KEY `idx_status_expire` (`status`,`expire_at`),
  KEY `idx_status_autoconfirm` (`status`,`auto_confirm_at`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_order`
--

LOCK TABLES `trade_order` WRITE;
/*!40000 ALTER TABLE `trade_order` DISABLE KEYS */;
INSERT INTO `trade_order` VALUES (1,'NO20260601001',101,102,1,'{\"title\":\"iPhone 14 Pro 256G 暗紫色 99新\",\"price\":5999,\"cover\":\"https://picsum.photos/seed/p1/600/600\"}',NULL,'express','{\"receiver\":\"阿花\",\"mobile\":\"13800001001\",\"province\":\"广东省\",\"city\":\"深圳市\",\"detail\":\"科技园南区A栋12楼\"}',NULL,5999.00,12.00,6011.00,'unshipped',NULL,NULL,NULL,'2026-06-06 11:02:11',NULL,NULL,NULL,'','2026-06-06 11:02:11','','2026-06-08 11:02:11',NULL,1),(2,'NO20260602003',101,102,4,'{\"title\":\"任天堂Switch OLED 红蓝 95新\",\"price\":1499,\"cover\":\"https://picsum.photos/seed/p4/600/600\"}',NULL,'express','{\"receiver\":\"阿花\",\"mobile\":\"13800001001\",\"province\":\"广东省\",\"city\":\"深圳市\",\"detail\":\"科技园南区A栋12楼\"}',NULL,1499.00,12.00,1511.00,'shipped',NULL,NULL,NULL,'2026-06-07 11:02:11','2026-06-08 11:02:11',NULL,NULL,'','2026-06-07 11:02:11','','2026-06-08 11:02:11',NULL,1),(3,'NO20260603006',101,102,6,'{\"title\":\"Apple AirPods Pro 2 USB-C\",\"price\":1099,\"cover\":\"https://picsum.photos/seed/p6/600/600\"}',NULL,'express','{\"receiver\":\"阿花\",\"mobile\":\"13800001001\",\"province\":\"广东省\",\"city\":\"深圳市\",\"detail\":\"科技园南区A栋12楼\"}',NULL,1099.00,8.00,1107.00,'done',NULL,NULL,NULL,'2026-06-05 11:02:11','2026-06-06 11:02:11','2026-06-08 11:02:11',NULL,'','2026-06-05 11:02:11','','2026-06-08 11:02:11',NULL,1),(4,'NO1780895428855',101,102,6,'{\"title\":\"Apple AirPods Pro 2 USB-C\",\"price\":1099.00,\"cover\":\"https://picsum.photos/seed/p6/600/600\"}',NULL,'express',NULL,NULL,1099.00,8.00,1107.00,'unshipped',NULL,NULL,NULL,'2026-06-08 13:10:28',NULL,NULL,NULL,'','2026-06-08 13:10:28','','2026-06-08 13:10:28',NULL,1),(5,'NO1781070360909',101,102,4,'{\"title\":\"任天堂Switch OLED 红蓝 95新\",\"price\":1499.00,\"cover\":\"https://picsum.photos/seed/p4/600/600\"}',NULL,'express',NULL,NULL,1499.00,12.00,1511.00,'unshipped',NULL,NULL,NULL,'2026-06-10 13:46:00',NULL,NULL,NULL,'','2026-06-10 13:46:00','','2026-06-10 13:46:00',NULL,1),(6,'NO1781158971988',101,102,4,'{\"title\":\"任天堂Switch OLED 红蓝 95新\",\"price\":1499.00,\"cover\":\"https://picsum.photos/seed/p4/600/600\"}',NULL,'express',NULL,NULL,1499.00,12.00,1511.00,'unpaid',NULL,NULL,NULL,'2026-06-11 14:22:51',NULL,NULL,NULL,'','2026-06-11 14:22:51','','2026-06-11 14:22:51',NULL,1),(13,'TWT17865177840101ecf0',101,102,8,NULL,NULL,'express',NULL,NULL,1599.00,0.00,1599.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-08-12 14:56:24','',NULL,NULL,1),(14,'TWT17865201240001f675',101,102,8,NULL,NULL,'express',NULL,NULL,1599.00,0.00,1599.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-08-12 15:35:24','',NULL,NULL,1),(15,'TWT17865847470001efcf',101,102,18,NULL,NULL,'express',NULL,NULL,899.00,0.00,899.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-08-13 09:32:27','',NULL,NULL,1),(16,'TWT178658679200017031',101,102,9,NULL,NULL,'express',NULL,NULL,2499.00,0.00,2499.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-08-13 10:06:32','',NULL,NULL,1),(17,'TWT17865868320001cdf4',101,102,9,NULL,NULL,'express',NULL,NULL,2499.00,0.00,2499.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-08-13 10:07:12','',NULL,NULL,1),(18,'NOfc0056ad162c48d3903f9b5555fa181d',101,102,19,'{\"title\":\"罗技MX Master 3S 无线鼠标 石墨黑\",\"price\":499.00,\"cover\":\"https://picsum.photos/seed/p19/600/600\"}',NULL,'express',NULL,NULL,499.00,8.00,507.00,'unpaid',NULL,NULL,NULL,'2026-08-28 21:46:45',NULL,NULL,NULL,'','2026-08-28 21:46:45','','2026-08-28 21:46:45',NULL,1),(19,'NOfefb01a46ed74583b3a40e73d72f5044',101,102,19,'{\"title\":\"罗技MX Master 3S 无线鼠标 石墨黑\",\"price\":499.00,\"cover\":\"https://picsum.photos/seed/p19/600/600\"}',NULL,'express',NULL,NULL,499.00,8.00,507.00,'unpaid',NULL,NULL,NULL,'2026-08-29 14:19:28',NULL,NULL,NULL,'','2026-08-29 14:19:28','','2026-08-29 14:19:28',NULL,1),(20,'TWT1788267686012514ee',101,102,19,NULL,NULL,'express',NULL,NULL,499.00,0.00,499.00,'unpaid',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-09-01 21:01:26','',NULL,NULL,1),(21,'NO60dad9b993f94882bb11e17a89fe87fe',101,102,4,'{\"title\":\"任天堂Switch OLED 红蓝 95新\",\"price\":1499.00,\"cover\":\"https://picsum.photos/seed/p4/600/600\"}',NULL,'express',NULL,NULL,1499.00,12.00,1511.00,'unpaid',NULL,NULL,NULL,'2026-09-25 17:51:27',NULL,NULL,NULL,'','2026-09-25 17:51:27','','2026-09-25 17:51:27',NULL,1),(24,'NO-DEMO-CLOSED-01',101,102,4,'{\"title\":\"任天堂Switch OLED 红蓝 95新\",\"price\":1499.00,\"cover\":\"https://picsum.photos/seed/p4/600/600\"}',NULL,'express',NULL,NULL,1499.00,12.00,1511.00,'closed',NULL,NULL,'2026-10-26 17:02:24',NULL,NULL,NULL,NULL,'','2026-10-24 17:02:24','','2026-10-27 17:02:24',NULL,1),(25,'NO-DEMO-UNSHIP-01',101,102,7,'{\"title\":\"MacBook Air M2 星光色 8+256\",\"price\":6999.00,\"cover\":\"https://picsum.photos/seed/p7/600/600\"}',NULL,'express','{\"receiver\":\"阿花\",\"mobile\":\"13800001001\",\"province\":\"广东省\",\"city\":\"深圳市\",\"detail\":\"科技园南区A栋12楼\"}',NULL,6999.00,12.00,7011.00,'unshipped',NULL,NULL,NULL,'2026-10-25 17:02:24',NULL,NULL,NULL,'','2026-10-25 17:02:24','','2026-10-27 17:02:24',NULL,1),(26,'NO-DEMO-DONE-01',101,102,13,'{\"title\":\"iPad mini 6 紫色 64G WiFi版\",\"price\":2999.00,\"cover\":\"https://picsum.photos/seed/p13/600/600\"}',NULL,'express','{\"receiver\":\"阿花\",\"mobile\":\"13800001001\",\"province\":\"广东省\",\"city\":\"深圳市\",\"detail\":\"科技园南区A栋12楼\"}',NULL,2999.00,8.00,3007.00,'done',3,4,NULL,'2026-10-17 17:02:24','2026-10-18 17:02:24','2026-10-21 17:02:24',NULL,'','2026-10-17 17:02:24','','2026-10-27 17:02:24',NULL,1);
/*!40000 ALTER TABLE `trade_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_pay`
--

DROP TABLE IF EXISTS `trade_pay`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_pay` (
  `pay_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL COMMENT '商品订单ID;续费支付为NULL',
  `biz_type` varchar(16) NOT NULL DEFAULT 'trade' COMMENT '涓氬姟绫诲瀷:trade/runner/renew',
  `pay_user_id` bigint DEFAULT NULL COMMENT '支付单发起人（收银台创建者，mock-pay/查询归属校验来源）',
  `out_trade_no` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(20) DEFAULT NULL COMMENT 'wechat/alipay',
  `pay_amount` decimal(10,2) NOT NULL,
  `qr_code_url` varchar(500) DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `status` varchar(20) DEFAULT 'unpaid' COMMENT 'unpaid/paid/closed/failed',
  `paid_at` datetime DEFAULT NULL,
  `fail_reason` varchar(200) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  PRIMARY KEY (`pay_id`),
  UNIQUE KEY `idx_out_trade_no` (`out_trade_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_trade_pay_tenant` (`tenant_id`,`create_time`),
  KEY `idx_biz_order` (`biz_type`,`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_pay`
--

LOCK TABLES `trade_pay` WRITE;
/*!40000 ALTER TABLE `trade_pay` DISABLE KEYS */;
INSERT INTO `trade_pay` VALUES (1,1,'trade',NULL,NULL,'wechat',6011.00,NULL,NULL,'paid','2026-06-06 11:02:11',NULL,NULL,NULL,1),(2,2,'trade',NULL,NULL,'wechat',1511.00,NULL,NULL,'paid','2026-06-07 11:02:11',NULL,NULL,NULL,1),(3,3,'trade',NULL,NULL,'alipay',1107.00,NULL,NULL,'paid','2026-06-05 11:02:11',NULL,NULL,NULL,1),(4,6,'trade',NULL,'PAY202606111422526076','alipay',1511.00,NULL,'2026-06-11 14:52:52','unpaid',NULL,NULL,'2026-06-11 14:22:52','2026-06-11 14:22:52',1),(5,6,'trade',NULL,'PAY202606281603337070','alipay',1511.00,NULL,'2026-06-28 16:33:33','unpaid',NULL,NULL,'2026-06-28 16:03:33','2026-06-28 16:03:33',1),(6,18,'trade',NULL,'PAY202608282146597439','alipay',507.00,NULL,'2026-08-28 22:16:59','unpaid',NULL,NULL,'2026-08-28 21:46:59','2026-08-28 21:46:59',1),(15,19,'trade',NULL,'PAY202608291419288395','alipay',507.00,NULL,'2026-08-29 14:49:28','unpaid',NULL,NULL,'2026-08-29 14:19:28','2026-08-29 14:19:28',1),(16,19,'trade',NULL,'PAY202609012102113442','alipay',507.00,NULL,'2026-09-01 21:32:11','unpaid',NULL,NULL,'2026-09-01 21:02:11','2026-09-01 21:02:11',1),(17,4,'trade',NULL,'PAY202609052357100450','alipay',8.00,NULL,'2026-09-06 00:27:10','unpaid',NULL,NULL,'2026-09-05 23:57:10','2026-09-05 23:57:10',1),(18,17,'trade',NULL,'PAY202609060548293859','alipay',1.50,NULL,'2026-09-06 06:18:29','unpaid',NULL,NULL,'2026-09-06 05:48:29','2026-09-06 05:48:29',1),(19,17,'trade',NULL,'PAY202609060548298092','alipay',1.50,NULL,'2026-09-06 06:18:29','unpaid',NULL,NULL,'2026-09-06 05:48:29','2026-09-06 05:48:29',1),(20,17,'trade',NULL,'PAY202609060548292050','alipay',1.50,NULL,'2026-09-06 06:18:29','unpaid',NULL,NULL,'2026-09-06 05:48:29','2026-09-06 05:48:29',1),(21,17,'trade',NULL,'PAY202609060549508654','alipay',1.50,NULL,'2026-09-06 06:19:50','unpaid',NULL,NULL,'2026-09-06 05:49:50','2026-09-06 05:49:50',1),(23,17,'trade',NULL,'PAY202609060550543081','alipay',1.50,NULL,'2026-09-06 06:20:54','unpaid',NULL,NULL,'2026-09-06 05:50:54','2026-09-06 05:50:54',1),(24,17,'trade',NULL,'PAY202609060551076894','alipay',1.50,NULL,'2026-09-06 06:21:07','unpaid',NULL,NULL,'2026-09-06 05:51:07','2026-09-06 05:51:07',1),(25,17,'trade',NULL,'PAY202609060555525272','alipay',1.50,NULL,'2026-09-06 06:25:52','unpaid',NULL,NULL,'2026-09-06 05:55:52','2026-09-06 05:55:52',1),(26,18,'trade',NULL,'PAY202609060600574491','alipay',2.00,NULL,'2026-09-06 06:30:57','unpaid',NULL,NULL,'2026-09-06 06:00:57','2026-09-06 06:00:57',1),(27,18,'trade',NULL,'PAY202609060600571277','alipay',2.00,NULL,'2026-09-06 06:30:57','unpaid',NULL,NULL,'2026-09-06 06:00:57','2026-09-06 06:00:57',1),(28,20,'trade',NULL,'PAY202609120950200582','alipay',4.00,NULL,'2026-09-12 10:20:20','unpaid',NULL,NULL,'2026-09-12 09:50:20','2026-09-12 09:50:20',1),(29,21,'trade',NULL,NULL,'alipay',10.00,'https://mock.pay/weiwei/PAY202609251751271067','2026-09-12 10:30:38','unpaid','2026-09-12 10:00:47',NULL,'2026-09-12 10:00:38','2026-09-25 17:51:28',1),(30,21,'trade',NULL,'PAY202609251751271067','alipay',1511.00,'https://mock.pay/weiwei/PAY202609251751271067','2026-09-25 18:21:28','unpaid',NULL,NULL,'2026-09-25 17:51:28','2026-09-25 17:51:28',1),(31,26,'trade',101,'PAY-DEMO-26','alipay',3007.00,NULL,NULL,'paid','2026-10-17 17:02:24',NULL,'2026-10-17 17:02:24','2026-10-27 17:02:24',1),(32,25,'trade',101,'PAY-DEMO-25','alipay',7011.00,NULL,NULL,'paid','2026-10-25 17:02:24',NULL,'2026-10-25 17:02:24','2026-10-27 17:02:24',1);
/*!40000 ALTER TABLE `trade_pay` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_pay_flow`
--

DROP TABLE IF EXISTS `trade_pay_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_pay_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'pay/refund',
  `amount` decimal(10,2) NOT NULL,
  `channel` varchar(20) DEFAULT NULL,
  `third_trans_no` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`flow_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_pay_flow`
--

LOCK TABLES `trade_pay_flow` WRITE;
/*!40000 ALTER TABLE `trade_pay_flow` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_pay_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_pickup_code`
--

DROP TABLE IF EXISTS `trade_pickup_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_pickup_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `code` varchar(6) NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `used` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同城面交码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_pickup_code`
--

LOCK TABLES `trade_pickup_code` WRITE;
/*!40000 ALTER TABLE `trade_pickup_code` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_pickup_code` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_reconcile`
--

DROP TABLE IF EXISTS `trade_reconcile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_reconcile` (
  `date` date NOT NULL,
  `channel` varchar(20) NOT NULL,
  `biz_count` int DEFAULT '0',
  `biz_amount` decimal(15,2) DEFAULT '0.00',
  `third_count` int DEFAULT '0',
  `third_amount` decimal(15,2) DEFAULT '0.00',
  `mismatch_json` text,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`date`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_reconcile`
--

LOCK TABLES `trade_reconcile` WRITE;
/*!40000 ALTER TABLE `trade_reconcile` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_reconcile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_refund`
--

DROP TABLE IF EXISTS `trade_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_refund` (
  `refund_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'refund_only/return_refund',
  `reason_code` varchar(50) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `description` text,
  `evidence_images` text,
  `status` varchar(20) NOT NULL DEFAULT 'pending_seller' COMMENT 'pending_seller/seller_agreed/buyer_returned/refunding/refund_success/seller_rejected/mediation/closed',
  `return_company` varchar(30) DEFAULT NULL,
  `return_no` varchar(50) DEFAULT NULL,
  `return_address_snapshot` text,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `prev_order_status` varchar(20) DEFAULT NULL COMMENT '申请退款前的订单状态(unshipped/shipped/done/paid)，退款关闭时用于恢复',
  `active_flag` tinyint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'pending_seller',_utf8mb4'seller_agreed',_utf8mb4'buyer_returned',_utf8mb4'refunding',_utf8mb4'mediation')) then 1 else NULL end)) STORED COMMENT '进行中标记(终态为NULL)',
  PRIMARY KEY (`refund_id`),
  UNIQUE KEY `uk_order_active` (`order_id`,`active_flag`),
  KEY `idx_status` (`status`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_refund`
--

LOCK TABLES `trade_refund` WRITE;
/*!40000 ALTER TABLE `trade_refund` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_refund` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_refund_log`
--

DROP TABLE IF EXISTS `trade_refund_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_refund_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `refund_id` bigint NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `content` text COMMENT 'JSON',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_refund_id` (`refund_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款协商日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_refund_log`
--

LOCK TABLES `trade_refund_log` WRITE;
/*!40000 ALTER TABLE `trade_refund_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_refund_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_review`
--

DROP TABLE IF EXISTS `trade_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_review` (
  `review_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `role` varchar(10) NOT NULL COMMENT 'buyer/seller',
  `reviewer_id` bigint NOT NULL,
  `reviewee_id` bigint NOT NULL,
  `overall_score` tinyint(1) NOT NULL COMMENT '1-5',
  `content` varchar(500) DEFAULT NULL,
  `images` text,
  `tags` text COMMENT 'JSON',
  `anonymous` tinyint(1) DEFAULT '0',
  `supplement` varchar(200) DEFAULT NULL,
  `supplement_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID(sys_tenant.id)',
  `product_id` bigint DEFAULT NULL COMMENT '商品ID(冗余字段，商品页评价查询用)',
  `status` varchar(20) NOT NULL DEFAULT 'normal' COMMENT 'normal/hidden(运营隐藏)',
  PRIMARY KEY (`review_id`),
  UNIQUE KEY `uk_order_role` (`tenant_id`,`order_id`,`role`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_reviewee_id` (`reviewee_id`),
  KEY `idx_reviewee_tenant` (`tenant_id`,`reviewee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_review`
--

LOCK TABLES `trade_review` WRITE;
/*!40000 ALTER TABLE `trade_review` DISABLE KEYS */;
INSERT INTO `trade_review` VALUES (1,3,'buyer',101,102,5,'AirPods跟新的一样,包装完好,发货很快!好评!',NULL,NULL,0,NULL,NULL,'2026-06-07 11:02:11',1,6,'normal'),(2,3,'seller',102,101,5,'好买家,付款爽快,沟通礼貌。',NULL,NULL,0,NULL,NULL,'2026-06-07 11:02:11',1,6,'normal'),(3,26,'buyer',101,102,5,'东西和描述一致，卖家发货很快，还送了小礼物，好评！',NULL,'[\"描述相符\",\"发货快\",\"态度好\"]',0,NULL,NULL,'2026-10-21 17:02:24',1,13,'normal'),(4,26,'seller',102,101,5,'爽快买家，付款及时，沟通顺畅，欢迎再来。',NULL,'[\"付款快\",\"好沟通\"]',0,NULL,NULL,'2026-10-21 17:02:24',1,13,'normal');
/*!40000 ALTER TABLE `trade_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_withdraw`
--

DROP TABLE IF EXISTS `trade_withdraw`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_withdraw` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `withdraw_no` varchar(64) NOT NULL COMMENT '对外提现单号 W+uuid（W+32 位 = 33 字符）',
  `account_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/paid/rejected',
  `operator_id` bigint DEFAULT NULL COMMENT '审核/打款人',
  `voucher_url` varchar(255) DEFAULT NULL COMMENT '线下打款凭证',
  `fail_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`withdraw_id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_account_status` (`account_id`,`status`),
  KEY `idx_seller_tenant` (`seller_id`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_withdraw`
--

LOCK TABLES `trade_withdraw` WRITE;
/*!40000 ALTER TABLE `trade_withdraw` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_withdraw` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_withdraw_legacy_baseline`
--

DROP TABLE IF EXISTS `trade_withdraw_legacy_baseline`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_withdraw_legacy_baseline` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `bank_name` varchar(50) DEFAULT NULL,
  `bank_card_no` varchar(30) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending/passed/rejected',
  `reject_reason` varchar(500) DEFAULT NULL,
  `approve_cs_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`withdraw_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_withdraw_legacy_baseline`
--

LOCK TABLES `trade_withdraw_legacy_baseline` WRITE;
/*!40000 ALTER TABLE `trade_withdraw_legacy_baseline` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_withdraw_legacy_baseline` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_daily_report`
--

DROP TABLE IF EXISTS `twelve_daily_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_daily_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `report_date` date NOT NULL COMMENT '日报日期',
  `title` varchar(200) NOT NULL COMMENT '日报标题（LLM 生成）',
  `content` text NOT NULL COMMENT '日报正文（Markdown）',
  `stats_json` json NOT NULL COMMENT '原始聚合指标（供审计/重新生成）',
  `source` varchar(20) NOT NULL DEFAULT 'llm' COMMENT 'llm=AI生成, fallback=模板兜底',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report` (`tenant_id`,`report_date`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 社区日报';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_daily_report`
--

LOCK TABLES `twelve_daily_report` WRITE;
/*!40000 ALTER TABLE `twelve_daily_report` DISABLE KEYS */;
INSERT INTO `twelve_daily_report` VALUES (1,1,'2026-09-05','9月5日社区日报 | 今日风平浪静，邻居们都在忙啥呢？','## 一句话总览\n\n今天社区很安静，一切都在平稳运转中～\n\n## 二手好物\n\n今天没有新上架的闲置物品，看来大家都在整理自己的小窝。如果你家里有闲置想出手，欢迎挂到社区集市里！\n\n## 邻里互助\n\n今天没有新的跑腿完成单，不过社区累计已帮助居民完成 4 单，口碑一直在攒着呢～\n\n## 社区动态\n\n今天没有热帖，帖子区暂时安静。有什么想聊的、想分享的，欢迎大家发帖哦！','{\"hotFeeds\": [], \"newFeeds\": 0, \"doneAmount\": 0.0, \"doneOrders\": 0, \"renewCount\": 0, \"newProducts\": 0, \"topProducts\": [], \"runnerAvgRating\": 0.0, \"runnerDoneToday\": 0, \"runnerDoneTotal\": 4, \"runnerNewReviews\": 0}','llm','2026-09-06 10:32:42','2026-09-06 10:36:54'),(2,2,'2026-09-05','9月5日thu社区日报｜今天很安静','## 一句话总览\n\n9月5日的thu-community今天格外安静，没什么新动静，适合好好休息。\n\n## 二手好物\n\n今天没有新的闲置好物上架，有想出售的居民可以随时发帖，说不定很快就会被邻居们带走。\n\n## 邻里互助\n\n今天的跑腿服务也很安静，没有新的跑腿完成单，也没有新好评。有需要帮忙的邻居随时在群里喊一声哦。\n\n## 社区动态\n\n今天社区里没有新的热帖发布，如果你有什么想聊的、想分享的，欢迎随时来发帖交流！','{\"hotFeeds\": [], \"newFeeds\": 0, \"doneAmount\": 0.0, \"doneOrders\": 0, \"renewCount\": 0, \"newProducts\": 0, \"topProducts\": [], \"runnerAvgRating\": 0.0, \"runnerDoneToday\": 0, \"runnerDoneTotal\": 0, \"runnerNewReviews\": 0}','llm','2026-09-06 10:38:29',NULL);
/*!40000 ALTER TABLE `twelve_daily_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_item`
--

DROP TABLE IF EXISTS `twelve_exchange_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物品ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `item_no` varchar(32) NOT NULL COMMENT '物品编号 ESC+yyyyMMddHHmmss+6位随机',
  `owner_id` bigint NOT NULL COMMENT '物品主人',
  `title` varchar(100) NOT NULL COMMENT '物品标题',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面图',
  `images` varchar(1000) DEFAULT NULL COMMENT '图片（JSON数组）',
  `cat_l1` varchar(20) NOT NULL COMMENT '一级类目（复用 product_category_dict）',
  `cat_l2` varchar(20) DEFAULT NULL COMMENT '二级类目',
  `condition_level` varchar(20) NOT NULL COMMENT '成色 brand_new/99/95/90/80',
  `est_value` decimal(10,2) DEFAULT NULL COMMENT '主人自估参考价值（仅展示，不构成交易价格）',
  `want_cats` varchar(200) DEFAULT NULL COMMENT '期望换得的类目（JSON数组，可空=不限）',
  `want_desc` varchar(500) DEFAULT NULL COMMENT '期望换什么（自由文本）',
  `accept_any` tinyint NOT NULL DEFAULT '0' COMMENT '1接受任意洽谈 0仅限期望范围',
  `delivery_mode` varchar(20) NOT NULL DEFAULT 'face' COMMENT 'face面交/express快递/both',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `location` varchar(200) DEFAULT NULL COMMENT '面交区域描述',
  `description` text COMMENT '物品描述',
  `status` varchar(20) NOT NULL DEFAULT 'auditing' COMMENT 'draft/auditing/onswap/locked/swapped/offshelf/rejected',
  `reject_reason` varchar(200) DEFAULT NULL COMMENT '审核驳回原因',
  `view_count` int NOT NULL DEFAULT '0',
  `offer_count` int NOT NULL DEFAULT '0' COMMENT '收到的意向数',
  `swap_count` int NOT NULL DEFAULT '0' COMMENT '累计换出次数',
  `hot_score` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `publish_time` datetime DEFAULT NULL COMMENT '首次上架时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_no` (`tenant_id`,`item_no`),
  KEY `idx_owner` (`tenant_id`,`owner_id`),
  KEY `idx_status` (`tenant_id`,`status`),
  KEY `idx_cat` (`tenant_id`,`cat_l1`,`status`),
  FULLTEXT KEY `ft_exchange_search` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='换物物品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_item`
--

LOCK TABLES `twelve_exchange_item` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_item` DISABLE KEYS */;
INSERT INTO `twelve_exchange_item` VALUES (1,1,'ESC-DEMO-0001',101,'Cherry MX 机械键盘 87 键','https://picsum.photos/seed/e101/600/600','[\"https://picsum.photos/seed/e101/600/600\"]','digital','digital-accessory','95',380.00,'[\"digital\"]','想换无线鼠标或耳机',1,'face','深圳','南山区科技园','红轴，手感很好，带原装键帽。','onswap',NULL,0,1,0,6.2000,'2026-10-23 17:02:24',NULL,'2026-10-27 17:02:24','2026-10-27 17:02:24'),(2,1,'ESC-DEMO-0002',102,'捷安特 ATX 山地车 27.5 寸','https://picsum.photos/seed/e102/600/600','[\"https://picsum.photos/seed/e102/600/600\"]','sport','sport-bike','85',900.00,'[\"sport\",\"digital\"]','想换运动手表',0,'face','深圳','南山区科技园','骑了两年，刚做过保养，链条新换。','onswap',NULL,0,1,0,9.4000,'2026-10-24 17:02:24',NULL,'2026-10-27 17:02:24','2026-10-27 17:02:24'),(3,1,'ESC-DEMO-0003',102,'极米 Z6X 投影仪','https://picsum.photos/seed/e103/600/600','[\"https://picsum.photos/seed/e103/600/600\"]','home','home-appliance','90',1200.00,'[\"home\",\"digital\"]','想换空气炸锅 + 差价',1,'both','深圳','南山区科技园','亮度够用，附幕布，适合卧室。','auditing',NULL,0,0,0,0.0000,NULL,NULL,'2026-10-27 17:02:24','2026-10-27 17:02:24'),(4,1,'ESC-DEMO-0004',101,'摩飞空气炸锅 4L','https://picsum.photos/seed/e104/600/600','[\"https://picsum.photos/seed/e104/600/600\"]','home','home-kitchen','95',260.00,'[\"home\"]','想换书或小家电',1,'face','深圳','南山区科技园','用了不到 10 次，配件齐全。','offshelf',NULL,0,0,0,2.1000,'2026-10-07 17:02:24',NULL,'2026-10-27 17:02:24','2026-10-27 17:02:24');
/*!40000 ALTER TABLE `twelve_exchange_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_match_push`
--

DROP TABLE IF EXISTS `twelve_exchange_match_push`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_match_push` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `item_id` bigint NOT NULL COMMENT '物品ID',
  `pushed_user_ids` varchar(1000) NOT NULL DEFAULT '[]' COMMENT '已推送过的需求方 userId（JSON 数组，diff 去重）',
  `last_push_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item` (`tenant_id`,`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='换物匹配推送去重快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_match_push`
--

LOCK TABLES `twelve_exchange_match_push` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_match_push` DISABLE KEYS */;
/*!40000 ALTER TABLE `twelve_exchange_match_push` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_offer`
--

DROP TABLE IF EXISTS `twelve_exchange_offer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_offer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `item_id` bigint NOT NULL COMMENT '被申请的物品',
  `owner_id` bigint NOT NULL COMMENT '被申请人（物品主人，冗余）',
  `offeror_id` bigint NOT NULL COMMENT '发起人',
  `offer_item_id` bigint DEFAULT NULL COMMENT '发起人用来交换的库内物品ID',
  `offer_snapshot` varchar(1000) DEFAULT NULL COMMENT '发起人物品快照或快速描述（JSON，未选库内物品时必填）',
  `message` varchar(500) DEFAULT NULL COMMENT '交换留言',
  `diff_amount` decimal(10,2) DEFAULT NULL COMMENT '愿补差价（仅展示，MVP不走资金流）',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/rejected/expired/withdrawn/closed',
  `expire_time` datetime NOT NULL COMMENT '过期时间（默认72h）',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`tenant_id`,`item_id`,`status`),
  KEY `idx_offeror` (`tenant_id`,`offeror_id`,`status`),
  KEY `idx_owner` (`tenant_id`,`owner_id`,`status`),
  KEY `idx_expire` (`tenant_id`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='换物意向表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_offer`
--

LOCK TABLES `twelve_exchange_offer` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_offer` DISABLE KEYS */;
INSERT INTO `twelve_exchange_offer` VALUES (1,1,1,101,102,2,'{\"title\":\"捷安特 ATX 山地车 27.5 寸\",\"estValue\":900}','我这辆车市价 900，可以补你 300 差价换键盘，考虑下？',300.00,'pending','2026-10-30 17:02:24',NULL,'2026-10-27 15:02:24','2026-10-27 17:02:24'),(2,1,2,102,101,1,'{\"title\":\"Cherry MX 机械键盘 87 键\",\"estValue\":380}','用键盘换你的山地车可以吗？可以补差价。',-500.00,'rejected','2026-10-30 17:02:24','2026-10-26 17:02:24','2026-10-26 17:02:24','2026-10-27 17:02:24');
/*!40000 ALTER TABLE `twelve_exchange_offer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_order`
--

DROP TABLE IF EXISTS `twelve_exchange_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_no` varchar(32) NOT NULL COMMENT 'EXC+yyyyMMddHHmmss+6位随机',
  `offer_id` bigint NOT NULL COMMENT '来源意向',
  `item_a_id` bigint NOT NULL COMMENT 'A 的物品',
  `item_b_id` bigint NOT NULL COMMENT 'B 的物品',
  `owner_a_id` bigint NOT NULL COMMENT 'A 用户（物品主）',
  `owner_b_id` bigint NOT NULL COMMENT 'B 用户（发起人）',
  `delivery_mode` varchar(20) NOT NULL DEFAULT 'face' COMMENT 'face/express',
  `status` varchar(20) NOT NULL DEFAULT 'created' COMMENT 'created/delivering/pending_confirm/completed/cancelled/disputed',
  `a_delivered_time` datetime DEFAULT NULL COMMENT 'A 标记交付时间',
  `b_delivered_time` datetime DEFAULT NULL COMMENT 'B 标记交付时间',
  `a_confirmed_time` datetime DEFAULT NULL COMMENT 'A 确认收到时间',
  `b_confirmed_time` datetime DEFAULT NULL COMMENT 'B 确认收到时间',
  `a_track_no` varchar(50) DEFAULT NULL COMMENT 'A 快递单号',
  `b_track_no` varchar(50) DEFAULT NULL COMMENT 'B 快递单号',
  `cancel_reason` varchar(200) DEFAULT NULL,
  `dispute_reason` varchar(200) DEFAULT NULL,
  `dispute_result` varchar(200) DEFAULT NULL COMMENT '后台裁决结果',
  `complete_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`tenant_id`,`order_no`),
  KEY `idx_owner_a` (`tenant_id`,`owner_a_id`,`status`),
  KEY `idx_owner_b` (`tenant_id`,`owner_b_id`,`status`),
  KEY `idx_item_a` (`tenant_id`,`item_a_id`),
  KEY `idx_item_b` (`tenant_id`,`item_b_id`),
  KEY `idx_status` (`tenant_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交换订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_order`
--

LOCK TABLES `twelve_exchange_order` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `twelve_exchange_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_order_log`
--

DROP TABLE IF EXISTS `twelve_exchange_order_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_order_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL COMMENT '操作人（用户或后台管理员）',
  `action` varchar(30) NOT NULL COMMENT 'create/deliver/confirm/cancel/dispute/timeout/admin_force_complete/admin_force_cancel/review',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`tenant_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交换订单流转日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_order_log`
--

LOCK TABLES `twelve_exchange_order_log` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_order_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `twelve_exchange_order_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_pickup_code`
--

DROP TABLE IF EXISTS `twelve_exchange_pickup_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_pickup_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `side` char(1) NOT NULL COMMENT 'a/b',
  `code` varchar(6) NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `used` tinyint(1) DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_side` (`order_id`,`side`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='换物面交码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_pickup_code`
--

LOCK TABLES `twelve_exchange_pickup_code` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_pickup_code` DISABLE KEYS */;
/*!40000 ALTER TABLE `twelve_exchange_pickup_code` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_exchange_review`
--

DROP TABLE IF EXISTS `twelve_exchange_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_exchange_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `reviewer_id` bigint NOT NULL,
  `reviewee_id` bigint NOT NULL,
  `score` tinyint NOT NULL COMMENT '1-5星',
  `content` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_reviewer` (`tenant_id`,`order_id`,`reviewer_id`),
  KEY `idx_reviewee` (`tenant_id`,`reviewee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交换评价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_exchange_review`
--

LOCK TABLES `twelve_exchange_review` WRITE;
/*!40000 ALTER TABLE `twelve_exchange_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `twelve_exchange_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_runner_notify_log`
--

DROP TABLE IF EXISTS `twelve_runner_notify_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_runner_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL,
  `action` varchar(30) NOT NULL,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `content` varchar(500) DEFAULT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'im',
  `status` varchar(20) NOT NULL DEFAULT 'sent',
  `error_msg` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`tenant_id`,`task_id`),
  KEY `idx_receiver` (`tenant_id`,`receiver_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='互助状态流转通知日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_runner_notify_log`
--

LOCK TABLES `twelve_runner_notify_log` WRITE;
/*!40000 ALTER TABLE `twelve_runner_notify_log` DISABLE KEYS */;
INSERT INTO `twelve_runner_notify_log` VALUES (1,1,7,'assign',108,1,'【跑腿】您的任务「帮我取快递通知测试」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 00:14:52'),(2,1,7,'start',108,1,'【跑腿】任务「帮我取快递通知测试」跑腿人已开始配送','im','sent',NULL,'2026-09-06 00:14:52'),(3,1,7,'finish',108,1,'【跑腿】任务「帮我取快递通知测试」跑腿人已提交完成，请确认验收','im','sent',NULL,'2026-09-06 00:14:52'),(4,1,7,'confirm',1,108,'【跑腿】任务「帮我取快递通知测试」已完成确认，感谢您的跑腿服务','im','sent',NULL,'2026-09-06 00:14:52'),(5,1,8,'assign',1,108,'【跑腿】您的任务「取消流程通知测试」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 00:14:52'),(6,1,8,'cancel',108,1,'【跑腿】任务「取消流程通知测试」已被取消：不用了测试取消','im','sent',NULL,'2026-09-06 00:14:52'),(7,1,10,'assign',108,1,'【跑腿】您的任务「Review E2E task: buy coffee」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 00:23:47'),(8,1,10,'start',108,1,'【跑腿】任务「Review E2E task: buy coffee」跑腿人已开始配送','im','sent',NULL,'2026-09-06 00:23:47'),(9,1,10,'finish',108,1,'【跑腿】任务「Review E2E task: buy coffee」跑腿人已提交完成，请确认验收','im','sent',NULL,'2026-09-06 00:23:47'),(10,1,10,'confirm',1,108,'【跑腿】任务「Review E2E task: buy coffee」已完成确认，感谢您的跑腿服务','im','sent',NULL,'2026-09-06 00:23:47'),(11,1,10,'review',1,108,'【跑腿】任务「Review E2E task: buy coffee」获得 5星 好评，信用分已更新','im','sent',NULL,'2026-09-06 00:23:47'),(12,1,12,'assign',108,1,'【跑腿】您的任务「KWPAGE7X 分页验证-走完成流程」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 05:47:45'),(13,1,14,'assign',108,1,'【跑腿】您的任务「KWPAGE7X 分页验证-走完成流程」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 05:48:29'),(14,1,14,'start',108,1,'【跑腿】任务「KWPAGE7X 分页验证-走完成流程」跑腿人已开始配送','im','sent',NULL,'2026-09-06 05:48:29'),(15,1,14,'finish',108,1,'【跑腿】任务「KWPAGE7X 分页验证-走完成流程」跑腿人已提交完成，请确认验收','im','sent',NULL,'2026-09-06 05:48:29'),(16,1,14,'confirm',1,108,'【跑腿】任务「KWPAGE7X 分页验证-走完成流程」已完成确认，感谢您的跑腿服务','im','sent',NULL,'2026-09-06 05:48:29'),(17,1,15,'assign',108,1,'【跑腿】您的任务「KWPAGE7X 分页验证-取消流程」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 05:48:29'),(18,1,15,'cancel',1,108,'【跑腿】任务「KWPAGE7X 分页验证-取消流程」已被取消：seeds penalty','im','sent',NULL,'2026-09-06 05:48:29'),(19,1,16,'assign',108,1,'【跑腿】您的任务「KWPAGE7X 分页验证-已接单」已被抢单，跑腿人正在准备接单','im','sent',NULL,'2026-09-06 05:48:29');
/*!40000 ALTER TABLE `twelve_runner_notify_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_runner_order`
--

DROP TABLE IF EXISTS `twelve_runner_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_runner_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL,
  `runner_id` bigint NOT NULL COMMENT '接单人',
  `status` varchar(20) NOT NULL COMMENT 'accepted/running/finished/rejected/cancelled',
  `assign_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `reject_reason` varchar(200) DEFAULT NULL COMMENT '拒单原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`tenant_id`,`task_id`),
  KEY `idx_runner` (`tenant_id`,`runner_id`),
  KEY `idx_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接单记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_runner_order`
--

LOCK TABLES `twelve_runner_order` WRITE;
/*!40000 ALTER TABLE `twelve_runner_order` DISABLE KEYS */;
INSERT INTO `twelve_runner_order` VALUES (2,1,6,108,'accepted','2026-09-06 00:00:23',NULL,NULL,'2026-09-06 00:00:23'),(3,1,7,108,'accepted','2026-09-06 00:14:52',NULL,NULL,'2026-09-06 00:14:52'),(4,1,8,1,'accepted','2026-09-06 00:14:52',NULL,NULL,'2026-09-06 00:14:52'),(5,1,10,108,'accepted','2026-09-06 00:23:47',NULL,NULL,'2026-09-06 00:23:47'),(7,1,14,108,'accepted','2026-09-06 05:48:29',NULL,NULL,'2026-09-06 05:48:29'),(8,1,15,108,'accepted','2026-09-06 05:48:29',NULL,NULL,'2026-09-06 05:48:29'),(9,1,16,108,'accepted','2026-09-06 05:48:29',NULL,NULL,'2026-09-06 05:48:29');
/*!40000 ALTER TABLE `twelve_runner_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_runner_review`
--

DROP TABLE IF EXISTS `twelve_runner_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_runner_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联接单记录ID（twelve_runner_order.id）',
  `task_no` varchar(32) DEFAULT NULL COMMENT '任务编号（冗余，便于查询）',
  `publisher_id` bigint NOT NULL COMMENT '评价人（任务发布者）',
  `runner_id` bigint NOT NULL COMMENT '被评价人（接单跑腿员）',
  `rating` tinyint NOT NULL COMMENT '评分 1-5 星',
  `tags` varchar(200) DEFAULT NULL COMMENT '评价标签（逗号分隔，可选）',
  `content` varchar(500) DEFAULT NULL COMMENT '评语',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task` (`tenant_id`,`task_id`),
  KEY `idx_runner` (`tenant_id`,`runner_id`),
  KEY `idx_publisher` (`tenant_id`,`publisher_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='互助任务评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_runner_review`
--

LOCK TABLES `twelve_runner_review` WRITE;
/*!40000 ALTER TABLE `twelve_runner_review` DISABLE KEYS */;
INSERT INTO `twelve_runner_review` VALUES (1,1,10,5,'RUN20260906002347684560',1,108,5,NULL,'great job, fast delivery','2026-09-06 00:23:47');
/*!40000 ALTER TABLE `twelve_runner_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_runner_task`
--

DROP TABLE IF EXISTS `twelve_runner_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_runner_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID（多租户隔离）',
  `task_no` varchar(32) NOT NULL COMMENT '任务编号',
  `publisher_id` bigint NOT NULL COMMENT '下单人用户ID',
  `assignee_id` bigint DEFAULT NULL COMMENT '接单跑腿人用户ID',
  `task_type` tinyint NOT NULL COMMENT '任务类型:1代取快递 2代买东西 3代排队 4其他',
  `title` varchar(100) NOT NULL COMMENT '任务标题',
  `description` text COMMENT '详细描述',
  `pickup_addr` varchar(255) DEFAULT NULL COMMENT '取件/起点地址',
  `deliver_addr` varchar(255) DEFAULT NULL COMMENT '送达/终点地址',
  `pickup_lat` decimal(10,7) DEFAULT NULL COMMENT '起点纬度',
  `pickup_lng` decimal(10,7) DEFAULT NULL COMMENT '起点经度',
  `deliver_lat` decimal(10,7) DEFAULT NULL COMMENT '终点纬度',
  `deliver_lng` decimal(10,7) DEFAULT NULL COMMENT '终点经度',
  `is_paid` tinyint NOT NULL DEFAULT '1' COMMENT '1付费 0免费',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '报酬（元）',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/running/pending_confirm/completed/cancelled/disputed/refund',
  `expect_time` datetime DEFAULT NULL COMMENT '期望完成时间',
  `deadline` datetime NOT NULL COMMENT '接单截止时间',
  `assign_time` datetime DEFAULT NULL COMMENT '接单时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `cancel_reason` varchar(200) DEFAULT NULL COMMENT '取消原因',
  `refund_reason` varchar(200) DEFAULT NULL COMMENT '退款原因',
  `refund_status` varchar(20) DEFAULT NULL COMMENT '退款状态',
  `pay_order_id` bigint DEFAULT NULL COMMENT '关联支付单ID（trade_pay.pay_id）',
  `pay_out_trade_no` varchar(64) DEFAULT NULL COMMENT '关联支付单商户流水号（trade_pay.out_trade_no，退款用）',
  `pay_status` varchar(20) NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/paid/refunded（与 trade_pay.status 同步）',
  `images` varchar(500) DEFAULT NULL COMMENT '任务图片（JSON数组）',
  `finish_images` varchar(500) DEFAULT NULL COMMENT '完成凭证图片（JSON数组）',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`tenant_id`,`task_no`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_publisher` (`tenant_id`,`publisher_id`),
  KEY `idx_assignee` (`tenant_id`,`assignee_id`),
  KEY `idx_deadline` (`tenant_id`,`deadline`),
  KEY `idx_task_type` (`tenant_id`,`task_type`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='互助任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_runner_task`
--

LOCK TABLES `twelve_runner_task` WRITE;
/*!40000 ALTER TABLE `twelve_runner_task` DISABLE KEYS */;
INSERT INTO `twelve_runner_task` VALUES (3,1,'RUN20260905235710757745',101,NULL,1,'帮我取快递','菜鸟驿站大件',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'cancelled',NULL,'2026-09-07 18:00:00',NULL,NULL,NULL,'临时不需要了',NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-05 23:57:10','','2026-10-27 17:02:24',NULL),(4,1,'RUN20260905235710042640',101,NULL,2,'代买奶茶两杯','一杯美式不加糖、一杯拿铁去冰。','科技园南区茶百道','科技园南区A栋12楼',NULL,NULL,NULL,NULL,1,15.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-05 23:57:10','','2026-10-27 17:07:32',NULL),(5,1,'RUN20260905235805438305',101,NULL,1,'帮我取快递2','北门驿站',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'cancelled',NULL,'2026-09-07 18:00:00',NULL,NULL,NULL,'测试清理',NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-05 23:58:05','','2026-10-27 17:02:24',NULL),(6,1,'RUN20260906000023797988',101,108,1,'帮我修电脑','宿舍楼下自取',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'completed',NULL,'2026-09-07 18:00:00','2026-09-06 00:00:23','2026-09-06 00:00:23','2026-09-06 00:00:23',NULL,NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 00:00:23','','2026-10-27 17:02:24',NULL),(7,1,'RUN20260906001442233030',101,108,1,'帮我取快递通知测试','测试IM通知',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'completed',NULL,'2026-09-30 23:59:59','2026-09-06 00:14:52','2026-09-06 00:14:52','2026-09-06 00:14:52',NULL,NULL,NULL,NULL,NULL,'unpaid',NULL,'[]','1','2026-09-06 00:14:42','','2026-10-27 17:02:24',NULL),(8,1,'RUN20260906001452629671',108,102,1,'取消流程通知测试','x',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'cancelled',NULL,'2026-09-30 23:59:59','2026-09-06 00:14:52',NULL,NULL,'不用了测试取消',NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'108','2026-09-06 00:14:52','','2026-10-27 17:02:24',NULL),(9,1,'RUN20260906002342324735',101,NULL,2,'帮取快递（顺丰驿站）','一个小件，麻烦今天取一下送到公司前台。','科技园南区顺丰驿站','科技园南区A栋12楼',NULL,NULL,NULL,NULL,1,8.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-06 00:23:42','','2026-10-27 17:07:32',NULL),(10,1,'RUN20260906002347684560',101,108,2,'Review E2E task: buy coffee','e2e',NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'completed',NULL,'2026-09-06 02:23:47','2026-09-06 00:23:47','2026-09-06 00:23:47','2026-09-06 00:23:47',NULL,NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 00:23:47','','2026-10-27 17:02:24',NULL),(13,1,'RUN20260906054829556901',101,NULL,1,'帮带一份猪脚饭','食堂二楼，微辣，不要香菜。','科技园食堂二楼','科技园北区B栋8楼',NULL,NULL,NULL,NULL,1,12.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-06 05:48:29','','2026-10-27 17:07:32',NULL),(14,1,'RUN20260906054829349398',101,108,1,'KWPAGE7X 分页验证-走完成流程','KWPAGE7X 分页验证-走完成流程','A栋门口','C栋502',NULL,NULL,NULL,NULL,0,0.00,'completed',NULL,'2026-09-06 06:48:29','2026-09-06 05:48:30','2026-09-06 05:48:29','2026-09-06 05:48:29',NULL,NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 05:48:29','','2026-10-27 17:02:24',NULL),(15,1,'RUN20260906054829233598',101,108,1,'KWPAGE7X 分页验证-取消流程','KWPAGE7X 分页验证-取消流程','A栋门口','C栋502',NULL,NULL,NULL,NULL,0,0.00,'cancelled',NULL,'2026-09-06 06:48:29','2026-09-06 05:48:30',NULL,NULL,'seeds penalty',NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 05:48:29','','2026-10-27 17:02:24',NULL),(16,1,'RUN20260906054829166330',101,108,1,'KWPAGE7X 分页验证-已接单','KWPAGE7X 分页验证-已接单','A栋门口','C栋502',NULL,NULL,NULL,NULL,0,0.00,'accepted',NULL,'2026-09-06 06:48:29','2026-09-06 05:48:30',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 05:48:29','','2026-10-27 17:02:24',NULL),(17,1,'RUN20260906054829981998',101,NULL,2,'帮去药店取药','已线上下单，报手机号后四位 1002 取药。','科技园大药房','科技园北区C栋502',NULL,NULL,NULL,NULL,1,10.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-06 05:48:29','','2026-10-27 17:07:32',NULL),(18,1,'RUN20260906060057829297',101,NULL,2,'傍晚帮遛狗 30 分钟','金毛，性格温顺，牵引绳在我家门口。','小区3栋1单元','小区中央花园',NULL,NULL,NULL,NULL,1,12.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-06 06:00:57','','2026-10-27 17:07:32',NULL),(19,1,'RUN20260906071303180831',101,NULL,1,'积分删除验证-互助任务',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'cancelled',NULL,'2026-09-08 18:00:00',NULL,NULL,NULL,'验证完清理',NULL,NULL,NULL,NULL,'unpaid',NULL,NULL,'1','2026-09-06 07:13:03','','2026-10-27 17:02:24',NULL),(20,1,'RUN20260912095020285796',101,NULL,2,'代买日用品（洗发水）','海飞丝去屑 750ml，小票留好我报销。','沃尔玛（科技园店）','小区5栋901',NULL,NULL,NULL,NULL,1,6.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'1','2026-09-12 09:50:20','','2026-10-27 17:07:32',NULL),(21,1,'RUN20260912100038933018',101,NULL,1,'帮搬两个纸箱上楼','不重，就是占手，电梯直达。','小区南门快递点','小区7栋1201',NULL,NULL,NULL,NULL,1,8.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,21,'PAY202609121000385144','paid',NULL,NULL,'1','2026-09-12 10:00:38','','2026-10-27 17:07:32',NULL),(22,1,'RUN-DEMO-0001',101,NULL,1,'帮取快递（菜鸟驿站）','两个小件，晚上 7 点后驿站关门，麻烦今天取一下放门口。','科技园南区菜鸟驿站','科技园南区A栋12楼',NULL,NULL,NULL,NULL,1,10.00,'pending','2026-10-27 21:07:10','2026-10-30 17:07:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'','2026-10-27 14:02:24','','2026-10-27 17:07:10',NULL),(23,1,'RUN-DEMO-0002',101,102,2,'代买星巴克咖啡两杯','美式 + 拿铁，去冰，送到 12 楼前台即可。','科技园星巴克','科技园南区A栋12楼',NULL,NULL,NULL,NULL,1,12.00,'accepted',NULL,'2026-10-28 17:02:24','2026-10-26 21:02:24',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'paid',NULL,NULL,'','2026-10-26 17:02:24','','2026-10-27 17:02:24',NULL);
/*!40000 ALTER TABLE `twelve_runner_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `twelve_runner_task_log`
--

DROP TABLE IF EXISTS `twelve_runner_task_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `twelve_runner_task_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL,
  `action` varchar(30) NOT NULL COMMENT 'publish/assign/reject/start/finish/confirm/cancel/refund/dispute',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`tenant_id`,`task_id`),
  KEY `idx_operator` (`tenant_id`,`operator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务流转日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `twelve_runner_task_log`
--

LOCK TABLES `twelve_runner_task_log` WRITE;
/*!40000 ALTER TABLE `twelve_runner_task_log` DISABLE KEYS */;
INSERT INTO `twelve_runner_task_log` VALUES (3,1,3,1,'publish','发布任务','2026-09-05 23:57:10'),(4,1,3,1,'cancel','临时不需要了','2026-09-05 23:57:10'),(5,1,4,1,'publish','发布任务','2026-09-05 23:57:10'),(6,1,5,1,'publish','发布任务','2026-09-05 23:58:05'),(7,1,6,1,'publish','发布任务','2026-09-06 00:00:23'),(8,1,6,108,'assign','抢单成功','2026-09-06 00:00:23'),(9,1,6,108,'start','开始配送','2026-09-06 00:00:23'),(10,1,6,108,'finish','提交完成','2026-09-06 00:00:23'),(11,1,6,1,'confirm','下单人确认完成','2026-09-06 00:00:23'),(12,1,5,1,'cancel','测试清理','2026-09-06 00:00:32'),(13,1,7,1,'publish','发布任务','2026-09-06 00:14:42'),(14,1,8,108,'publish','发布任务','2026-09-06 00:14:52'),(15,1,7,108,'assign','抢单成功','2026-09-06 00:14:52'),(16,1,7,108,'start','开始配送','2026-09-06 00:14:52'),(17,1,7,108,'finish','提交完成','2026-09-06 00:14:52'),(18,1,7,1,'confirm','下单人确认完成','2026-09-06 00:14:52'),(19,1,8,1,'assign','抢单成功','2026-09-06 00:14:52'),(20,1,8,108,'cancel','不用了测试取消','2026-09-06 00:14:52'),(21,1,9,1,'publish','发布任务','2026-09-06 00:23:42'),(22,1,10,1,'publish','发布任务','2026-09-06 00:23:47'),(23,1,10,108,'assign','抢单成功','2026-09-06 00:23:47'),(24,1,10,108,'start','开始配送','2026-09-06 00:23:47'),(25,1,10,108,'finish','提交完成','2026-09-06 00:23:47'),(26,1,10,1,'confirm','下单人确认完成','2026-09-06 00:23:47'),(30,1,13,1,'publish','发布任务','2026-09-06 05:48:29'),(31,1,14,1,'publish','发布任务','2026-09-06 05:48:29'),(32,1,14,108,'assign','抢单成功','2026-09-06 05:48:29'),(33,1,14,108,'start','开始配送','2026-09-06 05:48:29'),(34,1,14,108,'finish','提交完成','2026-09-06 05:48:29'),(35,1,14,1,'confirm','下单人确认完成','2026-09-06 05:48:29'),(36,1,15,1,'publish','发布任务','2026-09-06 05:48:29'),(37,1,15,108,'assign','抢单成功','2026-09-06 05:48:29'),(38,1,15,1,'cancel','seeds penalty','2026-09-06 05:48:29'),(39,1,16,1,'publish','发布任务','2026-09-06 05:48:29'),(40,1,16,108,'assign','抢单成功','2026-09-06 05:48:29'),(41,1,17,1,'publish','发布任务','2026-09-06 05:48:29'),(42,1,18,1,'publish','发布任务','2026-09-06 06:00:57'),(43,1,19,1,'publish','发布任务','2026-09-06 07:13:03'),(44,1,19,1,'cancel','验证完清理','2026-09-06 07:13:03'),(45,1,20,1,'publish','发布任务','2026-09-12 09:50:20'),(46,1,21,1,'publish','发布任务','2026-09-12 10:00:38'),(47,1,24,1,'publish','发布任务','2026-11-03 16:35:20'),(48,1,24,1,'cancel','smoke-cancel','2026-11-03 16:35:33');
/*!40000 ALTER TABLE `twelve_runner_task_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `undo_log`
--

DROP TABLE IF EXISTS `undo_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AT transaction mode undo table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `undo_log`
--

LOCK TABLES `undo_log` WRITE;
/*!40000 ALTER TABLE `undo_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `undo_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_address`
--

DROP TABLE IF EXISTS `user_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_address` (
  `address_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `receiver` varchar(30) NOT NULL,
  `mobile` varchar(20) NOT NULL,
  `province` varchar(20) NOT NULL,
  `city` varchar(20) NOT NULL,
  `district` varchar(20) DEFAULT '',
  `detail` varchar(200) NOT NULL,
  `zip_code` varchar(10) DEFAULT '',
  `is_default` tinyint(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`address_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_address`
--

LOCK TABLES `user_address` WRITE;
/*!40000 ALTER TABLE `user_address` DISABLE KEYS */;
INSERT INTO `user_address` VALUES (1,101,'阿花','13800001001','广东省','深圳市','南山区','科技园南区A栋12楼','',1,'2026-06-08 11:02:11',NULL),(6,101,'小美','13800001001','广东省','深圳市','南山区','科技园南区B栋 8 楼 802','',1,'2026-08-12 13:57:18',NULL),(7,125,'清华管理员','13800002002','北京市','北京市','海淀区','清华大学东门 1 号楼','',1,NULL,NULL),(8,102,'老张','13800001002','广东省','深圳市','南山区','科技园北区 3 栋 501','',1,'2026-10-17 17:02:24',NULL);
/*!40000 ALTER TABLE `user_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_ban_log`
--

DROP TABLE IF EXISTS `user_ban_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_ban_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `duration_hours` int NOT NULL,
  `reason` varchar(500) NOT NULL,
  `op_id` bigint NOT NULL,
  `ban_at` datetime NOT NULL,
  `unban_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='封禁记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_ban_log`
--

LOCK TABLES `user_ban_log` WRITE;
/*!40000 ALTER TABLE `user_ban_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_ban_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_dislike_log`
--

DROP TABLE IF EXISTS `user_dislike_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_dislike_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `cat_l1` varchar(20) DEFAULT NULL COMMENT '商品一级类目(打分降权按类目聚合)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='负反馈流水(不感兴趣)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_dislike_log`
--

LOCK TABLES `user_dislike_log` WRITE;
/*!40000 ALTER TABLE `user_dislike_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_dislike_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_interest_stat`
--

DROP TABLE IF EXISTS `user_interest_stat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_interest_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `cat_l1` varchar(20) NOT NULL COMMENT '一级类目',
  `cat_l2` varchar(20) NOT NULL DEFAULT '' COMMENT '二级类目(空串=仅一级)',
  `weight` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '兴趣权重(浏览1/收藏3/下单5,含时间衰减)',
  `source` varchar(10) NOT NULL DEFAULT 'behavior' COMMENT 'behavior=行为聚合/init=引导种子',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_cat` (`user_id`,`cat_l1`,`cat_l2`),
  KEY `idx_tenant_user` (`tenant_id`,`user_id`,`weight`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户兴趣画像(首页推荐召回)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_interest_stat`
--

LOCK TABLES `user_interest_stat` WRITE;
/*!40000 ALTER TABLE `user_interest_stat` DISABLE KEYS */;
INSERT INTO `user_interest_stat` VALUES (5,1,101,'digital','',6.0000,'init','2026-10-27 17:02:24'),(6,1,101,'home','',4.0000,'init','2026-10-27 17:02:24'),(7,1,101,'books','',2.0000,'init','2026-10-27 17:02:24'),(8,1,102,'digital','',5.0000,'init','2026-10-27 17:02:24'),(9,1,102,'sport','',3.0000,'init','2026-10-27 17:02:24');
/*!40000 ALTER TABLE `user_interest_stat` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_privacy`
--

DROP TABLE IF EXISTS `user_privacy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_privacy` (
  `user_id` bigint NOT NULL,
  `allow_mobile_search` tinyint(1) DEFAULT '1',
  `allow_feed_by_stranger` tinyint(1) DEFAULT '1',
  `enable_nearby_recommend` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户隐私设置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_privacy`
--

LOCK TABLES `user_privacy` WRITE;
/*!40000 ALTER TABLE `user_privacy` DISABLE KEYS */;
INSERT INTO `user_privacy` VALUES (101,1,1,1),(102,1,1,1),(103,1,1,1),(104,1,1,1),(108,1,1,1);
/*!40000 ALTER TABLE `user_privacy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_relation`
--

DROP TABLE IF EXISTS `user_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关注者',
  `target_user_id` bigint NOT NULL COMMENT '被关注者',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_user_id`),
  KEY `idx_target` (`target_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关注关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_relation`
--

LOCK TABLES `user_relation` WRITE;
/*!40000 ALTER TABLE `user_relation` DISABLE KEYS */;
INSERT INTO `user_relation` VALUES (1,101,102,'2026-06-03 11:02:11');
/*!40000 ALTER TABLE `user_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_tag`
--

DROP TABLE IF EXISTS `user_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `tag` varchar(20) NOT NULL,
  `op_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户标签(运营打标)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_tag`
--

LOCK TABLES `user_tag` WRITE;
/*!40000 ALTER TABLE `user_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_third_bind`
--

DROP TABLE IF EXISTS `user_third_bind`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_third_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL COMMENT 'wechat/alipay',
  `open_id` varchar(100) NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_openid` (`channel`,`open_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='第三方绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_third_bind`
--

LOCK TABLES `user_third_bind` WRITE;
/*!40000 ALTER TABLE `user_third_bind` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_third_bind` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'twelvet-fast'
--

--
-- Dumping routines for database 'twelvet-fast'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 17:35:01
