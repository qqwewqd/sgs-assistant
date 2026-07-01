package com.sanguosha.assistant.serviceimpl;

import com.sanguosha.assistant.entity.AppSetting;
import com.sanguosha.assistant.mapper.AppSettingMapper;
import com.sanguosha.assistant.service.AppSettingService;
import com.sanguosha.assistant.vo.AppSettingsRequest;
import com.sanguosha.assistant.vo.AppSettingsVO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppSettingServiceImpl implements AppSettingService {
    private static final String KEY_MANUAL_PICK_ENABLED = "manual_pick_enabled";
    private static final String KEY_CROWN_PRINCE_ENABLED = "crown_prince_enabled";

    private final AppSettingMapper appSettingMapper;

    @Override
    public AppSettingsVO getSettings() {
        AppSettingsVO vo = new AppSettingsVO();
        vo.setManualPickEnabled(isManualPickEnabled());
        vo.setCrownPrinceEnabled(isCrownPrinceEnabled());
        return vo;
    }

    @Override
    public AppSettingsVO updateSettings(AppSettingsRequest request) {
        if (request != null && request.getManualPickEnabled() != null) {
            setValue(KEY_MANUAL_PICK_ENABLED, Boolean.TRUE.equals(request.getManualPickEnabled()) ? "true" : "false");
        }
        if (request != null && request.getCrownPrinceEnabled() != null) {
            setValue(KEY_CROWN_PRINCE_ENABLED, Boolean.TRUE.equals(request.getCrownPrinceEnabled()) ? "true" : "false");
        }
        return getSettings();
    }

    @Override
    public boolean isManualPickEnabled() {
        AppSetting setting = appSettingMapper.selectById(KEY_MANUAL_PICK_ENABLED);
        return setting != null && "true".equalsIgnoreCase(setting.getSettingValue());
    }

    @Override
    public boolean isCrownPrinceEnabled() {
        AppSetting setting = appSettingMapper.selectById(KEY_CROWN_PRINCE_ENABLED);
        return setting != null && "true".equalsIgnoreCase(setting.getSettingValue());
    }

    private void setValue(String key, String value) {
        AppSetting setting = appSettingMapper.selectById(key);
        if (setting == null) {
            setting = new AppSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            appSettingMapper.insert(setting);
            return;
        }
        setting.setSettingValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        appSettingMapper.updateById(setting);
    }
}
