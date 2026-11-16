package com.twelvet.product.server.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 上游（Python ai-service）WebClient：显式连接/响应超时。
 * <p>
 * 缺省 Builder 无超时——上游挂起时请求线程无限等待。SSE 长回答按 180s 响应上限， 连接 5s 快速失败（配合控制器层的友好错误文案）。
 */
@Configuration
public class AiGuideWebClientConfig {

	@Bean
	public WebClient aiGuideWebClient() {
		HttpClient httpClient = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
			.responseTimeout(Duration.ofSeconds(180));
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
	}

}
