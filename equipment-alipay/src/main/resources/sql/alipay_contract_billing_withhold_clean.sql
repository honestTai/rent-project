-- 支付宝库：电子合同、分期账单、支付宝单期支付、支付宝周期扣款签约能力干净脚本
-- 说明：只包含本次能力所需结构，不包含 dev 环境业务数据，可重复执行。

ALTER TABLE goods_sku
  ADD COLUMN IF NOT EXISTS installment_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否开启分期：1是 0否' AFTER billing_cycle,
  ADD COLUMN IF NOT EXISTS installment_periods VARCHAR(128) NOT NULL DEFAULT '1' COMMENT '可选支付期数，逗号分隔，1表示不分期' AFTER installment_enabled;

CREATE TABLE IF NOT EXISTS installment_plan (
  id INT PRIMARY KEY AUTO_INCREMENT,
  buyout_price VARCHAR(64) NULL,
  period_no BIGINT NOT NULL,
  period_amount VARCHAR(64) NOT NULL,
  plan_pay_time DATETIME NULL,
  order_id INT NOT NULL,
  status INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_installment_plan_order_period (order_id, period_no),
  KEY idx_installment_plan_order (order_id),
  KEY idx_installment_plan_pay_time (plan_pay_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rent_buyout_payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  order_no VARCHAR(128) NOT NULL,
  rent_order_id VARCHAR(128) NOT NULL,
  attr_id INT NULL,
  user_uuid VARCHAR(128) NULL,
  buyout_price INT NOT NULL DEFAULT 0 COMMENT '买断价格，单位分',
  out_installment_order_id VARCHAR(64) NOT NULL COMMENT '支付宝买断分期单外部请求号，同时作为支付 out_trade_no',
  installment_order_id VARCHAR(128) NULL COMMENT '支付宝买断分期单号',
  payment_trade_no VARCHAR(128) NULL COMMENT '支付宝买断支付交易号',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATING',
  last_error VARCHAR(1024) NULL,
  raw_response LONGTEXT NULL,
  raw_notify LONGTEXT NULL,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rent_buyout_payment_order (order_id),
  UNIQUE KEY uk_rent_buyout_payment_out_installment (out_installment_order_id),
  KEY idx_rent_buyout_payment_trade_no (payment_trade_no),
  KEY idx_rent_buyout_payment_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_contract (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'esign',
  contract_no VARCHAR(128) NULL,
  flow_id VARCHAR(128) NULL,
  signer_id VARCHAR(128) NULL,
  file_id VARCHAR(128) NULL,
  contract_name VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  sign_url TEXT NULL,
  view_url TEXT NULL,
  download_url TEXT NULL,
  pdf_url TEXT NULL,
  signed_at DATETIME NULL,
  expire_at DATETIME NULL,
  raw_response LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_order_contract_order (order_id),
  UNIQUE KEY uk_order_contract_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS installment_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  bill_no VARCHAR(128) NOT NULL,
  period_no INT NOT NULL,
  period_total INT NOT NULL,
  amount INT NOT NULL DEFAULT 0,
  paid_amount INT NOT NULL DEFAULT 0,
  due_date DATETIME NULL,
  paid_at DATETIME NULL,
  status VARCHAR(32) NOT NULL,
  payment_trade_no VARCHAR(128) NULL,
  out_trade_no VARCHAR(128) NULL,
  last_error VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_installment_bill_order_period (order_id, period_no),
  UNIQUE KEY uk_installment_bill_no (bill_no),
  UNIQUE KEY uk_installment_bill_out_trade_no (out_trade_no),
  KEY idx_installment_bill_due (status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS withhold_agreement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  user_id VARCHAR(128) NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'alipay',
  agreement_no VARCHAR(128) NULL,
  external_agreement_no VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL,
  sign_url TEXT NULL,
  sign_str TEXT NULL,
  signed_at DATETIME NULL,
  next_deduct_at DATETIME NULL,
  raw_response LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_withhold_order (order_id),
  UNIQUE KEY uk_withhold_agreement_no (agreement_no),
  UNIQUE KEY uk_withhold_external_no (external_agreement_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE withhold_agreement
  ADD COLUMN IF NOT EXISTS sign_str TEXT NULL AFTER sign_url;

CREATE TABLE IF NOT EXISTS external_callback_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider VARCHAR(32) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  biz_id VARCHAR(128) NULL,
  payload LONGTEXT NULL,
  handled TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_external_callback_biz (provider, event_type, biz_id),
  KEY idx_external_callback_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
