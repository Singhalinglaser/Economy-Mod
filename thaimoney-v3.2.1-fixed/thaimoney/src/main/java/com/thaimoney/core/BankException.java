package com.thaimoney.core;

/** ข้อผิดพลาดทางธุรกรรม ข้อความเป็นภาษาไทยพร้อมแสดงให้ผู้เล่น */
public class BankException extends Exception {
    public BankException(String message) {
        super(message);
    }
}
