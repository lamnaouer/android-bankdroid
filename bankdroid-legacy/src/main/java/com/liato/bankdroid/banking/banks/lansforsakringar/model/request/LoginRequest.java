package com.liato.bankdroid.banking.banks.lansforsakringar.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

public class LoginRequest {

    private String mSsn;

    private String mPin;


    public LoginRequest(String ssn, String pin) {
        mSsn = ssn;
        mPin = pin;
    }

    @JsonProperty("ssn")
    public String getSsn() {
        return mSsn;
    }

    @JsonSetter("ssn")
    public void setSsn(String s) {
        mSsn = s;
    }

    @JsonProperty("pin")
    public String getPin() {
        return mPin;
    }

    @JsonSetter("pin")
    public void setPin(String p) {
        mPin = p;
    }

}