package com.example.myapplication.MRZ;

public class MRZInfo {
    public String fullName;
    public String dataOfBirth;
    public String expiryDate;
    public String numberCardId9;
    public String numberCardId12;
    public String gender;
    public String nationality;

    public MRZInfo(String dataOfBirth, String numberCardId9, String fullName, String expiryDate, String numberCardId12, String nationality, String gender) {
        this.dataOfBirth = dataOfBirth;
        this.numberCardId9 = numberCardId9;
        this.fullName = fullName;
        this.expiryDate = expiryDate;
        this.numberCardId12 = numberCardId12;
        this.nationality = nationality;
        this.gender = gender;

    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDataOfBirth() {
        return dataOfBirth;
    }

    public void setDataOfBirth(String dataOfBirth) {
        this.dataOfBirth = dataOfBirth;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getNumberCardId12() {
        return numberCardId12;
    }

    public void setNumberCardId12(String numberCardId12) {
        this.numberCardId12 = numberCardId12;
    }

    public String getNumberCardId9() {
        return numberCardId9;
    }

    public void setNumberCardId9(String numberCardId9) {
        this.numberCardId9 = numberCardId9;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}