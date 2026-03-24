package com.liu.shortlinkplatform.common;

import com.liu.shortlinkplatform.enums.ResultCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果类
 */
@Data
public class Result <T> implements Serializable{
    //状态码
    private int code;
    //提示信息
    private String msg;
    //业务数据
    private T data;
    //时间戳
    private long timestamp;

    //私有化构造器
    private Result(){
        this.timestamp = System.currentTimeMillis();
    }
    //成功返回（带数据）
    public static <T> Result<T> success(T data){
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.SUCCESS.getCode());
        result.setMsg(ResultCodeEnum.SUCCESS.getMsg());
        result.setData(data);
        return result;
    }
    //成功返回（不带数据）
    public static <T> Result<T> success(){
        return success(null);
    }
    //失败返回(提示语)
    public static <T> Result<T> fail(String msg){
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.FAIL.getCode());
        result.setMsg(msg);
        result.setData(null);
        return result;
    }
    //返回失败状态码和提示语
    public static <T> Result<T> fail(int code ,String  msg){
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }
}
