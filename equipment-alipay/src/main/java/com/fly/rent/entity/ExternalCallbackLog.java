package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@TableName("external_callback_log")
public class ExternalCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    @TableField("event_type")
    private String eventType;

    @TableField("biz_id")
    private String bizId;

    private String payload;

    private Boolean handled;

    @TableField("created_at")
    private Date createdAt;
}
