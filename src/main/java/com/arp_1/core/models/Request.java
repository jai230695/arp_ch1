// File: src/main/java/com/arp/core/models/Request.java
package com.arp_1.core.models;

import java.util.Objects;

public class Request {
    private String anaesthetistId;
    private int dayNumber;
    private String requestType; // NA, AM, PM, NC, XM, D, CME, CT, M

    public Request(String anaesthetistId, int dayNumber, String requestType) {
        this.anaesthetistId = anaesthetistId;
        this.dayNumber = dayNumber;
        this.requestType = requestType;
    }

    public boolean isAbsenceRequest() {
        return "NA".equals(requestType);
    }

    public boolean isMorningShiftRequest() {
        return "AM".equals(requestType);
    }

    public boolean isEveningShiftRequest() {
        return "PM".equals(requestType);
    }

    public boolean isNoCallRequest() {
        return "NC".equals(requestType);
    }

    public boolean isExaminationRequest() {
        return "XM".equals(requestType);
    }

    public boolean isDissertationRequest() {
        return "D".equals(requestType);
    }

    public boolean isTeachingRequest() {
        return "CME".equals(requestType);
    }

    public boolean isCardiothoracicRequest() {
        return "CT".equals(requestType);
    }

    public boolean isMeetingRequest() {
        return "M".equals(requestType);
    }

    // Getters
    public String getAnaesthetistId() {
        return anaesthetistId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public String getRequestType() {
        return requestType;
    }

    @Override
    public String toString() {
        return String.format("Request{anaesthetist='%s', day=%d, type='%s'}",
                anaesthetistId, dayNumber, requestType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Request request = (Request) o;
        return dayNumber == request.dayNumber &&
                Objects.equals(anaesthetistId, request.anaesthetistId) &&
                Objects.equals(requestType, request.requestType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anaesthetistId, dayNumber, requestType);
    }
}