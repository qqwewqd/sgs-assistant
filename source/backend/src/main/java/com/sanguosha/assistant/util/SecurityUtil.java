package com.sanguosha.assistant.util;

import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {
    private SecurityUtil() {
    }

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
        return authUser;
    }
}
