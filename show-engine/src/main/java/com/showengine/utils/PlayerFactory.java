package com.showengine.utils;

import com.showengine.enums.PlayerEnum;
import com.showengine.service.PlayerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerFactory {

    private Map<PlayerEnum, PlayerService> playerMap;

    private final List<PlayerService> players;

    @PostConstruct
    public void init() {
        playerMap = players.stream()
                .collect(Collectors.toMap(
                        PlayerService::getPlayer,
                        Function.identity()
                ));
    }

    public PlayerService get(PlayerEnum type) {
        return playerMap.get(type);
    }
}
