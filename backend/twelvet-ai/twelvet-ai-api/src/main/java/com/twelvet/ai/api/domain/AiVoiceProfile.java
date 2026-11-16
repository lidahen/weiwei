package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI 音色库实体
 */
@Schema(description = "AI 音色库")
public class AiVoiceProfile extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键 ID")
	private Long id;

	@Schema(description = "用户 ID")
	private Long userId;

	@Schema(description = "用户起的别名")
	private String name;

	@Schema(description = "供应商: siliconflow / cosyvoice")
	private String vendor;

	@Schema(description = "上游音色 uri,如 speech:my-name:xxx:xxx")
	private String vendorVoiceId;

	@Schema(description = "上游模型,如 IndexTeam/IndexTTS-2")
	private String vendorModel;

	@Schema(description = "OSS 样本地址")
	private String sampleUrl;

	@Schema(description = "样本朗读文本(用于一致性校验)")
	private String sampleText;

	@Schema(description = "样本时长(毫秒)")
	private Integer durationMs;

	/** 0=上传中 1=训练中 2=可用 3=失败 */
	@Schema(description = "状态:0=上传中 1=训练中 2=可用 3=失败")
	private Integer status;

	@Schema(description = "合规授权签署(强制=1)")
	private Integer consentSigned;

	@Schema(description = "签署时 IP")
	private String consentIp;

	@Schema(description = "签署时间")
	private java.util.Date consentAt;

	@Schema(description = "错误信息")
	private String errorMsg;

	@Schema(description = "删除标志(0=未删 1=已删)")
	private Integer delFlag;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getVendorVoiceId() {
		return vendorVoiceId;
	}

	public void setVendorVoiceId(String vendorVoiceId) {
		this.vendorVoiceId = vendorVoiceId;
	}

	public String getVendorModel() {
		return vendorModel;
	}

	public void setVendorModel(String vendorModel) {
		this.vendorModel = vendorModel;
	}

	public String getSampleUrl() {
		return sampleUrl;
	}

	public void setSampleUrl(String sampleUrl) {
		this.sampleUrl = sampleUrl;
	}

	public String getSampleText() {
		return sampleText;
	}

	public void setSampleText(String sampleText) {
		this.sampleText = sampleText;
	}

	public Integer getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(Integer durationMs) {
		this.durationMs = durationMs;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Integer getConsentSigned() {
		return consentSigned;
	}

	public void setConsentSigned(Integer consentSigned) {
		this.consentSigned = consentSigned;
	}

	public String getConsentIp() {
		return consentIp;
	}

	public void setConsentIp(String consentIp) {
		this.consentIp = consentIp;
	}

	public java.util.Date getConsentAt() {
		return consentAt;
	}

	public void setConsentAt(java.util.Date consentAt) {
		this.consentAt = consentAt;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}
