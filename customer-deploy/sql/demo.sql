-- Optional fictional local screenshot fixtures. Loaded once by initialize.py --demo.
-- Draft goods have status=0, is_public=2, no external IDs or remote images.
INSERT INTO goods (goods_id,title,description,slides,min_price,max_price,deposit,status,is_public,category_code,brand,model_name,device_type,default_rent_unit,sort_order)
VALUES (900001,'演示相机 A','虚构教学商品，请替换内容和图片后再上架。','',1500,1500,30000,0,2,'demo-camera','DEMO','CAMERA-A','camera','day',10),
(900002,'演示无人机 B','虚构教学商品，请替换内容和图片后再上架。','',2500,2500,50000,0,2,'demo-drone','DEMO','DRONE-B','drone','day',20);
INSERT INTO goods_sku (sku_id,goods_id,cover,title,daily_rent,rent_periods,stock,allow_deposit_free,deposit,allow_buyout,min_rent_days,max_rent_days,installment_enabled,installment_periods)
VALUES (900001,900001,'','标准套装',1500,'3,7,15,30',5,2,30000,0,3,30,0,'1'),
(900002,900002,'','标准套装',2500,'3,7,15,30',3,2,50000,0,3,30,0,'1');

-- Every identity is deliberately invalid for external service use. No payment,
-- preauthorization, Alipay order, user open_id, signing flow or transaction ID exists.
-- source_id is a fictional local list-tracking value, never an external order identity.
-- Keep provider configuration blank and scheduled tasks disabled in this demo instance.
SET @demo_now_ms = CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED);
INSERT INTO `user` (user_id,nickname,uuid,phone,address,created_at,is_blocked)
VALUES (900001,'演示用户甲（虚构）','DEMO-USER-0001','00000000000','演示地址（不存在，仅用于截图）',@demo_now_ms-2592000000,1),
(900002,'演示用户乙（虚构）','DEMO-USER-0002','00000000000','演示地址（不存在，仅用于截图）',@demo_now_ms-1728000000,1);

INSERT INTO rent_order (order_id,sku_id,sku_title,daily_rent,order_no,source_id,quantity,deposit,paid_amount,total_amount,goods_id,goods_cover,goods_title,rent_start,rent_end,rent_days,created_at,user_id,user_phone,user_name,address,status,billing_cycle,current_period,user_uuid,total_periods,first_period_amount,per_period_amount,last_period_amount,remaining_deposit,alipay_status,message,remark,contract_no,contract_version,contract_agreed_at,contract_snapshot_json)
VALUES
(910001,900001,'标准套装',1500,'DEMO-ORDER-0001','DEMO-SOURCE-0001',1,0,0,4500,900001,'','演示相机 A',@demo_now_ms-864000000,@demo_now_ms-604800000,3,@demo_now_ms-950400000,900001,'00000000000','演示用户甲（虚构）','演示地址（不存在，仅用于截图）',0,1,1,'DEMO-USER-0001',1,4500,4500,4500,0,'CLOSED','虚构已关闭订单，无真实支付与授权。','DEMO：仅用于截图，禁止向外部平台同步。',NULL,NULL,NULL,NULL),
(910002,900001,'标准套装',1500,'DEMO-ORDER-0002','DEMO-SOURCE-0002',1,0,4500,4500,900001,'','演示相机 A',@demo_now_ms-604800000,@demo_now_ms-345600000,3,@demo_now_ms-691200000,900001,'00000000000','演示用户甲（虚构）','演示地址（不存在，仅用于截图）',5,1,1,'DEMO-USER-0001',1,4500,4500,4500,0,'FINISHED','虚构已完结订单；金额仅为界面样例。','DEMO：仅用于截图，禁止向外部平台同步。','DEMO-CONTRACT-0002','demo-v1',@demo_now_ms-604800000,'{"demo":true,"notice":"虚构教学快照，不具有真实签署记录"}'),
(910003,900002,'标准套装',2500,'DEMO-ORDER-0003','DEMO-SOURCE-0003',1,0,7500,22500,900002,'','演示无人机 B',@demo_now_ms-86400000,@demo_now_ms+691200000,9,@demo_now_ms-172800000,900002,'00000000000','演示用户乙（虚构）','演示地址（不存在，仅用于截图）',4,2,2,'DEMO-USER-0002',3,7500,7500,7500,0,'RECEIVED','虚构租赁中订单；无任何扣款授权。','DEMO：仅用于截图，禁止向外部平台同步。','DEMO-CONTRACT-0003','demo-v1',@demo_now_ms-172800000,'{"demo":true,"notice":"虚构教学快照，不具有真实签署记录"}');

