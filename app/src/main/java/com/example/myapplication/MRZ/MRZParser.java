package com.example.myapplication.MRZ;

import android.util.Log;

import com.example.myapplication.MRZ.MRZInfo;

public class MRZParser {
    public static MRZInfo parseMrz(String mrzStr) {
        String[] lines = mrzStr.trim().split("\\n");
        if (lines.length < 3) {
            return null;
        }

        String line1 = lines[0].replace(" ","").toUpperCase();
        String line2 = lines[1].replace(" ","").toUpperCase();
        String line3 = lines[2].replace(" ","").toUpperCase();

        String countryCode = line1.substring(0,5);
        String numberCardId9 = line1.substring(5,14);
        int numbCardId9CheckDigit = Character.getNumericValue(line1.charAt(14));
        String numberCardId12 = line1.substring(15,27);
        int numberCardId12CheckDigit = Character.getNumericValue(line1.charAt(29));

        String dateOfBirth = line2.substring(0,6);
        int dateOfBirthCheckDigit = Character.getNumericValue(line2.charAt(6));
        char genderChar = line2.charAt(7);
        String expiryDate = line2.substring(8,14);
        int expiryDateCheckDigit = Character.getNumericValue(line2.charAt(14));
        String nationality = line2.substring(15,18);

        String nameRaw = line3.replace("<"," ").trim();
        String fullName = nameRaw.replaceAll(" +"," ");


        if (fullName.isEmpty()) {
            return  null;
        }

        if (!countryCode.equals("IDVNM") || !nationality.equals("VNM") || (genderChar != 'M' && genderChar != 'F')) {
            return null;
        }

        if (!validateCheckDigit(numberCardId9,numbCardId9CheckDigit)
                || !validateCheckDigit(numberCardId12,numberCardId12CheckDigit)
                || !validateCheckDigit(dateOfBirth,dateOfBirthCheckDigit)
                || !validateCheckDigit(expiryDate,expiryDateCheckDigit)
        ) {
            return null;
        }

        String gender = "";
        if (genderChar == 'M') {
            gender = "Male";
        } else {
            gender = "Female";
        }

        return new MRZInfo(dateOfBirth,numberCardId9,fullName,expiryDate,numberCardId12,nationality,gender);

    }

    // Thuật toán checksum MRZ sử dụng trọng số 7-3-1

    private static int charValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'Z') {
            return c-'A'+10;
        }  else
            return 0;
    }

    private static int calculateCheckDigit(String input) {
        int[] weights = {7,3,1};
        int sum = 0;
        for (int i=0;i<input.length();i++) {
            int value = charValue(input.charAt(i));
            sum += value * weights[i%3];
        }
        return sum%10;
    }

    private static boolean validateCheckDigit(String input,int checkDigit) {
        return calculateCheckDigit(input) == checkDigit;
    }
}