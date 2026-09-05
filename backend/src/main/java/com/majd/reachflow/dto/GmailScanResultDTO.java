package com.majd.reachflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailScanResultDTO {
    private int scannedCount;
    private int markedAsSentCount;
    private int markedAsRepliedCount;
}
