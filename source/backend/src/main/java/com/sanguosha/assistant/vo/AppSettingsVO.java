package com.sanguosha.assistant.vo;

import lombok.Data;
import java.util.List;

@Data
public class AppSettingsVO {
    private boolean manualPickEnabled;
    private boolean crownPrinceEnabled;
    private boolean generalSwapEnabled;
    private List<String> factions;
}
