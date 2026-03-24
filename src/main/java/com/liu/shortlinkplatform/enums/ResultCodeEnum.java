package com.liu.shortlinkplatform.enums;

import lombok.Getter;

/**
 * 全局返回状态码枚举
 * 规范：
 *  - 200 ：成功
 *  - 400 ：参数错误
 *  - 500 ：系统错误
 */
@Getter
public enum ResultCodeEnum {
    //通用状态码
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数非法"),
    NOT_FOUND(404, "资源不存在"),
    //短链接业务状态码
    SHORT_LINK_NOT_EXIT(10001,"短链接不存在"),
    SHORT_LINK_DISABLE(10002,"短链接已禁用"),
    SHORT_LINK_EXPIRED(10003,"短链接已过期"),
    LONG_URL_INVALID(10004,"长链接非法");

    private int code;
    private final String msg;
    ResultCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }


}
