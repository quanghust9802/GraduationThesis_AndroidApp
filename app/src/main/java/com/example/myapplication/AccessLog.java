package com.example.myapplication;

import com.google.gson.annotations.SerializedName;

public class AccessLog {
    @SerializedName("id")
    private int id;

    @SerializedName("userId")
    private Integer userId;

    @SerializedName("accessRequestId")
    private Integer accessRequestId;

    @SerializedName("accessTime")
    private String accessTime;

    @SerializedName("status")
    private Integer status;

    @SerializedName("user")
    private User user;

    @SerializedName("accessRequest")
    private Object accessRequest;

    public static class User {
        @SerializedName("cccdId")
        private String cccdId;

        @SerializedName("username")
        private String username;

        @SerializedName("fullName")
        private String fullName;

        @SerializedName("gender")
        private Integer gender;

        @SerializedName("phoneNumber")
        private String phoneNumber;

        @SerializedName("email")
        private String email;

        @SerializedName("imageUrl")
        private String imageUrl;

        @SerializedName("dateOfBirth")
        private String dateOfBirth;

        @SerializedName("address")
        private String address;

        @SerializedName("userRoleId")
        private Integer userRoleId;

        @SerializedName("createdAt")
        private String createdAt;

        @SerializedName("modifiedAt")
        private String modifiedAt;

        @SerializedName("isDeleted")
        private Boolean isDeleted;

        public String getCccdId() {
            return cccdId;
        }

        public void setCccdId(String cccdId) {
            this.cccdId = cccdId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public Integer getGender() {
            return gender;
        }

        public void setGender(Integer gender) {
            this.gender = gender;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public Integer getUserRoleId() {
            return userRoleId;
        }

        public void setUserRoleId(Integer userRoleId) {
            this.userRoleId = userRoleId;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getModifiedAt() {
            return modifiedAt;
        }

        public void setModifiedAt(String modifiedAt) {
            this.modifiedAt = modifiedAt;
        }

        public Boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getAccessRequestId() {
        return accessRequestId;
    }

    public void setAccessRequestId(Integer accessRequestId) {
        this.accessRequestId = accessRequestId;
    }

    public String getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(String accessTime) {
        this.accessTime = accessTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Object getAccessRequest() {
        return accessRequest;
    }

    public void setAccessRequest(Object accessRequest) {
        this.accessRequest = accessRequest;
    }
}