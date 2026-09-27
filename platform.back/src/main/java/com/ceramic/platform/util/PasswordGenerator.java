package com.ceramic.platform.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "admin";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("Encoded password for 'admin': " + encodedPassword);
        
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Matches: " + matches);
    }
}