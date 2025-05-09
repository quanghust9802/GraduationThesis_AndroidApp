package com.example.myapplication;

import java.util.List;

class Registration {
    private int id;
    private Integer userRequestId;
    private Integer userApprovalid;
    private String startTime;
    private String endTime;
    private String purpose;
    private Integer status; // Có thể null
    private String approvalAt;
    private String createdAt;
    private String modifiedAt;
    private Boolean isDeleted;
    private String mrz;
    private String cccdNumber; // Thêm trường này nếu server trả về hoặc cần lưu

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Integer getUserRequestId() { return userRequestId; }
    public void setUserRequestId(Integer userRequestId) { this.userRequestId = userRequestId; }
    public Integer getUserApprovalid() { return userApprovalid; }
    public void setUserApprovalid(Integer userApprovalid) { this.userApprovalid = userApprovalid; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getApprovalAt() { return approvalAt; }
    public void setApprovalAt(String approvalAt) { this.approvalAt = approvalAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public String getMrz() { return mrz; }
    public void setMrz(String mrz) { this.mrz = mrz; }
    public String getCccdNumber() { return cccdNumber; }
    public void setCccdNumber(String cccdNumber) { this.cccdNumber = cccdNumber; }
}

// Định nghĩa lớp wrapper cho response
class RegistrationResponse {
    private int errCode;
    private String errDesc;
    private String message;
    private List<Registration> data;

    public int getErrCode() { return errCode; }
    public void setErrCode(int errCode) { this.errCode = errCode; }
    public String getErrDesc() { return errDesc; }
    public void setErrDesc(String errDesc) { this.errDesc = errDesc; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<Registration> getData() { return data; }
    public void setData(List<Registration> data) { this.data = data; }
}
