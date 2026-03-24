package com.liu.shortlinkplatform.expection;

import com.liu.shortlinkplatform.enums.ResultCodeEnum;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{
    //状态码
    private final int code;

    //构造器1：传入状态码枚举
    public BusinessException(ResultCodeEnum resultCodeEnum){
        super(resultCodeEnum.getMsg());
        this.code = resultCodeEnum.getCode();
    }

    //构造器2：传入状自定义信息
    public BusinessException(String msg){
        super(msg);
        this.code = ResultCodeEnum.FAIL.getCode();
    }

    //构造器3：传入状态码和自定义信息
    public BusinessException(int code, String msg){
        super(msg);
        this.code = code;
    }
}
