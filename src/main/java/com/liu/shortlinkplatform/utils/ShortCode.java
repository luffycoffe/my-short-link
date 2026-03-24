package com.liu.shortlinkplatform.utils;

import cn.hutool.core.util.StrUtil;
import com.liu.shortlinkplatform.config.ShortLinkConfig;
import com.liu.shortlinkplatform.enums.ResultCodeEnum;
import com.liu.shortlinkplatform.expection.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShortCode {
    //62进制字符集（数字+大小写字母）
    private static final int BASE = 62;
    private static final String CHARSET= "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Resource
    private ShortLinkConfig shortLinkConfig;
    //ID转短码
    public String idToShortCode(long id){
        if (id <= 0){
            throw new BusinessException("ID必须为正数");
        }
        StringBuilder sb = new StringBuilder();
        while (id > 0){
            sb.append(CHARSET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        //补位到配置的最小长度
        int minLength = shortLinkConfig.getCode().getMinLength();
        while (sb.length() < minLength){
            sb.append(CHARSET.charAt(0));
        }
        String shortCode = sb.reverse().toString();
        log.debug("短码：{}", shortCode);
        return shortCode;
    }
    //短码转ID
    public long shortCodeToId(String shortCode){
        if (StrUtil.isBlank(shortCode)){
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(),"短码不能为空");
        }
        long id = 0;
        for(char c : shortCode.toCharArray()){
            int index = CHARSET.indexOf(c);
            if (index == -1){
                throw new BusinessException("短码包含非法字符" + c);
            }
            id = id * BASE + CHARSET.indexOf(c);
        }
        return id;
    }
    /**
     * 检验短码是否合法
     */
    public boolean isValidShortCode(String shortCode){
      if (StrUtil.isBlank(shortCode)){
          return false;
      }
      int minLength = shortLinkConfig.getCode().getMinLength();
      if (shortCode.length() < minLength){
          return false;
      }
      for (char c : shortCode.toCharArray()){
          if (CHARSET.indexOf(c) == -1){
              return false;
          }
      }
      return true;
    }
}
