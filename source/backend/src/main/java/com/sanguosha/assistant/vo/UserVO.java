package com.sanguosha.assistant.vo;

import com.sanguosha.assistant.entity.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String role;
    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
