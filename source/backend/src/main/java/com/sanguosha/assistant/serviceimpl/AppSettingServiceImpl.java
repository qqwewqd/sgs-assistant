package com.sanguosha.assistant.serviceimpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.assistant.entity.AppSetting;
import com.sanguosha.assistant.mapper.AppSettingMapper;
import com.sanguosha.assistant.service.AppSettingService;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.AppSettingsRequest;
import com.sanguosha.assistant.vo.AppSettingsVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppSettingServiceImpl implements AppSettingService {
    private static final String KEY_MANUAL_PICK_ENABLED = "manual_pick_enabled";
    private static final String KEY_CROWN_PRINCE_ENABLED = "crown_prince_enabled";
    private static final String KEY_GENERAL_SWAP_ENABLED = "general_swap_enabled";
    private static final String KEY_FACTIONS = "factions";
    private static final List<String> DEFAULT_FACTIONS = List.of("WEI", "SHU", "WU", "QUN", "JIN", "GOD");
    private static final Map<String, String> LEGACY_FACTIONS = Map.of(
            "魏", "WEI", "蜀", "SHU", "吴", "WU", "群", "QUN", "晋", "JIN", "神", "GOD"
    );

    private final AppSettingMapper appSettingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AppSettingsVO getSettings() {
        AppSettingsVO vo = new AppSettingsVO();
        vo.setManualPickEnabled(isManualPickEnabled());
        vo.setCrownPrinceEnabled(isCrownPrinceEnabled());
        vo.setGeneralSwapEnabled(isGeneralSwapEnabled());
        vo.setFactions(getFactions());
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
        if (request != null && request.getGeneralSwapEnabled() != null) {
            setValue(KEY_GENERAL_SWAP_ENABLED, Boolean.TRUE.equals(request.getGeneralSwapEnabled()) ? "true" : "false");
        }
        if (request != null && request.getFactions() != null) {
            setValue(KEY_FACTIONS, writeFactions(normalizeFactions(request.getFactions())));
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

    @Override
    public boolean isGeneralSwapEnabled() {
        AppSetting setting = appSettingMapper.selectById(KEY_GENERAL_SWAP_ENABLED);
        return setting != null && "true".equalsIgnoreCase(setting.getSettingValue());
    }

    @Override
    public List<String> getFactions() {
        AppSetting setting = appSettingMapper.selectById(KEY_FACTIONS);
        if (setting == null || setting.getSettingValue() == null || setting.getSettingValue().isBlank()) {
            return DEFAULT_FACTIONS;
        }
        try {
            List<String> factions = objectMapper.readValue(setting.getSettingValue(), new TypeReference<List<String>>() { });
            return normalizeFactions(factions);
        } catch (Exception ignored) {
            return DEFAULT_FACTIONS;
        }
    }

    private List<String> normalizeFactions(List<String> factions) {
        if (factions == null) {
            throw new AppException("势力列表格式不正确");
        }
        if (factions.size() > 10) {
            throw new AppException("最多可配置 10 个势力");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String faction : factions) {
            if (faction == null || faction.trim().isBlank()) {
                throw new AppException("势力名称不能为空");
            }
            String value = faction.trim().toUpperCase(Locale.ROOT);
            value = LEGACY_FACTIONS.getOrDefault(value, value);
            if (value.length() > 20) {
                throw new AppException("势力名称最多 20 个字符");
            }
            if (!normalized.add(value)) {
                throw new AppException("势力不能重复");
            }
        }
        return new ArrayList<>(normalized);
    }

    private String writeFactions(List<String> factions) {
        try {
            return objectMapper.writeValueAsString(factions);
        } catch (Exception exception) {
            throw new AppException("保存势力设置失败");
        }
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
