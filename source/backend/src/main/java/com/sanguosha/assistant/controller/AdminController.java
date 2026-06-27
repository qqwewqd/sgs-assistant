package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.AdminRoomService;
import com.sanguosha.assistant.service.GeneralService;
import com.sanguosha.assistant.service.UserService;
import com.sanguosha.assistant.util.SecurityUtil;
import com.sanguosha.assistant.vo.AdminRoomVO;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.CreateUserRequest;
import com.sanguosha.assistant.vo.GeneralVO;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.Result;
import com.sanguosha.assistant.vo.UserVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService userService;
    private final GeneralService generalService;
    private final AdminRoomService adminRoomService;

    @GetMapping("/users")
    public Result<PageVO<UserVO>> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return Result.success(userService.listUsers(page, pageSize));
    }

    @PostMapping("/users")
    public Result<UserVO> createUser(@RequestBody @Valid CreateUserRequest request) {
        return Result.success(userService.createUser(request));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        if (SecurityUtil.currentUser().getId().equals(id)) {
            throw new AppException("不能删除当前登录账号");
        }
        userService.deleteUser(id);
        return Result.success(null);
    }

    @GetMapping("/generals")
    public Result<PageVO<GeneralVO>> generals(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean lordOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return Result.success(generalService.listGenerals(keyword, lordOnly, page, pageSize));
    }

    @PostMapping(value = "/generals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeneralVO> createGeneral(
            @RequestParam String name,
            @RequestParam(required = false) String faction,
            @RequestParam(defaultValue = "false") Boolean isLord,
            @RequestParam(defaultValue = "false") Boolean startsHidden,
            @RequestParam(required = false) String imageName,
            @RequestPart MultipartFile image
    ) {
        return Result.success(generalService.createGeneral(name, faction, isLord, startsHidden, imageName, image));
    }

    @PutMapping(value = "/generals/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeneralVO> updateGeneral(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String faction,
            @RequestParam(required = false) Boolean isLord,
            @RequestParam(required = false) Boolean startsHidden,
            @RequestParam(required = false) String imageName,
            @RequestPart(required = false) MultipartFile image
    ) {
        return Result.success(generalService.updateGeneral(id, name, faction, isLord, startsHidden, imageName, image));
    }

    @DeleteMapping("/generals/{id}")
    public Result<Void> deleteGeneral(@PathVariable Long id) {
        generalService.deleteGeneral(id);
        return Result.success(null);
    }

    @GetMapping("/rooms")
    public Result<PageVO<AdminRoomVO>> rooms(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return Result.success(adminRoomService.listRooms(page, pageSize));
    }

    @DeleteMapping("/rooms/{roomCode}")
    public Result<Void> dissolveRoom(@PathVariable String roomCode) {
        adminRoomService.dissolveRoom(roomCode);
        return Result.success(null);
    }
}
