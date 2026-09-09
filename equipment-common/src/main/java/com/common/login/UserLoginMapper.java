package com.common.login;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLoginMapper {

    LoginUser userInfo(LoginParam loginParam);

    Integer userName(LoginParam loginParam);
}
