package com.example.myapplication;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AccessLogResponse {
    @SerializedName("data")
    private List<AccessLog> data;

    @SerializedName("errCode")
    private int errCode;

    @SerializedName("errDesc")
    private String errDesc;

    @SerializedName("message")
    private String message;

    public List<AccessLog> getData() {
        return data;
    }

    public void setData(List<AccessLog> data) {
        this.data = data;
    }

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrDesc() {
        return errDesc;
    }

    public void setErrDesc(String errDesc) {
        this.errDesc = errDesc;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}