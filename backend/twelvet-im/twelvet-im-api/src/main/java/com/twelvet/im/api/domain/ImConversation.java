package com.twelvet.im.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 会话对象 im_conversation
 *
 * @author twelvet
 */
@Schema(description = "会话")
public class ImConversation extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "会话ID")
	private Long convId;

	@Schema(description = "用户A(较小ID)")
	private Long userAId;

	@Schema(description = "用户B(较大ID)")
	private Long userBId;

	@Schema(description = "最后消息ID")
	private Long lastMsgId;

	@Schema(description = "最后消息时间")
	private String lastMsgTime;

	@Schema(description = "关联商品ID")
	private Long relatedProductId;

	@Schema(description = "对方用户ID")
	private Long peerId;

	@Schema(description = "对方昵称")
	private String peerNickName;

	@Schema(description = "对方头像")
	private String peerAvatar;

	@Schema(description = "最后消息内容")
	private String lastContent;

	@Schema(description = "最后消息类型")
	private String lastType;

	@Schema(description = "商品标题")
	private String productTitle;

	@Schema(description = "商品封面")
	private String productCover;

	@Schema(description = "商品价格")
	private java.math.BigDecimal productPrice;

	public Long getConvId() {
		return convId;
	}

	public void setConvId(Long convId) {
		this.convId = convId;
	}

	public Long getUserAId() {
		return userAId;
	}

	public void setUserAId(Long userAId) {
		this.userAId = userAId;
	}

	public Long getUserBId() {
		return userBId;
	}

	public void setUserBId(Long userBId) {
		this.userBId = userBId;
	}

	public Long getLastMsgId() {
		return lastMsgId;
	}

	public void setLastMsgId(Long lastMsgId) {
		this.lastMsgId = lastMsgId;
	}

	public String getLastMsgTime() {
		return lastMsgTime;
	}

	public void setLastMsgTime(String lastMsgTime) {
		this.lastMsgTime = lastMsgTime;
	}

	public Long getRelatedProductId() {
		return relatedProductId;
	}

	public void setRelatedProductId(Long relatedProductId) {
		this.relatedProductId = relatedProductId;
	}

	public Long getPeerId() {
		return peerId;
	}

	public void setPeerId(Long peerId) {
		this.peerId = peerId;
	}

	public String getPeerNickName() {
		return peerNickName;
	}

	public void setPeerNickName(String peerNickName) {
		this.peerNickName = peerNickName;
	}

	public String getPeerAvatar() {
		return peerAvatar;
	}

	public void setPeerAvatar(String peerAvatar) {
		this.peerAvatar = peerAvatar;
	}

	public String getLastContent() {
		return lastContent;
	}

	public void setLastContent(String lastContent) {
		this.lastContent = lastContent;
	}

	public String getLastType() {
		return lastType;
	}

	public void setLastType(String lastType) {
		this.lastType = lastType;
	}

	public String getProductTitle() {
		return productTitle;
	}

	public void setProductTitle(String productTitle) {
		this.productTitle = productTitle;
	}

	public String getProductCover() {
		return productCover;
	}

	public void setProductCover(String productCover) {
		this.productCover = productCover;
	}

	public java.math.BigDecimal getProductPrice() {
		return productPrice;
	}

	public void setProductPrice(java.math.BigDecimal productPrice) {
		this.productPrice = productPrice;
	}

}
