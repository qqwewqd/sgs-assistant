package com.sanguosha.assistant.service;

import com.sanguosha.assistant.entity.General;
import com.sanguosha.assistant.vo.GeneralVO;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.RoomView;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface GeneralService {
    PageVO<GeneralVO> listGenerals(String keyword, Boolean lordOnly, Integer page, Integer pageSize);

    List<RoomView.GeneralCard> listLordCards(String keyword);

    GeneralVO createGeneral(String name, String faction, Boolean isLord, Boolean startsHidden, String initialHp, String maxHp, String initialArmor, String maxArmor, String imageName, MultipartFile image);

    GeneralVO updateGeneral(Long id, String name, String faction, Boolean isLord, Boolean startsHidden, String initialHp, String maxHp, String initialArmor, String maxArmor, String imageName, MultipartFile image);

    GeneralVO updateGeneralVitals(Long id, Integer initialHp, Integer maxHp, Integer initialArmor);

    void deleteGeneral(Long id);

    General requireGeneral(Long id);

    List<General> allGenerals();
}
