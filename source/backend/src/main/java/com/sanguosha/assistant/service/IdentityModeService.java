package com.sanguosha.assistant.service;

import com.sanguosha.assistant.entity.IdentityMode;
import com.sanguosha.assistant.entity.IdentityModeRule;
import com.sanguosha.assistant.vo.IdentityModeRequest;
import com.sanguosha.assistant.vo.IdentityModeVO;
import java.util.List;

public interface IdentityModeService {
    List<IdentityModeVO> listModes(boolean admin);

    IdentityModeVO createMode(IdentityModeRequest request);

    IdentityModeVO updateMode(Long id, IdentityModeRequest request);

    void deleteMode(Long id);

    IdentityMode requireEnabledMode(Long id);

    List<IdentityModeRule> rulesForPlayerCount(Long modeId, int playerCount);
}
