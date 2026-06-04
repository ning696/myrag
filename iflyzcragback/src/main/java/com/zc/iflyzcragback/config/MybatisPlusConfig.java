package com.zc.iflyzcragback.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.zc.iflyzcragback.mapper")
/**
 * MyBatis-Plus 配置。
 *
 * <p>负责扫描 Mapper 接口，并注册分页插件。Service 中的 selectPage 能正常分页依赖这里。</p>
 */
public class MybatisPlusConfig {

    @Bean
    /**
     * 注册 MySQL 分页拦截器。
     */
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
