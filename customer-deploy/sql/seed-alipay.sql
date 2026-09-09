-- Generic category templates; no business orders, miniapp users or service-provider IDs.

INSERT INTO category (code,parent_code,name,short_name,icon_name,sort_order,status) VALUES
('demo-devices',NULL,'设备示例','设备','AppOutline',10,1),
('demo-camera','demo-devices','拍摄设备','相机','CameraOutline',10,1),
('demo-drone','demo-devices','航拍设备','无人机','TravelOutline',20,1);

INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('return_address_detail','','统一归还地址（详细）','首次部署后按实际业务配置',10,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('return_consignee','','统一归还联系人','首次部署后按实际业务配置',20,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('return_mobile','','统一归还联系电话','首次部署后按实际业务配置',30,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('return_address_json','','统一归还地址JSON','首次部署后按实际业务配置',40,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('service_phone','','客服电话','首次部署后按实际业务配置',0,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('alipay_goods_sync_item_details_page_model','','支付宝商品同步-详情页模式','首次部署后按实际业务配置',110,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('alipay_goods_sync_path_template','','支付宝商品同步-详情页路径模板','首次部署后按实际业务配置',120,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_title','','租赁协议标题','首次部署后按实际业务配置',130,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_subtitle','','租赁协议副标题','首次部署后按实际业务配置',131,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_merchant_name','','租赁协议-出租方名称','首次部署后按实际业务配置',132,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_merchant_credit_code','','租赁协议-统一社会信用代码','首次部署后按实际业务配置',133,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_merchant_legal_representative','','租赁协议-法定代表人','首次部署后按实际业务配置',134,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_merchant_registered_address','','租赁协议-注册地址','首次部署后按实际业务配置',135,NOW());
INSERT INTO `rent_sys_config` (`config_key`,`config_value`,`config_name`,`remark`,`sort`,`updated_at`) VALUES ('rent_contract_sections_json','','租赁协议条款JSON','首次部署后按实际业务配置',136,NOW());
