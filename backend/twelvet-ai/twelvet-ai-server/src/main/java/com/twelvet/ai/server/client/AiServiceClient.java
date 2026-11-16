package com.twelvet.ai.server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.twelvet.framework.core.exception.QuotaExceededException;
import com.twelvet.framework.core.exception.TWTException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI Python 服务 HTTP 客户端
 * <p>
 * Java Controller 保留 Sa-Token 权限校验，业务逻辑代理到 Python FastAPI 服务
 * <p>
 * Python 侧 Pydantic 模型使用 snake_case 字段名，本客户端统一用 snake_case ObjectMapper 与 Python 对齐。
 */
@Component
public class AiServiceClient {

	private final RestTemplate restTemplate;

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	public AiServiceClient(RestTemplateBuilder restTemplateBuilder) {
		ObjectMapper objectMapper = new ObjectMapper();
		// Python 侧 Pydantic 模型（ImageGenerateRequest 等）使用 snake_case 字段名，
		// 这里统一按 snake_case 序列化/反序列化，避免 genType/gen_type、inputImage/input_image
		// 这类字段名错位导致请求静默解析为空、图片参数丢失。
		objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
		converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));

		// 替换默认转换器（默认含 camelCase Jackson），同时保留 UTF-8 String 转换器处理字符编码、
		// ByteArray 转换器处理音频等二进制响应（/ai/chat/tts）、Form 转换器处理 multipart 转发
		// （/ai/creation/decompose-upload 视频上传）。
		this.restTemplate = restTemplateBuilder.customizers(template -> {
			template.setMessageConverters(List.of(new StringHttpMessageConverter(StandardCharsets.UTF_8),
					new ByteArrayHttpMessageConverter(), new FormHttpMessageConverter(), converter));
		}).build();
	}

	public <T> T post(String path, Object body, Class<T> responseType) {
		return post(path, body, responseType, null);
	}

	/**
	 * POST 请求（可附额外请求头）
	 * <p>
	 * AI 商品辅助接口（product-copy/product-image）靠此透传 X-Tenant-Id / X-User-Id， 供 Python
	 * 侧用量计费（事前配额检查 + ai_usage_log 明细落账）识别租户与用户。
	 * @param path Python 服务路径
	 * @param body 请求体
	 * @param responseType 响应类型
	 * @param extraHeaders 额外请求头（可为 null）
	 * @return 解包后的业务数据
	 */
	public <T> T post(String path, Object body, Class<T> responseType, Map<String, String> extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.valueOf("application/json; charset=UTF-8"));
		if (extraHeaders != null) {
			extraHeaders.forEach(headers::set);
		}
		HttpEntity<Object> entity = new HttpEntity<>(body, headers);
		ResponseEntity<T> response = restTemplate.exchange(aiServiceUrl + path, HttpMethod.POST, entity, responseType);
		return unwrapBody(response.getBody());
	}

	public <T> T get(String path, Class<T> responseType) {
		ResponseEntity<T> response = restTemplate.getForEntity(aiServiceUrl + path, responseType);
		return unwrapBody(response.getBody());
	}

	/**
	 * POST multipart 转发（文件上传代理，如 /ai/creation/decompose-upload）。
	 * <p>
	 * Python 侧 {@code file: UploadFile = File(...)} 接收 multipart 字段名 {@code file}； 额外参数经
	 * query string 传递（Python 侧标量参数默认从 query 解析）。
	 * @param path Python 服务路径
	 * @param fileBytes 文件字节
	 * @param filename 原始文件名（Content-Disposition 携带，Python 侧推断后缀用）
	 * @param queryParams 附加 query 参数（可为 null）
	 * @param extraHeaders 额外请求头（计费身份头等，可为 null）
	 * @return 解包后的业务数据
	 */
	public <T> T postMultipart(String path, byte[] fileBytes, String filename, Map<String, String> queryParams,
			Map<String, String> extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		if (extraHeaders != null) {
			extraHeaders.forEach(headers::set);
		}
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		// ByteArrayResource 需覆写 filename，否则 FormHttpMessageConverter 不生成 filename，
		// Python 侧 file.filename 为空导致后缀推断失败
		body.add("file", new ByteArrayResource(fileBytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		String url = aiServiceUrl + path;
		if (queryParams != null && !queryParams.isEmpty()) {
			StringBuilder query = new StringBuilder("?");
			queryParams.forEach((k, v) -> {
				if (v != null && !v.isEmpty()) {
					if (query.length() > 1) {
						query.append('&');
					}
					query.append(k).append('=').append(v);
				}
			});
			url += query;
		}
		HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
		return unwrapBody(response.getBody());
	}

	/**
	 * GET 请求（可附额外请求头）
	 * @param path Python 服务路径
	 * @param responseType 响应类型
	 * @param extraHeaders 额外请求头（可为 null）
	 */
	public <T> T get(String path, Class<T> responseType, Map<String, String> extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		if (extraHeaders != null) {
			extraHeaders.forEach(headers::set);
		}
		HttpEntity<Void> entity = new HttpEntity<>(headers);
		ResponseEntity<T> response = restTemplate.exchange(aiServiceUrl + path, HttpMethod.GET, entity, responseType);
		return unwrapBody(response.getBody());
	}

	/**
	 * POST 二进制响应（如 TTS 的 audio/mpeg），不做 {code,msg,data} 包装解包。
	 * <p>
	 * Python 侧约定：成功返回音频字节（audio/mpeg）；失败返回 ApiResponse JSON（application/json）。 调用方通过
	 * {@link #isJsonResponse} 判断是否为错误响应并自行解析 msg。
	 * @param path Python 服务路径
	 * @param body 请求体
	 * @param extraHeaders 额外请求头（可为 null）
	 * @return 原始响应（体 + Content-Type）
	 */
	public ResponseEntity<byte[]> postForBytes(String path, Object body, Map<String, String> extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.valueOf("application/json; charset=UTF-8"));
		headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON,
				MediaType.parseMediaType("audio/mpeg")));
		if (extraHeaders != null) {
			extraHeaders.forEach(headers::set);
		}
		HttpEntity<Object> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange(aiServiceUrl + path, HttpMethod.POST, entity, byte[].class);
	}

	/**
	 * 判断 postForBytes 的响应是否为 JSON（Python 侧错误 ApiResponse，而非音频二进制）
	 */
	public static boolean isJsonResponse(ResponseEntity<byte[]> response) {
		MediaType type = response.getHeaders().getContentType();
		return type != null && type.isCompatibleWith(MediaType.APPLICATION_JSON);
	}

	/**
	 * 从 postForBytes 的 JSON 错误响应中解析 msg（解析失败给默认文案）
	 */
	public static String jsonErrorMessage(ResponseEntity<byte[]> response) {
		try {
			String body = new String(response.getBody() == null ? new byte[0] : response.getBody(),
					StandardCharsets.UTF_8);
			com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(body);
			if (node != null && node.hasNonNull("msg")) {
				return node.get("msg").asText();
			}
		}
		catch (Exception ignored) {
		}
		return "AI 服务调用失败";
	}

	public <T> T put(String path, Object body, Class<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.valueOf("application/json; charset=UTF-8"));
		HttpEntity<Object> entity = new HttpEntity<>(body, headers);
		ResponseEntity<T> response = restTemplate.exchange(aiServiceUrl + path, HttpMethod.PUT, entity, responseType);
		return unwrapBody(response.getBody());
	}

	/**
	 * PUT 请求（可附额外请求头，计费身份头透传口径与 post 一致）
	 */
	public <T> T put(String path, Object body, Class<T> responseType, Map<String, String> extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.valueOf("application/json; charset=UTF-8"));
		if (extraHeaders != null) {
			extraHeaders.forEach(headers::set);
		}
		HttpEntity<Object> entity = new HttpEntity<>(body, headers);
		ResponseEntity<T> response = restTemplate.exchange(aiServiceUrl + path, HttpMethod.PUT, entity, responseType);
		return unwrapBody(response.getBody());
	}

	/**
	 * 解开 Python FastAPI 服务的 {@code {code, msg, data}} 统一响应包装。
	 * <p>
	 * - code == 200：返回内层 {@code data}，前端直接消费（数组 / 分页对象等）； - code == 429：配额超限/套餐过期（Python
	 * quota_service 的统一口径，聊天与 /ai/assist/* 共用 ai_usage_daily 单一账本），抛出
	 * {@link QuotaExceededException} → 全局异常处理器映射 HTTP 429， 把 Python 的友好文案原样透传给前端（不吞成
	 * 500/400）； - 其他 code != 200：抛出 {@link TWTException}，由全局异常处理器转为 JsonResult.error，透传
	 * Python 侧错误信息； - 非包装结构（如 CSV 文本）：原样返回。
	 * @param body Python 服务原始响应体
	 * @return 解包后的数据
	 */
	@SuppressWarnings("unchecked")
	public static <T> T unwrapBody(Object body) {
		if (body instanceof Map<?, ?> map) {
			Object code = map.get("code");
			if (code instanceof Number number) {
				if (number.intValue() == 200) {
					return (T) map.get("data");
				}
				Object msg = map.get("msg");
				String message = msg == null ? "AI 服务调用失败" : msg.toString();
				if (number.intValue() == 429) {
					throw new QuotaExceededException(message);
				}
				throw new TWTException(message);
			}
		}
		return (T) body;
	}

	/**
	 * DELETE 请求（exchange + unwrapBody：Python 业务失败 code!=200 时抛异常， 而非 restTemplate.delete
	 * 忽略响应体把失败吞成成功）。
	 */
	public Map<String, Object> delete(String path) {
		HttpHeaders headers = new HttpHeaders();
		HttpEntity<Void> entity = new HttpEntity<>(headers);
		ResponseEntity<Map> response = restTemplate.exchange(aiServiceUrl + path, HttpMethod.DELETE, entity, Map.class);
		return unwrapBody(response.getBody());
	}

}
