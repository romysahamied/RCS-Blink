package com.vernu.sms.dtos;

import java.util.List;

public class PullPendingSmsResponseDTO {
    public List<OutboundSmsPayloadDTO> data;
    public int count;
}
