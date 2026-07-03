package com.showengine.utils;

import com.showengine.enums.PlayerEnum;
import com.google.common.collect.Lists;

import java.util.List;

public final class PlayerUtils {

    public static List<PlayerEnum> listAllPlayers(){
        return Lists.newArrayList(PlayerEnum.PITCHER,PlayerEnum.CATCHER,PlayerEnum.FIELDER);
    }
}
