package com.jobera.security;

import java.util.Map;

import com.jobera.entity.User;

public record AccessContext(User user, Map<String, String> vars, Map<String, String[]> params) {

    public Long var(String name) {
        return toId(vars.get(name));
    }

    public Long param(String name) {
        String[] values = params.get(name);
        return values == null || values.length == 0 ? null : toId(values[0]);
    }

    public static Long toId(Object raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.toString().replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 18) {
            return null;
        }
        return Long.valueOf(digits);
    }
}
