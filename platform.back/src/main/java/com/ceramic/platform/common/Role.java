package com.ceramic.platform.common;

public enum Role {
    CUSTOMER("customer"),
    SERVICE("service"),
    ADMIN("admin");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Role fromValue(String value) {
        for (Role role : Role.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    public boolean isCustomer() {
        return this == CUSTOMER;
    }

    public boolean isService() {
        return this == SERVICE;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean canAccessAdmin() {
        return this == ADMIN;
    }

    public boolean canAccessService() {
        return this == SERVICE || this == ADMIN;
    }
}