INSERT INTO installment_plan (id,order_id,period_no,period_amount,buyout_price,plan_pay_time,status)
VALUES (920001,910001,1,'45.00','0',DATE_SUB(NOW(),INTERVAL 10 DAY),0),
(920002,910002,1,'45.00','0',DATE_SUB(NOW(),INTERVAL 7 DAY),1),
(920003,910003,1,'75.00','0',DATE_SUB(NOW(),INTERVAL 1 DAY),1),
(920004,910003,2,'75.00','0',DATE_ADD(NOW(),INTERVAL 2 DAY),0),
(920005,910003,3,'75.00','0',DATE_ADD(NOW(),INTERVAL 5 DAY),0);
INSERT INTO installment_bill (id,order_id,bill_no,period_no,period_total,amount,paid_amount,due_date,paid_at,status,last_error)
VALUES (930001,910001,'DEMO-BILL-0001',1,1,4500,0,DATE_SUB(NOW(),INTERVAL 10 DAY),NULL,'CLOSED','虚构账单，无交易记录'),
(930002,910002,'DEMO-BILL-0002',1,1,4500,4500,DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY),'PAID','虚构已支付展示，不代表真实支付'),
(930003,910003,'DEMO-BILL-0003-1',1,3,7500,7500,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'PAID','虚构已支付展示，不代表真实支付'),
(930004,910003,'DEMO-BILL-0003-2',2,3,7500,0,DATE_ADD(NOW(),INTERVAL 2 DAY),NULL,'WAIT_PAY','无扣款授权，不可发起扣款'),
(930005,910003,'DEMO-BILL-0003-3',3,3,7500,0,DATE_ADD(NOW(),INTERVAL 5 DAY),NULL,'WAIT_PAY','无扣款授权，不可发起扣款');

INSERT INTO order_contract (id,order_id,provider,contract_no,contract_name,status,signed_at,raw_response)
VALUES (940001,910002,'demo','DEMO-CONTRACT-0002','虚构租赁协议（截图样例）','COMPLETED',DATE_SUB(NOW(),INTERVAL 7 DAY),'{"demo":true,"notice":"不存在电子签平台签署流程"}'),
(940002,910003,'demo','DEMO-CONTRACT-0003','虚构租赁协议（截图样例）','INIT',NULL,'{"demo":true,"notice":"电子签功能关闭，没有外部流程或文件"}');

INSERT INTO rent_deposit_deduct_record (id,order_id,order_no,out_aftersale_id,out_request_no,fee_type,reason_code,deduct_amount,before_remaining_deposit,after_remaining_deposit,reason,remark,operator_name,aftersale_status,source_type,need_operation,status,created_at)
VALUES (950001,910002,'DEMO-ORDER-0002','DEMO-AFTERSALE-0001','DEMO-REQUEST-0001','INDEMNITY','DEMO_ONLY',0,0,0,'虚构零金额售后展示','DEMO：没有扣款、授权和外部售后编号。','演示管理员','SUCCESS','MERCHANT','N','SUCCESS',@demo_now_ms-259200000);
INSERT INTO rent_aftersale_sync_snapshot (id,order_id,order_no,out_aftersale_id,source_type,aftersale_type,aftersale_status,finished,apply_time,next_operation_types,need_operation,fee_type,deduct_amount,reason_description,matched_deduct_record_id,match_status,import_status,payload_json,synced_at,imported_at,created_at)
VALUES (950002,910002,'DEMO-ORDER-0002','DEMO-AFTERSALE-0001','MERCHANT','INDEMNITY','SUCCESS',1,DATE_SUB(NOW(),INTERVAL 3 DAY),'','N','INDEMNITY',0,'虚构零金额售后，只供查看详情。',950001,'MATCHED','IMPORTED','{"demo":true,"notice":"本地虚构快照，未查询外部平台"}',NOW(),NOW(),@demo_now_ms-259200000);

INSERT INTO order_operation_log (order_id,order_no,operation_type,operation_desc,status_after,success,operator,created_at)
VALUES (910001,'DEMO-ORDER-0001','REMARK','载入虚构关闭订单，无外部请求','CLOSED',1,'演示管理员',@demo_now_ms-950400000),
(910002,'DEMO-ORDER-0002','REMARK','载入虚构完结订单，无外部请求','FINISHED',1,'演示管理员',@demo_now_ms-345600000),
(910003,'DEMO-ORDER-0003','REMARK','载入虚构租赁中订单，无外部请求','RECEIVED',1,'演示管理员',@demo_now_ms-86400000);
