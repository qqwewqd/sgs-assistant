package com.sanguosha.assistant.service;

import com.sanguosha.assistant.vo.AppSettingsRequest;
import com.sanguosha.assistant.vo.AppSettingsVO;
import java.util.List;

public interface AppSettingService {
    AppSettingsVO getSettings();

    AppSettingsVO updateSettings(AppSettingsRequest request);

    boolean isManualPickEnabled();

    boolean isCrownPrinceEnabled();

    boolean isGeneralSwapEnabled();

    List<String> getFactions();
}
