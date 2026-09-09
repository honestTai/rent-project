-- 支付宝租赁买断支付台账，可重复执行。

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
