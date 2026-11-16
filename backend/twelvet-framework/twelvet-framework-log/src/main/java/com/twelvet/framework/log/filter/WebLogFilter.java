package com.twelvet.framework.log.filter;

import com.twelvet.framework.core.constants.Constants;
import com.twelvet.framework.utils.DateUtils;
import com.twelvet.framework.utils.JacksonUtils;
import com.twelvet.framework.utils.StrUtils;
import com.twelvet.framework.utils.TUtils;
import com.twelvet.framework.utils.http.ServletUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@WebFilter(filterName = "BaseFilter", urlPatterns = "/*")
@Component
public class WebLogFilter implements Filter {

	private final static Logger log = LoggerFactory.getLogger(WebLogFilter.class);

	/**
	 * 忽略日志输出地址
	 */
	private final static List<String> IGNORES = Arrays.asList("/actuator/health");

	/**
	 * 请求体含明文敏感信息的接口：日志中整段隐藏，不做字段级打印
	 */
	private final static List<String> SENSITIVE_BODY_PATHS = Arrays.asList("/trade/seller/account/apply");

	@Override
	public void init(FilterConfig filterConfig) {
		log.info("过滤器初始化");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletResponse servletResponse = (HttpServletResponse) response;

		HttpServletRequest servletRequest = (HttpServletRequest) request;

		// 忽略列表日志输出
		if (!ServletUtils.isAjax() || IGNORES.contains(servletRequest.getRequestURI())) {
			chain.doFilter(request, response);
			return;
		}

		String contentType = request.getContentType();
		if (!StringUtils.isEmpty(contentType)) {
			if (contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)
					|| contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
				chain.doFilter(request, response);
				return;
			}
		}

		long startTime = System.currentTimeMillis();

		RequestWrapper requestWrapper = new RequestWrapper((HttpServletRequest) request);
		ResponseWrapper responseWrapper = new ResponseWrapper(servletResponse);

		String reqJson = requestWrapper.getBody();
		Map<String, String[]> map = request.getParameterMap();
		if (map != null && !map.isEmpty() && StrUtils.isEmpty(reqJson)) {
			reqJson = mapToString(map);
		}
		// 实名提交等接口的请求体含证件号/银行卡号/姓名，必须在落日志与 jsonParam 之前脱敏
		reqJson = sanitizeBody(servletRequest.getRequestURI(), reqJson);
		requestWrapper.setAttribute("jsonParam", reqJson);

		chain.doFilter(requestWrapper, responseWrapper);
		long endTime = System.currentTimeMillis();
		// 获取response返回的内容并重新写入response
		byte[] bytes = responseWrapper.getResponseData();
		if (bytes.length > 0) {
			response.getOutputStream().write(bytes);
		}

		// 仅输出JSON
		String responseData = new String(bytes, StandardCharsets.UTF_8);

		// 判断是否为JSON响应
		if (!JacksonUtils.isValidJson(responseData)) {
			responseData = "IGNORES";
		}

		log.info("""

				===================Request================>
				时间：{}
				地址：{}
				token：{}
				参数：{}
				方式：{}
				<===================Response================
				状态：{}
				内容：{}
				时长：{}毫秒
				============================================
				""", DateUtils.getTime(), requestWrapper.getRequestURL(),
				// 认证Token（脱敏：仅保留最后4位）
				maskToken(ServletUtils.getRequest().get().getHeader(Constants.AUTHORIZATION)), reqJson,
				requestWrapper.getMethod(), responseWrapper.getStatus(), responseData, endTime - startTime

		);

	}

	/**
	 * 敏感接口请求体脱敏：整段隐藏，避免证件号/银行卡号/姓名进入日志。
	 * @param requestUri 请求路径
	 * @param body 原始请求体
	 * @return 可写日志的请求体
	 */
	static String sanitizeBody(String requestUri, String body) {
		if (StringUtils.isBlank(body) || requestUri == null) {
			return body;
		}
		for (String path : SENSITIVE_BODY_PATHS) {
			if (requestUri.endsWith(path)) {
				return "[敏感请求体已脱敏]";
			}
		}
		return body;
	}

	/**
	 * 脱敏 Authorization Token：仅保留前缀（如 "Bearer"）和末尾4位，防止 Token 泄露到日志。
	 */
	private static String maskToken(String token) {
		if (StringUtils.isBlank(token)) {
			return "";
		}
		String trimmed = token.trim();
		if (trimmed.length() <= 4) {
			return "****";
		}
		if (trimmed.startsWith("Bearer ")) {
			String suffix = trimmed.substring("Bearer ".length());
			if (suffix.length() <= 4) {
				return "Bearer ****";
			}
			return "Bearer ****" + suffix.substring(suffix.length() - 4);
		}
		return "****" + trimmed.substring(trimmed.length() - 4);
	}

	private String mapToString(Map<String, String[]> map) {
		StringBuilder sb = new StringBuilder();
		for (String key : map.keySet()) {
			sb.append(key).append("=");
			String[] paramValues = map.get(key);
			int len = paramValues.length - 1;
			for (int i = 0; i <= len; i++) {

				sb.append(paramValues[i]);
				if (i != len) {
					sb.append(",");
				}
			}
			sb.append(";");

		}
		return sb.toString();
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

}
