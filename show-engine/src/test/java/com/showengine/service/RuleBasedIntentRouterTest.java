package com.showengine.service;

import com.showengine.model.AskRequest;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.impl.RuleBasedIntentRouterImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedIntentRouterTest {

    private final RuleBasedIntentRouterImpl router = new RuleBasedIntentRouterImpl();

    @Test
    void casualMessageContainingTodayMatchesChatRule() {
        IntentResult result = router.route(request("你好，今天我的猫很开心，你开心吗"));

        assertThat(result.getIntentType()).isEqualTo(IntentTypeEnum.CHAT);
    }

    @Test
    void realTimeInformationStillMatchesSearchRule() {
        assertThat(router.route(request("今天的人民币对美元汇率是多少")).getIntentType())
                .isEqualTo(IntentTypeEnum.SEARCH);
        assertThat(router.route(request("现在Boardman这边天气怎么样")).getIntentType())
                .isEqualTo(IntentTypeEnum.SEARCH);
        assertThat(router.route(request("你好，现在天气怎么样")).getIntentType())
                .isEqualTo(IntentTypeEnum.SEARCH);
        assertThat(router.route(request("帮我查一下最近半年AI Agent相关的论文有哪些")).getIntentType())
                .isEqualTo(IntentTypeEnum.SEARCH);
    }

    private AskRequest request(String question) {
        AskRequest request = new AskRequest();
        request.setQuestion(question);
        return request;
    }
}
