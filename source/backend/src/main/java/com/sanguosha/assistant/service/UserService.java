package com.sanguosha.assistant.service;

import com.sanguosha.assistant.entity.User;
import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.vo.AuthVO;
import com.sanguosha.assistant.vo.BootstrapAdminRequest;
import com.sanguosha.assistant.vo.BootstrapStatusVO;
import com.sanguosha.assistant.vo.CreateUserRequest;
import com.sanguosha.assistant.vo.LoginRequest;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.UserVO;

public interface UserService {
    AuthVO login(LoginRequest request);

    UserVO bootstrapAdmin(BootstrapAdminRequest request);

    BootstrapStatusVO bootstrapStatus();

    UserVO current(AuthUser authUser);

    UserVO createUser(CreateUserRequest request);

    PageVO<UserVO> listUsers(Integer page, Integer pageSize);

    void deleteUser(Long id);

    User requireUser(Long id);
}
