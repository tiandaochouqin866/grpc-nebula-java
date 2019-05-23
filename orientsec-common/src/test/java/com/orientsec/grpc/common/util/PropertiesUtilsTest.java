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
package com.orientsec.grpc.common.util;

import com.orientsec.grpc.common.constant.GlobalConstants;
import com.orientsec.grpc.common.resource.SystemConfig;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

/**
 * Test for PropertiesUtils
 *
 * @author sxp
 * @since 2018/11/28
 */
public class PropertiesUtilsTest {
  private static String oldUserDir = System.getProperty("user.dir");

  @BeforeClass
  public static void setUp() {
    System.setProperty("user.dir", oldUserDir + "/..");
  }

  @AfterClass
  public static void tearDown() {
    System.setProperty("user.dir", oldUserDir);
  }

  @Test
  public void getProperties() throws Exception {
    Properties pros;

    pros = PropertiesUtils.getProperties(GlobalConstants.CONFIG_FILE_PATH);
    Assert.assertEquals("5", pros.getProperty("zookeeper.retryNum"));

    System.setProperty(GlobalConstants.SYSTEM_PATH_NAME, "config");
    pros = PropertiesUtils.getProperties(GlobalConstants.CONFIG_FILE_PATH);
    Assert.assertEquals("5", pros.getProperty("zookeeper.retryNum"));

    System.setProperty(GlobalConstants.SYSTEM_PATH_NAME, "config/");
    pros = PropertiesUtils.getProperties(GlobalConstants.CONFIG_FILE_PATH);
    Assert.assertEquals("5", pros.getProperty("zookeeper.retryNum"));

    System.setProperty(GlobalConstants.SYSTEM_PATH_NAME, "config\\");
    pros = PropertiesUtils.getProperties(GlobalConstants.CONFIG_FILE_PATH);
    Assert.assertEquals("5", pros.getProperty("zookeeper.retryNum"));

    try {
      System.setProperty(GlobalConstants.SYSTEM_PATH_NAME, "errorDir\\");
      PropertiesUtils.getProperties(GlobalConstants.CONFIG_FILE_PATH);
      Assert.fail();
    } catch (IOException e) {
    }

  }

  @Test
  public void getValidIntegerValue() throws Exception {
    String key = GlobalConstants.Consumer.Key.SWITCHOVER_THRESHOLD;
    int defaultValue = 5;

    Properties properties = SystemConfig.getProperties();

    int threshold = PropertiesUtils.getValidIntegerValue(properties, key, defaultValue);
    if (threshold <= 0) {
      threshold = defaultValue;
    }

    Assert.assertEquals(defaultValue, threshold);
  }

  @Test
  public void getValidLongValue() {
    String key = GlobalConstants.Consumer.Key.PUNISH_TIME;
    long defaultValue = 60;
    Properties properties = SystemConfig.getProperties();

    long time = PropertiesUtils.getValidLongValue(properties, key, defaultValue);
    if (time < 0) {// 可以为0
      time = defaultValue;
    }

    Assert.assertEquals(defaultValue, time);
  }



}
