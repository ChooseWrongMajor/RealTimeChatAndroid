package com.project_android.realtimechat.models;

import java.io.Serializable;

public class User implements Serializable {
    // Để private để đảm bảo tính đóng gói (Encapsulation)
    public String name, image, email, token, id;
    public boolean isAI;
}