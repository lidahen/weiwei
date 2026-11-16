package com.twelvet.admin;

import com.twelvet.framework.datasource.annotation.EnableDynamicDataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author twelvet
 * @Description: 启动入口
 */
@EnableDynamicDataSource
@MapperScan(basePackages = { "com.twelvet.system.server.mapper", "com.twelvet.system.api.mapper",
		"com.twelvet.dfs.server.mapper", "com.twelvet.gen.server.mapper", "com.twelvet.quartz.server.mapper",
		"com.twelvet.framework", "com.twelvet.product.server.mapper", "com.twelvet.trade.server.mapper",
		"com.twelvet.payment.server.mapper", "com.twelvet.runner.server.mapper", "com.twelvet.im.server.mapper",
		"com.twelvet.ai.server.mapper", "com.twelvet.exchange.server.mapper" })
@ComponentScan(basePackages = { "com.twelvet" })
// 表示通过aop框架暴露该代理对象,AopContext能够访问
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class FastApplication {

	public static void main(String[] args) {
		SpringApplication.run(FastApplication.class, args);
	}

}
