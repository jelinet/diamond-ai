package com.showengine.service;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.players.model.PlayerResponse;
import com.showengine.players.service.PlayerService;
import com.showengine.router.enums.IntentSourceEnum;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.IntentRouterService;
import com.showengine.service.impl.ConversationServiceImpl;
import com.showengine.supervision.service.SupervisionStateMachine;
import com.showengine.supervision.service.impl.SupervisorServiceImpl;
import com.showengine.supervision.service.SupervisorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConversationServiceMentionControlTest {

    private PlayerService pitcher;
    private PlayerService catcher;
    private PlayerService fielder;
    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        pitcher = player(PlayerEnum.PITCHER);
        catcher = player(PlayerEnum.CATCHER);
        fielder = player(PlayerEnum.FIELDER);

        IntentRouterService router = mock(IntentRouterService.class);
        when(router.route(any())).thenReturn(new IntentResult(
                IntentTypeEnum.PLAYER_CONTROL,
                0.95,
                "命中控制指令",
                IntentSourceEnum.RULE,
                null,
                null));

        SupervisorService supervisor = new SupervisorServiceImpl(
                List.of(pitcher, catcher, fielder),
                mock(DecomposeService.class),
                new SupervisionStateMachine());

        service = new ConversationServiceImpl(router, supervisor);
    }

    @Test
    void mentionWithQuestionRoutesToMentionedPlayerInsteadOfSwitchingOnly() {
        service.handle(request("@CATCHER 这个方案有什么风险？"), mock(SseEmitter.class));

        verify(catcher).ask(anyString(), eq("这个方案有什么风险？"), any());
        verify(pitcher, never()).ask(anyString(), anyString(), any());
        verify(fielder, never()).ask(anyString(), anyString(), any());
    }

    @Test
    void multipleMentionsWithoutAssignmentsRouteSameQuestionToEachMentionedPlayer() {
        service.handle(request("@PITCHER @FIELDER 这个方案靠谱吗？"), mock(SseEmitter.class));

        verify(pitcher).ask(anyString(), eq("这个方案靠谱吗？"), any());
        verify(fielder).ask(anyString(), eq("这个方案靠谱吗？"), any());
        verify(catcher, never()).ask(anyString(), anyString(), any());
    }

    @Test
    void multipleMentionsWithAssignmentsRouteEachPlayerTheirOwnText() {
        service.handle(request("@PITCHER 看实现方案，@FIELDER 查相关资料"), mock(SseEmitter.class));

        verify(pitcher).ask(anyString(), eq("看实现方案"), any());
        verify(fielder).ask(anyString(), eq("查相关资料"), any());
        verify(catcher, never()).ask(anyString(), anyString(), any());
    }

    @Test
    void pureMentionStillSwitchesSessionMaster() {
        service.handle(request("@捕手"), mock(SseEmitter.class));

        assertThat(service.getMaster("conversation-1", "PITCHER")).isEqualTo(PlayerEnum.CATCHER);
        verify(pitcher, never()).ask(anyString(), anyString(), any());
        verify(catcher, never()).ask(anyString(), anyString(), any());
        verify(fielder, never()).ask(anyString(), anyString(), any());
    }

    private AskRequest request(String question) {
        AskRequest request = new AskRequest();
        request.setConversationId("conversation-1");
        request.setMasterPlayer("PITCHER");
        request.setQuestion(question);
        return request;
    }

    private PlayerService player(PlayerEnum player) {
        PlayerService service = mock(PlayerService.class);
        when(service.getPlayer()).thenReturn(player);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<PlayerResponse> onChunk = invocation.getArgument(2, Consumer.class);
            onChunk.accept(PlayerResponse.done(player, "done", 0, 0));
            return null;
        }).when(service).ask(anyString(), anyString(), any());
        return service;
    }
}
