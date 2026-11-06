package com.example.ticket.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>两个插件，各自解决一个具体问题：
 * <ul>
 *   <li>分页插件：不加它，{@code Page} 对象在 SQL 里不会变成 LIMIT，查出来永远是全量数据</li>
 *   <li>乐观锁插件：抢单靠它。UPDATE 时会自动带上 {@code version = ?} 并自增，
 *       影响行数为 0 就说明这条工单已被别人改过，据此判定抢单失败</li>
 * </ul>
 *
 * <p>注意顺序：分页插件必须放在最后（官方要求），否则多插件组合时 SQL 改写会出错。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MP 拦截器链。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁：处理实体类上 @Version 注解的字段
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 分页：指定 MySQL 方言，否则分页 SQL 语法不对
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最多 100 条，防止前端传 size=100000 把数据库拖死
        pagination.setMaxLimit(100L);
        // 页码超过总页数时返回空列表，而不是回到第一页（避免前端无限翻页时数据重复）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
