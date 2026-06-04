package com.zc.iflyzcragback.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
/**
 * 异步任务配置。
 *
 * <p>文档向量化入库比较耗时，如果放在 HTTP 请求线程里会导致接口长时间不返回。
 * 这里开启异步能力，并提供专门的 ingestExecutor 线程池处理入库任务。</p>
 */
public class AsyncConfig {

    @Bean(name = "ingestExecutor")
    /**
     * 文档入库线程池。
     */
    public Executor ingestExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("ingest-");
        exec.setKeepAliveSeconds(60);
        exec.initialize();
        return exec;
    }
}
