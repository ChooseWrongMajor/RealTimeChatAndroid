package com.project_android.realtimechat.utilities;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {

    private final SharedPreferences sharedPreferences;

    // Hàm khởi tạo: Tạo một file xml để lưu trữ dữ liệu dưới dạng key-value
    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(Constants.KEY_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    // Lưu một giá trị kiểu Boolean (Dùng để lưu trạng thái đã đăng nhập hay chưa)
    public void putBoolean(String key, Boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    // Lấy một giá trị kiểu Boolean
    public Boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    // Lưu một giá trị kiểu String (Dùng để lưu Name, Email, UserId...)
    public void putString(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    // Lấy một giá trị kiểu String
    public String getString(String key) {
        return sharedPreferences.getString(key, null);
    }

    // Hàm dùng để xóa dữ liệu khi người dùng Đăng xuất (Sign out)
    public void clear() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}