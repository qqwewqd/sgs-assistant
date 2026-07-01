package com.sanguosha.assistant.service;

import com.sanguosha.assistant.vo.AppSettingsRequest;
import com.sanguosha.assistant.vo.AppSettingsVO;

public interface AppSettingService {
    AppSettingsVO getSettings();

    AppSettingsVO updateSettings(AppSettingsRequest request);

    boolean isManualPickEnabled();

    boolean isCrownPrinceEnabled();
}
