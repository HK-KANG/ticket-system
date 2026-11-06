package com.example.ticket.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。
 *
 * <p>用法：
 * <pre>
 *   &#64;RequireRole({"AGENT", "ADMIN"})
 *   &#64;PostMapping("/{id}/grab")
 *   public Result&lt;Void&gt; grab(...) { ... }
 * </pre>
 *
 * <p>不写这个注解 = 只要登录就能访问。注解写在方法上优先于写在类上。
 *
 * <p>为什么用注解而不是在方法里写 if：角色规则散落在几十个方法里，改一次规则要翻遍全项目；
 * 注解把规则声明在方法签名上，一眼能看出这个接口给谁用。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问的角色，满足其中任意一个即可。
     */
    String[] value();
}
