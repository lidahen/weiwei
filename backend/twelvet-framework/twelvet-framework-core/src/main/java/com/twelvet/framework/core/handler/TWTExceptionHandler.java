package com.twelvet.framework.core.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.QuotaExceededException;
import com.twelvet.framework.core.exception.TWTException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author twelvet
 * @Description: 全局异常处理器
 */
@RestControllerAdvice
public class TWTExceptionHandler {

	private final Logger log = LoggerFactory.getLogger(TWTExceptionHandler.class);

	/**
	 * 未登录拦截 更多拦截处理：<a href=
	 * "https://gitee.com/dromara/sa-token/blob/master/sa-token-demo/sa-token-demo-case/src/main/java/com/pj/current/GlobalException.java">...</a>
	 * @param e Exception
	 * @return JsonResult<String>
	 */
	@ExceptionHandler(NotLoginException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public JsonResult<String> handlerException(NotLoginException e) {
		return JsonResult.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
	}

	/**
	 * 权限不足拦截
	 * @param e Exception
	 * @return JsonResult<String>
	 */
	@ExceptionHandler(NotRoleException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JsonResult<String> handleNotRoleException(NotRoleException e) {
		return JsonResult.error(e.getMessage());
	}

	/**
	 * 权限码不足拦截（@SaCheckPermission 未通过）
	 * <p>
	 * 之前没有这个分支，NotPermissionException 会落到下面的全局 Exception 分支， 返回
	 * 500「服务器错误」，把鉴权失败伪装成服务端故障——前端无法区分，演示也容易误解。
	 * </p>
	 * @param e Exception
	 * @return JsonResult<String>
	 */
	@ExceptionHandler(NotPermissionException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public JsonResult<String> handleNotPermissionException(NotPermissionException e) {
		return JsonResult.error(HttpStatus.FORBIDDEN.value(), e.getMessage());
	}

	/**
	 * 全局异常
	 * @param e Exception
	 * @return JsonResult
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public JsonResult<String> handleException(Exception e) {
		log.error(e.getMessage(), e);
		return JsonResult.error(e.getLocalizedMessage());
	}

	/**
	 * 基础异常
	 * @param e TWTException
	 * @return JsonResult
	 */
	@ExceptionHandler(TWTException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JsonResult<String> fastGoException(TWTException e) {
		log.warn("业务异常: {}", e.getMessage());
		return JsonResult.error(HttpStatus.BAD_REQUEST.value(), e.getMessage());
	}

	/**
	 * 配额超限（商业化 P0：租户套餐配额检查触发）→ HTTP 429
	 * @param e QuotaExceededException
	 * @return JsonResult
	 */
	@ExceptionHandler(QuotaExceededException.class)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public JsonResult<String> handleQuotaExceeded(QuotaExceededException e) {
		log.warn("配额超限: {}", e.getMessage());
		return JsonResult.error(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage());
	}

	/**
	 * 处理业务校验过程中碰到的非法参数异常 该异常基本由{@link org.springframework.util.Assert}抛出
	 * @param exception 参数校验异常
	 * @return API返回结果对象包装后的错误输出结果
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JsonResult<String> handleIllegalArgumentException(IllegalArgumentException exception) {
		log.warn("非法参数,ex = {}", exception.getMessage());
		return JsonResult.error(exception.getMessage());
	}

	/**
	 * validation Exception 参数绑定异常
	 * @return JsonResult
	 */
	@ExceptionHandler({ MethodArgumentNotValidException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JsonResult<String> handleBodyValidException(MethodArgumentNotValidException exception) {
		List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
		log.warn("参数绑定异常,ex = {}", fieldErrors.get(0).getDefaultMessage());
		return JsonResult.error(fieldErrors.get(0).getDefaultMessage());
	}

	/**
	 * validation Exception (以form-data形式传参) 参数绑定异常
	 * @return JsonResult
	 */
	@ExceptionHandler({ BindException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JsonResult<String> bindExceptionHandler(BindException exception) {
		List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
		log.error("参数绑定异常,ex = {}", fieldErrors.get(0).getDefaultMessage());
		return JsonResult.error(fieldErrors.get(0).getDefaultMessage());
	}

	/**
	 * 请求方式不支持
	 * @param e HttpRequestMethodNotSupportedException
	 * @return JsonResult
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public JsonResult<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return JsonResult.error(e.getMessage());
	}

}
