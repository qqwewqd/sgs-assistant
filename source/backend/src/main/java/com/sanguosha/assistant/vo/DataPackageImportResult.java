package com.sanguosha.assistant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataPackageImportResult {
    private int createdGenerals;
    private int updatedGenerals;
    private int createdIdentityModes;
    private int updatedIdentityModes;
    private boolean sharedPackageVerified;
}
