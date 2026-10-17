package com.sanguosha.assistant.vo;

import lombok.Data;
import java.util.List;

@Data
public class AppSettingsRequest {
    private Boolean manualPickEnabled;
    private Boolean crownPrinceEnabled;
    private Boolean generalSwapEnabled;
    private List<String> factions;
}
