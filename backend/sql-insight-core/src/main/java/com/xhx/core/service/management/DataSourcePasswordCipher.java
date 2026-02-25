package com.xhx.core.service.management;

import com.xhx.common.util.AesUtil;
import com.xhx.dal.entity.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据源密码加解密组件
 *
 * <p>密钥通过 {@code ds.encrypt-key} 配置项注入（建议使用环境变量 DS_ENCRYPT_KEY）。
 * 生成密钥命令：{@code openssl rand -base64 32}
 *
 * @author master
 */
@Slf4j
@Component
public class DataSourcePasswordCipher {

    @Value("${ds.encrypt-key:}")
    private String encryptKey;

    /**
     * 加密密码，用于持久化到 DB
     */
    public String encrypt(String plainPassword) {
        assertKeyConfigured();
        return AesUtil.encrypt(plainPassword, encryptKey);
    }

    /**
     * 解密从 DB 读取的密码
     */
    public String decrypt(String encryptedPassword) {
        assertKeyConfigured();
        return AesUtil.decrypt(encryptedPassword, encryptKey);
    }

    /**
     * 返回一个 password 字段已解密的 DataSource 副本，用于建立 JDBC 连接。
     * 原始实体对象不受影响。
     */
    public DataSource decryptedCopy(DataSource ds) {
        DataSource copy = new DataSource();
        BeanUtils.copyProperties(ds, copy);
        copy.setPassword(decrypt(ds.getPassword()));
        return copy;
    }

    private void assertKeyConfigured() {
        if (!StringUtils.hasText(encryptKey)) {
            throw new IllegalStateException(
                    "数据源密码加密密钥未配置，请设置环境变量 DS_ENCRYPT_KEY 或配置 ds.encrypt-key");
        }
    }
}
