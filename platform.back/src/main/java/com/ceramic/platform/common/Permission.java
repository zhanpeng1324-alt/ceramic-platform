package com.ceramic.platform.common;

import java.util.Arrays;
import java.util.List;

public class Permission {
    public static final String CUSTOMER_READ_OWN = "customer:read_own";
    public static final String CUSTOMER_WRITE_OWN = "customer:write_own";
    public static final String SERVICE_READ_ALL = "service:read_all";
    public static final String SERVICE_WRITE_LIMITED = "service:write_limited";
    public static final String ADMIN_READ_ALL = "admin:read_all";
    public static final String ADMIN_WRITE_ALL = "admin:write_all";

    public static List<String> getCustomerPermissions() {
        return Arrays.asList(CUSTOMER_READ_OWN, CUSTOMER_WRITE_OWN);
    }

    public static List<String> getServicePermissions() {
        return Arrays.asList(CUSTOMER_READ_OWN, CUSTOMER_WRITE_OWN, SERVICE_READ_ALL, SERVICE_WRITE_LIMITED);
    }

    public static List<String> getAdminPermissions() {
        return Arrays.asList(CUSTOMER_READ_OWN, CUSTOMER_WRITE_OWN, SERVICE_READ_ALL, SERVICE_WRITE_LIMITED, ADMIN_READ_ALL, ADMIN_WRITE_ALL);
    }

    public static List<String> getPermissionsByRole(String role) {
        Role r = Role.fromValue(role);
        switch (r) {
            case CUSTOMER:
                return getCustomerPermissions();
            case SERVICE:
                return getServicePermissions();
            case ADMIN:
                return getAdminPermissions();
            default:
                return getCustomerPermissions();
        }
    }
}