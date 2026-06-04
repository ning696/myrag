package com.zc.iflyzcragback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * 后端应用启动类。
 *
 * <p>运行 main 方法后，Spring Boot 会扫描当前包及子包下的 Controller、Service、Mapper、
 * Config 等组件，并启动内置 Web 服务。</p>
 */
public class IflyzcragbackApplication {

	/**
	 * Java 程序入口。
	 */
	public static void main(String[] args) {
		SpringApplication.run(IflyzcragbackApplication.class, args);
	}
}
