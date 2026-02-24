/*
 Navicat Premium Dump SQL

 Source Server         : 阿里云服务器
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27)
 Source Host           : 127.0.0.1:3306
 Source Schema         : sql_insight

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27)
 File Encoding         : 65001

 Date: 25/02/2026 02:01:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `session_id` bigint NOT NULL COMMENT '关联会话ID',
                                 `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色：USER 或 AI',
                                 `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息文本（用户问题或生成的SQL）',
                                 `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`) USING BTREE,
                                 INDEX `idx_session`(`session_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 97 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `user_id` bigint NOT NULL COMMENT '用户ID',
                                 `data_source_id` bigint NOT NULL COMMENT '对应的数据源配置ID',
                                 `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '新对话' COMMENT '对话标题',
                                 `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`) USING BTREE,
                                 INDEX `idx_user_ds`(`user_id` ASC, `data_source_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for data_source
-- ----------------------------
DROP TABLE IF EXISTS `data_source`;
CREATE TABLE `data_source`  (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `conn_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `db_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `port` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `password` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `database_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                `driver_class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for query_policy
-- ----------------------------
DROP TABLE IF EXISTS `query_policy`;
CREATE TABLE `query_policy`  (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `role_id` bigint NOT NULL,
                                 `allow_join` tinyint NOT NULL,
                                 `allow_subquery` tinyint NOT NULL,
                                 `max_limit` int NOT NULL,
                                 `allow_aggregation` tinyint NOT NULL,
                                 `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
                                 `gmt_created` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                                 `gmt_modified` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色id',
                         `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
                         `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色描述',
                         `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
                         `gmt_created` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                         `gmt_modified` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`id`) USING BTREE,
                         UNIQUE INDEX `role_name`(`role_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for table_permission
-- ----------------------------
DROP TABLE IF EXISTS `table_permission`;
CREATE TABLE `table_permission`  (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `role_id` bigint NOT NULL,
                                     `data_source_id` bigint NOT NULL,
                                     `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `permission` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
                                     `gmt_created` datetime NULL DEFAULT CURRENT_TIMESTAMP,
                                     `gmt_modified` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `role_id` bigint NOT NULL,
                         `system_permission` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统权限',
                         `status` tinyint NOT NULL DEFAULT 1,
                         `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0正常, 1已删除',
                         PRIMARY KEY (`id`) USING BTREE,
                         UNIQUE INDEX `username`(`user_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_data_source
-- ----------------------------
DROP TABLE IF EXISTS `user_data_source`;
CREATE TABLE `user_data_source`  (
                                     `user_id` bigint NOT NULL,
                                     `data_source_id` bigint NOT NULL,
                                     PRIMARY KEY (`user_id`, `data_source_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
