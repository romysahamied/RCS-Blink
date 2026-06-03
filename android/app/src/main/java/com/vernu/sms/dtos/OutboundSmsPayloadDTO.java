package com.vernu.sms.dtos;

public class OutboundSmsPayloadDTO {
    public String smsId;
    public String smsBatchId;
    public String message;
    public String[] recipients;
    public Integer simSubscriptionId;
    public String channel;
}
