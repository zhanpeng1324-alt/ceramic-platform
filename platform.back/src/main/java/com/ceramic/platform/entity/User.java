package com.ceramic.platform.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class User {
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String nickname;
    private String avatar;
    private String role;
    private String email;
    private String phone;
    private String address;
    private String createdAt;
    private String updatedAt;
}
