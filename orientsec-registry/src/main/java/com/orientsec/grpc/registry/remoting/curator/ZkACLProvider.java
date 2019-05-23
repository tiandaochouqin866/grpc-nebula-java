/*
 * Copyright 2018-2019 The Apache Software Foundation
 * Modifications 2019 Orient Securities Co., Ltd.
 * Modifications 2019 BoCloud Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientsec.grpc.registry.remoting.curator;

import com.orientsec.grpc.common.resource.SystemConfig;
import com.orientsec.grpc.common.util.DesEncryptUtils;
import com.orientsec.grpc.common.util.PropertiesUtils;
import com.orientsec.grpc.common.util.StringUtils;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.orientsec.grpc.common.constant.GlobalConstants.ACL_PASSWORD;
import static com.orientsec.grpc.common.constant.GlobalConstants.ACL_USERNAME;

/**
 * zookeeper访问控制提供者
 *
 * @author sxp
 * @since 2019/2/12
 */
public class ZkACLProvider implements ACLProvider {
  private final static Logger logger = LoggerFactory.getLogger(ZkACLProvider.class);
  private static final String SCHEME = "digest";
  private static final String USER_PASSWORD = initUserPassword();

  /**
   * 加载配置文件中的用户名和密码
   */
  private static String initUserPassword() {
    Properties properties = SystemConfig.getProperties();
    String user = PropertiesUtils.getStringValue(properties, ACL_USERNAME, null);
    String password = PropertiesUtils.getStringValue(properties, ACL_PASSWORD, null);

    if (StringUtils.isNotEmpty(user) && StringUtils.isNotEmpty(password)) {
      try {
        password = DesEncryptUtils.decrypt(password);
      } catch (Exception e) {
        logger.warn( "访问控制密码解密失败，" + e.getMessage(), e);
        return null;
      }

      return user + ":" + password;
    }

    return null;
  }

  private List<ACL> acl;

  @Override
  public List<ACL> getDefaultAcl() {
    if (acl == null) {
      ArrayList<ACL> acl = ZooDefs.Ids.CREATOR_ALL_ACL;
      acl.clear();

      String auth;
      try {
        auth = DigestAuthenticationProvider.generateDigest(USER_PASSWORD);
      } catch (NoSuchAlgorithmException e) {
        auth = "";
      }

      acl.add(new ACL(ZooDefs.Perms.ALL, new Id(SCHEME, auth)));
      this.acl = acl;
    }
    return acl;
  }

  @Override
  public List<ACL> getAclForPath(String path) {
    return acl;
  }

  public static String getScheme() {
    return SCHEME;
  }

  public static String getUserPassword() {
    return USER_PASSWORD;
  }
}
