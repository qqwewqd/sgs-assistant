package com.sanguosha.assistant.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanguosha.assistant.entity.User;
import com.sanguosha.assistant.mapper.UserMapper;
import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.service.UserService;
import com.sanguosha.assistant.util.JwtUtil;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.AuthVO;
import com.sanguosha.assistant.vo.BootstrapAdminRequest;
import com.sanguosha.assistant.vo.BootstrapStatusVO;
import com.sanguosha.assistant.vo.CreateUserRequest;
import com.sanguosha.assistant.vo.LoginRequest;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.ResultCode;
import com.sanguosha.assistant.vo.UserVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_PLAYER = "player";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${sgs.bootstrap.enabled}")
    private boolean bootstrapEnabled;

    @Value("${sgs.bootstrap.key}")
    private String bootstrapKey;

    @Override
    public AuthVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, normalizeUsername(request.getUsername())));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException("账号或密码错误");
        }
        JwtUtil.TokenPair tokenPair = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthVO(tokenPair.token(), user.getId(), user.getUsername(), user.getRole(), tokenPair.expiresAt());
    }

    @Override
    @Transactional
    public UserVO bootstrapAdmin(BootstrapAdminRequest request) {
        if (!bootstrapEnabled || bootstrapKey == null || bootstrapKey.isBlank()) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
        if (!bootstrapKey.equals(request.getKey())) {
            throw new AppException(ResultCode.FORBIDDEN);
        }
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, ROLE_ADMIN));
        if (adminCount != null && adminCount > 0) {
            throw new AppException("管理员已存在，请关闭 bootstrap 初始化入口");
        }
        CreateUserRequest create = new CreateUserRequest();
        create.setUsername(request.getUsername());
        create.setPassword(request.getPassword());
        create.setRole(ROLE_ADMIN);
        return createUser(create);
    }

    @Override
    public BootstrapStatusVO bootstrapStatus() {
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, ROLE_ADMIN));
        boolean available = bootstrapEnabled
                && bootstrapKey != null
                && !bootstrapKey.isBlank()
                && (adminCount == null || adminCount == 0);
        return new BootstrapStatusVO(available);
    }

    @Override
    public UserVO current(AuthUser authUser) {
        return UserVO.from(requireUser(authUser.getId()));
    }

    @Override
    @Transactional
    public UserVO createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        String role = normalizeRole(request.getRole());
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new AppException("账号已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return UserVO.from(user);
    }

    @Override
    public PageVO<UserVO> listUsers(Integer page, Integer pageSize) {
        Page<User> pageRequest = new Page<>(normalizePage(page), normalizePageSize(pageSize));
        Page<User> result = userMapper.selectPage(
                pageRequest,
                new LambdaQueryWrapper<User>().orderByAsc(User::getId)
        );
        return PageVO.of(
                result.getRecords().stream().map(UserVO::from).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (id == null) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        userMapper.deleteById(id);
    }

    @Override
    public User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new AppException("用户不存在");
        }
        return user;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        return username.trim();
    }

    private long normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!ROLE_ADMIN.equals(normalized) && !ROLE_PLAYER.equals(normalized)) {
            throw new AppException("角色只能是 admin 或 player");
        }
        return normalized;
    }
}
