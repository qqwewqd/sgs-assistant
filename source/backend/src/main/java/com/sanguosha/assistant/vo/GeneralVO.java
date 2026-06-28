package com.sanguosha.assistant.vo;

import com.sanguosha.assistant.entity.General;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVO {
    private Long id;
    private String name;
    private String imagePath;
    private String faction;
    private Boolean isLord;
    private Boolean startsHidden;
    private Integer initialHp;
    private Integer maxHp;
    private Integer initialArmor;
    private Integer maxArmor;
    private LocalDateTime createdAt;

    public static GeneralVO from(General general) {
        return new GeneralVO(
                general.getId(),
                general.getName(),
                general.getImagePath(),
                general.getFaction(),
                general.getIsLord(),
                general.getStartsHidden(),
                general.getInitialHp(),
                general.getMaxHp(),
                general.getInitialArmor(),
                general.getMaxArmor(),
                general.getCreatedAt()
        );
    }
}
