package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.GameRoomService;
import com.sanguosha.assistant.util.SecurityUtil;
import com.sanguosha.assistant.vo.CreateRoomRequest;
import com.sanguosha.assistant.vo.Result;
import com.sanguosha.assistant.vo.RoomActionRequest;
import com.sanguosha.assistant.vo.RoomView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final GameRoomService gameRoomService;

    @PostMapping
    public Result<RoomView> createRoom(@RequestBody(required = false) CreateRoomRequest request) {
        Long modeId = request == null ? null : request.getModeId();
        return Result.success(gameRoomService.createRoom(modeId, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/join")
    public Result<RoomView> joinRoom(@PathVariable String roomCode) {
        return Result.success(gameRoomService.joinRoom(roomCode, SecurityUtil.currentUser()));
    }

    @GetMapping("/{roomCode}")
    public Result<RoomView> room(@PathVariable String roomCode) {
        return Result.success(gameRoomService.getRoom(roomCode, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/start")
    public Result<RoomView> start(@PathVariable String roomCode) {
        return Result.success(gameRoomService.startGame(roomCode, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/choose")
    public Result<RoomView> choose(@PathVariable String roomCode, @RequestBody RoomActionRequest request) {
        return Result.success(gameRoomService.chooseGeneral(roomCode, request.getGeneralId(), SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/lock")
    public Result<RoomView> lock(@PathVariable String roomCode) {
        return Result.success(gameRoomService.lockGeneral(roomCode, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/reveal-general")
    public Result<RoomView> revealGeneral(@PathVariable String roomCode) {
        return Result.success(gameRoomService.revealGeneral(roomCode, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/extra-generals/draw")
    public Result<RoomView> drawExtraGeneral(@PathVariable String roomCode, @RequestBody(required = false) RoomActionRequest request) {
        String faction = request == null ? null : request.getFaction();
        return Result.success(gameRoomService.drawExtraGeneral(roomCode, faction, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/extra-generals/{index}/reveal")
    public Result<RoomView> revealExtraGeneral(@PathVariable String roomCode, @PathVariable int index) {
        return Result.success(gameRoomService.revealExtraGeneral(roomCode, index, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{roomCode}/extra-generals/{index}")
    public Result<RoomView> removeExtraGeneral(@PathVariable String roomCode, @PathVariable int index) {
        return Result.success(gameRoomService.removeExtraGeneral(roomCode, index, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/dead")
    public Result<RoomView> dead(@PathVariable String roomCode, @RequestBody(required = false) RoomActionRequest request) {
        Long targetUserId = request == null ? null : request.getTargetUserId();
        return Result.success(gameRoomService.markDead(roomCode, targetUserId, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/vitals")
    public Result<RoomView> updateVitals(@PathVariable String roomCode, @RequestBody RoomActionRequest request) {
        return Result.success(gameRoomService.updateVitals(
                roomCode,
                request.getCurrentHp(),
                request.getMaxHp(),
                request.getCurrentArmor(),
                request.getMaxArmor(),
                SecurityUtil.currentUser()
        ));
    }

    @PostMapping("/{roomCode}/markers")
    public Result<RoomView> updateMarker(@PathVariable String roomCode, @RequestBody RoomActionRequest request) {
        return Result.success(gameRoomService.updateMarker(
                roomCode,
                request.getTargetUserId(),
                request.getMarkerName(),
                request.getMarkerCount(),
                SecurityUtil.currentUser()
        ));
    }

    @PostMapping("/{roomCode}/status")
    public Result<RoomView> updateOwnStatus(@PathVariable String roomCode, @RequestBody RoomActionRequest request) {
        return Result.success(gameRoomService.updateOwnStatus(
                roomCode,
                request.getChained(),
                request.getTurnedOver(),
                SecurityUtil.currentUser()
        ));
    }

    @PostMapping("/{roomCode}/crown-prince")
    public Result<RoomView> appointCrownPrince(@PathVariable String roomCode, @RequestBody RoomActionRequest request) {
        Long targetUserId = request == null ? null : request.getCrownPrinceUserId();
        if (targetUserId == null && request != null) {
            targetUserId = request.getTargetUserId();
        }
        return Result.success(gameRoomService.appointCrownPrince(roomCode, targetUserId, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/restart")
    public Result<RoomView> restart(@PathVariable String roomCode) {
        return Result.success(gameRoomService.restart(roomCode, SecurityUtil.currentUser()));
    }

    @PostMapping("/{roomCode}/leave")
    public Result<RoomView> leave(@PathVariable String roomCode) {
        return Result.success(gameRoomService.leave(roomCode, SecurityUtil.currentUser()));
    }

    @DeleteMapping("/{roomCode}")
    public Result<Void> dissolve(@PathVariable String roomCode) {
        gameRoomService.dissolve(roomCode, SecurityUtil.currentUser());
        return Result.success(null);
    }

    @GetMapping("/{roomCode}/lord-generals")
    public Result<List<RoomView.GeneralCard>> lordGenerals(
            @PathVariable String roomCode,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(gameRoomService.safeLordGenerals(roomCode, keyword, SecurityUtil.currentUser()));
    }

    @GetMapping("/{roomCode}/manual-pick-generals")
    public Result<List<RoomView.GeneralCard>> manualPickGenerals(
            @PathVariable String roomCode,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(gameRoomService.manualPickGenerals(roomCode, keyword, SecurityUtil.currentUser()));
    }
}
