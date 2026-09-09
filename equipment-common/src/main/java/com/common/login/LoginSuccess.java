package com.common.login;

import lombok.Data;

@Data
public class LoginSuccess {

    private String token;

    private LoginUser user;

    public LoginSuccess(String token, LoginUser user) {
        this.token = token;
        this.user = user;
    }
}
