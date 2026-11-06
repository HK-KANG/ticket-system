package com.example.ticket.security;

/**
 * 当前请求的登录用户上下文。
 *
 * <p>为什么用 ThreadLocal：Service 层需要知道"谁在操作"（比如查我的工单），
 * 但如果一路把 userId 当参数传下去，每个方法签名都会多一个参数，签到最后没人愿意改。
 * ThreadLocal 让当前线程内任何地方都能拿到，请求结束由拦截器清掉。
 *
 * <p>清理很重要：Tomcat 线程是复用的，不清会串号——
 * A 的请求用完线程没清理，B 的请求拿到同一个线程就读到了 A 的身份。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /**
     * 取当前用户，未登录返回 null。业务代码里需要"必须登录"时用 {@link #require()}。
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 取当前用户，未登录直接抛异常。
     */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new com.example.ticket.common.AuthException();
        }
        return user;
    }

    public static Long userId() {
        return require().userId();
    }

    public static String role() {
        return require().role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
