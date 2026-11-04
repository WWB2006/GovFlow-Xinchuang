package com.wwb.govflow.dialect;

public enum DialectType {
    MYSQL("MySQL 8.0 标准方言"),
    DAMENG_DM8("达梦 DM8 国产信创方言");

    private final String description;

    DialectType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
