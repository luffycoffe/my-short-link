package com.liu.shortlinkplatform.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class SentinelHotParamConfig {

    @PostConstruct
    public void init() {
        // 热点参数规则：创建短链接口
        ParamFlowRule rule = new ParamFlowRule();
        rule.setResource("createShortLink");
        rule.setParamIdx(0);
        rule.setCount(10);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setDurationInSec(1);
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }
}
