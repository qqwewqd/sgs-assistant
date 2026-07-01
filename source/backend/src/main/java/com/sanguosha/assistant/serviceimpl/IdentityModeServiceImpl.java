package com.sanguosha.assistant.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sanguosha.assistant.entity.IdentityMode;
import com.sanguosha.assistant.entity.IdentityModeRule;
import com.sanguosha.assistant.mapper.IdentityModeMapper;
import com.sanguosha.assistant.mapper.IdentityModeRuleMapper;
import com.sanguosha.assistant.service.IdentityModeService;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.IdentityModeRequest;
import com.sanguosha.assistant.vo.IdentityModeRuleRequest;
import com.sanguosha.assistant.vo.IdentityModeRuleVO;
import com.sanguosha.assistant.vo.IdentityModeVO;
import com.sanguosha.assistant.vo.ResultCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityModeServiceImpl implements IdentityModeService {
    private static final int MIN_PLAYER_COUNT = 2;
    private static final int MAX_PLAYER_COUNT = 10;
    private static final int MAX_RULE_BONUS = 20;
    private static final int DEFAULT_GENERAL_POOL_SIZE = 2;
    private static final int MAX_GENERAL_POOL_SIZE = 20;

    private final IdentityModeMapper identityModeMapper;
    private final IdentityModeRuleMapper identityModeRuleMapper;

    @Override
    public List<IdentityModeVO> listModes(boolean admin) {
        LambdaQueryWrapper<IdentityMode> wrapper = new LambdaQueryWrapper<IdentityMode>()
                .eq(!admin, IdentityMode::getEnabled, true)
                .orderByDesc(IdentityMode::getBuiltin)
                .orderByAsc(IdentityMode::getId);
        return identityModeMapper.selectList(wrapper).stream()
                .map(mode -> IdentityModeVO.from(mode, rulesByMode(mode.getId()).stream().map(IdentityModeRuleVO::from).toList()))
                .toList();
    }

    @Override
    @Transactional
    public IdentityModeVO createMode(IdentityModeRequest request) {
        List<IdentityModeRuleRequest> normalizedRules = validateRules(request == null ? null : request.getRules());
        IdentityMode mode = new IdentityMode();
        mode.setName(normalizeName(request == null ? null : request.getName()));
        mode.setEnabled(request == null || !Boolean.FALSE.equals(request.getEnabled()));
        mode.setBuiltin(false);
        mode.setCreatedAt(LocalDateTime.now());
        mode.setUpdatedAt(LocalDateTime.now());
        identityModeMapper.insert(mode);
        saveRules(mode.getId(), normalizedRules);
        return IdentityModeVO.from(mode, rulesByMode(mode.getId()).stream().map(IdentityModeRuleVO::from).toList());
    }

    @Override
    @Transactional
    public IdentityModeVO updateMode(Long id, IdentityModeRequest request) {
        IdentityMode mode = requireMode(id);
        List<IdentityModeRuleRequest> normalizedRules = validateRules(request == null ? null : request.getRules());
        String normalizedName = normalizeName(request == null ? null : request.getName());
        if (Boolean.TRUE.equals(mode.getBuiltin()) && !mode.getName().equals(normalizedName)) {
            throw new AppException("内置身份模式不能改名");
        }
        mode.setName(normalizedName);
        mode.setEnabled(request == null || !Boolean.FALSE.equals(request.getEnabled()));
        mode.setUpdatedAt(LocalDateTime.now());
        identityModeMapper.updateById(mode);
        identityModeRuleMapper.delete(new LambdaQueryWrapper<IdentityModeRule>().eq(IdentityModeRule::getModeId, mode.getId()));
        saveRules(mode.getId(), normalizedRules);
        return IdentityModeVO.from(mode, rulesByMode(mode.getId()).stream().map(IdentityModeRuleVO::from).toList());
    }

    @Override
    @Transactional
    public void deleteMode(Long id) {
        IdentityMode mode = requireMode(id);
        if (Boolean.TRUE.equals(mode.getBuiltin())) {
            throw new AppException("内置身份模式不能删除");
        }
        identityModeRuleMapper.delete(new LambdaQueryWrapper<IdentityModeRule>().eq(IdentityModeRule::getModeId, mode.getId()));
        identityModeMapper.deleteById(mode.getId());
    }

    @Override
    public IdentityMode requireEnabledMode(Long id) {
        IdentityMode mode = id == null ? defaultMode() : identityModeMapper.selectById(id);
        if (mode == null || !Boolean.TRUE.equals(mode.getEnabled())) {
            throw new AppException("身份模式不可用");
        }
        return mode;
    }

    @Override
    public List<IdentityModeRule> rulesForPlayerCount(Long modeId, int playerCount) {
        if (modeId == null) {
            throw new AppException("房间未选择身份模式");
        }
        List<IdentityModeRule> rules = identityModeRuleMapper.selectList(new LambdaQueryWrapper<IdentityModeRule>()
                .eq(IdentityModeRule::getModeId, modeId)
                .eq(IdentityModeRule::getPlayerCount, playerCount)
                .orderByAsc(IdentityModeRule::getSortOrder)
                .orderByAsc(IdentityModeRule::getId));
        int total = rules.stream().mapToInt(rule -> valueOrZero(rule.getQuantity())).sum();
        if (rules.isEmpty() || total != playerCount) {
            throw new AppException("当前身份模式没有适配 " + playerCount + " 人局");
        }
        return rules;
    }

    private IdentityMode requireMode(Long id) {
        if (id == null) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        IdentityMode mode = identityModeMapper.selectById(id);
        if (mode == null) {
            throw new AppException("身份模式不存在");
        }
        return mode;
    }

    private IdentityMode defaultMode() {
        return identityModeMapper.selectOne(new LambdaQueryWrapper<IdentityMode>()
                .eq(IdentityMode::getEnabled, true)
                .orderByDesc(IdentityMode::getBuiltin)
                .orderByAsc(IdentityMode::getId)
                .last("LIMIT 1"));
    }

    private List<IdentityModeRule> rulesByMode(Long modeId) {
        return identityModeRuleMapper.selectList(new LambdaQueryWrapper<IdentityModeRule>()
                .eq(IdentityModeRule::getModeId, modeId)
                .orderByAsc(IdentityModeRule::getPlayerCount)
                .orderByAsc(IdentityModeRule::getSortOrder)
                .orderByAsc(IdentityModeRule::getId));
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new AppException("请填写身份模式名称");
        }
        String normalized = name.trim();
        if (normalized.length() > 50) {
            throw new AppException("身份模式名称最多 50 个字");
        }
        return normalized;
    }

    private List<IdentityModeRuleRequest> validateRules(List<IdentityModeRuleRequest> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new AppException("请至少配置一条身份规则");
        }
        Map<Integer, Integer> totals = new HashMap<>();
        Map<Integer, Integer> leaders = new HashMap<>();
        Set<String> names = new HashSet<>();
        List<IdentityModeRuleRequest> normalized = new ArrayList<>();
        for (IdentityModeRuleRequest rule : rules) {
            IdentityModeRuleRequest copy = normalizeRule(rule);
            String key = copy.getPlayerCount() + ":" + copy.getIdentityName().toLowerCase(Locale.ROOT);
            if (!names.add(key)) {
                throw new AppException(copy.getPlayerCount() + " 人局身份名称重复");
            }
            totals.merge(copy.getPlayerCount(), copy.getQuantity(), Integer::sum);
            if (Boolean.TRUE.equals(copy.getIsLeader())) {
                leaders.merge(copy.getPlayerCount(), 1, Integer::sum);
            }
            normalized.add(copy);
        }
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            if (!entry.getKey().equals(entry.getValue())) {
                throw new AppException(entry.getKey() + " 人局身份数量合计需要等于 " + entry.getKey());
            }
        }
        for (Map.Entry<Integer, Integer> entry : leaders.entrySet()) {
            if (entry.getValue() > 1) {
                throw new AppException(entry.getKey() + " 人局最多只能有一个主身份");
            }
        }
        return normalized.stream()
                .sorted(Comparator.comparing(IdentityModeRuleRequest::getPlayerCount)
                        .thenComparing(IdentityModeRuleRequest::getSortOrder))
                .toList();
    }

    private IdentityModeRuleRequest normalizeRule(IdentityModeRuleRequest rule) {
        if (rule == null) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        int playerCount = normalizeRange(rule.getPlayerCount(), MIN_PLAYER_COUNT, MAX_PLAYER_COUNT, "玩家数");
        int quantity = normalizeRange(rule.getQuantity(), 1, MAX_PLAYER_COUNT, "身份数量");
        String identityName = normalizeIdentityName(rule.getIdentityName());
        int generalPoolSize = normalizeRange(rule.getGeneralPoolSize() == null ? DEFAULT_GENERAL_POOL_SIZE : rule.getGeneralPoolSize(), 1, MAX_GENERAL_POOL_SIZE, "发将数");
        int initialHpBonus = normalizeRange(rule.getInitialHpBonus() == null ? 0 : rule.getInitialHpBonus(), -MAX_RULE_BONUS, MAX_RULE_BONUS, "初始血加成");
        int maxHpBonus = normalizeRange(rule.getMaxHpBonus() == null ? 0 : rule.getMaxHpBonus(), -MAX_RULE_BONUS, MAX_RULE_BONUS, "上限加成");
        IdentityModeRuleRequest copy = new IdentityModeRuleRequest();
        copy.setPlayerCount(playerCount);
        copy.setIdentityName(identityName);
        copy.setQuantity(quantity);
        copy.setGeneralPoolSize(generalPoolSize);
        copy.setIsLeader(Boolean.TRUE.equals(rule.getIsLeader()));
        copy.setIdentityVisible(Boolean.TRUE.equals(rule.getIdentityVisible()));
        copy.setAllowLordGeneral(Boolean.TRUE.equals(rule.getAllowLordGeneral()));
        copy.setSameIdentityGeneralVisible(Boolean.TRUE.equals(rule.getSameIdentityGeneralVisible()));
        copy.setInitialHpBonus(initialHpBonus);
        copy.setMaxHpBonus(maxHpBonus);
        copy.setSortOrder(rule.getSortOrder() == null ? 0 : rule.getSortOrder());
        return copy;
    }

    private String normalizeIdentityName(String identityName) {
        if (identityName == null || identityName.trim().isBlank()) {
            throw new AppException("请填写身份名称");
        }
        String normalized = identityName.trim();
        if (normalized.length() > 20) {
            throw new AppException("身份名称最多 20 个字");
        }
        return normalized;
    }

    private int normalizeRange(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max) {
            throw new AppException(label + "需在 " + min + "-" + max + " 之间");
        }
        return value;
    }

    private void saveRules(Long modeId, List<IdentityModeRuleRequest> rules) {
        int index = 0;
        for (IdentityModeRuleRequest rule : rules) {
            IdentityModeRule entity = new IdentityModeRule();
            entity.setModeId(modeId);
            entity.setPlayerCount(rule.getPlayerCount());
            entity.setIdentityName(rule.getIdentityName());
            entity.setQuantity(rule.getQuantity());
            entity.setGeneralPoolSize(rule.getGeneralPoolSize());
            entity.setIsLeader(Boolean.TRUE.equals(rule.getIsLeader()));
            entity.setIdentityVisible(Boolean.TRUE.equals(rule.getIdentityVisible()));
            entity.setAllowLordGeneral(Boolean.TRUE.equals(rule.getAllowLordGeneral()));
            entity.setSameIdentityGeneralVisible(Boolean.TRUE.equals(rule.getSameIdentityGeneralVisible()));
            entity.setInitialHpBonus(rule.getInitialHpBonus());
            entity.setMaxHpBonus(rule.getMaxHpBonus());
            entity.setSortOrder(rule.getSortOrder() == null ? index : rule.getSortOrder());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            identityModeRuleMapper.insert(entity);
            index += 1;
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
