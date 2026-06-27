package com.sanguosha.assistant.service;

import com.sanguosha.assistant.vo.AdminRoomVO;
import com.sanguosha.assistant.vo.PageVO;

public interface AdminRoomService {
    PageVO<AdminRoomVO> listRooms(Integer page, Integer pageSize);

    void dissolveRoom(String roomCode);
}
