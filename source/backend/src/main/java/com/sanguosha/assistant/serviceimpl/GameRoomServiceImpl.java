package com.sanguosha.assistant.serviceimpl;

import com.sanguosha.assistant.entity.General;
import com.sanguosha.assistant.entity.IdentityMode;
import com.sanguosha.assistant.entity.IdentityModeRule;
import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.service.AppSettingService;
import com.sanguosha.assistant.service.GameRoomService;
import com.sanguosha.assistant.service.GeneralService;
import com.sanguosha.assistant.service.IdentityModeService;
import com.sanguosha.assistant.store.LocalRoomStore;
import com.sanguosha.assistant.store.PlayerState;
import com.sanguosha.assistant.store.RoomState;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.ResultCode;
import com.sanguosha.assistant.vo.RoomView;
import com.sanguosha.assistant.websocket.RoomSessionManager;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameRoomServiceImpl implements GameRoomService {
    private static final String IDENTITY_LORD = "主公";
    private static final String IDENTITY_LOYALIST = "忠臣";

    private record IdentityAssignment(
            String name,
            boolean leader,
            boolean visible,
            boolean allowLordGeneral,
            boolean sameIdentityGeneralVisible,
            int generalPoolSize,
            int initialHpBonus,
            int maxHpBonus
    ) {
    }

    private final LocalRoomStore roomStore;
    private final GeneralService generalService;
    private final IdentityModeService identityModeService;
    private final AppSettingService appSettingService;
    private final RoomSessionManager roomSessionManager;
    private final SecureRandom random = new SecureRandom();

    @Value("${sgs.room.ttl-hours}")
    private long roomTtlHours;

    @Override
    public synchronized RoomView createRoom(Long modeId, AuthUser user) {
        IdentityMode mode = identityModeService.requireEnabledMode(modeId);
        String roomCode = generateRoomCode();
        RoomState room = new RoomState();
        room.setRoomCode(roomCode);
        room.setOwnerUserId(user.getId());
        room.setIdentityModeId(mode.getId());
        room.setIdentityModeName(mode.getName());
        room.getPlayers().add(newPlayer(user, true));
        saveRoom(room, false);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView joinRoom(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState existed = findPlayer(room, user.getId());
        if (existed != null) {
            existed.setOnline(true);
            saveRoom(room, true);
            return toView(room, user.getId());
        }
        if (!RoomState.WAITING.equals(room.getStatus())) {
            throw new AppException("对局已开始，不能中途加入");
        }
        if (room.getPlayers().size() >= 10) {
            throw new AppException("房间最多支持 10 人");
        }
        room.getPlayers().add(newPlayer(user, false));
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public RoomView getRoom(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        requirePlayer(room, user.getId());
        saveRoom(room, false);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView startGame(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        ensureOwner(room, user);
        if (!RoomState.WAITING.equals(room.getStatus())) {
            throw new AppException("当前状态不能开始发牌");
        }
        int playerCount = room.getPlayers().size();
        if (playerCount < 2) {
            throw new AppException("至少 2 名玩家才能开始");
        }
        if (!allPlayersOnline(room)) {
            throw new AppException("有玩家离线，不能开始发牌");
        }

        List<IdentityAssignment> identities = new ArrayList<>(identityAssignments(room, playerCount));
        Collections.shuffle(identities, random);
        int requiredGeneralCount = identities.stream().mapToInt(IdentityAssignment::generalPoolSize).sum();
        List<General> generals = new ArrayList<>(generalService.allGenerals());
        if (generals.size() < requiredGeneralCount) {
            throw new AppException("武将数量不足，需要至少 " + requiredGeneralCount + " 张");
        }
        Collections.shuffle(generals, random);
        int generalCursor = 0;
        room.setCrownPrinceUserId(null);
        room.setCrownPrinceAppointed(false);
        for (int i = 0; i < playerCount; i++) {
            PlayerState player = room.getPlayers().get(i);
            IdentityAssignment identity = identities.get(i);
            applyIdentity(player, identity);
            player.setLocked(false);
            player.setDead(false);
            player.setGeneralRevealed(false);
            clearVitals(player);
            clearMarkers(player);
            clearCardStatus(player);
            player.setSelectedGeneral(null);
            player.setExtraGenerals(new ArrayList<>());
            int poolEnd = generalCursor + identity.generalPoolSize();
            player.setGeneralPool(new ArrayList<>(generals.subList(generalCursor, poolEnd).stream().map(this::toCard).toList()));
            generalCursor = poolEnd;
        }
        room.setStatus(RoomState.SELECTING);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView chooseGeneral(String roomCode, Long generalId, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.SELECTING.equals(room.getStatus())) {
            throw new AppException("当前不是选将阶段");
        }
        if (player.isLocked()) {
            throw new AppException("已经锁定出阵，不能更换武将");
        }
        RoomView.GeneralCard selected = findInPool(player.getGeneralPool(), generalId);
        if (selected == null && canUseLordGeneral(player)) {
            General general = generalService.requireGeneral(generalId);
            if (!Boolean.TRUE.equals(general.getIsLord())) {
                throw new AppException("该武将未标记为特殊身份可选");
            }
            Set<Long> usedByOthers = usedGeneralIds(room, player.getUserId());
            if (usedByOthers.contains(generalId)) {
                throw new AppException("该武将已进入其他玩家盲选池，不能重复选择");
            }
            selected = toCard(general);
        }
        if (selected == null && appSettingService.isManualPickEnabled()) {
            ensureManualPickAllowed(room, player);
            General general = generalService.requireGeneral(generalId);
            if (manualPickUnavailableIds(room, player.getUserId()).contains(generalId)) {
                throw new AppException("该武将已在场上或盲选池中，不能重复选择");
            }
            selected = toCard(general);
        }
        if (selected == null) {
            throw new AppException("只能从自己的 6 张盲选将中选择");
        }
        player.setSelectedGeneral(selected);
        player.setGeneralRevealed(false);
        applyConfiguredVitals(player, selected);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView lockGeneral(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.SELECTING.equals(room.getStatus())) {
            throw new AppException("当前不是选将阶段");
        }
        if (player.getSelectedGeneral() == null) {
            throw new AppException("请先选择出阵武将");
        }
        applyConfiguredVitals(player, player.getSelectedGeneral());
        player.setLocked(true);
        player.setGeneralRevealed(!startsHidden(player.getSelectedGeneral()));
        if (room.getPlayers().stream().allMatch(PlayerState::isLocked)) {
            room.setStatus(RoomState.PLAYING);
        }
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView revealGeneral(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        if (player.getSelectedGeneral() == null) {
            throw new AppException("请先选择出阵武将");
        }
        player.setGeneralRevealed(true);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView drawExtraGeneral(String roomCode, String faction, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        String normalizedFaction = normalizeFaction(faction);
        Set<Long> usedIds = activeGeneralIds(room);
        List<General> candidates = generalService.allGenerals().stream()
                .filter(general -> normalizedFaction == null || normalizedFaction.equals(normalizeFaction(general.getFaction())))
                .filter(general -> !usedIds.contains(general.getId()))
                .toList();
        if (candidates.isEmpty()) {
            throw new AppException("当前条件下没有可抽取的武将");
        }
        General drawn = candidates.get(random.nextInt(candidates.size()));
        if (player.getExtraGenerals() == null) {
            player.setExtraGenerals(new ArrayList<>());
        }
        player.getExtraGenerals().add(toExtraCard(drawn, false));
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView revealExtraGeneral(String roomCode, int index, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        RoomView.ExtraGeneralCard card = requireExtraGeneral(player, index);
        card.setRevealed(true);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView removeExtraGeneral(String roomCode, int index, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        requireExtraGeneral(player, index);
        player.getExtraGenerals().remove(index);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView markDead(String roomCode, Long targetUserId, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState requester = requirePlayer(room, user.getId());
        Long actualTarget = targetUserId == null ? user.getId() : targetUserId;
        if (!actualTarget.equals(user.getId()) && !requester.isOwner()) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
        PlayerState target = requirePlayer(room, actualTarget);
        boolean newlyDead = !target.isDead();
        target.setDead(true);
        if (newlyDead) {
            handleCrownPrinceDeath(room, target);
        }
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView updateVitals(String roomCode, Integer currentHp, Integer maxHp, Integer currentArmor, Integer maxArmor, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        if (player.getSelectedGeneral() == null) {
            throw new AppException("请先选择出阵武将");
        }
        validateVitals(currentHp, maxHp, currentArmor, maxArmor);
        boolean initializingVitals = !hasVitals(player);
        boolean shouldPersistTemplate = initializingVitals && !hasConfiguredVitals(player.getSelectedGeneral());
        if (shouldPersistTemplate) {
            generalService.updateGeneralVitals(player.getSelectedGeneral().getId(), currentHp, maxHp, currentArmor);
            applyVitalsToSelectedGeneral(player, currentHp, maxHp, currentArmor);
        }
        int initialBonus = initializingVitals ? initialHpBonus(player) : 0;
        int maxBonus = initializingVitals ? maxHpBonus(player) : 0;
        player.setCurrentHp(currentHp + initialBonus);
        player.setMaxHp(maxHp + maxBonus);
        player.setCurrentArmor(currentArmor);
        player.setMaxArmor(null);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView updateMarker(String roomCode, Long targetUserId, String markerName, Integer markerCount, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        PlayerState target = requirePlayer(room, targetUserId);
        String normalizedName = normalizeMarkerName(markerName);
        int normalizedCount = normalizeMarkerCount(markerCount);
        if (target.getMarkers() == null) {
            target.setMarkers(new ArrayList<>());
        }
        RoomView.MarkerView marker = findMarker(target.getMarkers(), normalizedName);
        if (normalizedCount == 0) {
            if (marker != null) {
                target.getMarkers().remove(marker);
            }
        } else if (marker == null) {
            marker = new RoomView.MarkerView();
            marker.setName(normalizedName);
            marker.setCount(normalizedCount);
            target.getMarkers().add(marker);
        } else {
            marker.setCount(normalizedCount);
        }
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView updateOwnStatus(String roomCode, Boolean chained, Boolean turnedOver, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        if (chained != null) {
            player.setChained(chained);
        }
        if (turnedOver != null) {
            player.setTurnedOver(turnedOver);
        }
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView appointCrownPrince(String roomCode, Long targetUserId, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState leader = requirePlayer(room, user.getId());
        if (!RoomState.PLAYING.equals(room.getStatus())) {
            throw new AppException("当前不是对局阶段");
        }
        if (!appSettingService.isCrownPrinceEnabled()) {
            throw new AppException("主公立储功能未开启");
        }
        if (!isLeaderIdentity(leader) || leader.isDead()) {
            throw new AppException("只有存活主身份玩家可以立储");
        }
        if (room.isCrownPrinceAppointed() || room.getCrownPrinceUserId() != null) {
            throw new AppException("本局已经立过储君");
        }
        PlayerState target = requirePlayer(room, targetUserId);
        if (target.getUserId().equals(leader.getUserId())) {
            throw new AppException("不能立自己为储君");
        }
        if (target.isDead()) {
            throw new AppException("不能立阵亡玩家为储君");
        }
        room.setCrownPrinceUserId(target.getUserId());
        room.setCrownPrinceAppointed(true);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView restart(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        ensureOwner(room, user);
        for (PlayerState player : room.getPlayers()) {
            clearIdentity(player);
            player.setLocked(false);
            player.setDead(false);
            player.setGeneralRevealed(false);
            clearVitals(player);
            clearMarkers(player);
            clearCardStatus(player);
            player.setSelectedGeneral(null);
            player.setExtraGenerals(new ArrayList<>());
            player.setGeneralPool(new ArrayList<>());
        }
        room.setCrownPrinceUserId(null);
        room.setCrownPrinceAppointed(false);
        room.setStatus(RoomState.WAITING);
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized RoomView leave(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!RoomState.WAITING.equals(room.getStatus())) {
            player.setOnline(false);
            saveRoom(room, true);
            return toView(room, user.getId());
        }
        room.getPlayers().removeIf(item -> item.getUserId().equals(user.getId()));
        if (room.getPlayers().isEmpty()) {
            roomStore.delete(roomCode);
            roomSessionManager.broadcastRoomUpdated(roomCode);
            return toView(room, user.getId());
        }
        if (player.isOwner()) {
            PlayerState nextOwner = room.getPlayers().get(0);
            nextOwner.setOwner(true);
            room.setOwnerUserId(nextOwner.getUserId());
        }
        saveRoom(room, true);
        return toView(room, user.getId());
    }

    @Override
    public synchronized void dissolve(String roomCode, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        ensureOwner(room, user);
        if (!roomStore.delete(roomCode)) {
            throw new AppException(ResultCode.ROOM_NOT_FOUND);
        }
        roomSessionManager.broadcastRoomUpdated(roomCode);
    }

    @Override
    public List<RoomView.GeneralCard> safeLordGenerals(String roomCode, String keyword, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        if (!canUseLordGeneral(player)) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
        Set<Long> dealtGeneralIds = dealtGeneralIds(room);
        Set<Long> usedByOthers = usedGeneralIds(room, player.getUserId());
        return generalService.listLordCards(keyword).stream()
                .filter(card -> !dealtGeneralIds.contains(card.getId()))
                .filter(card -> !usedByOthers.contains(card.getId()))
                .toList();
    }

    @Override
    public List<RoomView.GeneralCard> manualPickGenerals(String roomCode, String keyword, AuthUser user) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = requirePlayer(room, user.getId());
        ensureManualPickAllowed(room, player);
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        Set<Long> unavailableIds = manualPickUnavailableIds(room, player.getUserId());
        return generalService.allGenerals().stream()
                .filter(general -> !unavailableIds.contains(general.getId()))
                .filter(general -> normalizedKeyword == null || general.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .limit(50)
                .map(this::toCard)
                .toList();
    }

    @Override
    public void touch(String roomCode) {
        RoomState room = requireRoom(roomCode);
        saveRoom(room, false);
    }

    @Override
    public synchronized void setOnline(String roomCode, Long userId, boolean online) {
        RoomState room = requireRoom(roomCode);
        PlayerState player = findPlayer(room, userId);
        if (player == null) {
            return;
        }
        if (player.isOnline() != online) {
            player.setOnline(online);
            saveRoom(room, true);
        } else {
            saveRoom(room, false);
        }
    }

    private PlayerState newPlayer(AuthUser user, boolean owner) {
        PlayerState player = new PlayerState();
        player.setUserId(user.getId());
        player.setUsername(user.getUsername());
        player.setOwner(owner);
        player.setOnline(true);
        return player;
    }

    private String generateRoomCode() {
        for (int i = 0; i < 100; i++) {
            int number = random.nextBoolean()
                    ? 1000 + random.nextInt(9000)
                    : 10000 + random.nextInt(90000);
            String roomCode = String.valueOf(number);
            if (!roomStore.exists(roomCode)) {
                return roomCode;
            }
        }
        throw new AppException("房间号池繁忙，请稍后重试");
    }

    private RoomState requireRoom(String roomCode) {
        if (roomCode == null || !roomCode.matches("\\d{4,5}")) {
            throw new AppException(ResultCode.ROOM_NOT_FOUND);
        }
        RoomState room = roomStore.get(roomCode);
        if (room == null) {
            throw new AppException(ResultCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    private void saveRoom(RoomState room, boolean notify) {
        room.setUpdatedAt(Instant.now());
        roomStore.put(room.getRoomCode(), room, Duration.ofHours(roomTtlHours));
        if (notify) {
            roomSessionManager.broadcastRoomUpdated(room.getRoomCode());
        }
    }

    private PlayerState requirePlayer(RoomState room, Long userId) {
        PlayerState player = findPlayer(room, userId);
        if (player == null) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
        return player;
    }

    private PlayerState findPlayer(RoomState room, Long userId) {
        return room.getPlayers().stream()
                .filter(player -> player.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private void ensureOwner(RoomState room, AuthUser user) {
        PlayerState player = requirePlayer(room, user.getId());
        if (!player.isOwner()) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
    }

    private boolean allPlayersOnline(RoomState room) {
        return room.getPlayers().stream().allMatch(PlayerState::isOnline);
    }

    private List<IdentityAssignment> identityAssignments(RoomState room, int playerCount) {
        if (room.getIdentityModeId() == null) {
            IdentityMode mode = identityModeService.requireEnabledMode(null);
            room.setIdentityModeId(mode.getId());
            room.setIdentityModeName(mode.getName());
        }
        List<IdentityModeRule> rules = identityModeService.rulesForPlayerCount(room.getIdentityModeId(), playerCount);
        List<IdentityAssignment> identities = new ArrayList<>();
        for (IdentityModeRule rule : rules) {
            for (int index = 0; index < rule.getQuantity(); index++) {
                identities.add(new IdentityAssignment(
                        rule.getIdentityName(),
                        Boolean.TRUE.equals(rule.getIsLeader()),
                        Boolean.TRUE.equals(rule.getIdentityVisible()),
                        Boolean.TRUE.equals(rule.getAllowLordGeneral()),
                        Boolean.TRUE.equals(rule.getSameIdentityGeneralVisible()),
                        generalPoolSize(rule.getGeneralPoolSize()),
                        valueOrZero(rule.getInitialHpBonus()),
                        valueOrZero(rule.getMaxHpBonus())
                ));
            }
        }
        return identities;
    }

    private int generalPoolSize(Integer value) {
        return value == null || value < 1 ? 2 : value;
    }

    private void applyIdentity(PlayerState player, IdentityAssignment identity) {
        player.setIdentity(identity.name());
        player.setIdentityLeader(identity.leader());
        player.setIdentityVisibleRule(identity.visible());
        player.setAllowLordGeneral(identity.allowLordGeneral());
        player.setSameIdentityGeneralVisible(identity.sameIdentityGeneralVisible());
        player.setInitialHpBonus(identity.initialHpBonus());
        player.setMaxHpBonus(identity.maxHpBonus());
    }

    private void clearIdentity(PlayerState player) {
        player.setIdentity(null);
        player.setIdentityLeader(false);
        player.setIdentityVisibleRule(false);
        player.setAllowLordGeneral(false);
        player.setSameIdentityGeneralVisible(false);
        player.setInitialHpBonus(0);
        player.setMaxHpBonus(0);
    }

    private void applyConfiguredVitals(PlayerState player, RoomView.GeneralCard selected) {
        if (selected == null || !hasConfiguredVitals(selected)) {
            clearVitals(player);
            return;
        }
        player.setCurrentHp(selected.getInitialHp() + initialHpBonus(player));
        player.setMaxHp(selected.getMaxHp() + maxHpBonus(player));
        player.setCurrentArmor(selected.getInitialArmor());
        player.setMaxArmor(null);
    }

    private int initialHpBonus(PlayerState player) {
        return usesLegacyLordRule(player) ? 1 : valueOrZero(player.getInitialHpBonus());
    }

    private int maxHpBonus(PlayerState player) {
        return usesLegacyLordRule(player) ? 1 : valueOrZero(player.getMaxHpBonus());
    }

    private boolean isLeaderIdentity(PlayerState player) {
        return player != null && (player.isIdentityLeader() || usesLegacyLordRule(player));
    }

    private boolean canUseLordGeneral(PlayerState player) {
        return player != null && (player.isAllowLordGeneral() || usesLegacyLordRule(player));
    }

    private boolean canSeeSameIdentityGeneral(PlayerState viewer, PlayerState target) {
        return viewer != null
                && target != null
                && !viewer.getUserId().equals(target.getUserId())
                && viewer.isSameIdentityGeneralVisible()
                && viewer.getIdentity() != null
                && viewer.getIdentity().equals(target.getIdentity())
                && (target.getSelectedGeneral() != null || target.getGeneralPool() != null && !target.getGeneralPool().isEmpty());
    }

    private boolean canManualPick(RoomState room, PlayerState player) {
        return room != null
                && RoomState.SELECTING.equals(room.getStatus())
                && player != null
                && !player.isLocked()
                && !isLeaderIdentity(player)
                && appSettingService.isManualPickEnabled();
    }

    private boolean canAppointCrownPrince(RoomState room, PlayerState player) {
        return room != null
                && RoomState.PLAYING.equals(room.getStatus())
                && player != null
                && !player.isDead()
                && !room.isCrownPrinceAppointed()
                && room.getCrownPrinceUserId() == null
                && !hasFormerDeadLeader(room)
                && isLeaderIdentity(player)
                && appSettingService.isCrownPrinceEnabled();
    }

    private boolean hasFormerDeadLeader(RoomState room) {
        return room.getPlayers().stream()
                .anyMatch(player -> player.isDead()
                        && IDENTITY_LORD.equals(player.getIdentity())
                        && !player.isIdentityLeader());
    }

    private void handleCrownPrinceDeath(RoomState room, PlayerState target) {
        if (!appSettingService.isCrownPrinceEnabled() || room.getCrownPrinceUserId() == null) {
            return;
        }
        room.setCrownPrinceAppointed(true);
        if (isLeaderIdentity(target)) {
            resolveLeaderDeath(room, target);
            return;
        }
        if (target.getUserId().equals(room.getCrownPrinceUserId())) {
            PlayerState leader = findAliveLeader(room);
            if (leader != null) {
                reduceCurrentHp(leader, 1);
            }
            room.setCrownPrinceUserId(null);
        }
    }

    private void resolveLeaderDeath(RoomState room, PlayerState deadLeader) {
        PlayerState successor = findPlayer(room, room.getCrownPrinceUserId());
        if (successor != null && !successor.isDead() && IDENTITY_LOYALIST.equals(successor.getIdentity())) {
            promoteToLeader(deadLeader, successor);
        }
        room.setCrownPrinceUserId(null);
    }

    private void promoteToLeader(PlayerState deadLeader, PlayerState successor) {
        deadLeader.setIdentityLeader(false);
        deadLeader.setAllowLordGeneral(false);
        successor.setIdentity(IDENTITY_LORD);
        successor.setIdentityLeader(true);
        successor.setIdentityVisibleRule(true);
        successor.setAllowLordGeneral(true);
        successor.setSameIdentityGeneralVisible(false);
        successor.setInitialHpBonus(valueOrZero(successor.getInitialHpBonus()) + 1);
        successor.setMaxHpBonus(valueOrZero(successor.getMaxHpBonus()) + 1);
        if (successor.getMaxHp() != null) {
            successor.setMaxHp(successor.getMaxHp() + 1);
        }
        if (successor.getCurrentHp() != null) {
            successor.setCurrentHp(successor.getCurrentHp() + 1);
        }
    }

    private PlayerState findAliveLeader(RoomState room) {
        return room.getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(this::isLeaderIdentity)
                .findFirst()
                .orElse(null);
    }

    private void reduceCurrentHp(PlayerState player, int amount) {
        if (player.getCurrentHp() != null) {
            player.setCurrentHp(Math.max(0, player.getCurrentHp() - amount));
        }
    }

    private void ensureManualPickAllowed(RoomState room, PlayerState player) {
        if (!RoomState.SELECTING.equals(room.getStatus())) {
            throw new AppException("当前不是选将阶段");
        }
        if (player.isLocked()) {
            throw new AppException("已经锁定出阵，不能点将");
        }
        if (!appSettingService.isManualPickEnabled()) {
            throw new AppException("点将功能未开启");
        }
        if (isLeaderIdentity(player)) {
            throw new AppException("主身份玩家不能使用点将");
        }
    }

    private boolean isIdentityPublic(PlayerState player) {
        return player != null && (player.isIdentityVisibleRule() || isLeaderIdentity(player));
    }

    private boolean usesLegacyLordRule(PlayerState player) {
        return player != null
                && IDENTITY_LORD.equals(player.getIdentity())
                && !player.isIdentityLeader()
                && !player.isIdentityVisibleRule()
                && !player.isAllowLordGeneral()
                && valueOrZero(player.getInitialHpBonus()) == 0
                && valueOrZero(player.getMaxHpBonus()) == 0;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasConfiguredVitals(RoomView.GeneralCard card) {
        return card.getInitialHp() != null
                && card.getMaxHp() != null
                && card.getInitialArmor() != null;
    }

    private boolean hasVitals(PlayerState player) {
        return player.getCurrentHp() != null
                && player.getMaxHp() != null
                && player.getCurrentArmor() != null;
    }

    private void applyVitalsToSelectedGeneral(PlayerState player, Integer initialHp, Integer maxHp, Integer initialArmor) {
        RoomView.GeneralCard selected = player.getSelectedGeneral();
        if (selected == null) {
            return;
        }
        applyVitalsToCard(selected, initialHp, maxHp, initialArmor);
        for (RoomView.GeneralCard card : player.getGeneralPool()) {
            if (selected.getId().equals(card.getId())) {
                applyVitalsToCard(card, initialHp, maxHp, initialArmor);
            }
        }
    }

    private void applyVitalsToCard(RoomView.GeneralCard card, Integer initialHp, Integer maxHp, Integer initialArmor) {
        card.setInitialHp(initialHp);
        card.setMaxHp(maxHp);
        card.setInitialArmor(initialArmor);
        card.setMaxArmor(null);
    }

    private void clearVitals(PlayerState player) {
        player.setCurrentHp(null);
        player.setMaxHp(null);
        player.setCurrentArmor(null);
        player.setMaxArmor(null);
    }

    private void clearMarkers(PlayerState player) {
        player.setMarkers(new ArrayList<>());
    }

    private void clearCardStatus(PlayerState player) {
        player.setChained(false);
        player.setTurnedOver(false);
    }

    private void validateVitals(Integer currentHp, Integer maxHp, Integer currentArmor, Integer maxArmor) {
        if (currentHp == null || maxHp == null || currentArmor == null) {
            throw new AppException("请填写血量和护甲");
        }
        if (maxHp < 1 || maxHp > 99) {
            throw new AppException("血量上限需在 1-99 之间");
        }
        if (currentHp < 0 || currentHp > maxHp) {
            throw new AppException("当前血量需在 0 到血量上限之间");
        }
        if (currentArmor < 0 || currentArmor > 99) {
            throw new AppException("当前护甲需在 0-99 之间");
        }
    }

    private String normalizeMarkerName(String markerName) {
        if (markerName == null || markerName.trim().isBlank()) {
            throw new AppException("请填写标记名称");
        }
        String normalized = markerName.trim();
        if (normalized.length() > 12) {
            throw new AppException("标记名称最多 12 个字");
        }
        return normalized;
    }

    private int normalizeMarkerCount(Integer markerCount) {
        if (markerCount == null) {
            throw new AppException("请填写标记数量");
        }
        if (markerCount < 0 || markerCount > 999) {
            throw new AppException("标记数量需在 0-999 之间");
        }
        return markerCount;
    }

    private RoomView.MarkerView findMarker(List<RoomView.MarkerView> markers, String name) {
        return markers.stream()
                .filter(marker -> marker.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private RoomView.GeneralCard toCard(General general) {
        RoomView.GeneralCard card = new RoomView.GeneralCard();
        card.setId(general.getId());
        card.setName(general.getName());
        card.setImagePath(general.getImagePath());
        card.setFaction(general.getFaction());
        card.setIsLord(general.getIsLord());
        card.setStartsHidden(general.getStartsHidden());
        card.setInitialHp(general.getInitialHp());
        card.setMaxHp(general.getMaxHp());
        card.setInitialArmor(general.getInitialArmor());
        card.setMaxArmor(general.getMaxArmor());
        return card;
    }

    private RoomView.ExtraGeneralCard toExtraCard(General general, boolean revealed) {
        RoomView.ExtraGeneralCard card = new RoomView.ExtraGeneralCard();
        card.setId(general.getId());
        card.setName(general.getName());
        card.setImagePath(general.getImagePath());
        card.setFaction(general.getFaction());
        card.setInitialHp(general.getInitialHp());
        card.setMaxHp(general.getMaxHp());
        card.setInitialArmor(general.getInitialArmor());
        card.setMaxArmor(general.getMaxArmor());
        card.setRevealed(revealed);
        return card;
    }

    private RoomView.GeneralCard copyCard(RoomView.GeneralCard source) {
        if (source == null) {
            return null;
        }
        RoomView.GeneralCard card = new RoomView.GeneralCard();
        card.setId(source.getId());
        card.setName(source.getName());
        card.setImagePath(source.getImagePath());
        card.setFaction(source.getFaction());
        card.setIsLord(source.getIsLord());
        card.setStartsHidden(source.getStartsHidden());
        card.setInitialHp(source.getInitialHp());
        card.setMaxHp(source.getMaxHp());
        card.setInitialArmor(source.getInitialArmor());
        card.setMaxArmor(source.getMaxArmor());
        return card;
    }

    private RoomView.ExtraGeneralCard copyExtraCard(RoomView.ExtraGeneralCard source, boolean visible) {
        if (source == null) {
            return null;
        }
        RoomView.ExtraGeneralCard card = new RoomView.ExtraGeneralCard();
        card.setId(visible ? source.getId() : null);
        card.setName(visible ? source.getName() : null);
        card.setImagePath(visible ? source.getImagePath() : null);
        card.setFaction(visible ? source.getFaction() : null);
        card.setInitialHp(visible ? source.getInitialHp() : null);
        card.setMaxHp(visible ? source.getMaxHp() : null);
        card.setInitialArmor(visible ? source.getInitialArmor() : null);
        card.setMaxArmor(visible ? source.getMaxArmor() : null);
        card.setRevealed(source.isRevealed());
        return card;
    }

    private RoomView.MarkerView copyMarker(RoomView.MarkerView source) {
        RoomView.MarkerView marker = new RoomView.MarkerView();
        marker.setName(source.getName());
        marker.setCount(source.getCount());
        return marker;
    }

    private boolean startsHidden(RoomView.GeneralCard card) {
        return card != null && Boolean.TRUE.equals(card.getStartsHidden());
    }

    private RoomView.GeneralCard findInPool(List<RoomView.GeneralCard> pool, Long generalId) {
        if (generalId == null || pool == null) {
            return null;
        }
        return pool.stream().filter(card -> card.getId().equals(generalId)).findFirst().map(this::copyCard).orElse(null);
    }

    private Set<Long> usedGeneralIds(RoomState room, Long exceptUserId) {
        Set<Long> ids = new HashSet<>();
        for (PlayerState player : room.getPlayers()) {
            if (player.getUserId().equals(exceptUserId)) {
                continue;
            }
            for (RoomView.GeneralCard card : player.getGeneralPool()) {
                ids.add(card.getId());
            }
            if (player.getSelectedGeneral() != null) {
                ids.add(player.getSelectedGeneral().getId());
            }
            addExtraGeneralIds(ids, player);
        }
        return ids;
    }

    private Set<Long> activeGeneralIds(RoomState room) {
        Set<Long> ids = new HashSet<>();
        for (PlayerState player : room.getPlayers()) {
            if (player.getSelectedGeneral() != null) {
                ids.add(player.getSelectedGeneral().getId());
            }
            addExtraGeneralIds(ids, player);
        }
        return ids;
    }

    private Set<Long> manualPickUnavailableIds(RoomState room, Long exceptUserId) {
        Set<Long> ids = dealtGeneralIds(room);
        ids.addAll(usedGeneralIds(room, exceptUserId));
        return ids;
    }

    private void addExtraGeneralIds(Set<Long> ids, PlayerState player) {
        if (player.getExtraGenerals() == null) {
            player.setExtraGenerals(new ArrayList<>());
            return;
        }
        for (RoomView.ExtraGeneralCard card : player.getExtraGenerals()) {
            if (card.getId() != null) {
                ids.add(card.getId());
            }
        }
    }

    private RoomView.ExtraGeneralCard requireExtraGeneral(PlayerState player, int index) {
        if (player.getExtraGenerals() == null) {
            player.setExtraGenerals(new ArrayList<>());
        }
        if (index < 0 || index >= player.getExtraGenerals().size()) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        return player.getExtraGenerals().get(index);
    }

    private String normalizeFaction(String faction) {
        if (faction == null || faction.trim().isBlank()) {
            return null;
        }
        return faction.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private Set<Long> dealtGeneralIds(RoomState room) {
        Set<Long> ids = new HashSet<>();
        for (PlayerState player : room.getPlayers()) {
            for (RoomView.GeneralCard card : player.getGeneralPool()) {
                ids.add(card.getId());
            }
        }
        return ids;
    }

    private RoomView toView(RoomState room, Long viewerId) {
        PlayerState viewer = findPlayer(room, viewerId);
        RoomView view = new RoomView();
        view.setRoomCode(room.getRoomCode());
        view.setStatus(room.getStatus());
        view.setOwnerUserId(room.getOwnerUserId());
        view.setIdentityModeId(room.getIdentityModeId());
        view.setIdentityModeName(room.getIdentityModeName());
        view.setOwner(viewer != null && viewer.isOwner());
        view.setCanStart(viewer != null && viewer.isOwner()
                && RoomState.WAITING.equals(room.getStatus())
                && room.getPlayers().size() >= 2
                && allPlayersOnline(room));
        view.setCanRestart(viewer != null && viewer.isOwner());
        view.setManualPickEnabled(appSettingService.isManualPickEnabled());
        boolean crownPrinceEnabled = appSettingService.isCrownPrinceEnabled();
        view.setCrownPrinceEnabled(crownPrinceEnabled);
        view.setCrownPrinceUserId(crownPrinceEnabled ? room.getCrownPrinceUserId() : null);
        view.setUpdatedAt(room.getUpdatedAt());

        for (PlayerState player : room.getPlayers()) {
            RoomView.PlayerView playerView = new RoomView.PlayerView();
            playerView.setUserId(player.getUserId());
            playerView.setUsername(player.getUsername());
            playerView.setOwner(player.isOwner());
            playerView.setOnline(player.isOnline());
            playerView.setLocked(player.isLocked());
            playerView.setDead(player.isDead());
            playerView.setCrownPrince(crownPrinceEnabled && player.getUserId().equals(room.getCrownPrinceUserId()));
            playerView.setGeneralRevealed(player.isGeneralRevealed());
            playerView.setChained(player.isChained());
            playerView.setTurnedOver(player.isTurnedOver());
            playerView.setIdentityLeader(isLeaderIdentity(player));
            playerView.setAllowLordGeneral(canUseLordGeneral(player));
            playerView.setCurrentHp(player.getCurrentHp());
            playerView.setMaxHp(player.getMaxHp());
            playerView.setCurrentArmor(player.getCurrentArmor());
            playerView.setMaxArmor(player.getMaxArmor());
            if (player.getMarkers() == null) {
                player.setMarkers(new ArrayList<>());
            }
            playerView.setMarkers(player.getMarkers().stream().map(this::copyMarker).toList());
            boolean identityVisible = isIdentityPublic(player) || player.isDead() || player.getUserId().equals(viewerId);
            boolean publicGeneralVisible = player.isLocked()
                    && (isLeaderIdentity(player) || RoomState.PLAYING.equals(room.getStatus()))
                    && (!startsHidden(player.getSelectedGeneral()) || player.isGeneralRevealed());
            boolean sameIdentityGeneralVisible = canSeeSameIdentityGeneral(viewer, player);
            boolean generalVisible = player.getUserId().equals(viewerId) || publicGeneralVisible || sameIdentityGeneralVisible;
            playerView.setIdentityVisible(identityVisible);
            playerView.setIdentity(identityVisible ? player.getIdentity() : null);
            playerView.setGeneralVisible(generalVisible);
            playerView.setSelectedGeneral(generalVisible ? copyCard(player.getSelectedGeneral()) : null);
            if (sameIdentityGeneralVisible && player.getGeneralPool() != null) {
                playerView.setGeneralPool(player.getGeneralPool().stream().map(this::copyCard).toList());
            }
            if (player.getExtraGenerals() == null) {
                player.setExtraGenerals(new ArrayList<>());
            }
            for (RoomView.ExtraGeneralCard extraGeneral : player.getExtraGenerals()) {
                boolean extraVisible = player.getUserId().equals(viewerId) || extraGeneral.isRevealed();
                playerView.getExtraGenerals().add(copyExtraCard(extraGeneral, extraVisible));
            }
            view.getPlayers().add(playerView);
        }

        if (viewer != null) {
            RoomView.MeView me = new RoomView.MeView();
            me.setUserId(viewer.getUserId());
            me.setUsername(viewer.getUsername());
            me.setIdentity(viewer.getIdentity());
            me.setLocked(viewer.isLocked());
            me.setDead(viewer.isDead());
            me.setCrownPrince(crownPrinceEnabled && viewer.getUserId().equals(room.getCrownPrinceUserId()));
            me.setGeneralRevealed(viewer.isGeneralRevealed());
            me.setChained(viewer.isChained());
            me.setTurnedOver(viewer.isTurnedOver());
            me.setIdentityLeader(isLeaderIdentity(viewer));
            me.setIdentityVisibleRule(isIdentityPublic(viewer));
            me.setAllowLordGeneral(canUseLordGeneral(viewer));
            me.setCanManualPick(canManualPick(room, viewer));
            me.setCanAppointCrownPrince(canAppointCrownPrince(room, viewer));
            me.setInitialHpBonus(initialHpBonus(viewer));
            me.setMaxHpBonus(maxHpBonus(viewer));
            me.setCurrentHp(viewer.getCurrentHp());
            me.setMaxHp(viewer.getMaxHp());
            me.setCurrentArmor(viewer.getCurrentArmor());
            me.setMaxArmor(viewer.getMaxArmor());
            if (viewer.getMarkers() == null) {
                viewer.setMarkers(new ArrayList<>());
            }
            me.setMarkers(viewer.getMarkers().stream().map(this::copyMarker).toList());
            me.setSelectedGeneral(copyCard(viewer.getSelectedGeneral()));
            if (viewer.getExtraGenerals() == null) {
                viewer.setExtraGenerals(new ArrayList<>());
            }
            me.setExtraGenerals(viewer.getExtraGenerals().stream().map(card -> copyExtraCard(card, true)).toList());
            me.setGeneralPool(viewer.getGeneralPool().stream().map(this::copyCard).toList());
            view.setMe(me);
        }
        return view;
    }
}
