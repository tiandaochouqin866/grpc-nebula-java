/*
 * Copyright 2019 Orient Securities Co., Ltd.
 * Copyright 2019 BoCloud Inc.
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
package com.orientsec.grpc.common;

import com.orientsec.grpc.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.CodeSource;

/**
 * 东方证券grpc版本号
 *
 * @author sxp
 * @since V1.0 2017/5/23
 */
public class OrientsecGrpcVersion {
  private static final Logger logger = LoggerFactory.getLogger(OrientsecGrpcVersion.class);
  private static final String DEFAULT_VERSION = "1.2.4";// 默认版本号
  /**
   * 版本号
   */
  public static final String VERSION = initVersion();



  /**
   * 初始化版本号
   *
   * @author sxp
   * @since 2018-5-23
   * @since 2018-5-24 先声明logger和DEFAULT_VERSION，然后再声明VERSION
   * @since 2019-11-9 modify by sxp 为了区分不同语言版本的框架，增加语言作为前缀
   */
  private static String initVersion() {
    String version;

    try {
      version = getVersionByCodeSource();

      // 如果版本号中存在-SNAPSHOT，去掉该后缀(例如1.0.0-SNAPSHOT)
      if (!StringUtils.isEmpty(version) && version.contains("-SNAPSHOT")) {
        version = version.replace("-SNAPSHOT", "");
      }
    } catch (Throwable e) {
      version = null;
      logger.warn("初始化版本号出错", e);
    }

    if (StringUtils.isEmpty(version)) {
      version = DEFAULT_VERSION;
    }

    version = "java-" + version;

    return version;
  }

  /**
   * 首先基于jar包名获取版本号，如果获取不到返回null
   * <p>
   * 参考资料：com.alibaba.dubbo.common.Version
   * </p>
   *
   * @author sxp
   * @since 2018-5-23
   */
  private static String getVersionByCodeSource() {
    String version = null;

    Class<OrientsecGrpcVersion> cls = OrientsecGrpcVersion.class;
    CodeSource codeSource = cls.getProtectionDomain().getCodeSource();

    if (codeSource == null) {
      return null;
    }

    String fileName = codeSource.getLocation().getFile();

    // 文件名示例：/root/.gradle/caches/xxxxxx/netty-common-4.1.8.Final.jar

    if (fileName != null && fileName.length() > 0 && fileName.endsWith(".jar")) {
      // logger.info("OrientsecGrpcVersion.getVersionByCodeSource.fileName=" + fileName);

      fileName = fileName.substring(0, fileName.length() - 4);

      int i = fileName.lastIndexOf('/');
      if (i >= 0) {
        fileName = fileName.substring(i + 1);// netty-common-4.1.8.Final
      }

      i = fileName.indexOf("-");
      if (i >= 0) {
        fileName = fileName.substring(i + 1);// common-4.1.8.Final
      }

      while (fileName.length() > 0 && !Character.isDigit(fileName.charAt(0))) {
        i = fileName.indexOf("-");
        if (i >= 0) {
          fileName = fileName.substring(i + 1);// 4.1.8.Final
        } else {
          break;
        }
      }

      version = fileName;// 4.1.8.Final

      logger.info("OrientsecGrpcVersion.getVersionByCodeSource.version=" + version);
    }

    return version;
  }


}
